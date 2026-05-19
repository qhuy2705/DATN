package com.PrimeCare.PrimeCare.modules.billing.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RefundableInvoiceItemResponse {
    private Long invoiceItemId;
    private String sourceItemType;
    private Long sourceItemId;
    private String referenceType;
    private String name;
    private Integer quantity;
    private Long totalAmount;
    private Long alreadyRefundedAmount;
    private Long refundableAmount;
    private boolean refundable;
    private String notRefundableReason;
    private String currentStatus;
    private String resultStatus;
    private String group;
}
