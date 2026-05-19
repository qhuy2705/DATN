package com.PrimeCare.PrimeCare.modules.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "audit_outbox_events",
        uniqueConstraints = @UniqueConstraint(name = "uq_audit_outbox_event_id", columnNames = "event_id")
)
public class AuditOutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "entity", nullable = false, length = 64)
    private String entity;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_name", length = 255)
    private String actorName;

    @Column(name = "actor_email", length = 255)
    private String actorEmail;

    @Column(name = "actor_role", length = 32)
    private String actorRole;

    @Column(name = "before_json", columnDefinition = "json")
    private String beforeJson;

    @Column(name = "after_json", columnDefinition = "json")
    private String afterJson;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AuditOutboxEventStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @PrePersist
    void prePersist() {
        if (eventId == null) {
            eventId = java.util.UUID.randomUUID().toString();
        }
        if (status == null) {
            status = AuditOutboxEventStatus.PENDING;
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
