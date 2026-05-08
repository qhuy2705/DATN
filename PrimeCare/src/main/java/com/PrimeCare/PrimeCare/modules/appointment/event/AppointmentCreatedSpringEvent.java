package com.PrimeCare.PrimeCare.modules.appointment.event;

import java.time.LocalDate;

public record AppointmentCreatedSpringEvent(
        Long appointmentId,
        Long branchId,
        LocalDate visitDate,
        String appointmentCode
) {
}
