package com.PrimeCare.PrimeCare.modules.medical_service.controller;

import com.PrimeCare.PrimeCare.modules.medical_service.dto.request.CreateMedicalServiceRequest;
import com.PrimeCare.PrimeCare.modules.medical_service.dto.request.UpdateMedicalServiceRequest;
import com.PrimeCare.PrimeCare.modules.medical_service.dto.request.UpdateMedicalServiceStatusRequest;
import com.PrimeCare.PrimeCare.modules.medical_service.dto.response.MedicalServiceResponse;
import com.PrimeCare.PrimeCare.modules.medical_service.service.MedicalServiceService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import com.PrimeCare.PrimeCare.shared.enums.MedicalServiceStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/medical-services")
@RequiredArgsConstructor
public class AdminMedicalServiceController {

    private final MedicalServiceService service;

    @GetMapping
    @PreAuthorize("hasRole('OPERATIONS_ADMIN')")
    public ApiResponse<PageResponse<MedicalServiceResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) MedicalServiceStatus status,
            @RequestParam(required = false) Boolean publicVisible,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.ok("OK", service.listAdmin(q, status, publicVisible, pageable));
    }

    @PostMapping
    @PreAuthorize("hasRole('OPERATIONS_ADMIN')")
    public ApiResponse<MedicalServiceResponse> create(@Valid @RequestBody CreateMedicalServiceRequest req) {
        return ApiResponse.ok("Tạo dịch vụ thành công", service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OPERATIONS_ADMIN')")
    public ApiResponse<MedicalServiceResponse> update(
            @PathVariable Long id,
            @RequestBody UpdateMedicalServiceRequest req
    ) {
        return ApiResponse.ok("Cập nhật dịch vụ thành công", service.update(id, req));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('OPERATIONS_ADMIN')")
    public ApiResponse<MedicalServiceResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateMedicalServiceStatusRequest req
    ) {
        return ApiResponse.ok("Cập nhật trạng thái dịch vụ thành công", service.updateStatus(id, req.getStatus()));
    }
}