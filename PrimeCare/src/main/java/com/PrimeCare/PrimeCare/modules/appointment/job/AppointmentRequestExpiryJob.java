package com.PrimeCare.PrimeCare.modules.appointment.job;

import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.appointment.service.AppointmentStatusHistoryService;
import com.PrimeCare.PrimeCare.modules.notification.service.InternalNotificationService;
import com.PrimeCare.PrimeCare.modules.realtime.service.RealtimeEventPublisher;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentRequestExpiryJob {

    private static final String AUTO_CANCEL_REASON = "Tự động hủy do quá hạn xác nhận";

    private final AppointmentRepository appointmentRepository;
    private final AppointmentStatusHistoryService appointmentStatusHistoryService;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final InternalNotificationService internalNotificationService;

    @Transactional
    @Scheduled(cron = "${app.appointment.request-expiry-cron:0 */15 * * * *}")
    public void expireRequestedAppointments() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(12);
        List<Appointment> expiredRequests = appointmentRepository.findByStatusAndCreatedAtBefore(AppointmentStatus.REQUESTED, cutoff);
        if (expiredRequests.isEmpty()) {
            return;
        }

        for (Appointment appointment : expiredRequests) {
            AppointmentStatus previousStatus = appointment.getStatus();
            appointment.setStatus(AppointmentStatus.CANCELLED);
            appointment.setCancelledAt(LocalDateTime.now());
            if (appointment.getCancelReason() == null || appointment.getCancelReason().isBlank()) {
                appointment.setCancelReason(AUTO_CANCEL_REASON);
            }
            appointment.setProcessingBy(null);
            appointment.setProcessingStartedAt(null);
            appointment.setProcessingExpiresAt(null);
            appointmentStatusHistoryService.record(appointment, previousStatus, appointment.getStatus(), null, appointment.getCancelReason());
        }
        appointmentRepository.saveAll(expiredRequests);
        for (Appointment appointment : expiredRequests) {
            publishRealtime(appointment, AppointmentStatus.REQUESTED);
            notifyAutoCancelled(appointment);
        }
        log.info("Auto-cancelled {} requested appointments that exceeded confirmation window", expiredRequests.size());
    }

    private void publishRealtime(Appointment appointment, AppointmentStatus previousStatus) {
        if (appointment.getBranch() == null || appointment.getVisitDate() == null) {
            return;
        }
        realtimeEventPublisher.publishAppointmentSummaryChanged(
                appointment.getBranch().getId(),
                appointment.getVisitDate()
        );
        realtimeEventPublisher.publishAppointmentUpdated(
                appointment.getId(),
                appointment.getBranch().getId(),
                appointment.getDoctor() != null ? appointment.getDoctor().getId() : null,
                appointment.getVisitDate(),
                appointment.getSession() != null ? appointment.getSession().name() : null,
                previousStatus != null ? previousStatus.name() : null,
                appointment.getStatus() != null ? appointment.getStatus().name() : null,
                appointment.getArrivalStatus() != null ? appointment.getArrivalStatus().name() : null,
                appointment.getQueueNo(),
                appointment.getReceptionQueueNo(),
                appointment.getArrivedAt() != null ? appointment.getArrivedAt().toString() : null,
                null,
                appointment.getCheckedInAt() != null ? appointment.getCheckedInAt().toString() : null,
                null,
                appointment.getConfirmedAt() != null ? appointment.getConfirmedAt().toString() : null,
                null
        );
    }

    private void notifyAutoCancelled(Appointment appointment) {
        String title = "Lịch hẹn quá hạn xác nhận";
        String message = "Lịch hẹn " + appointment.getCode() + " đã tự động hủy do quá hạn xác nhận.";
        internalNotificationService.notifyRoleOnce(
                UserRole.STAFF,
                "APPOINTMENT_REQUEST_AUTO_CANCELLED",
                InternalNotificationService.SEVERITY_WARNING,
                title,
                message,
                "/app/appointments/" + appointment.getId() + "/process",
                "APPOINTMENT",
                appointment.getId(),
                java.util.Map.of("appointmentCode", appointment.getCode())
        );
        internalNotificationService.notifyRoleOnce(
                UserRole.OPERATIONS_ADMIN,
                "APPOINTMENT_REQUEST_AUTO_CANCELLED",
                InternalNotificationService.SEVERITY_WARNING,
                title,
                message,
                "/app/appointments",
                "APPOINTMENT",
                appointment.getId(),
                java.util.Map.of("appointmentCode", appointment.getCode())
        );
    }
}
