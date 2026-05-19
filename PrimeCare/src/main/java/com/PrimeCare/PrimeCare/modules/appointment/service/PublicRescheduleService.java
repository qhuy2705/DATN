package com.PrimeCare.PrimeCare.modules.appointment.service;

import com.PrimeCare.PrimeCare.modules.appointment.dto.response.PublicRescheduleOfferResponse;
import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.entity.AppointmentSlotHold;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentSlotHoldRepository;
import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.notification.messaging.AppointmentMailEventPublisher;
import com.PrimeCare.PrimeCare.modules.notification.messaging.event.SlotHoldAcceptedMailEvent;
import com.PrimeCare.PrimeCare.modules.notification.messaging.event.SlotHoldCancelledMailEvent;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentFollowUpType;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentSlotHoldStatus;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentSourceType;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.enums.ArrivalStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PublicRescheduleService {

    private final AppointmentSlotHoldRepository slotHoldRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentSlotHoldTokenService tokenService;
    private final AppointmentSlotAvailabilityGuard slotAvailabilityGuard;
    private final AppointmentCodeGenerator appointmentCodeGenerator;
    private final AppointmentStatusHistoryService appointmentStatusHistoryService;
    private final AppointmentQueueService appointmentQueueService;
    private final AppointmentAvailabilityService availabilityService;
    private final AppointmentMailEventPublisher mailEventPublisher;
    private final DoctorCancellationRecoveryService recoveryService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public PublicRescheduleOfferResponse getOffer(String token) {
        AppointmentSlotHold hold = resolveHold(token, false);
        return toResponse(hold, statusMessage(hold), null);
    }

    @Transactional
    public PublicRescheduleOfferResponse accept(String token) {
        AppointmentSlotHold hold = resolveHold(token, true);
        assertHeldAndNotExpired(hold);
        Map<String, Object> before = snapshotSlotHold(hold, null);

        slotAvailabilityGuard.assertSlotAvailable(
                hold.getDoctor().getId(),
                hold.getVisitDate(),
                hold.getSession(),
                hold.getSlotStart(),
                hold.getSlotEnd(),
                hold.getOriginalAppointment().getId(),
                hold.getId()
        );

        Appointment newAppointment = copyConfirmedAppointment(hold);
        Appointment saved = appointmentRepository.save(newAppointment);
        appointmentQueueService.recalculateProjectedQueue(
                saved.getDoctor().getId(),
                saved.getVisitDate(),
                saved.getSession()
        );
        saved = appointmentRepository.findById(saved.getId()).orElse(saved);

        hold.setStatus(AppointmentSlotHoldStatus.ACCEPTED);
        hold.setAcceptedAt(LocalDateTime.now());
        slotHoldRepository.save(hold);

        appointmentStatusHistoryService.record(saved, null, AppointmentStatus.CONFIRMED, null, "Bệnh nhân chấp nhận lịch dời do phòng khám đề xuất");
        availabilityService.evictAvailabilityCacheForDoctorDateSession(
                saved.getDoctor().getId(),
                saved.getVisitDate(),
                saved.getSession()
        );
        mailEventPublisher.publishSlotHoldAccepted(new SlotHoldAcceptedMailEvent(hold.getId(), saved.getId()));

        auditLogService.log(null, "PUBLIC_ACCEPT_RESCHEDULE", "SLOT_HOLD", hold.getId(), before, snapshotSlotHold(hold, saved.getId()));

        return toResponse(hold, "Lịch mới đã được xác nhận.", saved.getId());
    }

    @Transactional
    public PublicRescheduleOfferResponse requestContact(String token) {
        AppointmentSlotHold hold = resolveHold(token, true);
        assertHeldAndNotExpired(hold);
        Map<String, Object> before = snapshotSlotHold(hold, null);

        LocalDateTime now = LocalDateTime.now();
        if (hold.getContactRequestedAt() == null) {
            hold.setContactRequestedAt(now);
            slotHoldRepository.save(hold);
        }

        Appointment original = hold.getOriginalAppointment();
        recoveryService.markFollowUpRequired(original, AppointmentFollowUpType.DOCTOR_CANCELLATION_CONTACT_REQUESTED);
        recoveryService.notifyStaffFollowUpRequired(original, AppointmentFollowUpType.DOCTOR_CANCELLATION_CONTACT_REQUESTED);

        auditLogService.log(null, "PUBLIC_REQUEST_CONTACT", "SLOT_HOLD", hold.getId(), before, snapshotSlotHold(hold, null));

        return toResponse(hold, "PrimeCare đã ghi nhận yêu cầu. Lễ tân sẽ liên hệ để hỗ trợ bạn.", null);
    }

    @Transactional
    public PublicRescheduleOfferResponse cancel(String token) {
        AppointmentSlotHold hold = resolveHold(token, true);
        if (hold.getStatus() == AppointmentSlotHoldStatus.CANCELLED) {
            return toResponse(hold, "Yêu cầu dời lịch đã được hủy trước đó.", null);
        }
        if (hold.getStatus() == AppointmentSlotHoldStatus.ACCEPTED) {
            throw new ApiException(ErrorCode.APPOINTMENT_INVALID_STATUS, "Lịch mới đã được xác nhận, không thể hủy slot giữ tạm.");
        }
        if (hold.getStatus() == AppointmentSlotHoldStatus.EXPIRED || !hold.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new ApiException(ErrorCode.PUBLIC_LOOKUP_TOKEN_EXPIRED);
        }

        Map<String, Object> before = snapshotSlotHold(hold, null);
        hold.setStatus(AppointmentSlotHoldStatus.CANCELLED);
        hold.setCancelledAt(LocalDateTime.now());
        slotHoldRepository.save(hold);
        availabilityService.evictAvailabilityCacheForDoctorDateSession(
                hold.getDoctor().getId(),
                hold.getVisitDate(),
                hold.getSession()
        );
        mailEventPublisher.publishSlotHoldCancelled(new SlotHoldCancelledMailEvent(hold.getId()));

        auditLogService.log(null, "PUBLIC_CANCEL_RESCHEDULE", "SLOT_HOLD", hold.getId(), before, snapshotSlotHold(hold, null));

        return toResponse(hold, "PrimeCare đã hủy slot giữ tạm theo yêu cầu của bạn.", null);
    }

    private AppointmentSlotHold resolveHold(String token, boolean forUpdate) {
        tokenService.validateFormat(token);
        String tokenHash = tokenService.hashToken(token);
        return (forUpdate
                ? slotHoldRepository.findByTokenHashForUpdate(tokenHash)
                : slotHoldRepository.findByTokenHash(tokenHash))
                .orElseThrow(() -> new ApiException(ErrorCode.PUBLIC_LOOKUP_TOKEN_INVALID));
    }

    private void assertHeldAndNotExpired(AppointmentSlotHold hold) {
        if (hold.getStatus() != AppointmentSlotHoldStatus.HELD) {
            throw new ApiException(ErrorCode.APPOINTMENT_INVALID_STATUS, statusMessage(hold));
        }
        if (hold.getExpiresAt() == null || !hold.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new ApiException(ErrorCode.PUBLIC_LOOKUP_TOKEN_EXPIRED);
        }
    }

    private Appointment copyConfirmedAppointment(AppointmentSlotHold hold) {
        Appointment oldAppointment = hold.getOriginalAppointment();
        int slotMinutes = Math.toIntExact(ChronoUnit.MINUTES.between(hold.getSlotStart(), hold.getSlotEnd()));
        if (slotMinutes <= 0) {
            slotMinutes = AppointmentAvailabilityService.FIXED_SLOT_MINUTES;
        }
        return Appointment.builder()
                .code(appointmentCodeGenerator.generate(hold.getVisitDate(), hold.getDoctor().getId(), hold.getSlotStart()))
                .status(AppointmentStatus.CONFIRMED)
                .branch(hold.getBranch())
                .specialty(hold.getSpecialty())
                .doctor(hold.getDoctor())
                .visitDate(hold.getVisitDate())
                .session(hold.getSession())
                .queueNo(null)
                .slotMinutes(slotMinutes)
                .etaStart(hold.getSlotStart())
                .etaEnd(hold.getSlotEnd())
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
                .confirmedAt(LocalDateTime.now())
                .sourceType(oldAppointment.getSourceType() != null ? oldAppointment.getSourceType() : AppointmentSourceType.PUBLIC_BOOKING)
                .arrivalStatus(ArrivalStatus.NOT_ARRIVED)
                .receptionQueueNo(null)
                .arrivedAt(null)
                .arrivedBy(null)
                .patient(oldAppointment.getPatient())
                .rescheduledFromAppointment(oldAppointment)
                .rescheduledAt(LocalDateTime.now())
                .build();
    }

    private PublicRescheduleOfferResponse toResponse(AppointmentSlotHold hold, String message, Long newAppointmentId) {
        Appointment original = hold.getOriginalAppointment();
        boolean held = hold.getStatus() == AppointmentSlotHoldStatus.HELD
                && hold.getExpiresAt() != null
                && hold.getExpiresAt().isAfter(LocalDateTime.now());
        return PublicRescheduleOfferResponse.builder()
                .slotHoldId(hold.getId())
                .status(hold.getStatus())
                .holdReason(hold.getHoldReason())
                .expiresAt(hold.getExpiresAt())
                .contactRequestedAt(hold.getContactRequestedAt())
                .canAccept(held)
                .canRequestContact(held)
                .canCancel(held)
                .message(message)
                .oldAppointment(toAppointmentSummary(original))
                .heldSlot(PublicRescheduleOfferResponse.AppointmentSummary.builder()
                        .doctorName(hold.getDoctor() != null ? hold.getDoctor().getFullName() : null)
                        .branchName(hold.getBranch() != null ? firstNonBlank(hold.getBranch().getNameVn(), hold.getBranch().getNameEn()) : null)
                        .specialtyName(hold.getSpecialty() != null ? firstNonBlank(hold.getSpecialty().getNameVn(), hold.getSpecialty().getNameEn()) : null)
                        .visitDate(hold.getVisitDate())
                        .session(hold.getSession())
                        .startTime(hold.getSlotStart())
                        .endTime(hold.getSlotEnd())
                        .build())
                .newAppointmentId(newAppointmentId)
                .build();
    }

    private PublicRescheduleOfferResponse.AppointmentSummary toAppointmentSummary(Appointment appointment) {
        if (appointment == null) {
            return null;
        }
        return PublicRescheduleOfferResponse.AppointmentSummary.builder()
                .appointmentCode(appointment.getCode())
                .doctorName(appointment.getDoctor() != null ? appointment.getDoctor().getFullName() : null)
                .branchName(appointment.getBranch() != null ? firstNonBlank(appointment.getBranch().getNameVn(), appointment.getBranch().getNameEn()) : null)
                .specialtyName(appointment.getSpecialty() != null ? firstNonBlank(appointment.getSpecialty().getNameVn(), appointment.getSpecialty().getNameEn()) : null)
                .visitDate(appointment.getVisitDate())
                .session(appointment.getSession())
                .startTime(appointment.getEtaStart())
                .endTime(appointment.getEtaEnd())
                .build();
    }

    private String statusMessage(AppointmentSlotHold hold) {
        if (hold.getStatus() == AppointmentSlotHoldStatus.ACCEPTED) {
            return "Bạn đã xác nhận lịch mới.";
        }
        if (hold.getStatus() == AppointmentSlotHoldStatus.CANCELLED) {
            return "Bạn đã hủy slot giữ tạm.";
        }
        if (hold.getStatus() == AppointmentSlotHoldStatus.EXPIRED
                || (hold.getStatus() == AppointmentSlotHoldStatus.HELD && !hold.getExpiresAt().isAfter(LocalDateTime.now()))) {
            return "Slot giữ tạm đã hết hạn. PrimeCare sẽ liên hệ để hỗ trợ bạn.";
        }
        if (hold.getContactRequestedAt() != null) {
            return "PrimeCare đã nhận yêu cầu lễ tân liên hệ. Slot vẫn đang được giữ đến hạn hiển thị.";
        }
        return "PrimeCare đang giữ slot thay thế này cho bạn.";
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private Map<String, Object> snapshotSlotHold(AppointmentSlotHold hold, Long newAppointmentId) {
        Appointment original = hold.getOriginalAppointment();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("slotHoldId", hold.getId());
        data.put("appointmentId", original != null ? original.getId() : null);
        data.put("newAppointmentId", newAppointmentId);
        data.put("patientId", hold.getPatient() != null ? hold.getPatient().getId() : null);
        data.put("status", hold.getStatus() != null ? hold.getStatus().name() : null);
        data.put("originalAppointmentStatus", original != null && original.getStatus() != null ? original.getStatus().name() : null);
        data.put("doctorId", hold.getDoctor() != null ? hold.getDoctor().getId() : null);
        data.put("branchId", hold.getBranch() != null ? hold.getBranch().getId() : null);
        data.put("specialtyId", hold.getSpecialty() != null ? hold.getSpecialty().getId() : null);
        data.put("visitDate", hold.getVisitDate());
        data.put("session", hold.getSession() != null ? hold.getSession().name() : null);
        data.put("slotStart", hold.getSlotStart());
        data.put("slotEnd", hold.getSlotEnd());
        data.put("holdReason", hold.getHoldReason() != null ? hold.getHoldReason().name() : null);
        data.put("expiresAt", hold.getExpiresAt());
        data.put("acceptedAt", hold.getAcceptedAt());
        data.put("contactRequestedAt", hold.getContactRequestedAt());
        data.put("cancelledAt", hold.getCancelledAt());
        return data;
    }
}
