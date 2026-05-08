package com.PrimeCare.PrimeCare.modules.dashboard.dto.query;

import lombok.Getter;

@Getter
public class DashboardDoctorKpiRow {

    private final Long doctorId;
    private final String doctorName;
    private final Long totalAppointments;
    private final Long checkedInAppointments;
    private final Long noShowAppointments;

    public DashboardDoctorKpiRow(
            Long doctorId,
            String doctorName,
            Number totalAppointments,
            Number checkedInAppointments,
            Number noShowAppointments
    ) {
        this.doctorId = doctorId;
        this.doctorName = doctorName;
        this.totalAppointments = totalAppointments != null ? totalAppointments.longValue() : 0L;
        this.checkedInAppointments = checkedInAppointments != null ? checkedInAppointments.longValue() : 0L;
        this.noShowAppointments = noShowAppointments != null ? noShowAppointments.longValue() : 0L;
    }
}