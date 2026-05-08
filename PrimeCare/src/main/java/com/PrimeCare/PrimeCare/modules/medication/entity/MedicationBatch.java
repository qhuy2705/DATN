package com.PrimeCare.PrimeCare.modules.medication.entity;

import com.PrimeCare.PrimeCare.shared.auditing.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Medication Batch — tracks individual lot/batch of a medication.
 * Supports FIFO dispensing by expiry date.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "medication_batches",
        uniqueConstraints = @UniqueConstraint(name = "uq_batch_number", columnNames = {"medication_id", "batch_number"}))
public class MedicationBatch extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medication_id", nullable = false)
    private Medication medication;

    @Column(name = "batch_number", nullable = false, length = 64)
    private String batchNumber;

    @Column(name = "quantity_in_stock", nullable = false)
    private Integer quantityInStock;

    @Column(name = "manufacture_date")
    private LocalDate manufactureDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "import_price")
    private Long importPrice;

    @Column(name = "sell_price")
    private Long sellPrice;

    @Column(name = "supplier", length = 255)
    private String supplier;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
}
