package com.PrimeCare.PrimeCare.modules.dashboard.controller;

import com.PrimeCare.PrimeCare.modules.billing.dto.response.RevenueReportResponse;
import com.PrimeCare.PrimeCare.modules.billing.service.RevenueReportService;
import com.PrimeCare.PrimeCare.modules.dashboard.dto.response.DashboardBreakdownResponse;
import com.PrimeCare.PrimeCare.modules.dashboard.dto.response.DashboardKpiResponse;
import com.PrimeCare.PrimeCare.modules.dashboard.dto.response.DashboardOverviewResponse;
import com.PrimeCare.PrimeCare.modules.dashboard.service.DashboardService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final RevenueReportService revenueReportService;

    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','OPERATIONS_ADMIN')")
    public ApiResponse<DashboardOverviewResponse> overview(
            @RequestParam(defaultValue = "today") String period,
            @RequestParam(required = false) java.time.LocalDate fromDate,
            @RequestParam(required = false) java.time.LocalDate toDate
    ) {
        return ApiResponse.ok("OK", dashboardService.overview(period, fromDate, toDate));
    }

    @GetMapping("/breakdown")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','OPERATIONS_ADMIN')")
    public ApiResponse<DashboardBreakdownResponse> breakdown(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "5") int topN
    ) {
        return ApiResponse.ok("OK", dashboardService.breakdown(days, topN));
    }

    @GetMapping("/kpis")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','OPERATIONS_ADMIN')")
    public ApiResponse<DashboardKpiResponse> kpis(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "10") int topN
    ) {
        return ApiResponse.ok("OK", dashboardService.kpis(days, topN));
    }

    @GetMapping("/revenue-report")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','OPERATIONS_ADMIN')")
    public ApiResponse<RevenueReportResponse> revenueReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "DAY") String groupBy
    ) {
        return ApiResponse.ok("OK", revenueReportService.getReport(from, to, groupBy));
    }
}