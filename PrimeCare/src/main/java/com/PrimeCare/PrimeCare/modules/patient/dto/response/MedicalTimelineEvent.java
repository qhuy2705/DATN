package com.PrimeCare.PrimeCare.modules.patient.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Represents a single event in the patient's medical timeline.
 */
@Data
@Builder
public class MedicalTimelineEvent {
    private String type; // ENCOUNTER, PRESCRIPTION, SERVICE_RESULT, ALLERGY
    private Long referenceId;
    private String title;
    private String subtitle;
    private String description;
    private String status;
    private LocalDateTime occurredAt;
    private String performedBy;
}
