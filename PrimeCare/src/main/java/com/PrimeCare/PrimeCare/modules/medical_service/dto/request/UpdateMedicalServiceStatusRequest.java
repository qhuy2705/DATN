package com.PrimeCare.PrimeCare.modules.medical_service.dto.request;

import com.PrimeCare.PrimeCare.shared.enums.MedicalServiceStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateMedicalServiceStatusRequest {

    @NotNull
    private MedicalServiceStatus status;
}
