package com.PrimeCare.PrimeCare.modules.auth.controller;

import com.PrimeCare.PrimeCare.modules.auth.dto.request.ProvisionAccountRequest;
import com.PrimeCare.PrimeCare.modules.auth.dto.response.CreateUserResponse;
import com.PrimeCare.PrimeCare.modules.auth.dto.response.ResetPasswordResponse;
import com.PrimeCare.PrimeCare.modules.auth.service.AccountProvisionService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/doctors")
@RequiredArgsConstructor
public class AdminDoctorAccountController {

    private final AccountProvisionService accountProvisionService;

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @PostMapping("/{doctorId}/account")
    public ApiResponse<CreateUserResponse> provisionDoctorAccount(
            @PathVariable Long doctorId,
            @Valid @RequestBody ProvisionAccountRequest req
    ) {
        return ApiResponse.ok("Account created", accountProvisionService.provisionDoctorAccount(doctorId, req));
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @PostMapping("/{doctorId}/account/reset-password")
    public ApiResponse<ResetPasswordResponse> resetDoctorAccountPassword(@PathVariable Long doctorId) {
        return ApiResponse.ok("Password reset", accountProvisionService.resetDoctorAccountPassword(doctorId));
    }
}
