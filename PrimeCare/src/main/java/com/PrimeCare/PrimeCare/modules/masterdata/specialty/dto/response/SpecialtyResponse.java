package com.PrimeCare.PrimeCare.modules.masterdata.specialty.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SpecialtyResponse {
    private Long id;
    private String code;
    private String nameVn;
    private String nameEn;
    private String descriptionVn;
    private String descriptionEn;
    private String iconUrl;
    private String status;
    private Integer defaultSlotMinutes;
    private Integer maxPerSession;
}
