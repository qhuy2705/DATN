package com.PrimeCare.PrimeCare.modules.publiclookup.service;

import com.PrimeCare.PrimeCare.modules.ai.support.OpenAiCompatibleClient;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.request.PublicAssistantRequest;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.response.PublicAssistantResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class PublicAssistantServiceSafetyTest {

    private final OpenAiCompatibleClient openAiCompatibleClient = mock(OpenAiCompatibleClient.class);
    private final PublicAssistantKnowledgeProvider knowledgeProvider = mock(PublicAssistantKnowledgeProvider.class);
    private final PublicAssistantIntentDetector intentDetector = mock(PublicAssistantIntentDetector.class);
    private final AssistantToolService assistantToolService = mock(AssistantToolService.class);
    private final AiBookingAssistantService aiBookingAssistantService = mock(AiBookingAssistantService.class);
    private final PublicAssistantService service = new PublicAssistantService(
            openAiCompatibleClient,
            knowledgeProvider,
            intentDetector,
            assistantToolService,
            aiBookingAssistantService,
            new ObjectMapper()
    );

    @Test
    void headacheMedicationQuestionIsHardBlockedBeforeAiOrBooking() {
        PublicAssistantResponse response = service.ask(request("Tôi đau đầu uống thuốc gì?"));

        assertMedicationBlock(response);
        verifyNoAiOrBookingCalls();
    }

    @Test
    void stomachMedicationQuestionIsHardBlockedBeforeAiOrBooking() {
        PublicAssistantResponse response = service.ask(request("Tôi đau dạ dày uống thuốc gì?"));

        assertMedicationBlock(response);
        verifyNoAiOrBookingCalls();
    }

    @Test
    void prescriptionRequestIsHardBlockedBeforeAiOrBooking() {
        PublicAssistantResponse response = service.ask(request("Kê đơn cho tôi"));

        assertMedicationBlock(response);
        verifyNoAiOrBookingCalls();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Tôi uống Panadol được không?",
            "Có thuốc nào giảm đau không?",
            "Tôi nên uống gì?",
            "Liều dùng thế nào?",
            "Treatment plan",
            "Dosage",
            "Prescription",
            "Medicine",
            "Medication"
    })
    void medicationAndTreatmentPlanRequestsAreHardBlockedBeforeAiOrBooking(String question) {
        PublicAssistantResponse response = service.ask(request(question));

        assertMedicationBlock(response);
        verifyNoAiOrBookingCalls();
    }

    @Test
    void diseaseConclusionQuestionIsHardBlockedBeforeAiOrBooking() {
        PublicAssistantResponse response = service.ask(request("Tôi bị bệnh gì?"));

        assertDiagnosisBlock(response);
        verifyNoAiOrBookingCalls();
    }

    @Test
    void cancerDiagnosisQuestionIsHardBlockedBeforeAiOrBooking() {
        PublicAssistantResponse response = service.ask(request("Tôi có bị ung thư không?"));

        assertDiagnosisBlock(response);
        verifyNoAiOrBookingCalls();
    }

    @Test
    void diagnosisRequestIsHardBlockedBeforeAiOrBooking() {
        PublicAssistantResponse response = service.ask(request("Chẩn đoán giúp tôi"));

        assertDiagnosisBlock(response);
        verifyNoAiOrBookingCalls();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Tôi có phải bị viêm loét không?",
            "Tôi có bị viêm loét dạ dày không?",
            "Kết luận giúp tôi",
            "Đau như vậy là bệnh gì?",
            "Bệnh này có nguy hiểm không?",
            "Diagnose me",
            "What disease do I have?"
    })
    void diagnosisAndConclusionRequestsAreHardBlockedBeforeAiOrBooking(String question) {
        PublicAssistantResponse response = service.ask(request(question));

        assertDiagnosisBlock(response);
        verifyNoAiOrBookingCalls();
    }

    @Test
    void emergencySymptomsAreHardBlockedBeforeAiOrBooking() {
        PublicAssistantResponse response = service.ask(request("Tôi đau ngực dữ dội khó thở"));

        assertThat(response.getAnswer()).isEqualTo(PublicAssistantSafety.EMERGENCY_BLOCK_MESSAGE);
        assertThat(response.getMessage()).isEqualTo(PublicAssistantSafety.EMERGENCY_BLOCK_MESSAGE);
        assertThat(response.getIntent()).isEqualTo("EMERGENCY_SAFETY_BLOCK");
        assertThat(response.getProvider()).isEqualTo("PrimeCare AI");
        verifyNoAiOrBookingCalls();
    }

    private void assertMedicationBlock(PublicAssistantResponse response) {
        assertThat(response.getAnswer()).isEqualTo(PublicAssistantSafety.MEDICATION_BLOCK_MESSAGE);
        assertThat(response.getMessage()).isEqualTo(PublicAssistantSafety.MEDICATION_BLOCK_MESSAGE);
        assertThat(response.getIntent()).isEqualTo("MEDICATION_SAFETY_BLOCK");
        assertThat(response.getProvider()).isEqualTo("PrimeCare AI");
        assertThat(response.getBookingDraft()).isNull();
        assertThat(response.getSuggestedDoctor()).isNull();
    }

    private void assertDiagnosisBlock(PublicAssistantResponse response) {
        assertThat(response.getAnswer()).isEqualTo(PublicAssistantSafety.DIAGNOSIS_BLOCK_MESSAGE);
        assertThat(response.getMessage()).isEqualTo(PublicAssistantSafety.DIAGNOSIS_BLOCK_MESSAGE);
        assertThat(response.getIntent()).isEqualTo("DIAGNOSIS_SAFETY_BLOCK");
        assertThat(response.getProvider()).isEqualTo("PrimeCare AI");
        assertThat(response.getBookingDraft()).isNull();
        assertThat(response.getSuggestedDoctor()).isNull();
    }

    private void verifyNoAiOrBookingCalls() {
        verifyNoInteractions(openAiCompatibleClient);
        verifyNoInteractions(aiBookingAssistantService);
        verifyNoInteractions(knowledgeProvider);
        verifyNoInteractions(intentDetector);
        verifyNoInteractions(assistantToolService);
    }

    private PublicAssistantRequest request(String question) {
        PublicAssistantRequest request = new PublicAssistantRequest();
        request.setQuestion(question);
        return request;
    }
}
