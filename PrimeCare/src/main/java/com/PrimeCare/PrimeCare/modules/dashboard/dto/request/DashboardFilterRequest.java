package com.PrimeCare.PrimeCare.modules.dashboard.dto.request;

import lombok.Data;

import java.time.LocalDate;

@Data
public class DashboardFilterRequest {
    private LocalDate fromDate;
    private LocalDate toDate;
    private Long branchId;
    private Long specialtyId;
    private Long doctorId;
}