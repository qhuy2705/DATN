package com.PrimeCare.PrimeCare.modules.audit.service;

import com.PrimeCare.PrimeCare.modules.audit.entity.AuditLog;
import com.PrimeCare.PrimeCare.modules.audit.entity.AuditOutboxEvent;
import com.PrimeCare.PrimeCare.modules.audit.mapper.AuditLogMapper;
import com.PrimeCare.PrimeCare.modules.audit.repository.AuditLogRepository;
import com.PrimeCare.PrimeCare.modules.audit.repository.AuditOutboxEventRepository;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private AuditOutboxEventRepository auditOutboxEventRepository;
    @Mock
    private AuditLogMapper auditLogMapper;
    @Mock
    private UserRepository userRepository;

    @Test
    void logEnqueuesOutboxEventAndRedactsSensitiveFields() {
        AuditLogService service = service();
        User actor = User.builder()
                .id(10L)
                .email("staff@example.test")
                .role(UserRole.STAFF)
                .build();

        service.log(
                actor,
                "CHANGE_PASSWORD",
                "USER",
                10L,
                Map.of("oldPassword", "old-secret", "status", "ACTIVE"),
                Map.of("newPassword", "new-secret", "token", "jwt-token", "status", "ACTIVE")
        );

        ArgumentCaptor<AuditOutboxEvent> captor = ArgumentCaptor.forClass(AuditOutboxEvent.class);
        verify(auditOutboxEventRepository).save(captor.capture());
        verify(auditLogRepository, never()).save(any());

        AuditOutboxEvent event = captor.getValue();
        assertThat(event.getEventId()).isNotBlank();
        assertThat(event.getActorId()).isEqualTo(10L);
        assertThat(event.getActorEmail()).isEqualTo("staff@example.test");
        assertThat(event.getActorRole()).isEqualTo("STAFF");
        assertThat(event.getAction()).isEqualTo("CHANGE_PASSWORD");
        assertThat(event.getEntity()).isEqualTo("USER");
        assertThat(event.getEntityId()).isEqualTo(10L);
        assertThat(event.getBeforeJson()).contains("\"oldPassword\":\"[REDACTED]\"");
        assertThat(event.getBeforeJson()).contains("\"status\":\"ACTIVE\"");
        assertThat(event.getAfterJson()).contains("\"newPassword\":\"[REDACTED]\"");
        assertThat(event.getAfterJson()).contains("\"token\":\"[REDACTED]\"");
        assertThat(event.getAfterJson()).doesNotContain("new-secret", "jwt-token");
    }

    @Test
    void searchUsesCreatedAtDescendingByDefaultAndAcceptsFilters() {
        AuditLogService service = service();
        when(auditLogRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(AuditLog.builder()
                        .id(1L)
                        .action("UPDATE")
                        .entity("APPOINTMENT")
                        .build())));

        service.search(
                LocalDate.now(),
                "UPDATE",
                "APPOINTMENT",
                99L,
                10L,
                "STAFF",
                "staff",
                null,
                null,
                PageRequest.of(0, 20)
        );

        ArgumentCaptor<PageRequest> pageableCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(auditLogRepository).findAll(any(Specification.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private AuditLogService service() {
        return new AuditLogService(
                auditLogRepository,
                auditOutboxEventRepository,
                auditLogMapper,
                new AuditRedactionService(new ObjectMapper()),
                userRepository
        );
    }
}
