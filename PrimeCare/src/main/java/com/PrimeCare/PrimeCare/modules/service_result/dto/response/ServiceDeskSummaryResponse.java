package com.PrimeCare.PrimeCare.modules.service_result.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ServiceDeskSummaryResponse {
    private long waitingCount;
    private long inProgressCount;
    private long completedTodayCount;
    private long overdueCount;
    private long readyForDoctorCount;
    private long pdfPendingCount;
    private double averageTurnaroundMinutes;
    private double turnaroundBreachRate;
}
