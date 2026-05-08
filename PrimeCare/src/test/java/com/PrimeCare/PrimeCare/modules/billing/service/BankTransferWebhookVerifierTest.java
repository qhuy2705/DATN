package com.PrimeCare.PrimeCare.modules.billing.service;

import com.PrimeCare.PrimeCare.modules.billing.dto.request.BankTransferTransactionRequest;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BankTransferWebhookVerifierTest {

    private static final String SECRET = "webhook-secret";

    private BankTransferWebhookVerifier verifier;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        verifier = new BankTransferWebhookVerifier(objectMapper, validator);
        ReflectionTestUtils.setField(verifier, "webhookSecret", SECRET);
        ReflectionTestUtils.setField(verifier, "allowedSkewSeconds", 300L);
    }

    @Test
    void verifyAcceptsValidSignedPayload() {
        String payload = """
                {
                  "transactionRef": "BANK-123",
                  "amount": 150000,
                  "provider": "VCB",
                  "transactionTime": "2026-04-26T10:15:30"
                }
                """;
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        BankTransferWebhookVerifier.VerifiedBankTransferWebhook verified =
                verifier.verify(payload, timestamp, "sha256=" + sign(timestamp, payload, SECRET));

        BankTransferTransactionRequest transaction = verified.transaction();
        assertThat(transaction.getTransactionRef()).isEqualTo("BANK-123");
        assertThat(transaction.getAmount()).isEqualTo(150000L);
    }

    @Test
    void verifyRejectsBadSignature() {
        String payload = validPayload();
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        assertThatThrownBy(() -> verifier.verify(payload, timestamp, sign(timestamp, payload, "wrong-secret")))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(ex.getMessage()).contains("không hợp lệ");
                });
    }

    @Test
    void verifyRejectsExpiredTimestamp() {
        ReflectionTestUtils.setField(verifier, "allowedSkewSeconds", 60L);
        String payload = validPayload();
        String timestamp = String.valueOf(Instant.now().minusSeconds(120).getEpochSecond());

        assertThatThrownBy(() -> verifier.verify(payload, timestamp, sign(timestamp, payload, SECRET)))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED)
                );
    }

    @Test
    void verifyReturnsValidationDetailsForInvalidPayload() {
        String payload = """
                {
                  "transactionRef": "",
                  "provider": "VCB"
                }
                """;
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        assertThatThrownBy(() -> verifier.verify(payload, timestamp, sign(timestamp, payload, SECRET)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(ex.getDetails()).containsKey("fields");
                });
    }

    private String validPayload() {
        return """
                {
                  "transactionRef": "BANK-123",
                  "amount": 150000
                }
                """;
    }

    private String sign(String timestamp, String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
            StringBuilder hash = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hash.append(String.format("%02x", b));
            }
            return hash.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
