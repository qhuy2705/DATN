package com.PrimeCare.PrimeCare.modules.masterdata.branch.controller;

import com.PrimeCare.PrimeCare.modules.masterdata.branch.dto.request.CreateBranchRequest;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.dto.request.UpdateBranchRequest;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.dto.request.UpdateBranchStatusRequest;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.dto.response.BranchResponse;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.service.BranchService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import com.PrimeCare.PrimeCare.shared.common.StatusSummaryResponse;
import com.PrimeCare.PrimeCare.shared.enums.BranchStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/branches")
@RequiredArgsConstructor
public class AdminBranchController {

    private final BranchService branchService;

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @PostMapping
    public ApiResponse<BranchResponse> create(@Valid @RequestBody CreateBranchRequest req) {
        return ApiResponse.ok("Created", branchService.create(req));
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @PutMapping("/{id}")
    public ApiResponse<BranchResponse> update(@PathVariable Long id,
                                              @Valid @RequestBody UpdateBranchRequest req) {
        return ApiResponse.ok("Updated", branchService.update(id, req));
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @PatchMapping("/{id}/status")
    public ApiResponse<BranchResponse> updateStatus(@PathVariable Long id,
                                                    @Valid @RequestBody UpdateBranchStatusRequest req) {
        return ApiResponse.ok("Updated", branchService.updateStatus(id, req));
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @GetMapping
    public ApiResponse<PageResponse<BranchResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) BranchStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.ok("OK", branchService.listPage(false, q, status, pageable));
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @GetMapping("/summary")
    public ApiResponse<StatusSummaryResponse> summary(@RequestParam(required = false) String q) {
        return ApiResponse.ok("OK", branchService.summary(q));
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @GetMapping("/{id}")
    public ApiResponse<BranchResponse> get(@PathVariable Long id) {
        return ApiResponse.ok("OK", branchService.getById(id, false));
    }
}
