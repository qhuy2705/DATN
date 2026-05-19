package com.PrimeCare.PrimeCare.modules.appointment.dto.response;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class AppointmentBookingRestrictionSummaryResponse {
    Long appointmentId;
    Long patientId;
    String patientName;
    String patientPhone;
    String patientEmail;
    Integer monthlyScore;
    String level;
    String status;
    Boolean restricted;
    LocalDateTime restrictionExpiresAt;
    String latestViolationType;
    LocalDateTime latestViolationAt;
    String message;
}
