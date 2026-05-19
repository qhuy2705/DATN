package com.PrimeCare.PrimeCare.modules.bookingemailotp.messaging;

import com.PrimeCare.PrimeCare.config.RabbitMqConfig;
import com.PrimeCare.PrimeCare.modules.bookingemailotp.entity.BookingEmailOtp;
import com.PrimeCare.PrimeCare.modules.bookingemailotp.messaging.event.BookingEmailOtpEmailEvent;
import com.PrimeCare.PrimeCare.modules.realtime.service.AfterCommitExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingEmailOtpEmailPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final AfterCommitExecutor afterCommitExecutor;

    public void publishAfterCommit(
            BookingEmailOtp otp,
            String rawOtp,
            int ttlSeconds,
            int resendCooldownSeconds
    ) {
        afterCommitExecutor.executeAndPropagate(() -> publish(toEvent(otp, rawOtp, ttlSeconds, resendCooldownSeconds)));
    }

    private void publish(BookingEmailOtpEmailEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMqConfig.BOOKING_EMAIL_OTP_EXCHANGE,
                    RabbitMqConfig.BOOKING_EMAIL_OTP_ROUTING_KEY,
                    event
            );
        } catch (RuntimeException ex) {
            log.error(
                    "Publish booking email OTP failed verificationId={} otpId={} toEmail={}",
                    event != null ? event.verificationId() : null,
                    event != null ? event.otpId() : null,
                    event != null ? event.maskedEmail() : null,
                    ex
            );
            throw ex;
        }
    }

    private BookingEmailOtpEmailEvent toEvent(
            BookingEmailOtp otp,
            String rawOtp,
            int ttlSeconds,
            int resendCooldownSeconds
    ) {
        if (otp == null) {
            throw new IllegalArgumentException("Booking email OTP entity is required");
        }
        return BookingEmailOtpEmailEvent.builder()
                .otpId(otp.getId())
                .verificationId(otp.getVerificationId())
                .toEmail(otp.getNormalizedEmail())
                .maskedEmail(maskEmail(otp.getNormalizedEmail()))
                .otpCode(rawOtp)
                .expiresAt(otp.getExpiresAt())
                .ttlSeconds(ttlSeconds)
                .resendCooldownSeconds(resendCooldownSeconds)
                .requestedAt(otp.getCreatedAt() != null
                        ? otp.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()
                        : Instant.now())
                .build();
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
