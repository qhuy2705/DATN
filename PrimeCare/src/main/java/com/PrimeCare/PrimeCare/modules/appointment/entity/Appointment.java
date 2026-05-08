package com.PrimeCare.PrimeCare.modules.appointment.entity;

import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.entity.Specialty;
import com.PrimeCare.PrimeCare.modules.patient.entity.Patient;
import com.PrimeCare.PrimeCare.shared.auditing.BaseEntity;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentSourceType;
import com.PrimeCare.PrimeCare.shared.enums.ArrivalStatus;
import com.PrimeCare.PrimeCare.shared.enums.Gender;
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
@Table(name = "appointments")
public class Appointment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AppointmentStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "specialty_id", nullable = false)
    private Specialty specialty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    private DoctorProfile doctor;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "session", nullable = false, length = 8)
    private BranchSessionType session;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private AppointmentSourceType sourceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "arrival_status", nullable = false, length = 16)
    private ArrivalStatus arrivalStatus;

    @Column(name = "reception_queue_no")
    private Integer receptionQueueNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "arrived_by")
    private User arrivedBy;

    @Column(name = "arrived_at")
    private LocalDateTime arrivedAt;

    @Column(name = "queue_no")
    private Integer queueNo;

    @Column(name = "slot_minutes")
    private Integer slotMinutes;

    @Column(name = "eta_start")
    private LocalTime etaStart;

    @Column(name = "eta_end")
    private LocalTime etaEnd;

    @Column(name = "patient_full_name", nullable = false, length = 255)
    private String patientFullName;

    @Column(name = "patient_phone", nullable = false, length = 32)
    private String patientPhone;

    @Column(name = "patient_email", length = 255)
    private String patientEmail;

    @Column(name = "patient_dob")
    private LocalDate patientDob;

    @Enumerated(EnumType.STRING)
    @Column(name = "patient_gender", length = 16)
    private Gender patientGender;

    @Column(name = "patient_note", columnDefinition = "TEXT")
    private String patientNote;

    @Column(name = "reason_for_visit", columnDefinition = "TEXT")
    private String reasonForVisit;

    @Column(name = "visit_type", length = 32)
    private String visitType;

    @Column(name = "triage_priority", length = 32)
    private String triagePriority;

    @Column(name = "triage_note", columnDefinition = "TEXT")
    private String triageNote;

    @Column(name = "insurance_note", length = 500)
    private String insuranceNote;

    @Column(name = "emergency_contact_name", length = 255)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 32)
    private String emergencyContactPhone;

    @Column(name = "height_cm")
    private Double heightCm;

    @Column(name = "weight_kg")
    private Double weightKg;

    @Column(name = "temperature_c")
    private Double temperatureC;

    @Column(name = "pulse")
    private Integer pulse;

    @Column(name = "systolic_bp")
    private Integer systolicBp;

    @Column(name = "diastolic_bp")
    private Integer diastolicBp;

    @Column(name = "respiratory_rate")
    private Integer respiratoryRate;

    @Column(name = "spo2")
    private Integer spo2;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intake_completed_by")
    private User intakeCompletedBy;

    @Column(name = "intake_completed_at")
    private LocalDateTime intakeCompletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by")
    private User confirmedBy;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private User cancelledBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    // staff claim processing
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processing_by")
    private User processingBy;

    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    @Column(name = "processing_expires_at")
    private LocalDateTime processingExpiresAt;

    // check-in
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checked_in_by")
    private User checkedInBy;

    @Column(name = "checked_in_at")
    private LocalDateTime checkedInAt;

    // no-show
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "no_show_marked_by")
    private User noShowMarkedBy;

    @Column(name = "no_show_marked_at")
    private LocalDateTime noShowMarkedAt;

    @Column(name = "no_show_note", length = 500)
    private String noShowNote;

    @Column(name = "follow_up_pending")
    private Boolean followUpPending;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rescheduled_from_appointment_id")
    private Appointment rescheduledFromAppointment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rescheduled_by")
    private User rescheduledBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @Column(name = "rescheduled_at")
    private LocalDateTime rescheduledAt;

    @PrePersist
    void prePersist() {
        if (status == null) status = AppointmentStatus.REQUESTED;
        if (followUpPending == null) followUpPending = false;
        if (sourceType == null) sourceType = AppointmentSourceType.PUBLIC_BOOKING;
        if (arrivalStatus == null) arrivalStatus = ArrivalStatus.NOT_ARRIVED;
    }
}
