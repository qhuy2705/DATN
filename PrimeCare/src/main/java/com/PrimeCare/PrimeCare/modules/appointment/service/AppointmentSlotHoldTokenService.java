package com.PrimeCare.PrimeCare.modules.appointment.service;

import com.PrimeCare.PrimeCare.modules.appointment.entity.AppointmentSlotHold;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class AppointmentSlotHoldTokenService {

    private static final DateTimeFormatter TOKEN_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final String secret;

    public AppointmentSlotHoldTokenService(
            @Value("${app.reschedule.token-secret:${app.jwt.secret:dev_reschedule_secret_change_me_please_at_least_64_chars_long_1234567890}}") String secret
    ) {
        this.secret = secret;
    }

    public String issueToken(AppointmentSlotHold hold) {
        if (hold == null
                || hold.getOriginalAppointment() == null
                || hold.getOriginalAppointment().getId() == null
                || hold.getExpiresAt() == null
                || hold.getTokenNonce() == null
                || hold.getTokenNonce().isBlank()) {
            throw new IllegalArgumentException("Slot hold must have original appointment, expiry and nonce before issuing token");
        }
        String payload = hold.getOriginalAppointment().getId()
                + "." + hold.getTokenNonce()
                + "." + hold.getExpiresAt().format(TOKEN_TIME_FORMATTER);
        String encodedPayload = Base64.getUrlEncoder()
                                      .withoutPadding()
                                      .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + hmac(encodedPayload);
    }

    public String newNonce() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hashToken(String token) {
        String normalized = normalizeToken(token);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot hash slot hold token", ex);
        }
    }

    public void validateFormat(String token) {
        String normalized = normalizeToken(token);
        String[] parts = normalized.split("\\.");
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new ApiException(ErrorCode.PUBLIC_LOOKUP_TOKEN_INVALID);
        }
        String expectedSignature = hmac(parts[0]);
        if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8), parts[1].getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(ErrorCode.PUBLIC_LOOKUP_TOKEN_INVALID);
        }
    }

    private String normalizeToken(String token) {
        if (token == null || token.isBlank()) {
            throw new ApiException(ErrorCode.PUBLIC_LOOKUP_TOKEN_INVALID);
        }
        return token.trim();
    }

    private String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot sign slot hold token", ex);
        }
    }
}
