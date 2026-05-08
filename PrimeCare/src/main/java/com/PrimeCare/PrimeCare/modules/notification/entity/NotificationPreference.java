package com.PrimeCare.PrimeCare.modules.notification.entity;

import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.shared.auditing.BaseEntity;
import com.PrimeCare.PrimeCare.shared.enums.NotificationChannel;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "notification_preferences",
        uniqueConstraints = @UniqueConstraint(name = "uq_notification_preferences_user", columnNames = "user_id"))
public class NotificationPreference extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "allow_email", nullable = false)
    private Boolean allowEmail;

    @Column(name = "allow_sms", nullable = false)
    private Boolean allowSms;

    @Column(name = "appointment_reminders", nullable = false)
    private Boolean appointmentReminders;

    @Column(name = "result_ready_alerts", nullable = false)
    private Boolean resultReadyAlerts;

    @Column(name = "invoice_updates", nullable = false)
    private Boolean invoiceUpdates;

    @Column(name = "security_alerts", nullable = false)
    private Boolean securityAlerts;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_channel", length = 16)
    private NotificationChannel preferredChannel;

    @PrePersist
    void prePersist() {
        if (allowEmail == null) allowEmail = true;
        if (allowSms == null) allowSms = true;
        if (appointmentReminders == null) appointmentReminders = true;
        if (resultReadyAlerts == null) resultReadyAlerts = true;
        if (invoiceUpdates == null) invoiceUpdates = true;
        if (securityAlerts == null) securityAlerts = true;
        if (preferredChannel == null) preferredChannel = NotificationChannel.SMS;
    }
}
