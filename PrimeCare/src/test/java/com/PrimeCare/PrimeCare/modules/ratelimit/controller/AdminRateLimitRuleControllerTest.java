package com.PrimeCare.PrimeCare.modules.ratelimit.controller;

import com.PrimeCare.PrimeCare.modules.ratelimit.dto.response.RateLimitRuleResponse;
import com.PrimeCare.PrimeCare.modules.ratelimit.service.RateLimitRuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminRateLimitRuleControllerTest {

    private RateLimitRuleService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(RateLimitRuleService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminRateLimitRuleController(service))
                .build();
    }

    @Test
    void controllerIsSystemAdminOnly() {
        PreAuthorize annotation = AdminRateLimitRuleController.class.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasRole('SYSTEM_ADMIN')");
    }

    @Test
    void endpointsExistForCreateListDetailUpdateEnableDisableAndReset() throws Exception {
        RateLimitRuleResponse response = response();
        when(service.create(any(), eq(99L))).thenReturn(response);
        when(service.list()).thenReturn(List.of(response));
        when(service.detail(1L)).thenReturn(response);
        when(service.update(eq(1L), any(), eq(99L))).thenReturn(response);
        when(service.enable(1L, 99L)).thenReturn(response);
        when(service.disable(1L, 99L)).thenReturn(response);
        when(service.resetDefaults(1L, 99L)).thenReturn(response);

        var auth = new TestingAuthenticationToken("99", null, "ROLE_SYSTEM_ADMIN");

        mockMvc.perform(post("/api/admin/rate-limit-rules")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"PUBLIC_STATUS_LOOKUP",
                                  "name":"Public status lookup",
                                  "description":"Status lookup throttle",
                                  "pathPattern":"/api/public/status",
                                  "httpMethod":"GET",
                                  "eventType":"PUBLIC_STATUS_LOOKUP",
                                  "limitCount":50,
                                  "windowSeconds":60,
                                  "bucketSeconds":6,
                                  "enabled":true,
                                  "priority":500
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("AUTH_LOGIN"));

        mockMvc.perform(get("/api/admin/rate-limit-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("AUTH_LOGIN"));

        mockMvc.perform(get("/api/admin/rate-limit-rules/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("AUTH_LOGIN"));

        mockMvc.perform(put("/api/admin/rate-limit-rules/1")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"limitCount\":8,\"windowSeconds\":300,\"bucketSeconds\":30,\"description\":\"login\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/rate-limit-rules/1/enable").principal(auth))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/rate-limit-rules/1/disable").principal(auth))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/rate-limit-rules/1/reset-defaults").principal(auth))
                .andExpect(status().isOk());

        verify(service).create(any(), eq(99L));
        verify(service).update(eq(1L), any(), eq(99L));
    }

    @Test
    void updateRejectsUneditableFields() throws Exception {
        var auth = new TestingAuthenticationToken("99", null, "ROLE_SYSTEM_ADMIN");

        mockMvc.perform(put("/api/admin/rate-limit-rules/1")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"NEW_CODE\",\"limitCount\":8}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateRejectsEnabledField() throws Exception {
        var auth = new TestingAuthenticationToken("99", null, "ROLE_SYSTEM_ADMIN");

        mockMvc.perform(put("/api/admin/rate-limit-rules/1")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"limitCount\":8}"))
                .andExpect(status().isBadRequest());
    }

    private RateLimitRuleResponse response() {
        return RateLimitRuleResponse.builder()
                .id(1L)
                .code("AUTH_LOGIN")
                .name("Authentication login")
                .description("login")
                .pathPattern("/api/auth/login")
                .httpMethod("POST")
                .eventType("LOGIN")
                .limitCount(5L)
                .windowSeconds(300L)
                .bucketSeconds(30L)
                .enabled(true)
                .priority(10)
                .defaultLimitCount(5L)
                .defaultWindowSeconds(300L)
                .defaultBucketSeconds(30L)
                .defaultEnabled(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .updatedBy(99L)
                .build();
    }
}
