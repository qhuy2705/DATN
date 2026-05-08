package com.PrimeCare.PrimeCare.modules.medication.controller;

import com.PrimeCare.PrimeCare.modules.medication.dto.request.CreateMedicationRequest;
import com.PrimeCare.PrimeCare.modules.medication.dto.request.UpdateMedicationRequest;
import com.PrimeCare.PrimeCare.modules.medication.dto.response.MedicationResponse;
import com.PrimeCare.PrimeCare.modules.medication.service.MedicationService;
import com.PrimeCare.PrimeCare.modules.medication.dto.request.UpdateMedicationStatusRequest;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import com.PrimeCare.PrimeCare.shared.enums.MedicationStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/medications")
@RequiredArgsConstructor
public class AdminMedicationController {

    private final MedicationService medicationService;

    @GetMapping
    @PreAuthorize("hasRole('OPERATIONS_ADMIN')")
    public ApiResponse<PageResponse<MedicationResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) MedicationStatus status,
            @PageableDefault(size = 10, sort = "name", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return ApiResponse.ok("OK", medicationService.listAdmin(q, status, pageable));
    }

    @PostMapping
    @PreAuthorize("hasRole('OPERATIONS_ADMIN')")
    public ApiResponse<MedicationResponse> create(@Valid @RequestBody CreateMedicationRequest req) {
        return ApiResponse.ok("Tạo thuốc thành công", medicationService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OPERATIONS_ADMIN')")
    public ApiResponse<MedicationResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateMedicationRequest req
    ) {
        return ApiResponse.ok("Cập nhật thuốc thành công", medicationService.update(id, req));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('OPERATIONS_ADMIN')")
    public ApiResponse<MedicationResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateMedicationStatusRequest req
    ) {
        return ApiResponse.ok("Cập nhật trạng thái thuốc thành công", medicationService.updateStatus(id, req));
    }
}