package com.PrimeCare.PrimeCare.modules.booking_restriction.service;

import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.booking_restriction.entity.PatientViolationEvent;
import com.PrimeCare.PrimeCare.modules.booking_restriction.enums.ViolationEventSource;
import com.PrimeCare.PrimeCare.modules.booking_restriction.enums.ViolationEventStatus;
import com.PrimeCare.PrimeCare.modules.booking_restriction.enums.ViolationEventType;
import com.PrimeCare.PrimeCare.modules.booking_restriction.repository.PatientViolationEventRepository;
import com.PrimeCare.PrimeCare.modules.patient.entity.Patient;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentCancellationReasonType;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import com.PrimeCare.PrimeCare.shared.utils.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PatientViolationEventService {

    private static final int NO_SHOW_POINTS = 3;
    private static final int LATE_CANCEL_POINTS = 2;
    private static final int WRONG_CONTACT_POINTS = 2;
    private static final int SUCCESSFUL_VISIT_CREDIT_POINTS = -1;
    private static final int MAX_MANUAL_ABS_POINTS = 10;

    private final PatientViolationEventRepository violationEventRepository;
    private final BookingIdentityService bookingIdentityService;
    private final BookingRestrictionPolicyService bookingRestrictionPolicyService;
    private final AuditLogService auditLogService;

    @Transactional
    public PatientViolationEvent recordNoShow(Appointment appointment, User staff, String note) {
        requireAppointment(appointment);

        return violationEventRepository
                .findFirstByAppointment_IdAndTypeAndStatusOrderByCreatedAtDesc(
                        appointment.getId(),
                        ViolationEventType.NO_SHOW,
                        ViolationEventStatus.ACTIVE
                )
                .orElseGet(() -> createAppointmentEvent(
                        appointment,
                        ViolationEventType.NO_SHOW,
                        ViolationEventSource.SYSTEM,
                        NO_SHOW_POINTS,
                        note,
                        staff,
                        noShowPeriodDate(appointment),
                        "Tự động kích hoạt sau sự kiện no-show"
                ));
    }

    @Transactional
    public PatientViolationEvent recordLateCancelIfEligible(
            Appointment appointment,
            User staff,
            AppointmentCancellationReasonType cancellationReasonType,
            boolean countAsViolation,
            String note
    ) {
        if (!countAsViolation || cancellationReasonType != AppointmentCancellationReasonType.PATIENT_REQUEST) {
            return null;
        }
        requireAppointment(appointment);

        LocalDateTime appointmentStart = appointmentStart(appointment);
        LocalDateTime now = LocalDateTime.now();
        if (appointmentStart == null
                || now.isBefore(appointmentStart.minusHours(4))
                || !now.isBefore(appointmentStart)) {
            return null;
        }

        return violationEventRepository
                .findFirstByAppointment_IdAndTypeAndStatusOrderByCreatedAtDesc(
                        appointment.getId(),
                        ViolationEventType.LATE_CANCEL,
                        ViolationEventStatus.ACTIVE
                )
                .orElseGet(() -> createAppointmentEvent(
                        appointment,
                        ViolationEventType.LATE_CANCEL,
                        ViolationEventSource.STAFF,
                        LATE_CANCEL_POINTS,
                        note,
                        requireStaff(staff),
                        lateCancelPeriodDate(appointment),
                        "Tự động cập nhật hạn chế sau hủy trễ do bệnh nhân yêu cầu"
                ));
    }

    @Transactional
    public PatientViolationEvent recordSuccessfulVisitCredit(Appointment appointment, User actor) {
        requireAppointment(appointment);

        return violationEventRepository
                .findFirstByAppointment_IdAndTypeAndStatusOrderByCreatedAtDesc(
                        appointment.getId(),
                        ViolationEventType.SUCCESSFUL_VISIT_CREDIT,
                        ViolationEventStatus.ACTIVE
                )
                .orElseGet(() -> createSuccessfulVisitCreditIfScoreExists(appointment, actor));
    }

    @Transactional
    public PatientViolationEvent recordWrongContact(Appointment appointment, User staff, String note) {
        requireAppointment(appointment);
        requireStaff(staff);
        String normalizedNote = requireReason(note);

        return violationEventRepository
                .findFirstByAppointment_IdAndTypeAndStatusOrderByCreatedAtDesc(
                        appointment.getId(),
                        ViolationEventType.WRONG_CONTACT,
                        ViolationEventStatus.ACTIVE
                )
                .orElseGet(() -> createAppointmentEvent(
                        appointment,
                        ViolationEventType.WRONG_CONTACT,
                        ViolationEventSource.STAFF,
                        WRONG_CONTACT_POINTS,
                        normalizedNote,
                        staff,
                        LocalDate.now(),
                        "Nhân viên xác nhận sai thông tin liên hệ"
                ));
    }

    @Transactional
    public PatientViolationEvent createManualEvent(
            BookingIdentity identity,
            Patient patient,
            Appointment appointment,
            int points,
            String reason,
            User staff
    ) {
        requireIdentity(identity);
        requireStaff(staff);
        String normalizedReason = requireReason(reason);
        validateManualPoints(points);

        return createEvent(
                patient,
                appointment,
                identity,
                bookingRestrictionPolicyService.periodMonth(LocalDate.now()),
                ViolationEventType.MANUAL,
                ViolationEventSource.STAFF,
                points,
                normalizedReason,
                staff,
                "Nhân viên điều chỉnh điểm vi phạm thủ công",
                "CREATE_VIOLATION_EVENT"
        );
    }

    @Transactional
    public PatientViolationEvent createStaffPardon(
            BookingIdentity identity,
            Patient patient,
            Appointment appointment,
            int pointsToReduce,
            String reason,
            User staff
    ) {
        requireIdentity(identity);
        requireStaff(staff);
        String normalizedReason = requireReason(reason);

        int points = pointsToReduce > 0 ? -pointsToReduce : pointsToReduce;
        validateStaffPardonPoints(points);

        return createEvent(
                patient,
                appointment,
                identity,
                bookingRestrictionPolicyService.periodMonth(LocalDate.now()),
                ViolationEventType.STAFF_PARDON,
                ViolationEventSource.STAFF,
                points,
                normalizedReason,
                staff,
                "Nhân viên ân xá điểm vi phạm",
                "CREATE_STAFF_PARDON"
        );
    }

    @Transactional
    public PatientViolationEvent voidViolationEvent(Long eventId, String reason, User staff) {
        if (eventId == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Sự kiện vi phạm là bắt buộc.");
        }
        requireStaff(staff);
        String normalizedReason = requireReason(reason);

        PatientViolationEvent event = violationEventRepository.findById(eventId)
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy sự kiện vi phạm."));
        if (event.getStatus() != ViolationEventStatus.ACTIVE) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Chỉ có thể void sự kiện đang hoạt động.");
        }

        Map<String, Object> before = eventSnapshot(event);
        event.setStatus(ViolationEventStatus.VOIDED);
        event.setVoidedBy(staff);
        event.setVoidedAt(LocalDateTime.now());
        event.setVoidReason(normalizedReason);
        PatientViolationEvent saved = violationEventRepository.save(event);

        auditLogService.log(staff, "VOID_VIOLATION_EVENT", "PATIENT_VIOLATION_EVENT", saved.getId(), before, eventSnapshot(saved));
        bookingRestrictionPolicyService.recalculateAfterScoreChange(
                saved.getIdentityKeyHash(),
                saved.getPatient(),
                staff,
                "Void sự kiện vi phạm: " + normalizedReason
        );
        return saved;
    }

    private PatientViolationEvent createSuccessfulVisitCreditIfScoreExists(Appointment appointment, User actor) {
        BookingIdentity identity = bookingIdentityService.resolveAppointmentIdentity(appointment);
        String periodMonth = bookingRestrictionPolicyService.periodMonth(successfulVisitPeriodDate(appointment));
        if (bookingRestrictionPolicyService.monthlyScore(identity.identityKeyHash(), periodMonth) <= 0) {
            return null;
        }

        return createEvent(
                appointment.getPatient(),
                appointment,
                identity,
                periodMonth,
                ViolationEventType.SUCCESSFUL_VISIT_CREDIT,
                ViolationEventSource.SYSTEM,
                SUCCESSFUL_VISIT_CREDIT_POINTS,
                "Tự động trừ điểm sau lần khám hoàn tất",
                actor,
                "Tự động cập nhật hạn chế sau lần khám hoàn tất",
                "CREATE_SUCCESSFUL_VISIT_CREDIT"
        );
    }

    private PatientViolationEvent createAppointmentEvent(
            Appointment appointment,
            ViolationEventType type,
            ViolationEventSource source,
            int points,
            String note,
            User createdBy,
            LocalDate periodDate,
            String recalculateReason
    ) {
        BookingIdentity identity = bookingIdentityService.resolveAppointmentIdentity(appointment);
        return createEvent(
                appointment.getPatient(),
                appointment,
                identity,
                bookingRestrictionPolicyService.periodMonth(periodDate),
                type,
                source,
                points,
                note,
                createdBy,
                recalculateReason,
                "CREATE_VIOLATION_EVENT"
        );
    }

    private PatientViolationEvent createEvent(
            Patient patient,
            Appointment appointment,
            BookingIdentity identity,
            String periodMonth,
            ViolationEventType type,
            ViolationEventSource source,
            int points,
            String note,
            User createdBy,
            String recalculateReason,
            String auditAction
    ) {
        requireIdentity(identity);

        PatientViolationEvent event = PatientViolationEvent.builder()
                .patient(patient)
                .appointment(appointment)
                .identityKeyHash(identity.identityKeyHash())
                .periodMonth(periodMonth)
                .type(type)
                .source(source)
                .status(ViolationEventStatus.ACTIVE)
                .points(points)
                .note(StringUtil.trimToNull(note))
                .createdBy(createdBy)
                .build();

        PatientViolationEvent saved = violationEventRepository.save(event);
        auditLogService.log(createdBy, auditAction, "PATIENT_VIOLATION_EVENT", saved.getId(), null, eventSnapshot(saved));
        if (!"CREATE_VIOLATION_EVENT".equals(auditAction)) {
            auditLogService.log(createdBy, "CREATE_VIOLATION_EVENT", "PATIENT_VIOLATION_EVENT", saved.getId(), null, eventSnapshot(saved));
        }
        bookingRestrictionPolicyService.recalculateAfterScoreChange(
                saved.getIdentityKeyHash(),
                saved.getPatient(),
                createdBy,
                recalculateReason
        );
        return saved;
    }

    private void validateManualPoints(int points) {
        if (points == 0 || Math.abs(points) > MAX_MANUAL_ABS_POINTS) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Điểm điều chỉnh thủ công phải khác 0 và trong khoảng -10 đến 10.");
        }
    }

    private void validateStaffPardonPoints(int points) {
        if (points >= 0 || Math.abs(points) > MAX_MANUAL_ABS_POINTS) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Điểm ân xá phải là số âm trong khoảng -1 đến -10.");
        }
    }

    private void requireAppointment(Appointment appointment) {
        if (appointment == null || appointment.getId() == null) {
            throw new ApiException(ErrorCode.APPOINTMENT_NOT_FOUND);
        }
    }

    private User requireStaff(User staff) {
        if (staff == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        return staff;
    }

    private void requireIdentity(BookingIdentity identity) {
        if (identity == null || !identity.hasIdentityKeyHash()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Không đủ thông tin định danh để tính điểm vi phạm.");
        }
    }

    private String requireReason(String reason) {
        String normalizedReason = StringUtil.trimToNull(reason);
        if (normalizedReason == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Lý do là bắt buộc.");
        }
        return normalizedReason;
    }

    private LocalDate noShowPeriodDate(Appointment appointment) {
        LocalDateTime markedAt = appointment.getNoShowMarkedAt();
        if (markedAt != null) {
            return markedAt.toLocalDate();
        }
        return LocalDate.now();
    }

    private LocalDate lateCancelPeriodDate(Appointment appointment) {
        LocalDateTime cancelledAt = appointment.getCancelledAt();
        if (cancelledAt != null) {
            return cancelledAt.toLocalDate();
        }
        return LocalDate.now();
    }

    private LocalDate successfulVisitPeriodDate(Appointment appointment) {
        LocalDateTime completedAt = appointment.getCompletedAt();
        if (completedAt != null) {
            return completedAt.toLocalDate();
        }
        return LocalDate.now();
    }

    private LocalDateTime appointmentStart(Appointment appointment) {
        if (appointment.getVisitDate() == null || appointment.getEtaStart() == null) {
            return null;
        }
        return appointment.getVisitDate().atTime(appointment.getEtaStart());
    }

    private Map<String, Object> eventSnapshot(PatientViolationEvent event) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (event == null) {
            return snapshot;
        }
        snapshot.put("id", event.getId());
        snapshot.put("patientId", event.getPatient() != null ? event.getPatient().getId() : null);
        snapshot.put("appointmentId", event.getAppointment() != null ? event.getAppointment().getId() : null);
        snapshot.put("identityKeyHash", event.getIdentityKeyHash());
        snapshot.put("periodMonth", event.getPeriodMonth());
        snapshot.put("type", event.getType());
        snapshot.put("source", event.getSource());
        snapshot.put("status", event.getStatus());
        snapshot.put("points", event.getPoints());
        snapshot.put("note", event.getNote());
        snapshot.put("createdBy", event.getCreatedBy() != null ? event.getCreatedBy().getId() : null);
        snapshot.put("voidedBy", event.getVoidedBy() != null ? event.getVoidedBy().getId() : null);
        snapshot.put("voidedAt", event.getVoidedAt());
        snapshot.put("voidReason", event.getVoidReason());
        return snapshot;
    }
}
