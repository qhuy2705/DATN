package com.PrimeCare.PrimeCare.modules.patient_portal.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PatientResultHistoryItemResponse {
    private Long resultId;
    private Long serviceOrderItemId;
    private String serviceName;
    private String encounterCode;
    private String status;
    private String reportPdfStatus;
    private Boolean pdfReady;
    private String verifiedAt;
    private String performedAt;
}
