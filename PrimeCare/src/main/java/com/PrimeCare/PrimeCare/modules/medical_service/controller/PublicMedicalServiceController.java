package com.PrimeCare.PrimeCare.modules.medical_service.controller;

import com.PrimeCare.PrimeCare.modules.medical_service.dto.response.PublicMedicalServiceCatalogResponse;
import com.PrimeCare.PrimeCare.modules.medical_service.service.MedicalServiceService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController @RequestMapping("/api/public/medical-services") @RequiredArgsConstructor
public class PublicMedicalServiceController {
    private final MedicalServiceService service;

    @GetMapping
    public ApiResponse<List<PublicMedicalServiceCatalogResponse>> list() {
        return ApiResponse.ok("OK", service.listPublicCatalog());
    }

    @GetMapping("/{id}")
    public ApiResponse<PublicMedicalServiceCatalogResponse> get(@PathVariable Long id) {
        return ApiResponse.ok("OK", service.getPublicCatalogItem(id));
    }
}
