package com.PrimeCare.PrimeCare.modules.ai.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiCompatibleClient {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().build();
    private static final String GEMINI_OPENAI_BASE = "generativelanguage.googleapis.com/v1beta/openai";

    private final ObjectMapper objectMapper;

    @Value("${app.ai.enabled:false}")
    private boolean aiEnabled;

    @Value("${app.ai.chat-url:}")
    private String chatUrl;

    @Value("${app.ai.api-key:}")
    private String apiKey;

    @Value("${app.ai.model:gemini-2.5-flash}")
    private String model;

    @Value("${app.ai.temperature:0.2}")
    private double temperature;

    @Value("${app.ai.timeout-seconds:20}")
    private long timeoutSeconds;

    public Optional<String> generateText(String systemPrompt, String userPrompt) {
        return generateChat(systemPrompt, List.of(new ChatMessage("user", userPrompt == null ? "" : userPrompt)));
    }

    public Optional<String> generateChat(String systemPrompt, List<ChatMessage> messages) {
        if (!isEnabled()) {
            return Optional.empty();
        }

        try {
            List<Map<String, String>> requestMessages = new ArrayList<>();
            requestMessages.add(Map.of("role", "system", "content", systemPrompt == null ? "" : systemPrompt));
            if (messages != null) {
                for (ChatMessage message : messages) {
                    if (message == null || message.role() == null || message.content() == null) {
                        continue;
                    }
                    requestMessages.add(Map.of(
                            "role", normalizeRole(message.role()),
                            "content", message.content().trim()
                    ));
                }
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", normalizedModel());
            payload.put("temperature", temperature);
            payload.put("messages", requestMessages);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(chatUrl))
                    .timeout(Duration.ofSeconds(Math.max(5L, timeoutSeconds)))
                    .header("Content-Type", "application/json");

            if (apiKey != null && !apiKey.isBlank()) {
                builder.header("Authorization", "Bearer " + apiKey.trim());
            }

            HttpRequest request = builder
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.warn("AI provider call returned HTTP {} for {}", response.statusCode(), providerLabel());
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.body());
            String content = extractContent(root);
            if (content == null || content.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(content.trim());
        } catch (Exception ex) {
            log.warn("AI provider call failed for {}: {}", providerLabel(), ex.getMessage());
            return Optional.empty();
        }
    }

    public boolean isEnabled() {
        return aiEnabled && chatUrl != null && !chatUrl.isBlank();
    }

    public String providerLabel() {
        if (isGeminiProvider()) {
            if (normalizedModel().startsWith("gemini-2.5-flash")) {
                return "GEMINI_2_5_FLASH";
            }
            return "GEMINI_LIVE";
        }
        return "LIVE_AI";
    }

    private boolean isGeminiProvider() {
        String url = safeLower(chatUrl);
        String currentModel = safeLower(model);
        return url.contains(GEMINI_OPENAI_BASE) || currentModel.startsWith("gemini");
    }

    private String normalizedModel() {
        String value = model == null || model.isBlank() ? "gemini-2.5-flash" : model.trim();
        return value;
    }

    private String normalizeRole(String role) {
        String normalized = role == null ? "user" : role.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "assistant", "system", "user" -> normalized;
            default -> "user";
        };
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String extractContent(JsonNode root) {
        if (root == null || root.isNull()) {
            return null;
        }

        JsonNode directOutput = root.path("output_text");
        if (directOutput.isTextual()) {
            return directOutput.asText();
        }

        JsonNode choices = root.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            JsonNode messageContent = choices.get(0).path("message").path("content");
            String extracted = extractMessageContent(messageContent);
            if (extracted != null && !extracted.isBlank()) {
                return extracted;
            }
        }

        JsonNode content = root.path("content");
        if (content.isTextual()) {
            return content.asText();
        }

        return null;
    }

    private String extractMessageContent(JsonNode contentNode) {
        if (contentNode == null || contentNode.isNull()) {
            return null;
        }
        if (contentNode.isTextual()) {
            return contentNode.asText();
        }
        if (contentNode.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode node : contentNode) {
                if (node == null || node.isNull()) {
                    continue;
                }
                JsonNode textNode = node.path("text");
                if (textNode.isTextual()) {
                    if (builder.length() > 0) {
                        builder.append('\n');
                    }
                    builder.append(textNode.asText());
                }
            }
            return builder.toString();
        }
        return null;
    }

    public record ChatMessage(String role, String content) {
    }
}
