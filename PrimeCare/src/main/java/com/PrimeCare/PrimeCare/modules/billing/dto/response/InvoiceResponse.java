package com.PrimeCare.PrimeCare.modules.billing.dto.response;

import com.PrimeCare.PrimeCare.shared.enums.PaymentMethod;
import com.PrimeCare.PrimeCare.shared.enums.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class InvoiceResponse {
    private Long id;
    private String code;
    private Long serviceOrderId;
    private String serviceOrderCode;
    private Long encounterId;
    private String patientName;
    private String doctorName;
    private String branchName;
    private Long subtotalAmount;
    private Long discountAmount;
    private Long taxAmount;
    private Long totalAmount;
    private java.util.List<InvoiceItemResponse> items;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
    private String paymentReference;
    private String transferContent;
    private String paymentReviewReason;
    private String bankCode;
    private String bankAccountNo;
    private String bankAccountName;
    private String qrPayload;
    private String qrCodeBase64;
    private String vnpTxnRef;
    private String vnpPaymentUrl;
    private LocalDateTime paymentDetectedAt;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
}
