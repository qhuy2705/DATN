package com.PrimeCare.PrimeCare.modules.triage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TriageKnowledgeBaseServiceTest {

    @Test
    void loadsKnowledgeBaseJsonAndRules() {
        TriageKnowledgeBaseService service = new TriageKnowledgeBaseService(new ObjectMapper());

        service.reload();

        assertThat(service.knowledgeBaseVersion()).contains("2026.05.01");
        assertThat(service.rulesetVersion()).isEqualTo("triage-rules-v1");
        assertThat(service.redFlags()).extracting(TriageDefinition::getCode)
                .contains("CHEST_PAIN", "DYSPNEA", "STROKE_SIGNS");
        assertThat(service.rules()).extracting(TriageRuleDefinition::getId)
                .contains("chest_pain_with_dyspnea", "heavy_bleeding_on_blood_thinner");
    }
}
