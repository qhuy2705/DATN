package com.PrimeCare.PrimeCare.modules.masterdata.branch.controller;

import com.PrimeCare.PrimeCare.modules.masterdata.branch.dto.response.BranchResponse;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.service.BranchService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/branches")
@RequiredArgsConstructor
public class PublicBranchController {

    private final BranchService branchService;

    @GetMapping
    public ApiResponse<PageResponse<BranchResponse>> list(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.ok("OK", branchService.listPage(true, q, null, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<BranchResponse> get(@PathVariable Long id) {
        return ApiResponse.ok("OK", branchService.getById(id, true));
    }
}
