package com.PrimeCare.PrimeCare.modules.patient.entity;

import com.PrimeCare.PrimeCare.shared.auditing.BaseEntity;
import com.PrimeCare.PrimeCare.shared.enums.BloodType;
import com.PrimeCare.PrimeCare.shared.enums.Gender;
import com.PrimeCare.PrimeCare.shared.enums.PatientStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "patients",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_patient_code", columnNames = "code")
        },
        indexes = {
                @Index(name = "idx_patient_phone", columnList = "phone"),
                @Index(name = "idx_patient_identity_number", columnList = "identity_number"),
                @Index(name = "idx_patient_insurance_number", columnList = "insurance_number")
        }
)
public class Patient extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 32)
    private String code;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(name = "phone", nullable = false, length = 32)
    private String phone;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "avatar_url", length = 1000)
    private String avatarUrl;

    @Column(name = "dob")
    private LocalDate dob;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 16)
    private Gender gender;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "identity_number", length = 32)
    private String identityNumber;

    @Column(name = "insurance_number", length = 64)
    private String insuranceNumber;

    @Column(name = "insurance_expiry_date")
    private LocalDate insuranceExpiryDate;

    @Column(name = "insurance_registered_hospital", length = 500)
    private String insuranceRegisteredHospital;

    @Enumerated(EnumType.STRING)
    @Column(name = "blood_type", length = 16)
    private BloodType bloodType;

    @Column(name = "ethnicity", length = 64)
    private String ethnicity;

    @Builder.Default
    @Column(name = "nationality", length = 64)
    private String nationality = "Việt Nam";

    @Column(name = "occupation", length = 255)
    private String occupation;

    @Column(name = "province", length = 128)
    private String province;

    @Column(name = "district", length = 128)
    private String district;

    @Column(name = "ward", length = 128)
    private String ward;

    @Column(name = "emergency_contact_name", length = 255)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 32)
    private String emergencyContactPhone;

    @Column(name = "allergy_note", columnDefinition = "TEXT")
    private String allergyNote;

    @Column(name = "chronic_disease_note", columnDefinition = "TEXT")
    private String chronicDiseaseNote;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PatientStatus status;

    @PrePersist
    void prePersist() {
        if (status == null) {
            status = PatientStatus.ACTIVE;
        }
    }
}