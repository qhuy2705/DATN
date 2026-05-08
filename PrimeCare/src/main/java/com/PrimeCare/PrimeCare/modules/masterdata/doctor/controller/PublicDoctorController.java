package com.PrimeCare.PrimeCare.modules.masterdata.doctor.controller;

import com.PrimeCare.PrimeCare.modules.masterdata.doctor.dto.response.DoctorProfileResponse;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.service.PublicDoctorService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/doctors")
@RequiredArgsConstructor
public class PublicDoctorController {

    private final PublicDoctorService publicDoctorService;

    @GetMapping
    public ApiResponse<PageResponse<DoctorProfileResponse>> list(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long specialtyId,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 12, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.ok("OK", publicDoctorService.search(branchId, specialtyId, q, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<DoctorProfileResponse> get(@PathVariable Long id) {
        return ApiResponse.ok("OK", publicDoctorService.getById(id));
    }
}
