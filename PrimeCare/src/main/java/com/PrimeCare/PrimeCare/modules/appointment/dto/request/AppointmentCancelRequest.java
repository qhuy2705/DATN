package com.PrimeCare.PrimeCare.modules.appointment.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AppointmentCancelRequest {
    @NotBlank
    private String reason;
}