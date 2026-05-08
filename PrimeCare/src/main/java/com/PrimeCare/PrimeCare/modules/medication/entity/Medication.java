package com.PrimeCare.PrimeCare.modules.medication.entity;

import com.PrimeCare.PrimeCare.shared.auditing.BaseEntity;
import com.PrimeCare.PrimeCare.shared.enums.MedicationStatus;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "medications")
public class Medication extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "generic_name", length = 255)
    private String genericName;

    @Column(name = "strength", length = 128)
    private String strength;

    @Column(name = "dosage_form", length = 128)
    private String dosageForm;

    @Column(name = "unit", length = 64)
    private String unit;

    @Column(name = "manufacturer", length = 255)
    private String manufacturer;

    @Column(name = "indication_note", columnDefinition = "TEXT")
    private String indicationNote;

    @Column(name = "contraindication_note", columnDefinition = "TEXT")
    private String contraindicationNote;

    @Column(name = "category", length = 64)
    private String category;

    @Column(name = "route_of_administration", length = 64)
    private String routeOfAdministration;

    @Column(name = "storage_conditions", length = 128)
    private String storageConditions;

    @Column(name = "requires_prescription", nullable = false)
    @Builder.Default
    private Boolean requiresPrescription = true;

    @Column(name = "max_daily_dose", length = 128)
    private String maxDailyDose;

    @Column(name = "unit_price")
    private Long unitPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private MedicationStatus status;

    @PrePersist
    void prePersist() {
        if (status == null) {
            status = MedicationStatus.ACTIVE;
        }
    }
}