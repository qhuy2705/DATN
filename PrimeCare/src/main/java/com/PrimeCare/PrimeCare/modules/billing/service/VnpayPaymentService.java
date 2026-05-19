package com.PrimeCare.PrimeCare.modules.billing.service;

import com.PrimeCare.PrimeCare.modules.billing.dto.response.VnpayPaymentInit;
import com.PrimeCare.PrimeCare.modules.billing.dto.response.VnpayVerificationResult;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;

@Service
public class VnpayPaymentService {

    private static final DateTimeFormatter VNP_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId VNP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String TXN_REF_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${app.vnpay.enabled:false}")
    private boolean enabled;

    @Value("${app.vnpay.tmnCode:}")
    private String tmnCode;

    @Value("${app.vnpay.hashSecret:}")
    private String hashSecret;

    @Value("${app.vnpay.payUrl:}")
    private String payUrl;

    @Value("${app.vnpay.returnUrl:}")
    private String backendReturnUrl;

    @Value("${app.vnpay.defaultFrontendReturnUrl:http://localhost:8081/app/cashier/invoices}")
    private String defaultFrontendReturnUrl;

    public VnpayPaymentInit init(String invoiceCode, Long amount, String frontendReturnUrl) {
        ensureInitConfigured();
        if (amount == null || amount <= 0) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Số tiền thanh toán VNPAY không hợp lệ");
        }

        ZonedDateTime now = ZonedDateTime.now(VNP_ZONE);
        String createDate = now.format(VNP_DATE_TIME);
        String txnRef = generateTxnRef(createDate);

        String effectiveFrontendReturnUrl = frontendReturnUrl != null && !frontendReturnUrl.isBlank()
                ? frontendReturnUrl.trim()
                : defaultFrontendReturnUrl;

        String returnUrl = UriComponentsBuilder.fromUriString(backendReturnUrl)
                                               .queryParam("frontendReturnUrl", effectiveFrontendReturnUrl)
                                               .build()
                                               .encode(StandardCharsets.UTF_8)
                                               .toUriString();

        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_Amount", String.valueOf(amount * 100));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_OrderInfo", "Thanh toan hoa don " + invoiceCode);
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr", resolveClientIp());
        params.put("vnp_CreateDate", createDate);
        params.put("vnp_ExpireDate", now.plusMinutes(15).format(VNP_DATE_TIME));

        String query = buildQuery(params);
        String secureHash = hmacSha512(hashSecret, query);
        String paymentUrl = payUrl + (payUrl.contains("?") ? "&" : "?") + query + "&vnp_SecureHash=" + secureHash;

        return new VnpayPaymentInit(txnRef, paymentUrl, returnUrl);
    }

    public VnpayVerificationResult verifyCallback(Map<String, String> requestParams) {
        ensureVerificationConfigured();

        Map<String, String> params = new TreeMap<>();
        requestParams.forEach((key, value) -> {
            if (key != null
                    && key.startsWith("vnp_")
                    && value != null
                    && !value.isBlank()
                    && !"vnp_SecureHash".equals(key)
                    && !"vnp_SecureHashType".equals(key)) {
                params.put(key, value);
            }
        });

        String expectedHash = hmacSha512(hashSecret, buildQuery(params));
        String actualHash = requestParams.get("vnp_SecureHash");
        boolean valid = actualHash != null && actualHash.equalsIgnoreCase(expectedHash);

        Long amount = null;
        try {
            String rawAmount = requestParams.get("vnp_Amount");
            if (rawAmount != null && !rawAmount.isBlank()) {
                amount = Long.parseLong(rawAmount) / 100L;
            }
        } catch (NumberFormatException ignored) {
            amount = null;
        }

        return new VnpayVerificationResult(
                valid,
                requestParams.get("vnp_TxnRef"),
                requestParams.get("vnp_ResponseCode"),
                requestParams.get("vnp_TransactionStatus"),
                requestParams.get("vnp_TransactionNo"),
                requestParams.get("vnp_PayDate"),
                requestParams.get("frontendReturnUrl"),
                amount,
                requestParams.get("vnp_OrderInfo")
        );
    }

    private void ensureInitConfigured() {
        if (!enabled) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "VNPAY chưa được cấu hình");
        }
        if (isBlank(tmnCode) || isBlank(hashSecret) || isBlank(payUrl) || isBlank(backendReturnUrl)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Thiếu cấu hình VNPAY");
        }
    }

    private void ensureVerificationConfigured() {
        if (!enabled) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "VNPAY chưa được cấu hình");
        }
        if (isBlank(hashSecret)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Thiếu cấu hình VNPAY");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String generateTxnRef(String createDate) {
        StringBuilder suffix = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            suffix.append(TXN_REF_ALPHABET.charAt(SECURE_RANDOM.nextInt(TXN_REF_ALPHABET.length())));
        }
        return "VNP" + createDate + suffix;
    }

    private String resolveClientIp() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return "127.0.0.1";
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null && !remoteAddr.isBlank() ? remoteAddr : "127.0.0.1";
    }

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }

    private String buildQuery(Map<String, String> params) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : new TreeMap<>(params).entrySet()) {
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(entry.getKey()).append('=');
            builder.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return builder.toString();
    }

    private String hmacSha512(String key, String data) {
        try {
            Mac hmac512 = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac512.init(secretKey);
            byte[] bytes = hmac512.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hash = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hash.append(String.format("%02x", b));
            }
            return hash.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể ký dữ liệu VNPAY", ex);
        }
    }
}
