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
public class PreTriageResult {

    private String level;

    private String priority;

    @Builder.Default
    private List<String> flags = List.of();

    @Builder.Default
    private List<String> reasons = List.of();

    private String summary;

    private double confidence;

    private String confidenceLevel;

    private String source;

    @Builder.Default
    private List<TriageMatchedTerm> matchedTerms = List.of();

    @Builder.Default
    private List<TriageMatchedRule> matchedRules = List.of();

    private String knowledgeBaseVersion;

    private String rulesetVersion;

    private String aiModelVersion;
}
