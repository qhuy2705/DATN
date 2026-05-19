package com.PrimeCare.PrimeCare.modules.dashboard.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class DashboardKpiResponse {
    private LocalDate fromDate;
    private LocalDate toDate;
    private List<DashboardDoctorKpiResponse> doctorKpis;
    private List<DashboardSpecialtyKpiResponse> specialtyKpis;
    private List<DashboardBranchRevenueResponse> branchRevenue;
}
