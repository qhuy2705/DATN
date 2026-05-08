package com.PrimeCare.PrimeCare.modules.publiclookup.entity;

import com.PrimeCare.PrimeCare.shared.auditing.BaseEntity;
import com.PrimeCare.PrimeCare.shared.enums.PublicLookupType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "public_lookup_access_tokens", indexes = {
        @Index(name = "idx_public_lookup_access_token", columnList = "token", unique = true),
        @Index(name = "idx_public_lookup_access_ref", columnList = "lookup_type,reference_id")
})
public class PublicLookupAccessToken extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token", nullable = false, unique = true, length = 128)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "lookup_type", nullable = false, length = 32)
    private PublicLookupType lookupType;

    @Column(name = "reference_id", nullable = false)
    private Long referenceId;

    @Column(name = "reference_code", nullable = false, length = 64)
    private String referenceCode;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;
}