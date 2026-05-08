package com.PrimeCare.PrimeCare.modules.medication.dto.request;

import com.PrimeCare.PrimeCare.shared.enums.MedicationStatus;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateMedicationRequest {

    @Size(max = 255)
    private String name;

    @Size(max = 255)
    private String genericName;

    @Size(max = 128)
    private String strength;

    @Size(max = 128)
    private String dosageForm;

    @Size(max = 64)
    private String unit;

    @Size(max = 255)
    private String manufacturer;

    @Size(max = 4000)
    private String indicationNote;

    @Size(max = 4000)
    private String contraindicationNote;
}