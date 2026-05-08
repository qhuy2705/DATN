package com.PrimeCare.PrimeCare.modules.appointment.dto.request;

import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class AppointmentRescheduleRequest {

    @NotNull
    @FutureOrPresent
    private LocalDate visitDate;

    @NotNull
    private BranchSessionType session;

    @NotNull
    private LocalTime slotStart;

    @NotBlank
    private String note;
}
