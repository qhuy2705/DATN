package com.PrimeCare.PrimeCare.modules.triage;

import com.PrimeCare.PrimeCare.modules.appointment.dto.request.PreTriageInputRequest;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.TriageAuditLogResponse;
import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.triage.entity.TriageAuditLog;
import com.PrimeCare.PrimeCare.modules.triage.repository.TriageAuditLogRepository;
import com.PrimeCare.PrimeCare.shared.utils.StringUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TriageAuditService {

    public static final String ACTION_PRE_TRIAGE_SUGGESTED = "PRE_TRIAGE_SUGGESTED";
    public static final String ACTION_TRIAGE_ACCEPTED = "TRIAGE_ACCEPTED";
    public static final String ACTION_TRIAGE_OVERRIDDEN = "TRIAGE_OVERRIDDEN";
    public static final String ACTION_TRIAGE_MANUAL_SET = "TRIAGE_MANUAL_SET";

    private final TriageAuditLogRepository triageAuditLogRepository;
    private final ObjectMapper objectMapper;

    public void recordPreTriageSuggested(
            Appointment appointment,
            PreTriageResult result,
            PreTriageInputRequest input
    ) {
        if (appointment == null || appointment.getId() == null || result == null) {
            return;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("symptomOnset", TriagePriorityNormalizer.normalizeSymptomOnset(input != null ? input.getSymptomOnset() : null));
        snapshot.put("chronicConditions", input != null
                ? TriagePriorityNormalizer.normalizeChronicConditions(input.getChronicConditions())
                : List.of());
        snapshot.put("chronicConditionOthers", input != null
                ? TriagePriorityNormalizer.normalizeChronicConditionOthers(input.getChronicConditionOthers())
                : List.of());
        snapshot.put("functionalImpact", TriagePriorityNormalizer.normalizeFunctionalImpact(input != null ? input.getFunctionalImpact() : null));
        snapshot.put("redFlags", input != null
                ? TriagePriorityNormalizer.normalizeRedFlags(input.getRedFlags())
                : List.of());
        snapshot.put("matchedTerms", result.getMatchedTerms());
        snapshot.put("matchedRules", result.getMatchedRules());
        snapshot.put("source", result.getSource());
        snapshot.put("confidenceLevel", result.getConfidenceLevel());
        snapshot.put("knowledgeBaseVersion", result.getKnowledgeBaseVersion());
        snapshot.put("rulesetVersion", result.getRulesetVersion());
        snapshot.put("aiModelVersion", result.getAiModelVersion());

        saveAudit(TriageAuditLog.builder()
                .appointment(appointment)
                .actorType("SYSTEM")
                .action(ACTION_PRE_TRIAGE_SUGGESTED)
                .toPriority(result.getPriority())
                .reason(result.getSummary())
                .snapshotJson(writeSnapshot(snapshot))
                .build());
    }

    public void recordStaffReview(
            Appointment appointment,
            User staff,
            String action,
            String fromPriority,
            String toPriority,
            String reason
    ) {
        if (appointment == null || appointment.getId() == null || action == null || toPriority == null) {
            return;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("preTriagePriority", appointment.getPreTriagePriority());
        snapshot.put("triagePriority", appointment.getTriagePriority());
        snapshot.put("triageReviewStatus", appointment.getTriageReviewStatus());
        snapshot.put("triageReviewedById", staff != null ? staff.getId() : null);
        snapshot.put("triageReviewedAt", appointment.getTriageReviewedAt());
        snapshot.put("triageNote", appointment.getTriageNote());
        snapshot.put("triageOverrideReason", appointment.getTriageOverrideReason());

        saveAudit(TriageAuditLog.builder()
                .appointment(appointment)
                .actorType("STAFF")
                .actorId(staff != null ? staff.getId() : null)
                .actorName(staff != null ? resolveUserDisplayName(staff) : null)
                .action(action)
                .fromPriority(fromPriority)
                .toPriority(toPriority)
                .reason(StringUtil.trimToNull(reason))
                .snapshotJson(writeSnapshot(snapshot))
                .build());
    }

    @Transactional(readOnly = true)
    public List<TriageAuditLogResponse> findResponses(Long appointmentId) {
        if (appointmentId == null) {
            return List.of();
        }
        return triageAuditLogRepository.findByAppointment_IdOrderByCreatedAtAsc(appointmentId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private void saveAudit(TriageAuditLog auditLog) {
        try {
            triageAuditLogRepository.save(auditLog);
        } catch (Exception ex) {
            log.warn("Cannot save triage audit action={} appointmentId={}: {}",
                    auditLog.getAction(),
                    auditLog.getAppointment() != null ? auditLog.getAppointment().getId() : null,
                    ex.getMessage());
        }
    }

    private String writeSnapshot(Map<String, Object> snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            log.warn("Cannot serialize triage audit snapshot: {}", ex.getMessage());
            return "{}";
        }
    }

    private TriageAuditLogResponse toResponse(TriageAuditLog auditLog) {
        return TriageAuditLogResponse.builder()
                .id(auditLog.getId())
                .actorType(auditLog.getActorType())
                .actorId(auditLog.getActorId())
                .actorName(auditLog.getActorName())
                .action(auditLog.getAction())
                .fromPriority(auditLog.getFromPriority())
                .toPriority(auditLog.getToPriority())
                .reason(auditLog.getReason())
                .snapshotJson(auditLog.getSnapshotJson())
                .createdAt(auditLog.getCreatedAt())
                .build();
    }

    private String resolveUserDisplayName(User user) {
        if (user.getStaffProfile() != null && user.getStaffProfile().getFullName() != null) {
            return user.getStaffProfile().getFullName();
        }
        if (user.getDoctorProfile() != null && user.getDoctorProfile().getFullName() != null) {
            return user.getDoctorProfile().getFullName();
        }
        return user.getEmail();
    }
}
