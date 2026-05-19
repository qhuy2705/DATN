package com.PrimeCare.PrimeCare.modules.publiclookup.controller;

import com.PrimeCare.PrimeCare.modules.publiclookup.dto.response.PublicAssistantResponse;
import com.PrimeCare.PrimeCare.modules.publiclookup.service.AiBookingAssistantService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("deprecation")
class AiBookingControllerLegacyTest {

    private MockMvc mockMvc;
    private AiBookingAssistantService aiBookingAssistantService;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        aiBookingAssistantService = mock(AiBookingAssistantService.class);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(new AiBookingController(aiBookingAssistantService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void legacyChatRouteDelegatesToBookingAssistantService() throws Exception {
        when(aiBookingAssistantService.chat(any()))
                .thenReturn(PublicAssistantResponse.builder().message("legacy chat").build());

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"Can I book a visit?"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("legacy chat"));

        verify(aiBookingAssistantService).chat(any());
    }

    @Test
    void legacySelectSlotRouteDelegatesToBookingAssistantService() throws Exception {
        when(aiBookingAssistantService.selectSlot(any()))
                .thenReturn(PublicAssistantResponse.builder().message("legacy slot").build());

        mockMvc.perform(post("/api/ai/booking/select-slot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slotId":"slot-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("legacy slot"));

        verify(aiBookingAssistantService).selectSlot(any());
    }
}
