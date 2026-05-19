package com.PrimeCare.PrimeCare.modules.appointment.dto.request;

import com.PrimeCare.PrimeCare.shared.enums.AppointmentCancellationReasonType;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AppointmentCancelRequest {
    @NotBlank(message = "Cancellation reason is required.")
    private String reason;
    private AppointmentCancellationReasonType cancellationReasonType;
    private Boolean enableRecoveryFlow;
    private Boolean countAsViolation;
    private String violationNote;

    public void setReason(String reason) {
        this.reason = reason != null ? reason.trim() : null;
    }
}
