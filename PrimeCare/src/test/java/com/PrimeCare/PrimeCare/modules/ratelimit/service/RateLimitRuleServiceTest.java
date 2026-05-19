package com.PrimeCare.PrimeCare.modules.ratelimit.service;

import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.ratelimit.dto.request.CreateRateLimitRuleRequest;
import com.PrimeCare.PrimeCare.modules.ratelimit.dto.request.UpdateRateLimitRuleRequest;
import com.PrimeCare.PrimeCare.modules.ratelimit.entity.RateLimitRuleEntity;
import com.PrimeCare.PrimeCare.modules.ratelimit.repository.RateLimitRuleRepository;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import com.PrimeCare.PrimeCare.shared.enums.UserStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitRuleServiceTest {

    @Mock
    private RateLimitRuleRepository repository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private RateLimitRuleProvider provider;

    private RateLimitRuleService service;
    private User actor;

    @BeforeEach
    void setUp() {
        service = new RateLimitRuleService(repository, userRepository, auditLogService, provider);
        actor = User.builder()
                .id(99L)
                .email("sysadmin@primecare.vn")
                .role(UserRole.SYSTEM_ADMIN)
                .status(UserStatus.ACTIVE)
                .passwordHash("hash")
                .build();
    }

    @Test
    void createValidRuleSetsDefaultsAuditsAndRefreshesCache() {
        when(userRepository.findById(99L)).thenReturn(Optional.of(actor));
        when(repository.existsByCode("PUBLIC_STATUS_LOOKUP")).thenReturn(false);
        when(repository.existsByHttpMethodAndPathPattern("GET", "/api/public/status")).thenReturn(false);
        when(repository.save(any(RateLimitRuleEntity.class))).thenAnswer(invocation -> {
            RateLimitRuleEntity entity = invocation.getArgument(0);
            entity.setId(21L);
            return entity;
        });

        var response = service.create(createRequest(), 99L);

        assertThat(response.getId()).isEqualTo(21L);
        assertThat(response.getCode()).isEqualTo("PUBLIC_STATUS_LOOKUP");
        assertThat(response.getPathPattern()).isEqualTo("/api/public/status");
        assertThat(response.getDefaultLimitCount()).isEqualTo(50L);
        assertThat(response.getDefaultWindowSeconds()).isEqualTo(60L);
        assertThat(response.getDefaultBucketSeconds()).isEqualTo(6L);
        assertThat(response.isDefaultEnabled()).isTrue();
        verify(auditLogService).log(eq(actor), eq("CREATE_RATE_LIMIT_RULE"), eq("RATE_LIMIT_RULE"), eq(21L), eq(null), any());
        verify(provider).refreshCache();
    }

    @Test
    void createRejectsDuplicateCode() {
        when(repository.existsByCode("PUBLIC_STATUS_LOOKUP")).thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest(), 99L))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR)
                );

        verifyNoInteractions(auditLogService, provider, userRepository);
    }

    @Test
    void createRejectsDuplicateMethodAndPathPattern() {
        when(repository.existsByCode("PUBLIC_STATUS_LOOKUP")).thenReturn(false);
        when(repository.existsByHttpMethodAndPathPattern("GET", "/api/public/status")).thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest(), 99L))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR)
                );

        verifyNoInteractions(auditLogService, provider, userRepository);
    }

    @Test
    void createRejectsBroadPathPattern() {
        CreateRateLimitRuleRequest request = createRequest();
        request.setPathPattern("/api/**");

        assertThatThrownBy(() -> service.create(request, 99L))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR)
                );

        verifyNoInteractions(repository, userRepository, auditLogService, provider);
    }

    @Test
    void createRejectsPathOutsideApiRoutes() {
        CreateRateLimitRuleRequest request = createRequest();
        request.setPathPattern("/ws/notifications");

        assertThatThrownBy(() -> service.create(request, 99L))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR)
                );

        verifyNoInteractions(repository, userRepository, auditLogService, provider);
    }

    @Test
    void createRejectsInvalidCodeAndEventType() {
        CreateRateLimitRuleRequest invalidCode = createRequest();
        invalidCode.setCode("public_status_lookup");

        assertThatThrownBy(() -> service.create(invalidCode, 99L))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR)
                );

        CreateRateLimitRuleRequest invalidEventType = createRequest();
        invalidEventType.setEventType("PUBLIC-STATUS");

        assertThatThrownBy(() -> service.create(invalidEventType, 99L))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR)
                );

        verifyNoInteractions(repository, userRepository, auditLogService, provider);
    }

    @Test
    void updateReloadsCacheAndAuditsSuccessfulChange() {
        RateLimitRuleEntity rule = rule();
        rule.setEnabled(false);
        when(repository.findById(1L)).thenReturn(Optional.of(rule));
        when(userRepository.findById(99L)).thenReturn(Optional.of(actor));
        when(repository.save(any(RateLimitRuleEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateRateLimitRuleRequest request = new UpdateRateLimitRuleRequest();
        request.setLimitCount(8L);
        request.setBucketSeconds(10L);
        request.setDescription(" Updated description ");

        var response = service.update(1L, request, 99L);

        assertThat(response.getLimitCount()).isEqualTo(8L);
        assertThat(response.getBucketSeconds()).isEqualTo(10L);
        assertThat(response.getDescription()).isEqualTo("Updated description");
        assertThat(response.isEnabled()).isFalse();
        assertThat(rule.isEnabled()).isFalse();

        ArgumentCaptor<Map<String, Object>> before = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, Object>> after = ArgumentCaptor.forClass(Map.class);
        verify(auditLogService).log(eq(actor), eq("UPDATE_RATE_LIMIT_RULE"), eq("RATE_LIMIT_RULE"), eq(1L), before.capture(), after.capture());
        assertThat(before.getValue()).containsEntry("enabled", false);
        assertThat(after.getValue()).containsEntry("enabled", false);
        verify(provider).refreshCache();
    }

    @Test
    void updateRejectsInvalidBucketWithoutAuditOrCacheRefresh() {
        RateLimitRuleEntity rule = rule();
        when(repository.findById(1L)).thenReturn(Optional.of(rule));
        when(userRepository.findById(99L)).thenReturn(Optional.of(actor));

        UpdateRateLimitRuleRequest request = new UpdateRateLimitRuleRequest();
        request.setWindowSeconds(5L);
        request.setBucketSeconds(6L);

        assertThatThrownBy(() -> service.update(1L, request, 99L))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR)
                );

        verifyNoInteractions(auditLogService, provider);
    }

    @Test
    void updateRejectsTooLongDescriptionWithoutAuditOrCacheRefresh() {
        RateLimitRuleEntity rule = rule();
        when(repository.findById(1L)).thenReturn(Optional.of(rule));
        when(userRepository.findById(99L)).thenReturn(Optional.of(actor));

        UpdateRateLimitRuleRequest request = new UpdateRateLimitRuleRequest();
        request.setDescription("a".repeat(1001));

        assertThatThrownBy(() -> service.update(1L, request, 99L))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR)
                );

        verifyNoInteractions(auditLogService, provider);
        verify(repository).findById(1L);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void updateStillCannotModifyProtectedFields() {
        RateLimitRuleEntity rule = rule();
        when(repository.findById(1L)).thenReturn(Optional.of(rule));
        when(userRepository.findById(99L)).thenReturn(Optional.of(actor));

        UpdateRateLimitRuleRequest request = new UpdateRateLimitRuleRequest();
        request.setUnknownField("pathPattern", "/api/public/unsafe");

        assertThatThrownBy(() -> service.update(1L, request, 99L))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR)
                );

        verifyNoInteractions(auditLogService, provider);
        verify(repository).findById(1L);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void updateRejectsEnabledFieldWithoutAuditOrCacheRefresh() {
        RateLimitRuleEntity rule = rule();
        when(repository.findById(1L)).thenReturn(Optional.of(rule));
        when(userRepository.findById(99L)).thenReturn(Optional.of(actor));

        UpdateRateLimitRuleRequest request = new UpdateRateLimitRuleRequest();
        request.setUnknownField("enabled", false);

        assertThatThrownBy(() -> service.update(1L, request, 99L))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR)
                );

        assertThat(rule.isEnabled()).isTrue();
        verifyNoInteractions(auditLogService, provider);
        verify(repository).findById(1L);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void enableDisableAndResetAuditSuccessfulChanges() {
        when(userRepository.findById(99L)).thenReturn(Optional.of(actor));
        when(repository.save(any(RateLimitRuleEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RateLimitRuleEntity disabled = rule();
        disabled.setEnabled(false);
        when(repository.findById(1L)).thenReturn(Optional.of(disabled));
        service.enable(1L, 99L);
        verify(auditLogService).log(eq(actor), eq("ENABLE_RATE_LIMIT_RULE"), eq("RATE_LIMIT_RULE"), eq(1L), any(), any());
        verify(provider).refreshCache();

        clearInvocations(auditLogService, provider, repository, userRepository);
        when(userRepository.findById(99L)).thenReturn(Optional.of(actor));
        RateLimitRuleEntity enabled = rule();
        when(repository.findById(1L)).thenReturn(Optional.of(enabled));
        when(repository.save(any(RateLimitRuleEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service.disable(1L, 99L);
        verify(auditLogService).log(eq(actor), eq("DISABLE_RATE_LIMIT_RULE"), eq("RATE_LIMIT_RULE"), eq(1L), any(), any());
        verify(provider).refreshCache();

        clearInvocations(auditLogService, provider, repository, userRepository);
        when(userRepository.findById(99L)).thenReturn(Optional.of(actor));
        RateLimitRuleEntity changed = rule();
        changed.setLimitCount(99L);
        changed.setWindowSeconds(120L);
        changed.setBucketSeconds(12L);
        changed.setEnabled(false);
        when(repository.findById(1L)).thenReturn(Optional.of(changed));
        when(repository.save(any(RateLimitRuleEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service.resetDefaults(1L, 99L);
        verify(auditLogService).log(eq(actor), eq("RESET_RATE_LIMIT_RULE"), eq("RATE_LIMIT_RULE"), eq(1L), any(), any());
        verify(provider).refreshCache();
    }

    private RateLimitRuleEntity rule() {
        return RateLimitRuleEntity.builder()
                .id(1L)
                .code("AUTH_LOGIN")
                .name("Authentication login")
                .description("Rate limit for public login attempts.")
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
                .build();
    }

    private CreateRateLimitRuleRequest createRequest() {
        CreateRateLimitRuleRequest request = new CreateRateLimitRuleRequest();
        request.setCode("PUBLIC_STATUS_LOOKUP");
        request.setName("Public status lookup");
        request.setDescription("Status lookup throttle");
        request.setPathPattern("/api/public/status");
        request.setHttpMethod("GET");
        request.setEventType("PUBLIC_STATUS_LOOKUP");
        request.setLimitCount(50L);
        request.setWindowSeconds(60L);
        request.setBucketSeconds(6L);
        request.setEnabled(true);
        request.setPriority(500);
        return request;
    }
}
