package com.PrimeCare.PrimeCare.modules.masterdata.doctor.dto.response;

import lombok.Getter;
import lombok.Setter;
import com.PrimeCare.PrimeCare.shared.enums.DoctorStatus;

import java.util.List;

@Getter
@Setter
public class DoctorProfileResponse {
    private Long id;
    private String fullName;
    private Long branchId;
    private String branchNameVn;
    private String branchNameEn;

    private String displayTitleVn;
    private String displayTitleEn;

    private String specialtyNameVn;
    private String specialtyNameEn;

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
    private DoctorStatus effectiveStatus;
    private String inactiveReason;
    private boolean bookable;
    private String nextAvailableDate;
    private boolean hasUpcomingSchedule;

    private List<Long> specialtyIds;

    private boolean hasAccount;
    private Long accountId;
    private String accountEmail;
    private String accountPhone;
    private String accountRole;
    private String accountStatus;
    private boolean operationalReady;
    private String notReadyReason;
}
