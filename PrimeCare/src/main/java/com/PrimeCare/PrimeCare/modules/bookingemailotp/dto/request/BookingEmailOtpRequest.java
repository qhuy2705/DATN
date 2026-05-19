package com.PrimeCare.PrimeCare.modules.bookingemailotp.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BookingEmailOtpRequest {
    @NotBlank(message = "Email không được để trống")
    @Size(max = 255, message = "Email tối đa 255 ký tự")
    @Email(message = "Email không đúng định dạng")
    private String email;
}
