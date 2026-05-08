package com.PrimeCare.PrimeCare.modules.dashboard.dto.query;

import lombok.Getter;

@Getter
public class DashboardBranchRevenueRow {

    private final Long branchId;
    private final String branchName;
    private final Long paidRevenue;

    public DashboardBranchRevenueRow(Long branchId, String branchName, Number paidRevenue) {
        this.branchId = branchId;
        this.branchName = branchName;
        this.paidRevenue = paidRevenue != null ? paidRevenue.longValue() : 0L;
    }
}