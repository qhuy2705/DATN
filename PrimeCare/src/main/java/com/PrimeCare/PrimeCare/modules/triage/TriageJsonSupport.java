package com.PrimeCare.PrimeCare.modules.triage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public final class TriageJsonSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private TriageJsonSupport() {
    }

    public static String writeStringList(List<String> values) {
        return writeObject(values == null ? List.of() : values);
    }

    public static String writeObject(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException ex) {
            log.warn("Cannot serialize triage JSON payload: {}", ex.getMessage());
            return "[]";
        }
    }

    public static List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> values = OBJECT_MAPPER.readValue(json, STRING_LIST);
            return values == null
                    ? List.of()
                    : values.stream()
                            .filter(value -> value != null && !value.isBlank())
                            .toList();
        } catch (JsonProcessingException ex) {
            log.warn("Cannot parse triage string list JSON: {}", ex.getMessage());
            return List.of();
        }
    }

    public static <T> List<T> readObjectList(String json, Class<T> elementType) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JavaType type = OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, elementType);
            List<T> values = OBJECT_MAPPER.readValue(json, type);
            return values == null ? List.of() : values;
        } catch (JsonProcessingException ex) {
            log.warn("Cannot parse triage object list JSON: {}", ex.getMessage());
            return List.of();
        }
    }
}
