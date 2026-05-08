package com.PrimeCare.PrimeCare.modules.audit.mapper;

import com.PrimeCare.PrimeCare.modules.audit.dto.response.AuditLogResponse;
import com.PrimeCare.PrimeCare.modules.audit.entity.AuditLog;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import org.springframework.stereotype.Component;

@Component
public class AuditLogMapper {

    public AuditLogResponse toResponse(AuditLog log) {
        return AuditLogResponse.builder()
                               .id(log.getId())
                               .actorId(log.getActor() != null ? log.getActor().getId() : null)
                               .actorName(resolveActorName(log.getActor()))
                               .actorRole(log.getActorRole())
                               .action(log.getAction())
                               .entity(log.getEntity())
                               .entityId(log.getEntityId())
                               .beforeJson(log.getBeforeJson())
                               .afterJson(log.getAfterJson())
                               .ipAddress(log.getIpAddress())
                               .userAgent(log.getUserAgent())
                               .createdAt(log.getCreatedAt())
                               .build();
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
}
