package com.PrimeCare.PrimeCare.modules.publiclookup.service;

import com.PrimeCare.PrimeCare.modules.ai.support.OpenAiCompatibleClient;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.request.PublicAssistantMessageRequest;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.request.PublicAssistantRequest;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.response.PublicAssistantResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicAssistantServiceHistoryTruncationTest {

    @Test
    @SuppressWarnings("unchecked")
    void askUsesOnlyRecentHistoryAndTruncatesByRoleBeforePromptingAi() {
        OpenAiCompatibleClient openAiCompatibleClient = mock(OpenAiCompatibleClient.class);
        PublicAssistantKnowledgeProvider knowledgeProvider = mock(PublicAssistantKnowledgeProvider.class);
        PublicAssistantIntentDetector intentDetector = mock(PublicAssistantIntentDetector.class);
        AssistantToolService assistantToolService = mock(AssistantToolService.class);
        AiBookingAssistantService aiBookingAssistantService = mock(AiBookingAssistantService.class);
        PublicAssistantService service = new PublicAssistantService(
                openAiCompatibleClient,
                knowledgeProvider,
                intentDetector,
                assistantToolService,
                aiBookingAssistantService,
                new ObjectMapper()
        );

        when(aiBookingAssistantService.tryHandle(any())).thenReturn(Optional.empty());
        when(intentDetector.looksEnglish(anyString())).thenReturn(false);
        when(intentDetector.detect(anyString(), anyString())).thenReturn(AssistantIntent.FAQ);
        when(knowledgeProvider.loadKnowledge(false)).thenReturn(new AssistantKnowledge(List.of(), List.of(), List.of(), List.of()));
        ArgumentCaptor<List<OpenAiCompatibleClient.ChatMessage>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        when(openAiCompatibleClient.generateChat(anyString(), messagesCaptor.capture()))
                .thenReturn(Optional.of("OK"));

        PublicAssistantRequest request = new PublicAssistantRequest();
        request.setQuestion("Xin chào");
        request.setHistory(longHistory(10));

        PublicAssistantResponse response = service.ask(request);

        assertThat(response.getMessage()).isNull();
        assertThat(response.getAnswer()).isEqualTo("OK");
        assertThat(response.getProvider()).isEqualTo("PrimeCare AI");

        List<OpenAiCompatibleClient.ChatMessage> messages = messagesCaptor.getValue();
        assertThat(messages).hasSize(7);
        List<OpenAiCompatibleClient.ChatMessage> historyMessages = messages.subList(0, 6);

        assertThat(historyMessages.get(0).content()).contains("user-4");
        assertThat(historyMessages.get(5).content()).contains("assistant-9");
        historyMessages.forEach(message -> {
            if ("assistant".equals(message.role())) {
                assertThat(message.content()).hasSizeLessThanOrEqualTo(300);
            } else {
                assertThat(message.content()).hasSizeLessThanOrEqualTo(800);
            }
        });
    }

    private List<PublicAssistantMessageRequest> longHistory(int count) {
        List<PublicAssistantMessageRequest> history = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            boolean assistant = i % 2 == 1;
            PublicAssistantMessageRequest message = new PublicAssistantMessageRequest();
            message.setRole(assistant ? "assistant" : "user");
            message.setText((assistant ? "assistant-" : "user-") + i + " " + "x".repeat(4000));
            history.add(message);
        }
        return history;
    }
}
