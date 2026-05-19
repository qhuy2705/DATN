package com.PrimeCare.PrimeCare.modules.appointment.job;

import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.entity.AppointmentSlotHold;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentSlotHoldRepository;
import com.PrimeCare.PrimeCare.modules.appointment.service.AppointmentAvailabilityService;
import com.PrimeCare.PrimeCare.modules.appointment.service.AppointmentSlotHoldTokenService;
import com.PrimeCare.PrimeCare.modules.appointment.service.DoctorCancellationRecoveryService;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.notification.messaging.AppointmentMailEventPublisher;
import com.PrimeCare.PrimeCare.modules.notification.messaging.event.SlotHoldReminderMailEvent;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentFollowUpType;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentSlotHoldStatus;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlotHoldJobsTest {

    @Mock
    private AppointmentSlotHoldRepository slotHoldRepository;
    @Mock
    private AppointmentSlotHoldTokenService tokenService;
    @Mock
    private AppointmentMailEventPublisher mailEventPublisher;
    @Mock
    private DoctorCancellationRecoveryService recoveryService;
    @Mock
    private AppointmentAvailabilityService availabilityService;

    @Test
    void reminderJobQueuesReminderOnlyWhenConditionalMarkSucceeds() {
        AppointmentSlotHold hold = hold();
        when(slotHoldRepository.findDueForReminder(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(hold));
        when(slotHoldRepository.markReminderQueued(eq(20L), any(LocalDateTime.class))).thenReturn(1);
        when(tokenService.issueToken(hold)).thenReturn("token");

        new SlotHoldReminderJob(slotHoldRepository, tokenService, mailEventPublisher).run();

        verify(mailEventPublisher).publishSlotHoldReminder(new SlotHoldReminderMailEvent(20L, "token"));
    }

    @Test
    void reminderJobDoesNotPublishWhenAnotherWorkerAlreadyQueued() {
        AppointmentSlotHold hold = hold();
        when(slotHoldRepository.findDueForReminder(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(hold));
        when(slotHoldRepository.markReminderQueued(eq(20L), any(LocalDateTime.class))).thenReturn(0);

        new SlotHoldReminderJob(slotHoldRepository, tokenService, mailEventPublisher).run();

        verify(mailEventPublisher, never()).publishSlotHoldReminder(any());
    }

    @Test
    void expiryJobExpiresHoldAndCreatesNoResponseFollowUp() {
        AppointmentSlotHold hold = hold();
        when(slotHoldRepository.findDueForExpiryForUpdate(any(Collection.class), any(LocalDateTime.class)))
                .thenReturn(List.of(hold));
        when(slotHoldRepository.expireHeldHold(eq(20L), any(LocalDateTime.class))).thenReturn(1);

        new SlotHoldExpiryJob(slotHoldRepository, recoveryService, availabilityService).run();

        verify(recoveryService).markFollowUpRequired(
                hold.getOriginalAppointment(),
                AppointmentFollowUpType.DOCTOR_CANCELLATION_NO_RESPONSE
        );
        verify(recoveryService).notifyStaffFollowUpRequired(
                hold.getOriginalAppointment(),
                AppointmentFollowUpType.DOCTOR_CANCELLATION_NO_RESPONSE
        );
        verify(availabilityService).evictAvailabilityCacheForDoctorDateSession(4L, hold.getVisitDate(), hold.getSession());
    }

    @Test
    void expiryJobDoesNothingWhenConditionalExpireLosesRace() {
        AppointmentSlotHold hold = hold();
        when(slotHoldRepository.findDueForExpiryForUpdate(any(Collection.class), any(LocalDateTime.class)))
                .thenReturn(List.of(hold));
        when(slotHoldRepository.expireHeldHold(eq(20L), any(LocalDateTime.class))).thenReturn(0);

        new SlotHoldExpiryJob(slotHoldRepository, recoveryService, availabilityService).run();

        verify(recoveryService, never()).markFollowUpRequired(any(), any());
        verify(availabilityService, never()).evictAvailabilityCacheForDoctorDateSession(any(), any(), any());
    }

    private AppointmentSlotHold hold() {
        Appointment original = Appointment.builder()
                .id(10L)
                .code("APT-10")
                .patientFullName("Patient")
                .build();
        DoctorProfile doctor = DoctorProfile.builder().id(4L).fullName("Dr New").build();
        return AppointmentSlotHold.builder()
                .id(20L)
                .originalAppointment(original)
                .doctor(doctor)
                .visitDate(LocalDate.now().plusDays(1))
                .session(BranchSessionType.PM)
                .slotStart(LocalTime.of(13, 30))
                .slotEnd(LocalTime.of(14, 0))
                .status(AppointmentSlotHoldStatus.HELD)
                .expiresAt(LocalDateTime.now().plusHours(2))
                .tokenNonce("nonce")
                .build();
    }
}
