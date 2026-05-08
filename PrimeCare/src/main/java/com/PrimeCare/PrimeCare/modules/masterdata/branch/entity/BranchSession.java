package com.PrimeCare.PrimeCare.modules.masterdata.branch.entity;

import com.PrimeCare.PrimeCare.shared.auditing.BaseEntity;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name="branch_sessions",
        uniqueConstraints = @UniqueConstraint(name="uq_branch_session", columnNames = {"branch_id", "session"})
)
public class BranchSession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name="branch_id", nullable=false)
    private Branch branch;

    @Enumerated(EnumType.STRING)
    @Column(name="session", nullable=false, length=8)
    private BranchSessionType session;

    @Column(name="start_time", nullable=false)
    private LocalTime startTime;

    @Column(name="end_time", nullable=false)
    private LocalTime endTime;

    @Column(name="capacity_override")
    private Integer capacityOverride;

    @Column(name="status", nullable=false, length=16)
    private String status;

    @PrePersist
    void prePersist() {
        if (status == null) status = "ACTIVE";
    }
}
