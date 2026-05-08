package com.PrimeCare.PrimeCare.modules.notification.messaging.event;

public record ResultReadyMailEvent(
        Long appointmentId,
        Long encounterId,
        String encounterCode,
        String appointmentCode,
        String patientFullName,
        String patientEmail,
        String doctorName,
        String branchName
) {
}
