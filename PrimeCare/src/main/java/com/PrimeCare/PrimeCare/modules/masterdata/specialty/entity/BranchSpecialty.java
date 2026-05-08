package com.PrimeCare.PrimeCare.modules.masterdata.specialty.entity;

import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.entity.Specialty;
import com.PrimeCare.PrimeCare.shared.auditing.BaseEntity;
import com.PrimeCare.PrimeCare.shared.enums.BranchSpecialtyStatus;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "branch_specialties",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_branch_specialty_branch_specialty",
                        columnNames = {"branch_id", "specialty_id"}
                )
        }
)
public class BranchSpecialty extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "specialty_id", nullable = false)
    private Specialty specialty;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private BranchSpecialtyStatus status;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "consultation_fee")
    private Long consultationFee;

    @Column(name = "slot_minutes_override")
    private Integer slotMinutesOverride;

    @Column(name = "note", length = 500)
    private String note;

    @PrePersist
    void prePersist() {
        if (status == null) {
            status = BranchSpecialtyStatus.ACTIVE;
        }
        if (displayOrder == null) {
            displayOrder = 0;
        }
    }
}