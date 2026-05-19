package com.PrimeCare.PrimeCare.modules.appointment.job;

import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.notification.service.InternalNotificationService;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentOverdueNotificationJob {

    private final AppointmentRepository appointmentRepository;
    private final InternalNotificationService internalNotificationService;

    @Transactional
    @Scheduled(cron = "${app.appointment.overdue-scan-cron:0 */5 * * * *}")
    public void notifyOverdueConfirmedAppointments() {
        LocalDate today = LocalDate.now();
        LocalTime cutoffTime = overdueCutoffTime(LocalDateTime.now(), today);
        if (LocalTime.MIN.equals(cutoffTime)) {
            return;
        }

        List<Appointment> overdueAppointments = appointmentRepository.findOverdueConfirmedForNotification(today, cutoffTime);
        if (overdueAppointments.isEmpty()) {
            return;
        }

        for (Appointment appointment : overdueAppointments) {
            notifyStaff(appointment);
        }
        log.info("Checked {} overdue confirmed appointments for staff notifications", overdueAppointments.size());
    }

    private void notifyStaff(Appointment appointment) {
        String message = "Lịch hẹn " + appointment.getCode() + " của bệnh nhân "
                + appointment.getPatientFullName() + " đã hết khung giờ hẹn nhưng chưa check-in.";
        internalNotificationService.notifyRoleOnce(
                UserRole.STAFF,
                "APPOINTMENT_OVERDUE_CHECK_IN",
                InternalNotificationService.SEVERITY_WARNING,
                "Lịch hẹn đã quá giờ chưa check-in",
                message,
                "/app/appointments/" + appointment.getId() + "/process",
                "APPOINTMENT",
                appointment.getId(),
                Map.of(
                        "appointmentCode", appointment.getCode(),
                        "visitDate", appointment.getVisitDate() != null ? appointment.getVisitDate().toString() : "",
                        "etaStart", appointment.getEtaStart() != null ? appointment.getEtaStart().toString() : "",
                        "etaEnd", appointment.getEtaEnd() != null ? appointment.getEtaEnd().toString() : ""
                )
        );
    }

    private LocalTime overdueCutoffTime(LocalDateTime now, LocalDate today) {
        if (!now.toLocalDate().equals(today)) {
            return LocalTime.MIN;
        }
        return now.toLocalTime();
    }
}
