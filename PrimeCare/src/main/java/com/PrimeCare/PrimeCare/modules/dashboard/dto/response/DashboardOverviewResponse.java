package com.PrimeCare.PrimeCare.modules.dashboard.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class DashboardOverviewResponse {

    private LocalDate fromDate;
    private LocalDate toDate;
    private DashboardTodaySummaryResponse today;
    private List<DashboardDailyMetricPointResponse> appointmentSeries;
    private List<DashboardDailyMetricPointResponse> revenueSeries;
    private List<DashboardDailyMetricPointResponse> noShowSeries;
}
