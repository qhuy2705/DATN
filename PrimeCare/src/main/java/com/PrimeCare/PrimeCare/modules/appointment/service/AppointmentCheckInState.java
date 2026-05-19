package com.PrimeCare.PrimeCare.modules.appointment.service;

import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.enums.ArrivalStatus;

import java.time.LocalDateTime;

final class AppointmentCheckInState {

    private AppointmentCheckInState() {
    }

    static void apply(Appointment appointment, User staff, LocalDateTime checkedInAt) {
        if (appointment == null || staff == null || checkedInAt == null) {
            return;
        }

        appointment.setArrivalStatus(ArrivalStatus.ARRIVED);
        if (appointment.getArrivedAt() == null) {
            appointment.setArrivedAt(checkedInAt);
        }
        if (appointment.getArrivedBy() == null) {
            appointment.setArrivedBy(staff);
        }
        appointment.setStatus(AppointmentStatus.CHECKED_IN);
        appointment.setCheckedInAt(checkedInAt);
        appointment.setCheckedInBy(staff);
        AppointmentTimingPolicy.applyCheckInTiming(appointment, checkedInAt);
    }
}
