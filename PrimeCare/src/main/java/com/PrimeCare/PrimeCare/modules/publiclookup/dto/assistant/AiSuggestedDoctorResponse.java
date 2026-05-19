package com.PrimeCare.PrimeCare.modules.publiclookup.dto.assistant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSuggestedDoctorResponse {
    private Long doctorId;
    private String doctorName;
    private Long specialtyId;
    private String specialtyName;
    private Long facilityId;
    private String facilityName;
    private String facilityAddress;
    private String displayTitle;
}
