package com.PrimeCare.PrimeCare.modules.audit.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AuditLogResponse {
    private Long id;
    private Long actorId;
    private String actorName;
    private String actorRole;
    private String action;
    private String entity;
    private Long entityId;
    private String beforeJson;
    private String afterJson;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime createdAt;
}
