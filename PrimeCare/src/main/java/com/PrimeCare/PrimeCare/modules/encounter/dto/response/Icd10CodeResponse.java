package com.PrimeCare.PrimeCare.modules.encounter.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Icd10CodeResponse {
    private Long id;
    private String code;
    private String nameVn;
    private String nameEn;
    private String category;
}
