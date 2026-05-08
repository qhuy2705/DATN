package com.PrimeCare.PrimeCare.modules.publiclookup.messaging.event;

import lombok.Builder;

import java.time.Instant;

@Builder
public record PublicLookupOtpEmailEvent(
        String lookupType,
        Long referenceId,
        String referenceCode,
        String patientName,
        String toEmail,
        String otpCode,
        Integer ttlSeconds,
        Integer resendCooldownSeconds,
        Instant requestedAt
) {
}
