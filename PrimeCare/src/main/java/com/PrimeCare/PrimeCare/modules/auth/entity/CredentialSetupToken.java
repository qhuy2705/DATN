package com.PrimeCare.PrimeCare.modules.auth.entity;

import com.PrimeCare.PrimeCare.shared.auditing.BaseEntity;
import com.PrimeCare.PrimeCare.shared.enums.CredentialSetupTokenPurpose;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "credential_setup_tokens",
        indexes = {
                @Index(name = "idx_credential_setup_user", columnList = "user_id,purpose,expires_at"),
                @Index(name = "idx_credential_setup_expires", columnList = "expires_at")
        })
public class CredentialSetupToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 32)
    private CredentialSetupTokenPurpose purpose;

    @Column(name = "delivery_channel", length = 16)
    private String deliveryChannel;

    @Column(name = "delivery_target", length = 255)
    private String deliveryTarget;

    @Column(name = "requested_by_user_id")
    private Long requestedByUserId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "revoked", nullable = false)
    private boolean revoked;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "note", length = 255)
    private String note;

    public boolean isActive() {
        return !revoked && usedAt == null && expiresAt != null && expiresAt.isAfter(LocalDateTime.now());
    }
}
