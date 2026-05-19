package com.PrimeCare.PrimeCare.modules.appointment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WrongContactViolationRequest {
    @NotBlank
    @Size(max = 500)
    private String reason;
}
