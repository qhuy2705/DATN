package com.PrimeCare.PrimeCare.modules.notification.entity;

import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.shared.auditing.BaseEntity;
import com.PrimeCare.PrimeCare.shared.enums.NotificationChannel;
import com.PrimeCare.PrimeCare.shared.enums.NotificationEntityType;
import com.PrimeCare.PrimeCare.shared.enums.NotificationStatus;
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
        name="notifications",
        indexes = {
                @Index(name="idx_notif_status", columnList="status,created_at"),
                @Index(name="idx_notif_ap", columnList="appointment_id"),
                @Index(name="idx_notif_entity", columnList="entity_type,entity_id")
        }
)
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name="channel", nullable=false, length=16)
    private NotificationChannel channel;

    @Column(name="template_code", nullable=false, length=64)
    private String templateCode;

    @Enumerated(EnumType.STRING)
    @Column(name="entity_type", length=32)
    private NotificationEntityType entityType;

    @Column(name="entity_id")
    private Long entityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="appointment_id")
    private Appointment appointment;

    @Column(name="to_phone", length=32)
    private String toPhone;

    @Column(name="to_email", length=255)
    private String toEmail;

    @Lob
    @Column(name = "content", nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name="status", nullable=false, length=16)
    private NotificationStatus status;

    @Column(name="provider", length=64)
    private String provider;

    @Column(name="provider_message_id", length=128)
    private String providerMessageId;

    @Column(name="error_message", length=255)
    private String errorMessage;

    @Column(name="scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name="last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name="attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name="sent_at")
    private LocalDateTime sentAt;

    @PrePersist
    void prePersist() {
        if (status == null) status = NotificationStatus.PENDING;
        if (attemptCount == null) attemptCount = 0;
    }
}
