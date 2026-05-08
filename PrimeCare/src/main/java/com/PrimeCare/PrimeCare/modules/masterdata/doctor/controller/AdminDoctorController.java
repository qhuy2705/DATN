package com.PrimeCare.PrimeCare.modules.masterdata.doctor.controller;

import com.PrimeCare.PrimeCare.modules.masterdata.doctor.dto.request.CreateDoctorProfileRequest;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.dto.request.UpdateDoctorProfileRequest;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.dto.request.UpdateDoctorStatusRequest;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.dto.response.DoctorProfileResponse;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.service.DoctorAdminService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import com.PrimeCare.PrimeCare.shared.common.StatusSummaryResponse;
import com.PrimeCare.PrimeCare.shared.enums.DoctorStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/doctors")
@RequiredArgsConstructor
public class AdminDoctorController {

    private final DoctorAdminService doctorAdminService;

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @PostMapping
    public ApiResponse<DoctorProfileResponse> create(@Valid @RequestBody CreateDoctorProfileRequest req) {
        return ApiResponse.ok("Created", doctorAdminService.create(req));
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @PutMapping("/{id}")
    public ApiResponse<DoctorProfileResponse> update(@PathVariable Long id,
                                                     @Valid @RequestBody UpdateDoctorProfileRequest req) {
        return ApiResponse.ok("Updated", doctorAdminService.update(id, req));
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @PatchMapping("/{id}/status")
    public ApiResponse<DoctorProfileResponse> updateStatus(@PathVariable Long id,
                                                           @Valid @RequestBody UpdateDoctorStatusRequest req) {
        return ApiResponse.ok("Updated", doctorAdminService.updateStatus(id, req));
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @GetMapping("/{id}")
    public ApiResponse<DoctorProfileResponse> get(@PathVariable Long id) {
        return ApiResponse.ok("OK", doctorAdminService.get(id));
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @GetMapping
    public ApiResponse<PageResponse<DoctorProfileResponse>> list(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long specialtyId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) DoctorStatus status,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.ok("OK", doctorAdminService.list(branchId, specialtyId, q, status, pageable));
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @GetMapping("/summary")
    public ApiResponse<StatusSummaryResponse> summary(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long specialtyId,
            @RequestParam(required = false) String q
    ) {
        return ApiResponse.ok("OK", doctorAdminService.summary(branchId, specialtyId, q));
    }
}
