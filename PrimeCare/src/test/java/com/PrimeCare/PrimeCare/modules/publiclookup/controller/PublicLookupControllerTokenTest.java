package com.PrimeCare.PrimeCare.modules.publiclookup.controller;

import com.PrimeCare.PrimeCare.modules.publiclookup.service.PublicAppointmentLookupService;
import com.PrimeCare.PrimeCare.modules.publiclookup.service.PublicResultLookupService;
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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicLookupControllerTokenTest {

    private PublicAppointmentLookupService appointmentLookupService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        appointmentLookupService = mock(PublicAppointmentLookupService.class);
        PublicResultLookupService resultLookupService = mock(PublicResultLookupService.class);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(new PublicLookupController(appointmentLookupService, resultLookupService))
                                 .setControllerAdvice(new GlobalExceptionHandler())
                                 .setValidator(validator)
                                 .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                                 .build();
    }

    @Test
    void cancelAppointmentWithoutLookupTokenReturnsPublicLookupBadRequest() throws Exception {
        mockMvc.perform(post("/api/public/lookup/appointments/APT001/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.success").value(false))
               .andExpect(jsonPath("$.status").value(400))
               .andExpect(jsonPath("$.code").value("PUBLIC_LOOKUP_TOKEN_INVALID"))
               .andExpect(jsonPath("$.message").value("Token tra cứu không hợp lệ."));

        verify(appointmentLookupService, never()).cancel(anyString(), anyString(), anyString());
    }
}
