package com.PrimeCare.PrimeCare.modules.pharmacy.job;

import com.PrimeCare.PrimeCare.modules.medication.entity.MedicationBatch;
import com.PrimeCare.PrimeCare.modules.notification.service.InternalNotificationService;
import com.PrimeCare.PrimeCare.modules.pharmacy.dto.response.StockSummaryResponse;
import com.PrimeCare.PrimeCare.modules.pharmacy.service.InventoryService;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Scheduled jobs for the pharmacy module.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PharmacyScheduledJobs {

    private final InventoryService inventoryService;
    private final InternalNotificationService internalNotificationService;

    /**
     * Chạy mỗi ngày lúc 01:00 AM để kiểm tra các lô thuốc sắp hết hạn và thuốc tồn kho thấp.
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void checkExpiringMedications() {
        log.info("Starting scheduled job: checkExpiringMedications");
        
        try {
            List<MedicationBatch> expiringBatches = inventoryService.getExpiringBatches(30);
            
            if (!expiringBatches.isEmpty()) {
                log.warn("Found {} medication batches expiring within 30 days.", expiringBatches.size());
                for (MedicationBatch batch : expiringBatches) {
                    log.warn("Expiring Batch: Medication={}, BatchNumber={}, ExpiryDate={}, Quantity={}",
                            batch.getMedication().getName(),
                            batch.getBatchNumber(),
                            batch.getExpiryDate(),
                            batch.getQuantityInStock());
                }
                
                for (MedicationBatch batch : expiringBatches) {
                    internalNotificationService.notifyRoleOnce(
                            UserRole.PHARMACIST,
                            "MEDICATION_BATCH_EXPIRING",
                            InternalNotificationService.SEVERITY_WARNING,
                            "Lô thuốc sắp hết hạn",
                            "Lô " + batch.getBatchNumber() + " của thuốc "
                                    + batch.getMedication().getName() + " sắp hết hạn.",
                            "/app/pharmacy/dispense",
                            "MEDICATION_BATCH",
                            batch.getId(),
                            Map.of(
                                    "batchNumber", batch.getBatchNumber(),
                                    "expiryDate", batch.getExpiryDate() != null ? batch.getExpiryDate().toString() : "",
                                    "quantityInStock", batch.getQuantityInStock()
                            )
                    );
                }
            } else {
                log.info("No medication batches expiring within 30 days.");
            }

            List<StockSummaryResponse> lowStockItems = inventoryService.getLowStockMedications();
            if (!lowStockItems.isEmpty()) {
                log.warn("Found {} medications with low stock.", lowStockItems.size());
                for (StockSummaryResponse item : lowStockItems) {
                    internalNotificationService.notifyRoleOnce(
                            UserRole.PHARMACIST,
                            "MEDICATION_LOW_STOCK",
                            InternalNotificationService.SEVERITY_WARNING,
                            "Tồn kho thuốc thấp",
                            "Tồn kho thuốc " + item.getMedicationName() + " thấp. Vui lòng kiểm tra nhập kho.",
                            "/app/pharmacy/inventory",
                            "MEDICATION",
                            item.getMedicationId(),
                            Map.of(
                                    "medicationName", item.getMedicationName() != null ? item.getMedicationName() : "",
                                    "totalQuantity", item.getTotalQuantity() != null ? item.getTotalQuantity() : 0,
                                    "threshold", item.getLowStockThreshold() != null ? item.getLowStockThreshold() : 0,
                                    "unit", item.getUnit() != null ? item.getUnit() : ""
                            )
                    );
                }
            } else {
                log.info("No medications with low stock.");
            }
            
        } catch (Exception e) {
            log.error("Error running checkExpiringMedications job: ", e);
            internalNotificationService.notifyAdminRolesOnceRequiresNew(
                    "PHARMACY_INVENTORY_JOB_FAILED",
                    InternalNotificationService.SEVERITY_CRITICAL,
                    "Job kiểm tra tồn kho thất bại",
                    "Job kiểm tra lô thuốc sắp hết hạn/tồn kho thấp thất bại.",
                    "/app/admin/dashboard",
                    "PHARMACY_JOB",
                    0L,
                    Map.of("error", e.getMessage() != null ? e.getMessage() : "")
            );
        }
        
        log.info("Completed scheduled job: checkExpiringMedications");
    }
}
