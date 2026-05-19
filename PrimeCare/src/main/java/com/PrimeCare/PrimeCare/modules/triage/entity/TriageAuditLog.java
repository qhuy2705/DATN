package com.PrimeCare.PrimeCare.modules.triage.entity;

import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "triage_audit_logs")
public class TriageAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @Column(name = "actor_type", nullable = false, length = 32)
    private String actorType;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_name", length = 255)
    private String actorName;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "from_priority", length = 32)
    private String fromPriority;

    @Column(name = "to_priority", length = 32)
    private String toPriority;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "snapshot_json", columnDefinition = "TEXT")
    private String snapshotJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
