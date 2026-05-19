package com.PrimeCare.PrimeCare.modules.appointment.service;

import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.entity.AppointmentSlotHold;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentSlotHoldRepository;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.doctor_leave.entity.DoctorLeaveRequest;
import com.PrimeCare.PrimeCare.modules.notification.messaging.AppointmentMailEventPublisher;
import com.PrimeCare.PrimeCare.modules.notification.messaging.event.AppointmentRescheduleOfferMailEvent;
import com.PrimeCare.PrimeCare.modules.notification.service.InternalNotificationService;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentCancellationReasonType;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentFollowUpType;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentSlotHoldReason;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentSlotHoldStatus;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DoctorCancellationRecoveryService {

    private static final String DOCTOR_UNAVAILABLE_NOTE = "Lịch hẹn bị hủy do bác sĩ/phòng khám không thể phục vụ khung giờ này.";
    private static final EnumSet<AppointmentStatus> RECOVERABLE_STATUSES = EnumSet.of(
            AppointmentStatus.REQUESTED,
            AppointmentStatus.CONFIRMED,
            AppointmentStatus.CHECKED_IN
    );

    private final AppointmentRepository appointmentRepository;
    private final AppointmentSlotHoldRepository slotHoldRepository;
    private final AlternativeSlotSuggestionService suggestionService;
    private final AppointmentSlotHoldTokenService tokenService;
    private final AppointmentMailEventPublisher mailEventPublisher;
    private final AppointmentStatusHistoryService appointmentStatusHistoryService;
    private final AppointmentQueueService appointmentQueueService;
    private final AppointmentAvailabilityService availabilityService;
    private final InternalNotificationService internalNotificationService;

    @Transactional
    public List<AppointmentSlotHold> recoverForLeaveApproval(
            DoctorLeaveRequest leave,
            User actor
    ) {
        if (leave == null || leave.getDoctor() == null) {
            return List.of();
        }
        List<Appointment> affectedAppointments = appointmentRepository.findAffectedForDoctorRecovery(
                        leave.getDoctor().getId(),
                        leave.getStartDate(),
                        leave.getEndDate(),
                        RECOVERABLE_STATUSES
                )
                .stream()
                .filter(appointment -> isSessionCovered(
                        appointment.getVisitDate(),
                        appointment.getSession(),
                        leave.getStartDate(),
                        leave.getEndDate(),
                        leave.getStartSession(),
                        leave.getEndSession()
                ))
                .toList();

        return recoverAppointments(
                affectedAppointments,
                AppointmentSlotHoldReason.DOCTOR_LEAVE_APPROVED,
                actor,
                appointment -> isFullDayLeaveForDate(leave, appointment.getVisitDate()),
                null
        );
    }

    @Transactional
    public List<AppointmentSlotHold> recoverDoctorCancellation(
            Collection<Appointment> affectedAppointments,
            User actor
    ) {
        return recoverDoctorCancellation(affectedAppointments, actor, null);
    }

    @Transactional
    public List<AppointmentSlotHold> recoverDoctorCancellation(
            Collection<Appointment> affectedAppointments,
            User actor,
            String cancellationNote
    ) {
        return recoverAppointments(
                affectedAppointments,
                AppointmentSlotHoldReason.DOCTOR_CANCELLED,
                actor,
                ignored -> false,
                cancellationNote
        );
    }

    @Transactional
    public Optional<AppointmentSlotHold> recoverSingleDoctorCancellation(Appointment appointment, User actor) {
        return recoverSingleDoctorCancellation(appointment, actor, null);
    }

    @Transactional
    public Optional<AppointmentSlotHold> recoverSingleDoctorCancellation(Appointment appointment, User actor, String cancellationNote) {
        List<AppointmentSlotHold> holds = recoverDoctorCancellation(List.of(appointment), actor, cancellationNote);
        return holds.stream().findFirst();
    }

    private List<AppointmentSlotHold> recoverAppointments(
            Collection<Appointment> affectedAppointments,
            AppointmentSlotHoldReason reason,
            User actor,
            FullDayDecision fullDayDecision,
            String cancellationNote
    ) {
        if (affectedAppointments == null || affectedAppointments.isEmpty()) {
            return List.of();
        }

        return affectedAppointments.stream()
                .map(appointment -> recoverAppointment(
                        appointment,
                        reason,
                        actor,
                        fullDayDecision.isFullDay(appointment),
                        cancellationNote
                ))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<AppointmentSlotHold> recoverAppointment(
            Appointment appointment,
            AppointmentSlotHoldReason reason,
            User actor,
            boolean fullDayUnavailable,
            String cancellationNote
    ) {
        if (appointment == null || appointment.getId() == null || !RECOVERABLE_STATUSES.contains(appointment.getStatus())) {
            return Optional.empty();
        }

        List<AppointmentSlotHold> existingHolds = slotHoldRepository.findByOriginalAppointment_IdAndStatusIn(
                appointment.getId(),
                AppointmentSlotAvailabilityGuard.BLOCKING_HOLD_STATUSES
        );
        if (!existingHolds.isEmpty()) {
            return Optional.of(existingHolds.get(0));
        }

        AppointmentStatus previousStatus = appointment.getStatus();
        String customNote = cancellationNote != null && !cancellationNote.isBlank() ? cancellationNote.trim() : null;
        String note = customNote != null
                ? customNote
                : reason == AppointmentSlotHoldReason.DOCTOR_LEAVE_APPROVED
                ? "Bác sĩ được duyệt nghỉ, lịch cũ cần dời sang khung giờ khác."
                : DOCTOR_UNAVAILABLE_NOTE;

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setCancelledBy(actor);
        appointment.setCancelledAt(LocalDateTime.now());
        appointment.setCancelReason(note);
        appointment.setCancellationReasonType(AppointmentCancellationReasonType.DOCTOR_UNAVAILABLE);
        appointment.setQueueNo(null);
        appointment.setProcessingBy(null);
        appointment.setProcessingStartedAt(null);
        appointment.setProcessingExpiresAt(null);

        appointmentRepository.save(appointment);
        appointmentStatusHistoryService.record(appointment, previousStatus, AppointmentStatus.CANCELLED, actor, note);
        if (previousStatus == AppointmentStatus.CONFIRMED) {
            appointmentQueueService.recalculateProjectedQueue(
                    appointment.getDoctor().getId(),
                    appointment.getVisitDate(),
                    appointment.getSession()
            );
        }
        availabilityService.evictAvailabilityCacheForDoctorDateSession(
                appointment.getDoctor().getId(),
                appointment.getVisitDate(),
                appointment.getSession()
        );

        Optional<AlternativeSlotSuggestion> suggestion = fullDayUnavailable
                ? suggestionService.suggestForFullDayLeave(appointment)
                : suggestionService.suggest(appointment);

        if (suggestion.isEmpty()) {
            markFollowUpRequired(appointment, AppointmentFollowUpType.DOCTOR_CANCELLATION_CONTACT_REQUESTED);
            notifyStaffNoAlternativeSlot(appointment);
            return Optional.empty();
        }

        AppointmentSlotHold hold = createHold(appointment, suggestion.get(), reason);
        publishOffer(hold);
        availabilityService.evictAvailabilityCacheForDoctorDateSession(
                hold.getDoctor().getId(),
                hold.getVisitDate(),
                hold.getSession()
        );
        return Optional.of(hold);
    }

    private AppointmentSlotHold createHold(
            Appointment originalAppointment,
            AlternativeSlotSuggestion suggestion,
            AppointmentSlotHoldReason reason
    ) {
        LocalDateTime now = LocalDateTime.now();
        AppointmentSlotHold hold = AppointmentSlotHold.builder()
                .originalAppointment(originalAppointment)
                .patient(originalAppointment.getPatient())
                .doctor(suggestion.doctor())
                .branch(originalAppointment.getBranch())
                .specialty(originalAppointment.getSpecialty())
                .visitDate(suggestion.visitDate())
                .session(suggestion.session())
                .slotStart(suggestion.slotStart())
                .slotEnd(suggestion.slotEnd())
                .status(AppointmentSlotHoldStatus.HELD)
                .expiresAt(now.plusHours(12))
                .holdReason(reason)
                .createdAt(now)
                .tokenNonce(tokenService.newNonce())
                .build();

        String token = tokenService.issueToken(hold);
        hold.setTokenHash(tokenService.hashToken(token));
        return slotHoldRepository.save(hold);
    }

    private void publishOffer(AppointmentSlotHold hold) {
        String token = tokenService.issueToken(hold);
        mailEventPublisher.publishRescheduleOffer(new AppointmentRescheduleOfferMailEvent(
                hold.getId(),
                hold.getOriginalAppointment().getId(),
                token
        ));
    }

    public void markFollowUpRequired(Appointment appointment, AppointmentFollowUpType followUpType) {
        appointment.setFollowUpPending(true);
        appointment.setFollowUpType(followUpType);
        appointmentRepository.save(appointment);
    }

    public void notifyStaffFollowUpRequired(Appointment appointment, AppointmentFollowUpType followUpType) {
        String typeLabel = followUpType == AppointmentFollowUpType.DOCTOR_CANCELLATION_NO_RESPONSE
                ? "bệnh nhân chưa phản hồi đề xuất dời lịch"
                : "bệnh nhân yêu cầu lễ tân liên hệ";
        String message = "Lịch hẹn " + appointment.getCode() + " của bệnh nhân "
                + appointment.getPatientFullName() + " cần follow-up vì " + typeLabel + ".";
        notifyStaff(
                "DOCTOR_CANCELLATION_FOLLOW_UP",
                "Lịch hẹn cần liên hệ bệnh nhân",
                message,
                appointment
        );
    }

    private void notifyStaffNoAlternativeSlot(Appointment appointment) {
        String message = "Lịch hẹn " + appointment.getCode() + " của bệnh nhân "
                + appointment.getPatientFullName()
                + " đã bị hủy do bác sĩ/phòng khám không thể phục vụ nhưng chưa tìm được slot thay thế tự động.";
        notifyStaff(
                "DOCTOR_CANCELLATION_NO_SLOT",
                "Không tìm được slot dời lịch tự động",
                message,
                appointment
        );
    }

    private void notifyStaff(String type, String title, String message, Appointment appointment) {
        internalNotificationService.notifyRole(
                UserRole.STAFF,
                type,
                InternalNotificationService.SEVERITY_WARNING,
                title,
                message,
                "/app/appointments/" + appointment.getId() + "/process",
                "APPOINTMENT",
                appointment.getId()
        );
        internalNotificationService.notifyRole(
                UserRole.OPERATIONS_ADMIN,
                type,
                InternalNotificationService.SEVERITY_WARNING,
                title,
                message,
                "/app/appointments/" + appointment.getId() + "/process",
                "APPOINTMENT",
                appointment.getId()
        );
    }

    private boolean isFullDayLeaveForDate(DoctorLeaveRequest leave, LocalDate date) {
        if (leave == null || date == null || date.isBefore(leave.getStartDate()) || date.isAfter(leave.getEndDate())) {
            return false;
        }
        return isSessionCovered(date, BranchSessionType.AM, leave.getStartDate(), leave.getEndDate(), leave.getStartSession(), leave.getEndSession())
                && isSessionCovered(date, BranchSessionType.PM, leave.getStartDate(), leave.getEndDate(), leave.getStartSession(), leave.getEndSession());
    }

    private boolean isSessionCovered(
            LocalDate targetDate,
            BranchSessionType targetSession,
            LocalDate startDate,
            LocalDate endDate,
            BranchSessionType startSession,
            BranchSessionType endSession
    ) {
        if (targetDate == null || targetSession == null || startDate == null || endDate == null
                || startSession == null || endSession == null) {
            return false;
        }
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

    private interface FullDayDecision {
        boolean isFullDay(Appointment appointment);
    }
}
