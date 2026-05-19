package com.PrimeCare.PrimeCare.modules.pharmacy.controller;

import com.PrimeCare.PrimeCare.config.PaginationConfig;
import com.PrimeCare.PrimeCare.modules.medication.entity.MedicationBatch;
import com.PrimeCare.PrimeCare.modules.pharmacy.dto.request.ImportBatchRequest;
import com.PrimeCare.PrimeCare.modules.pharmacy.dto.request.UpdateMedicationBatchRequest;
import com.PrimeCare.PrimeCare.modules.pharmacy.dto.response.MedicationBatchResponse;
import com.PrimeCare.PrimeCare.modules.pharmacy.dto.response.StockSummaryResponse;
import com.PrimeCare.PrimeCare.modules.pharmacy.service.InventoryService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pharmacy/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping
    @PreAuthorize("hasRole('PHARMACIST')")
    public ApiResponse<PageResponse<StockSummaryResponse>> listInventory(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.ok(
                "OK",
                inventoryService.listInventory(q, PaginationConfig.withSort(pageable, Sort.by("name").ascending()))
        );
    }

    @PostMapping("/import")
    @PreAuthorize("hasRole('PHARMACIST')")
    public ApiResponse<MedicationBatchResponse> importBatch(
            @Valid @RequestBody ImportBatchRequest request,
            Authentication authentication
    ) {
        Long userId = Long.valueOf(authentication.getName());
        MedicationBatch batch = inventoryService.importBatch(
                request.getMedicationId(), request.getBatchNumber(), request.getBatchCode(), request.getQuantity(),
                request.getManufactureDate(), request.getExpiryDate(),
                request.getImportPrice(), request.getSellPrice(), request.getSupplier(),
                userId
        );
        return ApiResponse.ok("Đã nhập kho thành công", inventoryService.toBatchResponse(batch));
    }

    @PostMapping("/batches")
    @PreAuthorize("hasRole('PHARMACIST')")
    public ApiResponse<MedicationBatchResponse> createBatch(
            @Valid @RequestBody ImportBatchRequest request,
            Authentication authentication
    ) {
        Long userId = Long.valueOf(authentication.getName());
        MedicationBatch batch = inventoryService.createBatch(
                request.getMedicationId(), request.getBatchNumber(), request.getBatchCode(), request.getQuantity(),
                request.getManufactureDate(), request.getExpiryDate(),
                request.getImportPrice(), request.getSellPrice(), request.getSupplier(),
                userId
        );
        return ApiResponse.ok("Đã nhập kho thành công", inventoryService.toBatchResponse(batch));
    }

    @GetMapping("/batches")
    @PreAuthorize("hasRole('PHARMACIST')")
    public ApiResponse<PageResponse<MedicationBatchResponse>> listBatches(
            @RequestParam(required = false) Long medicationId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.ok(
                "OK",
                inventoryService.listBatches(
                        medicationId,
                        PaginationConfig.withSort(pageable, Sort.by("expiryDate").ascending().and(Sort.by("id").ascending()))
                )
        );
    }

    @PutMapping("/batches/{batchId}")
    @PreAuthorize("hasRole('PHARMACIST')")
    public ApiResponse<MedicationBatchResponse> updateBatch(
            @PathVariable Long batchId,
            @Valid @RequestBody UpdateMedicationBatchRequest request,
            Authentication authentication
    ) {
        Long userId = Long.valueOf(authentication.getName());
        MedicationBatch batch = inventoryService.updateBatch(batchId, request, userId);
        return ApiResponse.ok("Đã cập nhật lô thuốc", inventoryService.toBatchResponse(batch));
    }

    @GetMapping("/medications/{medicationId}/batches")
    @PreAuthorize("hasRole('PHARMACIST')")
    public ApiResponse<List<MedicationBatchResponse>> getBatches(@PathVariable Long medicationId) {
        List<MedicationBatch> batches = inventoryService.getBatches(medicationId);
        return ApiResponse.ok("OK", batches.stream().map(inventoryService::toBatchResponse).toList());
    }

    @GetMapping("/medications/{medicationId}/stock")
    @PreAuthorize("hasRole('PHARMACIST')")
    public ApiResponse<StockSummaryResponse> getStock(@PathVariable Long medicationId) {
        int stock = inventoryService.getTotalStock(medicationId);
        return ApiResponse.ok("OK", StockSummaryResponse.builder()
                .medicationId(medicationId)
                .totalQuantity(stock)
                .totalStock(stock)
                .build());
    }

    @GetMapping("/expiring")
    @PreAuthorize("hasRole('PHARMACIST')")
    public ApiResponse<PageResponse<MedicationBatchResponse>> getExpiringBatches(
            @RequestParam(defaultValue = "30") int daysAhead,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.ok(
                "OK",
                inventoryService.getExpiringBatches(
                        daysAhead,
                        PaginationConfig.withSort(pageable, Sort.by("expiryDate").ascending().and(Sort.by("id").ascending()))
                )
        );
    }

    @PostMapping("/batches/{batchId}/adjust")
    @PreAuthorize("hasRole('PHARMACIST')")
    public ApiResponse<MedicationBatchResponse> adjustStock(
            @PathVariable Long batchId,
            @RequestParam int newQuantity,
            @RequestParam(required = false) String reason,
            Authentication authentication
    ) {
        Long userId = Long.valueOf(authentication.getName());
        MedicationBatch batch = inventoryService.adjustStock(batchId, newQuantity, reason, userId);
        return ApiResponse.ok("Đã điều chỉnh tồn kho", inventoryService.toBatchResponse(batch));
    }
}
