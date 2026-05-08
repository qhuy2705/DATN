package com.PrimeCare.PrimeCare.modules.masterdata.specialty.controller;

import com.PrimeCare.PrimeCare.modules.masterdata.specialty.dto.request.CreateBranchSpecialtyRequest;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.dto.request.UpdateBranchSpecialtyRequest;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.dto.response.BranchSpecialtyResponse;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.service.BranchSpecialtyService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import com.PrimeCare.PrimeCare.shared.enums.BranchSpecialtyStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/branch-specialties")
@RequiredArgsConstructor
public class AdminBranchSpecialtyController {

    private final BranchSpecialtyService branchSpecialtyService;

    @PostMapping
    @PreAuthorize("hasRole('OPERATIONS_ADMIN')")
    public ApiResponse<BranchSpecialtyResponse> create(@Valid @RequestBody CreateBranchSpecialtyRequest request) {
        return ApiResponse.ok(
                "Tạo cấu hình chuyên khoa cho chi nhánh thành công",
                branchSpecialtyService.create(request)
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OPERATIONS_ADMIN')")
    public ApiResponse<BranchSpecialtyResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBranchSpecialtyRequest request
    ) {
        return ApiResponse.ok(
                "Cập nhật cấu hình chuyên khoa cho chi nhánh thành công",
                branchSpecialtyService.update(id, request)
        );
    }

    @GetMapping
    @PreAuthorize("hasRole('OPERATIONS_ADMIN')")
    public ApiResponse<PageResponse<BranchSpecialtyResponse>> list(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) BranchSpecialtyStatus status,
            @PageableDefault(size = 10, sort = "displayOrder", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return ApiResponse.ok(
                "OK",
                branchSpecialtyService.listAdmin(branchId, q, status, pageable)
        );
    }
}