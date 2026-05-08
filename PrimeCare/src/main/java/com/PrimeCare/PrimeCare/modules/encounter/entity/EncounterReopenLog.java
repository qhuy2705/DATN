package com.PrimeCare.PrimeCare.modules.encounter.entity;

import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.shared.auditing.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Audit log for encounter re-openings.
 * Tracks who re-opened a completed encounter and why.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "encounter_reopen_logs")
public class EncounterReopenLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "encounter_id", nullable = false)
    private Encounter encounter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reopened_by_user_id", nullable = false)
    private User reopenedByUser;

    @Column(name = "reason", columnDefinition = "TEXT", nullable = false)
    private String reason;

    @Column(name = "previous_status", nullable = false, length = 32)
    private String previousStatus;

    @Column(name = "reopened_at", nullable = false)
    private LocalDateTime reopenedAt;

    @PrePersist
    void prePersist() {
        if (reopenedAt == null) reopenedAt = LocalDateTime.now();
    }
}
