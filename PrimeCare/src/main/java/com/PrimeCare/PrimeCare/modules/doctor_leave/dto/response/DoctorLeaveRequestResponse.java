package com.PrimeCare.PrimeCare.modules.doctor_leave.dto.response;

import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import com.PrimeCare.PrimeCare.shared.enums.DoctorLeaveRequestStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class DoctorLeaveRequestResponse {
    private Long id;
    private Long doctorId;
    private String doctorName;
    private LocalDate startDate;
    private LocalDate endDate;
    private BranchSessionType startSession;
    private BranchSessionType endSession;
    private String reason;
    private DoctorLeaveRequestStatus status;
    private String reviewNote;
    private LocalDateTime reviewedAt;
    private String reviewedByName;
}
