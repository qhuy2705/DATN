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
@RequestMapping("/api/admin/staffs")
@RequiredArgsConstructor
public class AdminStaffAccountController {

    private final AccountProvisionService accountProvisionService;

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @PostMapping("/{staffId}/account")
    public ApiResponse<CreateUserResponse> provisionStaffAccount(
            @PathVariable Long staffId,
            @Valid @RequestBody ProvisionAccountRequest req
    ) {
        return ApiResponse.ok("Account created", accountProvisionService.provisionStaffAccount(staffId, req));
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @PostMapping("/{staffId}/account/reset-password")
    public ApiResponse<ResetPasswordResponse> resetStaffAccountPassword(@PathVariable Long staffId) {
        return ApiResponse.ok("Password reset", accountProvisionService.resetStaffAccountPassword(staffId));
    }
}
