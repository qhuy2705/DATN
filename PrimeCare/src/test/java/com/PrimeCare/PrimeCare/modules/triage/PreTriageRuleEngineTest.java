package com.PrimeCare.PrimeCare.modules.triage;

import com.PrimeCare.PrimeCare.modules.appointment.dto.request.PreTriageInputRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PreTriageRuleEngineTest {

    private final PreTriageRuleEngine ruleEngine = new PreTriageRuleEngine();

    @Test
    void chestPainWithDyspneaIsUrgentRedFlag() {
        PreTriageResult result = ruleEngine.assess(null, null, input(
                "TODAY",
                List.of("NONE"),
                "MILD_DIFFICULTY",
                List.of("CHEST_PAIN", "DYSPNEA")
        ), AiTriageExtractionResult.empty());

        assertThat(result.getLevel()).isEqualTo("RED_FLAG");
        assertThat(result.getPriority()).isEqualTo("URGENT");
    }

    @Test
    void strokeSignsAreUrgentRedFlag() {
        PreTriageResult result = ruleEngine.assess(null, null, input(
                "UNKNOWN",
                List.of(),
                "UNKNOWN",
                List.of("STROKE_SIGNS")
        ), AiTriageExtractionResult.empty());

        assertThat(result.getLevel()).isEqualTo("RED_FLAG");
        assertThat(result.getPriority()).isEqualTo("URGENT");
    }

    @Test
    void unableSelfCareIsUrgent() {
        PreTriageResult result = ruleEngine.assess(null, null, input(
                "UNKNOWN",
                List.of(),
                "UNABLE_SELF_CARE",
                List.of()
        ), AiTriageExtractionResult.empty());

        assertThat(result.getPriority()).isEqualTo("URGENT");
        assertThat(result.getLevel()).isIn("RED_FLAG", "WATCH");
    }

    @Test
    void diabetesWithSevereDifficultyIsPriorityWatch() {
        PreTriageResult result = ruleEngine.assess(null, null, input(
                "DAYS_2_3",
                List.of("DIABETES"),
                "SEVERE_DIFFICULTY",
                List.of("NONE")
        ), AiTriageExtractionResult.empty());

        assertThat(result.getLevel()).isEqualTo("WATCH");
        assertThat(result.getPriority()).isEqualTo("PRIORITY");
    }

    @Test
    void normalImpactWithoutRedFlagsIsRoutine() {
        PreTriageResult result = ruleEngine.assess(null, null, input(
                "WEEK_1",
                List.of("NONE"),
                "NORMAL",
                List.of("NONE")
        ), AiTriageExtractionResult.empty());

        assertThat(result.getLevel()).isEqualTo("NONE");
        assertThat(result.getPriority()).isEqualTo("ROUTINE");
    }

    @Test
    void aiRedFlagWithEmptyCardsIsUrgent() {
        AiTriageExtractionResult aiResult = AiTriageExtractionResult.builder()
                .redFlags(List.of("DYSPNEA"))
                .normalizedSummary("Bệnh nhân mô tả khó thở")
                .build();

        PreTriageResult result = ruleEngine.assess(null, null, input(
                "UNKNOWN",
                List.of(),
                "UNKNOWN",
                List.of()
        ), aiResult);

        assertThat(result.getLevel()).isEqualTo("RED_FLAG");
        assertThat(result.getPriority()).isEqualTo("URGENT");
        assertThat(result.getSource()).isEqualTo("AI");
    }

    @Test
    void cardRedFlagWithEmptyAiIsUrgent() {
        PreTriageResult result = ruleEngine.assess(null, null, input(
                "UNKNOWN",
                List.of(),
                "UNKNOWN",
                List.of("SEIZURE")
        ), AiTriageExtractionResult.empty());

        assertThat(result.getLevel()).isEqualTo("RED_FLAG");
        assertThat(result.getPriority()).isEqualTo("URGENT");
    }

    @Test
    void aiFailureFallsBackToRuleOnly() {
        TriageAiExtractor aiExtractor = mock(TriageAiExtractor.class);
        when(aiExtractor.extract(any(), any(), any())).thenThrow(new RuntimeException("provider down"));
        PreTriageService service = new PreTriageService(ruleEngine, aiExtractor);

        PreTriageResult result = service.assess(null, null, input(
                "UNKNOWN",
                List.of("NONE"),
                "NORMAL",
                List.of("NONE")
        ), null, null);

        assertThat(result.getLevel()).isEqualTo("NONE");
        assertThat(result.getPriority()).isEqualTo("ROUTINE");
        assertThat(result.getSource()).isEqualTo("RULE");
    }

    private PreTriageInputRequest input(
            String symptomOnset,
            List<String> chronicConditions,
            String functionalImpact,
            List<String> redFlags
    ) {
        PreTriageInputRequest input = new PreTriageInputRequest();
        input.setSymptomOnset(symptomOnset);
        input.setChronicConditions(chronicConditions);
        input.setFunctionalImpact(functionalImpact);
        input.setRedFlags(redFlags);
        return input;
    }
}
