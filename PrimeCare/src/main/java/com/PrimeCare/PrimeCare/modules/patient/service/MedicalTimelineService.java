package com.PrimeCare.PrimeCare.modules.patient.service;

import com.PrimeCare.PrimeCare.modules.encounter.entity.Encounter;
import com.PrimeCare.PrimeCare.modules.patient.dto.response.MedicalTimelineEvent;
import com.PrimeCare.PrimeCare.modules.patient.entity.Patient;
import com.PrimeCare.PrimeCare.modules.patient.repository.PatientRepository;
import com.PrimeCare.PrimeCare.modules.prescription.entity.Prescription;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Aggregates medical history from multiple sources (encounters, prescriptions, service results)
 * into a unified chronological timeline for a patient.
 */
@Service
@RequiredArgsConstructor
public class MedicalTimelineService {

    private final PatientRepository patientRepository;
    private final EntityManager entityManager;
    private final DoctorPatientAuthorizationService doctorPatientAuthorizationService;

    @Transactional(readOnly = true)
    public List<MedicalTimelineEvent> getTimelineForDoctor(Long patientId, Long doctorUserId, LocalDate from, LocalDate to, String typeFilter) {
        doctorPatientAuthorizationService.requireAccess(patientId, doctorUserId);
        return getTimeline(patientId, from, to, typeFilter);
    }

    private List<MedicalTimelineEvent> getTimeline(Long patientId, LocalDate from, LocalDate to, String typeFilter) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ApiException(ErrorCode.PATIENT_NOT_FOUND));

        LocalDateTime fromDt = from != null ? from.atStartOfDay() : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime toDt = to != null ? to.atTime(LocalTime.MAX) : LocalDateTime.now().plusDays(1);

        List<MedicalTimelineEvent> timeline = new ArrayList<>();

        if (typeFilter == null || typeFilter.equals("ENCOUNTER")) {
            timeline.addAll(getEncounterEvents(patientId, fromDt, toDt));
        }
        if (typeFilter == null || typeFilter.equals("PRESCRIPTION")) {
            timeline.addAll(getPrescriptionEvents(patientId, fromDt, toDt));
        }
        if (typeFilter == null || typeFilter.equals("SERVICE_RESULT")) {
            timeline.addAll(getServiceResultEvents(patientId, fromDt, toDt));
        }

        timeline.sort(Comparator.comparing(MedicalTimelineEvent::getOccurredAt).reversed());
        return timeline;
    }

    private List<MedicalTimelineEvent> getEncounterEvents(Long patientId, LocalDateTime from, LocalDateTime to) {
        TypedQuery<Encounter> query = entityManager.createQuery(
                "SELECT e FROM Encounter e JOIN FETCH e.doctor JOIN FETCH e.specialty " +
                        "WHERE e.patient.id = :pid AND e.startedAt BETWEEN :from AND :to " +
                        "ORDER BY e.startedAt DESC", Encounter.class);
        query.setParameter("pid", patientId);
        query.setParameter("from", from);
        query.setParameter("to", to);

        return query.getResultList().stream().map(e -> MedicalTimelineEvent.builder()
                .type("ENCOUNTER")
                .referenceId(e.getId())
                .title("Lần khám: " + e.getCode())
                .subtitle(e.getSpecialty() != null ? e.getSpecialty().getNameVn() : null)
                .description(e.getFinalDiagnosis() != null ? e.getFinalDiagnosis() : e.getPreliminaryDiagnosis())
                .status(e.getStatus().name())
                .occurredAt(e.getStartedAt())
                .performedBy(e.getDoctor() != null ? e.getDoctor().getFullName() : null)
                .build()).toList();
    }

    private List<MedicalTimelineEvent> getPrescriptionEvents(Long patientId, LocalDateTime from, LocalDateTime to) {
        TypedQuery<Prescription> query = entityManager.createQuery(
                "SELECT p FROM Prescription p JOIN FETCH p.encounter e " +
                        "WHERE e.patient.id = :pid AND p.createdAt BETWEEN :from AND :to " +
                        "ORDER BY p.createdAt DESC", Prescription.class);
        query.setParameter("pid", patientId);
        query.setParameter("from", from);
        query.setParameter("to", to);

        return query.getResultList().stream().map(p -> {
            int itemCount = p.getItems() != null ? p.getItems().size() : 0;
            return MedicalTimelineEvent.builder()
                    .type("PRESCRIPTION")
                    .referenceId(p.getId())
                    .title("Đơn thuốc: " + p.getCode())
                    .subtitle(itemCount + " loại thuốc")
                    .description(p.getGeneralNote())
                    .status(p.getStatus().name())
                    .occurredAt(p.getCreatedAt())
                    .performedBy(p.getDoctorUser() != null ? p.getDoctorUser().getFullName() : null)
                    .build();
        }).toList();
    }

    @SuppressWarnings("unchecked")
    private List<MedicalTimelineEvent> getServiceResultEvents(Long patientId, LocalDateTime from, LocalDateTime to) {
        // Use native-ish JPQL to join service_result -> service_order_item -> service_order -> encounter -> patient
        var query = entityManager.createQuery(
                "SELECT sr FROM com.PrimeCare.PrimeCare.modules.service_result.entity.ServiceResult sr " +
                        "JOIN sr.serviceOrderItem soi JOIN soi.serviceOrder so JOIN so.encounter e " +
                        "WHERE e.patient.id = :pid AND sr.performedAt BETWEEN :from AND :to " +
                        "ORDER BY sr.performedAt DESC");
        query.setParameter("pid", patientId);
        query.setParameter("from", from);
        query.setParameter("to", to);

        List<?> results = query.getResultList();
        List<MedicalTimelineEvent> events = new ArrayList<>();
        for (Object obj : results) {
            var sr = (com.PrimeCare.PrimeCare.modules.service_result.entity.ServiceResult) obj;
            events.add(MedicalTimelineEvent.builder()
                    .type("SERVICE_RESULT")
                    .referenceId(sr.getId())
                    .title("Kết quả: " + (sr.getReportTitle() != null ? sr.getReportTitle() : "Xét nghiệm"))
                    .subtitle(sr.getServiceOrderItem() != null ? sr.getServiceOrderItem().getServiceNameVnSnapshot() : null)
                    .description(sr.getConclusionText())
                    .status(sr.getStatus().name())
                    .occurredAt(sr.getPerformedAt() != null ? sr.getPerformedAt() : sr.getCreatedAt())
                    .performedBy(sr.getPerformedByUser() != null ? sr.getPerformedByUser().getFullName() : null)
                    .build());
        }
        return events;
    }
}
