package com.PrimeCare.PrimeCare.modules.medication.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateMedicationRequest {

    @NotBlank
    @Size(max = 64)
    private String code;

    @NotBlank
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