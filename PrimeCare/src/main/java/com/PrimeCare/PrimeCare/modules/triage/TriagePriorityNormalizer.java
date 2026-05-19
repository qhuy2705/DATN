package com.PrimeCare.PrimeCare.modules.triage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class TriagePriorityNormalizer {

    public static final String URGENT = "URGENT";
    public static final String PRIORITY = "PRIORITY";
    public static final String ROUTINE = "ROUTINE";

    public static final String NONE = "NONE";
    public static final String WATCH = "WATCH";
    public static final String RED_FLAG = "RED_FLAG";

    private static final Set<String> SYMPTOM_ONSETS = Set.of(
            "TODAY",
            "DAYS_2_3",
            "WEEK_1",
            "OVER_MONTH",
            "UNKNOWN"
    );

    private static final Set<String> CHRONIC_CONDITIONS = Set.of(
            "CARDIOVASCULAR",
            "DIABETES",
            "RESPIRATORY",
            "CANCER",
            "IMMUNODEFICIENCY",
            "PREGNANCY",
            "ELDERLY",
            "NONE"
    );

    private static final Set<String> FUNCTIONAL_IMPACTS = Set.of(
            "NORMAL",
            "MILD_DIFFICULTY",
            "SEVERE_DIFFICULTY",
            "UNABLE_SELF_CARE",
            "UNKNOWN"
    );

    private static final Set<String> RED_FLAGS = Set.of(
            "CHEST_PAIN",
            "DYSPNEA",
            "FAINTING",
            "SEIZURE",
            "STROKE_SIGNS",
            "HEAVY_BLEEDING",
            "SEVERE_PAIN",
            "HIGH_FEVER",
            "ALLERGIC_REACTION",
            "NONE"
    );

    private static final Set<String> AI_RED_FLAGS = Set.of(
            "CHEST_PAIN",
            "DYSPNEA",
            "FAINTING",
            "SEIZURE",
            "STROKE_SIGNS",
            "HEAVY_BLEEDING",
            "SEVERE_PAIN",
            "HIGH_FEVER",
            "ALLERGIC_REACTION",
            "ALTERED_MENTAL_STATUS",
            "SEVERE_TRAUMA"
    );

    private static final Set<String> RISK_MODIFIERS = Set.of(
            "CARDIOVASCULAR_RISK",
            "DIABETES_RISK",
            "RESPIRATORY_RISK",
            "IMMUNOCOMPROMISED",
            "PREGNANCY",
            "ELDERLY",
            "PEDIATRIC",
            "ON_BLOOD_THINNER",
            "RECENT_SURGERY",
            "DIALYSIS",
            "TRANSPLANT_HISTORY",
            "COMPLEX_CHRONIC_DISEASE",
            "UNKNOWN_RISK_CONDITION"
    );

    private static final Set<String> REVIEW_STATUSES = Set.of("ACCEPTED", "OVERRIDDEN", "MANUAL");

    private TriagePriorityNormalizer() {
    }

    public static String normalizePriority(String value) {
        String normalized = normalizeToken(value);
        if (normalized == null) {
            return null;
        }
        return switch (normalized) {
            case "P1", "EMERGENCY", "URGENT" -> URGENT;
            case "P2", "HIGH", "PRIORITY" -> PRIORITY;
            case "P3", "NORMAL", "ROUTINE" -> ROUTINE;
            default -> null;
        };
    }

    public static String normalizeReviewStatus(String value) {
        String normalized = normalizeToken(value);
        return normalized != null && REVIEW_STATUSES.contains(normalized) ? normalized : null;
    }

    public static String normalizeSymptomOnset(String value) {
        String normalized = normalizeToken(value);
        return normalized != null && SYMPTOM_ONSETS.contains(normalized) ? normalized : "UNKNOWN";
    }

    public static String normalizeFunctionalImpact(String value) {
        String normalized = normalizeToken(value);
        return normalized != null && FUNCTIONAL_IMPACTS.contains(normalized) ? normalized : "UNKNOWN";
    }

    public static List<String> normalizeChronicConditions(List<String> values) {
        return normalizeList(values, CHRONIC_CONDITIONS, true);
    }

    public static List<String> normalizeRedFlags(List<String> values) {
        return normalizeList(values, RED_FLAGS, true);
    }

    public static List<String> normalizeAiRedFlags(List<String> values) {
        return normalizeList(values, AI_RED_FLAGS, false);
    }

    public static List<String> normalizeRiskModifiers(List<String> values) {
        return normalizeList(values, RISK_MODIFIERS, false);
    }

    public static List<String> normalizeChronicConditionOthers(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<String> normalized = new ArrayList<>();
        for (String rawValue : values) {
            if (rawValue == null) {
                continue;
            }
            String value = rawValue.trim().replaceAll("\\s+", " ");
            if (value.isEmpty()) {
                continue;
            }
            if (value.length() > 80) {
                value = value.substring(0, 80).trim();
            }
            String key = value.toLowerCase(Locale.ROOT);
            if (seen.add(key)) {
                normalized.add(value);
            }
            if (normalized.size() >= 5) {
                break;
            }
        }
        return normalized;
    }

    public static List<String> removeNone(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                     .map(TriagePriorityNormalizer::normalizeToken)
                     .filter(value -> value != null && !"NONE".equals(value))
                     .distinct()
                     .toList();
    }

    public static String normalizeToken(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toUpperCase(Locale.ROOT)
                      .replace('-', '_')
                      .replace(' ', '_');
    }

    private static List<String> normalizeList(List<String> values, Set<String> allowedValues, boolean allowNone) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        Set<String> normalized = new LinkedHashSet<>();
        for (String rawValue : values) {
            String value = normalizeToken(rawValue);
            if (value != null && allowedValues.contains(value)) {
                normalized.add(value);
            }
        }

        if (allowNone && normalized.contains("NONE")) {
            return List.of("NONE");
        }

        normalized.remove("NONE");
        return new ArrayList<>(normalized);
    }
}
