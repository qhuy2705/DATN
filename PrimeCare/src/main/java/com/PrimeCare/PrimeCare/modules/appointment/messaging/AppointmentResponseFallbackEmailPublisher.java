package com.PrimeCare.PrimeCare.modules.appointment.messaging;

import com.PrimeCare.PrimeCare.config.RabbitMqConfig;
import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.entity.AppointmentResponseToken;
import com.PrimeCare.PrimeCare.modules.appointment.messaging.event.AppointmentResponseFallbackEmailEvent;
import com.PrimeCare.PrimeCare.modules.realtime.service.AfterCommitExecutor;
import com.PrimeCare.PrimeCare.shared.pdf.PdfFormatters;
import com.PrimeCare.PrimeCare.shared.utils.StringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentResponseFallbackEmailPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final AfterCommitExecutor afterCommitExecutor;

    @Value("${app.frontend.base-url:http://localhost:8081}")
    private String frontendBaseUrl;

    public void publishFallbackEmail(
            Appointment appointment,
            AppointmentResponseToken token,
            String rawToken
    ) {
        AppointmentResponseFallbackEmailEvent event = toEvent(appointment, token, rawToken);
        afterCommitExecutor.execute(() -> publish(event));
    }

    private void publish(AppointmentResponseFallbackEmailEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMqConfig.APPOINTMENT_RESPONSE_FALLBACK_EXCHANGE,
                    RabbitMqConfig.APPOINTMENT_RESPONSE_FALLBACK_EMAIL_ROUTING_KEY,
                    event
            );
        } catch (RuntimeException ex) {
            log.error(
                    "Publish appointment response fallback email failed appointmentId={} appointmentCode={} toEmail={}",
                    event != null ? event.appointmentId() : null,
                    event != null ? event.appointmentCode() : null,
                    event != null ? maskEmail(event.patientEmail()) : null,
                    ex
            );
            throw ex;
        }
    }

    private AppointmentResponseFallbackEmailEvent toEvent(
            Appointment appointment,
            AppointmentResponseToken token,
            String rawToken
    ) {
        if (appointment == null || appointment.getId() == null) {
            throw new IllegalArgumentException("Appointment is required for fallback email");
        }
        if (token == null || token.getExpiresAt() == null) {
            throw new IllegalArgumentException("Appointment response token is required for fallback email");
        }
        String patientEmail = StringUtil.trimToNull(appointment.getPatientEmail());
        if (patientEmail == null) {
            throw new IllegalArgumentException("Patient email is required for fallback email");
        }
        String tokenValue = StringUtil.trimToNull(rawToken);
        if (tokenValue == null) {
            throw new IllegalArgumentException("Raw response token is required for fallback email");
        }

        return AppointmentResponseFallbackEmailEvent.builder()
                .appointmentId(appointment.getId())
                .appointmentCode(appointment.getCode())
                .patientEmail(patientEmail)
                .patientName(appointment.getPatientFullName())
                .maskedPatientPhone(maskPhone(appointment.getPatientPhone()))
                .visitDate(appointment.getVisitDate())
                .etaStart(appointment.getEtaStart())
                .etaEnd(appointment.getEtaEnd())
                .responseUrl(responseUrl(tokenValue))
                .tokenExpiresAt(token.getExpiresAt())
                .branchName(appointment.getBranch() != null
                        ? PdfFormatters.firstNonBlank(appointment.getBranch().getNameVn(), appointment.getBranch().getNameEn())
                        : null)
                .branchHotline(appointment.getBranch() != null ? appointment.getBranch().getPhone() : null)
                .createdAt(Instant.now())
                .build();
    }

    private String responseUrl(String rawToken) {
        String base = frontendBaseUrl == null || frontendBaseUrl.isBlank()
                ? "http://localhost:8081"
                : frontendBaseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/appointment-response/" + rawToken;
    }

    private String maskPhone(String phone) {
        String value = StringUtil.trimToNull(phone);
        if (value == null || value.length() <= 4) {
            return value;
        }
        return "***" + value.substring(value.length() - 4);
    }

    private String maskEmail(String email) {
        String value = StringUtil.trimToNull(email);
        if (value == null) {
            return null;
        }
        int at = value.indexOf('@');
        if (at <= 1) {
            return "***" + (at >= 0 ? value.substring(at) : "");
        }
        return value.charAt(0) + "***" + value.substring(at);
    }
}
