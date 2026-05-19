package com.PrimeCare.PrimeCare.modules.encounter.service;

import com.PrimeCare.PrimeCare.modules.ai.support.OpenAiCompatibleClient;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.encounter.dto.response.EncounterAiSummaryResponse;
import com.PrimeCare.PrimeCare.modules.encounter.entity.Encounter;
import com.PrimeCare.PrimeCare.modules.encounter.repository.EncounterRepository;
import com.PrimeCare.PrimeCare.modules.service_order.entity.ServiceOrder;
import com.PrimeCare.PrimeCare.modules.service_order.entity.ServiceOrderItem;
import com.PrimeCare.PrimeCare.modules.service_order.repository.ServiceOrderRepository;
import com.PrimeCare.PrimeCare.modules.service_result.entity.ServiceResult;
import com.PrimeCare.PrimeCare.modules.service_result.repository.ServiceResultRepository;
import com.PrimeCare.PrimeCare.shared.enums.EncounterStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceOrderItemStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EncounterAiSummaryService {

    private static final int CLINICAL_TEXT_LIMIT = 1200;
    private static final int SHORT_TEXT_LIMIT = 500;

    private final EncounterRepository encounterRepository;
    private final UserRepository userRepository;
    private final ServiceOrderRepository serviceOrderRepository;
    private final ServiceResultRepository serviceResultRepository;
    private final ObjectMapper objectMapper;
    private final OpenAiCompatibleClient openAiCompatibleClient;

    @Transactional(readOnly = true)
    public EncounterAiSummaryResponse generate(Long encounterId, Long doctorUserId) {
        Encounter encounter = getEncounterForDoctor(encounterId, doctorUserId);
        List<ServiceOrder> orders = serviceOrderRepository.findByEncounter_IdOrderByCreatedAtDesc(encounter.getId());
        Map<Long, ServiceResult> resultByItemId = serviceResultRepository
                .findByServiceOrderItem_ServiceOrder_Encounter_Id(encounter.getId())
                .stream()
                .collect(Collectors.toMap(r -> r.getServiceOrderItem().getId(), Function.identity()));

        List<String> highlightedResults = buildHighlightedResults(orders, resultByItemId);
        List<String> riskFlags = buildRiskFlags(encounter);
        List<String> nextSteps = buildNextStepSuggestions(encounter, orders);
        String quickSummary = buildQuickSummary(encounter, orders, highlightedResults, riskFlags);
        String draftDoctorNote = buildDraftDoctorNote(encounter, highlightedResults, riskFlags, nextSteps);
        String provider = "RULES";

        String aiDraft = openAiCompatibleClient.generateText(
                "You are an assistant for a doctor in an outpatient hospital system. Reply in Vietnamese. Create a concise draft doctor note based on the structured encounter data provided. Do not claim final certainty and do not invent diagnoses.",
                buildAiPrompt(encounter, quickSummary, highlightedResults, riskFlags, nextSteps)
        ).orElse(null);
        if (aiDraft != null && !aiDraft.isBlank()) {
            draftDoctorNote = aiDraft.trim();
            provider = openAiCompatibleClient.providerLabel();
        }

        return EncounterAiSummaryResponse.builder()
                .provider(provider)
                .disclaimer("AI chỉ hỗ trợ tóm tắt và soạn nháp; bác sĩ vẫn là người chịu trách nhiệm kết luận cuối cùng.")
                .quickSummary(quickSummary)
                .highlightedResults(highlightedResults)
                .riskFlags(riskFlags)
                .nextStepSuggestions(nextSteps)
                .draftDoctorNote(draftDoctorNote)
                .build();
    }

    private Encounter getEncounterForDoctor(Long encounterId, Long doctorUserId) {
        User doctorUser = userRepository.findById(doctorUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));

        if (doctorUser.getDoctorProfile() == null || doctorUser.getDoctorProfile().getId() == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Bạn không thuộc bác sĩ phụ trách lần khám này");
        }

        Encounter encounter = encounterRepository.findById(encounterId)
                .orElseThrow(() -> new ApiException(ErrorCode.ENCOUNTER_NOT_FOUND));
        Long encounterDoctorId = encounter.getDoctor() != null ? encounter.getDoctor().getId() : null;
        if (!doctorUser.getDoctorProfile().getId().equals(encounterDoctorId)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Bác sĩ chỉ được truy cập hồ sơ khám do mình phụ trách");
        }
        return encounter;
    }

    private String buildQuickSummary(Encounter encounter, List<ServiceOrder> orders, List<String> highlightedResults, List<String> riskFlags) {
        int totalItems = orders.stream().mapToInt(order -> order.getItems().size()).sum();
        long completedItems = orders.stream()
                .flatMap(order -> order.getItems().stream())
                .filter(item -> item.getStatus() == ServiceOrderItemStatus.DONE)
                .count();

        List<String> pieces = new ArrayList<>();
        pieces.add(demographicSummary(encounter));
        if (encounter.getChiefComplaint() != null && !encounter.getChiefComplaint().isBlank()) {
            pieces.add("lý do khám: " + encounter.getChiefComplaint());
        }
        if (encounter.getPreliminaryDiagnosis() != null && !encounter.getPreliminaryDiagnosis().isBlank()) {
            pieces.add("chẩn đoán ban đầu: " + encounter.getPreliminaryDiagnosis());
        }
        pieces.add("dịch vụ: " + completedItems + "/" + totalItems + " mục đã hoàn tất");
        if (!highlightedResults.isEmpty()) {
            pieces.add("có " + highlightedResults.size() + " điểm kết quả cần chú ý");
        }
        if (!riskFlags.isEmpty()) {
            pieces.add("có " + riskFlags.size() + " cảnh báo lâm sàng/vital");
        }
        if (encounter.getStatus() == EncounterStatus.READY_FOR_CONCLUSION) {
            pieces.add("đã sẵn sàng để bác sĩ kết luận");
        }
        if (encounter.getStatus() == EncounterStatus.COMPLETED) {
            pieces.add("lần khám đã hoàn tất");
        }
        return String.join(". ", pieces) + ".";
    }

    private List<String> buildHighlightedResults(List<ServiceOrder> orders, Map<Long, ServiceResult> resultByItemId) {
        List<String> highlights = new ArrayList<>();
        for (ServiceOrder order : orders) {
            for (ServiceOrderItem item : order.getItems()) {
                ServiceResult result = resultByItemId.get(item.getId());
                if (result == null) {
                    continue;
                }

                String serviceName = item.getServiceNameVnSnapshot() != null && !item.getServiceNameVnSnapshot().isBlank()
                        ? item.getServiceNameVnSnapshot()
                        : item.getServiceCodeSnapshot();

                highlights.addAll(extractAbnormalLabRows(serviceName, result));
                if (highlights.size() >= 6) {
                    return highlights.subList(0, 6);
                }

                String impression = firstNonBlank(result.getImpressionText(), result.getConclusionText(), result.getResultTextVn());
                if (impression != null && !impression.isBlank()) {
                    highlights.add(serviceName + ": " + impression);
                }
                if (highlights.size() >= 6) {
                    return highlights.subList(0, 6);
                }
            }
        }
        return highlights.stream().distinct().limit(6).toList();
    }

    private List<String> extractAbnormalLabRows(String serviceName, ServiceResult result) {
        List<String> rows = new ArrayList<>();
        if (result.getFieldValuesJson() == null || result.getFieldValuesJson().isBlank()) {
            return rows;
        }
        try {
            JsonNode root = objectMapper.readTree(result.getFieldValuesJson());
            JsonNode dataRows = root.path("rows");
            if (!dataRows.isArray()) {
                return rows;
            }
            for (JsonNode row : dataRows) {
                String flag = row.path("flag").asText("");
                if (flag.isBlank()) {
                    continue;
                }
                String parameter = row.path("parameter").asText("Chỉ số");
                String value = row.path("result").asText("—");
                String unit = row.path("unit").asText("");
                rows.add(serviceName + ": " + parameter + " = " + value + (unit.isBlank() ? "" : " " + unit) + " (" + flag + ")");
            }
        } catch (Exception ignored) {
            return rows;
        }
        return rows;
    }

    private List<String> buildRiskFlags(Encounter encounter) {
        List<String> flags = new ArrayList<>();
        if (encounter.getAllergySnapshot() != null && !encounter.getAllergySnapshot().isBlank()) {
            flags.add("Dị ứng ghi nhận: " + encounter.getAllergySnapshot());
        }
        if (encounter.getChronicDiseaseSnapshot() != null && !encounter.getChronicDiseaseSnapshot().isBlank()) {
            flags.add("Bệnh nền/tiền sử mạn tính: " + encounter.getChronicDiseaseSnapshot());
        }
        if (encounter.getTemperatureC() != null && encounter.getTemperatureC() >= 38.0) {
            flags.add("Nhiệt độ tăng: " + encounter.getTemperatureC() + "°C");
        }
        if (encounter.getSpo2() != null && encounter.getSpo2() < 94) {
            flags.add("SpO2 thấp: " + encounter.getSpo2() + "%");
        }
        if (encounter.getPulse() != null && (encounter.getPulse() > 100 || encounter.getPulse() < 50)) {
            flags.add("Mạch cần lưu ý: " + encounter.getPulse() + " bpm");
        }
        if (encounter.getSystolicBp() != null && (encounter.getSystolicBp() >= 140 || encounter.getSystolicBp() < 90)) {
            flags.add("Huyết áp tâm thu bất thường: " + encounter.getSystolicBp() + " mmHg");
        }
        if (encounter.getDiastolicBp() != null && (encounter.getDiastolicBp() >= 90 || encounter.getDiastolicBp() < 60)) {
            flags.add("Huyết áp tâm trương bất thường: " + encounter.getDiastolicBp() + " mmHg");
        }
        return flags.stream().distinct().toList();
    }

    private List<String> buildNextStepSuggestions(Encounter encounter, List<ServiceOrder> orders) {
        List<String> suggestions = new ArrayList<>();
        boolean hasWaitingResults = orders.stream()
                .flatMap(order -> order.getItems().stream())
                .anyMatch(item -> item.getStatus() == ServiceOrderItemStatus.WAITING_EXECUTION || item.getStatus() == ServiceOrderItemStatus.IN_PROGRESS);

        if (hasWaitingResults) {
            suggestions.add("Tiếp tục chờ đủ kết quả cận lâm sàng trước khi hoàn tất hồ sơ.");
        }
        if (encounter.getFinalDiagnosis() == null || encounter.getFinalDiagnosis().isBlank()) {
            suggestions.add("Bổ sung chẩn đoán cuối cùng trước khi kết thúc lần khám.");
        }
        if (encounter.getConclusion() == null || encounter.getConclusion().isBlank()) {
            suggestions.add("Bổ sung kết luận và kế hoạch xử trí để hoàn tất hồ sơ.");
        }
        if (encounter.getFollowUpDate() != null) {
            suggestions.add("Đã có lịch tái khám vào " + encounter.getFollowUpDate() + ", cần dặn dò bệnh nhân rõ ràng.");
        } else {
            suggestions.add("Cân nhắc thiết lập lịch tái khám nếu bệnh nhân cần theo dõi tiếp.");
        }
        if (encounter.getStatus() == EncounterStatus.READY_FOR_CONCLUSION) {
            suggestions.add("Có thể chốt kết luận, kê đơn và hoàn tất lần khám nếu chỉ định đã xong.");
        }
        return suggestions.stream().distinct().toList();
    }

    private String buildDraftDoctorNote(Encounter encounter, List<String> highlightedResults, List<String> riskFlags, List<String> nextSteps) {
        List<String> sections = new ArrayList<>();
        sections.add("Tóm tắt bệnh án: " + safe(firstNonBlank(encounter.getChiefComplaint(), encounter.getClinicalNote(), "Chưa nhập triệu chứng chính")) + ".");
        if (!highlightedResults.isEmpty()) {
            sections.add("Điểm cận lâm sàng nổi bật: " + String.join("; ", highlightedResults) + ".");
        }
        if (!riskFlags.isEmpty()) {
            sections.add("Yếu tố cần lưu ý: " + String.join("; ", riskFlags) + ".");
        }
        sections.add("Định hướng tiếp theo: " + String.join(" ", nextSteps));
        return String.join(" ", sections).trim();
    }

    private String buildAiPrompt(Encounter encounter, String quickSummary, List<String> highlightedResults, List<String> riskFlags, List<String> nextSteps) {
        String sanitizedQuickSummary = sanitizeForAi(encounter, quickSummary, CLINICAL_TEXT_LIMIT);
        List<String> sanitizedHighlightedResults = sanitizeListForAi(encounter, highlightedResults, SHORT_TEXT_LIMIT);
        List<String> sanitizedRiskFlags = sanitizeListForAi(encounter, riskFlags, SHORT_TEXT_LIMIT);
        List<String> sanitizedNextSteps = sanitizeListForAi(encounter, nextSteps, SHORT_TEXT_LIMIT);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("patient", patientContext(encounter));
        payload.put("chiefComplaint", sanitizeForAi(encounter, encounter.getChiefComplaint(), CLINICAL_TEXT_LIMIT));
        payload.put("clinicalNote", sanitizeForAi(encounter, encounter.getClinicalNote(), CLINICAL_TEXT_LIMIT));
        payload.put("preliminaryDiagnosis", sanitizeForAi(encounter, encounter.getPreliminaryDiagnosis(), SHORT_TEXT_LIMIT));
        payload.put("finalDiagnosis", sanitizeForAi(encounter, encounter.getFinalDiagnosis(), SHORT_TEXT_LIMIT));
        payload.put("conclusion", sanitizeForAi(encounter, encounter.getConclusion(), SHORT_TEXT_LIMIT));
        payload.put("quickSummary", sanitizedQuickSummary);
        payload.put("highlightedResults", sanitizedHighlightedResults);
        payload.put("riskFlags", sanitizedRiskFlags);
        payload.put("nextSteps", sanitizedNextSteps);
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload)
                    + "\nHãy soạn một ghi chú tóm tắt ngắn cho bác sĩ bằng tiếng Việt, chia 2-3 câu, không kết luận vượt quá dữ liệu đã có.";
        } catch (Exception ex) {
            return sanitizedQuickSummary
                    + "\nĐiểm nổi bật: " + String.join("; ", sanitizedHighlightedResults)
                    + "\nCảnh báo: " + String.join("; ", sanitizedRiskFlags);
        }
    }

    private Map<String, Object> patientContext(Encounter encounter) {
        Map<String, Object> patient = new LinkedHashMap<>();
        if (encounter.getPatientDobSnapshot() != null) {
            patient.put("birthYear", encounter.getPatientDobSnapshot().getYear());
        }
        if (encounter.getPatientGenderSnapshot() != null) {
            patient.put("gender", encounter.getPatientGenderSnapshot().name());
        }
        return patient;
    }

    private String demographicSummary(Encounter encounter) {
        List<String> pieces = new ArrayList<>();
        if (encounter.getPatientDobSnapshot() != null) {
            pieces.add("sinh năm " + encounter.getPatientDobSnapshot().getYear());
        }
        if (encounter.getPatientGenderSnapshot() != null) {
            pieces.add("giới tính " + encounter.getPatientGenderSnapshot().name());
        }
        return pieces.isEmpty() ? "Người bệnh" : "Người bệnh " + String.join(", ", pieces);
    }

    private List<String> sanitizeListForAi(Encounter encounter, List<String> values, int maxLength) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(value -> sanitizeForAi(encounter, value, maxLength))
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    private String sanitizeForAi(Encounter encounter, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String sanitized = value.trim();
        sanitized = redactKnownValue(sanitized, encounter.getPatientFullNameSnapshot(), "[patient]");
        sanitized = redactKnownValue(sanitized, encounter.getPatientPhoneSnapshot(), "[phone]");
        sanitized = redactKnownValue(sanitized, encounter.getPatientEmailSnapshot(), "[email]");
        sanitized = sanitized.replaceAll("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", "[email]");
        sanitized = sanitized.replaceAll("(?<!\\d)(?:\\+?84|0)\\d{9,10}(?!\\d)", "[phone]");
        return truncate(sanitized, maxLength);
    }

    private String redactKnownValue(String source, String value, String replacement) {
        if (source == null || source.isBlank() || value == null || value.isBlank()) {
            return source;
        }
        return source.replace(value.trim(), replacement);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)).trim() + "...";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
