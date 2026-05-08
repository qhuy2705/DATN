package com.PrimeCare.PrimeCare.modules.medication.controller;

import com.PrimeCare.PrimeCare.modules.medication.dto.response.MedicationResponse;
import com.PrimeCare.PrimeCare.modules.medication.service.MedicationService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/doctor/medications")
@RequiredArgsConstructor
public class DoctorMedicationController {

    private final MedicationService medicationService;

    @GetMapping
    @PreAuthorize("hasRole('DOCTOR')")
    public ApiResponse<PageResponse<MedicationResponse>> list(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 10, sort = "name", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return ApiResponse.ok("OK", medicationService.listActive(q, pageable));
    }
}