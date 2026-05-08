package com.PrimeCare.PrimeCare.modules.publiclookup.service;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

final class PublicAssistantText {

    private static final Set<String> STOP_WORDS = Set.of(
            "la", "va", "voi", "cho", "cua", "toi", "ban", "minh", "nhung", "nhu", "nao",
            "lam", "sao", "duoc", "gi", "khi", "can", "hay", "tren", "trong", "den", "tai", "ve",
            "theo", "mot", "cac", "primecare", "website", "benh", "vien", "please", "how", "what",
            "when", "where", "which", "for", "the", "and", "with", "your", "you", "help",
            "need", "before", "after", "from", "into", "page", "trang"
    );

    private PublicAssistantText() {
    }

    static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    static String joinOrFallback(List<String> items, String fallback) {
        if (items == null || items.isEmpty()) {
            return fallback;
        }
        return items.stream().filter(Objects::nonNull).filter(item -> !item.isBlank()).collect(Collectors.joining(", "));
    }

    static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    static String searchable(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('đ', 'd')
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    static Set<String> tokenize(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(searchable(value).split(" "))
                .filter(token -> token.length() >= 2)
                .filter(token -> !STOP_WORDS.contains(token))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    static String compactText(String value, int maxChars) {
        String stripped = stripHtml(blankToNull(value));
        if (stripped == null) {
            return null;
        }
        String compact = stripped.replaceAll("\\s+", " ").trim();
        if (compact.length() <= maxChars) {
            return compact;
        }
        return compact.substring(0, Math.max(0, maxChars - 3)).trim() + "...";
    }

    static String stripHtml(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("<[^>]+>", " ").replace("&nbsp;", " ");
    }

    static String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, Math.max(0, maxChars - 3)).trim() + "...";
    }

    static String trimAndTruncate(String value, int maxChars, String fallback) {
        String trimmed = blankToNull(value);
        if (trimmed == null) {
            return fallback;
        }
        return truncate(trimmed, maxChars);
    }

    static List<String> tagList(String... values) {
        return Arrays.stream(values)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }
}
