package com.PrimeCare.PrimeCare.modules.appointment.job;

import com.PrimeCare.PrimeCare.modules.appointment.entity.AppointmentSlotHold;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentSlotHoldRepository;
import com.PrimeCare.PrimeCare.modules.appointment.service.AppointmentSlotHoldTokenService;
import com.PrimeCare.PrimeCare.modules.notification.messaging.AppointmentMailEventPublisher;
import com.PrimeCare.PrimeCare.modules.notification.messaging.event.SlotHoldReminderMailEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlotHoldReminderJob {

    private final AppointmentSlotHoldRepository slotHoldRepository;
    private final AppointmentSlotHoldTokenService tokenService;
    private final AppointmentMailEventPublisher mailEventPublisher;

    @Scheduled(cron = "${app.appointment.slot-hold-reminder-cron:0 */10 * * * *}")
    @Transactional
    public void run() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.plusHours(2);
        for (AppointmentSlotHold hold : slotHoldRepository.findDueForReminder(now, cutoff)) {
            int updated = slotHoldRepository.markReminderQueued(hold.getId(), now);
            if (updated == 0) {
                continue;
            }
            String token = tokenService.issueToken(hold);
            mailEventPublisher.publishSlotHoldReminder(new SlotHoldReminderMailEvent(hold.getId(), token));
            log.info("slot_hold_reminder_queued holdId={} expiresAt={}", hold.getId(), hold.getExpiresAt());
        }
    }
}
