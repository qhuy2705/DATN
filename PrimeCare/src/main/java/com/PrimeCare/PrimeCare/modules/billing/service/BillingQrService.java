package com.PrimeCare.PrimeCare.modules.billing.service;

import com.PrimeCare.PrimeCare.modules.billing.entity.Invoice;
import com.PrimeCare.PrimeCare.modules.notification.service.QrCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class BillingQrService {

    private static final String VIETQR_GUID = "A000000727";
    private static final String SERVICE_CODE_TRANSFER_TO_ACCOUNT = "QRIBFTTA";
    private static final String CURRENCY_VND = "704";
    private static final String COUNTRY_CODE = "VN";
    private static final String DEFAULT_MERCHANT_CATEGORY_CODE = "8099";

    private final QrCodeService qrCodeService;

    @Value("${app.billing.qr.bank-code:}")
    private String bankCode;

    @Value("${app.billing.qr.account-no:}")
    private String accountNo;

    @Value("${app.billing.qr.account-name:}")
    private String accountName;

    @Value("${app.billing.qr.description-prefix:THANH TOAN HOA DON}")
    private String descriptionPrefix;

    @Value("${app.billing.qr.merchant-city:HA NOI}")
    private String merchantCity;

    @Value("${app.billing.qr.merchant-category-code:8099}")
    private String merchantCategoryCode;

    public byte[] generatePaymentQr(Invoice invoice) {
        if (invoice == null || invoice.getTotalAmount() == null || invoice.getTotalAmount() <= 0) {
            return null;
        }

        String content = buildVietQrPayload(invoice);
        if (content == null || content.isBlank()) {
            return null;
        }

        return qrCodeService.generatePng(content, 180, 180);
    }

    public String buildPaymentContent(Invoice invoice) {
        return buildTransferNote(invoice);
    }

    public String buildVietQrPayload(Invoice invoice) {
        if (invoice == null) {
            return null;
        }
        if (invoice.getTotalAmount() == null || invoice.getTotalAmount() <= 0) {
            return null;
        }

        String normalizedBankCode = digitsOnly(bankCode);
        String normalizedAccountNo = sanitizeAccountNo(accountNo);
        if (normalizedBankCode.isBlank() || normalizedAccountNo.isBlank()) {
            return null;
        }

        String beneficiaryInfo = tlv("00", normalizedBankCode) + tlv("01", normalizedAccountNo);
        String merchantAccountInfo = tlv("00", VIETQR_GUID)
                + tlv("01", beneficiaryInfo)
                + tlv("02", SERVICE_CODE_TRANSFER_TO_ACCOUNT);

        String transferNote = sanitizeAdditionalInfo(buildTransferNote(invoice));
        String merchantName = sanitizeMerchantText(accountName(), 25, "PRIME CARE");
        String city = sanitizeMerchantText(safe(merchantCity), 15, "HA NOI");
        String amount = toAmount(Math.toIntExact(invoice.getTotalAmount()));
        String mcc = normalizeMerchantCategoryCode();

        String payloadWithoutCrc = tlv("00", "01")
                + tlv("01", "12")
                + tlv("38", merchantAccountInfo)
                + tlv("52", mcc)
                + tlv("53", CURRENCY_VND)
                + tlv("54", amount)
                + tlv("58", COUNTRY_CODE)
                + tlv("59", merchantName)
                + tlv("60", city)
                + tlv("62", tlv("08", transferNote))
                + "6304";

        return payloadWithoutCrc + crc16CcittFalse(payloadWithoutCrc);
    }

    public String bankCode() {
        return safe(bankCode);
    }

    public String accountNo() {
        return safe(accountNo);
    }

    public String accountName() {
        return safe(accountName);
    }

    public String buildTransferNote(Invoice invoice) {
        if (invoice == null) {
            return null;
        }
        String reference = safe(invoice.getPaymentReference());
        if (!reference.isBlank()) {
            return ("HD " + reference).trim();
        }
        reference = safe(invoice.getCode());
        if (reference.isBlank()) {
            return null;
        }
        return (descriptionPrefix + " " + reference).trim();
    }

    private String normalizeMerchantCategoryCode() {
        String value = digitsOnly(merchantCategoryCode);
        if (value.length() != 4) {
            return DEFAULT_MERCHANT_CATEGORY_CODE;
        }
        return value;
    }

    private String toAmount(Integer totalAmount) {
        BigDecimal amount = BigDecimal.valueOf(totalAmount).setScale(0, RoundingMode.DOWN);
        return amount.toPlainString();
    }

    private String sanitizeAccountNo(String value) {
        return safe(value).replaceAll("[^A-Za-z0-9]", "");
    }

    private String sanitizeAdditionalInfo(String value) {
        String normalized = sanitizeText(value, true);
        if (normalized.length() > 25) {
            return normalized.substring(0, 25).trim();
        }
        return normalized;
    }

    private String sanitizeMerchantText(String value, int maxLen, String fallback) {
        String normalized = sanitizeText(value, true);
        if (normalized.isBlank()) {
            normalized = fallback;
        }
        if (normalized.length() > maxLen) {
            normalized = normalized.substring(0, maxLen).trim();
        }
        return normalized;
    }

    private String sanitizeText(String value, boolean uppercase) {
        String normalized = Normalizer.normalize(safe(value), Normalizer.Form.NFD)
                                      .replaceAll("\\p{M}", "")
                                      .replace('Đ', 'D')
                                      .replace('đ', 'd');
        normalized = normalized.replaceAll("[^A-Za-z0-9 /\\-._]", " ")
                               .replaceAll("\\s+", " ")
                               .trim();
        return uppercase ? normalized.toUpperCase(Locale.ROOT) : normalized;
    }

    private String digitsOnly(String value) {
        return safe(value).replaceAll("\\D", "");
    }

    private String tlv(String id, String value) {
        String safeValue = value == null ? "" : value;
        int length = safeValue.getBytes(StandardCharsets.UTF_8).length;
        return id + String.format(Locale.ROOT, "%02d", length) + safeValue;
    }

    private String crc16CcittFalse(String payload) {
        int crc = 0xFFFF;
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            crc ^= (b & 0xFF) << 8;
            for (int i = 0; i < 8; i++) {
                if ((crc & 0x8000) != 0) {
                    crc = (crc << 1) ^ 0x1021;
                } else {
                    crc <<= 1;
                }
                crc &= 0xFFFF;
            }
        }
        return String.format(Locale.ROOT, "%04X", crc);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
