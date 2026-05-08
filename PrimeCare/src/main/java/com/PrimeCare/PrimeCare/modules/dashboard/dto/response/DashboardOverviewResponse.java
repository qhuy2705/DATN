package com.PrimeCare.PrimeCare.modules.dashboard.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DashboardOverviewResponse {

    private DashboardTodaySummaryResponse today;
    private List<DashboardDailyMetricPointResponse> appointmentSeries;
    private List<DashboardDailyMetricPointResponse> revenueSeries;
    private List<DashboardDailyMetricPointResponse> noShowSeries;
}