package com.PrimeCare.PrimeCare.modules.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name="user_id", nullable=false)
    private User user;

    @Column(name="token", nullable=false, unique=true, length=500)
    private String token;

    @Column(name="expires_at", nullable=false)
    private LocalDateTime expiresAt;

    @Column(name="revoked", nullable=false)
    private boolean revoked;

    @Column(name="created_at", nullable=false)
    private LocalDateTime createdAt;

    @Column(name="revoked_at")
    private LocalDateTime revokedAt;

    @Column(name="user_agent", length=255)
    private String userAgent;

    @Column(name="ip_address", length=64)
    private String ipAddress;

    @Column(name="family_id", length=64)
    private String familyId;

    @Column(name="replaced_by_token", length=500)
    private String replacedByToken;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
