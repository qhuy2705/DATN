package com.PrimeCare.PrimeCare.modules.booking_restriction.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VoidViolationEventRequest {
    @NotBlank
    private String reason;
}
