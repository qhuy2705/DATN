package com.PrimeCare.PrimeCare.modules.encounter.dto.response;

import com.PrimeCare.PrimeCare.modules.encounter.entity.EncounterDiagnosis;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EncounterDiagnosisResponse {
    private Long id;
    private Long icd10CodeId;
    private String icd10Code;
    private String icd10NameVn;
    private String icd10NameEn;
    private EncounterDiagnosis.DiagnosisType diagnosisType;
    private String note;
    private int displayOrder;
}
