package com.PrimeCare.PrimeCare.modules.masterdata.doctor.dto.request;

import com.PrimeCare.PrimeCare.shared.enums.DoctorStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreateDoctorProfileRequest {

    @NotBlank
    private String fullName;

    @NotNull
    private Long branchId;

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
    private Integer slotMinutesOverride;

    private DoctorStatus status;

    private List<Long> specialtyIds;
}