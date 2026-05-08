package com.PrimeCare.PrimeCare.modules.doctor_schedule.dto.response;

import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
public class DoctorWorkScheduleResponse {
    private Long id;
    private Long doctorId;
    private String doctorName;
    private Long branchId;
    private String branchName;
    private LocalDate workDate;
    private BranchSessionType session;
    private LocalTime startTime;
    private LocalTime endTime;
    private String note;
}
