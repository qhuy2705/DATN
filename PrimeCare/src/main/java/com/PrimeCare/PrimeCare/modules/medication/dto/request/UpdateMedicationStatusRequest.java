package com.PrimeCare.PrimeCare.modules.medication.dto.request;

import com.PrimeCare.PrimeCare.shared.enums.MedicationStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateMedicationStatusRequest {

    @NotNull
    private MedicationStatus status;
}