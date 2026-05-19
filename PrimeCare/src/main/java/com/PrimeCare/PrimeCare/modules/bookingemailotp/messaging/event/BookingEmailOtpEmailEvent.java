package com.PrimeCare.PrimeCare.modules.bookingemailotp.messaging.event;

import lombok.Builder;

import java.time.Instant;
import java.time.LocalDateTime;

@Builder
public record BookingEmailOtpEmailEvent(
        Long otpId,
        String verificationId,
        String toEmail,
        String maskedEmail,
        String otpCode,
        LocalDateTime expiresAt,
        Integer ttlSeconds,
        Integer resendCooldownSeconds,
        Instant requestedAt
) {
}
