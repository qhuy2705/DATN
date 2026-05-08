package com.PrimeCare.PrimeCare.modules.billing.service;

import com.PrimeCare.PrimeCare.modules.billing.dto.request.BankTransferTransactionRequest;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BankTransferWebhookVerifier {

    private final ObjectMapper objectMapper;
    private final Validator validator;

    @Value("${app.billing.bank-transfer.webhook.secret:}")
    private String webhookSecret;

    @Value("${app.billing.bank-transfer.webhook.allowed-skew-seconds:300}")
    private long allowedSkewSeconds;

    public VerifiedBankTransferWebhook verify(String payload, String timestamp, String signature) {
        verifySignature(payload, timestamp, signature);
        return new VerifiedBankTransferWebhook(parseAndValidate(payload));
    }

    private void verifySignature(String payload, String timestamp, String signature) {
        String secret = webhookSecret == null ? "" : webhookSecret.trim();
        if (secret.isBlank()) {
            throw unauthorized("Webhook ngân hàng chưa được cấu hình xác thực");
        }
        if (timestamp == null || timestamp.isBlank() || signature == null || signature.isBlank()) {
            throw unauthorized("Thiếu chữ ký webhook ngân hàng");
        }

        String trimmedTimestamp = timestamp.trim();
        Instant requestTime = parseTimestamp(trimmedTimestamp);
        long absoluteSkew = Math.abs(Duration.between(requestTime, Instant.now()).getSeconds());
        if (absoluteSkew > Math.max(0, allowedSkewSeconds)) {
            throw unauthorized("Webhook ngân hàng đã hết hạn hoặc chưa hợp lệ");
        }

        String signedPayload = trimmedTimestamp + "." + (payload == null ? "" : payload);
        String expectedSignature = hmacSha256(secret, signedPayload);
        if (!signatureMatches(expectedSignature, signature)) {
            throw unauthorized("Chữ ký webhook ngân hàng không hợp lệ");
        }
    }

    private Instant parseTimestamp(String value) {
        try {
            long raw = Long.parseLong(value);
            return raw > 10_000_000_000L ? Instant.ofEpochMilli(raw) : Instant.ofEpochSecond(raw);
        } catch (NumberFormatException ignored) {
            try {
                return Instant.parse(value);
            } catch (DateTimeParseException ex) {
                throw unauthorized("Timestamp webhook ngân hàng không hợp lệ");
            }
        }
    }

    private BankTransferTransactionRequest parseAndValidate(String payload) {
        BankTransferTransactionRequest req;
        try {
            req = objectMapper.readValue(payload, BankTransferTransactionRequest.class);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Payload webhook ngân hàng không hợp lệ");
        }

        Set<ConstraintViolation<BankTransferTransactionRequest>> violations = validator.validate(req);
        if (!violations.isEmpty()) {
            Map<String, String> fields = new LinkedHashMap<>();
            for (ConstraintViolation<BankTransferTransactionRequest> violation : violations) {
                fields.put(violation.getPropertyPath().toString(), violation.getMessage());
            }
            throw new ApiException(
                    ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.getDefaultMessage(),
                    Map.of("fields", fields)
            );
        }
        return req;
    }

    private String hmacSha256(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hash = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hash.append(String.format("%02x", b));
            }
            return hash.toString();
        } catch (Exception ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Không thể xác thực webhook ngân hàng");
        }
    }

    private boolean signatureMatches(String expectedSignature, String actualSignature) {
        String normalized = actualSignature.trim();
        if (normalized.regionMatches(true, 0, "sha256=", 0, "sha256=".length())) {
            normalized = normalized.substring("sha256=".length());
        }
        return MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.US_ASCII),
                normalized.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII)
        );
    }

    private ApiException unauthorized(String message) {
        return new ApiException(ErrorCode.UNAUTHORIZED, message);
    }

    public static final class VerifiedBankTransferWebhook {
        private final BankTransferTransactionRequest transaction;

        private VerifiedBankTransferWebhook(BankTransferTransactionRequest transaction) {
            this.transaction = transaction;
        }

        public BankTransferTransactionRequest transaction() {
            return transaction;
        }
    }
}
