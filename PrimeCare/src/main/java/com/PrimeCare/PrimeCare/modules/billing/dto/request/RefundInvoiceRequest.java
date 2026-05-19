package com.PrimeCare.PrimeCare.modules.billing.dto.request;

import lombok.Data;

@Data
public class RefundInvoiceRequest {
    private Long refundAmount;
    private String reason;
}
