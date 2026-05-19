package com.PrimeCare.PrimeCare.modules.publiclookup.messaging;

import com.PrimeCare.PrimeCare.config.RabbitMqConfig;
import com.PrimeCare.PrimeCare.modules.notification.service.InternalNotificationService;
import com.PrimeCare.PrimeCare.modules.publiclookup.messaging.event.PublicLookupOtpEmailEvent;
import com.PrimeCare.PrimeCare.modules.publiclookup.service.PublicLookupMailService;
import com.PrimeCare.PrimeCare.shared.enums.PublicLookupType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PublicLookupOtpEmailConsumer {

    private final PublicLookupMailService mailService;
    private final InternalNotificationService internalNotificationService;

    @RabbitListener(queues = RabbitMqConfig.PUBLIC_LOOKUP_OTP_EMAIL_QUEUE)
    public void consume(PublicLookupOtpEmailEvent event) {
        try {
            validate(event);
            PublicLookupType lookupType = PublicLookupType.valueOf(event.lookupType());
            if (lookupType == PublicLookupType.APPOINTMENT) {
                mailService.sendAppointmentLookupOtp(
                        event.toEmail(),
                        event.patientName(),
                        event.referenceCode(),
                        event.otpCode(),
                        event.ttlSeconds(),
                        event.resendCooldownSeconds()
                );
                return;
            }
            if (lookupType == PublicLookupType.RESULT) {
                mailService.sendResultLookupOtp(
                        event.toEmail(),
                        event.patientName(),
                        event.referenceCode(),
                        event.otpCode(),
                        event.ttlSeconds(),
                        event.resendCooldownSeconds()
                );
                return;
            }
            throw new IllegalArgumentException("Unsupported public lookup type: " + event.lookupType());
        } catch (RuntimeException ex) {
            log.error(
                    "Send public lookup OTP email failed lookupType={} referenceCode={} toEmail={} error={}",
                    event != null ? event.lookupType() : null,
                    event != null ? event.referenceCode() : null,
                    event != null ? maskEmail(event.toEmail()) : null,
                    ex.getMessage(),
                    ex
            );
            notifyOperationsOtpFailed(event, ex);
            throw ex;
        }
    }

    private void notifyOperationsOtpFailed(PublicLookupOtpEmailEvent event, RuntimeException ex) {
        if (event == null) {
            return;
        }
        internalNotificationService.notifyAdminRolesOnceRequiresNew(
                "OTP_EMAIL_DELIVERY_FAILED",
                InternalNotificationService.SEVERITY_CRITICAL,
                "Gửi OTP email thất bại",
                "Gửi OTP tra cứu " + event.lookupType() + " cho mã " + event.referenceCode() + " thất bại.",
                "/app/admin/dashboard",
                "OTP",
                event.referenceId(),
                java.util.Map.of(
                        "lookupType", event.lookupType() != null ? event.lookupType() : "",
                        "referenceCode", event.referenceCode() != null ? event.referenceCode() : "",
                        "toEmail", event.toEmail() != null ? maskEmail(event.toEmail()) : "",
                        "error", ex.getMessage() != null ? ex.getMessage() : ""
                )
        );
    }

    private void validate(PublicLookupOtpEmailEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Public lookup OTP email event is null");
        }
        if (!hasText(event.lookupType())) {
            throw new IllegalArgumentException("Public lookup OTP email event missing lookupType");
        }
        if (!hasText(event.toEmail())) {
            throw new IllegalArgumentException("Public lookup OTP email event missing toEmail");
        }
        if (!hasText(event.otpCode())) {
            throw new IllegalArgumentException("Public lookup OTP email event missing otpCode");
        }
        if (event.ttlSeconds() == null || event.ttlSeconds() <= 0) {
            throw new IllegalArgumentException("Public lookup OTP email event has invalid ttlSeconds");
        }
        if (event.resendCooldownSeconds() == null || event.resendCooldownSeconds() < 0) {
            throw new IllegalArgumentException("Public lookup OTP email event has invalid resendCooldownSeconds");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(Math.max(0, at));
        }
        String prefix = email.substring(0, at);
        String domain = email.substring(at);
        if (prefix.length() <= 2) {
            return prefix.charAt(0) + "***" + domain;
        }
        return prefix.substring(0, 2) + "***" + domain;
    }
}
