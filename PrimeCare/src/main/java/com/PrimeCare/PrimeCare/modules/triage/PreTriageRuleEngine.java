package com.PrimeCare.PrimeCare.modules.triage;

import com.PrimeCare.PrimeCare.modules.appointment.dto.request.PreTriageInputRequest;
import com.PrimeCare.PrimeCare.shared.utils.StringUtil;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class PreTriageRuleEngine {

    private static final Set<String> HIGH_RISK_CONDITIONS = Set.of(
            "CARDIOVASCULAR",
            "DIABETES",
            "RESPIRATORY",
            "CANCER",
            "IMMUNODEFICIENCY",
            "PREGNANCY",
            "ELDERLY"
    );

    private static final Set<String> IMMEDIATE_URGENT_FLAGS = Set.of(
            "DYSPNEA",
            "FAINTING",
            "SEIZURE",
            "STROKE_SIGNS",
            "HEAVY_BLEEDING",
            "ALTERED_MENTAL_STATUS",
            "SEVERE_TRAUMA"
    );

    private static final Map<String, String> CHRONIC_RISK_CODES = Map.of(
            "CARDIOVASCULAR", "CARDIOVASCULAR_RISK",
            "DIABETES", "DIABETES_RISK",
            "RESPIRATORY", "RESPIRATORY_RISK",
            "CANCER", "COMPLEX_CHRONIC_DISEASE",
            "IMMUNODEFICIENCY", "IMMUNOCOMPROMISED",
            "PREGNANCY", "PREGNANCY",
            "ELDERLY", "ELDERLY"
    );

    public PreTriageResult assess(
            String reasonForVisit,
            String patientNote,
            PreTriageInputRequest input,
            AiTriageExtractionResult aiResult
    ) {
        return assess(
                reasonForVisit,
                patientNote,
                input,
                aiResult,
                TriageDictionaryMatchResult.empty(),
                List.of(),
                "unknown",
                "unknown",
                null
        );
    }

    public PreTriageResult assess(
            String reasonForVisit,
            String patientNote,
            PreTriageInputRequest input,
            AiTriageExtractionResult aiResult,
            TriageDictionaryMatchResult dictionaryResult,
            List<TriageRuleDefinition> triageRules,
            String knowledgeBaseVersion,
            String rulesetVersion,
            String aiModelVersion
    ) {
        String symptomOnset = input != null
                ? TriagePriorityNormalizer.normalizeSymptomOnset(input.getSymptomOnset())
                : "UNKNOWN";
        String functionalImpact = input != null
                ? TriagePriorityNormalizer.normalizeFunctionalImpact(input.getFunctionalImpact())
                : "UNKNOWN";
        List<String> chronicConditions = input != null
                ? TriagePriorityNormalizer.removeNone(TriagePriorityNormalizer.normalizeChronicConditions(input.getChronicConditions()))
                : List.of();
        List<String> chronicConditionOthers = input != null
                ? TriagePriorityNormalizer.normalizeChronicConditionOthers(input.getChronicConditionOthers())
                : List.of();
        List<String> cardRedFlags = input != null
                ? TriagePriorityNormalizer.removeNone(TriagePriorityNormalizer.normalizeRedFlags(input.getRedFlags()))
                : List.of();

        AiTriageExtractionResult safeAi = aiResult != null ? aiResult : AiTriageExtractionResult.empty();
        List<TriageMatchedTerm> dictionaryTerms = dictionaryResult != null && dictionaryResult.getMatchedTerms() != null
                ? dictionaryResult.getMatchedTerms()
                : List.of();
        List<TriageMatchedTerm> matchedTerms = new ArrayList<>(dictionaryTerms);
        appendAiMatchedTerms(matchedTerms, safeAi);

        Set<String> dictionaryRedFlags = codesByCategory(dictionaryTerms, "RED_FLAG");
        Set<String> dictionaryRiskModifiers = codesByCategories(dictionaryTerms, Set.of("RISK_MODIFIER", "MEDICATION_RISK"));
        Set<String> dictionarySymptoms = codesByCategory(dictionaryTerms, "SYMPTOM");
        Set<String> dictionarySeverity = codesByCategory(dictionaryTerms, "SEVERITY");
        Set<String> aiRedFlags = new LinkedHashSet<>(TriagePriorityNormalizer.normalizeAiRedFlags(safeAi.getRedFlags()));
        Set<String> aiRiskModifiers = new LinkedHashSet<>(TriagePriorityNormalizer.normalizeRiskModifiers(safeAi.getRiskModifiers()));

        Set<String> textRedFlags = new LinkedHashSet<>(dictionaryRedFlags);
        textRedFlags.addAll(aiRedFlags);
        Set<String> redFlags = new LinkedHashSet<>(cardRedFlags);
        redFlags.addAll(textRedFlags);

        Set<String> riskModifiers = new LinkedHashSet<>();
        for (String chronicCondition : chronicConditions) {
            String riskCode = CHRONIC_RISK_CODES.get(chronicCondition);
            if (riskCode != null) {
                riskModifiers.add(riskCode);
            }
        }
        riskModifiers.addAll(dictionaryRiskModifiers);
        riskModifiers.addAll(aiRiskModifiers);
        if (aiRiskModifiers.isEmpty()
                && safeAi.getChronicConditionMentions() != null
                && !safeAi.getChronicConditionMentions().isEmpty()) {
            riskModifiers.add("UNKNOWN_RISK_CONDITION");
        }
        if (!chronicConditionOthers.isEmpty()
                && dictionaryRiskModifiers.isEmpty()
                && aiRiskModifiers.isEmpty()
                && (safeAi.getChronicConditionMentions() == null || safeAi.getChronicConditionMentions().isEmpty())) {
            riskModifiers.add("UNKNOWN_RISK_CONDITION");
        }

        Set<String> flags = new LinkedHashSet<>(redFlags);
        flags.addAll(riskModifiers);
        addContextFlags(flags, symptomOnset, functionalImpact, chronicConditions);

        List<String> reasons = new ArrayList<>();
        for (String redFlag : redFlags) {
            reasons.add(redFlagReason(redFlag, cardRedFlags.contains(redFlag), dictionaryRedFlags.contains(redFlag), aiRedFlags.contains(redFlag)));
        }
        for (String chronicOther : chronicConditionOthers) {
            reasons.add("Bệnh nhân khai báo bệnh nền/thông tin y tế khác cần staff xác minh: " + chronicOther);
        }

        boolean highRisk = chronicConditions.stream().anyMatch(HIGH_RISK_CONDITIONS::contains)
                || riskModifiers.stream().anyMatch(this::isHighRiskModifier);
        boolean textHasSevereChestPain = hasSevereChestPain(reasonForVisit, patientNote);
        boolean textHasPrioritySeverity = hasPrioritySeverityTerm(reasonForVisit, patientNote, safeAi)
                || dictionarySeverity.contains("MODERATE")
                || dictionarySeverity.contains("SEVERE");
        boolean acuteSymptom = "TODAY".equals(symptomOnset)
                || "DAYS_2_3".equals(symptomOnset)
                || !dictionarySymptoms.isEmpty();

        Set<String> ruleCodes = new LinkedHashSet<>();
        ruleCodes.addAll(redFlags);
        ruleCodes.addAll(riskModifiers);
        ruleCodes.addAll(dictionarySymptoms);
        ruleCodes.addAll(dictionarySeverity);
        if (!"UNKNOWN".equals(symptomOnset)) {
            ruleCodes.add("ONSET_" + symptomOnset);
        }
        if (!"UNKNOWN".equals(functionalImpact)) {
            ruleCodes.add("IMPACT_" + functionalImpact);
        }

        List<TriageMatchedRule> matchedRules = new ArrayList<>();
        applyKnowledgeRules(matchedRules, ruleCodes, triageRules);
        applyBuiltInRules(
                matchedRules,
                reasons,
                redFlags,
                riskModifiers,
                textRedFlags,
                symptomOnset,
                functionalImpact,
                chronicConditions,
                chronicConditionOthers,
                highRisk,
                textHasSevereChestPain,
                textHasPrioritySeverity,
                acuteSymptom
        );

        TriageMatchedRule strongestRule = matchedRules.stream()
                .min(Comparator.comparingInt(rule -> priorityRank(rule.getPriority())))
                .orElse(null);
        String resolvedPriority = strongestRule != null
                ? TriagePriorityNormalizer.normalizePriority(strongestRule.getPriority())
                : TriagePriorityNormalizer.ROUTINE;
        String level = strongestRule != null && strongestRule.getLevel() != null
                ? strongestRule.getLevel()
                : TriagePriorityNormalizer.ROUTINE.equals(resolvedPriority) ? TriagePriorityNormalizer.NONE : TriagePriorityNormalizer.WATCH;

        for (TriageMatchedRule matchedRule : matchedRules) {
            reasons.add("Khớp rule: " + matchedRule.getReason());
        }

        if (reasons.isEmpty()) {
            reasons.add("Chưa ghi nhận dấu hiệu ưu tiên rõ từ mô tả hoặc lựa chọn sàng lọc");
        }

        boolean nonAiSignal = !cardRedFlags.isEmpty()
                || !dictionaryTerms.isEmpty()
                || !chronicConditions.isEmpty()
                || !chronicConditionOthers.isEmpty()
                || !"UNKNOWN".equals(functionalImpact)
                || !"UNKNOWN".equals(symptomOnset);
        String source = resolveSource(safeAi.hasSignal(), nonAiSignal);
        String confidenceLevel = confidenceLevelFor(
                resolvedPriority,
                source,
                cardRedFlags,
                dictionaryRedFlags,
                aiRedFlags,
                matchedRules,
                hasSparseInput(symptomOnset, functionalImpact, chronicConditions, cardRedFlags, reasonForVisit, patientNote)
                        && chronicConditionOthers.isEmpty()
                        && dictionaryTerms.isEmpty()
        );
        return PreTriageResult.builder()
                .level(level)
                .priority(resolvedPriority)
                .flags(new ArrayList<>(flags))
                .reasons(reasons.stream().distinct().toList())
                .summary(summaryFor(resolvedPriority))
                .confidence(confidenceValue(confidenceLevel))
                .confidenceLevel(confidenceLevel)
                .source(source)
                .matchedTerms(matchedTerms)
                .matchedRules(matchedRules)
                .knowledgeBaseVersion(knowledgeBaseVersion)
                .rulesetVersion(rulesetVersion)
                .aiModelVersion(aiModelVersion)
                .build();
    }

    private boolean applyUrgentRules(
            boolean current,
            List<String> reasons,
            Set<String> redFlags,
            Set<String> textRedFlags,
            String symptomOnset,
            String functionalImpact,
            boolean highRisk,
            boolean textHasSevereChestPain
    ) {
        boolean urgent = current;

        if (redFlags.contains("CHEST_PAIN") && redFlags.contains("DYSPNEA")) {
            reasons.add("Bệnh nhân có dấu hiệu đau ngực kèm khó thở");
            urgent = true;
        }
        if (redFlags.contains("ALLERGIC_REACTION") && redFlags.contains("DYSPNEA")) {
            reasons.add("Bệnh nhân có dấu hiệu dị ứng kèm khó thở");
            urgent = true;
        }
        if (textHasSevereChestPain) {
            reasons.add("Mô tả tự do có dấu hiệu đau ngực dữ dội");
            urgent = true;
        }
        if (redFlags.contains("SEVERE_PAIN") && "TODAY".equals(symptomOnset)) {
            reasons.add("Bệnh nhân khai đau nhiều xuất hiện trong ngày");
            urgent = true;
        }
        if ("UNABLE_SELF_CARE".equals(functionalImpact)) {
            reasons.add("Bệnh nhân khai không tự sinh hoạt được");
            urgent = true;
        }
        if (redFlags.stream().anyMatch(IMMEDIATE_URGENT_FLAGS::contains)) {
            if (redFlags.contains("DYSPNEA")) {
                reasons.add(sourceAwareReason("khó thở", textRedFlags.contains("DYSPNEA")));
            }
            if (redFlags.contains("FAINTING")) {
                reasons.add(sourceAwareReason("ngất", textRedFlags.contains("FAINTING")));
            }
            if (redFlags.contains("SEIZURE")) {
                reasons.add(sourceAwareReason("co giật", textRedFlags.contains("SEIZURE")));
            }
            if (redFlags.contains("STROKE_SIGNS")) {
                reasons.add(sourceAwareReason("dấu hiệu đột quỵ", textRedFlags.contains("STROKE_SIGNS")));
            }
            if (redFlags.contains("HEAVY_BLEEDING")) {
                reasons.add(sourceAwareReason("chảy máu nhiều", textRedFlags.contains("HEAVY_BLEEDING")));
            }
            urgent = true;
        }
        if (!redFlags.isEmpty() && highRisk) {
            reasons.add("Có dấu hiệu cảnh báo kèm bệnh nền hoặc yếu tố nguy cơ cao");
            urgent = true;
        }

        return urgent;
    }

    private void appendAiMatchedTerms(List<TriageMatchedTerm> matchedTerms, AiTriageExtractionResult aiResult) {
        if (aiResult == null || !aiResult.hasSignal()) {
            return;
        }
        appendAiTerms(matchedTerms, TriagePriorityNormalizer.normalizeAiRedFlags(aiResult.getRedFlags()), "RED_FLAG");
        appendAiTerms(matchedTerms, TriagePriorityNormalizer.normalizeRiskModifiers(aiResult.getRiskModifiers()), "RISK_MODIFIER");
        appendAiTerms(matchedTerms, aiResult.getSeverityTerms(), "SEVERITY");
        appendAiEvidenceTerms(matchedTerms, aiResult.getDetectedSymptoms(), "AI_DETECTED_SYMPTOM", "SYMPTOM");
        appendAiEvidenceTerms(matchedTerms, aiResult.getChronicConditionMentions(), "UNKNOWN_RISK_CONDITION", "RISK_MODIFIER");
        appendAiEvidenceTerms(matchedTerms, aiResult.getMedicationRiskMentions(), "MEDICATION_RISK_MENTION", "MEDICATION_RISK");
    }

    private void appendAiTerms(List<TriageMatchedTerm> matchedTerms, List<String> codes, String category) {
        if (codes == null || codes.isEmpty()) {
            return;
        }
        Set<String> existing = existingMatchedTermKeys(matchedTerms);
        for (String code : codes) {
            String normalized = TriagePriorityNormalizer.normalizeToken(code);
            if (normalized == null) {
                continue;
            }
            String key = category + ":" + normalized + ":AI";
            if (existing.add(key)) {
                matchedTerms.add(TriageMatchedTerm.builder()
                        .term(normalized)
                        .code(normalized)
                        .label(normalized)
                        .category(category)
                        .source("AI")
                        .evidenceText(normalized)
                        .build());
            }
        }
    }

    private void appendAiEvidenceTerms(List<TriageMatchedTerm> matchedTerms, List<String> values, String code, String category) {
        if (values == null || values.isEmpty()) {
            return;
        }
        Set<String> existing = existingMatchedTermKeys(matchedTerms);
        for (String value : values) {
            String term = StringUtil.trimToNull(value);
            if (term == null) {
                continue;
            }
            String key = category + ":" + code + ":" + term.toLowerCase(Locale.ROOT) + ":AI";
            if (existing.add(key)) {
                matchedTerms.add(TriageMatchedTerm.builder()
                        .term(term)
                        .code(code)
                        .label(term)
                        .category(category)
                        .source("AI")
                        .evidenceText(term)
                        .build());
            }
        }
    }

    private Set<String> existingMatchedTermKeys(List<TriageMatchedTerm> matchedTerms) {
        Set<String> keys = new LinkedHashSet<>();
        if (matchedTerms == null) {
            return keys;
        }
        for (TriageMatchedTerm term : matchedTerms) {
            if (term == null) {
                continue;
            }
            keys.add(term.getCategory() + ":" + term.getCode() + ":" + term.getSource());
        }
        return keys;
    }

    private Set<String> codesByCategory(List<TriageMatchedTerm> terms, String category) {
        return codesByCategories(terms, Set.of(category));
    }

    private Set<String> codesByCategories(List<TriageMatchedTerm> terms, Set<String> categories) {
        Set<String> codes = new LinkedHashSet<>();
        if (terms == null || terms.isEmpty()) {
            return codes;
        }
        for (TriageMatchedTerm term : terms) {
            if (term == null || term.getCode() == null || term.getCategory() == null) {
                continue;
            }
            if (categories.contains(term.getCategory())) {
                codes.add(term.getCode());
            }
        }
        return codes;
    }

    private void applyKnowledgeRules(
            List<TriageMatchedRule> matchedRules,
            Set<String> ruleCodes,
            List<TriageRuleDefinition> triageRules
    ) {
        if (triageRules == null || triageRules.isEmpty()) {
            return;
        }
        for (TriageRuleDefinition rule : triageRules) {
            if (rule == null || rule.getId() == null || rule.getPriority() == null) {
                continue;
            }
            List<String> ifAll = safeList(rule.getIfAll());
            List<String> ifAny = safeList(rule.getIfAny());
            boolean allMatched = ifAll.isEmpty() || ruleCodes.containsAll(ifAll);
            boolean anyMatched = ifAny.isEmpty() || ifAny.stream().anyMatch(ruleCodes::contains);
            if (!allMatched || !anyMatched) {
                continue;
            }
            List<String> matchedCodes = new ArrayList<>();
            matchedCodes.addAll(ifAll.stream().filter(ruleCodes::contains).toList());
            matchedCodes.addAll(ifAny.stream().filter(ruleCodes::contains).toList());
            addMatchedRule(
                    matchedRules,
                    rule.getId(),
                    rule.getPriority(),
                    rule.getLevel(),
                    rule.getReason(),
                    matchedCodes
            );
        }
    }

    private void applyBuiltInRules(
            List<TriageMatchedRule> matchedRules,
            List<String> reasons,
            Set<String> redFlags,
            Set<String> riskModifiers,
            Set<String> textRedFlags,
            String symptomOnset,
            String functionalImpact,
            List<String> chronicConditions,
            List<String> chronicConditionOthers,
            boolean highRisk,
            boolean textHasSevereChestPain,
            boolean textHasPrioritySeverity,
            boolean acuteSymptom
    ) {
        if (redFlags.contains("CHEST_PAIN") && redFlags.contains("DYSPNEA")) {
            addMatchedRule(matchedRules, "builtin_chest_pain_with_dyspnea",
                    TriagePriorityNormalizer.URGENT, TriagePriorityNormalizer.RED_FLAG,
                    "Bệnh nhân có đau ngực kèm khó thở", List.of("CHEST_PAIN", "DYSPNEA"));
        }
        if (redFlags.contains("ALLERGIC_REACTION") && redFlags.contains("DYSPNEA")) {
            addMatchedRule(matchedRules, "builtin_allergic_reaction_with_dyspnea",
                    TriagePriorityNormalizer.URGENT, TriagePriorityNormalizer.RED_FLAG,
                    "Bệnh nhân có phản ứng dị ứng kèm khó thở", List.of("ALLERGIC_REACTION", "DYSPNEA"));
        }
        if (redFlags.contains("HEAVY_BLEEDING") && riskModifiers.contains("ON_BLOOD_THINNER")) {
            addMatchedRule(matchedRules, "builtin_heavy_bleeding_on_blood_thinner",
                    TriagePriorityNormalizer.URGENT, TriagePriorityNormalizer.RED_FLAG,
                    "Chảy máu nhiều kèm thông tin dùng thuốc chống đông", List.of("HEAVY_BLEEDING", "ON_BLOOD_THINNER"));
        }
        if (redFlags.contains("SEVERE_PAIN") && "TODAY".equals(symptomOnset)) {
            addMatchedRule(matchedRules, "builtin_severe_pain_today",
                    TriagePriorityNormalizer.URGENT, TriagePriorityNormalizer.RED_FLAG,
                    "Đau nhiều xuất hiện trong ngày", List.of("SEVERE_PAIN", "ONSET_TODAY"));
        }
        if ("UNABLE_SELF_CARE".equals(functionalImpact)) {
            addMatchedRule(matchedRules, "builtin_unable_self_care",
                    TriagePriorityNormalizer.URGENT, TriagePriorityNormalizer.RED_FLAG,
                    "Bệnh nhân khai không tự sinh hoạt được", List.of("IMPACT_UNABLE_SELF_CARE"));
        }
        if (textHasSevereChestPain) {
            addMatchedRule(matchedRules, "builtin_severe_chest_pain_text",
                    TriagePriorityNormalizer.URGENT, TriagePriorityNormalizer.RED_FLAG,
                    "Mô tả tự do có dấu hiệu đau ngực dữ dội", List.of("CHEST_PAIN", "SEVERE"));
        }
        for (String flag : IMMEDIATE_URGENT_FLAGS) {
            if (redFlags.contains(flag)) {
                addMatchedRule(matchedRules, "builtin_immediate_" + flag.toLowerCase(Locale.ROOT),
                        TriagePriorityNormalizer.URGENT, TriagePriorityNormalizer.RED_FLAG,
                        immediateUrgentReason(flag, textRedFlags.contains(flag)), List.of(flag));
            }
        }

        if (redFlags.contains("HIGH_FEVER") && highRisk) {
            addMatchedRule(matchedRules, "builtin_high_fever_with_risk",
                    TriagePriorityNormalizer.PRIORITY, TriagePriorityNormalizer.WATCH,
                    "Sốt cao kèm yếu tố nguy cơ cần được ưu tiên", withFirstRiskCode("HIGH_FEVER", riskModifiers));
        }
        if ("SEVERE_DIFFICULTY".equals(functionalImpact)) {
            addMatchedRule(matchedRules, "builtin_severe_difficulty",
                    TriagePriorityNormalizer.PRIORITY, TriagePriorityNormalizer.WATCH,
                    "Bệnh nhân khai ảnh hưởng sinh hoạt nghiêm trọng", List.of("IMPACT_SEVERE_DIFFICULTY"));
        }
        if (!chronicConditionOthers.isEmpty() && "SEVERE_DIFFICULTY".equals(functionalImpact)) {
            addMatchedRule(matchedRules, "builtin_chronic_other_with_severe_difficulty",
                    TriagePriorityNormalizer.PRIORITY, TriagePriorityNormalizer.WATCH,
                    "Bệnh nền/thông tin y tế khác kèm ảnh hưởng sinh hoạt nghiêm trọng", List.of("UNKNOWN_RISK_CONDITION", "IMPACT_SEVERE_DIFFICULTY"));
        }
        if (redFlags.contains("HIGH_FEVER")) {
            addMatchedRule(matchedRules, "builtin_high_fever",
                    TriagePriorityNormalizer.PRIORITY, TriagePriorityNormalizer.WATCH,
                    "Bệnh nhân có dấu hiệu sốt cao", List.of("HIGH_FEVER"));
        }
        if (redFlags.contains("CHEST_PAIN")) {
            addMatchedRule(matchedRules, "builtin_chest_pain_watch",
                    TriagePriorityNormalizer.PRIORITY, TriagePriorityNormalizer.WATCH,
                    "Bệnh nhân có dấu hiệu đau ngực cần staff xác minh", List.of("CHEST_PAIN"));
        }
        if (redFlags.contains("SEVERE_PAIN")) {
            addMatchedRule(matchedRules, "builtin_severe_pain_watch",
                    TriagePriorityNormalizer.PRIORITY, TriagePriorityNormalizer.WATCH,
                    "Bệnh nhân có dấu hiệu đau nhiều", List.of("SEVERE_PAIN"));
        }
        if (redFlags.contains("ALLERGIC_REACTION")) {
            addMatchedRule(matchedRules, "builtin_allergic_reaction_watch",
                    TriagePriorityNormalizer.PRIORITY, TriagePriorityNormalizer.WATCH,
                    "Bệnh nhân có dấu hiệu phản ứng dị ứng", List.of("ALLERGIC_REACTION"));
        }
        if (highRisk && ("TODAY".equals(symptomOnset) || "DAYS_2_3".equals(symptomOnset)) && !redFlags.isEmpty()) {
            addMatchedRule(matchedRules, "builtin_high_risk_acute_red_flag",
                    TriagePriorityNormalizer.PRIORITY, TriagePriorityNormalizer.WATCH,
                    "Yếu tố nguy cơ kèm triệu chứng mới xuất hiện cần staff chú ý", withFirstRiskCode("ONSET_" + symptomOnset, riskModifiers));
        }
        if (riskModifiers.stream().anyMatch(this::isComplexRiskModifier) && acuteSymptom) {
            addMatchedRule(matchedRules, "builtin_complex_risk_with_acute_symptom",
                    TriagePriorityNormalizer.PRIORITY, TriagePriorityNormalizer.WATCH,
                    "Yếu tố nguy cơ cao kèm triệu chứng cấp tính", withFirstRiskCode("ACUTE_SYMPTOM", riskModifiers));
        }
        if (("TODAY".equals(symptomOnset) || "DAYS_2_3".equals(symptomOnset))
                && ("MILD_DIFFICULTY".equals(functionalImpact) || "SEVERE_DIFFICULTY".equals(functionalImpact))) {
            addMatchedRule(matchedRules, "builtin_acute_with_functional_impact",
                    TriagePriorityNormalizer.PRIORITY, TriagePriorityNormalizer.WATCH,
                    "Triệu chứng mới xuất hiện và có ảnh hưởng sinh hoạt", List.of("ONSET_" + symptomOnset, "IMPACT_" + functionalImpact));
        }
        if (textHasPrioritySeverity) {
            addMatchedRule(matchedRules, "builtin_text_priority_severity",
                    TriagePriorityNormalizer.PRIORITY, TriagePriorityNormalizer.WATCH,
                    "Mô tả tự do có từ khóa mức độ vừa hoặc nặng", List.of("SEVERITY_TEXT"));
        }
        for (String chronicCondition : chronicConditions) {
            if (HIGH_RISK_CONDITIONS.contains(chronicCondition)) {
                reasons.add(riskReason(chronicCondition));
                addMatchedRule(matchedRules, "builtin_chronic_" + chronicCondition.toLowerCase(Locale.ROOT),
                        TriagePriorityNormalizer.PRIORITY, TriagePriorityNormalizer.WATCH,
                        riskReason(chronicCondition), List.of("CHRONIC_" + chronicCondition));
            }
        }
    }

    private void addMatchedRule(
            List<TriageMatchedRule> matchedRules,
            String id,
            String priority,
            String level,
            String reason,
            List<String> matchedCodes
    ) {
        if (matchedRules.stream().anyMatch(rule -> id.equals(rule.getId()))) {
            return;
        }
        matchedRules.add(TriageMatchedRule.builder()
                .id(id)
                .priority(TriagePriorityNormalizer.normalizePriority(priority))
                .level(level != null ? level : levelFor(priority))
                .reason(reason)
                .source("RULE_ENGINE")
                .matchedCodes(matchedCodes == null ? List.of() : matchedCodes.stream().distinct().toList())
                .build());
    }

    private List<String> withFirstRiskCode(String leadingCode, Set<String> riskModifiers) {
        List<String> codes = new ArrayList<>();
        codes.add(leadingCode);
        if (riskModifiers != null) {
            riskModifiers.stream().findFirst().ifPresent(codes::add);
        }
        return codes;
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private int priorityRank(String priority) {
        String normalizedPriority = TriagePriorityNormalizer.normalizePriority(priority);
        if (TriagePriorityNormalizer.URGENT.equals(normalizedPriority)) {
            return 0;
        }
        if (TriagePriorityNormalizer.PRIORITY.equals(normalizedPriority)) {
            return 1;
        }
        if (TriagePriorityNormalizer.ROUTINE.equals(normalizedPriority)) {
            return 2;
        }
        return 3;
    }

    private String levelFor(String priority) {
        String normalizedPriority = TriagePriorityNormalizer.normalizePriority(priority);
        if (TriagePriorityNormalizer.URGENT.equals(normalizedPriority)) {
            return TriagePriorityNormalizer.RED_FLAG;
        }
        if (TriagePriorityNormalizer.PRIORITY.equals(normalizedPriority)) {
            return TriagePriorityNormalizer.WATCH;
        }
        return TriagePriorityNormalizer.NONE;
    }

    private String redFlagReason(String redFlag, boolean fromCard, boolean fromDictionary, boolean fromAi) {
        String symptom = redFlagLabel(redFlag);
        if (fromCard) {
            return "Bệnh nhân chọn dấu hiệu " + symptom;
        }
        if (fromDictionary) {
            return "Mô tả tự do khớp từ điển dấu hiệu " + symptom;
        }
        if (fromAi) {
            return "AI trích xuất dấu hiệu " + symptom + " từ mô tả tự do";
        }
        return "Phát hiện dấu hiệu " + symptom;
    }

    private String immediateUrgentReason(String redFlag, boolean fromText) {
        return fromText
                ? "Mô tả tự do có dấu hiệu " + redFlagLabel(redFlag)
                : "Bệnh nhân chọn dấu hiệu " + redFlagLabel(redFlag);
    }

    private String redFlagLabel(String redFlag) {
        return switch (redFlag) {
            case "CHEST_PAIN" -> "đau ngực";
            case "DYSPNEA" -> "khó thở";
            case "FAINTING" -> "ngất";
            case "SEIZURE" -> "co giật";
            case "STROKE_SIGNS" -> "nghi ngờ đột quỵ";
            case "HEAVY_BLEEDING" -> "chảy máu nhiều";
            case "SEVERE_PAIN" -> "đau nhiều";
            case "HIGH_FEVER" -> "sốt cao";
            case "ALLERGIC_REACTION" -> "phản ứng dị ứng";
            case "ALTERED_MENTAL_STATUS" -> "thay đổi tri giác";
            case "SEVERE_TRAUMA" -> "chấn thương nặng";
            default -> redFlag;
        };
    }

    private boolean isHighRiskModifier(String code) {
        return Set.of(
                "CARDIOVASCULAR_RISK",
                "DIABETES_RISK",
                "RESPIRATORY_RISK",
                "IMMUNOCOMPROMISED",
                "PREGNANCY",
                "ELDERLY",
                "PEDIATRIC",
                "DIALYSIS",
                "TRANSPLANT_HISTORY",
                "COMPLEX_CHRONIC_DISEASE",
                "ON_BLOOD_THINNER",
                "IMMUNOSUPPRESSANT_USE"
        ).contains(code);
    }

    private boolean isComplexRiskModifier(String code) {
        return Set.of(
                "DIALYSIS",
                "IMMUNOCOMPROMISED",
                "ON_BLOOD_THINNER",
                "TRANSPLANT_HISTORY",
                "COMPLEX_CHRONIC_DISEASE",
                "IMMUNOSUPPRESSANT_USE"
        ).contains(code);
    }

    private String resolveSource(boolean hasAiSignal, boolean hasNonAiSignal) {
        if (hasAiSignal && hasNonAiSignal) {
            return "HYBRID";
        }
        if (hasAiSignal) {
            return "AI";
        }
        return "RULE";
    }

    private String confidenceLevelFor(
            String priority,
            String source,
            List<String> cardRedFlags,
            Set<String> dictionaryRedFlags,
            Set<String> aiRedFlags,
            List<TriageMatchedRule> matchedRules,
            boolean sparseInput
    ) {
        if (sparseInput) {
            return "LOW";
        }
        boolean strongRule = matchedRules != null && matchedRules.stream()
                .anyMatch(rule -> TriagePriorityNormalizer.URGENT.equals(TriagePriorityNormalizer.normalizePriority(rule.getPriority())));
        boolean dictionaryAndAiSameFlag = dictionaryRedFlags != null && aiRedFlags != null
                && dictionaryRedFlags.stream().anyMatch(aiRedFlags::contains);
        if (!cardRedFlags.isEmpty() || dictionaryAndAiSameFlag || strongRule) {
            return "HIGH";
        }
        if (TriagePriorityNormalizer.URGENT.equals(priority)
                || TriagePriorityNormalizer.PRIORITY.equals(priority)
                || "AI".equals(source)
                || "HYBRID".equals(source)) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private double confidenceValue(String confidenceLevel) {
        return switch (confidenceLevel) {
            case "HIGH" -> 0.9d;
            case "MEDIUM" -> 0.7d;
            default -> 0.45d;
        };
    }

    private boolean applyPriorityRules(
            boolean current,
            List<String> reasons,
            Set<String> redFlags,
            String symptomOnset,
            String functionalImpact,
            List<String> chronicConditions,
            boolean textHasPrioritySeverity
    ) {
        boolean priority = current;

        if ("SEVERE_DIFFICULTY".equals(functionalImpact)) {
            reasons.add("Bệnh nhân khai ảnh hưởng sinh hoạt nghiêm trọng");
            priority = true;
        }
        if (redFlags.contains("HIGH_FEVER")) {
            reasons.add("Bệnh nhân có dấu hiệu sốt cao");
            priority = true;
        }
        if (redFlags.contains("CHEST_PAIN")) {
            reasons.add("Bệnh nhân có dấu hiệu đau ngực cần staff xác minh");
            priority = true;
        }
        if (redFlags.contains("SEVERE_PAIN")) {
            reasons.add("Bệnh nhân có dấu hiệu đau nhiều");
            priority = true;
        }
        if (redFlags.contains("ALLERGIC_REACTION")) {
            reasons.add("Bệnh nhân có dấu hiệu phản ứng dị ứng");
            priority = true;
        }
        for (String chronicCondition : chronicConditions) {
            reasons.add(riskReason(chronicCondition));
            priority = true;
        }
        if (("TODAY".equals(symptomOnset) || "DAYS_2_3".equals(symptomOnset))
                && ("MILD_DIFFICULTY".equals(functionalImpact) || "SEVERE_DIFFICULTY".equals(functionalImpact))) {
            reasons.add("Triệu chứng mới xuất hiện và có ảnh hưởng sinh hoạt");
            priority = true;
        }
        if (textHasPrioritySeverity) {
            reasons.add("Mô tả tự do có từ khóa mức độ vừa hoặc nặng");
            priority = true;
        }

        return priority;
    }

    private void addContextFlags(Set<String> flags, String symptomOnset, String functionalImpact, List<String> chronicConditions) {
        if (!"UNKNOWN".equals(symptomOnset)) {
            flags.add("ONSET_" + symptomOnset);
        }
        if (!"UNKNOWN".equals(functionalImpact) && !"NORMAL".equals(functionalImpact)) {
            flags.add("IMPACT_" + functionalImpact);
        }
        for (String chronicCondition : chronicConditions) {
            flags.add("CHRONIC_" + chronicCondition);
        }
    }

    private String sourceAwareReason(String symptom, boolean fromText) {
        return fromText
                ? "Mô tả tự do có dấu hiệu " + symptom
                : "Bệnh nhân chọn dấu hiệu " + symptom;
    }

    private String riskReason(String chronicCondition) {
        return switch (chronicCondition) {
            case "CARDIOVASCULAR" -> "Có bệnh nền tim mạch";
            case "DIABETES" -> "Có bệnh nền đái tháo đường";
            case "RESPIRATORY" -> "Có bệnh nền hô hấp";
            case "CANCER" -> "Có tiền sử ung thư";
            case "IMMUNODEFICIENCY" -> "Có tình trạng suy giảm miễn dịch";
            case "PREGNANCY" -> "Bệnh nhân đang mang thai";
            case "ELDERLY" -> "Bệnh nhân thuộc nhóm cao tuổi";
            default -> "Có yếu tố nguy cơ cần staff chú ý";
        };
    }

    private String summaryFor(String priority) {
        return switch (priority) {
            case TriagePriorityNormalizer.URGENT ->
                    "Gợi ý P1 vì có dấu hiệu nguy hiểm cần staff xác minh trước khi xác nhận lịch.";
            case TriagePriorityNormalizer.PRIORITY ->
                    "Gợi ý P2 vì có yếu tố nguy cơ hoặc ảnh hưởng sinh hoạt đáng kể.";
            default -> "Gợi ý P3 vì chưa phát hiện dấu hiệu ưu tiên rõ.";
        };
    }

    private double confidenceFor(String priority, String source, boolean sparseInput) {
        if (sparseInput) {
            return 0.45d;
        }
        double base = switch (priority) {
            case TriagePriorityNormalizer.URGENT -> 0.9d;
            case TriagePriorityNormalizer.PRIORITY -> 0.78d;
            default -> 0.68d;
        };
        if ("HYBRID".equals(source)) {
            base += 0.05d;
        }
        return Math.min(base, 0.95d);
    }

    private boolean hasSparseInput(
            String symptomOnset,
            String functionalImpact,
            List<String> chronicConditions,
            List<String> cardRedFlags,
            String reasonForVisit,
            String patientNote
    ) {
        return "UNKNOWN".equals(symptomOnset)
                && "UNKNOWN".equals(functionalImpact)
                && chronicConditions.isEmpty()
                && cardRedFlags.isEmpty()
                && StringUtil.trimToNull(reasonForVisit) == null
                && StringUtil.trimToNull(patientNote) == null;
    }

    private Set<String> detectKeywordRedFlags(String reasonForVisit, String patientNote) {
        String text = normalizedFreeText(reasonForVisit, patientNote);
        if (text == null) {
            return Set.of();
        }
        Set<String> flags = new LinkedHashSet<>();
        if (containsAny(text, "dau nguc", "tuc nguc", "nang nguc")) {
            flags.add("CHEST_PAIN");
        }
        if (containsAny(text, "kho tho", "hut hoi", "tho gap", "nghet tho")) {
            flags.add("DYSPNEA");
        }
        if (containsAny(text, "ngat", "xiu", "bat tinh")) {
            flags.add("FAINTING");
        }
        if (containsAny(text, "co giat", "dong kinh")) {
            flags.add("SEIZURE");
        }
        if (containsAny(text, "yeu liet nua nguoi", "liet nua nguoi", "meo mieng", "noi kho", "noi ngo", "dau hieu dot quy")) {
            flags.add("STROKE_SIGNS");
        }
        if (containsAny(text, "chay mau nhieu", "xuat huyet nhieu", "mat mau nhieu")) {
            flags.add("HEAVY_BLEEDING");
        }
        if (containsAny(text, "dau du doi", "dau rat nang", "dau khong chiu duoc")) {
            flags.add("SEVERE_PAIN");
        }
        if (containsAny(text, "sot cao", "sot rat cao")) {
            flags.add("HIGH_FEVER");
        }
        if (containsAny(text, "di ung", "phan ve", "noi me day", "phu mach", "sung moi", "sung mat")) {
            flags.add("ALLERGIC_REACTION");
        }
        return flags;
    }

    private boolean hasSevereChestPain(String reasonForVisit, String patientNote) {
        String text = normalizedFreeText(reasonForVisit, patientNote);
        return text != null
                && containsAny(text, "dau nguc du doi", "tuc nguc du doi", "dau tuc nguc du doi");
    }

    private boolean hasPrioritySeverityTerm(String reasonForVisit, String patientNote, AiTriageExtractionResult aiResult) {
        if (aiResult != null && aiResult.getSeverityTerms() != null) {
            for (String term : aiResult.getSeverityTerms()) {
                String normalized = normalizeText(term);
                if (normalized != null && containsAny(normalized, "vua", "nang", "nhieu", "du doi")) {
                    return true;
                }
            }
        }
        String text = normalizedFreeText(reasonForVisit, patientNote);
        return text != null && containsAny(text, "vua", "nang", "rat dau", "nhieu", "khong do");
    }

    private String normalizedFreeText(String reasonForVisit, String patientNote) {
        String reason = StringUtil.trimToNull(reasonForVisit);
        String note = StringUtil.trimToNull(patientNote);
        String joined = String.join(" ", reason != null ? reason : "", note != null ? note : "").trim();
        return normalizeText(joined);
    }

    private String normalizeText(String value) {
        String text = StringUtil.trimToNull(value);
        if (text == null) {
            return null;
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
                                      .replaceAll("\\p{M}", "")
                                      .toLowerCase(Locale.ROOT);
        return normalized.replace('đ', 'd').replaceAll("\\s+", " ").trim();
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
