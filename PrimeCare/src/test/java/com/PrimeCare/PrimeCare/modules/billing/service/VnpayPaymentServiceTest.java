package com.PrimeCare.PrimeCare.modules.billing.service;

import com.PrimeCare.PrimeCare.modules.billing.dto.response.VnpayVerificationResult;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VnpayPaymentServiceTest {

    private static final String TMN_CODE = "7HM1AZZK";
    private static final String HASH_SECRET = "test-vnpay-secret";
    private static final String PAY_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    private static final String BACKEND_RETURN_URL = "http://localhost:8080/api/public/payments/vnpay/return";

    private VnpayPaymentService service;

    @BeforeEach
    void setUp() {
        service = new VnpayPaymentService();
        configure(true);
    }

    @Test
    void initBuildsSandboxPaymentUrlWithAmountTimesOneHundredAndNoSecureHashType() {
        var init = service.init("INV-1", 12345L, "http://localhost:8081/app/cashier/invoices");

        Map<String, String> query = parseQuery(URI.create(init.paymentUrl()).getRawQuery());
        assertThat(init.paymentUrl()).startsWith(PAY_URL + "?");
        assertThat(init.txnRef()).matches("VNP\\d{14}[A-Z0-9]{6}");
        assertThat(query).containsEntry("vnp_TmnCode", TMN_CODE);
        assertThat(query).containsEntry("vnp_Amount", "1234500");
        assertThat(query).containsEntry("vnp_OrderInfo", "Thanh toan hoa don INV-1");
        assertThat(query).containsKey("vnp_SecureHash");
        assertThat(query).doesNotContainKey("vnp_SecureHashType");
    }

    @Test
    void initRejectsWhenVnpayDisabled() {
        configure(false);

        assertThatThrownBy(() -> service.init("INV-1", 12345L, null))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("VNPAY chưa được cấu hình");
                });
    }

    @Test
    void verifyCallbackSucceedsWithValidSignatureAndIgnoresNonVnpParams() {
        Map<String, String> params = successfulCallbackParams();
        params.put("vnp_SecureHash", sign(params));
        params.put("frontendReturnUrl", "http://localhost:8081/app/cashier/invoices");

        VnpayVerificationResult result = service.verifyCallback(params);

        assertThat(result.validSignature()).isTrue();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.txnRef()).isEqualTo("TXN-1");
        assertThat(result.amount()).isEqualTo(12345L);
        assertThat(result.frontendReturnUrl()).isEqualTo("http://localhost:8081/app/cashier/invoices");
    }

    @Test
    void verifyCallbackMarksInvalidSignature() {
        Map<String, String> params = successfulCallbackParams();
        params.put("vnp_SecureHash", "bad-signature");

        VnpayVerificationResult result = service.verifyCallback(params);

        assertThat(result.validSignature()).isFalse();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.amount()).isEqualTo(12345L);
    }

    @Test
    void verifyCallbackRequiresResponseCodeAndTransactionStatusSuccess() {
        Map<String, String> params = successfulCallbackParams();
        params.put("vnp_TransactionStatus", "01");
        params.put("vnp_SecureHash", sign(params));

        VnpayVerificationResult result = service.verifyCallback(params);

        assertThat(result.validSignature()).isTrue();
        assertThat(result.responseCode()).isEqualTo("00");
        assertThat(result.transactionStatus()).isEqualTo("01");
        assertThat(result.isSuccess()).isFalse();
    }

    private void configure(boolean enabled) {
        ReflectionTestUtils.setField(service, "enabled", enabled);
        ReflectionTestUtils.setField(service, "tmnCode", TMN_CODE);
        ReflectionTestUtils.setField(service, "hashSecret", HASH_SECRET);
        ReflectionTestUtils.setField(service, "payUrl", PAY_URL);
        ReflectionTestUtils.setField(service, "backendReturnUrl", BACKEND_RETURN_URL);
        ReflectionTestUtils.setField(service, "defaultFrontendReturnUrl", "http://localhost:8081/app/cashier/invoices");
    }

    private Map<String, String> successfulCallbackParams() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_Amount", "1234500");
        params.put("vnp_OrderInfo", "Thanh toan hoa don INV-1");
        params.put("vnp_PayDate", "20260102030405");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TmnCode", TMN_CODE);
        params.put("vnp_TransactionNo", "14123456");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TxnRef", "TXN-1");
        return params;
    }

    private String sign(Map<String, String> params) {
        try {
            String data = buildQuery(params);
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(HASH_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hash = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hash.append(String.format("%02x", b));
            }
            return hash.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String buildQuery(Map<String, String> params) {
        StringBuilder builder = new StringBuilder();
        new TreeMap<>(params).forEach((key, value) -> {
            if (key.startsWith("vnp_")
                    && !"vnp_SecureHash".equals(key)
                    && !"vnp_SecureHashType".equals(key)
                    && value != null
                    && !value.isBlank()) {
                if (builder.length() > 0) {
                    builder.append('&');
                }
                builder.append(key)
                        .append('=')
                        .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
            }
        });
        return builder.toString();
    }

    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : rawQuery.split("&")) {
            String[] parts = pair.split("=", 2);
            result.put(
                    URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : ""
            );
        }
        return result;
    }
}
