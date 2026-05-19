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

    public Optional<String> generateText(String systemPrompt, String userPrompt, double requestTemperature, long requestTimeoutSeconds) {
        return generateChat(
                systemPrompt,
                List.of(new ChatMessage("user", userPrompt == null ? "" : userPrompt)),
                requestTemperature,
                requestTimeoutSeconds
        );
    }

    public Optional<String> generateChat(String systemPrompt, List<ChatMessage> messages) {
        return generateChat(systemPrompt, messages, temperature, timeoutSeconds);
    }

    private Optional<String> generateChat(
            String systemPrompt,
            List<ChatMessage> messages,
            double requestTemperature,
            long requestTimeoutSeconds
    ) {
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
                            "content", message.content().toString().trim()
                    ));
                }
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", normalizedModel());
            payload.put("temperature", Math.max(0.0d, requestTemperature));
            payload.put("messages", requestMessages);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(chatUrl))
                    .timeout(Duration.ofSeconds(Math.max(5L, requestTimeoutSeconds)))
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

    /**
     * Enhanced chat completion that supports OpenAI-compatible tool/function calling.
     * Accepts an optional list of tool definitions (JSON Schema) and returns either
     * a text response or a list of tool calls requested by the AI.
     */
    public Optional<ChatCompletionResult> generateChatWithTools(String systemPrompt,
                                                                 List<ToolAwareMessage> messages,
                                                                 List<Map<String, Object>> tools) {
        if (!isEnabled()) {
            return Optional.empty();
        }

        try {
            List<Map<String, Object>> requestMessages = new ArrayList<>();
            requestMessages.add(Map.of("role", "system", "content", systemPrompt == null ? "" : systemPrompt));

            if (messages != null) {
                for (ToolAwareMessage msg : messages) {
                    if (msg == null || msg.role() == null) {
                        continue;
                    }
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("role", normalizeRoleExtended(msg.role()));

                    if (msg.content() != null) {
                        entry.put("content", msg.content().toString().trim());
                    }
                    if (msg.toolCallId() != null && !msg.toolCallId().isBlank()) {
                        entry.put("tool_call_id", msg.toolCallId());
                    }
                    if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                        List<Map<String, Object>> tcList = new ArrayList<>();
                        for (ToolCall tc : msg.toolCalls()) {
                            Map<String, Object> tcMap = new LinkedHashMap<>();
                            tcMap.put("id", tc.id());
                            tcMap.put("type", "function");
                            tcMap.put("function", Map.of("name", tc.functionName(), "arguments", tc.arguments()));
                            tcList.add(tcMap);
                        }
                        entry.put("tool_calls", tcList);
                    }

                    requestMessages.add(entry);
                }
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", normalizedModel());
            payload.put("temperature", temperature);
            payload.put("messages", requestMessages);

            if (tools != null && !tools.isEmpty()) {
                payload.put("tools", tools);
            }

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

            HttpResponse<String> httpResponse = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (httpResponse.statusCode() >= 400) {
                log.warn("AI provider tool call returned HTTP {} for {}", httpResponse.statusCode(), providerLabel());
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(httpResponse.body());
            return Optional.ofNullable(extractChatCompletionResult(root));
        } catch (Exception ex) {
            log.warn("AI provider tool call failed for {}: {}", providerLabel(), ex.getMessage());
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

    public String modelVersion() {
        return normalizedModel();
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

    private String normalizeRoleExtended(String role) {
        String normalized = role == null ? "user" : role.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "assistant", "system", "user", "tool" -> normalized;
            default -> "user";
        };
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private ChatCompletionResult extractChatCompletionResult(JsonNode root) {
        if (root == null || root.isNull()) {
            return null;
        }

        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            // Fallback: try extracting direct content
            String content = extractContent(root);
            return content != null && !content.isBlank()
                    ? new ChatCompletionResult(content.trim(), null)
                    : null;
        }

        JsonNode firstChoice = choices.get(0);
        JsonNode message = firstChoice.path("message");

        // Check for tool_calls first
        JsonNode toolCallsNode = message.path("tool_calls");
        if (toolCallsNode.isArray() && !toolCallsNode.isEmpty()) {
            List<ToolCall> toolCalls = new ArrayList<>();
            for (JsonNode tcNode : toolCallsNode) {
                String id = tcNode.path("id").asText("");
                JsonNode functionNode = tcNode.path("function");
                String functionName = functionNode.path("name").asText("");
                String arguments = functionNode.path("arguments").asText("{}");
                if (!functionName.isBlank()) {
                    toolCalls.add(new ToolCall(id, functionName, arguments));
                }
            }
            if (!toolCalls.isEmpty()) {
                return new ChatCompletionResult(null, toolCalls);
            }
        }

        // Fallback to regular content
        JsonNode messageContent = message.path("content");
        String extracted = extractMessageContent(messageContent);
        if (extracted != null && !extracted.isBlank()) {
            return new ChatCompletionResult(extracted.trim(), null);
        }

        return null;
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

    // ─── Records ────────────────────────────────────────────────────────────────

    /** Original simple chat message (backward compatible). */
    public record ChatMessage(String role, String content) {
    }

    /** Extended message supporting tool role, tool_call_id, and assistant tool_calls. */
    public record ToolAwareMessage(String role, Object content, String toolCallId, List<ToolCall> toolCalls) {

        public static ToolAwareMessage system(String content) {
            return new ToolAwareMessage("system", content, null, null);
        }

        public static ToolAwareMessage user(String content) {
            return new ToolAwareMessage("user", content, null, null);
        }

        public static ToolAwareMessage assistant(String content) {
            return new ToolAwareMessage("assistant", content, null, null);
        }

        public static ToolAwareMessage assistantWithToolCalls(List<ToolCall> toolCalls) {
            return new ToolAwareMessage("assistant", null, null, toolCalls);
        }

        public static ToolAwareMessage toolResult(String toolCallId, String resultJson) {
            return new ToolAwareMessage("tool", resultJson, toolCallId, null);
        }
    }

    /** Represents a function call requested by the AI. */
    public record ToolCall(String id, String functionName, String arguments) {
    }

    /** Result of a chat completion: either textual content or a list of tool calls. */
    public record ChatCompletionResult(String textContent, List<ToolCall> toolCalls) {

        public boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }

        public boolean hasText() {
            return textContent != null && !textContent.isBlank();
        }
    }
}
