package com.PrimeCare.PrimeCare.modules.bookingemailotp.entity;

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
        name = "booking_email_otps",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_booking_email_otp_verification_id", columnNames = "verification_id")
        },
        indexes = {
                @Index(name = "idx_booking_email_otp_email", columnList = "normalized_email,created_at"),
                @Index(name = "idx_booking_email_otp_status", columnList = "status,expires_at")
        }
)
public class BookingEmailOtp extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "normalized_email", nullable = false, length = 255)
    private String normalizedEmail;

    @Column(name = "verification_id", nullable = false, length = 64)
    private String verificationId;

    @Column(name = "otp_hash", nullable = false, length = 255)
    private String otpHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 64)
    private BookingEmailOtpPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private BookingEmailOtpStatus status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "token_hash", length = 255)
    private String tokenHash;

    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @PrePersist
    void prePersist() {
        if (attemptCount == null) attemptCount = 0;
        if (purpose == null) purpose = BookingEmailOtpPurpose.GUEST_BOOKING_EMAIL_VERIFICATION;
        if (status == null) status = BookingEmailOtpStatus.PENDING;
    }
}
