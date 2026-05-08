package com.PrimeCare.PrimeCare.modules.notification.messaging.event;

public record AppointmentConfirmedMailReadyEvent(
        Long appointmentId,
        Long jobId,
        String patientEmail,
        String patientFullName,
        String appointmentCode,
        String branchName,
        String doctorName,
        String specialtyName,
        String visitDate,
        String session,
        String slotStart,
        String slotEnd,
        String pdfS3Key
) {
}
