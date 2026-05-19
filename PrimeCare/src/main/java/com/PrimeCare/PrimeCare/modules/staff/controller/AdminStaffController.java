package com.PrimeCare.PrimeCare.modules.staff.controller;

import com.PrimeCare.PrimeCare.modules.staff.dto.request.CreateStaffProfileRequest;
import com.PrimeCare.PrimeCare.modules.staff.dto.request.UpdateStaffProfileRequest;
import com.PrimeCare.PrimeCare.modules.staff.dto.request.UpdateStaffStatusRequest;
import com.PrimeCare.PrimeCare.modules.staff.dto.response.StaffAdminSummaryResponse;
import com.PrimeCare.PrimeCare.modules.staff.dto.response.StaffProfileResponse;
import com.PrimeCare.PrimeCare.modules.staff.service.StaffAdminService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import com.PrimeCare.PrimeCare.shared.enums.StaffStatus;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/staffs")
@RequiredArgsConstructor
public class AdminStaffController {

    private final StaffAdminService staffAdminService;

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @PostMapping
    public ApiResponse<StaffProfileResponse> create(@Valid @RequestBody CreateStaffProfileRequest req) {
        return ApiResponse.ok("Created", staffAdminService.create(req));
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @PutMapping("/{id}")
    public ApiResponse<StaffProfileResponse> update(@PathVariable Long id,
                                                    @Valid @RequestBody UpdateStaffProfileRequest req) {
        return ApiResponse.ok("Updated", staffAdminService.update(id, req));
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @GetMapping("/{id}")
    public ApiResponse<StaffProfileResponse> get(@PathVariable Long id) {
        return ApiResponse.ok("OK", staffAdminService.get(id));
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @GetMapping
    public ApiResponse<PageResponse<StaffProfileResponse>> list(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) StaffStatus status,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.ok("OK", staffAdminService.list(branchId, q, status, role, pageable));
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @GetMapping("/summary")
    public ApiResponse<StaffAdminSummaryResponse> summary(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) StaffStatus status
    ) {
        return ApiResponse.ok("OK", staffAdminService.summary(branchId, q, status, role));
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @PatchMapping("/{id}/status")
    public ApiResponse<StaffProfileResponse> updateStatus(@PathVariable Long id,
                                                          @Valid @RequestBody UpdateStaffStatusRequest req) {
        return ApiResponse.ok("Updated", staffAdminService.updateStatus(id, req));
    }
}
