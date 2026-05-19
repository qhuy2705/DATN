package com.PrimeCare.PrimeCare.modules.prescription.entity;

import com.PrimeCare.PrimeCare.modules.medication.entity.Medication;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.shared.enums.PrescriptionItemStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "prescription_items")
public class PrescriptionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prescription_id", nullable = false)
    private Prescription prescription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medication_id", nullable = false)
    private Medication medication;

    @Column(name = "medication_code_snapshot", nullable = false, length = 64)
    private String medicationCodeSnapshot;

    @Column(name = "medication_name_snapshot", nullable = false, length = 255)
    private String medicationNameSnapshot;

    @Column(name = "strength_snapshot", length = 128)
    private String strengthSnapshot;

    @Column(name = "dosage_form_snapshot", length = 128)
    private String dosageFormSnapshot;

    @Column(name = "unit_snapshot", length = 64)
    private String unitSnapshot;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "dose", length = 128)
    private String dose;

    @Column(name = "frequency", length = 128)
    private String frequency;

    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(name = "route", length = 128)
    private String route;

    @Column(name = "instruction", columnDefinition = "TEXT")
    private String instruction;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private PrescriptionItemStatus status;

    @Column(name = "refund_reason", length = 500)
    private String refundReason;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refunded_by_user_id")
    private User refundedByUser;

    @PrePersist
    void prePersist() {
        if (status == null) {
            status = PrescriptionItemStatus.ISSUED;
        }
    }
}
