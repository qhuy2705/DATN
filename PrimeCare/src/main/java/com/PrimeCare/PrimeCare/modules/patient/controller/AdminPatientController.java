package com.PrimeCare.PrimeCare.modules.patient.controller;

import com.PrimeCare.PrimeCare.modules.patient.dto.request.CreatePatientRequest;
import com.PrimeCare.PrimeCare.modules.patient.dto.request.UpdatePatientRequest;
import com.PrimeCare.PrimeCare.modules.patient.dto.response.PatientResponse;
import com.PrimeCare.PrimeCare.modules.patient.service.PatientService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import com.PrimeCare.PrimeCare.shared.enums.PatientStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/patients")
@RequiredArgsConstructor
public class AdminPatientController {

    private final PatientService patientService;

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','OPERATIONS_ADMIN','STAFF')")
    @PostMapping
    public ApiResponse<PatientResponse> create(@Valid @RequestBody CreatePatientRequest req) {
        return ApiResponse.ok("Created", patientService.create(req));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','OPERATIONS_ADMIN','STAFF')")
    @PutMapping("/{id}")
    public ApiResponse<PatientResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody UpdatePatientRequest req) {
        return ApiResponse.ok("Updated", patientService.update(id, req));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','OPERATIONS_ADMIN','STAFF')")
    @GetMapping("/{id}")
    public ApiResponse<PatientResponse> get(@PathVariable Long id) {
        return ApiResponse.ok("OK", patientService.get(id));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','OPERATIONS_ADMIN','STAFF')")
    @GetMapping
    public ApiResponse<PageResponse<PatientResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) PatientStatus status,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.ok("OK", patientService.list(q, status, pageable));
    }
}