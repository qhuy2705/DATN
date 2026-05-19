package com.PrimeCare.PrimeCare.modules.booking_restriction.service;

import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.booking_restriction.entity.PatientBookingRestriction;
import com.PrimeCare.PrimeCare.modules.booking_restriction.enums.BookingRestrictionLevel;
import com.PrimeCare.PrimeCare.modules.booking_restriction.enums.BookingRestrictionStatus;
import com.PrimeCare.PrimeCare.modules.booking_restriction.repository.PatientBookingRestrictionRepository;
import com.PrimeCare.PrimeCare.modules.booking_restriction.repository.PatientViolationEventRepository;
import com.PrimeCare.PrimeCare.modules.patient.entity.Patient;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import com.PrimeCare.PrimeCare.shared.utils.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class BookingRestrictionPolicyService {

    public static final String CLEAR = "CLEAR";
    public static final String WARNING = "WARNING";
    public static final String VERIFY_REQUIRED = "VERIFY_REQUIRED";
    public static final String STAFF_ONLY = "STAFF_ONLY";

    private static final int VERIFY_REQUIRED_SCORE = 5;
    private static final int STAFF_ONLY_SCORE = 7;
    private static final int VERIFY_REQUIRED_EXTRA_DAYS = 7;
    private static final int STAFF_ONLY_EXTRA_DAYS = 14;
    private static final int MAX_ACTIVE_PER_PHONE_IN_SEVEN_DAYS = 2;
    private static final int MAX_ACTIVE_PER_IDENTITY_PER_DAY = 1;
    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final EnumSet<AppointmentStatus> ACTIVE_APPOINTMENT_STATUSES = EnumSet.of(
            AppointmentStatus.REQUESTED,
            AppointmentStatus.CONFIRMED,
            AppointmentStatus.CHECKED_IN
    );

    private final PatientViolationEventRepository violationEventRepository;
    private final PatientBookingRestrictionRepository restrictionRepository;
    private final AppointmentRepository appointmentRepository;
    private final BookingIdentityService bookingIdentityService;
    private final AuditLogService auditLogService;

    @Transactional
    public void assertPublicBookingAllowed(
            BookingIdentity identity,
            LocalDate visitDate,
            Long doctorId,
            BranchSessionType session,
            LocalTime slotStart
    ) {
        if (identity == null || !identity.hasIdentityKeyHash()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Không đủ thông tin định danh để đặt lịch.");
        }

        LocalDateTime now = LocalDateTime.now();
        PatientBookingRestriction activeRestriction = findActiveBlockingRestriction(identity.identityKeyHash(), now);
        if (activeRestriction != null) {
            throwStaffAssistanceRequired();
        }

        String currentPeriod = periodMonth(now.toLocalDate());
        int score = monthlyScore(identity.identityKeyHash(), currentPeriod);
        BookingRestrictionLevel scoreLevel = blockingLevelForScore(score);
        if (scoreLevel != null) {
            ensureRestrictionForScore(
                    identity.identityKeyHash(),
                    null,
                    currentPeriod,
                    score,
                    scoreLevel,
                    "Tự động kích hoạt theo điểm vi phạm trong tháng",
                    null,
                    now
            );
            throwStaffAssistanceRequired();
        }

        assertActiveBookingLimit(identity, visitDate, doctorId, session, slotStart);
    }

    @Transactional
    public PatientBookingRestriction ensureRestrictionForCurrentScore(
            String identityKeyHash,
            Patient patient,
            User createdBy,
            String reason
    ) {
        LocalDateTime now = LocalDateTime.now();
        String currentPeriod = periodMonth(now.toLocalDate());
        int score = monthlyScore(identityKeyHash, currentPeriod);
        BookingRestrictionLevel level = blockingLevelForScore(score);
        if (level == null) {
            return null;
        }
        return ensureRestrictionForScore(identityKeyHash, patient, currentPeriod, score, level, reason, createdBy, now);
    }

    @Transactional(readOnly = true)
    public BookingRestrictionSummary evaluateCurrent(String identityKeyHash) {
        LocalDateTime now = LocalDateTime.now();
        String currentPeriod = periodMonth(now.toLocalDate());
        int score = monthlyScore(identityKeyHash, currentPeriod);
        PatientBookingRestriction activeRestriction = findActiveBlockingRestriction(identityKeyHash, now);
        return new BookingRestrictionSummary(
                identityKeyHash,
                currentPeriod,
                score,
                scoreLevel(score),
                activeRestriction
        );
    }

    @Transactional(readOnly = true)
    public int monthlyScore(String identityKeyHash, String periodMonth) {
        if (identityKeyHash == null || identityKeyHash.isBlank() || periodMonth == null || periodMonth.isBlank()) {
            return 0;
        }
        Integer score = violationEventRepository.sumActivePointsByIdentityKeyHashAndPeriodMonth(identityKeyHash, periodMonth);
        return Math.max(score != null ? score : 0, 0);
    }

    @Transactional(readOnly = true)
    public int rawMonthlyScore(String identityKeyHash, String periodMonth) {
        if (identityKeyHash == null || identityKeyHash.isBlank() || periodMonth == null || periodMonth.isBlank()) {
            return 0;
        }
        Integer score = violationEventRepository.sumActivePointsByIdentityKeyHashAndPeriodMonth(identityKeyHash, periodMonth);
        return score != null ? score : 0;
    }

    @Transactional
    public PatientBookingRestriction recalculateAfterScoreChange(
            String identityKeyHash,
            Patient patient,
            User actor,
            String reason
    ) {
        if (identityKeyHash == null || identityKeyHash.isBlank()) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        String currentPeriod = periodMonth(now.toLocalDate());
        int score = monthlyScore(identityKeyHash, currentPeriod);
        BookingRestrictionLevel level = blockingLevelForScore(score);
        if (level != null) {
            return ensureRestrictionForScore(identityKeyHash, patient, currentPeriod, score, level, reason, actor, now);
        }

        liftActiveRestrictionsBelowThreshold(identityKeyHash, actor, reason, now);
        return null;
    }

    @Transactional
    public PatientBookingRestriction liftRestriction(PatientBookingRestriction restriction, User staff, String reason) {
        if (restriction == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy hạn chế đặt lịch.");
        }
        String normalizedReason = requireReason(reason);
        if (staff == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        if (restriction.getStatus() != BookingRestrictionStatus.ACTIVE) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Chỉ có thể gỡ hạn chế đang hoạt động.");
        }

        Map<String, Object> before = restrictionSnapshot(restriction);
        restriction.setStatus(BookingRestrictionStatus.LIFTED);
        restriction.setLiftedBy(staff);
        restriction.setLiftedAt(LocalDateTime.now());
        restriction.setLiftReason(normalizedReason);
        PatientBookingRestriction saved = restrictionRepository.save(restriction);
        auditLogService.log(staff, "LIFT_BOOKING_RESTRICTION", "PATIENT_BOOKING_RESTRICTION", saved.getId(), before, restrictionSnapshot(saved));
        return saved;
    }

    public String scoreLevel(int score) {
        if (score >= STAFF_ONLY_SCORE) {
            return STAFF_ONLY;
        }
        if (score >= VERIFY_REQUIRED_SCORE) {
            return VERIFY_REQUIRED;
        }
        if (score >= 3) {
            return WARNING;
        }
        return CLEAR;
    }

    public String periodMonth(LocalDate date) {
        return PERIOD_FORMATTER.format(YearMonth.from(date != null ? date : LocalDate.now()));
    }

    private PatientBookingRestriction ensureRestrictionForScore(
            String identityKeyHash,
            Patient patient,
            String periodMonth,
            int score,
            BookingRestrictionLevel level,
            String reason,
            User createdBy,
            LocalDateTime now
    ) {
        LocalDateTime expiresAt = restrictionExpiry(level, now);
        List<PatientBookingRestriction> activeRestrictions =
                restrictionRepository.findByIdentityKeyHashAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(
                        identityKeyHash,
                        BookingRestrictionStatus.ACTIVE,
                        now
                );

        PatientBookingRestriction existing = strongestBlockingRestriction(activeRestrictions);
        if (existing != null) {
            if (isStronger(level, existing.getLevel())) {
                existing.setLevel(level);
            }
            existing.setScoreSnapshot(score);
            existing.setPeriodMonth(periodMonth);
            existing.setReason(reason);
            if (patient != null && existing.getPatient() == null) {
                existing.setPatient(patient);
            }
            if (expiresAt.isAfter(existing.getExpiresAt())) {
                existing.setExpiresAt(expiresAt);
            }
            return restrictionRepository.save(existing);
        }

        PatientBookingRestriction restriction = PatientBookingRestriction.builder()
                .patient(patient)
                .identityKeyHash(identityKeyHash)
                .periodMonth(periodMonth)
                .level(level)
                .status(BookingRestrictionStatus.ACTIVE)
                .scoreSnapshot(score)
                .reason(reason)
                .startsAt(now)
                .expiresAt(expiresAt)
                .createdBy(createdBy)
                .build();
        PatientBookingRestriction saved = restrictionRepository.save(restriction);
        auditLogService.log(
                createdBy,
                "CREATE_BOOKING_RESTRICTION",
                "PATIENT_BOOKING_RESTRICTION",
                saved.getId(),
                null,
                restrictionSnapshot(saved)
        );
        return saved;
    }

    private void liftActiveRestrictionsBelowThreshold(
            String identityKeyHash,
            User actor,
            String reason,
            LocalDateTime now
    ) {
        List<PatientBookingRestriction> activeRestrictions =
                restrictionRepository.findByIdentityKeyHashAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(
                        identityKeyHash,
                        BookingRestrictionStatus.ACTIVE,
                        now
                );
        if (activeRestrictions == null || activeRestrictions.isEmpty()) {
            return;
        }

        String normalizedReason = StringUtil.trimToNull(reason);
        for (PatientBookingRestriction restriction : activeRestrictions) {
            Map<String, Object> before = restrictionSnapshot(restriction);
            restriction.setStatus(BookingRestrictionStatus.LIFTED);
            restriction.setLiftedBy(actor);
            restriction.setLiftedAt(now);
            restriction.setLiftReason(normalizedReason != null
                    ? normalizedReason
                    : "Tự động gỡ hạn chế sau khi điểm tháng hiện tại dưới ngưỡng");
            PatientBookingRestriction saved = restrictionRepository.save(restriction);
            auditLogService.log(actor, "LIFT_BOOKING_RESTRICTION", "PATIENT_BOOKING_RESTRICTION", saved.getId(), before, restrictionSnapshot(saved));
        }
    }

    private void assertActiveBookingLimit(
            BookingIdentity identity,
            LocalDate visitDate,
            Long doctorId,
            BranchSessionType session,
            LocalTime slotStart
    ) {
        if (identity.canonicalPhone() == null || visitDate == null) {
            return;
        }

        List<String> phoneCandidates = bookingIdentityService.phoneLookupCandidates(identity.canonicalPhone());
        if (phoneCandidates.isEmpty()) {
            return;
        }

        List<Appointment> sameSlotAppointments = appointmentRepository.findActiveByDoctorSlotAndPatientPhoneIn(
                doctorId,
                visitDate,
                session,
                slotStart,
                ACTIVE_APPOINTMENT_STATUSES,
                phoneCandidates
        );
        if (sameSlotAppointments.stream().anyMatch(existing -> matchesIdentity(existing, identity))) {
            throwActiveLimitExceeded("Bạn đã có lịch hẹn đang hoạt động với bác sĩ trong khung giờ này.");
        }

        List<Appointment> sameDayAppointments = appointmentRepository.findActiveByPatientPhoneInAndVisitDateBetween(
                phoneCandidates,
                visitDate,
                visitDate,
                ACTIVE_APPOINTMENT_STATUSES
        );
        long sameDayIdentityCount = sameDayAppointments.stream()
                .filter(existing -> matchesIdentity(existing, identity))
                .count();
        if (sameDayIdentityCount >= MAX_ACTIVE_PER_IDENTITY_PER_DAY) {
            throwActiveLimitExceeded("Bạn đã có lịch hẹn đang hoạt động trong ngày này.");
        }

        LocalDate rangeStart = visitDate.minusDays(6);
        LocalDate rangeEnd = visitDate.plusDays(6);
        List<Appointment> nearbyAppointments = appointmentRepository.findActiveByPatientPhoneInAndVisitDateBetween(
                phoneCandidates,
                rangeStart,
                rangeEnd,
                ACTIVE_APPOINTMENT_STATUSES
        );
        if (wouldExceedPhoneLimitInAnySevenDayWindow(nearbyAppointments, identity.canonicalPhone(), visitDate)) {
            throwActiveLimitExceeded("Bạn đã có số lịch hẹn đang hoạt động tối đa trong 7 ngày gần ngày khám này.");
        }
    }

    private boolean wouldExceedPhoneLimitInAnySevenDayWindow(
            List<Appointment> existingAppointments,
            String canonicalPhone,
            LocalDate requestedVisitDate
    ) {
        List<LocalDate> dates = new ArrayList<>();
        dates.add(requestedVisitDate);
        if (existingAppointments != null) {
            existingAppointments.stream()
                    .filter(appointment -> canonicalPhone.equals(bookingIdentityService.normalizePhone(appointment.getPatientPhone())))
                    .map(Appointment::getVisitDate)
                    .filter(Objects::nonNull)
                    .forEach(dates::add);
        }

        dates.sort(Comparator.naturalOrder());
        for (int i = 0; i < dates.size(); i++) {
            LocalDate windowStart = dates.get(i);
            LocalDate windowEnd = windowStart.plusDays(6);
            long count = dates.stream()
                    .filter(date -> !date.isBefore(windowStart) && !date.isAfter(windowEnd))
                    .count();
            if (count > MAX_ACTIVE_PER_PHONE_IN_SEVEN_DAYS) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesIdentity(Appointment appointment, BookingIdentity identity) {
        return appointment != null
                && identity != null
                && identity.hasIdentityKeyHash()
                && identity.identityKeyHash().equals(bookingIdentityService.resolveAppointmentIdentity(appointment).identityKeyHash());
    }

    private PatientBookingRestriction findActiveBlockingRestriction(String identityKeyHash, LocalDateTime now) {
        return strongestBlockingRestriction(
                restrictionRepository.findByIdentityKeyHashAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(
                        identityKeyHash,
                        BookingRestrictionStatus.ACTIVE,
                        now
                )
        );
    }

    private PatientBookingRestriction strongestBlockingRestriction(List<PatientBookingRestriction> restrictions) {
        if (restrictions == null || restrictions.isEmpty()) {
            return null;
        }
        return restrictions.stream()
                .filter(restriction -> restriction.getLevel() != null && restriction.getLevel().blocksPublicBooking())
                .max(Comparator
                        .comparingInt((PatientBookingRestriction restriction) -> levelRank(restriction.getLevel()))
                        .thenComparing(PatientBookingRestriction::getExpiresAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    private BookingRestrictionLevel blockingLevelForScore(int score) {
        if (score >= STAFF_ONLY_SCORE) {
            return BookingRestrictionLevel.STAFF_ONLY;
        }
        if (score >= VERIFY_REQUIRED_SCORE) {
            return BookingRestrictionLevel.VERIFY_REQUIRED;
        }
        return null;
    }

    private LocalDateTime restrictionExpiry(BookingRestrictionLevel level, LocalDateTime now) {
        LocalDateTime endOfCurrentMonth = YearMonth.from(now).atEndOfMonth().atTime(LocalTime.MAX);
        int extraDays = level == BookingRestrictionLevel.STAFF_ONLY
                ? STAFF_ONLY_EXTRA_DAYS
                : VERIFY_REQUIRED_EXTRA_DAYS;
        LocalDateTime minimumDurationExpiry = now.plusDays(extraDays);
        return minimumDurationExpiry.isAfter(endOfCurrentMonth) ? minimumDurationExpiry : endOfCurrentMonth;
    }

    private boolean isStronger(BookingRestrictionLevel candidate, BookingRestrictionLevel current) {
        return levelRank(candidate) > levelRank(current);
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

    private void throwStaffAssistanceRequired() {
        throw new ApiException(ErrorCode.BOOKING_REQUIRES_STAFF_ASSISTANCE);
    }

    private void throwActiveLimitExceeded(String detail) {
        throw new ApiException(
                ErrorCode.BOOKING_ACTIVE_LIMIT_EXCEEDED,
                detail + " Vui lòng liên hệ nhân viên nếu cần hỗ trợ đặt thêm lịch."
        );
    }

    private String requireReason(String reason) {
        String normalizedReason = StringUtil.trimToNull(reason);
        if (normalizedReason == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Lý do là bắt buộc.");
        }
        return normalizedReason;
    }

    private Map<String, Object> restrictionSnapshot(PatientBookingRestriction restriction) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (restriction == null) {
            return snapshot;
        }
        snapshot.put("id", restriction.getId());
        snapshot.put("patientId", restriction.getPatient() != null ? restriction.getPatient().getId() : null);
        snapshot.put("identityKeyHash", restriction.getIdentityKeyHash());
        snapshot.put("periodMonth", restriction.getPeriodMonth());
        snapshot.put("level", restriction.getLevel());
        snapshot.put("status", restriction.getStatus());
        snapshot.put("scoreSnapshot", restriction.getScoreSnapshot());
        snapshot.put("reason", restriction.getReason());
        snapshot.put("expiresAt", restriction.getExpiresAt());
        snapshot.put("liftedBy", restriction.getLiftedBy() != null ? restriction.getLiftedBy().getId() : null);
        snapshot.put("liftedAt", restriction.getLiftedAt());
        snapshot.put("liftReason", restriction.getLiftReason());
        return snapshot;
    }

    public record BookingRestrictionSummary(
            String identityKeyHash,
            String periodMonth,
            int score,
            String level,
            PatientBookingRestriction activeRestriction
    ) {
    }
}
