package com.PrimeCare.PrimeCare.modules.pharmacy.job;

import com.PrimeCare.PrimeCare.modules.notification.service.InternalNotificationService;
import com.PrimeCare.PrimeCare.modules.pharmacy.dto.response.StockSummaryResponse;
import com.PrimeCare.PrimeCare.modules.pharmacy.service.InventoryService;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PharmacyScheduledJobsTest {

    @Mock
    private InventoryService inventoryService;
    @Mock
    private InternalNotificationService internalNotificationService;

    @InjectMocks
    private PharmacyScheduledJobs jobs;

    @Test
    void checkExpiringMedicationsCreatesLowStockNotificationOnceForPharmacists() {
        when(inventoryService.getExpiringBatches(30)).thenReturn(List.of());
        when(inventoryService.getLowStockMedications()).thenReturn(List.of(
                StockSummaryResponse.builder()
                        .medicationId(7L)
                        .medicationName("Paracetamol")
                        .unit("vien")
                        .totalQuantity(2)
                        .lowStockThreshold(10)
                        .build()
        ));

        jobs.checkExpiringMedications();

        verify(internalNotificationService).notifyRoleOnce(
                eq(UserRole.PHARMACIST),
                eq("MEDICATION_LOW_STOCK"),
                eq(InternalNotificationService.SEVERITY_WARNING),
                eq("Tồn kho thuốc thấp"),
                eq("Tồn kho thuốc Paracetamol thấp. Vui lòng kiểm tra nhập kho."),
                eq("/app/pharmacy/inventory"),
                eq("MEDICATION"),
                eq(7L),
                any(Map.class)
        );
    }

    @Test
    void checkExpiringMedicationsNotifiesAdminsWhenJobFails() {
        RuntimeException failure = new RuntimeException("database down");
        when(inventoryService.getExpiringBatches(30)).thenThrow(failure);

        jobs.checkExpiringMedications();

        verify(internalNotificationService).notifyAdminRolesOnceRequiresNew(
                eq("PHARMACY_INVENTORY_JOB_FAILED"),
                eq(InternalNotificationService.SEVERITY_CRITICAL),
                eq("Job kiểm tra tồn kho thất bại"),
                eq("Job kiểm tra lô thuốc sắp hết hạn/tồn kho thấp thất bại."),
                eq("/app/admin/notifications"),
                eq("PHARMACY_JOB"),
                eq(0L),
                any(Map.class)
        );
    }
}
