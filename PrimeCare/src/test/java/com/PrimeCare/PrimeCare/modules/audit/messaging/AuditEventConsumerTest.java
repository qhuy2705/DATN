package com.PrimeCare.PrimeCare.modules.audit.messaging;

import com.PrimeCare.PrimeCare.modules.audit.dto.message.AuditEventMessage;
import com.PrimeCare.PrimeCare.modules.audit.entity.AuditLog;
import com.PrimeCare.PrimeCare.modules.audit.repository.AuditLogRepository;
import com.PrimeCare.PrimeCare.modules.audit.service.AuditRedactionService;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DataIntegrityViolationException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditEventConsumerTest {

    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private UserRepository userRepository;

    @Test
    void consumeSkipsWhenEventIdAlreadyExists() {
        AuditEventConsumer consumer = consumer();
        when(auditLogRepository.existsByEventId("event-1")).thenReturn(true);

        consumer.consume(AuditEventMessage.builder().eventId("event-1").build());

        verify(auditLogRepository, never()).saveAndFlush(any());
        verify(userRepository, never()).findById(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void consumeTreatsDuplicateInsertRaceAsAlreadyProcessed() {
        AuditEventConsumer consumer = consumer();
        when(auditLogRepository.existsByEventId("event-race")).thenReturn(false, true);
        when(auditLogRepository.saveAndFlush(any(AuditLog.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate event_id"));

        assertThatCode(() -> consumer.consume(AuditEventMessage.builder()
                .eventId("event-race")
                .action("UPDATE_DOCTOR")
                .entity("DOCTOR")
                .entityId(5L)
                .build()))
                .doesNotThrowAnyException();
    }

    @Test
    void consumeWritesAuditLogAndRedactsPayloadAgain() {
        AuditEventConsumer consumer = consumer();
        when(auditLogRepository.existsByEventId("event-2")).thenReturn(false);
        when(userRepository.findById(10L)).thenReturn(Optional.of(User.builder()
                .id(10L)
                .email("staff@example.test")
                .role(UserRole.STAFF)
                .build()));

        consumer.consume(AuditEventMessage.builder()
                .eventId("event-2")
                .actorId(10L)
                .actorName("Staff Test")
                .actorEmail("staff@example.test")
                .actorRole("STAFF")
                .action("UPDATE")
                .entity("APPOINTMENT")
                .entityId(99L)
                .beforeJson("{\"status\":\"REQUESTED\"}")
                .afterJson("{\"status\":\"CONFIRMED\",\"accessToken\":\"raw-token\"}")
                .ipAddress("127.0.0.1")
                .userAgent("JUnit")
                .createdAt(LocalDateTime.of(2026, 5, 17, 8, 0))
                .build());

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).saveAndFlush(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo("event-2");
        assertThat(saved.getActor()).isNotNull();
        assertThat(saved.getActorName()).isEqualTo("Staff Test");
        assertThat(saved.getActorEmail()).isEqualTo("staff@example.test");
        assertThat(saved.getActorRole()).isEqualTo("STAFF");
        assertThat(saved.getAction()).isEqualTo("UPDATE");
        assertThat(saved.getEntity()).isEqualTo("APPOINTMENT");
        assertThat(saved.getEntityId()).isEqualTo(99L);
        assertThat(saved.getAfterJson()).contains("\"accessToken\":\"[REDACTED]\"");
        assertThat(saved.getAfterJson()).doesNotContain("raw-token");
    }

    private AuditEventConsumer consumer() {
        return new AuditEventConsumer(
                auditLogRepository,
                userRepository,
                new AuditRedactionService(new ObjectMapper())
        );
    }
}
