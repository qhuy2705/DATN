package com.PrimeCare.PrimeCare.modules.auth.mapper;

import com.PrimeCare.PrimeCare.modules.auth.dto.response.CreateUserResponse;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;

public class UserMapper {
    public static CreateUserResponse toResponse(User u) {
        CreateUserResponse r = new CreateUserResponse();
        r.setId(u.getId());
        if (u.getDoctorProfile() != null) {
            r.setFullName(u.getDoctorProfile().getFullName());
            r.setDoctorProfileId(u.getDoctorProfile().getId());
        } else if (u.getStaffProfile() != null) {
            r.setFullName(u.getStaffProfile().getFullName());
            r.setStaffProfileId(u.getStaffProfile().getId());
        } else if (u.getAdminProfile() != null) {
            r.setFullName(u.getAdminProfile().getFullName());
        }
        r.setEmail(u.getEmail());
        r.setPhone(u.getPhone());
        r.setRole(u.getRole().name());
        r.setStatus(u.getStatus().name());
        return r;
    }
}
