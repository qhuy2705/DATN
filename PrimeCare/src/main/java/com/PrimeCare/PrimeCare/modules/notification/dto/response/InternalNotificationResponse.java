package com.PrimeCare.PrimeCare.modules.notification.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class InternalNotificationResponse {
    private Long id;
    private String title;
    private String message;
    private String type;
    private String severity;
    private String route;
    private String entityType;
    private Long entityId;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private Object metadata;
}
