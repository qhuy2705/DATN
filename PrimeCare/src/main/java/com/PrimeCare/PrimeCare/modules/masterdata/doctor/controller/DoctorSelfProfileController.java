package com.PrimeCare.PrimeCare.modules.masterdata.doctor.controller;

import com.PrimeCare.PrimeCare.modules.masterdata.doctor.dto.request.UpdateMyDoctorProfileRequest;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.dto.response.DoctorProfileResponse;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.service.DoctorSelfProfileService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/doctor/profile")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DOCTOR')")
public class DoctorSelfProfileController {

    private final DoctorSelfProfileService doctorSelfProfileService;

    @GetMapping
    public ApiResponse<DoctorProfileResponse> getMyProfile(Authentication authentication) {
        Long userId = Long.valueOf(authentication.getName());
        return ApiResponse.ok("OK", doctorSelfProfileService.getMyProfile(userId));
    }

    @PutMapping
    public ApiResponse<DoctorProfileResponse> updateMyProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateMyDoctorProfileRequest req
    ) {
        Long userId = Long.valueOf(authentication.getName());
        return ApiResponse.ok("Updated", doctorSelfProfileService.updateMyProfile(userId, req));
    }
}
