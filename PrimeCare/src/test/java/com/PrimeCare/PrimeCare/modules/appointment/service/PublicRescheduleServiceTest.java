package com.PrimeCare.PrimeCare.modules.appointment.service;

import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.entity.AppointmentSlotHold;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentSlotHoldRepository;
import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.entity.Specialty;
import com.PrimeCare.PrimeCare.modules.notification.messaging.AppointmentMailEventPublisher;
import com.PrimeCare.PrimeCare.modules.notification.messaging.event.SlotHoldAcceptedMailEvent;
import com.PrimeCare.PrimeCare.modules.notification.messaging.event.SlotHoldCancelledMailEvent;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentFollowUpType;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentSlotHoldReason;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentSlotHoldStatus;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.enums.ArrivalStatus;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicRescheduleServiceTest {

    @Mock
    private AppointmentSlotHoldRepository slotHoldRepository;
    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private AppointmentSlotHoldTokenService tokenService;
    @Mock
    private AppointmentSlotAvailabilityGuard slotAvailabilityGuard;
    @Mock
    private AppointmentCodeGenerator appointmentCodeGenerator;
    @Mock
    private AppointmentStatusHistoryService appointmentStatusHistoryService;
    @Mock
    private AppointmentQueueService appointmentQueueService;
    @Mock
    private AppointmentAvailabilityService availabilityService;
    @Mock
    private AppointmentMailEventPublisher mailEventPublisher;
    @Mock
    private DoctorCancellationRecoveryService recoveryService;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private PublicRescheduleService service;

    @BeforeEach
    void setUp() {
        when(tokenService.hashToken("token")).thenReturn("hash");
    }

    @Test
    void acceptCreatesConfirmedAppointmentAndMarksHoldAccepted() {
        AppointmentSlotHold hold = hold(LocalDateTime.now().plusHours(4));
        when(slotHoldRepository.findByTokenHashForUpdate("hash")).thenReturn(Optional.of(hold));
        when(appointmentCodeGenerator.generate(hold.getVisitDate(), hold.getDoctor().getId(), hold.getSlotStart()))
                .thenReturn("APT-NEW");
        Appointment[] savedRef = new Appointment[1];
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> {
            Appointment appointment = invocation.getArgument(0);
            appointment.setId(88L);
            savedRef[0] = appointment;
            return appointment;
        });
        when(appointmentRepository.findById(88L)).thenAnswer(ignored -> Optional.of(savedRef[0]));
        when(slotHoldRepository.save(hold)).thenReturn(hold);

        service.accept("token");

        ArgumentCaptor<Appointment> appointmentCaptor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).save(appointmentCaptor.capture());
        Appointment newAppointment = appointmentCaptor.getValue();
        assertThat(newAppointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(newAppointment.getCode()).isEqualTo("APT-NEW");
        assertThat(newAppointment.getRescheduledFromAppointment()).isSameAs(hold.getOriginalAppointment());
        assertThat(hold.getStatus()).isEqualTo(AppointmentSlotHoldStatus.ACCEPTED);
        assertThat(hold.getAcceptedAt()).isNotNull();
        verify(mailEventPublisher).publishSlotHoldAccepted(new SlotHoldAcceptedMailEvent(hold.getId(), 88L));
    }

    @Test
    void acceptRejectsExpiredHold() {
        AppointmentSlotHold hold = hold(LocalDateTime.now().minusMinutes(1));
        when(slotHoldRepository.findByTokenHashForUpdate("hash")).thenReturn(Optional.of(hold));

        assertThatThrownBy(() -> service.accept("token"))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PUBLIC_LOOKUP_TOKEN_EXPIRED)
                );

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void requestContactCreatesDoctorCancellationFollowUpAndKeepsHoldHeld() {
        AppointmentSlotHold hold = hold(LocalDateTime.now().plusHours(4));
        when(slotHoldRepository.findByTokenHashForUpdate("hash")).thenReturn(Optional.of(hold));
        when(slotHoldRepository.save(hold)).thenReturn(hold);

        service.requestContact("token");

        assertThat(hold.getStatus()).isEqualTo(AppointmentSlotHoldStatus.HELD);
        assertThat(hold.getContactRequestedAt()).isNotNull();
        verify(recoveryService).markFollowUpRequired(
                hold.getOriginalAppointment(),
                AppointmentFollowUpType.DOCTOR_CANCELLATION_CONTACT_REQUESTED
        );
    }

    @Test
    void cancelMarksHoldCancelledWithoutCreatingAppointment() {
        AppointmentSlotHold hold = hold(LocalDateTime.now().plusHours(4));
        when(slotHoldRepository.findByTokenHashForUpdate("hash")).thenReturn(Optional.of(hold));
        when(slotHoldRepository.save(hold)).thenReturn(hold);

        service.cancel("token");

        assertThat(hold.getStatus()).isEqualTo(AppointmentSlotHoldStatus.CANCELLED);
        assertThat(hold.getCancelledAt()).isNotNull();
        verify(appointmentRepository, never()).save(any());
        verify(mailEventPublisher).publishSlotHoldCancelled(new SlotHoldCancelledMailEvent(hold.getId()));
    }

    private AppointmentSlotHold hold(LocalDateTime expiresAt) {
        Branch branch = Branch.builder().id(1L).nameVn("PrimeCare").build();
        Specialty specialty = Specialty.builder().id(2L).nameVn("Nội tổng quát").build();
        DoctorProfile oldDoctor = DoctorProfile.builder().id(3L).fullName("Dr Old").branch(branch).build();
        DoctorProfile newDoctor = DoctorProfile.builder().id(4L).fullName("Dr New").branch(branch).build();
        Appointment original = Appointment.builder()
                .id(10L)
                .code("APT-10")
                .status(AppointmentStatus.CANCELLED)
                .branch(branch)
                .specialty(specialty)
                .doctor(oldDoctor)
                .visitDate(LocalDate.now().plusDays(1))
                .session(BranchSessionType.AM)
                .etaStart(LocalTime.of(8, 0))
                .etaEnd(LocalTime.of(8, 30))
                .patientFullName("Patient")
                .patientPhone("0900000000")
                .patientEmail("patient@example.test")
                .sourceType(com.PrimeCare.PrimeCare.shared.enums.AppointmentSourceType.PUBLIC_BOOKING)
                .arrivalStatus(ArrivalStatus.NOT_ARRIVED)
                .build();
        return AppointmentSlotHold.builder()
                .id(20L)
                .originalAppointment(original)
                .patient(original.getPatient())
                .doctor(newDoctor)
                .branch(branch)
                .specialty(specialty)
                .visitDate(LocalDate.now().plusDays(2))
                .session(BranchSessionType.PM)
                .slotStart(LocalTime.of(13, 30))
                .slotEnd(LocalTime.of(14, 0))
                .status(AppointmentSlotHoldStatus.HELD)
                .expiresAt(expiresAt)
                .holdReason(AppointmentSlotHoldReason.DOCTOR_CANCELLED)
                .tokenHash("hash")
                .tokenNonce("nonce")
                .createdAt(LocalDateTime.now())
                .build();
    }
}
