package com.PrimeCare.PrimeCare.modules.patient.controller;

import com.PrimeCare.PrimeCare.config.PaginationConfig;
import com.PrimeCare.PrimeCare.modules.patient.dto.response.ReceptionPatientSearchResponse;
import com.PrimeCare.PrimeCare.modules.patient.service.PatientService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reception/patients")
@RequiredArgsConstructor
public class ReceptionPatientController {

    private final PatientService patientService;

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('OPERATIONS_ADMIN','STAFF')")
    public ApiResponse<PageResponse<ReceptionPatientSearchResponse>> search(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 10, sort = "fullName", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return ApiResponse.ok(
                "OK",
                patientService.searchForReception(q, PaginationConfig.withSort(pageable, Sort.by("fullName").ascending()))
        );
    }
}
