package com.PrimeCare.PrimeCare.modules.dashboard.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardSpecialtyKpiResponse {
    private Long specialtyId;
    private String specialtyName;
    private long totalAppointments;
    private long noShowAppointments;
    private double noShowRate;
}