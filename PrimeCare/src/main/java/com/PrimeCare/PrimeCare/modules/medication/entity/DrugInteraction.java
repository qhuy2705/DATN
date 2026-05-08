package com.PrimeCare.PrimeCare.modules.medication.entity;

import com.PrimeCare.PrimeCare.shared.auditing.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Drug-Drug Interaction record.
 * Stores known dangerous interactions between two medications.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "drug_interactions",
        uniqueConstraints = @UniqueConstraint(name = "uq_drug_interaction_pair", columnNames = {"medication_a_id", "medication_b_id"}))
public class DrugInteraction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medication_a_id", nullable = false)
    private Medication medicationA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medication_b_id", nullable = false)
    private Medication medicationB;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 32)
    private InteractionSeverity severity;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "clinical_effect", columnDefinition = "TEXT")
    private String clinicalEffect;

    public enum InteractionSeverity {
        MINOR,
        MODERATE,
        SEVERE,
        CONTRAINDICATED
    }
}
