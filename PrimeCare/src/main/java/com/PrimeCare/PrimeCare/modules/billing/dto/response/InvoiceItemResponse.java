package com.PrimeCare.PrimeCare.modules.billing.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class InvoiceItemResponse {
    private Long id;
    private String referenceType;
    private Long referenceId;
    private String nameSnapshot;
    private Long unitPrice;
    private Integer quantity;
    private BigDecimal taxRate;
    private Long subtotalAmount;
    private Long taxAmount;
    private Long totalAmount;
}
