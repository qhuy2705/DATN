package com.PrimeCare.PrimeCare.modules.publiclookup.service;

import com.PrimeCare.PrimeCare.modules.ai.support.OpenAiCompatibleClient;
import com.PrimeCare.PrimeCare.modules.ai.support.OpenAiCompatibleClient.ChatCompletionResult;
import com.PrimeCare.PrimeCare.modules.ai.support.OpenAiCompatibleClient.ToolCall;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.assistant.AiBookingDraftResponse;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.request.PublicAssistantMessageRequest;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.request.PublicAssistantRequest;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.response.PublicAssistantActionResponse;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.response.PublicAssistantResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PublicAssistantProductionReadinessTest {

    private static final ZoneId SYSTEM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String VALID_SLOT_ID = "SLOT|1|2|3|" + LocalDate.now(SYSTEM_ZONE).plusDays(1) + "|AM|09:00";

    @Mock
    private OpenAiCompatibleClient openAiCompatibleClient;
    @Mock
    private PublicAssistantKnowledgeProvider knowledgeProvider;
    @Mock
    private PublicAssistantIntentDetector intentDetector;
    @Mock
    private AssistantToolService assistantToolService;
    @Mock
    private AiBookingAssistantService aiBookingAssistantService;

    private PublicAssistantService service;

    @BeforeEach
    void setUp() {
        service = new PublicAssistantService(
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
    }

    @Test
    @SuppressWarnings("unchecked")
    void promptContainsCurrentDateAndAssistantHistoryIsCleanedBeforeSendingToAi() {
        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<OpenAiCompatibleClient.ChatMessage>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        when(openAiCompatibleClient.generateChat(systemPromptCaptor.capture(), messagesCaptor.capture()))
                .thenReturn(Optional.of("PrimeCare AI đã nhận câu hỏi. ```json{\"doctorId\":1}```"));

        PublicAssistantRequest request = request("Tôi muốn khám ngày mai");
        request.setHistory(List.of(
                message("assistant", "PrimeCare AI đã tìm lịch. ```json{\"doctorId\":1}```"),
                message("user", "Cảm ơn")
        ));

        PublicAssistantResponse response = service.ask(request);

        String today = LocalDate.now(SYSTEM_ZONE).toString();
        assertThat(systemPromptCaptor.getValue()).contains("Hôm nay là: " + today);
        assertThat(systemPromptCaptor.getValue()).contains("Múi giờ hệ thống: Asia/Ho_Chi_Minh");

        List<OpenAiCompatibleClient.ChatMessage> messages = messagesCaptor.getValue();
        assertThat(messages.get(messages.size() - 1).content()).contains("Hôm nay là: " + today);
        assertThat(messages)
                .filteredOn(message -> "assistant".equals(message.role()))
                .allSatisfy(message -> assertThat(message.content()).doesNotContain("```json", "doctorId"));
        assertThat(response.getAnswer()).isEqualTo("PrimeCare AI đã nhận câu hỏi.");
    }

    @Test
    void userVisibleAnswerRemovesInlineMultilineMultipleAndTrailingJsonPayloads() {
        when(openAiCompatibleClient.generateChat(anyString(), anyList())).thenReturn(Optional.of(
                "PrimeCare AI đã tìm thấy thông tin.\n"
                        + "```json\n{\"slotId\":\"" + VALID_SLOT_ID + "\"}\n```\n"
                        + "Bạn có thể chọn ca phù hợp.\n"
                        + "```JSON{\"doctorId\":1}```\n"
                        + "{\"doctorId\":1,\"slotId\":\"" + VALID_SLOT_ID + "\"}"
        ));

        PublicAssistantResponse response = service.ask(request("Tôi muốn khám ngày mai"));

        assertThat(response.getAnswer()).contains("PrimeCare AI đã tìm thấy thông tin.");
        assertThat(response.getAnswer()).contains("Bạn có thể chọn ca phù hợp.");
        assertThat(response.getAnswer()).doesNotContain("```json", "```JSON", "slotId", "doctorId");
    }

    @Test
    void invalidJsonPayloadTypesDoNotThrowAndDoNotCreatePrefillAction() {
        when(intentDetector.detect(anyString(), anyString())).thenReturn(AssistantIntent.BOOKING);
        when(openAiCompatibleClient.isEnabled()).thenReturn(true);
        when(openAiCompatibleClient.generateChatWithTools(anyString(), anyList(), nullable(List.class)))
                .thenReturn(Optional.of(new ChatCompletionResult(
                        "Tôi đã ghi nhận lựa chọn. ```json{\"doctorId\":\"doc-123\",\"slotId\":\"\"}```",
                        null
                )));

        PublicAssistantResponse response = service.ask(request("Tôi muốn khám ngày mai"));

        assertThat(response.getAnswer()).isEqualTo("Tôi đã ghi nhận lựa chọn.");
        assertThat(response.getActions()).extracting(PublicAssistantActionResponse::getType)
                .doesNotContain("BOOK_APPOINTMENT_PREFILL");
        verify(aiBookingAssistantService, never()).validateBookingDraftPayload(any());
        verify(openAiCompatibleClient, never()).generateChat(anyString(), anyList());
    }

    @Test
    void malformedJsonPayloadDoesNotThrowAndDoesNotFallBackToStandardChat() {
        when(intentDetector.detect(anyString(), anyString())).thenReturn(AssistantIntent.BOOKING);
        when(openAiCompatibleClient.isEnabled()).thenReturn(true);
        when(openAiCompatibleClient.generateChatWithTools(anyString(), anyList(), nullable(List.class)))
                .thenReturn(Optional.of(new ChatCompletionResult(
                        "Tôi đã ghi nhận lựa chọn. ```json{\"doctorId\":```",
                        null
                )));

        PublicAssistantResponse response = service.ask(request("Tôi muốn khám ngày mai"));

        assertThat(response.getAnswer()).isEqualTo("Tôi đã ghi nhận lựa chọn.");
        assertThat(response.getActions()).extracting(PublicAssistantActionResponse::getType)
                .doesNotContain("BOOK_APPOINTMENT_PREFILL");
        verify(openAiCompatibleClient, never()).generateChat(anyString(), anyList());
    }

    @Test
    void toolFlowFailureAfterToolCallReturnsHardcodedFallbackWithoutThirdLlmCall() {
        when(intentDetector.detect(anyString(), anyString())).thenReturn(AssistantIntent.BOOKING);
        when(openAiCompatibleClient.isEnabled()).thenReturn(true);
        ToolCall toolCall = new ToolCall("call-1", "searchAvailableSlots", "{\"specialtyKeyword\":\"tiêu hóa\",\"dateKeyword\":\"ngày mai\"}");
        when(openAiCompatibleClient.generateChatWithTools(anyString(), anyList(), nullable(List.class)))
                .thenReturn(Optional.of(new ChatCompletionResult(null, List.of(toolCall))))
                .thenReturn(Optional.empty());
        when(assistantToolService.searchAvailableSlots(anyString(), anyString()))
                .thenReturn("{\"totalSlots\":0,\"slots\":[]}");

        PublicAssistantResponse response = service.ask(request("Có lịch tiêu hóa ngày mai không?"));

        assertThat(response.getAnswer()).isEqualTo("PrimeCare AI chưa thể hoàn tất tra cứu ở thời điểm này. Bạn vui lòng thử lại hoặc chọn chuyên khoa/bác sĩ cụ thể hơn.");
        assertThat(response.getActions()).isEmpty();
        assertThat(response.getSuggestions()).isEmpty();
        verify(openAiCompatibleClient, times(2)).generateChatWithTools(anyString(), anyList(), nullable(List.class));
        verify(openAiCompatibleClient, never()).generateChat(anyString(), anyList());
    }

    @Test
    void invalidSlotPayloadIsRejectedBeforePrefillAction() {
        when(intentDetector.detect(anyString(), anyString())).thenReturn(AssistantIntent.BOOKING);
        when(openAiCompatibleClient.isEnabled()).thenReturn(true);
        when(openAiCompatibleClient.generateChatWithTools(anyString(), anyList(), nullable(List.class)))
                .thenReturn(Optional.of(new ChatCompletionResult(
                        "Tôi đã tìm thấy một ca. ```json{\"slotId\":\"SLOT|1|2|3|2099-01-01|AM|09:00\",\"doctorId\":3,\"specialtyId\":2,\"facilityId\":1}```",
                        null
                )));
        when(aiBookingAssistantService.validateBookingDraftPayload(any())).thenReturn(Optional.empty());

        PublicAssistantResponse response = service.ask(request("Tôi muốn chọn ca này"));

        assertThat(response.getAnswer()).isEqualTo("Tôi đã tìm thấy một ca.");
        assertThat(response.getActions()).extracting(PublicAssistantActionResponse::getType)
                .doesNotContain("BOOK_APPOINTMENT_PREFILL");
    }

    @Test
    void validSlotPayloadAddsValidatedBookingDraftToPrefillAction() {
        when(intentDetector.detect(anyString(), anyString())).thenReturn(AssistantIntent.BOOKING);
        when(openAiCompatibleClient.isEnabled()).thenReturn(true);
        AiBookingDraftResponse bookingDraft = AiBookingDraftResponse.builder()
                .source("AI_ASSISTANT")
                .slotId(VALID_SLOT_ID)
                .doctorId(3L)
                .specialtyId(2L)
                .facilityId(1L)
                .appointmentDate(LocalDate.now(SYSTEM_ZONE).plusDays(1))
                .startTime("09:00")
                .build();
        when(openAiCompatibleClient.generateChatWithTools(anyString(), anyList(), nullable(List.class)))
                .thenReturn(Optional.of(new ChatCompletionResult(
                        "Tôi đã tìm thấy một ca. ```json{\"slotId\":\"" + VALID_SLOT_ID + "\",\"doctorId\":3,\"specialtyId\":2,\"facilityId\":1}```",
                        null
                )));
        when(aiBookingAssistantService.validateBookingDraftPayload(any())).thenReturn(Optional.of(bookingDraft));

        PublicAssistantResponse response = service.ask(request("Tôi muốn chọn ca này"));

        assertThat(response.getActions()).extracting(PublicAssistantActionResponse::getType)
                .contains("BOOK_APPOINTMENT_PREFILL");
        PublicAssistantActionResponse prefillAction = response.getActions().get(0);
        assertThat(prefillAction.getPayload()).containsEntry("bookingDraft", bookingDraft);
    }

    private PublicAssistantRequest request(String question) {
        PublicAssistantRequest request = new PublicAssistantRequest();
        request.setQuestion(question);
        return request;
    }

    private PublicAssistantMessageRequest message(String role, String text) {
        PublicAssistantMessageRequest message = new PublicAssistantMessageRequest();
        message.setRole(role);
        message.setText(text);
        return message;
    }
}
