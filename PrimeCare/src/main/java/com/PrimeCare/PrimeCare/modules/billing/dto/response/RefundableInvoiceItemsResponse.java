package com.PrimeCare.PrimeCare.modules.billing.dto.response;

import com.PrimeCare.PrimeCare.shared.enums.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RefundableInvoiceItemsResponse {
    private Long invoiceId;
    private String invoiceCode;
    private PaymentStatus paymentStatus;
    private Long totalAmount;
    private Long refundedAmount;
    private Long remainingAmount;
    private List<RefundableInvoiceItemResponse> items;
}
