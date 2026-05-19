package com.PrimeCare.PrimeCare.modules.triage;

import com.PrimeCare.PrimeCare.modules.ai.support.OpenAiCompatibleClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TriageAiExtractorTest {

    @Test
    void invalidJsonFallsBackToEmptyExtraction() {
        OpenAiCompatibleClient client = mock(OpenAiCompatibleClient.class);
        when(client.generateText(anyString(), anyString(), anyDouble(), anyLong()))
                .thenReturn(Optional.of("không phải JSON"));

        TriageAiExtractor extractor = new TriageAiExtractor(client, new ObjectMapper());

        AiTriageExtractionResult result = extractor.extract("Không khỏe", null);

        assertThat(result.hasSignal()).isFalse();
    }
}
