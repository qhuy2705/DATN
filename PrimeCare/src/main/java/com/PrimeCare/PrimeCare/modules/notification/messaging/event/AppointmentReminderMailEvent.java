package com.PrimeCare.PrimeCare.modules.notification.messaging.event;

public record AppointmentReminderMailEvent(
        Long appointmentId,
        String appointmentCode,
        String patientFullName,
        String patientEmail,
        String doctorName,
        String branchName,
        String visitDate,
        String slotStart,
        String slotEnd
) {
}
