package com.PrimeCare.PrimeCare.modules.audit.entity;

import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name="audit_logs",
        indexes = @Index(name="idx_audit_entity", columnList="entity,entity_id,created_at"),
        uniqueConstraints = @UniqueConstraint(name = "uq_audit_logs_event_id", columnNames = "event_id")
)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", length = 36)
    private String eventId;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="actor_id")
    private User actor;

    @Column(name = "actor_name", length = 255)
    private String actorName;

    @Column(name = "actor_email", length = 255)
    private String actorEmail;

    @Column(name="actor_role", length=32)
    private String actorRole;

    @Column(name="action", nullable=false, length=64)
    private String action;

    @Column(name="entity", nullable=false, length=64)
    private String entity;

    @Column(name="entity_id")
    private Long entityId;

    @Column(name="before_json", columnDefinition="json")
    private String beforeJson;

    @Column(name="after_json", columnDefinition="json")
    private String afterJson;

    @Column(name="ip_address", length=64)
    private String ipAddress;

    @Column(name="user_agent", length=255)
    private String userAgent;

    @Column(name="created_at", nullable=false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
