package com.PrimeCare.PrimeCare.modules.appointment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AppointmentResponseUpdatePhoneRequest {
    @NotBlank
    @Size(max = 32)
    private String phone;
}
