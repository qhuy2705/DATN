package com.PrimeCare.PrimeCare.modules.masterdata.doctor.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties({"fullName", "yearsExp"})
public class UpdateMyDoctorProfileRequest {
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

    private String avatarUrl;
}
