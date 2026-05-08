package com.PrimeCare.PrimeCare.modules.medication.repository;

import com.PrimeCare.PrimeCare.modules.medication.entity.MedicationBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface MedicationBatchRepository extends JpaRepository<MedicationBatch, Long> {

    /** FIFO: oldest expiry first, only batches with stock > 0 */
    @Query("SELECT mb FROM MedicationBatch mb WHERE mb.medication.id = :medId " +
            "AND mb.quantityInStock > 0 AND mb.deleted = false " +
            "ORDER BY mb.expiryDate ASC NULLS LAST")
    List<MedicationBatch> findAvailableBatchesFIFO(@Param("medId") Long medicationId);

    /** Total stock across all batches for a medication */
    @Query("SELECT COALESCE(SUM(mb.quantityInStock), 0) FROM MedicationBatch mb " +
            "WHERE mb.medication.id = :medId AND mb.deleted = false")
    Integer getTotalStock(@Param("medId") Long medicationId);

    /** Batches expiring within N days */
    @Query("SELECT mb FROM MedicationBatch mb WHERE mb.expiryDate IS NOT NULL " +
            "AND mb.expiryDate <= :cutoff AND mb.quantityInStock > 0 AND mb.deleted = false " +
            "ORDER BY mb.expiryDate ASC")
    List<MedicationBatch> findExpiringBatches(@Param("cutoff") LocalDate cutoffDate);

    @Query("SELECT mb FROM MedicationBatch mb WHERE mb.expiryDate IS NOT NULL " +
            "AND mb.expiryDate <= :cutoff AND mb.quantityInStock > 0 AND mb.deleted = false")
    Page<MedicationBatch> findExpiringBatches(@Param("cutoff") LocalDate cutoffDate, Pageable pageable);

    List<MedicationBatch> findByMedication_IdAndDeletedFalseOrderByExpiryDateAsc(Long medicationId);

    Page<MedicationBatch> findByDeletedFalse(Pageable pageable);

    Page<MedicationBatch> findByMedication_IdAndDeletedFalse(Long medicationId, Pageable pageable);
}
