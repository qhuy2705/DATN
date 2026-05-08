package com.PrimeCare.PrimeCare.modules.notification.entity;

import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.shared.auditing.BaseEntity;
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
        name = "internal_notifications",
        indexes = {
                @Index(name = "idx_internal_notif_recipient_read_created", columnList = "recipient_user_id,read_at,created_at"),
                @Index(name = "idx_internal_notif_recipient_created", columnList = "recipient_user_id,created_at"),
                @Index(name = "idx_internal_notif_entity", columnList = "entity_type,entity_id")
        }
)
public class InternalNotification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_user_id", nullable = false)
    private User recipientUser;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "type", nullable = false, length = 64)
    private String type;

    @Column(name = "severity", nullable = false, length = 32)
    private String severity;

    @Column(name = "route", length = 500)
    private String route;

    @Column(name = "entity_type", length = 64)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Lob
    @Column(name = "metadata_json", columnDefinition = "LONGTEXT")
    private String metadataJson;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
}
