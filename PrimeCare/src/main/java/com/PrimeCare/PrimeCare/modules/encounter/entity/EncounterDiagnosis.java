package com.PrimeCare.PrimeCare.modules.encounter.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "encounter_diagnoses",
        indexes = {
                @Index(name = "idx_enc_diag_encounter", columnList = "encounter_id"),
                @Index(name = "idx_enc_diag_icd10", columnList = "icd10_code_id")
        }
)
public class EncounterDiagnosis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "encounter_id", nullable = false)
    private Encounter encounter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "icd10_code_id", nullable = false)
    private Icd10Code icd10Code;

    @Enumerated(EnumType.STRING)
    @Column(name = "diagnosis_type", nullable = false, length = 16)
    private DiagnosisType diagnosisType;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Builder.Default
    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    public enum DiagnosisType {
        PRELIMINARY,
        FINAL,
        SECONDARY
    }
}
