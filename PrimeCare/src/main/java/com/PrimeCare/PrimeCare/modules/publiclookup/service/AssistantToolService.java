package com.PrimeCare.PrimeCare.modules.publiclookup.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantToolService {

    private final AiBookingAssistantService aiBookingAssistantService;
    private final ObjectMapper objectMapper;

    public String searchAvailableSlots(String specialtyKeyword, String dateKeyword) {
        try {
            return objectMapper.writeValueAsString(
                    aiBookingAssistantService.searchAvailableSlotsForTool(specialtyKeyword, dateKeyword)
            );
        } catch (Exception ex) {
            log.warn("AssistantToolService.searchAvailableSlots failed: {}", ex.getMessage());
            return "{\"totalSlots\":0,\"slots\":[],\"error\":\"Không thể tra cứu lịch trống lúc này.\"}";
        }
    }

    public static List<Map<String, Object>> getToolDefinitions() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", Map.of(
                "specialtyKeyword", Map.of(
                        "type", "string",
                        "description", "Keyword to search for a medical specialty, doctor specialty, or symptom-derived specialty."
                ),
                "dateKeyword", Map.of(
                        "type", "string",
                        "description", "Target date in ISO format (YYYY-MM-DD) or natural language such as today/tomorrow/ngay mai."
                )
        ));
        parameters.put("required", List.of("specialtyKeyword"));

        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", "searchAvailableSlots");
        function.put("description",
                "Search real PrimeCare database-backed doctor appointment availability. " +
                "Returns only existing specialties, doctors, facilities and currently available virtual slot IDs. " +
                "Never invent IDs or schedule data.");
        function.put("parameters", parameters);

        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("function", function);
        return List.of(tool);
    }
}
