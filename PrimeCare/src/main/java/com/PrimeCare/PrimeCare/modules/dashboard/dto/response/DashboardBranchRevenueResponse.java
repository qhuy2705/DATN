package com.PrimeCare.PrimeCare.modules.dashboard.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardBranchRevenueResponse {
    private Long branchId;
    private String branchName;
    private long netPaidRevenue;
    private long paidRevenue;
}
