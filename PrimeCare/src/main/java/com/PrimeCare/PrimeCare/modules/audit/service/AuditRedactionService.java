package com.PrimeCare.PrimeCare.modules.audit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AuditRedactionService {

    private static final String REDACTED = "[REDACTED]";
    private static final Set<String> EXACT_SENSITIVE_KEYS = Set.of(
            "password",
            "oldpassword",
            "newpassword",
            "confirmpassword",
            "otp",
            "verificationcode",
            "token",
            "accesstoken",
            "refreshtoken",
            "resettoken",
            "setupurl",
            "reseturl",
            "authorization",
            "secret",
            "secretkey",
            "privatekey",
            "paymentsecret",
            "paymenturl",
            "vnppaymenturl",
            "vnpsecurehash"
    );

    private final ObjectMapper objectMapper;

    public String writeRedactedJson(Object value) {
        if (value == null) {
            return null;
        }

        try {
            JsonNode tree = objectMapper.valueToTree(value);
            JsonNode redacted = redactNode(null, tree);
            return objectMapper.writeValueAsString(redacted);
        } catch (Exception ex) {
            return objectMapper.createObjectNode()
                    .put("serializationError", true)
                    .put("valueType", value.getClass().getName())
                    .toString();
        }
    }

    public String redactJson(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }

        try {
            JsonNode tree = objectMapper.readTree(json);
            return objectMapper.writeValueAsString(redactNode(null, tree));
        } catch (Exception ex) {
            return json;
        }
    }

    private JsonNode redactNode(String key, JsonNode node) {
        if (node == null || node.isNull()) {
            return node;
        }

        if (isSensitiveKeyValue(key, node)) {
            return TextNode.valueOf(REDACTED);
        }

        if (node.isObject()) {
            ObjectNode copy = objectMapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                copy.set(field.getKey(), redactNode(field.getKey(), field.getValue()));
            }
            return copy;
        }

        if (node.isArray()) {
            ArrayNode copy = objectMapper.createArrayNode();
            node.forEach(item -> copy.add(redactNode(key, item)));
            return copy;
        }

        return node;
    }

    private boolean isSensitiveKeyValue(String key, JsonNode node) {
        if (key == null) {
            return false;
        }

        String normalized = normalizeKey(key);
        if (EXACT_SENSITIVE_KEYS.contains(normalized)) {
            return true;
        }

        if (normalized.contains("password")
                || normalized.contains("authorization")
                || normalized.contains("accesstoken")
                || normalized.contains("refreshtoken")
                || normalized.contains("resettoken")
                || normalized.contains("setupurl")
                || normalized.contains("reseturl")
                || normalized.contains("privatekey")
                || normalized.contains("secret")
                || normalized.contains("paymenturl")
                || normalized.contains("otp")) {
            return true;
        }

        if (normalized.equals("code") && node.isTextual()) {
            String value = node.asText();
            return value != null && value.matches("\\d{4,8}");
        }

        return false;
    }

    private String normalizeKey(String key) {
        return key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }
}
