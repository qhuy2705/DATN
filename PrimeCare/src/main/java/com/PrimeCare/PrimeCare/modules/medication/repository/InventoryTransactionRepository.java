package com.PrimeCare.PrimeCare.modules.medication.repository;

import com.PrimeCare.PrimeCare.modules.medication.entity.InventoryTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {
    Page<InventoryTransaction> findByBatch_IdOrderByPerformedAtDesc(Long batchId, Pageable pageable);

    Page<InventoryTransaction> findByBatch_Medication_IdOrderByPerformedAtDesc(Long medicationId, Pageable pageable);
}
