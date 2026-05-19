package com.PrimeCare.PrimeCare.modules.bookingemailotp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BookingEmailOtpVerifyRequest {
    @NotBlank(message = "verificationId không được để trống")
    @Size(max = 64, message = "verificationId không hợp lệ")
    private String verificationId;

    @NotBlank(message = "OTP không được để trống")
    @Pattern(regexp = "^\\d{6}$", message = "OTP phải gồm 6 chữ số")
    private String otp;
}
