package com.PrimeCare.PrimeCare.modules.triage;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Data
@Builder
public class TriageDictionaryMatchResult {

    @Builder.Default
    private List<TriageMatchedTerm> matchedTerms = List.of();

    public static TriageDictionaryMatchResult empty() {
        return TriageDictionaryMatchResult.builder().build();
    }

    public boolean hasSignal() {
        return matchedTerms != null && !matchedTerms.isEmpty();
    }

    public Set<String> codesByCategory(String category) {
        if (matchedTerms == null || matchedTerms.isEmpty()) {
            return Set.of();
        }
        return matchedTerms.stream()
                .filter(term -> term != null && category.equals(term.getCategory()) && term.getCode() != null)
                .map(TriageMatchedTerm::getCode)
                .collect(Collectors.toSet());
    }
}
