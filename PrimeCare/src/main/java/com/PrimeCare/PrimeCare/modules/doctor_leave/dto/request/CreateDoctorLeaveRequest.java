package com.PrimeCare.PrimeCare.modules.doctor_leave.dto.request;

import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateDoctorLeaveRequest {

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
