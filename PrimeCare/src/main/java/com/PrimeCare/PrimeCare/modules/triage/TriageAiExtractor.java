package com.PrimeCare.PrimeCare.modules.triage;

import com.PrimeCare.PrimeCare.modules.ai.support.OpenAiCompatibleClient;
import com.PrimeCare.PrimeCare.modules.appointment.dto.request.PreTriageInputRequest;
import com.PrimeCare.PrimeCare.shared.utils.StringUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TriageAiExtractor {

    private static final String SYSTEM_PROMPT = """
            Bạn là bộ trích xuất thông tin sàng lọc sơ bộ cho hệ thống đặt lịch khám.
            Nhiệm vụ của bạn KHÔNG phải chẩn đoán và KHÔNG quyết định mức ưu tiên khám.
            Chỉ đọc mô tả tự do của bệnh nhân và trích xuất dấu hiệu liên quan dưới dạng JSON hợp lệ.
            Không thêm giải thích ngoài JSON.
            Nếu không chắc, để mảng rỗng hoặc null.
            Không bịa triệu chứng không có trong mô tả.

            Schema JSON bắt buộc:
            {
              "detectedSymptoms": [],
              "redFlags": [],
              "riskModifiers": [],
              "chronicConditionMentions": [],
              "medicationRiskMentions": [],
              "severityTerms": [],
              "durationText": null,
              "normalizedSummary": null
            }

            Allowed redFlags:
            CHEST_PAIN,
            DYSPNEA,
            FAINTING,
            SEIZURE,
            STROKE_SIGNS,
            HEAVY_BLEEDING,
            SEVERE_PAIN,
            HIGH_FEVER,
            ALLERGIC_REACTION,
            ALTERED_MENTAL_STATUS,
            SEVERE_TRAUMA

            Allowed riskModifiers:
            CARDIOVASCULAR_RISK,
            DIABETES_RISK,
            RESPIRATORY_RISK,
            IMMUNOCOMPROMISED,
            PREGNANCY,
            ELDERLY,
            PEDIATRIC,
            ON_BLOOD_THINNER,
            RECENT_SURGERY,
            DIALYSIS,
            TRANSPLANT_HISTORY,
            COMPLEX_CHRONIC_DISEASE,
            UNKNOWN_RISK_CONDITION

            Không chẩn đoán bệnh. Không quyết định P1/P2/P3. Không khuyên dùng thuốc.
            Chỉ extract thông tin có trong input. Nếu bệnh nền không phân loại được, đưa nguyên văn vào chronicConditionMentions và thêm UNKNOWN_RISK_CONDITION.

            Ví dụ:
            Input: "Tôi đau tức ngực lan ra tay trái, khó thở từ sáng"
            Output:
            {
              "detectedSymptoms": ["đau tức ngực", "khó thở"],
              "redFlags": ["CHEST_PAIN", "DYSPNEA"],
              "riskModifiers": [],
              "chronicConditionMentions": [],
              "medicationRiskMentions": [],
              "severityTerms": [],
              "durationText": "từ sáng",
              "normalizedSummary": "Bệnh nhân mô tả đau tức ngực lan tay trái kèm khó thở từ sáng"
            }

            Input: "Đau họng nhẹ 2 ngày, vẫn sinh hoạt bình thường"
            Output:
            {
              "detectedSymptoms": ["đau họng"],
              "redFlags": [],
              "riskModifiers": [],
              "chronicConditionMentions": [],
              "medicationRiskMentions": [],
              "severityTerms": ["nhẹ"],
              "durationText": "2 ngày",
              "normalizedSummary": "Bệnh nhân mô tả đau họng nhẹ trong 2 ngày, vẫn sinh hoạt bình thường"
            }
            """;

    private final OpenAiCompatibleClient aiClient;
    private final ObjectMapper objectMapper;

    public AiTriageExtractionResult extract(String reasonForVisit, String patientNote) {
        return extract(reasonForVisit, patientNote, null);
    }

    public AiTriageExtractionResult extract(String reasonForVisit, String patientNote, PreTriageInputRequest input) {
        String text = buildPromptInput(reasonForVisit, patientNote, input);
        if (text == null) {
            return AiTriageExtractionResult.empty();
        }

        try {
            Optional<String> response = aiClient.generateText(
                    SYSTEM_PROMPT,
                    "Mô tả tự do của bệnh nhân:\n" + text,
                    0.0d,
                    8L
            );
            if (response.isEmpty()) {
                return AiTriageExtractionResult.empty();
            }

            String json = extractJsonObject(response.get());
            if (json == null) {
                log.warn("AI triage extraction did not return a JSON object");
                return AiTriageExtractionResult.empty();
            }

            JsonNode root = objectMapper.readTree(json);
            return AiTriageExtractionResult.builder()
                    .detectedSymptoms(readStringList(root.path("detectedSymptoms")))
                    .redFlags(TriagePriorityNormalizer.normalizeAiRedFlags(readStringList(root.path("redFlags"))))
                    .riskModifiers(TriagePriorityNormalizer.normalizeRiskModifiers(readStringList(root.path("riskModifiers"))))
                    .chronicConditionMentions(readStringList(root.path("chronicConditionMentions")))
                    .medicationRiskMentions(readStringList(root.path("medicationRiskMentions")))
                    .severityTerms(readStringList(root.path("severityTerms")))
                    .durationText(textOrNull(root.path("durationText")))
                    .normalizedSummary(textOrNull(root.path("normalizedSummary")))
                    .build();
        } catch (Exception ex) {
            log.warn("AI triage extraction failed: {}", ex.getMessage());
            return AiTriageExtractionResult.empty();
        }
    }

    public String aiModelVersion() {
        return aiClient.modelVersion();
    }

    private String buildPromptInput(String reasonForVisit, String patientNote, PreTriageInputRequest input) {
        List<String> parts = new ArrayList<>();
        String reason = StringUtil.trimToNull(reasonForVisit);
        String note = StringUtil.trimToNull(patientNote);
        if (reason != null) {
            parts.add(reason);
        }
        if (note != null) {
            parts.add("patientNote: " + note);
        }
        if (input != null) {
            parts.add("symptomOnset: " + TriagePriorityNormalizer.normalizeSymptomOnset(input.getSymptomOnset()));
            parts.add("functionalImpact: " + TriagePriorityNormalizer.normalizeFunctionalImpact(input.getFunctionalImpact()));
            parts.add("chronicConditions: " + TriagePriorityNormalizer.normalizeChronicConditions(input.getChronicConditions()));
            parts.add("chronicConditionOthers: " + TriagePriorityNormalizer.normalizeChronicConditionOthers(input.getChronicConditionOthers()));
            parts.add("selectedRedFlags: " + TriagePriorityNormalizer.normalizeRedFlags(input.getRedFlags()));
        }
        return parts.isEmpty() ? null : String.join("\n", parts);
    }

    private List<String> readStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item != null && item.isTextual() && !item.asText().isBlank()) {
                values.add(item.asText().trim());
            }
        }
        return values;
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || !node.isTextual()) {
            return null;
        }
        return StringUtil.trimToNull(node.asText());
    }

    private String extractJsonObject(String value) {
        String text = StringUtil.trimToNull(value);
        if (text == null) {
            return null;
        }

        int fenceStart = text.indexOf("```");
        if (fenceStart >= 0) {
            int contentStart = text.indexOf('\n', fenceStart);
            int fenceEnd = text.indexOf("```", contentStart + 1);
            if (contentStart >= 0 && fenceEnd > contentStart) {
                text = text.substring(contentStart + 1, fenceEnd).trim();
            }
        }

        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return text.substring(start, end + 1);
    }
}
