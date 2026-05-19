package com.PrimeCare.PrimeCare.modules.audit.service;

import com.PrimeCare.PrimeCare.config.RabbitMqConfig;
import com.PrimeCare.PrimeCare.modules.audit.dto.message.AuditEventMessage;
import com.PrimeCare.PrimeCare.modules.audit.entity.AuditOutboxEvent;
import com.PrimeCare.PrimeCare.modules.audit.entity.AuditOutboxEventStatus;
import com.PrimeCare.PrimeCare.modules.audit.repository.AuditOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditOutboxPublisher {

    private static final int BATCH_SIZE = 50;
    private static final int MAX_ERROR_LENGTH = 4000;

    private final AuditOutboxEventRepository auditOutboxEventRepository;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelayString = "${app.audit.outbox-publisher-delay-ms:5000}")
    @Transactional
    public void publishPending() {
        var events = auditOutboxEventRepository.findPublishableForUpdate(
                EnumSet.of(AuditOutboxEventStatus.PENDING, AuditOutboxEventStatus.FAILED),
                PageRequest.of(0, BATCH_SIZE)
        );

        for (AuditOutboxEvent event : events) {
            publishOne(event);
        }
    }

    private void publishOne(AuditOutboxEvent event) {
        event.setLockedAt(LocalDateTime.now());
        try {
            // TODO: Enable RabbitMQ publisher confirms (for example spring.rabbitmq.publisher-confirm-type=correlated)
            // and wait for a positive broker ack before marking this outbox event as PUBLISHED.
            rabbitTemplate.convertAndSend(
                    RabbitMqConfig.AUDIT_EXCHANGE,
                    RabbitMqConfig.AUDIT_ROUTING_KEY,
                    toMessage(event)
            );
            event.setStatus(AuditOutboxEventStatus.PUBLISHED);
            event.setPublishedAt(LocalDateTime.now());
            event.setLastError(null);
        } catch (Exception ex) {
            event.setStatus(AuditOutboxEventStatus.FAILED);
            event.setRetryCount(event.getRetryCount() + 1);
            event.setLastError(trimError(ex.getMessage()));
            log.warn("Failed to publish audit outbox event. eventId={} retryCount={}",
                    event.getEventId(),
                    event.getRetryCount(),
                    ex);
        }
    }

    private AuditEventMessage toMessage(AuditOutboxEvent event) {
        return AuditEventMessage.builder()
                .eventId(event.getEventId())
                .action(event.getAction())
                .entity(event.getEntity())
                .entityId(event.getEntityId())
                .actorId(event.getActorId())
                .actorName(event.getActorName())
                .actorEmail(event.getActorEmail())
                .actorRole(event.getActorRole())
                .beforeJson(event.getBeforeJson())
                .afterJson(event.getAfterJson())
                .ipAddress(event.getIpAddress())
                .userAgent(event.getUserAgent())
                .createdAt(event.getCreatedAt())
                .build();
    }

    private String trimError(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }
}
