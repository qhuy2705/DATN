package com.PrimeCare.PrimeCare.modules.encounter.entity;

import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.entity.Specialty;
import com.PrimeCare.PrimeCare.modules.patient.entity.Patient;
import com.PrimeCare.PrimeCare.shared.auditing.BaseEntity;
import com.PrimeCare.PrimeCare.shared.enums.EncounterStatus;
import com.PrimeCare.PrimeCare.shared.enums.Gender;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "encounters",
        uniqueConstraints = @UniqueConstraint(name = "uq_encounter_appointment", columnNames = "appointment_id")
)
public class Encounter extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "specialty_id", nullable = false)
    private Specialty specialty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    private DoctorProfile doctor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @Column(name = "patient_full_name_snapshot", nullable = false, length = 255)
    private String patientFullNameSnapshot;

    @Column(name = "patient_phone_snapshot", length = 32)
    private String patientPhoneSnapshot;

    @Column(name = "patient_email_snapshot", length = 255)
    private String patientEmailSnapshot;

    @Column(name = "patient_dob_snapshot")
    private LocalDate patientDobSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "patient_gender_snapshot", length = 16)
    private Gender patientGenderSnapshot;

    @Column(name = "intake_reason_for_visit", columnDefinition = "TEXT")
    private String intakeReasonForVisit;

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

    @Column(name = "intake_completed_at")
    private LocalDateTime intakeCompletedAt;

    @Column(name = "intake_completed_by_name", length = 255)
    private String intakeCompletedByName;

    @Column(name = "chief_complaint", columnDefinition = "TEXT")
    private String chiefComplaint;

    @Column(name = "clinical_note", columnDefinition = "TEXT")
    private String clinicalNote;

    @Column(name = "preliminary_diagnosis", columnDefinition = "TEXT")
    private String preliminaryDiagnosis;

    @Column(name = "final_diagnosis", columnDefinition = "TEXT")
    private String finalDiagnosis;

    @Column(name = "conclusion", columnDefinition = "TEXT")
    private String conclusion;

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

    @Column(name = "allergy_snapshot", columnDefinition = "TEXT")
    private String allergySnapshot;

    @Column(name = "chronic_disease_snapshot", columnDefinition = "TEXT")
    private String chronicDiseaseSnapshot;

    @Column(name = "past_medical_history", columnDefinition = "TEXT")
    private String pastMedicalHistory;

    @Column(name = "physical_examination", columnDefinition = "TEXT")
    private String physicalExamination;

    @Column(name = "treatment_plan", columnDefinition = "TEXT")
    private String treatmentPlan;

    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    @Column(name = "follow_up_note", columnDefinition = "TEXT")
    private String followUpNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private EncounterStatus status;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Builder.Default
    @Column(name = "reopened_count", nullable = false)
    private Integer reopenedCount = 0;

    @PrePersist
    void prePersist() {
        if (status == null) status = EncounterStatus.IN_PROGRESS;
        if (startedAt == null) startedAt = LocalDateTime.now();
    }
}