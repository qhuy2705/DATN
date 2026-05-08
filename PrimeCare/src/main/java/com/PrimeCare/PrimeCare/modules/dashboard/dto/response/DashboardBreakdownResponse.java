package com.PrimeCare.PrimeCare.modules.dashboard.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DashboardBreakdownResponse {
    private List<DashboardBreakdownItemResponse> branches;
    private List<DashboardBreakdownItemResponse> specialties;
    private List<DashboardBreakdownItemResponse> topDoctors;
    private List<DashboardBreakdownItemResponse> topServices;
}