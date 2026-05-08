package com.PrimeCare.PrimeCare.modules.encounter.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class EncounterAiSummaryResponse {
    private String provider;
    private String disclaimer;
    private String quickSummary;
    private List<String> highlightedResults;
    private List<String> riskFlags;
    private List<String> nextStepSuggestions;
    private String draftDoctorNote;
}
