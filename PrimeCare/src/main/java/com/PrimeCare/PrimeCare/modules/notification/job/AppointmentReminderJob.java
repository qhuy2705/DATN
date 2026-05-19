package com.PrimeCare.PrimeCare.modules.notification.job;

import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.notification.entity.NotificationPreference;
import com.PrimeCare.PrimeCare.modules.notification.messaging.AppointmentMailEventPublisher;
import com.PrimeCare.PrimeCare.modules.notification.messaging.event.AppointmentReminderMailEvent;
import com.PrimeCare.PrimeCare.modules.notification.repository.NotificationPreferenceRepository;
import com.PrimeCare.PrimeCare.modules.notification.repository.NotificationRepository;
import com.PrimeCare.PrimeCare.modules.notification.service.InternalNotificationService;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.enums.NotificationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentReminderJob {

    static final String REMINDER_24H_TEMPLATE = "APPOINTMENT_REMINDER_24H";
    static final String REMINDER_6H_TEMPLATE = "APPOINTMENT_REMINDER_6H";
    static final String REMINDER_24H_KIND = "24H";
    static final String REMINDER_6H_KIND = "6H";

    private static final Duration REMINDER_24H_OFFSET = Duration.ofHours(24);
    private static final Duration REMINDER_6H_OFFSET = Duration.ofHours(6);
    private static final Duration REMINDER_24H_MIN_EFFECTIVE_LEAD = Duration.ofHours(30);
    private static final Duration REMINDER_6H_MIN_EFFECTIVE_LEAD = Duration.ofHours(8);
    private static final Duration CANCEL_DEADLINE = Duration.ofHours(4);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final AppointmentRepository appointmentRepository;
    private final NotificationRepository notificationRepository;
    private final AppointmentMailEventPublisher appointmentMailEventPublisher;
    private final InternalNotificationService internalNotificationService;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final UserRepository userRepository;

    @Value("${app.notification.appointment-reminder-scan-window-minutes:20}")
    private long scanWindowMinutes = 20L;

    @Scheduled(cron = "${app.notification.appointment-reminder-cron:0 */15 * * * *}")
    public void dispatchReminders() {
        dispatchReminders(LocalDateTime.now());
    }

    void dispatchReminders(LocalDateTime now) {
        LocalDateTime effectiveNow = now != null ? now : LocalDateTime.now();
        Duration scanWindow = Duration.ofMinutes(Math.max(1L, scanWindowMinutes));
        LocalDate from = effectiveNow.toLocalDate();
        LocalDate to = effectiveNow.plus(REMINDER_24H_OFFSET).plus(scanWindow).toLocalDate();

        appointmentRepository.findForEmailReminderCandidates(from, to, EnumSet.of(AppointmentStatus.CONFIRMED))
                .forEach(appointment -> dispatchCandidate(appointment, effectiveNow, scanWindow));
    }

    private void dispatchCandidate(Appointment appointment, LocalDateTime now, Duration scanWindow) {
        if (!isBaseEligible(appointment)) {
            return;
        }
        if (!isReminderPreferenceEnabled(appointment)) {
            return;
        }

        LocalDateTime appointmentStart = appointmentStart(appointment).orElse(null);
        if (appointmentStart == null) {
            log.debug("Skip APPOINTMENT_REMINDER email: missing appointmentStart id={}", appointment != null ? appointment.getId() : null);
            return;
        }

        tryDispatchReminder(
                appointment,
                appointmentStart,
                now,
                scanWindow,
                REMINDER_24H_OFFSET,
                REMINDER_24H_MIN_EFFECTIVE_LEAD,
                REMINDER_24H_TEMPLATE,
                REMINDER_24H_KIND
        );
        tryDispatchReminder(
                appointment,
                appointmentStart,
                now,
                scanWindow,
                REMINDER_6H_OFFSET,
                REMINDER_6H_MIN_EFFECTIVE_LEAD,
                REMINDER_6H_TEMPLATE,
                REMINDER_6H_KIND
        );
    }

    private boolean isBaseEligible(Appointment appointment) {
        if (appointment == null || appointment.getId() == null) {
            log.debug("Skip APPOINTMENT_REMINDER email: appointment/id missing");
            return false;
        }
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            log.debug("Skip APPOINTMENT_REMINDER email: status={} id={}", appointment.getStatus(), appointment.getId());
            return false;
        }
        if (isBlank(appointment.getPatientEmail())) {
            log.debug("Skip APPOINTMENT_REMINDER email: missing email id={}", appointment.getId());
            return false;
        }
        if (appointment.getVisitDate() == null) {
            log.debug("Skip APPOINTMENT_REMINDER email: missing visitDate id={}", appointment.getId());
            return false;
        }
        if (appointment.getEtaStart() == null) {
            log.debug("Skip APPOINTMENT_REMINDER email: missing etaStart id={}", appointment.getId());
            return false;
        }
        return true;
    }

    private boolean isReminderPreferenceEnabled(Appointment appointment) {
        if (appointment.getPatient() == null || appointment.getPatient().getId() == null) {
            return true;
        }

        return userRepository.findByPatient_Id(appointment.getPatient().getId())
                .flatMap(user -> notificationPreferenceRepository.findByUser_Id(user.getId()))
                .map(preference -> isPreferenceEnabled(appointment, preference))
                .orElse(true);
    }

    private boolean isPreferenceEnabled(Appointment appointment, NotificationPreference preference) {
        if (Boolean.FALSE.equals(preference.getAllowEmail())) {
            log.debug("Skip APPOINTMENT_REMINDER email: allowEmail=false id={}", appointment.getId());
            return false;
        }
        if (Boolean.FALSE.equals(preference.getAppointmentReminders())) {
            log.debug("Skip APPOINTMENT_REMINDER email: appointmentReminders=false id={}", appointment.getId());
            return false;
        }
        return true;
    }

    private void tryDispatchReminder(
            Appointment appointment,
            LocalDateTime appointmentStart,
            LocalDateTime now,
            Duration scanWindow,
            Duration offset,
            Duration minimumEffectiveLead,
            String templateCode,
            String reminderKind
    ) {
        LocalDateTime targetStart = now.plus(offset);
        LocalDateTime targetEnd = targetStart.plus(scanWindow);
        if (appointmentStart.isBefore(targetStart) || !appointmentStart.isBefore(targetEnd)) {
            log.debug("Skip {} email: outside window appointmentId={} appointmentStart={} targetStart={} targetEnd={}",
                    templateCode, appointment.getId(), appointmentStart, targetStart, targetEnd);
            return;
        }

        Duration effectiveLeadTime = Duration.between(effectiveReferenceTime(appointment, now), appointmentStart);
        if (effectiveLeadTime.compareTo(minimumEffectiveLead) < 0) {
            log.debug("Skip {} email: lead time too short appointmentId={} effectiveLeadMinutes={}",
                    templateCode, appointment.getId(), effectiveLeadTime.toMinutes());
            return;
        }

        boolean alreadyQueued = notificationRepository.existsByAppointment_IdAndTemplateCodeAndStatusIn(
                appointment.getId(),
                templateCode,
                EnumSet.of(NotificationStatus.PENDING, NotificationStatus.SENT)
        );
        if (alreadyQueued) {
            log.debug("Skip {} email: duplicate PENDING/SENT appointmentId={}", templateCode, appointment.getId());
            return;
        }

        publish(appointment, appointmentStart, templateCode, reminderKind);
    }

    private void publish(Appointment appointment, LocalDateTime appointmentStart, String templateCode, String reminderKind) {
        try {
            appointmentMailEventPublisher.publishReminder(new AppointmentReminderMailEvent(
                    appointment.getId(),
                    appointment.getCode(),
                    appointment.getPatientFullName(),
                    appointment.getPatientEmail(),
                    appointment.getDoctor() != null ? appointment.getDoctor().getFullName() : null,
                    appointment.getSpecialty() != null
                            ? firstNonBlank(appointment.getSpecialty().getNameVn(), appointment.getSpecialty().getNameEn())
                            : null,
                    appointment.getBranch() != null
                            ? firstNonBlank(appointment.getBranch().getNameVn(), appointment.getBranch().getNameEn())
                            : null,
                    appointment.getVisitDate() != null ? appointment.getVisitDate().toString() : null,
                    appointment.getEtaStart() != null ? appointment.getEtaStart().format(TIME_FORMATTER) : null,
                    appointment.getEtaEnd() != null ? appointment.getEtaEnd().format(TIME_FORMATTER) : null,
                    appointmentStart.minus(CANCEL_DEADLINE).format(DATE_TIME_FORMATTER),
                    templateCode,
                    reminderKind
            ));
        } catch (Exception ex) {
            log.warn("Could not publish appointment reminder {} for appointment {}: {}",
                    templateCode, appointment.getId(), ex.getMessage());
            internalNotificationService.notifyAdminRolesOnceRequiresNew(
                    "APPOINTMENT_REMINDER_PUBLISH_FAILED",
                    InternalNotificationService.SEVERITY_WARNING,
                    "Gửi nhắc lịch thất bại",
                    "Đưa job nhắc lịch vào hàng đợi thất bại.",
                    "/app/admin/dashboard",
                    "APPOINTMENT",
                    appointment.getId(),
                    Map.of(
                            "appointmentCode", appointment.getCode() != null ? appointment.getCode() : "",
                            "templateCode", templateCode,
                            "error", ex.getMessage() != null ? ex.getMessage() : ""
                    )
            );
        }
    }

    private Optional<LocalDateTime> appointmentStart(Appointment appointment) {
        if (appointment == null || appointment.getVisitDate() == null || appointment.getEtaStart() == null) {
            return Optional.empty();
        }
        return Optional.of(LocalDateTime.of(appointment.getVisitDate(), appointment.getEtaStart()));
    }

    private LocalDateTime effectiveReferenceTime(Appointment appointment, LocalDateTime fallback) {
        return max(appointment.getCreatedAt(), appointment.getConfirmedAt()).orElse(fallback);
    }

    private Optional<LocalDateTime> max(LocalDateTime first, LocalDateTime second) {
        if (first == null) {
            return Optional.ofNullable(second);
        }
        if (second == null) {
            return Optional.of(first);
        }
        return Optional.of(first.isAfter(second) ? first : second);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
