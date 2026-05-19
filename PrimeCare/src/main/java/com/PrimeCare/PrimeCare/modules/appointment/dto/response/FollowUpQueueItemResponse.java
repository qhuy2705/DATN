package com.PrimeCare.PrimeCare.modules.appointment.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
public class FollowUpQueueItemResponse {
    private Long id;
    private Long appointmentId;
    private String appointmentCode;
    private String followUpType;
    private String followUpCategory;
    private String patientFullName;
    private String patientPhone;
    private String patientEmail;
    private String doctorName;
    private String specialtyName;
    private LocalDate originalVisitDate;
    private LocalTime originalSlotStart;
    private LocalTime originalSlotEnd;
    private String heldDoctorName;
    private LocalDate heldVisitDate;
    private LocalTime heldSlotStart;
    private LocalTime heldSlotEnd;
    private String holdStatus;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private Boolean followUpPending;
    private String contactStatus;
    private String patientResponseStatus;
}
