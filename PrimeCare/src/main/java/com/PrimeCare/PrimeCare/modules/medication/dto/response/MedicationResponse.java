package com.PrimeCare.PrimeCare.modules.medication.dto.response;

import com.PrimeCare.PrimeCare.shared.enums.MedicationStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MedicationResponse {
    private Long id;
    private String code;
    private String name;
    private String genericName;
    private String strength;
    private String dosageForm;
    private String unit;
    private String manufacturer;
    private String indicationNote;
    private String contraindicationNote;
    private MedicationStatus status;
}