package com.PrimeCare.PrimeCare.modules.notification.service;

import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.notification.dto.response.InternalNotificationResponse;
import com.PrimeCare.PrimeCare.modules.notification.dto.response.InternalNotificationUnreadCountResponse;
import com.PrimeCare.PrimeCare.modules.notification.entity.InternalNotification;
import com.PrimeCare.PrimeCare.modules.notification.repository.InternalNotificationRepository;
import com.PrimeCare.PrimeCare.modules.realtime.service.RealtimeEventPublisher;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import com.PrimeCare.PrimeCare.shared.enums.UserStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InternalNotificationService {

    public static final String SEVERITY_INFO = "INFO";
    public static final String SEVERITY_SUCCESS = "SUCCESS";
    public static final String SEVERITY_WARNING = "WARNING";
    public static final String SEVERITY_CRITICAL = "CRITICAL";

    private final InternalNotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PageResponse<InternalNotificationResponse> list(
            Long recipientUserId,
            Boolean unreadOnly,
            String type,
            String severity,
            Pageable pageable
    ) {
        Page<InternalNotification> page = notificationRepository.findForRecipient(
                recipientUserId,
                Boolean.TRUE.equals(unreadOnly),
                normalize(type),
                normalize(severity),
                LocalDateTime.now(),
                pageable
        );

        return PageResponse.<InternalNotificationResponse>builder()
                .items(page.getContent().stream().map(this::toResponse).toList())
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

    @Transactional(readOnly = true)
    public InternalNotificationUnreadCountResponse unreadCount(Long recipientUserId) {
        return InternalNotificationUnreadCountResponse.builder()
                .unreadCount(notificationRepository.countUnreadActive(recipientUserId, LocalDateTime.now()))
                .build();
    }

    @Transactional
    public InternalNotificationResponse markRead(Long notificationId, Long recipientUserId) {
        InternalNotification notification = notificationRepository
                .findByIdAndRecipientUser_Id(notificationId, recipientUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_NOTIFICATION_NOT_FOUND));

        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
            notification = notificationRepository.save(notification);
        }

        return toResponse(notification);
    }

    @Transactional
    public InternalNotificationUnreadCountResponse markAllRead(Long recipientUserId) {
        notificationRepository.markAllRead(recipientUserId, LocalDateTime.now());
        return unreadCount(recipientUserId);
    }

    @Transactional
    public InternalNotificationResponse notifyUser(
            Long recipientUserId,
            String type,
            String severity,
            String title,
            String message,
            String route,
            String entityType,
            Long entityId
    ) {
        return notifyUser(recipientUserId, type, severity, title, message, route, entityType, entityId, null, null);
    }

    @Transactional
    public InternalNotificationResponse notifyUser(
            Long recipientUserId,
            String type,
            String severity,
            String title,
            String message,
            String route,
            String entityType,
            Long entityId,
            Map<String, ?> metadata,
            LocalDateTime expiresAt
    ) {
        if (recipientUserId == null) {
            return null;
        }
        return userRepository.findById(recipientUserId)
                .map(user -> createAndPublish(user, type, severity, title, message, route, entityType, entityId, metadata, expiresAt))
                .orElse(null);
    }

    @Transactional
    public List<InternalNotificationResponse> notifyRole(
            UserRole role,
            String type,
            String severity,
            String title,
            String message,
            String route,
            String entityType,
            Long entityId
    ) {
        return notifyRole(role, type, severity, title, message, route, entityType, entityId, null, null);
    }

    @Transactional
    public List<InternalNotificationResponse> notifyRole(
            UserRole role,
            String type,
            String severity,
            String title,
            String message,
            String route,
            String entityType,
            Long entityId,
            Map<String, ?> metadata,
            LocalDateTime expiresAt
    ) {
        if (role == null) {
            return List.of();
        }

        return userRepository.findByRoleAndStatus(role, UserStatus.ACTIVE)
                .stream()
                .map(user -> createAndPublish(user, type, severity, title, message, route, entityType, entityId, metadata, expiresAt))
                .toList();
    }

    @Transactional
    public List<InternalNotificationResponse> notifyRoleOnce(
            UserRole role,
            String type,
            String severity,
            String title,
            String message,
            String route,
            String entityType,
            Long entityId,
            Map<String, ?> metadata
    ) {
        if (role == null) {
            return List.of();
        }

        return userRepository.findByRoleAndStatus(role, UserStatus.ACTIVE)
                .stream()
                .filter(user -> !notificationRepository.existsByRecipientUser_IdAndTypeAndEntityTypeAndEntityId(
                        user.getId(),
                        type,
                        entityType,
                        entityId
                ))
                .map(user -> createAndPublish(user, type, severity, title, message, route, entityType, entityId, metadata, null))
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<InternalNotificationResponse> notifyRoleOnceRequiresNew(
            UserRole role,
            String type,
            String severity,
            String title,
            String message,
            String route,
            String entityType,
            Long entityId,
            Map<String, ?> metadata
    ) {
        return notifyRoleOnce(role, type, severity, title, message, route, entityType, entityId, metadata);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<InternalNotificationResponse> notifyAdminRolesOnceRequiresNew(
            String type,
            String severity,
            String title,
            String message,
            String route,
            String entityType,
            Long entityId,
            Map<String, ?> metadata
    ) {
        List<InternalNotificationResponse> responses = new ArrayList<>();
        responses.addAll(notifyRoleOnce(
                UserRole.OPERATIONS_ADMIN,
                type,
                severity,
                title,
                message,
                route,
                entityType,
                entityId,
                metadata
        ));
        responses.addAll(notifyRoleOnce(
                UserRole.SYSTEM_ADMIN,
                type,
                severity,
                title,
                message,
                route,
                entityType,
                entityId,
                metadata
        ));
        return responses;
    }

    private InternalNotificationResponse createAndPublish(
            User recipient,
            String type,
            String severity,
            String title,
            String message,
            String route,
            String entityType,
            Long entityId,
            Map<String, ?> metadata,
            LocalDateTime expiresAt
    ) {
        InternalNotification notification = notificationRepository.saveAndFlush(
                InternalNotification.builder()
                        .recipientUser(recipient)
                        .type(defaultIfBlank(type, "GENERAL"))
                        .severity(defaultIfBlank(severity, SEVERITY_INFO))
                        .title(defaultIfBlank(title, "Thông báo"))
                        .message(defaultIfBlank(message, ""))
                        .route(normalize(route))
                        .entityType(normalize(entityType))
                        .entityId(entityId)
                        .metadataJson(toMetadataJson(metadata))
                        .expiresAt(expiresAt)
                        .build()
        );

        InternalNotificationResponse response = toResponse(notification);
        realtimeEventPublisher.publishInternalNotification(recipient.getId(), toRealtimePayload(response));
        return response;
    }

    private Map<String, Object> toRealtimePayload(InternalNotificationResponse response) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "INTERNAL_NOTIFICATION");
        payload.put("id", response.getId());
        payload.put("title", response.getTitle());
        payload.put("message", response.getMessage());
        payload.put("type", response.getType());
        payload.put("severity", response.getSeverity());
        payload.put("route", response.getRoute());
        payload.put("entityType", response.getEntityType());
        payload.put("entityId", response.getEntityId());
        payload.put("readAt", response.getReadAt() != null ? response.getReadAt().toString() : null);
        payload.put("createdAt", response.getCreatedAt() != null ? response.getCreatedAt().toString() : null);
        payload.put("metadata", response.getMetadata());
        return payload;
    }

    private InternalNotificationResponse toResponse(InternalNotification notification) {
        return InternalNotificationResponse.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .type(notification.getType())
                .severity(notification.getSeverity())
                .route(notification.getRoute())
                .entityType(notification.getEntityType())
                .entityId(notification.getEntityId())
                .readAt(notification.getReadAt())
                .createdAt(notification.getCreatedAt())
                .expiresAt(notification.getExpiresAt())
                .metadata(parseMetadata(notification.getMetadataJson()))
                .build();
    }

    private String toMetadataJson(Map<String, ?> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private Object parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(metadataJson, Object.class);
        } catch (JsonProcessingException ex) {
            return metadataJson;
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String defaultIfBlank(String value, String fallback) {
        String normalized = normalize(value);
        return normalized != null ? normalized : fallback;
    }
}
