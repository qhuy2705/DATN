package com.PrimeCare.PrimeCare.modules.dashboard.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardTodaySummaryResponse {
    private long totalAppointments;
    private long arrivedAppointments;
    private long checkedInAppointments;
    private long noShowAppointments;
    private long totalEncounters;
    private long inProgressEncounters;
    private long waitingServiceItems;
    private long paidRevenue;
}