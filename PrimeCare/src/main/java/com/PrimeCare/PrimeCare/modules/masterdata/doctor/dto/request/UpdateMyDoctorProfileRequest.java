package com.PrimeCare.PrimeCare.modules.masterdata.doctor.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateMyDoctorProfileRequest {
    @NotBlank
    private String fullName;

    private String displayTitleVn;
    private String displayTitleEn;

    private String bioVn;
    private String bioEn;

    private String expertiseVn;
    private String expertiseEn;

    private String educationVn;
    private String educationEn;

    private String achievementsVn;
    private String achievementsEn;

    private Integer yearsExp;
    private String avatarUrl;
}
