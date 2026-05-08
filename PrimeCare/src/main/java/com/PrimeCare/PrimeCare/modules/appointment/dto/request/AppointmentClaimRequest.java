package com.PrimeCare.PrimeCare.modules.appointment.dto.request;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class AppointmentClaimRequest {
    @Min(1)
    private Integer holdMinutes = 5;
}
