package com.PrimeCare.PrimeCare.modules.patient.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PatientAllergyResponse {
    private Long id;
    private Long patientId;
    private String allergenName;
    private String allergyType;
    private String severity;
    private String reaction;
    private Long notedById;
    private String notedByName;
    private LocalDateTime createdAt;
}
