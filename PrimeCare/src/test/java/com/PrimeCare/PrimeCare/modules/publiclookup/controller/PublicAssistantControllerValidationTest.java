package com.PrimeCare.PrimeCare.modules.publiclookup.controller;

import com.PrimeCare.PrimeCare.modules.publiclookup.service.AiBookingAssistantService;
import com.PrimeCare.PrimeCare.modules.publiclookup.service.PublicAssistantService;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.response.PublicAssistantResponse;
import com.PrimeCare.PrimeCare.shared.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.mock;

class PublicAssistantControllerValidationTest {

    private MockMvc mockMvc;
    private PublicAssistantService publicAssistantService;
    private AiBookingAssistantService aiBookingAssistantService;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        publicAssistantService = mock(PublicAssistantService.class);
        aiBookingAssistantService = mock(AiBookingAssistantService.class);
        when(publicAssistantService.ask(any())).thenReturn(PublicAssistantResponse.builder().message("OK").build());
        when(aiBookingAssistantService.chat(any())).thenReturn(PublicAssistantResponse.builder().message("OK").build());
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(new PublicAssistantController(publicAssistantService, aiBookingAssistantService))
                                 .setControllerAdvice(new GlobalExceptionHandler())
                                 .setValidator(validator)
                                 .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                                 .build();
    }

    @Test
    void askRejectsQuestionOverLimit() throws Exception {
        String payload = """
                {
                  "question": "%s"
                }
                """.formatted("a".repeat(2001));

        mockMvc.perform(post("/api/public/assistant/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.success").value(false))
               .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
               .andExpect(jsonPath("$.details.fields.question").exists());
    }

    @Test
    void askRejectsTooManyHistoryMessages() throws Exception {
        String history = IntStream.range(0, 21)
                                  .mapToObj(i -> """
                                          {"role":"user","text":"hello %d"}""".formatted(i))
                                  .collect(Collectors.joining(","));
        String payload = """
                {
                  "question": "Xin chao",
                  "history": [%s]
                }
                """.formatted(history);

        mockMvc.perform(post("/api/public/assistant/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.success").value(false))
               .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
               .andExpect(jsonPath("$.details.fields.history").exists());
    }

    @Test
    void askRejectsOversizedNestedHistoryMessage() throws Exception {
        String payload = """
                {
                  "question": "Xin chao",
                  "history": [
                    {"role":"user","text":"%s"}
                  ]
                }
                """.formatted("a".repeat(8001));

        mockMvc.perform(post("/api/public/assistant/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.success").value(false))
               .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
               .andExpect(jsonPath("$['details']['fields']['history[0].text']").exists());
    }

    @Test
    void askAcceptsAssistantHistoryLongerThanOldOneThousandCharacterLimit() throws Exception {
        String payload = """
                {
                  "question": "Cơ sở này ở đâu?",
                  "history": [
                    {"role":"assistant","text":"%s"}
                  ]
                }
                """.formatted("a".repeat(1500));

        mockMvc.perform(post("/api/public/assistant/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
               .andExpect(status().isOk());
    }

    @Test
    void chatAcceptsTenHistoryMessages() throws Exception {
        String history = IntStream.range(0, 10)
                                  .mapToObj(i -> """
                                          {"role":"user","text":"hello %d"}""".formatted(i))
                                  .collect(Collectors.joining(","));
        String payload = """
                {
                  "question": "Cơ sở này ở đâu?",
                  "history": [%s]
                }
                """.formatted(history);

        mockMvc.perform(post("/api/public/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
               .andExpect(status().isOk());
    }
}
