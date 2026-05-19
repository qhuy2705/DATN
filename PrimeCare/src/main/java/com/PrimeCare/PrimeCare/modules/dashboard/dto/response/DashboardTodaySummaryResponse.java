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
    private long uniquePatientsInRange;
    private long waitingServiceItems;
    private long grossPaidRevenue;
    private long refundedAmountForPaidInvoices;
    private long netPaidRevenue;
    private long refundsProcessed;
    private long paidRevenue;
}
