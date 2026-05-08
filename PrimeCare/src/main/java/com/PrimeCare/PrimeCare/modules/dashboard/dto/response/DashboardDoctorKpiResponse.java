package com.PrimeCare.PrimeCare.modules.dashboard.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardDoctorKpiResponse {
    private Long doctorId;
    private String doctorName;
    private long totalAppointments;
    private long checkedInAppointments;
    private long noShowAppointments;
    private double fillRate;
    private double noShowRate;
}