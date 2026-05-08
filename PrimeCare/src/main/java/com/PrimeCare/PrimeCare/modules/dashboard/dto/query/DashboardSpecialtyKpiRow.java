package com.PrimeCare.PrimeCare.modules.dashboard.dto.query;

import lombok.Getter;

@Getter
public class DashboardSpecialtyKpiRow {

    private final Long specialtyId;
    private final String specialtyName;
    private final Long totalAppointments;
    private final Long noShowAppointments;

    public DashboardSpecialtyKpiRow(
            Long specialtyId,
            String specialtyName,
            Number totalAppointments,
            Number noShowAppointments
    ) {
        this.specialtyId = specialtyId;
        this.specialtyName = specialtyName;
        this.totalAppointments = totalAppointments != null ? totalAppointments.longValue() : 0L;
        this.noShowAppointments = noShowAppointments != null ? noShowAppointments.longValue() : 0L;
    }
}