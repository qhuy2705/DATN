package com.PrimeCare.PrimeCare.modules.audit.service;

import com.PrimeCare.PrimeCare.modules.audit.dto.response.AuditLogResponse;
import com.PrimeCare.PrimeCare.modules.audit.entity.AuditLog;
import com.PrimeCare.PrimeCare.modules.audit.entity.AuditOutboxEvent;
import com.PrimeCare.PrimeCare.modules.audit.entity.AuditOutboxEventStatus;
import com.PrimeCare.PrimeCare.modules.audit.mapper.AuditLogMapper;
import com.PrimeCare.PrimeCare.modules.audit.repository.AuditLogRepository;
import com.PrimeCare.PrimeCare.modules.audit.repository.AuditOutboxEventRepository;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.config.PaginationConfig;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final AuditOutboxEventRepository auditOutboxEventRepository;
    private final AuditLogMapper auditLogMapper;
    private final AuditRedactionService auditRedactionService;
    private final UserRepository userRepository;

    @Transactional
    public void log(User actor, String action, String entity, Long entityId, Object before, Object after) {
        if (action == null || action.isBlank() || entity == null || entity.isBlank()) {
            return;
        }

        User effectiveActor = actor != null ? actor : resolveCurrentActor();

        AuditOutboxEvent event = AuditOutboxEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .actorId(effectiveActor != null ? effectiveActor.getId() : null)
                .actorName(resolveActorName(effectiveActor))
                .actorEmail(effectiveActor != null ? effectiveActor.getEmail() : null)
                .actorRole(effectiveActor != null && effectiveActor.getRole() != null ? effectiveActor.getRole().name() : null)
                .action(action.trim())
                .entity(entity.trim())
                .entityId(entityId)
                .beforeJson(auditRedactionService.writeRedactedJson(before))
                .afterJson(auditRedactionService.writeRedactedJson(after))
                .ipAddress(resolveIpAddress())
                .userAgent(resolveUserAgent())
                .status(AuditOutboxEventStatus.PENDING)
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .build();

        auditOutboxEventRepository.save(event);
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
            LocalDate date,
            String action,
            String entity,
            Long entityId,
            Long actorId,
            String actorRole,
            String q,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable
    ) {
        LocalDateTime start;
        LocalDateTime end;

        if (date != null) {
            start = date.atStartOfDay();
            end = date.atTime(LocalTime.MAX);
        } else {
            start = fromDate != null
                    ? fromDate.atStartOfDay()
                    : LocalDate.now().minusDays(30).atStartOfDay();
            end = toDate != null
                    ? toDate.atTime(LocalTime.MAX)
                    : LocalDateTime.now();
        }

        Pageable sortedPageable = withDefaultSort(pageable);
        Page<AuditLog> page = auditLogRepository.findAll(
                buildSpec(start, end, action, entity, entityId, actorId, actorRole, q),
                sortedPageable
        );

        return PageResponse.<AuditLogResponse>builder()
                           .items(page.getContent().stream().map(auditLogMapper::toResponse).toList())
                           .meta(PageResponse.Meta.builder()
                                                  .page(page.getNumber())
                                                  .size(page.getSize())
                                                  .totalItems(page.getTotalElements())
                                                  .totalPages(page.getTotalPages())
                                                  .hasNext(page.hasNext())
                                                  .hasPrev(page.hasPrevious())
                                                  .sort(sortedPageable.getSort().toString())
                                                  .build())
                           .build();
    }

    private Specification<AuditLog> buildSpec(
            LocalDateTime start,
            LocalDateTime end,
            String action,
            String entity,
            Long entityId,
            Long actorId,
            String actorRole,
            String q
    ) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.between(root.get("createdAt"), start, end));

            if (hasText(action)) {
                predicates.add(cb.equal(cb.lower(root.get("action")), action.trim().toLowerCase(Locale.ROOT)));
            }
            if (hasText(entity)) {
                predicates.add(cb.equal(cb.lower(root.get("entity")), entity.trim().toLowerCase(Locale.ROOT)));
            }
            if (entityId != null) {
                predicates.add(cb.equal(root.get("entityId"), entityId));
            }
            if (actorId != null) {
                predicates.add(cb.equal(root.get("actor").get("id"), actorId));
            }
            if (hasText(actorRole)) {
                predicates.add(cb.equal(cb.lower(root.get("actorRole")), actorRole.trim().toLowerCase(Locale.ROOT)));
            }
            if (hasText(q)) {
                String pattern = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("actorName")), pattern),
                        cb.like(cb.lower(root.get("actorEmail")), pattern),
                        cb.like(cb.lower(root.get("action")), pattern),
                        cb.like(cb.lower(root.get("entity")), pattern),
                        cb.like(root.get("entityId").as(String.class), pattern),
                        cb.like(cb.lower(root.get("ipAddress")), pattern)
                ));
            }

            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private Pageable withDefaultSort(Pageable pageable) {
        Sort sort = pageable != null && pageable.getSort().isSorted()
                ? pageable.getSort()
                : Sort.by(Sort.Direction.DESC, "createdAt");
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequest.of(0, PaginationConfig.DEFAULT_PAGE_SIZE, sort);
        }
        return PaginationConfig.withSort(pageable, sort);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String resolveActorName(User actor) {
        if (actor == null) {
            return null;
        }
        if (actor.getStaffProfile() != null && actor.getStaffProfile().getFullName() != null) {
            return actor.getStaffProfile().getFullName();
        }
        if (actor.getDoctorProfile() != null && actor.getDoctorProfile().getFullName() != null) {
            return actor.getDoctorProfile().getFullName();
        }
        return actor.getEmail();
    }

    private User resolveCurrentActor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        try {
            Long userId = Long.valueOf(authentication.getName());
            return userRepository.findById(userId).orElse(null);
        } catch (Exception ignored) {
            return null;
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
