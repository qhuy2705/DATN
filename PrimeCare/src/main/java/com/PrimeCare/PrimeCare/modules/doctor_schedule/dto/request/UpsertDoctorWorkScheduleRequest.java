package com.PrimeCare.PrimeCare.modules.doctor_schedule.dto.request;

import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpsertDoctorWorkScheduleRequest {

    @NotNull
    private Long doctorId;

    @NotNull
    private LocalDate workDate;

    @NotNull
    private BranchSessionType session;

    private String note;
}
