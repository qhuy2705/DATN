package com.PrimeCare.PrimeCare.modules.encounter.controller;

import com.PrimeCare.PrimeCare.config.PaginationConfig;
import com.PrimeCare.PrimeCare.modules.encounter.dto.response.Icd10CodeResponse;
import com.PrimeCare.PrimeCare.modules.encounter.service.Icd10CodeService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/doctor/icd10-codes")
@RequiredArgsConstructor
public class Icd10CodeController {

    private final Icd10CodeService icd10CodeService;

    @GetMapping
    public ApiResponse<PageResponse<Icd10CodeResponse>> search(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "code", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return ApiResponse.ok("OK", icd10CodeService.search(q, PaginationConfig.withSort(pageable, Sort.by("code").ascending())));
    }
}
