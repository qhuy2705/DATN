package com.PrimeCare.PrimeCare.modules.appointment.service;

import com.PrimeCare.PrimeCare.modules.appointment.dto.response.AppointmentBookingRestrictionSummaryResponse;
import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.booking_restriction.entity.PatientBookingRestriction;
import com.PrimeCare.PrimeCare.modules.booking_restriction.entity.PatientViolationEvent;
import com.PrimeCare.PrimeCare.modules.booking_restriction.enums.BookingRestrictionLevel;
import com.PrimeCare.PrimeCare.modules.booking_restriction.enums.BookingRestrictionStatus;
import com.PrimeCare.PrimeCare.modules.booking_restriction.enums.ViolationEventStatus;
import com.PrimeCare.PrimeCare.modules.booking_restriction.repository.PatientBookingRestrictionRepository;
import com.PrimeCare.PrimeCare.modules.booking_restriction.repository.PatientViolationEventRepository;
import com.PrimeCare.PrimeCare.modules.booking_restriction.service.BookingIdentity;
import com.PrimeCare.PrimeCare.modules.booking_restriction.service.BookingIdentityService;
import com.PrimeCare.PrimeCare.modules.booking_restriction.service.BookingRestrictionPolicyService;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AppointmentBookingRestrictionSummaryService {

    private final AppointmentRepository appointmentRepository;
    private final BookingIdentityService bookingIdentityService;
    private final BookingRestrictionPolicyService bookingRestrictionPolicyService;
    private final PatientBookingRestrictionRepository restrictionRepository;
    private final PatientViolationEventRepository violationEventRepository;

    @Transactional(readOnly = true)
    public AppointmentBookingRestrictionSummaryResponse getSummary(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ApiException(ErrorCode.APPOINTMENT_NOT_FOUND));
        BookingIdentity identity = bookingIdentityService.resolveAppointmentIdentity(appointment);

        LocalDateTime now = LocalDateTime.now();
        String periodMonth = bookingRestrictionPolicyService.periodMonth(LocalDate.now());
        int monthlyScore = bookingRestrictionPolicyService.monthlyScore(identity.identityKeyHash(), periodMonth);
        String scoreLevel = bookingRestrictionPolicyService.scoreLevel(monthlyScore);

        PatientBookingRestriction activeRestriction = strongestActiveRestriction(identity.identityKeyHash(), now);
        PatientViolationEvent latestViolation = violationEventRepository
                .findFirstByIdentityKeyHashAndPeriodMonthAndStatusOrderByCreatedAtDesc(
                        identity.identityKeyHash(),
                        periodMonth,
                        ViolationEventStatus.ACTIVE
                )
                .orElse(null);

        String level = activeRestriction != null && activeRestriction.getLevel() != null
                ? activeRestriction.getLevel().name()
                : scoreLevel;
        boolean restricted = isRestricted(activeRestriction, scoreLevel);
        String status = activeRestriction != null && activeRestriction.getStatus() != null
                ? activeRestriction.getStatus().name()
                : null;

        return AppointmentBookingRestrictionSummaryResponse.builder()
                .appointmentId(appointment.getId())
                .patientId(appointment.getPatient() != null ? appointment.getPatient().getId() : null)
                .patientName(appointment.getPatientFullName())
                .patientPhone(appointment.getPatientPhone())
                .patientEmail(appointment.getPatientEmail())
                .monthlyScore(monthlyScore)
                .level(level)
                .status(status)
                .restricted(restricted)
                .restrictionExpiresAt(activeRestriction != null ? activeRestriction.getExpiresAt() : null)
                .latestViolationType(latestViolation != null && latestViolation.getType() != null
                        ? latestViolation.getType().name()
                        : null)
                .latestViolationAt(latestViolation != null ? latestViolation.getCreatedAt() : null)
                .message(summaryMessage(monthlyScore, scoreLevel, activeRestriction, latestViolation))
                .build();
    }

    private PatientBookingRestriction strongestActiveRestriction(String identityKeyHash, LocalDateTime now) {
        List<PatientBookingRestriction> restrictions =
                restrictionRepository.findByIdentityKeyHashAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(
                        identityKeyHash,
                        BookingRestrictionStatus.ACTIVE,
                        now
                );
        if (restrictions == null || restrictions.isEmpty()) {
            return null;
        }

        return restrictions.stream()
                .filter(restriction -> restriction.getLevel() != null)
                .max(Comparator
                        .comparingInt((PatientBookingRestriction restriction) -> levelRank(restriction.getLevel()))
                        .thenComparing(PatientBookingRestriction::getExpiresAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    private boolean isRestricted(PatientBookingRestriction activeRestriction, String scoreLevel) {
        boolean activeRestrictionBlocks = activeRestriction != null
                && activeRestriction.getLevel() != null
                && activeRestriction.getLevel().blocksPublicBooking();
        return activeRestrictionBlocks
                || BookingRestrictionPolicyService.VERIFY_REQUIRED.equals(scoreLevel)
                || BookingRestrictionPolicyService.STAFF_ONLY.equals(scoreLevel);
    }

    private String summaryMessage(
            int monthlyScore,
            String scoreLevel,
            PatientBookingRestriction activeRestriction,
            PatientViolationEvent latestViolation
    ) {
        if (monthlyScore == 0 && activeRestriction == null && latestViolation == null) {
            return "Không có hạn chế đặt lịch trong tháng này.";
        }
        if (activeRestriction != null && activeRestriction.getLevel() != null && activeRestriction.getLevel().blocksPublicBooking()) {
            return "Bệnh nhân đang có hạn chế đặt lịch đang hoạt động.";
        }
        if (BookingRestrictionPolicyService.STAFF_ONLY.equals(scoreLevel)) {
            return "Điểm vi phạm tháng này yêu cầu nhân viên hỗ trợ đặt lịch.";
        }
        if (BookingRestrictionPolicyService.VERIFY_REQUIRED.equals(scoreLevel)) {
            return "Điểm vi phạm tháng này yêu cầu xác minh trước khi đặt lịch.";
        }
        if (BookingRestrictionPolicyService.WARNING.equals(scoreLevel)) {
            return "Điểm vi phạm tháng này đang ở mức cảnh báo.";
        }
        return "Không có hạn chế đặt lịch trong tháng này.";
    }

    private int levelRank(BookingRestrictionLevel level) {
        if (level == BookingRestrictionLevel.STAFF_ONLY) {
            return 3;
        }
        if (level == BookingRestrictionLevel.VERIFY_REQUIRED) {
            return 2;
        }
        if (level == BookingRestrictionLevel.WARNING) {
            return 1;
        }
        return 0;
    }
}
