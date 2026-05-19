package com.PrimeCare.PrimeCare.modules.appointment.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class PreTriageInputRequest {

    @Size(max = 32)
    private String symptomOnset;

    private List<@Size(max = 32) String> chronicConditions;

    private List<String> chronicConditionOthers;

    @Size(max = 32)
    private String functionalImpact;

    private List<@Size(max = 32) String> redFlags;
}
