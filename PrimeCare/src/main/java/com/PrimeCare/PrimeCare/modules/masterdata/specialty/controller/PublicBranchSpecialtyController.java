package com.PrimeCare.PrimeCare.modules.masterdata.specialty.controller;

import com.PrimeCare.PrimeCare.modules.masterdata.specialty.dto.response.BranchSpecialtyResponse;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.service.BranchSpecialtyService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/public/branches/{branchId}/specialties")
@RequiredArgsConstructor
public class PublicBranchSpecialtyController {

    private final BranchSpecialtyService branchSpecialtyService;

    @GetMapping
    public ApiResponse<List<BranchSpecialtyResponse>> list(@PathVariable Long branchId) {
        return ApiResponse.ok(
                "OK",
                branchSpecialtyService.listPublicByBranch(branchId)
        );
    }
}