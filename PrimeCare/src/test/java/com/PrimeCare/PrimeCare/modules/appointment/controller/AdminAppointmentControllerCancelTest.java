package com.PrimeCare.PrimeCare.modules.appointment.controller;

import com.PrimeCare.PrimeCare.modules.appointment.dto.response.AppointmentAdminResponse;
import com.PrimeCare.PrimeCare.modules.appointment.service.AppointmentAdminService;
import com.PrimeCare.PrimeCare.modules.appointment.service.AppointmentBookingRestrictionSummaryService;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminAppointmentControllerCancelTest {

    private AppointmentAdminService appointmentAdminService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        appointmentAdminService = mock(AppointmentAdminService.class);
        AppointmentBookingRestrictionSummaryService bookingRestrictionSummaryService =
                mock(AppointmentBookingRestrictionSummaryService.class);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminAppointmentController(appointmentAdminService, bookingRestrictionSummaryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void cancelWithoutBodyReturnsClearValidationError() throws Exception {
        mockMvc.perform(post("/api/admin/appointments/1/cancel")
                        .principal(staffAuthentication())
                        .contentType(MediaType.APPLICATION_JSON))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.success").value(false))
               .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
               .andExpect(jsonPath("$.message").value("Cancellation reason is required."));

        verify(appointmentAdminService, never()).cancel(
                anyLong(),
                anyLong(),
                any(),
                any(),
                anyBoolean(),
                anyBoolean(),
                any()
        );
    }

    @Test
    void cancelWithBlankReasonReturnsFieldValidationError() throws Exception {
        mockMvc.perform(post("/api/admin/appointments/1/cancel")
                        .principal(staffAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.success").value(false))
               .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
               .andExpect(jsonPath("$.details.fields.reason").value("Cancellation reason is required."));

        verify(appointmentAdminService, never()).cancel(
                anyLong(),
                anyLong(),
                any(),
                any(),
                anyBoolean(),
                anyBoolean(),
                any()
        );
    }

    @Test
    void cancelWithValidReasonTrimsAndDelegates() throws Exception {
        AppointmentAdminResponse response = AppointmentAdminResponse.builder()
                                                                   .id(1L)
                                                                   .status(AppointmentStatus.CANCELLED)
                                                                   .build();
        when(appointmentAdminService.cancel(
                eq(1L),
                eq(10L),
                eq("Patient request"),
                isNull(),
                eq(false),
                eq(false),
                isNull()
        )).thenReturn(response);

        mockMvc.perform(post("/api/admin/appointments/1/cancel")
                        .principal(staffAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\" Patient request \"}"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.success").value(true))
               .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        verify(appointmentAdminService).cancel(
                eq(1L),
                eq(10L),
                eq("Patient request"),
                isNull(),
                eq(false),
                eq(false),
                isNull()
        );
    }

    private TestingAuthenticationToken staffAuthentication() {
        return new TestingAuthenticationToken("10", "n/a", "ROLE_STAFF");
    }
}
