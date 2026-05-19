package com.PrimeCare.PrimeCare.modules.appointment.entity;

import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.entity.Specialty;
import com.PrimeCare.PrimeCare.modules.patient.entity.Patient;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentSlotHoldReason;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentSlotHoldStatus;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "appointment_slot_holds",
        indexes = {
                @Index(name = "idx_slot_hold_token_hash", columnList = "token_hash"),
                @Index(name = "idx_slot_hold_doctor_slot", columnList = "doctor_id, visit_date, session, slot_start, slot_end, status, expires_at"),
                @Index(name = "idx_slot_hold_status_expires", columnList = "status, expires_at")
        }
)
public class AppointmentSlotHold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "original_appointment_id", nullable = false)
    private Appointment originalAppointment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private DoctorProfile doctor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "specialty_id", nullable = false)
    private Specialty specialty;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "session", nullable = false, length = 8)
    private BranchSessionType session;

    @Column(name = "slot_start", nullable = false)
    private LocalTime slotStart;

    @Column(name = "slot_end", nullable = false)
    private LocalTime slotEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AppointmentSlotHoldStatus status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "reminder_queued_at")
    private LocalDateTime reminderQueuedAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "contact_requested_at")
    private LocalDateTime contactRequestedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "hold_reason", nullable = false, length = 64)
    private AppointmentSlotHoldReason holdReason;

    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    private String tokenHash;

    @Column(name = "token_nonce", nullable = false, length = 64)
    private String tokenNonce;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (status == null) {
            status = AppointmentSlotHoldStatus.HELD;
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
