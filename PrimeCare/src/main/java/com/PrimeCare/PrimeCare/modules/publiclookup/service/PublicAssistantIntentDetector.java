package com.PrimeCare.PrimeCare.modules.publiclookup.service;

import org.springframework.stereotype.Component;

import static com.PrimeCare.PrimeCare.modules.publiclookup.service.PublicAssistantText.containsAny;
import static com.PrimeCare.PrimeCare.modules.publiclookup.service.PublicAssistantText.searchable;

@Component
public class PublicAssistantIntentDetector {

    AssistantIntent detect(String question, String pagePath) {
        String normalized = searchable(question);
        if (containsAny(normalized,
                "kho tho", "dau nguc", "co giat", "ngat", "chay mau nhieu", "sot cao co giat",
                "severe chest pain", "shortness of breath", "fainting", "seizure", "emergency", "urgent")) {
            return AssistantIntent.URGENT;
        }
        if (containsAny(normalized, "ket qua", "result", "pdf", "otp", "tra cuu", "lookup", "ma ho so", "ma lich hen")) {
            return AssistantIntent.RESULT_LOOKUP;
        }
        if (containsAny(normalized,
                "nhin an", "chuan bi", "xet nghiem", "sieu am", "noi soi", "prepare", "preparation", "fasting")) {
            return AssistantIntent.PREPARATION;
        }
        if (containsAny(normalized,
                "dat lich", "book", "appointment", "slot", "lich trong", "available slot", "availability", "buoc", "dang ky kham")) {
            return AssistantIntent.BOOKING;
        }
        if (containsAny(normalized,
                "khoa nao", "chuyen khoa", "doctor", "bac si", "dau bung", "mun", "tim mach", "nhi khoa", "pediatrics", "specialty")) {
            return AssistantIntent.SPECIALTY_GUIDANCE;
        }
        if ("/booking".equals(pagePath) || "/availability".equals(pagePath)) {
            return AssistantIntent.BOOKING;
        }
        if ("/appointments/lookup".equals(pagePath)) {
            return AssistantIntent.RESULT_LOOKUP;
        }
        return AssistantIntent.FAQ;
    }

    boolean looksEnglish(String question) {
        String normalized = searchable(question);
        return containsAny(normalized, "book", "appointment", "result", "lookup", "doctor", "test", "insurance", "clinic", "specialty");
    }
}
