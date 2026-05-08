package com.PrimeCare.PrimeCare.modules.patient_portal.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PatientStatusHistoryItemResponse {
    private Long id;
    private String fromStatus;
    private String toStatus;
    private String changedAt;
    private String changedBy;
    private String note;
}
