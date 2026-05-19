package com.PrimeCare.PrimeCare.modules.booking_restriction.entity;

import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.booking_restriction.enums.BookingRestrictionLevel;
import com.PrimeCare.PrimeCare.modules.booking_restriction.enums.BookingRestrictionStatus;
import com.PrimeCare.PrimeCare.modules.patient.entity.Patient;
import com.PrimeCare.PrimeCare.shared.auditing.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
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
        name = "patient_booking_restrictions",
        indexes = {
                @Index(name = "idx_restriction_identity_status_expires", columnList = "identity_key_hash, status, expires_at"),
                @Index(name = "idx_restriction_period_level", columnList = "period_month, level")
        }
)
public class PatientBookingRestriction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @Column(name = "identity_key_hash", nullable = false, length = 64)
    private String identityKeyHash;

    @Column(name = "period_month", nullable = false, length = 7)
    private String periodMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false, length = 32)
    private BookingRestrictionLevel level;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private BookingRestrictionStatus status;

    @Column(name = "score_snapshot", nullable = false)
    private int scoreSnapshot;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lifted_by")
    private User liftedBy;

    @Column(name = "lifted_at")
    private LocalDateTime liftedAt;

    @Column(name = "lift_reason", length = 1000)
    private String liftReason;

    @PrePersist
    void prePersist() {
        if (status == null) {
            status = BookingRestrictionStatus.ACTIVE;
        }
        if (startsAt == null) {
            startsAt = LocalDateTime.now();
        }
    }
}
