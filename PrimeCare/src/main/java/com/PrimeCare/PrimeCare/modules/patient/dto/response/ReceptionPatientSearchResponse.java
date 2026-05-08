package com.PrimeCare.PrimeCare.modules.patient.dto.response;

import com.PrimeCare.PrimeCare.shared.enums.Gender;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class ReceptionPatientSearchResponse {
    private Long id;
    private String patientCode;
    private String fullName;
    private String phone;
    private String email;
    private LocalDate dob;
    private Gender gender;
    private String address;
}
