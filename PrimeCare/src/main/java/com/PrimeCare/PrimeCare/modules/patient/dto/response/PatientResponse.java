package com.PrimeCare.PrimeCare.modules.patient.dto.response;

import com.PrimeCare.PrimeCare.shared.enums.Gender;
import com.PrimeCare.PrimeCare.shared.enums.PatientStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class PatientResponse {
    private Long id;
    private String code;
    private String avatarUrl;
    private String fullName;
    private String phone;
    private String email;
    private LocalDate dob;
    private Gender gender;
    private String address;
    private String identityNumber;
    
    // Insurance
    private String insuranceNumber;
    private LocalDate insuranceExpiryDate;
    private String insuranceRegisteredHospital;

    // Clinical & Demographics
    private com.PrimeCare.PrimeCare.shared.enums.BloodType bloodType;
    private String ethnicity;
    private String nationality;
    private String occupation;

    // Detailed Address
    private String province;
    private String district;
    private String ward;

    private java.util.List<PatientAllergyResponse> allergies;

    private String emergencyContactName;
    private String emergencyContactPhone;
    private String allergyNote;
    private String chronicDiseaseNote;
    private String note;
    private PatientStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}