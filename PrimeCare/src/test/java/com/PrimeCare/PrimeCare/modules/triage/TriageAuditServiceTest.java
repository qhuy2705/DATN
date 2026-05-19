package com.PrimeCare.PrimeCare.modules.triage;

import com.PrimeCare.PrimeCare.modules.appointment.dto.request.PreTriageInputRequest;
import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.staff.entity.StaffProfile;
import com.PrimeCare.PrimeCare.modules.triage.entity.TriageAuditLog;
import com.PrimeCare.PrimeCare.modules.triage.repository.TriageAuditLogRepository;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TriageAuditServiceTest {

    @Mock
    private TriageAuditLogRepository repository;

    @Test
    void recordsPreTriageSuggestedSnapshot() {
        TriageAuditService service = new TriageAuditService(repository, new ObjectMapper());
        Appointment appointment = Appointment.builder().id(1L).build();
        PreTriageInputRequest input = new PreTriageInputRequest();
        input.setSymptomOnset("TODAY");
        input.setChronicConditions(List.of("DIABETES"));
        input.setChronicConditionOthers(List.of("Suy thận mạn"));
        PreTriageResult result = PreTriageResult.builder()
                .priority("PRIORITY")
                .summary("Gợi ý P2")
                .source("RULE")
                .confidenceLevel("MEDIUM")
                .knowledgeBaseVersion("2026.05.01")
                .rulesetVersion("triage-rules-v1")
                .build();

        service.recordPreTriageSuggested(appointment, result, input);

        TriageAuditLog auditLog = capturedAudit();
        assertThat(auditLog.getAction()).isEqualTo(TriageAuditService.ACTION_PRE_TRIAGE_SUGGESTED);
        assertThat(auditLog.getActorType()).isEqualTo("SYSTEM");
        assertThat(auditLog.getToPriority()).isEqualTo("PRIORITY");
        assertThat(auditLog.getSnapshotJson()).contains("Suy thận mạn", "triage-rules-v1");
    }

    @Test
    void recordsStaffOverride() {
        TriageAuditService service = new TriageAuditService(repository, new ObjectMapper());
        Appointment appointment = Appointment.builder()
                .id(1L)
                .preTriagePriority("ROUTINE")
                .triagePriority("URGENT")
                .triageReviewStatus("OVERRIDDEN")
                .triageOverrideReason("Đau tăng nhanh")
                .build();
        User staff = User.builder()
                .id(10L)
                .role(UserRole.STAFF)
                .staffProfile(StaffProfile.builder().fullName("Lễ tân A").build())
                .build();

        service.recordStaffReview(
                appointment,
                staff,
                TriageAuditService.ACTION_TRIAGE_OVERRIDDEN,
                "ROUTINE",
                "URGENT",
                "Đau tăng nhanh"
        );

        TriageAuditLog auditLog = capturedAudit();
        assertThat(auditLog.getAction()).isEqualTo(TriageAuditService.ACTION_TRIAGE_OVERRIDDEN);
        assertThat(auditLog.getActorType()).isEqualTo("STAFF");
        assertThat(auditLog.getActorId()).isEqualTo(10L);
        assertThat(auditLog.getActorName()).isEqualTo("Lễ tân A");
        assertThat(auditLog.getFromPriority()).isEqualTo("ROUTINE");
        assertThat(auditLog.getToPriority()).isEqualTo("URGENT");
    }

    private TriageAuditLog capturedAudit() {
        ArgumentCaptor<TriageAuditLog> captor = ArgumentCaptor.forClass(TriageAuditLog.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
