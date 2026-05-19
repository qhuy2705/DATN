package com.PrimeCare.PrimeCare.modules.appointment.service;

import com.PrimeCare.PrimeCare.modules.appointment.dto.query.AppointmentStatusCountRow;
import com.PrimeCare.PrimeCare.modules.appointment.dto.request.AppointmentConfirmRequest;
import com.PrimeCare.PrimeCare.modules.appointment.dto.request.DoctorCancellationRecoveryRequest;
import com.PrimeCare.PrimeCare.modules.appointment.dto.request.UpdateAppointmentIntakeRequest;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.AppointmentAdminResponse;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.AppointmentAvailabilityResponse;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.FollowUpQueueItemResponse;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.AppointmentStatusSummaryResponse;
import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.entity.AppointmentSlotHold;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentCallLogRepository;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentSlotHoldRepository;
import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.booking_restriction.service.PatientViolationEventService;
import com.PrimeCare.PrimeCare.modules.doctor_schedule.entity.DoctorWorkSchedule;
import com.PrimeCare.PrimeCare.modules.doctor_schedule.repository.DoctorWorkScheduleRepository;
import com.PrimeCare.PrimeCare.modules.encounter.repository.EncounterRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.BranchSession;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.repository.BranchSessionRepository;
import com.PrimeCare.PrimeCare.modules.notification.messaging.AppointmentMailEventPublisher;
import com.PrimeCare.PrimeCare.modules.notification.service.InternalNotificationService;
import com.PrimeCare.PrimeCare.modules.notification.service.AppointmentPdfJobService;
import com.PrimeCare.PrimeCare.modules.realtime.service.AfterCommitExecutor;
import com.PrimeCare.PrimeCare.modules.realtime.service.RealtimeEventPublisher;
import com.PrimeCare.PrimeCare.modules.triage.TriageJsonSupport;
import com.PrimeCare.PrimeCare.modules.triage.TriageAuditService;
import com.PrimeCare.PrimeCare.modules.triage.TriageMatchedRule;
import com.PrimeCare.PrimeCare.modules.triage.TriageMatchedTerm;
import com.PrimeCare.PrimeCare.modules.triage.TriagePriorityNormalizer;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentSourceType;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentCancellationReasonType;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentContactStatus;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentFollowUpType;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentPatientResponseStatus;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.enums.ArrivalStatus;
import com.PrimeCare.PrimeCare.shared.enums.CallOutcome;
import com.PrimeCare.PrimeCare.shared.enums.EncounterStatus;
import com.PrimeCare.PrimeCare.shared.enums.FollowUpQueueCategory;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import com.PrimeCare.PrimeCare.shared.utils.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppointmentAdminService {

    private static final String CANCELLATION_REASON_REQUIRED_MESSAGE = "Cancellation reason is required.";

    private static final EnumSet<AppointmentStatus> STAFF_SUMMARY_STATUSES = EnumSet.of(
            AppointmentStatus.REQUESTED,
            AppointmentStatus.CONFIRMED,
            AppointmentStatus.CHECKED_IN,
            AppointmentStatus.COMPLETED,
            AppointmentStatus.NO_SHOW,
            AppointmentStatus.CANCELLED
    );

    private static final EnumSet<EncounterStatus> ACTIVE_ENCOUNTER_STATUSES = EnumSet.of(
            EncounterStatus.IN_PROGRESS,
            EncounterStatus.WAITING_PAYMENT,
            EncounterStatus.WAITING_RESULTS,
            EncounterStatus.READY_FOR_CONCLUSION,
            EncounterStatus.REOPENED
    );
    private static final DateTimeFormatter CLAIM_EXPIRY_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final AppointmentRepository appointmentRepository;
    private final AppointmentSlotHoldRepository appointmentSlotHoldRepository;
    private final UserRepository userRepository;
    private final AppointmentCallLogRepository appointmentCallLogRepository;
    private final AppointmentMailEventPublisher appointmentMailEventPublisher;
    private final AppointmentCheckInTokenService appointmentCheckInTokenService;
    private final AppointmentAvailabilityService appointmentAvailabilityService;
    private final DoctorWorkScheduleRepository doctorWorkScheduleRepository;
    private final BranchSessionRepository branchSessionRepository;
    private final AppointmentCodeGenerator appointmentCodeGenerator;
    private final AppointmentStatusHistoryService appointmentStatusHistoryService;
    private final AppointmentPdfJobService appointmentPdfJobService;
    private final ReceptionQueueAllocator receptionQueueAllocator;
    private final AppointmentQueueService appointmentQueueService;
    private final EncounterRepository encounterRepository;
    private final AuditLogService auditLogService;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final AfterCommitExecutor afterCommitExecutor;
    private final InternalNotificationService internalNotificationService;
    private final TriageAuditService triageAuditService;
    private final DoctorCancellationRecoveryService doctorCancellationRecoveryService;
    private final PatientViolationEventService patientViolationEventService;
    private final AppointmentResponseService appointmentResponseService;

    @Value("${app.appointment.claim.ttl-minutes:5}")
    private long claimTtlMinutes;

    @Transactional(readOnly = true)
    public PageResponse<AppointmentAdminResponse> search(
            AppointmentStatus status,
            LocalDate visitDate,
            Long branchId,
            Long doctorId,
            Long specialtyId,
            Boolean overdue,
            Boolean followUpPending,
            String q,
            Pageable pageable
    ) {
        String keyword = StringUtil.trimToNull(q);
        LocalDateTime now = LocalDateTime.now();
        Page<Appointment> page = appointmentRepository.search(
                status,
                visitDate,
                branchId,
                doctorId,
                specialtyId,
                Boolean.TRUE.equals(overdue),
                followUpPending,
                LocalDate.now(),
                overdueCutoffTime(now),
                keyword,
                keyword != null ? keyword.toLowerCase(java.util.Locale.ROOT) : null,
                pageable
        );

        return PageResponse.<AppointmentAdminResponse>builder()
                           .items(page.getContent().stream().map(this::toAdminResponse).toList())
                           .meta(PageResponse.Meta.builder()
                                                  .page(page.getNumber())
                                                  .size(page.getSize())
                                                  .totalItems(page.getTotalElements())
                                                  .totalPages(page.getTotalPages())
                                                  .hasNext(page.hasNext())
                                                  .hasPrev(page.hasPrevious())
                                                  .sort(pageable.getSort().toString())
                                                  .build())
                           .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<FollowUpQueueItemResponse> getFollowUpQueue(
            FollowUpQueueCategory category,
            AppointmentFollowUpType followUpType,
            String q,
            Pageable pageable
    ) {
        String keyword = StringUtil.trimToNull(q);
        FollowUpFilter filter = followUpFilter(category, followUpType);
        Page<Appointment> page = appointmentRepository.searchFollowUpQueue(
                filter.followUpTypes(),
                filter.filterTypes(),
                filter.includeLegacyNoShow(),
                keyword,
                keyword != null ? keyword.toLowerCase(java.util.Locale.ROOT) : null,
                pageable
        );
        Map<Long, AppointmentSlotHold> latestHolds = latestSlotHoldsByAppointmentId(page.getContent());

        return PageResponse.<FollowUpQueueItemResponse>builder()
                           .items(page.getContent()
                                      .stream()
                                      .map(appointment -> toFollowUpQueueItemResponse(
                                              appointment,
                                              latestHolds.get(appointment.getId())
                                      ))
                                      .toList())
                           .meta(PageResponse.Meta.builder()
                                                  .page(page.getNumber())
                                                  .size(page.getSize())
                                                  .totalItems(page.getTotalElements())
                                                  .totalPages(page.getTotalPages())
                                                  .hasNext(page.hasNext())
                                                  .hasPrev(page.hasPrevious())
                                                  .sort(pageable.getSort().toString())
                                                  .build())
                           .build();
    }

    @Transactional(readOnly = true)
    public AppointmentAdminResponse getDetail(Long appointmentId) {
        AppointmentAdminResponse response = toAdminResponse(getAppointment(appointmentId));
        response.setTriageAuditLogs(triageAuditService.findResponses(appointmentId));
        return response;
    }

    @Transactional
    public AppointmentAdminResponse claim(Long appointmentId, Long staffUserId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireAt = claimExpiresAt(now);

        User staff = getUser(staffUserId);

        int updated = appointmentRepository.claimForProcessing(appointmentId, staff, now, expireAt);
        if (updated > 0) {
            return completeClaim(appointmentId, staff);
        }

        return handleClaimMiss(appointmentId, staffUserId, staff, now);
    }

    private AppointmentAdminResponse completeClaim(Long appointmentId, User staff) {
        Appointment appointment = getAppointment(appointmentId);
        auditLogService.log(staff, "CLAIM", "APPOINTMENT", appointment.getId(), null, snapshotAppointment(appointment));
        publishProcessingChanged(appointment);

        return toAdminResponse(appointment);
    }

    private AppointmentAdminResponse handleClaimMiss(
            Long appointmentId,
            Long staffUserId,
            User staff,
            LocalDateTime now
    ) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                                                       .orElseThrow(() -> new ApiException(ErrorCode.APPOINTMENT_NOT_FOUND));

        if (!isClaimableForProcessing(appointment)) {
            throw new ApiException(ErrorCode.APPOINTMENT_INVALID_STATUS, claimNotNeededMessage(appointment));
        }

        if (hasActiveClaimByAnotherUser(appointment, staffUserId, now)) {
            throw new ApiException(ErrorCode.APPOINTMENT_ALREADY_CLAIMED, activeClaimMessage(appointment));
        }

        if (isClaimOwnedByCurrentUser(appointment, staffUserId, now)) {
            return toAdminResponse(appointment);
        }

        LocalDateTime retryNow = LocalDateTime.now();
        int retried = appointmentRepository.claimForProcessing(
                appointmentId,
                staff,
                retryNow,
                claimExpiresAt(retryNow)
        );
        if (retried > 0) {
            return completeClaim(appointmentId, staff);
        }

        Appointment latest = getAppointment(appointmentId);
        LocalDateTime latestNow = LocalDateTime.now();
        if (!isClaimableForProcessing(latest)) {
            throw new ApiException(ErrorCode.APPOINTMENT_INVALID_STATUS, claimNotNeededMessage(latest));
        }
        if (hasActiveClaimByAnotherUser(latest, staffUserId, latestNow)) {
            throw new ApiException(ErrorCode.APPOINTMENT_ALREADY_CLAIMED, activeClaimMessage(latest));
        }
        if (isClaimOwnedByCurrentUser(latest, staffUserId, latestNow)) {
            return toAdminResponse(latest);
        }

        throw new ApiException(
                ErrorCode.APPOINTMENT_CLAIM_CONFLICT,
                "Không thể nhận xử lý lịch hẹn lúc này. Vui lòng tải lại dữ liệu."
        );
    }

    @Transactional
    public AppointmentAdminResponse heartbeat(Long appointmentId, Long staffUserId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireAt = claimExpiresAt(now);

        int updated = appointmentRepository.heartbeatClaim(appointmentId, staffUserId, now, expireAt);
        if (updated == 0) {
            throw new ApiException(ErrorCode.APPOINTMENT_CLAIM_REQUIRED, "Bạn không giữ quyền xử lý lịch hẹn này.");
        }

        Appointment appointment = getAppointment(appointmentId);
        publishProcessingChanged(appointment);

        return toAdminResponse(appointment);
    }

    @Transactional
    public AppointmentAdminResponse releaseClaim(Long appointmentId, Long staffUserId) {
        int updated = appointmentRepository.releaseClaim(appointmentId, staffUserId);
        if (updated == 0) {
            Appointment appointment = getAppointment(appointmentId);
            LocalDateTime now = LocalDateTime.now();
            if (!hasActiveClaim(appointment, now)) {
                if (appointment.getProcessingBy() != null) {
                    clearProcessingClaim(appointment);
                    appointment = appointmentRepository.save(appointment);
                    publishProcessingChanged(appointment);
                }
                return toAdminResponse(appointment);
            }
            throw new ApiException(ErrorCode.APPOINTMENT_CLAIM_REQUIRED, "Bạn không giữ quyền xử lý lịch hẹn này.");
        }

        User staff = getUser(staffUserId);
        Appointment appointment = getAppointment(appointmentId);

        auditLogService.log(staff, "RELEASE_CLAIM", "APPOINTMENT", appointment.getId(), null, snapshotAppointment(appointment));
        publishProcessingChanged(appointment);

        return toAdminResponse(appointment);
    }

    @Transactional
    public AppointmentAdminResponse updateIntake(Long appointmentId, Long staffUserId, UpdateAppointmentIntakeRequest request) {
        Appointment appointment = getAppointment(appointmentId);
        User staff = getUser(staffUserId);

        validateClaimOwnership(appointment, staffUserId);

        if (appointment.getStatus() == AppointmentStatus.CANCELLED
                || appointment.getStatus() == AppointmentStatus.NO_SHOW
                || appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new ApiException(ErrorCode.APPOINTMENT_INVALID_STATUS, "Không thể cập nhật tiếp đón cho lịch hiện tại");
        }

        Map<String, Object> before = snapshotAppointment(appointment);

        if (request.getReasonForVisit() != null) {
            appointment.setReasonForVisit(StringUtil.trimToNull(request.getReasonForVisit()));
        }
        if (request.getVisitType() != null) {
            appointment.setVisitType(normalizeCode(request.getVisitType()));
        }
        if (request.getTriagePriority() != null) {
            String normalizedPriority = TriagePriorityNormalizer.normalizePriority(request.getTriagePriority());
            if (normalizedPriority == null && StringUtil.trimToNull(request.getTriagePriority()) != null) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "Mức ưu tiên khám không hợp lệ");
            }
            appointment.setTriagePriority(normalizedPriority);
        }
        if (request.getTriageNote() != null) {
            appointment.setTriageNote(StringUtil.trimToNull(request.getTriageNote()));
        }
        if (request.getInsuranceNote() != null) {
            appointment.setInsuranceNote(StringUtil.trimToNull(request.getInsuranceNote()));
        }
        if (request.getEmergencyContactName() != null) {
            appointment.setEmergencyContactName(StringUtil.trimToNull(request.getEmergencyContactName()));
        }
        if (request.getEmergencyContactPhone() != null) {
            appointment.setEmergencyContactPhone(StringUtil.trimToNull(request.getEmergencyContactPhone()));
        }
        if (request.getHeightCm() != null) {
            appointment.setHeightCm(request.getHeightCm());
        }
        if (request.getWeightKg() != null) {
            appointment.setWeightKg(request.getWeightKg());
        }
        if (request.getTemperatureC() != null) {
            appointment.setTemperatureC(request.getTemperatureC());
        }
        if (request.getPulse() != null) {
            appointment.setPulse(request.getPulse());
        }
        if (request.getSystolicBp() != null) {
            appointment.setSystolicBp(request.getSystolicBp());
        }
        if (request.getDiastolicBp() != null) {
            appointment.setDiastolicBp(request.getDiastolicBp());
        }
        if (request.getRespiratoryRate() != null) {
            appointment.setRespiratoryRate(request.getRespiratoryRate());
        }
        if (request.getSpo2() != null) {
            appointment.setSpo2(request.getSpo2());
        }
        appointment.setIntakeCompletedBy(staff);
        appointment.setIntakeCompletedAt(LocalDateTime.now());

        Appointment saved = appointmentRepository.save(appointment);

        auditLogService.log(staff, "UPDATE_INTAKE", "APPOINTMENT", saved.getId(), before, snapshotAppointment(saved));
        publishProcessingChanged(saved);

        return toAdminResponse(saved);
    }

    @Transactional
    public AppointmentAdminResponse confirm(Long appointmentId, Long staffUserId, AppointmentConfirmRequest request) {
        Appointment appointment = getAppointment(appointmentId);
        User staff = getUser(staffUserId);
        String note = request != null ? request.getNote() : null;

        validateClaimOwnership(appointment, staffUserId);

        if (appointment.getStatus() != AppointmentStatus.REQUESTED) {
            throw new ApiException(ErrorCode.APPOINTMENT_INVALID_STATUS);
        }

        Map<String, Object> before = snapshotAppointment(appointment);
        AppointmentStatus previousStatus = appointment.getStatus();

        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment.setConfirmedBy(staff);
        appointment.setConfirmedAt(LocalDateTime.now());
        appointment.setContactStatus(AppointmentContactStatus.PHONE_STAFF_VERIFIED);
        appointment.setPatientResponseStatus(AppointmentPatientResponseStatus.NONE);
        TriageReviewAuditPayload triageReviewAudit = applyTriageReview(appointment, staff, request);

        clearProcessingClaim(appointment);

        Appointment saved = appointmentRepository.save(appointment);
        appointmentQueueService.recalculateProjectedQueue(
                saved.getDoctor().getId(),
                saved.getVisitDate(),
                saved.getSession()
        );
        saved = appointmentRepository.findById(saved.getId()).orElse(saved);

        publishSummaryChanged(saved);
        publishAppointmentUpdated(saved, previousStatus);
        publishProcessingChanged(saved);
        notifyDoctorAppointmentConfirmed(saved);

        saveCallLog(saved, staff, com.PrimeCare.PrimeCare.shared.enums.CallOutcome.CONFIRMED, note);
        appointmentStatusHistoryService.record(saved, previousStatus, saved.getStatus(), staff, note);

        auditLogService.log(staff, "CONFIRM_APPOINTMENT", "APPOINTMENT", saved.getId(), before, snapshotAppointment(saved));
        if (triageReviewAudit != null) {
            triageAuditService.recordStaffReview(
                    saved,
                    staff,
                    triageReviewAudit.action(),
                    triageReviewAudit.fromPriority(),
                    triageReviewAudit.toPriority(),
                    triageReviewAudit.reason()
            );
        }

        if (saved.getPatientEmail() != null && !saved.getPatientEmail().isBlank()) {
            appointmentPdfJobService.requestGenerate(saved);
        }

        return toAdminResponse(saved);
    }

    @Transactional
    public AppointmentAdminResponse recordCallResult(
            Long appointmentId,
            Long staffUserId,
            CallOutcome outcome,
            String note,
            boolean sendFallbackEmail
    ) {
        if (outcome == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Kết quả cuộc gọi là bắt buộc.");
        }
        if (outcome == CallOutcome.CONFIRMED) {
            AppointmentConfirmRequest request = new AppointmentConfirmRequest();
            request.setNote(note);
            return confirm(appointmentId, staffUserId, request);
        }

        Appointment appointment = getAppointment(appointmentId);
        User staff = getUser(staffUserId);
        validateClaimOwnership(appointment, staffUserId);

        if (appointment.getStatus() != AppointmentStatus.REQUESTED) {
            throw new ApiException(ErrorCode.APPOINTMENT_INVALID_STATUS);
        }
        if (!isUnreachableOutcome(outcome)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Kết quả cuộc gọi không hỗ trợ fallback email.");
        }

        Map<String, Object> before = snapshotAppointment(appointment);
        appointment.setContactStatus(AppointmentContactStatus.PHONE_UNREACHABLE);
        appointment.setPatientResponseStatus(AppointmentPatientResponseStatus.NEED_PATIENT_RESPONSE);
        Appointment saved = appointmentRepository.save(appointment);
        saveCallLog(saved, staff, outcome, note);
        auditLogService.log(staff, "APPOINTMENT_CALL_UNREACHABLE", "APPOINTMENT", saved.getId(), before, snapshotAppointment(saved));
        publishProcessingChanged(saved);

        if (sendFallbackEmail) {
            appointmentResponseService.sendFallbackEmail(saved, staff, note);
        }

        return toAdminResponse(saved);
    }

    @Transactional
    public AppointmentAdminResponse markWrongContactViolation(Long appointmentId, Long staffUserId, String reason) {
        Appointment appointment = getAppointment(appointmentId);
        User staff = getUser(staffUserId);
        validateClaimOwnership(appointment, staffUserId);
        patientViolationEventService.recordWrongContact(appointment, staff, reason);
        auditLogService.log(staff, "MARK_WRONG_CONTACT_VIOLATION", "APPOINTMENT", appointment.getId(), null, Map.of(
                "appointmentId", appointment.getId(),
                "reason", StringUtil.trimToNull(reason)
        ));
        return toAdminResponse(appointment);
    }

    private TriageReviewAuditPayload applyTriageReview(Appointment appointment, User staff, AppointmentConfirmRequest request) {
        if (request == null) {
            return null;
        }

        String requestedPriority = TriagePriorityNormalizer.normalizePriority(request.getTriagePriority());
        String requestedReviewStatus = TriagePriorityNormalizer.normalizeReviewStatus(request.getTriageReviewStatus());
        String overrideReason = StringUtil.trimToNull(request.getTriageOverrideReason());
        boolean hasPriorityPayload = StringUtil.trimToNull(request.getTriagePriority()) != null;
        boolean hasReviewStatusPayload = StringUtil.trimToNull(request.getTriageReviewStatus()) != null;

        if (hasPriorityPayload && requestedPriority == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Mức ưu tiên khám không hợp lệ");
        }
        if (hasReviewStatusPayload && requestedReviewStatus == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Trạng thái review ưu tiên không hợp lệ");
        }

        if (requestedPriority == null
                && "ACCEPTED".equals(requestedReviewStatus)
                && StringUtil.trimToNull(appointment.getPreTriagePriority()) != null) {
            requestedPriority = TriagePriorityNormalizer.normalizePriority(appointment.getPreTriagePriority());
        }

        if (requestedPriority == null) {
            return null;
        }

        String suggestedPriority = TriagePriorityNormalizer.normalizePriority(appointment.getPreTriagePriority());
        boolean overridden = suggestedPriority != null && !suggestedPriority.equals(requestedPriority);
        if (overridden && overrideReason == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Vui lòng nhập lý do khi override mức ưu tiên gợi ý");
        }

        String reviewStatus = requestedReviewStatus != null
                ? requestedReviewStatus
                : suggestedPriority == null ? "MANUAL" : overridden ? "OVERRIDDEN" : "ACCEPTED";
        if (suggestedPriority == null && "ACCEPTED".equals(reviewStatus)) {
            reviewStatus = "MANUAL";
        }
        if (overridden && !"OVERRIDDEN".equals(reviewStatus)) {
            reviewStatus = "OVERRIDDEN";
        }

        appointment.setTriagePriority(requestedPriority);
        appointment.setTriageNote(StringUtil.trimToNull(request.getTriageNote()));
        appointment.setTriageReviewStatus(reviewStatus);
        appointment.setTriageReviewedBy(staff);
        appointment.setTriageReviewedAt(LocalDateTime.now());
        appointment.setTriageOverrideReason(overrideReason);
        String action = resolveTriageReviewAction(suggestedPriority, requestedPriority, reviewStatus, overridden);
        String auditReason = auditReasonForTriageAction(action, request, overrideReason);
        return new TriageReviewAuditPayload(action, suggestedPriority, requestedPriority, auditReason);
    }

    private String resolveTriageReviewAction(
            String suggestedPriority,
            String requestedPriority,
            String reviewStatus,
            boolean overridden
    ) {
        if (overridden || "OVERRIDDEN".equals(reviewStatus)) {
            return TriageAuditService.ACTION_TRIAGE_OVERRIDDEN;
        }
        if (suggestedPriority == null || "MANUAL".equals(reviewStatus)) {
            return TriageAuditService.ACTION_TRIAGE_MANUAL_SET;
        }
        return TriageAuditService.ACTION_TRIAGE_ACCEPTED;
    }

    private String auditReasonForTriageAction(
            String action,
            AppointmentConfirmRequest request,
            String overrideReason
    ) {
        if (TriageAuditService.ACTION_TRIAGE_OVERRIDDEN.equals(action)) {
            return overrideReason;
        }
        String triageNote = request != null ? StringUtil.trimToNull(request.getTriageNote()) : null;
        if (TriageAuditService.ACTION_TRIAGE_ACCEPTED.equals(action)) {
            return triageNote != null ? triageNote : "Staff chấp nhận gợi ý";
        }
        return triageNote != null ? triageNote : overrideReason;
    }

    @Transactional
    public AppointmentAdminResponse cancel(Long appointmentId, Long staffUserId, String reason) {
        return cancel(appointmentId, staffUserId, reason, AppointmentCancellationReasonType.STAFF_MANUAL, false, false, null);
    }

    @Transactional
    public AppointmentAdminResponse cancel(
            Long appointmentId,
            Long staffUserId,
            String reason,
            AppointmentCancellationReasonType cancellationReasonType,
            boolean enableRecoveryFlow,
            boolean countAsViolation,
            String violationNote
    ) {
        String normalizedReason = StringUtil.trimToNull(reason);
        if (normalizedReason == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, CANCELLATION_REASON_REQUIRED_MESSAGE);
        }

        Appointment appointment = getAppointment(appointmentId);
        User staff = getUser(staffUserId);

        if (appointment.getStatus() == AppointmentStatus.CHECKED_IN && hasActiveEncounter(appointment.getId())) {
            throw new ApiException(
                    ErrorCode.APPOINTMENT_INVALID_STATUS,
                    "Không thể hủy lịch đã check-in khi lần khám đang hoạt động"
            );
        }

        validateClaimOwnership(appointment, staffUserId);

        if (appointment.getStatus() != AppointmentStatus.REQUESTED
                && appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new ApiException(ErrorCode.APPOINTMENT_INVALID_STATUS);
        }

        Map<String, Object> before = snapshotAppointment(appointment);
        AppointmentStatus previousStatus = appointment.getStatus();

        AppointmentCancellationReasonType effectiveReasonType = cancellationReasonType != null
                ? cancellationReasonType
                : AppointmentCancellationReasonType.STAFF_MANUAL;

        if (effectiveReasonType == AppointmentCancellationReasonType.DOCTOR_UNAVAILABLE) {
            if (!enableRecoveryFlow) {
                throw new ApiException(
                        ErrorCode.INVALID_REQUEST,
                        "Hủy do bác sĩ/phòng khám không thể phục vụ cần bật enableRecoveryFlow để hệ thống hỗ trợ dời lịch."
                );
            }
            doctorCancellationRecoveryService.recoverSingleDoctorCancellation(appointment, staff, normalizedReason);
            Appointment saved = getAppointment(appointmentId);
            publishSummaryChanged(saved);
            publishAppointmentUpdated(saved, previousStatus);
            publishProcessingChanged(saved);
            auditLogService.log(staff, "DOCTOR_CANCELLATION_RECOVERY", "APPOINTMENT", saved.getId(), before, snapshotAppointment(saved));
            return toAdminResponse(saved);
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setCancelledBy(staff);
        appointment.setCancelledAt(LocalDateTime.now());
        appointment.setCancelReason(normalizedReason);
        appointment.setCancellationReasonType(effectiveReasonType);
        appointment.setQueueNo(null);

        clearProcessingClaim(appointment);

        Appointment saved = appointmentRepository.save(appointment);
        if (previousStatus == AppointmentStatus.CONFIRMED) {
            appointmentQueueService.recalculateProjectedQueue(
                    saved.getDoctor().getId(),
                    saved.getVisitDate(),
                    saved.getSession()
            );
        }
        saved = appointmentRepository.findById(saved.getId()).orElse(saved);

        publishSummaryChanged(saved);
        publishAppointmentUpdated(saved, previousStatus);
        publishProcessingChanged(saved);

        saveCallLog(saved, staff, com.PrimeCare.PrimeCare.shared.enums.CallOutcome.CANCELLED, normalizedReason);
        appointmentStatusHistoryService.record(saved, previousStatus, saved.getStatus(), staff, normalizedReason);

        auditLogService.log(staff, "CANCEL_APPOINTMENT", "APPOINTMENT", saved.getId(), before, snapshotAppointment(saved));
        patientViolationEventService.recordLateCancelIfEligible(
                saved,
                staff,
                effectiveReasonType,
                countAsViolation,
                violationNote != null ? violationNote : normalizedReason
        );

        return toAdminResponse(saved);
    }

    @Transactional
    public List<AppointmentAdminResponse> recoverDoctorCancellationRange(
            Long staffUserId,
            DoctorCancellationRecoveryRequest request
    ) {
        User staff = getUser(staffUserId);
        validateDoctorCancellationRequest(request);

        List<Appointment> affectedAppointments = appointmentRepository.findAffectedForDoctorRecovery(
                        request.getDoctorId(),
                        request.getStartDate(),
                        request.getEndDate(),
                        EnumSet.of(AppointmentStatus.REQUESTED, AppointmentStatus.CONFIRMED, AppointmentStatus.CHECKED_IN)
                )
                .stream()
                .filter(appointment -> isSessionCovered(
                        appointment.getVisitDate(),
                        appointment.getSession(),
                        request.getStartDate(),
                        request.getEndDate(),
                        request.getStartSession(),
                        request.getEndSession()
                ))
                .toList();

        Map<Long, AppointmentStatus> previousStatuses = affectedAppointments.stream()
                .collect(Collectors.toMap(Appointment::getId, Appointment::getStatus));

        doctorCancellationRecoveryService.recoverDoctorCancellation(affectedAppointments, staff, request.getReason());

        List<AppointmentAdminResponse> responses = new ArrayList<>();
        for (Appointment appointment : affectedAppointments) {
            Appointment saved = getAppointment(appointment.getId());
            publishSummaryChanged(saved);
            publishAppointmentUpdated(saved, previousStatuses.get(saved.getId()));
            publishProcessingChanged(saved);
            responses.add(toAdminResponse(saved));
        }
        return responses;
    }

    private boolean hasActiveEncounter(Long appointmentId) {
        return encounterRepository.findByAppointment_Id(appointmentId)
                                  .map(encounter -> encounter.getStatus() != null
                                          && ACTIVE_ENCOUNTER_STATUSES.contains(encounter.getStatus()))
                                  .orElse(false);
    }

    @Transactional
    public AppointmentAdminResponse checkInByQrToken(String qrToken, Long staffUserId) {
        var payload = appointmentCheckInTokenService.verify(qrToken);

        Appointment appointment = appointmentRepository.findById(payload.appointmentId())
                                                       .orElseThrow(() -> new ApiException(ErrorCode.APPOINTMENT_NOT_FOUND));

        User staff = getUser(staffUserId);

        if (!appointment.getCode().equals(payload.code())) {
            throw new ApiException(ErrorCode.APPOINTMENT_CHECKIN_TOKEN_INVALID, "Mã QR check-in không hợp lệ.");
        }

        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new ApiException(ErrorCode.APPOINTMENT_INVALID_STATUS, "Lịch hẹn không ở trạng thái có thể check-in.");
        }

        if (!LocalDate.now().equals(appointment.getVisitDate())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Chỉ check-in được cho lịch hẹn trong ngày.");
        }

        Map<String, Object> before = snapshotAppointment(appointment);
        AppointmentStatus previousStatus = appointment.getStatus();
        LocalDateTime now = LocalDateTime.now();
        boolean needsArrivalQueueNumber = appointment.getArrivalStatus() != ArrivalStatus.ARRIVED;

        if (needsArrivalQueueNumber && appointment.getReceptionQueueNo() == null) {
            appointment.setReceptionQueueNo(
                    receptionQueueAllocator.allocateNext(
                            appointment.getBranch().getId(),
                            appointment.getVisitDate()
                    )
            );
        }

        AppointmentCheckInState.apply(appointment, staff, now);

        Appointment saved = appointmentRepository.save(appointment);
        publishSummaryChanged(saved);
        publishAppointmentUpdated(saved, previousStatus);
        notifyDoctorAppointmentCheckedIn(saved);
        appointmentStatusHistoryService.record(saved, previousStatus, saved.getStatus(), staff, "Check-in by QR");

        auditLogService.log(staff, "QR_CHECK_IN", "APPOINTMENT", saved.getId(), before, snapshotAppointment(saved));

        return toAdminResponse(saved);
    }

    @Transactional
    public AppointmentAdminResponse markNoShow(Long appointmentId, Long staffUserId, String note) {
        Appointment appointment = getAppointment(appointmentId);
        User staff = getUser(staffUserId);

        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new ApiException(ErrorCode.APPOINTMENT_INVALID_STATUS);
        }
        if (appointment.getArrivalStatus() == ArrivalStatus.ARRIVED || appointment.getCheckedInAt() != null) {
            throw new ApiException(ErrorCode.APPOINTMENT_INVALID_STATUS, "Bệnh nhân đã đến hoặc đã check-in");
        }

        LocalDateTime now = LocalDateTime.now();
        String blockedReason = noShowBlockedReason(appointment, now);
        if (blockedReason != null) {
            throw new ApiException(ErrorCode.APPOINTMENT_TOO_EARLY_FOR_NO_SHOW, blockedReason);
        }

        validateClaimOwnership(appointment, staffUserId);

        Map<String, Object> before = snapshotAppointment(appointment);
        AppointmentStatus previousStatus = appointment.getStatus();

        appointment.setStatus(AppointmentStatus.NO_SHOW);
        appointment.setNoShowMarkedBy(staff);
        appointment.setNoShowMarkedAt(LocalDateTime.now());
        appointment.setNoShowNote(StringUtil.trimToNull(note));
        appointment.setFollowUpPending(true);
        appointment.setFollowUpType(AppointmentFollowUpType.NO_SHOW);

        Appointment saved = appointmentRepository.save(appointment);
        publishSummaryChanged(saved);
        publishAppointmentUpdated(saved, previousStatus);
        notifyStaffNoShowFollowUp(saved);

        saveCallLog(saved, staff, com.PrimeCare.PrimeCare.shared.enums.CallOutcome.NO_SHOW, note);
        appointmentStatusHistoryService.record(saved, previousStatus, saved.getStatus(), staff, note);

        auditLogService.log(staff, "MARK_NO_SHOW", "APPOINTMENT", saved.getId(), before, snapshotAppointment(saved));
        patientViolationEventService.recordNoShow(saved, staff, note);

        return toAdminResponse(saved);
    }

    @Transactional(readOnly = true)
    public AppointmentStatusSummaryResponse summary(
            LocalDate visitDate,
            Long branchId,
            Long doctorId,
            Long specialtyId
    ) {
        List<AppointmentStatusCountRow> rows = appointmentRepository.countSummaryByStatus(
                visitDate,
                branchId,
                doctorId,
                specialtyId,
                STAFF_SUMMARY_STATUSES
        );

        long pending = 0;
        long confirmed = 0;
        long checkedIn = 0;
        long completed = 0;
        long noShow = 0;
        long cancelled = 0;

        for (AppointmentStatusCountRow row : rows) {
            switch (row.getStatus()) {
                case REQUESTED -> pending = row.getCount();
                case CONFIRMED -> confirmed = row.getCount();
                case CHECKED_IN -> checkedIn = row.getCount();
                case COMPLETED -> completed = row.getCount();
                case NO_SHOW -> noShow = row.getCount();
                case CANCELLED -> cancelled = row.getCount();
            }
        }

        return AppointmentStatusSummaryResponse.builder()
                                               .all(pending + confirmed + checkedIn + completed + noShow + cancelled)
                                               .pending(pending)
                                               .confirmed(confirmed)
                                               .checkedIn(checkedIn)
                                               .completed(completed)
                                               .noShow(noShow)
                                               .cancelled(cancelled)
                                               .build();
    }

    private void validateClaimOwnership(Appointment appointment, Long staffUserId) {
        LocalDateTime now = LocalDateTime.now();
        if (hasActiveClaimByAnotherUser(appointment, staffUserId, now)) {
            throw new ApiException(ErrorCode.APPOINTMENT_ALREADY_CLAIMED, activeClaimMessage(appointment));
        }

        if (!isClaimOwnedByCurrentUser(appointment, staffUserId, now)) {
            throw new ApiException(ErrorCode.APPOINTMENT_CLAIM_REQUIRED, "Bạn không giữ quyền xử lý lịch hẹn này.");
        }
    }

    private boolean isClaimableForProcessing(Appointment appointment) {
        if (appointment.getStatus() == AppointmentStatus.REQUESTED
                || appointment.getStatus() == AppointmentStatus.CONFIRMED) {
            return true;
        }
        return Boolean.TRUE.equals(appointment.getFollowUpPending());
    }

    private boolean hasActiveClaimByAnotherUser(Appointment appointment, Long currentUserId, LocalDateTime now) {
        return hasActiveClaim(appointment, now)
                && !appointment.getProcessingBy().getId().equals(currentUserId);
    }

    private boolean isClaimOwnedByCurrentUser(Appointment appointment, Long currentUserId, LocalDateTime now) {
        return hasActiveClaim(appointment, now)
                && appointment.getProcessingBy().getId().equals(currentUserId);
    }

    private boolean hasActiveClaim(Appointment appointment, LocalDateTime now) {
        return appointment.getProcessingBy() != null
                && appointment.getProcessingBy().getId() != null
                && appointment.getProcessingExpiresAt() != null
                && !appointment.getProcessingExpiresAt().isBefore(now);
    }

    private String activeClaimMessage(Appointment appointment) {
        String staffName = appointment.getProcessingBy() != null
                ? resolveUserDisplayName(appointment.getProcessingBy())
                : "nhân viên khác";
        if (staffName == null || staffName.isBlank()) {
            staffName = "nhân viên khác";
        }
        String expiresAt = appointment.getProcessingExpiresAt() != null
                ? appointment.getProcessingExpiresAt().format(CLAIM_EXPIRY_TIME_FORMATTER)
                : "";
        return "Lịch hẹn đang được xử lý bởi " + staffName + " đến " + expiresAt + ".";
    }

    private String claimNotNeededMessage(Appointment appointment) {
        if (appointment.getStatus() == AppointmentStatus.NO_SHOW
                && !Boolean.TRUE.equals(appointment.getFollowUpPending())) {
            return "Follow-up của lịch hẹn này đã được xử lý.";
        }
        return "Trạng thái lịch hẹn hiện tại không cần nhận xử lý.";
    }

    private void clearProcessingClaim(Appointment appointment) {
        appointment.setProcessingBy(null);
        appointment.setProcessingStartedAt(null);
        appointment.setProcessingExpiresAt(null);
    }

    private void publishSummaryChanged(Appointment appointment) {
        if (appointment.getDoctor() != null && appointment.getVisitDate() != null && appointment.getSession() != null) {
            appointmentAvailabilityService.evictAvailabilityCacheForDoctorDateSession(
                    appointment.getDoctor().getId(),
                    appointment.getVisitDate(),
                    appointment.getSession()
            );
        }
        if (appointment.getBranch() != null && appointment.getVisitDate() != null) {
            afterCommitExecutor.execute(() -> realtimeEventPublisher.publishAppointmentSummaryChanged(
                    appointment.getBranch().getId(),
                    appointment.getVisitDate()
            ));
        }
    }

    private void publishProcessingChanged(Appointment appointment) {
        if (appointment.getBranch() != null && appointment.getVisitDate() != null) {
            afterCommitExecutor.execute(() -> realtimeEventPublisher.publishAppointmentProcessingChanged(
                    appointment.getId(),
                    appointment.getBranch().getId(),
                    appointment.getDoctor() != null ? appointment.getDoctor().getId() : null,
                    appointment.getVisitDate(),
                    appointment.getProcessingBy() != null ? appointment.getProcessingBy().getId() : null,
                    appointment.getProcessingBy() != null ? resolveUserDisplayName(appointment.getProcessingBy()) : null,
                    appointment.getProcessingStartedAt() != null ? appointment.getProcessingStartedAt().toString() : null,
                    appointment.getProcessingExpiresAt() != null ? appointment.getProcessingExpiresAt().toString() : null
            ));
        }
    }

    private void publishAppointmentUpdated(Appointment appointment, AppointmentStatus previousStatus) {
        if (appointment.getBranch() != null && appointment.getVisitDate() != null) {
            afterCommitExecutor.execute(() -> realtimeEventPublisher.publishAppointmentUpdated(
                    appointment.getId(),
                    appointment.getBranch().getId(),
                    appointment.getDoctor() != null ? appointment.getDoctor().getId() : null,
                    appointment.getVisitDate(),
                    appointment.getSession() != null ? appointment.getSession().name() : null,
                    previousStatus != null ? previousStatus.name() : null,
                    appointment.getStatus() != null ? appointment.getStatus().name() : null,
                    appointment.getArrivalStatus() != null ? appointment.getArrivalStatus().name() : null,
                    appointment.getQueueNo(),
                    appointment.getReceptionQueueNo(),
                    appointment.getArrivedAt() != null ? appointment.getArrivedAt().toString() : null,
                    appointment.getArrivedBy() != null ? resolveUserDisplayName(appointment.getArrivedBy()) : null,
                    appointment.getCheckedInAt() != null ? appointment.getCheckedInAt().toString() : null,
                    appointment.getCheckedInBy() != null ? resolveUserDisplayName(appointment.getCheckedInBy()) : null,
                    appointment.getConfirmedAt() != null ? appointment.getConfirmedAt().toString() : null,
                    appointment.getConfirmedBy() != null ? resolveUserDisplayName(appointment.getConfirmedBy()) : null
            ));
        }
    }

    private Appointment getAppointment(Long appointmentId) {
        return appointmentRepository.findById(appointmentId)
                                    .orElseThrow(() -> new ApiException(ErrorCode.APPOINTMENT_NOT_FOUND));
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                             .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
    }

    private LocalDateTime claimExpiresAt(LocalDateTime now) {
        return now.plusMinutes(Math.max(1, claimTtlMinutes));
    }

    private LocalTime overdueCutoffTime(LocalDateTime now) {
        if (!now.toLocalDate().equals(LocalDate.now())) {
            return LocalTime.MIN;
        }
        return now.toLocalTime();
    }

    private FollowUpFilter followUpFilter(
            FollowUpQueueCategory category,
            AppointmentFollowUpType followUpType
    ) {
        if (followUpType != null) {
            return new FollowUpFilter(
                    EnumSet.of(followUpType),
                    true,
                    followUpType == AppointmentFollowUpType.NO_SHOW
            );
        }

        FollowUpQueueCategory effectiveCategory = category != null ? category : FollowUpQueueCategory.ALL;
        return new FollowUpFilter(
                effectiveCategory.followUpTypes(),
                effectiveCategory.filtersTypes(),
                effectiveCategory.includesLegacyNoShow()
        );
    }

    private Map<Long, AppointmentSlotHold> latestSlotHoldsByAppointmentId(List<Appointment> appointments) {
        if (appointments == null || appointments.isEmpty()) {
            return Map.of();
        }

        List<Long> appointmentIds = appointments.stream()
                                                .map(Appointment::getId)
                                                .filter(id -> id != null)
                                                .toList();
        if (appointmentIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, AppointmentSlotHold> holdsByAppointmentId = new LinkedHashMap<>();
        for (AppointmentSlotHold hold : appointmentSlotHoldRepository.findLatestByOriginalAppointmentIds(appointmentIds)) {
            if (hold.getOriginalAppointment() != null && hold.getOriginalAppointment().getId() != null) {
                holdsByAppointmentId.putIfAbsent(hold.getOriginalAppointment().getId(), hold);
            }
        }
        return holdsByAppointmentId;
    }

    private FollowUpQueueItemResponse toFollowUpQueueItemResponse(
            Appointment appointment,
            AppointmentSlotHold hold
    ) {
        return FollowUpQueueItemResponse.builder()
                                        .id(appointment.getId())
                                        .appointmentId(appointment.getId())
                                        .appointmentCode(appointment.getCode())
                                        .followUpType(resolveFollowUpType(appointment))
                                        .followUpCategory(resolveFollowUpCategory(appointment))
                                        .patientFullName(appointment.getPatientFullName())
                                        .patientPhone(appointment.getPatientPhone())
                                        .patientEmail(appointment.getPatientEmail())
                                        .doctorName(appointment.getDoctor() != null ? appointment.getDoctor().getFullName() : null)
                                        .specialtyName(appointment.getSpecialty() != null ? appointment.getSpecialty().getNameVn() : null)
                                        .originalVisitDate(appointment.getVisitDate())
                                        .originalSlotStart(appointment.getEtaStart())
                                        .originalSlotEnd(appointment.getEtaEnd())
                                        .heldDoctorName(hold != null && hold.getDoctor() != null ? hold.getDoctor().getFullName() : null)
                                        .heldVisitDate(hold != null ? hold.getVisitDate() : null)
                                        .heldSlotStart(hold != null ? hold.getSlotStart() : null)
                                        .heldSlotEnd(hold != null ? hold.getSlotEnd() : null)
                                        .holdStatus(hold != null && hold.getStatus() != null ? hold.getStatus().name() : null)
                                        .expiresAt(hold != null ? hold.getExpiresAt() : null)
                                        .createdAt(followUpCreatedAt(appointment))
                                        .followUpPending(Boolean.TRUE.equals(appointment.getFollowUpPending()))
                                        .contactStatus(appointment.getContactStatus() != null ? appointment.getContactStatus().name() : null)
                                        .patientResponseStatus(appointment.getPatientResponseStatus() != null ? appointment.getPatientResponseStatus().name() : null)
                                        .build();
    }

    private String resolveFollowUpType(Appointment appointment) {
        if (appointment.getFollowUpType() != null) {
            return appointment.getFollowUpType().name();
        }
        if (appointment.getStatus() == AppointmentStatus.NO_SHOW) {
            return AppointmentFollowUpType.NO_SHOW.name();
        }
        return null;
    }

    private String resolveFollowUpCategory(Appointment appointment) {
        AppointmentFollowUpType followUpType = appointment.getFollowUpType();
        if (followUpType == null && appointment.getStatus() == AppointmentStatus.NO_SHOW) {
            followUpType = AppointmentFollowUpType.NO_SHOW;
        }

        FollowUpQueueCategory category = FollowUpQueueCategory.categoryOf(followUpType);
        return category != null ? category.name() : null;
    }

    private LocalDateTime followUpCreatedAt(Appointment appointment) {
        if (appointment.getNoShowMarkedAt() != null) {
            return appointment.getNoShowMarkedAt();
        }
        if (appointment.getUpdatedAt() != null) {
            return appointment.getUpdatedAt();
        }
        return appointment.getCreatedAt();
    }

    private AppointmentAdminResponse toAdminResponse(Appointment entity) {
        return AppointmentAdminResponse.builder()
                                       .id(entity.getId())
                                       .code(entity.getCode())
                                       .status(entity.getStatus())
                                       .branchId(entity.getBranch().getId())
                                       .branchName(entity.getBranch().getNameVn())
                                       .specialtyId(entity.getSpecialty().getId())
                                       .specialtyName(entity.getSpecialty().getNameVn())
                                       .doctorId(entity.getDoctor().getId())
                                       .doctorName(entity.getDoctor().getFullName())
                                       .visitDate(entity.getVisitDate())
                                       .session(entity.getSession())
                                       .queueNo(entity.getQueueNo())
                                       .slotMinutes(entity.getSlotMinutes())
                                       .etaStart(entity.getEtaStart())
                                       .etaEnd(entity.getEtaEnd())
                                       .patientFullName(entity.getPatientFullName())
                                       .patientPhone(entity.getPatientPhone())
                                       .patientEmail(entity.getPatientEmail())
                                       .contactStatus(entity.getContactStatus())
                                       .patientResponseStatus(entity.getPatientResponseStatus())
                                       .reasonForVisit(entity.getReasonForVisit())
                                       .visitType(entity.getVisitType())
                                       .triagePriority(entity.getTriagePriority())
                                       .triageNote(entity.getTriageNote())
                                       .preTriageLevel(entity.getPreTriageLevel())
                                       .preTriagePriority(entity.getPreTriagePriority())
                                       .preTriageFlags(TriageJsonSupport.readStringList(entity.getPreTriageFlagsJson()))
                                       .preTriageReasons(TriageJsonSupport.readStringList(entity.getPreTriageReasonsJson()))
                                       .preTriageSummary(entity.getPreTriageSummary())
                                       .preTriageAssessedAt(entity.getPreTriageAssessedAt())
                                       .symptomOnset(entity.getSymptomOnset())
                                       .chronicConditions(TriageJsonSupport.readStringList(entity.getChronicConditionsJson()))
                                       .chronicConditionOthers(TriageJsonSupport.readStringList(entity.getChronicConditionOthersJson()))
                                       .functionalImpact(entity.getFunctionalImpact())
                                       .redFlagSelections(TriageJsonSupport.readStringList(entity.getRedFlagSelectionsJson()))
                                       .preTriageMatchedTerms(TriageJsonSupport.readObjectList(entity.getPreTriageMatchedTermsJson(), TriageMatchedTerm.class))
                                       .preTriageMatchedRules(TriageJsonSupport.readObjectList(entity.getPreTriageMatchedRulesJson(), TriageMatchedRule.class))
                                       .preTriageSource(entity.getPreTriageSource())
                                       .preTriageConfidenceLevel(entity.getPreTriageConfidenceLevel())
                                       .preTriageKnowledgeBaseVersion(entity.getPreTriageKnowledgeBaseVersion())
                                       .preTriageRulesetVersion(entity.getPreTriageRulesetVersion())
                                       .preTriageAiModelVersion(entity.getPreTriageAiModelVersion())
                                       .triageReviewStatus(entity.getTriageReviewStatus())
                                       .triageReviewedAt(entity.getTriageReviewedAt())
                                       .triageReviewedByName(entity.getTriageReviewedBy() != null ? resolveUserDisplayName(entity.getTriageReviewedBy()) : null)
                                       .triageOverrideReason(entity.getTriageOverrideReason())
                                       .receptionPriority(entity.getTriagePriority())
                                       .receptionNote(entity.getTriageNote())
                                       .insuranceNote(entity.getInsuranceNote())
                                       .emergencyContactName(entity.getEmergencyContactName())
                                       .emergencyContactPhone(entity.getEmergencyContactPhone())
                                       .heightCm(entity.getHeightCm())
                                       .weightKg(entity.getWeightKg())
                                       .temperatureC(entity.getTemperatureC())
                                       .pulse(entity.getPulse())
                                       .systolicBp(entity.getSystolicBp())
                                       .diastolicBp(entity.getDiastolicBp())
                                       .respiratoryRate(entity.getRespiratoryRate())
                                       .spo2(entity.getSpo2())
                                       .intakeCompletedAt(entity.getIntakeCompletedAt())
                                       .intakeCompletedByName(entity.getIntakeCompletedBy() != null ? resolveUserDisplayName(entity.getIntakeCompletedBy()) : null)
                                       .processingById(entity.getProcessingBy() != null ? entity.getProcessingBy().getId() : null)
                                       .processingByName(entity.getProcessingBy() != null ? resolveUserDisplayName(entity.getProcessingBy()) : null)
                                       .processingStartedAt(entity.getProcessingStartedAt())
                                       .processingExpiresAt(entity.getProcessingExpiresAt())
                                       .confirmedAt(entity.getConfirmedAt())
                                       .sourceType(entity.getSourceType())
                                       .arrivalStatus(entity.getArrivalStatus())
                                       .receptionQueueNo(entity.getReceptionQueueNo())
                                       .arrivedAt(entity.getArrivedAt())
                                       .patientId(entity.getPatient() != null ? entity.getPatient().getId() : null)
                                       .arrivedByName(entity.getArrivedBy() != null ? resolveUserDisplayName(entity.getArrivedBy()) : null)
                                       .confirmedByName(entity.getConfirmedBy() != null ? resolveUserDisplayName(entity.getConfirmedBy()) : null)
                                       .checkedInAt(entity.getCheckedInAt())
                                       .checkedInByName(entity.getCheckedInBy() != null ? resolveUserDisplayName(entity.getCheckedInBy()) : null)
                                       .checkedInLate(Boolean.TRUE.equals(entity.getCheckedInLate()))
                                       .lateMinutes(entity.getLateMinutes() != null ? entity.getLateMinutes() : 0)
                                       .noShowMarkedAt(entity.getNoShowMarkedAt())
                                       .noShowMarkedByName(entity.getNoShowMarkedBy() != null ? resolveUserDisplayName(entity.getNoShowMarkedBy()) : null)
                                       .noShowNote(entity.getNoShowNote())
                                       .cancellationReasonType(entity.getCancellationReasonType() != null ? entity.getCancellationReasonType().name() : null)
                                       .canMarkNoShow(noShowBlockedReason(entity) == null)
                                       .noShowEligibleAt(noShowEligibleAt(entity))
                                       .noShowBlockedReason(noShowBlockedReason(entity))
                                       .followUpPending(Boolean.TRUE.equals(entity.getFollowUpPending()))
                                       .followUpType(entity.getFollowUpType() != null ? entity.getFollowUpType().name() : null)
                                       .rescheduledFromAppointmentId(entity.getRescheduledFromAppointment() != null ? entity.getRescheduledFromAppointment().getId() : null)
                                       .rescheduledToAppointmentId(resolveRescheduledToAppointmentId(entity.getId()))
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

    private Long resolveRescheduledToAppointmentId(Long appointmentId) {
        if (appointmentId == null) {
            return null;
        }
        return appointmentRepository.findFirstByRescheduledFromAppointment_IdOrderByCreatedAtDesc(appointmentId)
                                    .map(Appointment::getId)
                                    .orElse(null);
    }

    private LocalDateTime noShowEligibleAt(Appointment appointment) {
        return AppointmentTimingPolicy.noShowEligibleAt(appointment);
    }

    private String noShowBlockedReason(Appointment appointment) {
        return noShowBlockedReason(appointment, LocalDateTime.now());
    }

    private String noShowBlockedReason(Appointment appointment, LocalDateTime now) {
        return AppointmentTimingPolicy.noShowBlockedReason(appointment, now);
    }

    private void notifyStaffNoShowFollowUp(Appointment appointment) {
        String message = "Lịch hẹn " + appointment.getCode() + " của bệnh nhân "
                + appointment.getPatientFullName() + " đã được đánh dấu no-show và cần follow-up.";
        internalNotificationService.notifyRole(
                UserRole.STAFF,
                "APPOINTMENT_NO_SHOW_FOLLOW_UP",
                InternalNotificationService.SEVERITY_WARNING,
                "Lịch hẹn cần follow-up no-show",
                message,
                "/app/appointments/" + appointment.getId() + "/process",
                "APPOINTMENT",
                appointment.getId()
        );
        internalNotificationService.notifyRole(
                UserRole.OPERATIONS_ADMIN,
                "APPOINTMENT_NO_SHOW_FOLLOW_UP",
                InternalNotificationService.SEVERITY_WARNING,
                "Lịch hẹn cần follow-up no-show",
                message,
                "/app/appointments/" + appointment.getId() + "/process",
                "APPOINTMENT",
                appointment.getId()
        );
    }

    private void notifyStaffNoShowResolved(Appointment appointment) {
        String message = "Follow-up no-show của lịch hẹn " + appointment.getCode() + " đã được xử lý.";
        internalNotificationService.notifyRole(
                UserRole.STAFF,
                "APPOINTMENT_NO_SHOW_RESOLVED",
                InternalNotificationService.SEVERITY_INFO,
                "Đã đóng follow-up no-show",
                message,
                "/app/appointments/" + appointment.getId() + "/process",
                "APPOINTMENT",
                appointment.getId()
        );
    }

    private void notifyStaffFollowUpResolved(Appointment appointment) {
        String message = "Follow-up của lịch hẹn " + appointment.getCode() + " đã được xử lý.";
        internalNotificationService.notifyRole(
                UserRole.STAFF,
                "APPOINTMENT_FOLLOW_UP_RESOLVED",
                InternalNotificationService.SEVERITY_INFO,
                "Đã đóng follow-up",
                message,
                "/app/appointments/" + appointment.getId() + "/process",
                "APPOINTMENT",
                appointment.getId()
        );
    }

    private void notifyDoctorAppointmentConfirmed(Appointment appointment) {
        if (appointment.getDoctor() == null || appointment.getDoctor().getId() == null) {
            return;
        }

        userRepository.findByDoctorProfile_Id(appointment.getDoctor().getId())
                      .ifPresent(doctorUser -> internalNotificationService.notifyUser(
                              doctorUser.getId(),
                              "DOCTOR_APPOINTMENT_CONFIRMED",
                              InternalNotificationService.SEVERITY_INFO,
                              "Lịch hẹn mới đã được xác nhận",
                              "Bệnh nhân " + appointment.getPatientFullName() + " đã được xác nhận lịch hẹn.",
                              "/app/doctor/appointments",
                              "APPOINTMENT",
                              appointment.getId()
                      ));
    }

    private void notifyDoctorAppointmentCheckedIn(Appointment appointment) {
        if (appointment.getDoctor() == null || appointment.getDoctor().getId() == null) {
            return;
        }

        userRepository.findByDoctorProfile_Id(appointment.getDoctor().getId())
                      .ifPresent(doctorUser -> internalNotificationService.notifyUser(
                              doctorUser.getId(),
                              "PATIENT_CHECKED_IN",
                              InternalNotificationService.SEVERITY_INFO,
                              "Bệnh nhân đã check-in",
                              "Bệnh nhân " + appointment.getPatientFullName() + " đã check-in và sẵn sàng khám.",
                              "/app/doctor/appointments",
                              "APPOINTMENT",
                              appointment.getId()
                      ));
    }

    private void saveCallLog(Appointment appointment, User staff, com.PrimeCare.PrimeCare.shared.enums.CallOutcome outcome, String note) {
        appointmentCallLogRepository.save(
                com.PrimeCare.PrimeCare.modules.appointment.entity.AppointmentCallLog.builder()
                                                                                     .appointment(appointment)
                                                                                     .staff(staff)
                                                                                     .outcome(outcome)
                                                                                     .note(StringUtil.trimToNull(note))
                                                                                     .build()
        );
    }

    private boolean isUnreachableOutcome(CallOutcome outcome) {
        return outcome == CallOutcome.NO_ANSWER
                || outcome == CallOutcome.BUSY
                || outcome == CallOutcome.WRONG_NUMBER;
    }

    @Transactional(readOnly = true)
    public AppointmentAvailabilityResponse getRescheduleAvailability(
            Long appointmentId,
            LocalDate visitDate,
            com.PrimeCare.PrimeCare.shared.enums.BranchSessionType session,
            boolean onlyAvailable
    ) {
        Appointment appointment = getAppointment(appointmentId);
        return appointmentAvailabilityService.getAvailabilityExcludingAppointment(
                appointment.getBranch().getId(),
                appointment.getSpecialty().getId(),
                appointment.getDoctor().getId(),
                visitDate,
                session,
                onlyAvailable,
                appointment.getId()
        );
    }

    @Transactional
    public AppointmentAdminResponse reschedule(
            Long appointmentId,
            Long staffUserId,
            com.PrimeCare.PrimeCare.modules.appointment.dto.request.AppointmentRescheduleRequest request
    ) {
        Appointment oldAppointment = getAppointment(appointmentId);
        User staff = getUser(staffUserId);

        Map<String, Object> oldBefore = snapshotAppointment(oldAppointment);
        AppointmentStatus previousStatus = oldAppointment.getStatus();

        boolean noShowFlow = oldAppointment.getStatus() == AppointmentStatus.NO_SHOW;
        boolean editableFlow = oldAppointment.getStatus() == AppointmentStatus.REQUESTED
                || oldAppointment.getStatus() == AppointmentStatus.CONFIRMED;

        if (!noShowFlow && !editableFlow) {
            throw new ApiException(
                    ErrorCode.APPOINTMENT_INVALID_STATUS,
                    "Chỉ lịch REQUESTED, CONFIRMED hoặc NO_SHOW mới được dời lịch"
            );
        }

        if (noShowFlow && !Boolean.TRUE.equals(oldAppointment.getFollowUpPending())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Follow-up của lịch hẹn này đã được xử lý.");
        }

        if (editableFlow || noShowFlow) {
            validateClaimOwnership(oldAppointment, staffUserId);
        }

        DoctorWorkSchedule lockedSchedule = doctorWorkScheduleRepository
                .findWithLockByDoctor_IdAndWorkDateAndSession(
                        oldAppointment.getDoctor().getId(),
                        request.getVisitDate(),
                        request.getSession()
                )
                .orElseThrow(() -> new ApiException(
                        ErrorCode.APPOINTMENT_SLOT_NOT_AVAILABLE,
                        "Bác sĩ không có lịch làm việc trong buổi đã chọn"
                ));

        BranchSession branchSession = branchSessionRepository
                .findByBranch_IdAndSessionAndStatus(
                        oldAppointment.getBranch().getId(),
                        request.getSession(),
                        "ACTIVE"
                )
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REQUEST, "Chi nhánh chưa mở buổi khám này"));

        AppointmentAvailabilityResponse rescheduleAvailability = appointmentAvailabilityService.getAvailabilityExcludingAppointment(
                oldAppointment.getBranch().getId(),
                oldAppointment.getSpecialty().getId(),
                oldAppointment.getDoctor().getId(),
                request.getVisitDate(),
                request.getSession(),
                false,
                oldAppointment.getId()
        );

        var selectedSlot = rescheduleAvailability.getSlots().stream()
                                       .filter(slot -> slot.getStartTime().equals(request.getSlotStart()))
                                       .findFirst()
                                       .orElseThrow(() -> new ApiException(
                                               ErrorCode.APPOINTMENT_SLOT_NOT_AVAILABLE,
                                               "Khung giờ mới không nằm trong danh sách khả dụng"
                                       ));

        appointmentAvailabilityService.assertSlotStillBookable(request.getVisitDate(), request.getSlotStart());

        if (!selectedSlot.isAvailable()) {
            throw new ApiException(
                    ErrorCode.APPOINTMENT_SLOT_NOT_AVAILABLE,
                    "Khung giờ đã có lịch hẹn. Vui lòng chọn khung giờ khác."
            );
        }

        int slotCapacity = appointmentAvailabilityService.resolveSlotCapacity(branchSession);

        var blockingAppointments = appointmentRepository.findByDoctor_IdAndVisitDateAndSessionAndStatusInOrderByEtaStartAsc(
                oldAppointment.getDoctor().getId(),
                request.getVisitDate(),
                request.getSession(),
                java.util.EnumSet.of(
                        AppointmentStatus.REQUESTED,
                        AppointmentStatus.CONFIRMED,
                        AppointmentStatus.CHECKED_IN
                )
        ).stream()
         .filter(existing -> !oldAppointment.getId().equals(existing.getId()))
         .toList();

        long bookingCount = appointmentAvailabilityService.countOverlappingAppointments(
                blockingAppointments,
                selectedSlot.getStartTime(),
                selectedSlot.getEndTime()
        );

        if (bookingCount >= slotCapacity) {
            throw new ApiException(
                    ErrorCode.APPOINTMENT_SLOT_NOT_AVAILABLE,
                    "Khung giờ đã có lịch hẹn. Vui lòng chọn khung giờ khác."
            );
        }

        Appointment newAppointment = Appointment.builder()
                                                .code(appointmentCodeGenerator.generate(request.getVisitDate(), oldAppointment.getDoctor().getId(), request.getSlotStart()))
                                                .status(AppointmentStatus.CONFIRMED)
                                                .branch(oldAppointment.getBranch())
                                                .specialty(oldAppointment.getSpecialty())
                                                .doctor(oldAppointment.getDoctor())
                                                .visitDate(request.getVisitDate())
                                                .session(request.getSession())
                                                .queueNo(null)
                                                .slotMinutes(rescheduleAvailability.getSlotMinutes())
                                                .etaStart(selectedSlot.getStartTime())
                                                .etaEnd(selectedSlot.getEndTime())
                                                .patientFullName(oldAppointment.getPatientFullName())
                                                .patientPhone(oldAppointment.getPatientPhone())
                                                .patientEmail(oldAppointment.getPatientEmail())
                                                .patientDob(oldAppointment.getPatientDob())
                                                .patientGender(oldAppointment.getPatientGender())
                                                .patientNote(oldAppointment.getPatientNote())
                                                .reasonForVisit(oldAppointment.getReasonForVisit())
                                                .visitType(oldAppointment.getVisitType())
                                                .triagePriority(oldAppointment.getTriagePriority())
                                                .triageNote(oldAppointment.getTriageNote())
                                                .preTriageLevel(oldAppointment.getPreTriageLevel())
                                                .preTriagePriority(oldAppointment.getPreTriagePriority())
                                                .preTriageFlagsJson(oldAppointment.getPreTriageFlagsJson())
                                                .preTriageReasonsJson(oldAppointment.getPreTriageReasonsJson())
                                                .preTriageSummary(oldAppointment.getPreTriageSummary())
                                                .preTriageAssessedAt(oldAppointment.getPreTriageAssessedAt())
                                                .symptomOnset(oldAppointment.getSymptomOnset())
                                                .chronicConditionsJson(oldAppointment.getChronicConditionsJson())
                                                .chronicConditionOthersJson(oldAppointment.getChronicConditionOthersJson())
                                                .functionalImpact(oldAppointment.getFunctionalImpact())
                                                .redFlagSelectionsJson(oldAppointment.getRedFlagSelectionsJson())
                                                .preTriageMatchedTermsJson(oldAppointment.getPreTriageMatchedTermsJson())
                                                .preTriageMatchedRulesJson(oldAppointment.getPreTriageMatchedRulesJson())
                                                .preTriageSource(oldAppointment.getPreTriageSource())
                                                .preTriageConfidenceLevel(oldAppointment.getPreTriageConfidenceLevel())
                                                .preTriageKnowledgeBaseVersion(oldAppointment.getPreTriageKnowledgeBaseVersion())
                                                .preTriageRulesetVersion(oldAppointment.getPreTriageRulesetVersion())
                                                .preTriageAiModelVersion(oldAppointment.getPreTriageAiModelVersion())
                                                .triageReviewStatus(oldAppointment.getTriageReviewStatus())
                                                .triageReviewedBy(oldAppointment.getTriageReviewedBy())
                                                .triageReviewedAt(oldAppointment.getTriageReviewedAt())
                                                .triageOverrideReason(oldAppointment.getTriageOverrideReason())
                                                .insuranceNote(oldAppointment.getInsuranceNote())
                                                .emergencyContactName(oldAppointment.getEmergencyContactName())
                                                .emergencyContactPhone(oldAppointment.getEmergencyContactPhone())
                                                .heightCm(oldAppointment.getHeightCm())
                                                .weightKg(oldAppointment.getWeightKg())
                                                .temperatureC(oldAppointment.getTemperatureC())
                                                .pulse(oldAppointment.getPulse())
                                                .systolicBp(oldAppointment.getSystolicBp())
                                                .diastolicBp(oldAppointment.getDiastolicBp())
                                                .respiratoryRate(oldAppointment.getRespiratoryRate())
                                                .spo2(oldAppointment.getSpo2())
                                                .intakeCompletedBy(oldAppointment.getIntakeCompletedBy())
                                                .intakeCompletedAt(oldAppointment.getIntakeCompletedAt())
                                                .confirmedBy(staff)
                                                .sourceType(entitySourceOrFallback(oldAppointment))
                                                .arrivalStatus(ArrivalStatus.NOT_ARRIVED)
                                                .receptionQueueNo(null)
                                                .arrivedAt(null)
                                                .arrivedBy(null)
                                                .patient(oldAppointment.getPatient())
                                                .confirmedAt(LocalDateTime.now())
                                                .rescheduledFromAppointment(oldAppointment)
                                                .rescheduledBy(staff)
                                                .rescheduledAt(LocalDateTime.now())
                                                .build();

        Appointment saved = appointmentRepository.save(newAppointment);
        appointmentQueueService.recalculateProjectedQueue(
                saved.getDoctor().getId(),
                saved.getVisitDate(),
                saved.getSession()
        );
        saved = appointmentRepository.findById(saved.getId()).orElse(saved);
        publishSummaryChanged(saved);
        publishAppointmentUpdated(saved, null);
        notifyDoctorAppointmentConfirmed(saved);
        appointmentStatusHistoryService.record(saved, null, saved.getStatus(), staff, "Tạo lịch mới từ lịch dời");

        auditLogService.log(staff, "RESCHEDULE_TARGET", "APPOINTMENT", saved.getId(), null, snapshotAppointment(saved));

        if (editableFlow) {
            oldAppointment.setStatus(AppointmentStatus.CANCELLED);
            oldAppointment.setCancelledBy(staff);
            oldAppointment.setCancelledAt(LocalDateTime.now());
            oldAppointment.setCancelReason(StringUtil.trimToNull(request.getNote()));
            oldAppointment.setQueueNo(null);
            clearProcessingClaim(oldAppointment);
            appointmentRepository.save(oldAppointment);
            if (previousStatus == AppointmentStatus.CONFIRMED) {
                appointmentQueueService.recalculateProjectedQueue(
                        oldAppointment.getDoctor().getId(),
                        oldAppointment.getVisitDate(),
                        oldAppointment.getSession()
                );
            }
            publishSummaryChanged(oldAppointment);
            publishAppointmentUpdated(oldAppointment, previousStatus);
            publishProcessingChanged(oldAppointment);
            appointmentStatusHistoryService.record(oldAppointment, previousStatus, oldAppointment.getStatus(), staff, request.getNote());
        } else {
            oldAppointment.setFollowUpPending(false);
            clearProcessingClaim(oldAppointment);
            appointmentRepository.save(oldAppointment);
            publishSummaryChanged(oldAppointment);
            publishAppointmentUpdated(oldAppointment, previousStatus);
            publishProcessingChanged(oldAppointment);
            appointmentStatusHistoryService.record(oldAppointment, previousStatus, oldAppointment.getStatus(), staff, request.getNote());
        }

        auditLogService.log(staff, "RESCHEDULE_SOURCE", "APPOINTMENT", oldAppointment.getId(), oldBefore, snapshotAppointment(oldAppointment));

        saveCallLog(oldAppointment, staff, com.PrimeCare.PrimeCare.shared.enums.CallOutcome.RESCHEDULE, request.getNote());
        saveCallLog(saved, staff, com.PrimeCare.PrimeCare.shared.enums.CallOutcome.CONFIRMED, "Tạo lịch mới từ lịch dời");

        if (saved.getPatientEmail() != null && !saved.getPatientEmail().isBlank()) {
            appointmentPdfJobService.requestGenerate(saved);
        }

        return toAdminResponse(saved);
    }

    @Transactional
    public AppointmentAdminResponse rescheduleFromNoShow(
            Long appointmentId,
            Long staffUserId,
            com.PrimeCare.PrimeCare.modules.appointment.dto.request.AppointmentRescheduleRequest request
    ) {
        return reschedule(appointmentId, staffUserId, request);
    }

    @Transactional
    public AppointmentAdminResponse resolveNoShowAsClosed(Long appointmentId, Long staffUserId, String note) {
        Appointment appointment = getAppointment(appointmentId);
        User staff = getUser(staffUserId);

        if (appointment.getStatus() != AppointmentStatus.NO_SHOW) {
            throw new ApiException(ErrorCode.APPOINTMENT_INVALID_STATUS, "Chỉ lịch no-show mới được đóng follow-up");
        }

        if (!Boolean.TRUE.equals(appointment.getFollowUpPending())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Follow-up của lịch hẹn này đã được xử lý.");
        }

        validateClaimOwnership(appointment, staffUserId);

        Map<String, Object> before = snapshotAppointment(appointment);
        AppointmentStatus previousStatus = appointment.getStatus();

        appointment.setFollowUpPending(false);
        clearProcessingClaim(appointment);

        Appointment saved = appointmentRepository.save(appointment);
        saveCallLog(
                saved,
                staff,
                com.PrimeCare.PrimeCare.shared.enums.CallOutcome.CANCELLED,
                note != null && !note.isBlank() ? note : "Khách xác nhận không đến nữa sau khi follow-up no-show"
        );
        publishSummaryChanged(saved);
        publishAppointmentUpdated(saved, previousStatus);
        publishProcessingChanged(saved);
        appointmentStatusHistoryService.record(saved, previousStatus, saved.getStatus(), staff, note);
        notifyStaffNoShowResolved(saved);

        auditLogService.log(staff, "RESOLVE_NO_SHOW", "APPOINTMENT", saved.getId(), before, snapshotAppointment(saved));

        return toAdminResponse(saved);
    }

    @Transactional
    public AppointmentAdminResponse resolveFollowUpAsClosed(Long appointmentId, Long staffUserId, String note) {
        Appointment appointment = getAppointment(appointmentId);
        User staff = getUser(staffUserId);

        if (!Boolean.TRUE.equals(appointment.getFollowUpPending())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Follow-up của lịch hẹn này đã được xử lý.");
        }

        validateClaimOwnership(appointment, staffUserId);

        Map<String, Object> before = snapshotAppointment(appointment);
        AppointmentStatus previousStatus = appointment.getStatus();

        appointment.setFollowUpPending(false);
        clearProcessingClaim(appointment);

        Appointment saved = appointmentRepository.save(appointment);
        saveCallLog(
                saved,
                staff,
                com.PrimeCare.PrimeCare.shared.enums.CallOutcome.CANCELLED,
                note != null && !note.isBlank() ? note : "Đóng follow-up liên hệ bệnh nhân"
        );
        publishSummaryChanged(saved);
        publishAppointmentUpdated(saved, previousStatus);
        publishProcessingChanged(saved);
        appointmentStatusHistoryService.record(saved, previousStatus, saved.getStatus(), staff, note);
        notifyStaffFollowUpResolved(saved);

        auditLogService.log(staff, "RESOLVE_FOLLOW_UP", "APPOINTMENT", saved.getId(), before, snapshotAppointment(saved));

        return toAdminResponse(saved);
    }

    private void validateDoctorCancellationRequest(DoctorCancellationRecoveryRequest request) {
        if (request == null
                || request.getDoctorId() == null
                || request.getStartDate() == null
                || request.getEndDate() == null
                || request.getStartSession() == null
                || request.getEndSession() == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Thiếu thông tin khoảng hủy lịch của bác sĩ.");
        }
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Ngày bắt đầu không được sau ngày kết thúc.");
        }
        if (request.getStartDate().equals(request.getEndDate())
                && request.getStartSession().ordinal() > request.getEndSession().ordinal()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Buổi bắt đầu phải trước hoặc bằng buổi kết thúc.");
        }
    }

    private boolean isSessionCovered(
            LocalDate targetDate,
            com.PrimeCare.PrimeCare.shared.enums.BranchSessionType targetSession,
            LocalDate startDate,
            LocalDate endDate,
            com.PrimeCare.PrimeCare.shared.enums.BranchSessionType startSession,
            com.PrimeCare.PrimeCare.shared.enums.BranchSessionType endSession
    ) {
        if (targetDate.isBefore(startDate) || targetDate.isAfter(endDate)) {
            return false;
        }
        if (startDate.equals(endDate)) {
            return targetSession.ordinal() >= startSession.ordinal()
                    && targetSession.ordinal() <= endSession.ordinal();
        }
        if (targetDate.equals(startDate)) {
            return targetSession.ordinal() >= startSession.ordinal();
        }
        if (targetDate.equals(endDate)) {
            return targetSession.ordinal() <= endSession.ordinal();
        }
        return true;
    }

    private String normalizeCode(String value) {
        String normalized = StringUtil.trimToNull(value);
        return normalized != null ? normalized.toUpperCase().replace(' ', '_') : null;
    }

    private AppointmentSourceType entitySourceOrFallback(Appointment appointment) {
        return appointment.getSourceType() != null
                ? appointment.getSourceType()
                : com.PrimeCare.PrimeCare.shared.enums.AppointmentSourceType.PUBLIC_BOOKING;
    }

    private Map<String, Object> snapshotAppointment(Appointment appointment) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("appointmentId", appointment.getId());
        data.put("id", appointment.getId());
        data.put("code", appointment.getCode());
        data.put("status", appointment.getStatus() != null ? appointment.getStatus().name() : null);
        data.put("visitDate", appointment.getVisitDate());
        data.put("etaStart", appointment.getEtaStart());
        data.put("etaEnd", appointment.getEtaEnd());
        data.put("doctorId", appointment.getDoctor() != null ? appointment.getDoctor().getId() : null);
        data.put("branchId", appointment.getBranch() != null ? appointment.getBranch().getId() : null);
        data.put("specialtyId", appointment.getSpecialty() != null ? appointment.getSpecialty().getId() : null);
        data.put("patientEmail", appointment.getPatientEmail());
        data.put("contactStatus", appointment.getContactStatus() != null ? appointment.getContactStatus().name() : null);
        data.put("patientResponseStatus", appointment.getPatientResponseStatus() != null ? appointment.getPatientResponseStatus().name() : null);
        data.put("reasonForVisit", appointment.getReasonForVisit());
        data.put("visitType", appointment.getVisitType());
        data.put("triagePriority", appointment.getTriagePriority());
        data.put("triageNote", appointment.getTriageNote());
        data.put("preTriageLevel", appointment.getPreTriageLevel());
        data.put("preTriagePriority", appointment.getPreTriagePriority());
        data.put("preTriageSource", appointment.getPreTriageSource());
        data.put("preTriageConfidenceLevel", appointment.getPreTriageConfidenceLevel());
        data.put("preTriageKnowledgeBaseVersion", appointment.getPreTriageKnowledgeBaseVersion());
        data.put("preTriageRulesetVersion", appointment.getPreTriageRulesetVersion());
        data.put("preTriageAiModelVersion", appointment.getPreTriageAiModelVersion());
        data.put("triageReviewStatus", appointment.getTriageReviewStatus());
        data.put("triageReviewedById", appointment.getTriageReviewedBy() != null ? appointment.getTriageReviewedBy().getId() : null);
        data.put("triageReviewedAt", appointment.getTriageReviewedAt());
        data.put("triageOverrideReason", appointment.getTriageOverrideReason());
        data.put("insuranceNote", appointment.getInsuranceNote());
        data.put("emergencyContactName", appointment.getEmergencyContactName());
        data.put("emergencyContactPhone", appointment.getEmergencyContactPhone());
        data.put("heightCm", appointment.getHeightCm());
        data.put("weightKg", appointment.getWeightKg());
        data.put("temperatureC", appointment.getTemperatureC());
        data.put("pulse", appointment.getPulse());
        data.put("systolicBp", appointment.getSystolicBp());
        data.put("diastolicBp", appointment.getDiastolicBp());
        data.put("respiratoryRate", appointment.getRespiratoryRate());
        data.put("spo2", appointment.getSpo2());
        data.put("intakeCompletedAt", appointment.getIntakeCompletedAt());
        data.put("confirmedAt", appointment.getConfirmedAt());
        data.put("checkedInAt", appointment.getCheckedInAt());
        data.put("checkedInLate", appointment.getCheckedInLate());
        data.put("lateMinutes", appointment.getLateMinutes());
        data.put("completedAt", appointment.getCompletedAt());
        data.put("cancelReason", appointment.getCancelReason());
        data.put("cancellationReasonType", appointment.getCancellationReasonType() != null ? appointment.getCancellationReasonType().name() : null);
        data.put("noShowMarkedAt", appointment.getNoShowMarkedAt());
        data.put("noShowNote", appointment.getNoShowNote());
        data.put("followUpPending", appointment.getFollowUpPending());
        data.put("followUpType", appointment.getFollowUpType() != null ? appointment.getFollowUpType().name() : null);
        data.put("rescheduledFromAppointmentId", appointment.getRescheduledFromAppointment() != null ? appointment.getRescheduledFromAppointment().getId() : null);
        data.put("rescheduledAt", appointment.getRescheduledAt());
        return data;
    }

    private record TriageReviewAuditPayload(
            String action,
            String fromPriority,
            String toPriority,
            String reason
    ) {
    }

    private record FollowUpFilter(
            Set<AppointmentFollowUpType> followUpTypes,
            boolean filterTypes,
            boolean includeLegacyNoShow
    ) {
    }
}
