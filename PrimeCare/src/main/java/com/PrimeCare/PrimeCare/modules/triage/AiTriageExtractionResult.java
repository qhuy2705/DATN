package com.PrimeCare.PrimeCare.modules.triage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTriageExtractionResult {

    @Builder.Default
    private List<String> detectedSymptoms = List.of();

    @Builder.Default
    private List<String> redFlags = List.of();

    @Builder.Default
    private List<String> riskModifiers = List.of();

    @Builder.Default
    private List<String> chronicConditionMentions = List.of();

    @Builder.Default
    private List<String> medicationRiskMentions = List.of();

    @Builder.Default
    private List<String> severityTerms = List.of();

    private String durationText;

    private String normalizedSummary;

    public static AiTriageExtractionResult empty() {
        return AiTriageExtractionResult.builder().build();
    }

    public boolean hasSignal() {
        return notEmpty(detectedSymptoms)
                || notEmpty(redFlags)
                || notEmpty(riskModifiers)
                || notEmpty(chronicConditionMentions)
                || notEmpty(medicationRiskMentions)
                || notEmpty(severityTerms)
                || notBlank(durationText)
                || notBlank(normalizedSummary);
    }

    private boolean notEmpty(List<String> values) {
        return values != null && !values.isEmpty();
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
