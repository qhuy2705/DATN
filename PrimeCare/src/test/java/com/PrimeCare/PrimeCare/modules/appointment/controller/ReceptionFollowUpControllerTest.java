package com.PrimeCare.PrimeCare.modules.appointment.controller;

import com.PrimeCare.PrimeCare.modules.appointment.dto.response.FollowUpQueueItemResponse;
import com.PrimeCare.PrimeCare.modules.appointment.service.AppointmentAdminService;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentFollowUpType;
import com.PrimeCare.PrimeCare.shared.enums.FollowUpQueueCategory;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import com.PrimeCare.PrimeCare.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReceptionFollowUpControllerTest {

    @Mock
    private AppointmentAdminService appointmentAdminService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReceptionFollowUpController(appointmentAdminService))
                                 .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                                 .setControllerAdvice(new GlobalExceptionHandler())
                                 .build();
    }

    @Test
    void queueAcceptsCategoryAndKeepsFollowUpTypeBackwardCompatibility() throws Exception {
        when(appointmentAdminService.getFollowUpQueue(any(), any(), eq("patient"), any(Pageable.class)))
                .thenReturn(PageResponse.<FollowUpQueueItemResponse>builder()
                                        .items(List.of())
                                        .meta(PageResponse.Meta.builder().page(0).size(10).build())
                                        .build());

        mockMvc.perform(get("/api/reception/follow-up/queue")
                        .param("category", "CONTACT_REQUEST")
                        .param("followUpType", "PATIENT_CONTACT_REQUESTED")
                        .param("q", "patient"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<FollowUpQueueCategory> categoryCaptor = ArgumentCaptor.forClass(FollowUpQueueCategory.class);
        ArgumentCaptor<AppointmentFollowUpType> typeCaptor = ArgumentCaptor.forClass(AppointmentFollowUpType.class);
        verify(appointmentAdminService).getFollowUpQueue(
                categoryCaptor.capture(),
                typeCaptor.capture(),
                eq("patient"),
                any(Pageable.class)
        );
        assertThat(categoryCaptor.getValue()).isEqualTo(FollowUpQueueCategory.CONTACT_REQUEST);
        assertThat(typeCaptor.getValue()).isEqualTo(AppointmentFollowUpType.PATIENT_CONTACT_REQUESTED);
    }

    @Test
    void invalidCategoryReturnsBadRequestInsteadOfServerError() throws Exception {
        mockMvc.perform(get("/api/reception/follow-up/queue")
                        .param("category", "BROKEN"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.success").value(false))
               .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
               .andExpect(jsonPath("$.message").value("Danh mục follow-up không hợp lệ. Giá trị hợp lệ: ALL, NO_SHOW, DOCTOR_CANCELLATION, CONTACT_REQUEST"));
    }

    @Test
    void invalidFollowUpTypeReturnsBadRequestInsteadOfServerError() throws Exception {
        mockMvc.perform(get("/api/reception/follow-up/queue")
                        .param("followUpType", "BROKEN"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.success").value(false))
               .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void resolveDoesNotAutoClaimWhenClaimOwnershipIsMissing() throws Exception {
        when(appointmentAdminService.resolveFollowUpAsClosed(1L, 10L, "done"))
                .thenThrow(new ApiException(ErrorCode.APPOINTMENT_CLAIM_REQUIRED));

        mockMvc.perform(post("/api/reception/follow-up/1/resolve")
                        .principal(new TestingAuthenticationToken("10", null))
                        .contentType("application/json")
                        .content("""
                                {"note":"done"}
                                """))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.success").value(false))
               .andExpect(jsonPath("$.code").value("APPOINTMENT_CLAIM_REQUIRED"));

        verify(appointmentAdminService, never()).claim(any(), any());
    }
}
