package com.PrimeCare.PrimeCare.modules.publiclookup.service;

import com.PrimeCare.PrimeCare.shared.enums.NotificationChannel;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PublicLookupOtpDeliveryService {

    public DeliveryTarget resolveEmail(String email, String missingMessage) {
        if (email != null && !email.isBlank()) {
            return new DeliveryTarget(email.trim(), NotificationChannel.EMAIL);
        }
        throw new ApiException(ErrorCode.INVALID_REQUEST, missingMessage);
    }

    public String mask(DeliveryTarget target) {
        return maskEmail(target.value());
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***" + email.substring(Math.max(0, at));
        String prefix = email.substring(0, at);
        String domain = email.substring(at);
        if (prefix.length() <= 2) return prefix.charAt(0) + "***" + domain;
        return prefix.substring(0, 2) + "***" + domain;
    }

    public record DeliveryTarget(String value, NotificationChannel channel) {
    }
}
