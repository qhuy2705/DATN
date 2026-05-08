package com.PrimeCare.PrimeCare.modules.pharmacy.service;

import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.medication.entity.InventoryTransaction;
import com.PrimeCare.PrimeCare.modules.medication.entity.Medication;
import com.PrimeCare.PrimeCare.modules.medication.entity.MedicationBatch;
import com.PrimeCare.PrimeCare.modules.medication.repository.InventoryTransactionRepository;
import com.PrimeCare.PrimeCare.modules.medication.repository.MedicationBatchRepository;
import com.PrimeCare.PrimeCare.modules.medication.repository.MedicationRepository;
import com.PrimeCare.PrimeCare.modules.pharmacy.dto.request.UpdateMedicationBatchRequest;
import com.PrimeCare.PrimeCare.modules.pharmacy.dto.response.MedicationBatchResponse;
import com.PrimeCare.PrimeCare.modules.pharmacy.dto.response.StockSummaryResponse;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import com.PrimeCare.PrimeCare.shared.enums.MedicationStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Manages medication inventory: import, export, FIFO dispensing, stock queries.
 */
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final MedicationBatchRepository batchRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final MedicationRepository medicationRepository;
    private final UserRepository userRepository;

    @Value("${app.pharmacy.default-low-stock-threshold:10}")
    private int defaultLowStockThreshold;

    /**
     * Import a new batch of medication into inventory.
     */
    @Transactional
    public MedicationBatch importBatch(Long medicationId, String batchNumber, Integer quantity,
                                       LocalDate manufactureDate, LocalDate expiryDate,
                                       Long importPrice, Long sellPrice, String supplier,
                                       Long userId) {
        validateBatchInput(batchNumber, quantity, manufactureDate, expiryDate, importPrice, sellPrice, true, true);

        Medication med = medicationRepository.findById(medicationId)
                .orElseThrow(() -> new ApiException(ErrorCode.MEDICATION_NOT_FOUND));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));

        MedicationBatch batch = MedicationBatch.builder()
                .medication(med)
                .batchNumber(batchNumber.trim())
                .quantityInStock(quantity)
                .manufactureDate(manufactureDate)
                .expiryDate(expiryDate)
                .importPrice(importPrice)
                .sellPrice(sellPrice)
                .supplier(normalize(supplier))
                .build();

        MedicationBatch saved = batchRepository.save(batch);

        // Record transaction
        transactionRepository.save(InventoryTransaction.builder()
                .batch(saved)
                .transactionType(InventoryTransaction.TransactionType.IMPORT)
                .quantity(quantity)
                .reason("Nhập kho lô " + batchNumber)
                .performedByUser(user)
                .performedAt(LocalDateTime.now())
                .build());

        return saved;
    }

    /**
     * Dispense medication using FIFO (First Expiry, First Out).
     * Returns the total quantity actually dispensed.
     */
    @Transactional
    public int dispenseFIFO(Long medicationId, int requiredQuantity, Long prescriptionId, Long userId) {
        List<MedicationBatch> batches = batchRepository.findAvailableBatchesFIFO(medicationId);

        User user = userId != null ? userRepository.findById(userId).orElse(null) : null;

        int remaining = requiredQuantity;
        for (MedicationBatch batch : batches) {
            if (remaining <= 0) break;

            int toDeduct = Math.min(remaining, batch.getQuantityInStock());
            batch.setQuantityInStock(batch.getQuantityInStock() - toDeduct);
            batchRepository.save(batch);

            transactionRepository.save(InventoryTransaction.builder()
                    .batch(batch)
                    .transactionType(InventoryTransaction.TransactionType.DISPENSED)
                    .quantity(-toDeduct)
                    .reason("Phát thuốc theo đơn")
                    .referenceType("PRESCRIPTION")
                    .referenceId(prescriptionId)
                    .performedByUser(user)
                    .performedAt(LocalDateTime.now())
                    .build());

            remaining -= toDeduct;
        }

        if (remaining > 0) {
            throw new ApiException(ErrorCode.INVENTORY_INSUFFICIENT_STOCK,
                    "Không đủ tồn kho. Cần " + requiredQuantity + " nhưng chỉ còn " + (requiredQuantity - remaining));
        }

        return requiredQuantity;
    }

    /**
     * Get total stock for a medication across all batches.
     */
    @Transactional(readOnly = true)
    public int getTotalStock(Long medicationId) {
        return batchRepository.getTotalStock(medicationId);
    }

    @Transactional(readOnly = true)
    public PageResponse<StockSummaryResponse> listInventory(String q, Pageable pageable) {
        Page<Medication> page = medicationRepository.searchAdmin(normalize(q), MedicationStatus.ACTIVE, pageable);
        List<StockSummaryResponse> items = page.getContent().stream()
                .map(this::toStockSummary)
                .toList();
        return toPageResponse(page, items, pageable);
    }

    @Transactional(readOnly = true)
    public List<StockSummaryResponse> getLowStockMedications() {
        int threshold = effectiveLowStockThreshold();
        if (threshold <= 0) {
            return List.of();
        }

        return medicationRepository.findByStatus(MedicationStatus.ACTIVE)
                .stream()
                .map(this::toStockSummary)
                .filter(item -> Boolean.TRUE.equals(item.getLowStock()))
                .toList();
    }

    /**
     * Get all batches for a medication.
     */
    @Transactional(readOnly = true)
    public List<MedicationBatch> getBatches(Long medicationId) {
        return batchRepository.findByMedication_IdAndDeletedFalseOrderByExpiryDateAsc(medicationId);
    }

    @Transactional(readOnly = true)
    public PageResponse<MedicationBatchResponse> listBatches(Long medicationId, Pageable pageable) {
        Page<MedicationBatch> page = medicationId == null
                ? batchRepository.findByDeletedFalse(pageable)
                : batchRepository.findByMedication_IdAndDeletedFalse(medicationId, pageable);
        return toPageResponse(
                page,
                page.getContent().stream().map(this::toBatchResponse).toList(),
                pageable
        );
    }

    /**
     * Get batches expiring within N days.
     */
    @Transactional(readOnly = true)
    public List<MedicationBatch> getExpiringBatches(int daysAhead) {
        return batchRepository.findExpiringBatches(LocalDate.now().plusDays(Math.max(0, daysAhead)));
    }

    @Transactional(readOnly = true)
    public PageResponse<MedicationBatchResponse> getExpiringBatches(int daysAhead, Pageable pageable) {
        Page<MedicationBatch> page = batchRepository.findExpiringBatches(
                LocalDate.now().plusDays(Math.max(0, daysAhead)),
                pageable
        );
        return toPageResponse(
                page,
                page.getContent().stream().map(this::toBatchResponse).toList(),
                pageable
        );
    }

    /**
     * Adjust stock (manual correction).
     */
    @Transactional
    public MedicationBatch adjustStock(Long batchId, int newQuantity, String reason, Long userId) {
        if (newQuantity < 0) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Số lượng không được âm");
        }

        MedicationBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new ApiException(ErrorCode.MEDICATION_BATCH_NOT_FOUND));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));

        int diff = newQuantity - batch.getQuantityInStock();
        batch.setQuantityInStock(newQuantity);
        batchRepository.save(batch);

        transactionRepository.save(InventoryTransaction.builder()
                .batch(batch)
                .transactionType(InventoryTransaction.TransactionType.ADJUSTMENT)
                .quantity(diff)
                .reason(reason != null ? reason : "Điều chỉnh tồn kho")
                .performedByUser(user)
                .performedAt(LocalDateTime.now())
                .build());

        return batch;
    }

    @Transactional
    public MedicationBatch updateBatch(Long batchId, UpdateMedicationBatchRequest request, Long userId) {
        if (request == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Dữ liệu cập nhật lô thuốc không hợp lệ");
        }

        MedicationBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new ApiException(ErrorCode.MEDICATION_BATCH_NOT_FOUND));

        LocalDate manufactureDate = request.getManufactureDate() != null ? request.getManufactureDate() : batch.getManufactureDate();
        LocalDate expiryDate = request.getExpiryDate() != null ? request.getExpiryDate() : batch.getExpiryDate();
        Integer quantity = request.getQuantity() != null ? request.getQuantity() : batch.getQuantityInStock();
        Long importPrice = request.getImportPrice() != null ? request.getImportPrice() : batch.getImportPrice();
        Long sellPrice = request.getSellPrice() != null ? request.getSellPrice() : batch.getSellPrice();
        String batchNumber = request.getBatchNumber() != null ? request.getBatchNumber() : batch.getBatchNumber();

        validateBatchInput(
                batchNumber,
                quantity,
                manufactureDate,
                expiryDate,
                importPrice,
                sellPrice,
                false,
                request.getExpiryDate() != null
        );

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));

        int oldQuantity = batch.getQuantityInStock();
        batch.setBatchNumber(batchNumber.trim());
        batch.setQuantityInStock(quantity);
        batch.setManufactureDate(manufactureDate);
        batch.setExpiryDate(expiryDate);
        batch.setImportPrice(importPrice);
        batch.setSellPrice(sellPrice);
        if (request.getSupplier() != null) {
            batch.setSupplier(normalize(request.getSupplier()));
        }

        MedicationBatch saved = batchRepository.save(batch);
        int diff = quantity - oldQuantity;
        if (diff != 0) {
            transactionRepository.save(InventoryTransaction.builder()
                    .batch(saved)
                    .transactionType(InventoryTransaction.TransactionType.ADJUSTMENT)
                    .quantity(diff)
                    .reason("Cập nhật lô thuốc")
                    .performedByUser(user)
                    .performedAt(LocalDateTime.now())
                    .build());
        }
        return saved;
    }

    public MedicationBatchResponse toBatchResponse(MedicationBatch batch) {
        String batchNumber = batch.getBatchNumber();
        Integer quantity = batch.getQuantityInStock();
        return MedicationBatchResponse.builder()
                .id(batch.getId())
                .batchId(batch.getId())
                .medicationId(batch.getMedication().getId())
                .medicationName(batch.getMedication().getName())
                .unit(batch.getMedication().getUnit())
                .batchNumber(batchNumber)
                .batchCode(batchNumber)
                .quantityInStock(quantity)
                .quantity(quantity)
                .manufactureDate(batch.getManufactureDate())
                .expiryDate(batch.getExpiryDate())
                .status(resolveBatchStatus(batch.getExpiryDate()))
                .importPrice(batch.getImportPrice())
                .sellPrice(batch.getSellPrice())
                .supplier(batch.getSupplier())
                .build();
    }

    private StockSummaryResponse toStockSummary(Medication medication) {
        int totalQuantity = getTotalStock(medication.getId());
        int threshold = effectiveLowStockThreshold();
        boolean lowStock = threshold > 0 && totalQuantity <= threshold;
        return StockSummaryResponse.builder()
                .medicationId(medication.getId())
                .medicationName(medication.getName())
                .unit(medication.getUnit())
                .totalQuantity(totalQuantity)
                .totalStock(totalQuantity)
                .lowStock(lowStock)
                .lowStockThreshold(threshold)
                .build();
    }

    private void validateBatchInput(
            String batchNumber,
            Integer quantity,
            LocalDate manufactureDate,
            LocalDate expiryDate,
            Long importPrice,
            Long sellPrice,
            boolean requireBatchNumber,
            boolean rejectPastExpiryDate
    ) {
        if ((requireBatchNumber || batchNumber != null) && (batchNumber == null || batchNumber.isBlank())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Số lô không được để trống");
        }
        if (quantity == null || quantity < 0) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Số lượng không được âm");
        }
        if (manufactureDate != null && expiryDate != null && manufactureDate.isAfter(expiryDate)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Ngày sản xuất không được sau hạn dùng");
        }
        if (rejectPastExpiryDate && expiryDate != null && expiryDate.isBefore(LocalDate.now())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Hạn dùng không hợp lệ");
        }
        if (importPrice != null && importPrice < 0) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Giá nhập không được âm");
        }
        if (sellPrice != null && sellPrice < 0) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Giá bán không được âm");
        }
    }

    private int effectiveLowStockThreshold() {
        return Math.max(0, defaultLowStockThreshold);
    }

    private String resolveBatchStatus(LocalDate expiryDate) {
        if (expiryDate == null) {
            return "ACTIVE";
        }
        LocalDate today = LocalDate.now();
        if (expiryDate.isBefore(today)) {
            return "EXPIRED";
        }
        if (!expiryDate.isAfter(today.plusDays(30))) {
            return "EXPIRING";
        }
        return "ACTIVE";
    }

    private <E, T> PageResponse<T> toPageResponse(Page<E> page, List<T> items, Pageable pageable) {
        return PageResponse.<T>builder()
                .items(items)
                .meta(PageResponse.Meta.builder()
                        .page(page.getNumber())
                        .size(page.getSize())
                        .totalItems(page.getTotalElements())
                        .totalPages(page.getTotalPages())
                        .hasNext(page.hasNext())
                        .hasPrev(page.hasPrevious())
                        .sort(pageable.getSort().toString())
                        .build())
                .build();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
