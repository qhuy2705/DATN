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
@Table(name = "public_lookup_otps", indexes = {
        @Index(name = "idx_public_lookup_otp_ref", columnList = "lookup_type,reference_code,created_at"),
        @Index(name = "idx_public_lookup_otp_email", columnList = "target_email")
})
public class PublicLookupOtp extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "lookup_type", nullable = false, length = 32)
    private PublicLookupType lookupType;

    @Column(name = "reference_id", nullable = false)
    private Long referenceId;

    @Column(name = "reference_code", nullable = false, length = 64)
    private String referenceCode;

    @Column(name = "target_email", nullable = false, length = 255)
    private String targetEmail;

    @Column(name = "otp_code", nullable = false, length = 255)
    private String otpCode;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @PrePersist
    void prePersist() {
        if (attemptCount == null) attemptCount = 0;
    }
}
