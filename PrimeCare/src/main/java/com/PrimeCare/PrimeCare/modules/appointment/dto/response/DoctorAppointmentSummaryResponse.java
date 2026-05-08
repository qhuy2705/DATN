package com.PrimeCare.PrimeCare.modules.appointment.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class DoctorAppointmentSummaryResponse {
    private LocalDate fromDate;
    private LocalDate toDate;
    private Long confirmed;
    private Long checkedIn;
    private Long waitingExam;
    private Long completedToday;
    private Long cancelled;
    private Long noShow;
    private Long totalToday;
}
