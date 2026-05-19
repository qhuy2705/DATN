package com.PrimeCare.PrimeCare.modules.audit.messaging;

import com.PrimeCare.PrimeCare.config.RabbitMqConfig;
import com.PrimeCare.PrimeCare.modules.audit.dto.message.AuditEventMessage;
import com.PrimeCare.PrimeCare.modules.audit.entity.AuditLog;
import com.PrimeCare.PrimeCare.modules.audit.repository.AuditLogRepository;
import com.PrimeCare.PrimeCare.modules.audit.service.AuditRedactionService;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuditEventConsumer {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final AuditRedactionService auditRedactionService;

    @RabbitListener(queues = RabbitMqConfig.AUDIT_QUEUE)
    public void consume(AuditEventMessage message) {
        if (message == null || message.getEventId() == null || message.getEventId().isBlank()) {
            return;
        }
        if (auditLogRepository.existsByEventId(message.getEventId())) {
            return;
        }

        User actor = message.getActorId() != null
                ? userRepository.findById(message.getActorId()).orElse(null)
                : null;

        AuditLog auditLog = AuditLog.builder()
                .eventId(message.getEventId())
                .actor(actor)
                .actorName(message.getActorName())
                .actorEmail(message.getActorEmail())
                .actorRole(message.getActorRole())
                .action(message.getAction())
                .entity(message.getEntity())
                .entityId(message.getEntityId())
                .beforeJson(auditRedactionService.redactJson(message.getBeforeJson()))
                .afterJson(auditRedactionService.redactJson(message.getAfterJson()))
                .ipAddress(message.getIpAddress())
                .userAgent(message.getUserAgent())
                .createdAt(message.getCreatedAt())
                .build();

        try {
            auditLogRepository.saveAndFlush(auditLog);
        } catch (DataIntegrityViolationException ex) {
            if (auditLogRepository.existsByEventId(message.getEventId())) {
                return;
            }
            throw ex;
        }
    }
}
