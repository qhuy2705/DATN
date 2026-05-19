package com.PrimeCare.PrimeCare.modules.appointment.job;

import com.PrimeCare.PrimeCare.modules.appointment.entity.AppointmentSlotHold;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentSlotHoldRepository;
import com.PrimeCare.PrimeCare.modules.appointment.service.AppointmentAvailabilityService;
import com.PrimeCare.PrimeCare.modules.appointment.service.DoctorCancellationRecoveryService;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentFollowUpType;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentSlotHoldStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlotHoldExpiryJob {

    private final AppointmentSlotHoldRepository slotHoldRepository;
    private final DoctorCancellationRecoveryService recoveryService;
    private final AppointmentAvailabilityService availabilityService;

    @Scheduled(cron = "${app.appointment.slot-hold-expiry-cron:0 */5 * * * *}")
    @Transactional
    public void run() {
        LocalDateTime now = LocalDateTime.now();
        for (AppointmentSlotHold hold : slotHoldRepository.findDueForExpiryForUpdate(EnumSet.of(AppointmentSlotHoldStatus.HELD), now)) {
            int updated = slotHoldRepository.expireHeldHold(hold.getId(), now);
            if (updated == 0) {
                continue;
            }
            hold.setStatus(AppointmentSlotHoldStatus.EXPIRED);
            hold.setExpiredAt(now);
            recoveryService.markFollowUpRequired(
                    hold.getOriginalAppointment(),
                    AppointmentFollowUpType.DOCTOR_CANCELLATION_NO_RESPONSE
            );
            recoveryService.notifyStaffFollowUpRequired(
                    hold.getOriginalAppointment(),
                    AppointmentFollowUpType.DOCTOR_CANCELLATION_NO_RESPONSE
            );
            availabilityService.evictAvailabilityCacheForDoctorDateSession(
                    hold.getDoctor().getId(),
                    hold.getVisitDate(),
                    hold.getSession()
            );
            log.info("slot_hold_expired holdId={} originalAppointmentId={}",
                    hold.getId(),
                    hold.getOriginalAppointment() != null ? hold.getOriginalAppointment().getId() : null);
        }
    }
}
