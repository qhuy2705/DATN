package com.PrimeCare.PrimeCare.modules.masterdata.doctor.mapper;

import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.dto.response.DoctorProfileResponse;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.service.DoctorOperationalGuardService;
import com.PrimeCare.PrimeCare.shared.enums.BranchStatus;
import com.PrimeCare.PrimeCare.shared.enums.DoctorStatus;

import java.util.stream.Collectors;

public class DoctorProfileMapper {

    public static DoctorProfileResponse toResponse(DoctorProfile d, User account) {
        DoctorProfileResponse r = new DoctorProfileResponse();
        r.setId(d.getId());
        r.setFullName(d.getFullName());
        r.setBranchId(d.getBranch().getId());
        r.setBranchNameVn(d.getBranch().getNameVn());
        r.setBranchNameEn(d.getBranch().getNameEn());

        r.setDisplayTitleVn(d.getDisplayTitleVn());
        r.setDisplayTitleEn(d.getDisplayTitleEn());
        r.setBioVn(d.getBioVn());
        r.setBioEn(d.getBioEn());
        r.setExpertiseVn(d.getExpertiseVn());
        r.setExpertiseEn(d.getExpertiseEn());
        r.setEducationVn(d.getEducationVn());
        r.setEducationEn(d.getEducationEn());
        r.setAchievementsVn(d.getAchievementsVn());
        r.setAchievementsEn(d.getAchievementsEn());
        r.setYearsExp(d.getYearsExp());
        r.setAvatarUrl(d.getAvatarUrl());
        r.setSlotMinutesOverride(d.getSlotMinutesOverride());

        DoctorStatus currentStatus = d.getStatus() != null ? d.getStatus() : DoctorStatus.ACTIVE;
        r.setStatus(currentStatus);

        if (currentStatus == DoctorStatus.INACTIVE) {
            r.setEffectiveStatus(DoctorStatus.INACTIVE);
            r.setInactiveReason("SELF_INACTIVE");
        } else if (d.getBranch() != null && d.getBranch().getStatus() != BranchStatus.ACTIVE) {
            r.setEffectiveStatus(DoctorStatus.INACTIVE);
            r.setInactiveReason("BRANCH_INACTIVE");
        } else {
            r.setEffectiveStatus(DoctorStatus.ACTIVE);
            r.setInactiveReason(null);
        }

        if (d.getDoctorSpecialties() != null) {
            r.setSpecialtyIds(
                    d.getDoctorSpecialties().stream()
                     .map(x -> x.getSpecialty().getId())
                     .collect(Collectors.toList())
            );

            d.getDoctorSpecialties().stream()
             .findFirst()
             .ifPresent(doctorSpecialty -> {
                 r.setSpecialtyNameVn(doctorSpecialty.getSpecialty().getNameVn());
                 r.setSpecialtyNameEn(doctorSpecialty.getSpecialty().getNameEn());
             });
        }

        r.setHasAccount(account != null);
        if (account != null) {
            r.setAccountId(account.getId());
            r.setAccountEmail(account.getEmail());
            r.setAccountPhone(account.getPhone());
            r.setAccountRole(account.getRole().name());
            r.setAccountStatus(account.getStatus().name());
        }

        var readiness = DoctorOperationalGuardService.evaluateReadiness(d, account);
        r.setOperationalReady(readiness.operationalReady());
        r.setBookable(readiness.operationalReady());
        r.setNotReadyReason(readiness.notReadyReason());
        return r;
    }
}
