package com.PrimeCare.PrimeCare.modules.billing.dto.response;

import com.PrimeCare.PrimeCare.shared.enums.InvoicePdfJobStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InvoicePdfJobResponse {
    private Long id;
    private Long invoiceId;
    private InvoicePdfJobStatus status;
    private String errorMessage;
    private String downloadUrl;
}