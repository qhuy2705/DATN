package com.PrimeCare.PrimeCare.modules.publiclookup.dto.assistant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAvailableSlotResponse {
    private String slotId;
    private Long doctorId;
    private String doctorName;
    private Long specialtyId;
    private String specialtyName;
    private Long facilityId;
    private String facilityName;
    private String facilityAddress;
    private LocalDate appointmentDate;
    private String session;
    private String startTime;
    private String endTime;
    private String displayLabel;
}
