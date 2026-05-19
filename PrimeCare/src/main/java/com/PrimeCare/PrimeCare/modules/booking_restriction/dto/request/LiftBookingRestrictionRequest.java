package com.PrimeCare.PrimeCare.modules.booking_restriction.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LiftBookingRestrictionRequest {
    @NotBlank
    private String reason;
}
