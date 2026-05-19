package com.PrimeCare.PrimeCare.modules.booking_restriction.entity;

import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.booking_restriction.enums.ViolationEventSource;
import com.PrimeCare.PrimeCare.modules.booking_restriction.enums.ViolationEventStatus;
import com.PrimeCare.PrimeCare.modules.booking_restriction.enums.ViolationEventType;
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
import jakarta.persistence.UniqueConstraint;
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
        name = "patient_violation_events",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_patient_violation_event_appt_type_status",
                        columnNames = {"appointment_id", "type", "status"}
                )
        },
        indexes = {
                @Index(name = "idx_violation_identity_month_status", columnList = "identity_key_hash, period_month, status"),
                @Index(name = "idx_violation_appointment_type_status", columnList = "appointment_id, type, status")
        }
)
public class PatientViolationEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    @Column(name = "identity_key_hash", nullable = false, length = 64)
    private String identityKeyHash;

    @Column(name = "period_month", nullable = false, length = 7)
    private String periodMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 64)
    private ViolationEventType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 32)
    private ViolationEventSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ViolationEventStatus status;

    @Column(name = "points", nullable = false)
    private int points;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voided_by")
    private User voidedBy;

    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    @Column(name = "void_reason", length = 1000)
    private String voidReason;

    @PrePersist
    void prePersist() {
        if (status == null) {
            status = ViolationEventStatus.ACTIVE;
        }
        if (source == null) {
            source = ViolationEventSource.SYSTEM;
        }
    }
}
