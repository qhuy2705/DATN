package com.PrimeCare.PrimeCare.modules.prescription.dto.response;

import com.PrimeCare.PrimeCare.shared.enums.PrescriptionPdfJobStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PrescriptionPdfJobResponse {
    private Long id;
    private Long prescriptionId;
    private PrescriptionPdfJobStatus status;
    private String errorMessage;
    private String downloadUrl;
}