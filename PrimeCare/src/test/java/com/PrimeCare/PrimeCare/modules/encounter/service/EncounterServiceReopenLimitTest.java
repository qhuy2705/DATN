package com.PrimeCare.PrimeCare.modules.encounter.service;

import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.booking_restriction.service.PatientViolationEventService;
import com.PrimeCare.PrimeCare.modules.encounter.dto.request.UpdateEncounterRequest;
import com.PrimeCare.PrimeCare.modules.encounter.dto.record.EncounterWorkflowState;
import com.PrimeCare.PrimeCare.modules.encounter.entity.Encounter;
import com.PrimeCare.PrimeCare.modules.encounter.repository.EncounterDiagnosisRepository;
import com.PrimeCare.PrimeCare.modules.encounter.repository.EncounterReopenLogRepository;
import com.PrimeCare.PrimeCare.modules.encounter.repository.EncounterRepository;
import com.PrimeCare.PrimeCare.modules.notification.messaging.AppointmentMailEventPublisher;
import com.PrimeCare.PrimeCare.modules.prescription.repository.PrescriptionRepository;
import com.PrimeCare.PrimeCare.modules.realtime.service.AfterCommitExecutor;
import com.PrimeCare.PrimeCare.modules.realtime.service.RealtimeEventPublisher;
import com.PrimeCare.PrimeCare.modules.service_order.repository.ServiceOrderRepository;
import com.PrimeCare.PrimeCare.modules.service_result.repository.ServiceResultRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.entity.Specialty;
import com.PrimeCare.PrimeCare.modules.patient.entity.Patient;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import com.PrimeCare.PrimeCare.shared.enums.EncounterStatus;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class EncounterServiceReopenLimitTest {

    @Mock
    private EncounterRepository encounterRepository;
    @Mock
    private EncounterDiagnosisRepository encounterDiagnosisRepository;
    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ServiceOrderRepository serviceOrderRepository;
    @Mock
    private PrescriptionRepository prescriptionRepository;
    @Mock
    private ServiceResultRepository serviceResultRepository;
    @Mock
    private EncounterWorkflowService encounterWorkflowService;
    @Mock
    private EncounterReopenLogRepository encounterReopenLogRepository;
    @Mock
    private AppointmentMailEventPublisher appointmentMailEventPublisher;
    @Mock
    private RealtimeEventPublisher realtimeEventPublisher;
    @Mock
    private AfterCommitExecutor afterCommitExecutor;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private PatientViolationEventService patientViolationEventService;

    private EncounterService service;

    @BeforeEach
    void setUp() {
        service = new EncounterService(
                encounterRepository,
                encounterDiagnosisRepository,
                appointmentRepository,
                userRepository,
                serviceOrderRepository,
                prescriptionRepository,
                serviceResultRepository,
                encounterWorkflowService,
                encounterReopenLogRepository,
                appointmentMailEventPublisher,
                realtimeEventPublisher,
                afterCommitExecutor,
                auditLogService,
                patientViolationEventService
        );
        ReflectionTestUtils.setField(service, "maxReopenCount", 3);
    }

    @Test
    void reopenEncounterRejectsWhenLimitReached() {
        Encounter encounter = Encounter.builder()
                .id(1L)
                .status(EncounterStatus.COMPLETED)
                .reopenedCount(3)
                .build();
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(encounter));

        assertThatThrownBy(() -> service.reopenEncounter(1L, 9L, "reason"))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ENCOUNTER_REOPEN_NOT_ALLOWED)
                );

        verify(userRepository, never()).findById(9L);
    }

    @Test
    void createFromAppointmentStartsEncounterOnlyFromCheckedInAppointment() {
        DoctorProfile doctor = DoctorProfile.builder().id(3L).fullName("Dr Test").build();
        User doctorUser = User.builder()
                .id(9L)
                .role(UserRole.DOCTOR)
                .doctorProfile(doctor)
                .email("doctor@example.test")
                .build();
        Appointment appointment = Appointment.builder()
                .id(2L)
                .code("APT-1")
                .status(AppointmentStatus.CHECKED_IN)
                .visitDate(LocalDate.now())
                .session(BranchSessionType.AM)
                .branch(Branch.builder().id(4L).nameVn("PrimeCare").build())
                .specialty(Specialty.builder().id(5L).nameVn("Noi tong quat").build())
                .doctor(doctor)
                .patient(Patient.builder().id(6L).fullName("Patient Test").build())
                .patientFullName("Patient Test")
                .patientPhone("0900000000")
                .build();
        when(userRepository.findById(9L)).thenReturn(Optional.of(doctorUser));
        when(encounterRepository.findByAppointment_Id(2L)).thenReturn(Optional.empty());
        when(appointmentRepository.findByIdAndDoctor_Id(2L, 3L)).thenReturn(Optional.of(appointment));
        when(encounterRepository.saveAndFlush(any(Encounter.class))).thenAnswer(invocation -> {
            Encounter encounter = invocation.getArgument(0);
            encounter.setId(1L);
            return encounter;
        });
        when(encounterWorkflowService.getWorkflowState(any(Encounter.class))).thenReturn(emptyWorkflowState());
        when(encounterDiagnosisRepository.findWithIcd10ByEncounterId(1L)).thenReturn(List.of());

        var response = service.createFromAppointment(2L, 9L);

        assertThat(response.getStatus()).isEqualTo(EncounterStatus.IN_PROGRESS);
        assertThat(response.getAppointmentId()).isEqualTo(2L);
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CHECKED_IN);
    }

    @Test
    void reopenEncounterRequiresReasonBeforeLoadingEncounter() {
        assertThatThrownBy(() -> service.reopenEncounter(1L, 9L, "  "))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST)
                );

        verify(encounterRepository, never()).findById(1L);
    }

    @Test
    void reopenEncounterDoesNotMoveCompletedAppointmentBackToCheckedIn() {
        LocalDateTime completedAt = LocalDateTime.now().minusHours(2);
        DoctorProfile doctor = DoctorProfile.builder().id(3L).fullName("Dr Test").build();
        Appointment appointment = Appointment.builder()
                .id(2L)
                .code("APT-1")
                .status(AppointmentStatus.COMPLETED)
                .visitDate(LocalDate.now())
                .session(BranchSessionType.AM)
                .etaStart(LocalTime.of(8, 0))
                .completedAt(completedAt)
                .branch(Branch.builder().id(4L).nameVn("PrimeCare").build())
                .specialty(Specialty.builder().id(5L).nameVn("Noi tong quat").build())
                .doctor(doctor)
                .patient(Patient.builder().id(6L).fullName("Patient Test").build())
                .patientFullName("Patient Test")
                .patientPhone("0900000000")
                .build();
        Encounter encounter = Encounter.builder()
                .id(1L)
                .code("ENC-1")
                .appointment(appointment)
                .branch(appointment.getBranch())
                .specialty(appointment.getSpecialty())
                .doctor(doctor)
                .patient(appointment.getPatient())
                .patientFullNameSnapshot("Patient Test")
                .status(EncounterStatus.COMPLETED)
                .completedAt(completedAt)
                .reopenedCount(0)
                .build();
        User doctorUser = User.builder()
                .id(9L)
                .role(UserRole.DOCTOR)
                .doctorProfile(doctor)
                .email("doctor@example.test")
                .build();
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(encounter));
        when(userRepository.findById(9L)).thenReturn(Optional.of(doctorUser));
        when(encounterRepository.save(encounter)).thenReturn(encounter);
        when(encounterWorkflowService.getWorkflowState(encounter)).thenReturn(emptyWorkflowState());
        when(encounterDiagnosisRepository.findWithIcd10ByEncounterId(1L)).thenReturn(List.of());

        service.reopenEncounter(1L, 9L, "Bổ sung kết luận");

        assertThat(encounter.getStatus()).isEqualTo(EncounterStatus.REOPENED);
        assertThat(encounter.getCompletedAt()).isEqualTo(completedAt);
        assertThat(encounter.getReopenedCount()).isEqualTo(1);
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
        assertThat(appointment.getCompletedAt()).isEqualTo(completedAt);
        verify(auditLogService).log(any(), org.mockito.ArgumentMatchers.eq("REOPEN"), org.mockito.ArgumentMatchers.eq("ENCOUNTER"), org.mockito.ArgumentMatchers.eq(1L), any(), any());
    }

    @Test
    void completeEncounterRequiresFinalDiagnosisAndConclusion() {
        DoctorProfile doctor = DoctorProfile.builder().id(3L).fullName("Dr Test").build();
        User doctorUser = User.builder()
                .id(9L)
                .role(UserRole.DOCTOR)
                .doctorProfile(doctor)
                .email("doctor@example.test")
                .build();
        Encounter encounter = Encounter.builder()
                .id(1L)
                .doctor(doctor)
                .status(EncounterStatus.IN_PROGRESS)
                .build();
        when(userRepository.findById(9L)).thenReturn(Optional.of(doctorUser));
        when(encounterRepository.findWithLockById(1L)).thenReturn(Optional.of(encounter));
        when(encounterWorkflowService.getWorkflowState(encounter)).thenReturn(emptyWorkflowState());

        assertThatThrownBy(() -> service.complete(1L, 9L, new com.PrimeCare.PrimeCare.modules.encounter.dto.request.UpdateEncounterRequest()))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST)
                );

        verify(encounterRepository, never()).save(any());
    }

    @Test
    void completeEncounterCreatesSuccessfulVisitCredit() {
        DoctorProfile doctor = DoctorProfile.builder().id(3L).fullName("Dr Test").build();
        User doctorUser = User.builder()
                .id(9L)
                .role(UserRole.DOCTOR)
                .doctorProfile(doctor)
                .email("doctor@example.test")
                .build();
        Appointment appointment = Appointment.builder()
                .id(2L)
                .code("APT-1")
                .status(AppointmentStatus.CHECKED_IN)
                .visitDate(LocalDate.now())
                .session(BranchSessionType.AM)
                .etaStart(LocalTime.of(8, 0))
                .branch(Branch.builder().id(4L).nameVn("PrimeCare").build())
                .specialty(Specialty.builder().id(5L).nameVn("Noi tong quat").build())
                .doctor(doctor)
                .patient(Patient.builder().id(6L).fullName("Patient Test").build())
                .patientFullName("Patient Test")
                .patientPhone("0900000000")
                .build();
        Encounter encounter = Encounter.builder()
                .id(1L)
                .code("ENC-1")
                .appointment(appointment)
                .branch(appointment.getBranch())
                .specialty(appointment.getSpecialty())
                .doctor(doctor)
                .patient(appointment.getPatient())
                .patientFullNameSnapshot("Patient Test")
                .status(EncounterStatus.IN_PROGRESS)
                .finalDiagnosis("Viêm họng")
                .conclusion("Theo dõi")
                .build();
        UpdateEncounterRequest request = new UpdateEncounterRequest();
        request.setFinalDiagnosis("Viêm họng");
        request.setConclusion("Theo dõi");

        when(userRepository.findById(9L)).thenReturn(Optional.of(doctorUser));
        when(encounterRepository.findWithLockById(1L)).thenReturn(Optional.of(encounter));
        when(encounterWorkflowService.getWorkflowState(encounter)).thenReturn(emptyWorkflowState());
        when(encounterRepository.save(encounter)).thenReturn(encounter);
        when(encounterDiagnosisRepository.findWithIcd10ByEncounterId(1L)).thenReturn(List.of());

        service.complete(1L, 9L, request);

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
        verify(patientViolationEventService).recordSuccessfulVisitCredit(eq(appointment), eq(doctorUser));
    }

    @Test
    void shouldUpdateOnlyProvidedEncounterFieldsWhenPatchRequestContainsNulls() {
        DoctorProfile doctor = DoctorProfile.builder().id(3L).fullName("Dr Test").build();
        User doctorUser = User.builder()
                .id(9L)
                .role(UserRole.DOCTOR)
                .doctorProfile(doctor)
                .email("doctor@example.test")
                .build();
        Encounter encounter = Encounter.builder()
                .id(1L)
                .code("ENC-1")
                .appointment(Appointment.builder().id(2L).build())
                .branch(Branch.builder().id(4L).nameVn("PrimeCare").build())
                .specialty(Specialty.builder().id(5L).nameVn("Noi tong quat").build())
                .doctor(doctor)
                .patient(Patient.builder().id(6L).fullName("Patient Test").build())
                .patientFullNameSnapshot("Patient Test")
                .status(EncounterStatus.IN_PROGRESS)
                .chiefComplaint("Đau đầu")
                .clinicalNote("Ghi chú cũ")
                .conclusion("Kết luận cũ")
                .treatmentPlan("Kế hoạch cũ")
                .followUpDate(LocalDate.now().plusDays(7))
                .pulse(80)
                .spo2(98)
                .build();
        UpdateEncounterRequest request = new UpdateEncounterRequest();
        request.setClinicalNote("Ghi chú mới");

        when(userRepository.findById(9L)).thenReturn(Optional.of(doctorUser));
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(encounter));
        when(encounterRepository.save(encounter)).thenReturn(encounter);
        when(encounterWorkflowService.getWorkflowState(encounter)).thenReturn(emptyWorkflowState());
        when(encounterDiagnosisRepository.findWithIcd10ByEncounterId(1L)).thenReturn(List.of());

        service.update(1L, 9L, request);

        assertThat(encounter.getClinicalNote()).isEqualTo("Ghi chú mới");
        assertThat(encounter.getChiefComplaint()).isEqualTo("Đau đầu");
        assertThat(encounter.getConclusion()).isEqualTo("Kết luận cũ");
        assertThat(encounter.getTreatmentPlan()).isEqualTo("Kế hoạch cũ");
        assertThat(encounter.getFollowUpDate()).isEqualTo(LocalDate.now().plusDays(7));
        assertThat(encounter.getPulse()).isEqualTo(80);
        assertThat(encounter.getSpo2()).isEqualTo(98);
    }

    private EncounterWorkflowState emptyWorkflowState() {
        return new EncounterWorkflowState(0, 0, 0, 0, 0, 0, false, false, false, true, false);
    }
}
