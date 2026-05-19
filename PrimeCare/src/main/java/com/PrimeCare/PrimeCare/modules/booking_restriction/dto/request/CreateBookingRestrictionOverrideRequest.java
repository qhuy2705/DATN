package com.PrimeCare.PrimeCare.modules.booking_restriction.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateBookingRestrictionOverrideRequest {
    @NotBlank
    private String reason;

    @Min(1)
    @Max(72)
    private Integer validHours;
}
