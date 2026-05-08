package com.PrimeCare.PrimeCare.modules.masterdata.specialty.controller;

import com.PrimeCare.PrimeCare.modules.masterdata.specialty.dto.response.SpecialtyResponse;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.service.SpecialtyService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/specialties")
@RequiredArgsConstructor
public class PublicSpecialtyController {

    private final SpecialtyService specialtyService;

    @GetMapping
    public ApiResponse<PageResponse<SpecialtyResponse>> list(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.ok("OK", specialtyService.listActive(q, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<SpecialtyResponse> get(@PathVariable Long id) {
        return ApiResponse.ok("OK", specialtyService.getActiveById(id));
    }
}
