package com.PrimeCare.PrimeCare.modules.notification.messaging.event;

import lombok.Builder;

@Builder
public record AppointmentCreatedMailEvent(
        Long appointmentId,
        String patientEmail,
        String patientFullName,
        String appointmentCode,
        String branchName,
        String doctorName,
        String visitDate,
        String session,
        String slotStart,
        String slotEnd
) {
}
