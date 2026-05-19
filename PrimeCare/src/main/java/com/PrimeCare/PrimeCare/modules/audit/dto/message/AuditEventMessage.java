package com.PrimeCare.PrimeCare.modules.audit.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEventMessage {
    private String eventId;
    private String action;
    private String entity;
    private Long entityId;
    private Long actorId;
    private String actorName;
    private String actorEmail;
    private String actorRole;
    private String beforeJson;
    private String afterJson;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime createdAt;
}
