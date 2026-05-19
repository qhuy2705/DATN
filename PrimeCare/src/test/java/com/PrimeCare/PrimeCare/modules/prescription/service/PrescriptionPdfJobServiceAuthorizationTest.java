package com.PrimeCare.PrimeCare.modules.prescription.service;

import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.encounter.entity.Encounter;
import com.PrimeCare.PrimeCare.modules.file.service.FileStorageService;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.prescription.entity.Prescription;
import com.PrimeCare.PrimeCare.modules.prescription.entity.PrescriptionPdfJob;
import com.PrimeCare.PrimeCare.modules.prescription.repository.PrescriptionPdfJobRepository;
import com.PrimeCare.PrimeCare.modules.prescription.repository.PrescriptionRepository;
import com.PrimeCare.PrimeCare.modules.realtime.service.AfterCommitExecutor;
import com.PrimeCare.PrimeCare.shared.enums.PrescriptionPdfJobStatus;
import com.PrimeCare.PrimeCare.shared.enums.PrescriptionStatus;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrescriptionPdfJobServiceAuthorizationTest {

    @Mock
    private PrescriptionPdfJobRepository prescriptionPdfJobRepository;
    @Mock
    private PrescriptionRepository prescriptionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private PrescriptionPdfProcessorService prescriptionPdfProcessorService;
    @Mock
    private AfterCommitExecutor afterCommitExecutor;

    private PrescriptionPdfJobService service;

    @BeforeEach
    void setUp() {
        service = new PrescriptionPdfJobService(
                prescriptionPdfJobRepository,
                prescriptionRepository,
                userRepository,
                rabbitTemplate,
                fileStorageService,
                prescriptionPdfProcessorService,
                afterCommitExecutor
        );
    }

    @Test
    void shouldCreatePdfJobWhenDoctorOwnsPrescription() {
        Prescription prescription = prescription(11L, 20L);
        User doctorUser = doctorUser(7L, 20L);
        when(prescriptionRepository.findById(prescription.getId())).thenReturn(Optional.of(prescription));
        when(userRepository.findById(doctorUser.getId())).thenReturn(Optional.of(doctorUser));
        when(prescriptionPdfJobRepository.saveAndFlush(any(PrescriptionPdfJob.class))).thenAnswer(invocation -> {
            PrescriptionPdfJob job = invocation.getArgument(0);
            job.setId(100L);
            return job;
        });

        PrescriptionPdfJob job = service.requestGenerate(prescription.getId(), doctorUser.getId());

        assertThat(job.getPrescriptionId()).isEqualTo(prescription.getId());
        assertThat(job.getRequestedBy()).isEqualTo(doctorUser);
        assertThat(job.getStatus()).isEqualTo(PrescriptionPdfJobStatus.PENDING);
        verify(afterCommitExecutor).execute(any(Runnable.class));
    }

    @Test
    void shouldRejectPdfJobRequestWhenDoctorDoesNotOwnPrescription() {
        Prescription prescription = prescription(11L, 30L);
        User doctorUser = doctorUser(7L, 20L);
        when(prescriptionRepository.findById(prescription.getId())).thenReturn(Optional.of(prescription));
        when(userRepository.findById(doctorUser.getId())).thenReturn(Optional.of(doctorUser));
        lenient().when(prescriptionPdfJobRepository.saveAndFlush(any(PrescriptionPdfJob.class))).thenAnswer(invocation -> {
            PrescriptionPdfJob job = invocation.getArgument(0);
            job.setId(100L);
            return job;
        });

        assertThatThrownBy(() -> service.requestGenerate(prescription.getId(), doctorUser.getId()))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED)
                );

        verifyNoInteractions(prescriptionPdfJobRepository, afterCommitExecutor);
    }

    @Test
    void shouldRejectPdfJobRequestWhenDoctorProfileIsMissing() {
        Prescription prescription = prescription(11L, 30L);
        User userWithoutDoctorProfile = User.builder()
                .id(7L)
                .role(UserRole.DOCTOR)
                .build();
        when(prescriptionRepository.findById(prescription.getId())).thenReturn(Optional.of(prescription));
        when(userRepository.findById(userWithoutDoctorProfile.getId())).thenReturn(Optional.of(userWithoutDoctorProfile));
        lenient().when(prescriptionPdfJobRepository.saveAndFlush(any(PrescriptionPdfJob.class))).thenAnswer(invocation -> {
            PrescriptionPdfJob job = invocation.getArgument(0);
            job.setId(100L);
            return job;
        });

        assertThatThrownBy(() -> service.requestGenerate(prescription.getId(), userWithoutDoctorProfile.getId()))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED)
                );

        verifyNoInteractions(prescriptionPdfJobRepository, afterCommitExecutor);
    }

    @Test
    void shouldRejectGetJobWhenRequesterIsNotJobRequester() {
        PrescriptionPdfJob job = PrescriptionPdfJob.builder()
                .id(44L)
                .prescriptionId(11L)
                .requestedBy(doctorUser(8L, 21L))
                .status(PrescriptionPdfJobStatus.PENDING)
                .build();
        when(prescriptionPdfJobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.getJob(job.getId(), 7L))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED)
                );
    }

    @Test
    void shouldRejectDownloadWhenRequesterIsNotJobRequester() {
        PrescriptionPdfJob job = PrescriptionPdfJob.builder()
                .id(44L)
                .prescriptionId(11L)
                .requestedBy(doctorUser(8L, 21L))
                .status(PrescriptionPdfJobStatus.COMPLETED)
                .filePath("prescriptions/rx-11.pdf")
                .build();
        when(prescriptionPdfJobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.download(job.getId(), 7L))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED)
                );

        verifyNoInteractions(fileStorageService);
    }

    @Test
    void shouldDownloadPdfWhenRequesterOwnsCompletedJob() {
        User requester = doctorUser(7L, 20L);
        PrescriptionPdfJob job = PrescriptionPdfJob.builder()
                .id(44L)
                .prescriptionId(11L)
                .requestedBy(requester)
                .status(PrescriptionPdfJobStatus.COMPLETED)
                .filePath("prescriptions/rx-11.pdf")
                .build();
        byte[] pdf = "%PDF prescription".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(prescriptionPdfJobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(fileStorageService.downloadAsBytes(job.getFilePath())).thenReturn(pdf);

        byte[] downloaded = service.download(job.getId(), requester.getId());

        assertThat(downloaded).isEqualTo(pdf);
    }

    private Prescription prescription(Long id, Long encounterDoctorProfileId) {
        DoctorProfile encounterDoctor = DoctorProfile.builder()
                .id(encounterDoctorProfileId)
                .fullName("Dr Owner")
                .build();
        Encounter encounter = Encounter.builder()
                .id(500L + id)
                .code("ENC-" + id)
                .doctor(encounterDoctor)
                .patientFullNameSnapshot("Patient")
                .build();
        return Prescription.builder()
                .id(id)
                .code("RX-" + id)
                .encounter(encounter)
                .doctorUser(doctorUser(100L + id, encounterDoctorProfileId))
                .issuedDate(LocalDate.now())
                .status(PrescriptionStatus.ISSUED)
                .build();
    }

    private User doctorUser(Long userId, Long doctorProfileId) {
        return User.builder()
                .id(userId)
                .role(UserRole.DOCTOR)
                .doctorProfile(DoctorProfile.builder()
                        .id(doctorProfileId)
                        .fullName("Dr " + doctorProfileId)
                        .build())
                .build();
    }
}
