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
@Table(name = "access_token_blacklist")
public class AccessTokenBlacklist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="jti", nullable=false, unique=true, length=64)
    private String jti;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name="user_id", nullable=false)
    private User user;

    @Column(name="expires_at", nullable=false)
    private LocalDateTime expiresAt;

    @Column(name="revoked_at", nullable=false)
    private LocalDateTime revokedAt;

    @Column(name="reason", length=100)
    private String reason;

    @PrePersist
    void prePersist() {
        if (revokedAt == null) revokedAt = LocalDateTime.now();
    }
}