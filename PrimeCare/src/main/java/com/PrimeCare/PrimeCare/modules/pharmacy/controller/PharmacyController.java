package com.PrimeCare.PrimeCare.modules.pharmacy.controller;

import com.PrimeCare.PrimeCare.config.PaginationConfig;
import com.PrimeCare.PrimeCare.modules.prescription.dto.response.PrescriptionResponse;
import com.PrimeCare.PrimeCare.modules.prescription.service.PrescriptionService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import com.PrimeCare.PrimeCare.shared.enums.PrescriptionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pharmacy/prescriptions")
@RequiredArgsConstructor
public class PharmacyController {

    private final PrescriptionService prescriptionService;

    @GetMapping
    @PreAuthorize("hasAnyRole('PHARMACIST', 'OPERATIONS_ADMIN')")
    public ApiResponse<PageResponse<PrescriptionResponse>> getPendingPrescriptions(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) PrescriptionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(
                "OK",
                prescriptionService.listForPharmacy(
                        status,
                        q,
                        PaginationConfig.pageRequest(page, size, Sort.by("createdAt").descending())
                )
        );
    }

    @PostMapping("/{id}/dispense")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'OPERATIONS_ADMIN')")
    public ApiResponse<PrescriptionResponse> dispensePrescription(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ApiResponse.ok("Đã phát thuốc", prescriptionService.markAsDispensed(id, Long.valueOf(authentication.getName())));
    }
}
