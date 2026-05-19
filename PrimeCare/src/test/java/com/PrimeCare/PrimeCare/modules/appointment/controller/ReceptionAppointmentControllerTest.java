package com.PrimeCare.PrimeCare.modules.appointment.controller;

import com.PrimeCare.PrimeCare.modules.appointment.dto.response.AppointmentAdminResponse;
import com.PrimeCare.PrimeCare.modules.appointment.service.AppointmentAdminService;
import com.PrimeCare.PrimeCare.modules.appointment.service.AppointmentReceptionService;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReceptionAppointmentControllerTest {

    @Mock
    private AppointmentReceptionService appointmentReceptionService;
    @Mock
    private AppointmentAdminService appointmentAdminService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new ReceptionAppointmentController(appointmentReceptionService, appointmentAdminService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setValidator(validator)
                .build();
    }

    @Test
    void qrCheckInUsesExistingQrCheckInBusinessLogic() throws Exception {
        when(appointmentAdminService.checkInByQrToken("qr-token", 10L))
                .thenReturn(AppointmentAdminResponse.builder()
                        .id(1L)
                        .status(AppointmentStatus.CHECKED_IN)
                        .build());

        mockMvc.perform(post("/api/reception/appointments/check-in/qr")
                        .principal(new TestingAuthenticationToken("10", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"qrToken":"qr-token"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CHECKED_IN"));

        verify(appointmentAdminService).checkInByQrToken("qr-token", 10L);
    }

    @Test
    void queueWithoutTriagePriorityDelegatesNullPriorityFilter() throws Exception {
        mockMvc.perform(get("/api/reception/appointments/queue")
                        .principal(new TestingAuthenticationToken("10", null)))
                .andExpect(status().isOk());

        verify(appointmentReceptionService).queue(
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                any(Pageable.class)
        );
    }

    @Test
    void queueWithTriagePriorityDelegatesPriorityFilter() throws Exception {
        mockMvc.perform(get("/api/reception/appointments/queue")
                        .principal(new TestingAuthenticationToken("10", null))
                        .param("triagePriority", "P1"))
                .andExpect(status().isOk());

        verify(appointmentReceptionService).queue(
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq("P1"),
                isNull(),
                isNull(),
                any(Pageable.class)
        );
    }
}
