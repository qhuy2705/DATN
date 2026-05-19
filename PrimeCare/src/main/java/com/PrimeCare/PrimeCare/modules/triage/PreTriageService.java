package com.PrimeCare.PrimeCare.modules.triage;

import com.PrimeCare.PrimeCare.modules.appointment.dto.request.PreTriageInputRequest;
import com.PrimeCare.PrimeCare.shared.enums.Gender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Slf4j
@Service
public class PreTriageService {

    private final PreTriageRuleEngine ruleEngine;
    private final TriageAiExtractor aiExtractor;
    private final TriageDictionaryMatcher dictionaryMatcher;
    private final TriageKnowledgeBaseService knowledgeBaseService;

    @Autowired
    public PreTriageService(
            PreTriageRuleEngine ruleEngine,
            TriageAiExtractor aiExtractor,
            TriageDictionaryMatcher dictionaryMatcher,
            TriageKnowledgeBaseService knowledgeBaseService
    ) {
        this.ruleEngine = ruleEngine;
        this.aiExtractor = aiExtractor;
        this.dictionaryMatcher = dictionaryMatcher;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    public PreTriageService(PreTriageRuleEngine ruleEngine, TriageAiExtractor aiExtractor) {
        this.ruleEngine = ruleEngine;
        this.aiExtractor = aiExtractor;
        this.dictionaryMatcher = null;
        this.knowledgeBaseService = null;
    }

    public PreTriageResult assess(
            String reasonForVisit,
            String patientNote,
            PreTriageInputRequest input,
            LocalDate patientDob,
            Gender patientGender
    ) {
        TriageDictionaryMatchResult dictionaryResult = dictionaryMatcher != null
                ? dictionaryMatcher.match(
                        reasonForVisit,
                        patientNote,
                        input != null ? input.getChronicConditions() : null,
                        input != null ? input.getChronicConditionOthers() : null
                )
                : TriageDictionaryMatchResult.empty();

        AiTriageExtractionResult aiResult = AiTriageExtractionResult.empty();
        try {
            AiTriageExtractionResult extracted = aiExtractor != null
                    ? aiExtractor.extract(reasonForVisit, patientNote, input)
                    : AiTriageExtractionResult.empty();
            aiResult = extracted != null ? extracted : AiTriageExtractionResult.empty();
        } catch (Exception ex) {
            log.warn("AI triage extractor failed before rule assessment: {}", ex.getMessage());
        }
        String aiModelVersion = aiResult.hasSignal() && aiExtractor != null ? aiExtractor.aiModelVersion() : null;
        return ruleEngine.assess(
                reasonForVisit,
                patientNote,
                input,
                aiResult,
                dictionaryResult,
                knowledgeBaseService != null ? knowledgeBaseService.rules() : java.util.List.of(),
                knowledgeBaseService != null ? knowledgeBaseService.knowledgeBaseVersion() : "unknown",
                knowledgeBaseService != null ? knowledgeBaseService.rulesetVersion() : "unknown",
                aiModelVersion
        );
    }
}
