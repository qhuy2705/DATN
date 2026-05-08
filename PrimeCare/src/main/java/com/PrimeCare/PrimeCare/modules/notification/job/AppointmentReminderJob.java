package com.PrimeCare.PrimeCare.modules.notification.job;

import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.notification.messaging.AppointmentMailEventPublisher;
import com.PrimeCare.PrimeCare.modules.notification.messaging.event.AppointmentReminderMailEvent;
import com.PrimeCare.PrimeCare.modules.notification.repository.NotificationRepository;
import com.PrimeCare.PrimeCare.modules.notification.service.InternalNotificationService;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.enums.NotificationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentReminderJob {

    private static final String TEMPLATE_CODE = "APPOINTMENT_REMINDER";

    private final AppointmentRepository appointmentRepository;
    private final NotificationRepository notificationRepository;
    private final AppointmentMailEventPublisher appointmentMailEventPublisher;
    private final InternalNotificationService internalNotificationService;

    @Scheduled(cron = "${app.notification.appointment-reminder-cron:0 0 * * * *}")
    public void dispatchReminders() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);

        appointmentRepository.findForReminder(today, tomorrow, EnumSet.of(AppointmentStatus.CONFIRMED))
                .forEach(appointment -> {
                    boolean alreadyQueued = notificationRepository.existsByAppointment_IdAndTemplateCodeAndStatusIn(
                            appointment.getId(),
                            TEMPLATE_CODE,
                            EnumSet.of(NotificationStatus.PENDING, NotificationStatus.SENT)
                    );
                    if (alreadyQueued) {
                        return;
                    }

                    try {
                        appointmentMailEventPublisher.publishReminder(new AppointmentReminderMailEvent(
                                appointment.getId(),
                                appointment.getCode(),
                                appointment.getPatientFullName(),
                                appointment.getPatientEmail(),
                                appointment.getDoctor() != null ? appointment.getDoctor().getFullName() : null,
                                appointment.getBranch() != null ? appointment.getBranch().getNameVn() : null,
                                appointment.getVisitDate() != null ? appointment.getVisitDate().toString() : null,
                                appointment.getEtaStart() != null ? appointment.getEtaStart().toString() : null,
                                appointment.getEtaEnd() != null ? appointment.getEtaEnd().toString() : null
                        ));
                    } catch (Exception ex) {
                        log.warn("Could not publish appointment reminder for appointment {}: {}", appointment.getId(), ex.getMessage());
                        internalNotificationService.notifyAdminRolesOnceRequiresNew(
                                "APPOINTMENT_REMINDER_PUBLISH_FAILED",
                                InternalNotificationService.SEVERITY_WARNING,
                                "Gửi nhắc lịch thất bại",
                                "Đưa job nhắc lịch vào hàng đợi thất bại.",
                                "/app/admin/notifications",
                                "APPOINTMENT",
                                appointment.getId(),
                                Map.of(
                                        "appointmentCode", appointment.getCode() != null ? appointment.getCode() : "",
                                        "error", ex.getMessage() != null ? ex.getMessage() : ""
                                )
                        );
                    }
                });
    }
}
