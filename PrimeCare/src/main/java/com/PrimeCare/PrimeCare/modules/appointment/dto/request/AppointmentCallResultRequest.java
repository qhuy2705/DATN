package com.PrimeCare.PrimeCare.modules.appointment.dto.request;

import com.PrimeCare.PrimeCare.shared.enums.CallOutcome;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AppointmentCallResultRequest {
    @NotNull
    private CallOutcome outcome;

    @Size(max = 500)
    private String note;

    private Boolean sendFallbackEmail;
}
