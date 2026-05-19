package com.PrimeCare.PrimeCare.modules.triage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TriageDictionaryMatcherTest {

    private final TriageKnowledgeBaseService knowledgeBaseService = knowledgeBase();
    private final TriageDictionaryMatcher matcher = new TriageDictionaryMatcher(knowledgeBaseService);

    @Test
    void matchesVietnameseRedFlagsAndRiskModifiers() {
        TriageDictionaryMatchResult result = matcher.match(
                "Tức ngực, thở không nổi từ sáng",
                null,
                List.of(),
                List.of("Đang chạy thận")
        );

        assertThat(result.codesByCategory("RED_FLAG")).contains("CHEST_PAIN", "DYSPNEA");
        assertThat(result.codesByCategory("RISK_MODIFIER")).contains("DIALYSIS");
    }

    @Test
    void matchesAccentFoldedTermsAndAvoidsShortTermInsideOtherWords() {
        TriageDictionaryMatchResult result = matcher.match(
                "Kho chiu nhe",
                null,
                List.of(),
                List.of("Hen phe quan")
        );

        assertThat(result.codesByCategory("RISK_MODIFIER")).contains("RESPIRATORY_RISK");
        assertThat(result.codesByCategory("SYMPTOM")).doesNotContain("COUGH");
    }

    private TriageKnowledgeBaseService knowledgeBase() {
        TriageKnowledgeBaseService service = new TriageKnowledgeBaseService(new ObjectMapper());
        service.reload();
        return service;
    }
}
