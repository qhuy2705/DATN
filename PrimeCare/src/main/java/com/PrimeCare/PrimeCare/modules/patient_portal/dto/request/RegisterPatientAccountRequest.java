package com.PrimeCare.PrimeCare.modules.patient_portal.dto.request;

import com.PrimeCare.PrimeCare.shared.enums.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class RegisterPatientAccountRequest {
    @NotBlank
    @Size(max = 255)
    private String fullName;

    @NotBlank
    @Pattern(regexp = "^(\\+84|84|0)(3|5|7|8|9)\\d{8}$", message = "Số điện thoại không đúng định dạng Việt Nam")
    private String phone;

    @Email
    @Size(max = 255)
    private String email;

    @PastOrPresent
    private LocalDate dob;

    private Gender gender;

    @Size(max = 500)
    private String address;

    @Size(max = 64)
    private String insuranceNumber;

    @NotBlank
    @Size(min = 8, max = 100)
    private String password;
}
