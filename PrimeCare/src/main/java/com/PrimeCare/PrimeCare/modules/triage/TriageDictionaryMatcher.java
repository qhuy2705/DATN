package com.PrimeCare.PrimeCare.modules.triage;

import com.PrimeCare.PrimeCare.shared.utils.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class TriageDictionaryMatcher {

    private final TriageKnowledgeBaseService knowledgeBaseService;

    public TriageDictionaryMatchResult match(
            String reasonForVisit,
            String patientNote,
            List<String> chronicConditions,
            List<String> chronicConditionOthers
    ) {
        String freeText = joinText(reasonForVisit, patientNote);
        String chronicOtherText = joinText(TriagePriorityNormalizer.normalizeChronicConditionOthers(chronicConditionOthers));
        String text = joinText(freeText, chronicOtherText);
        if (StringUtil.trimToNull(text) == null) {
            return TriageDictionaryMatchResult.empty();
        }

        MatchText matchText = MatchText.of(text);
        List<TriageMatchedTerm> terms = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        matchDefinitions(terms, seen, matchText, knowledgeBaseService.redFlags(), "RED_FLAG");
        matchDefinitions(terms, seen, matchText, knowledgeBaseService.symptoms(), "SYMPTOM");
        matchDefinitions(terms, seen, matchText, knowledgeBaseService.riskModifiers(), "RISK_MODIFIER");
        matchDefinitions(terms, seen, matchText, knowledgeBaseService.medicationRisks(), "MEDICATION_RISK");
        matchDefinitions(terms, seen, matchText, knowledgeBaseService.severityTerms(), "SEVERITY");

        return TriageDictionaryMatchResult.builder()
                .matchedTerms(terms)
                .build();
    }

    private void matchDefinitions(
            List<TriageMatchedTerm> terms,
            Set<String> seen,
            MatchText matchText,
            List<TriageDefinition> definitions,
            String category
    ) {
        if (definitions == null || definitions.isEmpty()) {
            return;
        }
        for (TriageDefinition definition : definitions) {
            if (definition == null || definition.getCode() == null || definition.getSynonyms() == null) {
                continue;
            }
            for (String synonym : definition.getSynonyms()) {
                String term = StringUtil.trimToNull(synonym);
                if (term == null) {
                    continue;
                }
                if (matchText.contains(term)) {
                    String key = category + ":" + definition.getCode();
                    if (seen.add(key)) {
                        terms.add(TriageMatchedTerm.builder()
                                .term(term)
                                .code(definition.getCode())
                                .label(definition.getLabel())
                                .category(category)
                                .source("KNOWLEDGE_BASE")
                                .evidenceText(term)
                                .weight(definition.getWeight())
                                .build());
                    }
                    break;
                }
            }
        }
    }

    private String joinText(String... values) {
        List<String> parts = new ArrayList<>();
        if (values != null) {
            for (String value : values) {
                String trimmed = StringUtil.trimToNull(value);
                if (trimmed != null) {
                    parts.add(trimmed);
                }
            }
        }
        return parts.isEmpty() ? null : String.join("\n", parts);
    }

    private String joinText(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return joinText(values.toArray(String[]::new));
    }

    private record MatchText(String raw, String folded) {
        static MatchText of(String text) {
            String raw = normalize(text, false);
            String folded = normalize(text, true);
            return new MatchText(raw, folded);
        }

        boolean contains(String term) {
            String normalizedTerm = normalize(term, false);
            String foldedTerm = normalize(term, true);
            return normalizedTerm != null
                    && (containsTerm(raw, normalizedTerm) || containsTerm(folded, foldedTerm));
        }

        private static String normalize(String value, boolean foldAccent) {
            if (value == null) {
                return null;
            }
            String text = value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            if (foldAccent) {
                text = Normalizer.normalize(text, Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "")
                        .replace('đ', 'd');
            }
            text = text.replaceAll("[\\p{Punct}]+", " ").replaceAll("\\s+", " ").trim();
            return text;
        }

        private static boolean containsTerm(String text, String term) {
            if (text == null || term == null) {
                return false;
            }
            return (" " + text + " ").contains(" " + term + " ");
        }
    }
}
