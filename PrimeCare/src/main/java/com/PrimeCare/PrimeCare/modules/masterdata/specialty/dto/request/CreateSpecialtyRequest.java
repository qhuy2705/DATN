package com.PrimeCare.PrimeCare.modules.masterdata.specialty.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateSpecialtyRequest {

    @NotBlank
    private String code;

    @NotBlank
    private String nameVn;

    private String nameEn;
    private String descriptionVn;
    private String descriptionEn;
    private String iconUrl;

    @NotNull
    private Integer defaultSlotMinutes;

    private Integer maxPerSession;
}