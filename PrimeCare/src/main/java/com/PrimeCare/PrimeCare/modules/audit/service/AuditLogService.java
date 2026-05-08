package com.PrimeCare.PrimeCare.modules.audit.service;

import com.PrimeCare.PrimeCare.modules.audit.dto.response.AuditLogResponse;
import com.PrimeCare.PrimeCare.modules.audit.entity.AuditLog;
import com.PrimeCare.PrimeCare.modules.audit.mapper.AuditLogMapper;
import com.PrimeCare.PrimeCare.modules.audit.repository.AuditLogRepository;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public void log(User actor, String action, String entity, Long entityId, Object before, Object after) {
        AuditLog auditLog = AuditLog.builder()
                                    .actor(actor)
                                    .actorRole(actor != null && actor.getRole() != null ? actor.getRole().name() : null)
                                    .action(action)
                                    .entity(entity)
                                    .entityId(entityId)
                                    .beforeJson(writeJson(before))
                                    .afterJson(writeJson(after))
                                    .ipAddress(resolveIpAddress())
                                    .userAgent(resolveUserAgent())
                                    .build();

        auditLogRepository.save(auditLog);
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> listEntityHistory(String entity, Long entityId) {
        if (entity == null || entity.isBlank() || entityId == null) {
            return List.of();
        }

        return auditLogRepository.findTop20ByEntityAndEntityIdOrderByCreatedAtDesc(entity.trim(), entityId)
                                 .stream()
                                 .map(auditLogMapper::toResponse)
                                 .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(
            String entity,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable
    ) {
        LocalDateTime start = fromDate != null
                ? fromDate.atStartOfDay()
                : LocalDate.now().minusDays(30).atStartOfDay();

        LocalDateTime end = toDate != null
                ? toDate.plusDays(1).atStartOfDay().minusNanos(1)
                : LocalDateTime.now();

        Page<AuditLog> page = (entity != null && !entity.isBlank())
                ? auditLogRepository.findByEntityContainingIgnoreCaseAndCreatedAtBetween(entity.trim(), start, end, pageable)
                : auditLogRepository.findByCreatedAtBetween(start, end, pageable);

        return PageResponse.<AuditLogResponse>builder()
                           .items(page.getContent().stream().map(auditLogMapper::toResponse).toList())
                           .meta(PageResponse.Meta.builder()
                                                  .page(page.getNumber())
                                                  .size(page.getSize())
                                                  .totalItems(page.getTotalElements())
                                                  .totalPages(page.getTotalPages())
                                                  .hasNext(page.hasNext())
                                                  .hasPrev(page.hasPrevious())
                                                  .sort(pageable.getSort().toString())
                                                  .build())
                           .build();
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private String resolveIpAddress() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }

    private String resolveUserAgent() {
        HttpServletRequest request = currentRequest();
        return request != null ? request.getHeader("User-Agent") : null;
    }

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }
}