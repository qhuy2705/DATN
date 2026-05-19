package com.PrimeCare.PrimeCare.modules.bookingemailotp.messaging;

import com.PrimeCare.PrimeCare.config.RabbitMqConfig;
import com.PrimeCare.PrimeCare.modules.bookingemailotp.messaging.event.BookingEmailOtpEmailEvent;
import com.PrimeCare.PrimeCare.modules.bookingemailotp.service.BookingEmailOtpMailService;
import com.PrimeCare.PrimeCare.modules.notification.service.EmailNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEmailOtpEmailConsumer {

    private static final String SUBJECT = "Mã xác thực đặt lịch PrimeCare";
    private static final String TEMPLATE_CODE = "BOOKING_EMAIL_OTP";

    private final BookingEmailOtpMailService mailService;
    private final EmailNotificationService emailNotificationService;

    @RabbitListener(queues = RabbitMqConfig.BOOKING_EMAIL_OTP_QUEUE)
    public void consume(BookingEmailOtpEmailEvent event) {
        try {
            validate(event);
            String html = mailService.renderBookingEmailVerificationOtp(
                    event.toEmail(),
                    event.otpCode(),
                    event.ttlSeconds(),
                    event.resendCooldownSeconds()
            );
            emailNotificationService.sendHtmlEmail(
                    null,
                    event.toEmail(),
                    SUBJECT,
                    html,
                    TEMPLATE_CODE,
                    null,
                    null
            );
        } catch (RuntimeException ex) {
            log.error(
                    "Send booking email OTP failed verificationId={} otpId={} toEmail={} error={}",
                    event != null ? event.verificationId() : null,
                    event != null ? event.otpId() : null,
                    event != null ? event.maskedEmail() : null,
                    ex.getMessage(),
                    ex
            );
            throw ex;
        }
    }

    private void validate(BookingEmailOtpEmailEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Booking email OTP event is null");
        }
        if (!hasText(event.toEmail())) {
            throw new IllegalArgumentException("Booking email OTP event missing toEmail");
        }
        if (!hasText(event.otpCode())) {
            throw new IllegalArgumentException("Booking email OTP event missing otpCode");
        }
        if (event.ttlSeconds() == null || event.ttlSeconds() <= 0) {
            throw new IllegalArgumentException("Booking email OTP event has invalid ttlSeconds");
        }
        if (event.resendCooldownSeconds() == null || event.resendCooldownSeconds() < 0) {
            throw new IllegalArgumentException("Booking email OTP event has invalid resendCooldownSeconds");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
