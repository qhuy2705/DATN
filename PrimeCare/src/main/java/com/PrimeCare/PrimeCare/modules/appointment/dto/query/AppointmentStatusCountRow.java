package com.PrimeCare.PrimeCare.modules.appointment.dto.query;

import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;

public interface AppointmentStatusCountRow {
    AppointmentStatus getStatus();
    long getCount();
}