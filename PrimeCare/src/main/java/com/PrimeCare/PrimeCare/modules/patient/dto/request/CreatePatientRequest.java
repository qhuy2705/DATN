package com.PrimeCare.PrimeCare.modules.patient.dto.request;

import com.PrimeCare.PrimeCare.shared.enums.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreatePatientRequest {

    @NotBlank(message = "Họ tên không được để trống")
    @Size(max = 255)
    private String fullName;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(
            regexp = "^(\\+84|84|0)(3|5|7|8|9)\\d{8}$",
            message = "Số điện thoại không đúng định dạng Việt Nam"
    )
    private String phone;

    @Email(message = "Email không đúng định dạng")
    @Size(max = 255)
    private String email;

    @PastOrPresent(message = "Ngày sinh không được ở tương lai")
    private LocalDate dob;

    private Gender gender;

    @Size(max = 500)
    private String address;

    @Size(max = 32)
    private String identityNumber;

    @Size(max = 64)
    private String insuranceNumber;

    private LocalDate insuranceExpiryDate;

    @Size(max = 500)
    private String insuranceRegisteredHospital;

    private com.PrimeCare.PrimeCare.shared.enums.BloodType bloodType;

    @Size(max = 64)
    private String ethnicity;

    @Size(max = 64)
    private String nationality;

    @Size(max = 255)
    private String occupation;

    @Size(max = 128)
    private String province;

    @Size(max = 128)
    private String district;

    @Size(max = 128)
    private String ward;

    @Size(max = 255)
    private String emergencyContactName;

    @Size(max = 32)
    private String emergencyContactPhone;

    @Size(max = 2000)
    private String allergyNote;

    @Size(max = 2000)
    private String chronicDiseaseNote;

    @Size(max = 2000)
    private String note;
}