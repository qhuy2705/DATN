package com.PrimeCare.PrimeCare.modules.appointment.dto.request;

import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class DoctorCancellationRecoveryRequest {
    @NotNull
    private Long doctorId;

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    @NotNull
    private BranchSessionType startSession;

    @NotNull
    private BranchSessionType endSession;

    private String reason;
}
