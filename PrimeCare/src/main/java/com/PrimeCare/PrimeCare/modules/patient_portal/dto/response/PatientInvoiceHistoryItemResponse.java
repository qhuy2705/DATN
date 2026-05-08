package com.PrimeCare.PrimeCare.modules.patient_portal.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PatientInvoiceHistoryItemResponse {
    private Long id;
    private String code;
    private String serviceOrderCode;
    private Long totalAmount;
    private String paymentStatus;
    private String paymentMethod;
    private String paidAt;
    private String createdAt;
    private Boolean canDownloadPdf;
}
