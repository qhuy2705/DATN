package com.PrimeCare.PrimeCare.modules.ratelimit.service;

import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.ratelimit.dto.request.CreateRateLimitRuleRequest;
import com.PrimeCare.PrimeCare.modules.ratelimit.dto.request.UpdateRateLimitRuleRequest;
import com.PrimeCare.PrimeCare.modules.ratelimit.dto.response.RateLimitRuleResponse;
import com.PrimeCare.PrimeCare.modules.ratelimit.entity.RateLimitRuleEntity;
import com.PrimeCare.PrimeCare.modules.ratelimit.repository.RateLimitRuleRepository;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import com.PrimeCare.PrimeCare.shared.utils.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class RateLimitRuleService {

    private static final long MAX_LIMIT_COUNT = 100_000L;
    private static final long MAX_WINDOW_SECONDS = 86_400L;
    private static final int MAX_DESCRIPTION_LENGTH = 1_000;
    private static final Pattern UPPER_SNAKE_PATTERN = Pattern.compile("^[A-Z0-9_]+$");
    private static final Set<String> ALLOWED_HTTP_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");
    private static final Set<String> BROAD_PATH_PATTERNS = Set.of(
            "/",
            "/**",
            "/api",
            "/api/",
            "/api/**",
            "/actuator/**",
            "/ws/**"
    );
    private static final String AUDIT_ENTITY = "RATE_LIMIT_RULE";

    private final RateLimitRuleRepository rateLimitRuleRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final RateLimitRuleProvider rateLimitRuleProvider;

    @Transactional(readOnly = true)
    public List<RateLimitRuleResponse> list() {
        return rateLimitRuleRepository.findAllByOrderByPriorityAscIdAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RateLimitRuleResponse detail(Long id) {
        return toResponse(getRule(id));
    }

    @Transactional
    public RateLimitRuleResponse create(CreateRateLimitRuleRequest request, Long actorUserId) {
        CreateRuleValues values = validateCreateRequest(request);

        if (rateLimitRuleRepository.existsByCode(values.code())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Mã rate limit rule đã tồn tại.");
        }
        if (rateLimitRuleRepository.existsByHttpMethodAndPathPattern(values.httpMethod(), values.pathPattern())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Rate limit rule cho phương thức và path này đã tồn tại.");
        }

        User actor = getActor(actorUserId);

        RateLimitRuleEntity rule = RateLimitRuleEntity.builder()
                .code(values.code())
                .name(values.name())
                .description(values.description())
                .pathPattern(values.pathPattern())
                .httpMethod(values.httpMethod())
                .eventType(values.eventType())
                .limitCount(values.limitCount())
                .windowSeconds(values.windowSeconds())
                .bucketSeconds(values.bucketSeconds())
                .enabled(values.enabled())
                .priority(values.priority())
                .defaultLimitCount(values.limitCount())
                .defaultWindowSeconds(values.windowSeconds())
                .defaultBucketSeconds(values.bucketSeconds())
                .defaultEnabled(values.enabled())
                .updatedBy(actor)
                .build();

        RateLimitRuleEntity saved = rateLimitRuleRepository.save(rule);
        auditLogService.log(actor, "CREATE_RATE_LIMIT_RULE", AUDIT_ENTITY, saved.getId(), null, auditSnapshot(saved));
        refreshCacheAfterCommit();
        return toResponse(saved);
    }

    @Transactional
    public RateLimitRuleResponse update(Long id, UpdateRateLimitRuleRequest request, Long actorUserId) {
        RateLimitRuleEntity rule = getRule(id);
        User actor = getActor(actorUserId);
        Map<String, Object> before = auditSnapshot(rule);

        applyUpdate(rule, request);
        validate(rule.getLimitCount(), rule.getWindowSeconds(), rule.getBucketSeconds());
        rule.setUpdatedBy(actor);

        RateLimitRuleEntity saved = rateLimitRuleRepository.save(rule);
        Map<String, Object> after = auditSnapshot(saved);
        if (!Objects.equals(before, after)) {
            auditLogService.log(actor, "UPDATE_RATE_LIMIT_RULE", AUDIT_ENTITY, saved.getId(), before, after);
        }
        refreshCacheAfterCommit();
        return toResponse(saved);
    }

    @Transactional
    public RateLimitRuleResponse enable(Long id, Long actorUserId) {
        return setEnabled(id, true, actorUserId, "ENABLE_RATE_LIMIT_RULE");
    }

    @Transactional
    public RateLimitRuleResponse disable(Long id, Long actorUserId) {
        return setEnabled(id, false, actorUserId, "DISABLE_RATE_LIMIT_RULE");
    }

    @Transactional
    public RateLimitRuleResponse resetDefaults(Long id, Long actorUserId) {
        RateLimitRuleEntity rule = getRule(id);
        User actor = getActor(actorUserId);
        Map<String, Object> before = auditSnapshot(rule);

        rule.setLimitCount(rule.getDefaultLimitCount());
        rule.setWindowSeconds(rule.getDefaultWindowSeconds());
        rule.setBucketSeconds(rule.getDefaultBucketSeconds());
        rule.setEnabled(rule.isDefaultEnabled());
        validate(rule.getLimitCount(), rule.getWindowSeconds(), rule.getBucketSeconds());
        rule.setUpdatedBy(actor);

        RateLimitRuleEntity saved = rateLimitRuleRepository.save(rule);
        Map<String, Object> after = auditSnapshot(saved);
        if (!Objects.equals(before, after)) {
            auditLogService.log(actor, "RESET_RATE_LIMIT_RULE", AUDIT_ENTITY, saved.getId(), before, after);
        }
        refreshCacheAfterCommit();
        return toResponse(saved);
    }

    private RateLimitRuleResponse setEnabled(Long id, boolean enabled, Long actorUserId, String auditAction) {
        RateLimitRuleEntity rule = getRule(id);
        User actor = getActor(actorUserId);
        Map<String, Object> before = auditSnapshot(rule);

        rule.setEnabled(enabled);
        rule.setUpdatedBy(actor);

        RateLimitRuleEntity saved = rateLimitRuleRepository.save(rule);
        Map<String, Object> after = auditSnapshot(saved);
        if (!Objects.equals(before, after)) {
            auditLogService.log(actor, auditAction, AUDIT_ENTITY, saved.getId(), before, after);
        }
        refreshCacheAfterCommit();
        return toResponse(saved);
    }

    private void applyUpdate(RateLimitRuleEntity rule, UpdateRateLimitRuleRequest request) {
        if (request == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Dữ liệu cập nhật rule rate limit là bắt buộc.");
        }
        if (!request.getUnknownFields().isEmpty()) {
            throw new ApiException(
                    ErrorCode.VALIDATION_ERROR,
                    "Chỉ được cập nhật limitCount, windowSeconds, bucketSeconds và description."
            );
        }
        if (request.isLimitCountPresent()) {
            if (request.getLimitCount() == null) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "limitCount là bắt buộc khi cập nhật.");
            }
            rule.setLimitCount(request.getLimitCount());
        }
        if (request.isWindowSecondsPresent()) {
            if (request.getWindowSeconds() == null) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "windowSeconds là bắt buộc khi cập nhật.");
            }
            rule.setWindowSeconds(request.getWindowSeconds());
        }
        if (request.isBucketSecondsPresent()) {
            if (request.getBucketSeconds() == null) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "bucketSeconds là bắt buộc khi cập nhật.");
            }
            rule.setBucketSeconds(request.getBucketSeconds());
        }
        if (request.isDescriptionPresent()) {
            validateDescription(request.getDescription());
            rule.setDescription(StringUtil.trimToNull(request.getDescription()));
        }
    }

    private CreateRuleValues validateCreateRequest(CreateRateLimitRuleRequest request) {
        if (request == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Dữ liệu tạo rate limit rule là bắt buộc.");
        }

        String code = requireStrictToken(request.getCode(), "code", 128);
        if (!UPPER_SNAKE_PATTERN.matcher(code).matches()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "code chỉ được chứa A-Z, 0-9 và dấu gạch dưới.");
        }

        String name = requireText(request.getName(), "name", 255);
        String description = validateDescription(request.getDescription());
        String pathPattern = validatePathPattern(request.getPathPattern());

        String httpMethod = requireText(request.getHttpMethod(), "httpMethod", 16).toUpperCase(Locale.ROOT);
        if (!ALLOWED_HTTP_METHODS.contains(httpMethod)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "httpMethod không hợp lệ.");
        }

        String eventType = requireStrictToken(request.getEventType(), "eventType", 64);
        if (!UPPER_SNAKE_PATTERN.matcher(eventType).matches()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "eventType chỉ được chứa A-Z, 0-9 và dấu gạch dưới.");
        }

        Boolean enabled = request.getEnabled();
        if (enabled == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "enabled phải là true hoặc false.");
        }

        Integer priority = request.getPriority();
        if (priority == null || priority < 1 || priority > 100_000) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "priority phải từ 1 đến 100000.");
        }

        validate(request.getLimitCount(), request.getWindowSeconds(), request.getBucketSeconds());

        return new CreateRuleValues(
                code,
                name,
                description,
                pathPattern,
                httpMethod,
                eventType,
                request.getLimitCount(),
                request.getWindowSeconds(),
                request.getBucketSeconds(),
                enabled,
                priority
        );
    }

    private String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, field + " là bắt buộc.");
        }
        if (value.length() > maxLength) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, field + " không được vượt quá " + maxLength + " ký tự.");
        }
        return value.trim();
    }

    private String requireStrictToken(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, field + " là bắt buộc.");
        }
        if (value.length() > maxLength) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, field + " không được vượt quá " + maxLength + " ký tự.");
        }
        if (!value.equals(value.trim())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, field + " không được chứa khoảng trắng.");
        }
        return value;
    }

    private String validateDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "description không được vượt quá 1000 ký tự.");
        }
        return description.trim();
    }

    private String validatePathPattern(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "pathPattern là bắt buộc.");
        }
        if (value.length() > 255) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "pathPattern không được vượt quá 255 ký tự.");
        }
        if (containsWhitespace(value)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "pathPattern không được chứa khoảng trắng.");
        }
        if (BROAD_PATH_PATTERNS.contains(value)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "pathPattern quá rộng.");
        }
        if (!value.startsWith("/api/")) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "pathPattern phải bắt đầu bằng /api/.");
        }
        if (value.contains("*")) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "pathPattern không được chứa wildcard.");
        }
        return value;
    }

    private boolean containsWhitespace(String value) {
        return value.chars().anyMatch(Character::isWhitespace);
    }

    private void validate(Long limitCount, Long windowSeconds, Long bucketSeconds) {
        if (limitCount == null || limitCount < 1 || limitCount > MAX_LIMIT_COUNT) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "limitCount phải từ 1 đến 100000.");
        }
        if (windowSeconds == null || windowSeconds < 1 || windowSeconds > MAX_WINDOW_SECONDS) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "windowSeconds phải từ 1 đến 86400.");
        }
        if (bucketSeconds == null || bucketSeconds < 1) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "bucketSeconds phải lớn hơn hoặc bằng 1.");
        }
        if (bucketSeconds > windowSeconds) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "bucketSeconds không được lớn hơn windowSeconds.");
        }
    }

    private RateLimitRuleEntity getRule(Long id) {
        if (id == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Rate limit rule là bắt buộc.");
        }
        return rateLimitRuleRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy rate limit rule."));
    }

    private User getActor(Long actorUserId) {
        if (actorUserId == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        return userRepository.findById(actorUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
    }

    private void refreshCacheAfterCommit() {
        Runnable refresh = rateLimitRuleProvider::refreshCache;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            refresh.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                refresh.run();
            }
        });
    }

    private RateLimitRuleResponse toResponse(RateLimitRuleEntity rule) {
        return RateLimitRuleResponse.builder()
                .id(rule.getId())
                .code(rule.getCode())
                .name(rule.getName())
                .description(rule.getDescription())
                .pathPattern(rule.getPathPattern())
                .httpMethod(rule.getHttpMethod())
                .eventType(rule.getEventType())
                .limitCount(rule.getLimitCount())
                .windowSeconds(rule.getWindowSeconds())
                .bucketSeconds(rule.getBucketSeconds())
                .enabled(rule.isEnabled())
                .priority(rule.getPriority())
                .defaultLimitCount(rule.getDefaultLimitCount())
                .defaultWindowSeconds(rule.getDefaultWindowSeconds())
                .defaultBucketSeconds(rule.getDefaultBucketSeconds())
                .defaultEnabled(rule.isDefaultEnabled())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .updatedBy(rule.getUpdatedBy() != null ? rule.getUpdatedBy().getId() : null)
                .build();
    }

    private Map<String, Object> auditSnapshot(RateLimitRuleEntity rule) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", rule.getId());
        snapshot.put("code", rule.getCode());
        snapshot.put("name", rule.getName());
        snapshot.put("description", rule.getDescription());
        snapshot.put("pathPattern", rule.getPathPattern());
        snapshot.put("httpMethod", rule.getHttpMethod());
        snapshot.put("eventType", rule.getEventType());
        snapshot.put("limitCount", rule.getLimitCount());
        snapshot.put("windowSeconds", rule.getWindowSeconds());
        snapshot.put("bucketSeconds", rule.getBucketSeconds());
        snapshot.put("enabled", rule.isEnabled());
        snapshot.put("priority", rule.getPriority());
        return snapshot;
    }

    private record CreateRuleValues(
            String code,
            String name,
            String description,
            String pathPattern,
            String httpMethod,
            String eventType,
            Long limitCount,
            Long windowSeconds,
            Long bucketSeconds,
            boolean enabled,
            Integer priority
    ) {
    }
}
