package com.PrimeCare.PrimeCare.modules.patient.entity;

import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.shared.auditing.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "patient_allergies")
public class PatientAllergy extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "allergen_name", nullable = false, length = 255)
    private String allergenName;

    @Enumerated(EnumType.STRING)
    @Column(name = "allergy_type", nullable = false, length = 32)
    private AllergyType allergyType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 32)
    private AllergySeverity severity;

    @Column(name = "reaction", columnDefinition = "TEXT")
    private String reaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "noted_by")
    private User notedBy;

    public enum AllergyType {
        DRUG, FOOD, ENVIRONMENTAL, OTHER
    }

    public enum AllergySeverity {
        MILD, MODERATE, SEVERE, LIFE_THREATENING
    }
}
