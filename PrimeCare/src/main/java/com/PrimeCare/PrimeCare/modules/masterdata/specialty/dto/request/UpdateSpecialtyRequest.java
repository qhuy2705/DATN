package com.PrimeCare.PrimeCare.modules.masterdata.specialty.dto.request;

import lombok.Data;

@Data
public class UpdateSpecialtyRequest {
    private String nameVn;
    private String nameEn;
    private String descriptionVn;
    private String descriptionEn;
    private String iconUrl;
    private String status;
    private Integer defaultSlotMinutes;
    private Integer maxPerSession;
}