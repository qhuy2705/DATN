package com.PrimeCare.PrimeCare.modules.staff.mapper;

import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.staff.dto.response.StaffProfileResponse;
import com.PrimeCare.PrimeCare.modules.staff.entity.StaffProfile;

public class StaffProfileMapper {

    public static StaffProfileResponse toResponse(StaffProfile s, User account) {
        StaffProfileResponse r = new StaffProfileResponse();
        r.setId(s.getId());
        r.setFullName(s.getFullName());
        if (s.getBranch() != null) {
            r.setBranchId(s.getBranch().getId());
            r.setBranchNameVn(s.getBranch().getNameVn());
            r.setBranchNameEn(s.getBranch().getNameEn());
        }
        r.setStatus(s.getStatus());
        r.setHasAccount(account != null);
        if (account != null) {
            r.setAccountId(account.getId());
            r.setAccountEmail(account.getEmail());
            r.setAccountPhone(account.getPhone());
            r.setAccountRole(account.getRole() != null ? account.getRole().name() : null);
            r.setAccountStatus(account.getStatus() != null ? account.getStatus().name() : null);
        }
        return r;
    }
}
