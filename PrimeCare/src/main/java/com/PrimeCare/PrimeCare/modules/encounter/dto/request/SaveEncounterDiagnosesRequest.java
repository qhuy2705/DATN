package com.PrimeCare.PrimeCare.modules.encounter.dto.request;

import com.PrimeCare.PrimeCare.modules.encounter.entity.EncounterDiagnosis;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class SaveEncounterDiagnosesRequest {

    @NotNull(message = "Loại chẩn đoán không được để trống")
    private EncounterDiagnosis.DiagnosisType diagnosisType;

    @NotNull(message = "Danh sách chẩn đoán không được để trống")
    @Valid
    private List<DiagnosisItem> items;

    @Data
    public static class DiagnosisItem {

        @NotNull(message = "Mã ICD-10 không được để trống")
        private Long icd10CodeId;

        @Size(max = 500, message = "Ghi chú không vượt quá 500 ký tự")
        private String note;

        private int displayOrder;
    }
}
