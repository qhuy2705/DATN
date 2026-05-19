package com.PrimeCare.PrimeCare.modules.appointment.messaging.event;

import lombok.Builder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Builder
public record AppointmentResponseFallbackEmailEvent(
        Long appointmentId,
        String appointmentCode,
        String patientEmail,
        String patientName,
        String maskedPatientPhone,
        LocalDate visitDate,
        LocalTime etaStart,
        LocalTime etaEnd,
        String responseUrl,
        LocalDateTime tokenExpiresAt,
        String branchName,
        String branchHotline,
        Instant createdAt
) {
}
