package com.PrimeCare.PrimeCare.modules.triage;

import com.PrimeCare.PrimeCare.modules.appointment.dto.request.PreTriageInputRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PreTriageGoldenCasesTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TriageKnowledgeBaseService knowledgeBaseService = knowledgeBase();
    private final TriageDictionaryMatcher dictionaryMatcher = new TriageDictionaryMatcher(knowledgeBaseService);
    private final PreTriageRuleEngine ruleEngine = new PreTriageRuleEngine();

    @Test
    void goldenCasesPassWithRuleAndDictionaryOnly() throws Exception {
        try (InputStream inputStream = new ClassPathResource("triage/golden-cases.vi.json").getInputStream()) {
            JsonNode cases = objectMapper.readTree(inputStream).path("items");
            assertThat(cases).isNotEmpty();

            for (JsonNode testCase : cases) {
                PreTriageInputRequest input = inputFrom(testCase);
                String reasonForVisit = textOrNull(testCase.path("reasonForVisit"));
                TriageDictionaryMatchResult dictionaryResult = dictionaryMatcher.match(
                        reasonForVisit,
                        null,
                        input.getChronicConditions(),
                        input.getChronicConditionOthers()
                );

                PreTriageResult result = ruleEngine.assess(
                        reasonForVisit,
                        null,
                        input,
                        AiTriageExtractionResult.empty(),
                        dictionaryResult,
                        knowledgeBaseService.rules(),
                        knowledgeBaseService.knowledgeBaseVersion(),
                        knowledgeBaseService.rulesetVersion(),
                        null
                );

                assertCase(testCase, result);
                assertThat(result.getSource()).as(testCase.path("id").asText()).isEqualTo("RULE");
            }
        }
    }

    private void assertCase(JsonNode testCase, PreTriageResult result) {
        String id = testCase.path("id").asText();
        if (testCase.has("expectedPriority")) {
            assertThat(result.getPriority()).as(id).isEqualTo(testCase.path("expectedPriority").asText());
        }
        if (testCase.has("expectedPriorityOneOf")) {
            assertThat(result.getPriority()).as(id).isIn(stringList(testCase.path("expectedPriorityOneOf")));
        }
        if (testCase.has("expectedNotPriority")) {
            assertThat(result.getPriority()).as(id).isNotEqualTo(testCase.path("expectedNotPriority").asText());
        }
        if (testCase.has("expectedLevel")) {
            assertThat(result.getLevel()).as(id).isEqualTo(testCase.path("expectedLevel").asText());
        }
        if (testCase.has("expectedConfidence")) {
            assertThat(result.getConfidenceLevel()).as(id).isEqualTo(testCase.path("expectedConfidence").asText());
        }
        if (testCase.has("expectedFlags")) {
            assertThat(result.getFlags()).as(id).containsAll(stringList(testCase.path("expectedFlags")));
        }
        if (testCase.has("expectedRiskModifiers")) {
            assertThat(result.getFlags()).as(id).containsAll(stringList(testCase.path("expectedRiskModifiers")));
        }
    }

    private PreTriageInputRequest inputFrom(JsonNode testCase) {
        PreTriageInputRequest input = new PreTriageInputRequest();
        input.setSymptomOnset(textOrDefault(testCase.path("symptomOnset"), "UNKNOWN"));
        input.setFunctionalImpact(textOrDefault(testCase.path("functionalImpact"), "UNKNOWN"));
        input.setChronicConditions(testCase.has("chronicConditions")
                ? stringList(testCase.path("chronicConditions"))
                : List.of());
        input.setChronicConditionOthers(testCase.has("chronicConditionOthers")
                ? stringList(testCase.path("chronicConditionOthers"))
                : List.of());
        input.setRedFlags(testCase.has("redFlags")
                ? stringList(testCase.path("redFlags"))
                : List.of());
        return input;
    }

    private List<String> stringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                if (item != null && item.isTextual()) {
                    values.add(item.asText());
                }
            }
        }
        return values;
    }

    private String textOrDefault(JsonNode node, String defaultValue) {
        String value = textOrNull(node);
        return value != null ? value : defaultValue;
    }

    private String textOrNull(JsonNode node) {
        return node != null && node.isTextual() && !node.asText().isBlank() ? node.asText() : null;
    }

    private TriageKnowledgeBaseService knowledgeBase() {
        TriageKnowledgeBaseService service = new TriageKnowledgeBaseService(objectMapper);
        service.reload();
        return service;
    }
}
