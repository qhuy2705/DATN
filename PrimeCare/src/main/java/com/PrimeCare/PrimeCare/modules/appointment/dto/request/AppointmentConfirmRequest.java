package com.PrimeCare.PrimeCare.modules.appointment.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AppointmentConfirmRequest {

    @Size(max = 500)
    private String note;

    @Size(max = 32)
    private String triagePriority;

    @Size(max = 2000)
    private String triageNote;

    @Size(max = 32)
    private String triageReviewStatus;

    @Size(max = 500)
    private String triageOverrideReason;
}
