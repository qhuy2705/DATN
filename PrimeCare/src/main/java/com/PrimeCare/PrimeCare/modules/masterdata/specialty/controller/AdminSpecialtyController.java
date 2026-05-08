package com.PrimeCare.PrimeCare.modules.masterdata.specialty.controller;

import com.PrimeCare.PrimeCare.modules.masterdata.specialty.dto.request.CreateSpecialtyRequest;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.dto.request.UpdateSpecialtyRequest;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.dto.request.UpdateSpecialtyStatusRequest;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.dto.response.SpecialtyResponse;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.service.SpecialtyService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/specialties")
@RequiredArgsConstructor
public class AdminSpecialtyController {

    private final SpecialtyService specialtyService;

    @GetMapping
    @PreAuthorize("hasRole('OPERATIONS_ADMIN')")
    public ApiResponse<PageResponse<SpecialtyResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.ok("OK", specialtyService.listAdmin(q, status, pageable));
    }

    @PostMapping
    @PreAuthorize("hasRole('OPERATIONS_ADMIN')")
    public ApiResponse<SpecialtyResponse> create(@Valid @RequestBody CreateSpecialtyRequest request) {
        return ApiResponse.ok("Tạo chuyên khoa thành công", specialtyService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OPERATIONS_ADMIN')")
    public ApiResponse<SpecialtyResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSpecialtyRequest request
    ) {
        return ApiResponse.ok("Cập nhật chuyên khoa thành công", specialtyService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('OPERATIONS_ADMIN')")
    public ApiResponse<SpecialtyResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSpecialtyStatusRequest request
    ) {
        return ApiResponse.ok("Cập nhật trạng thái chuyên khoa thành công", specialtyService.updateStatus(id, request.getStatus()));
    }
}