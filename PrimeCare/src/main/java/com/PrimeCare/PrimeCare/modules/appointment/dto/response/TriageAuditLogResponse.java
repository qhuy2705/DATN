package com.PrimeCare.PrimeCare.modules.appointment.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TriageAuditLogResponse {
    private Long id;
    private String actorType;
    private Long actorId;
    private String actorName;
    private String action;
    private String fromPriority;
    private String toPriority;
    private String reason;
    private String snapshotJson;
    private LocalDateTime createdAt;
}
