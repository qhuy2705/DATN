package com.PrimeCare.PrimeCare.modules.appointment.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.AppointmentAvailabilityResponse;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.AvailabilityPreviewResponse;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.BookableSlotResponse;
import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.entity.AppointmentSlotHold;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentSlotHoldRepository;
import com.PrimeCare.PrimeCare.modules.doctor_leave.entity.DoctorLeaveRequest;
import com.PrimeCare.PrimeCare.modules.doctor_leave.repository.DoctorLeaveRequestRepository;
import com.PrimeCare.PrimeCare.modules.doctor_schedule.repository.DoctorWorkScheduleRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.BranchSession;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.repository.BranchSessionRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.repository.DoctorProfileRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.service.DoctorOperationalGuardService;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.entity.BranchSpecialty;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.service.BranchSpecialtyService;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import com.PrimeCare.PrimeCare.shared.enums.DoctorLeaveRequestStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentAvailabilityService {

    public static final int FIXED_SLOT_MINUTES = 30;
    public static final int DEFAULT_SLOT_CAPACITY = 1;

    private static final long AVAILABILITY_CACHE_TTL_MILLIS = 20_000L;
    private static final long AVAILABILITY_CACHE_MAX_SIZE = 2_000L;
    private static final EnumSet<AppointmentStatus> BLOCKING_STATUSES = EnumSet.of(
            AppointmentStatus.REQUESTED,
            AppointmentStatus.CONFIRMED,
            AppointmentStatus.CHECKED_IN
    );

    private final DoctorProfileRepository doctorProfileRepository;
    private final BranchSessionRepository branchSessionRepository;
    private final DoctorWorkScheduleRepository doctorWorkScheduleRepository;
    private final DoctorLeaveRequestRepository doctorLeaveRequestRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentSlotHoldRepository appointmentSlotHoldRepository;
    private final BranchSpecialtyService branchSpecialtyService;
    private final DoctorOperationalGuardService doctorOperationalGuardService;
    private final Cache<AvailabilityCacheKey, AppointmentAvailabilityResponse> availabilityCache = Caffeine.newBuilder()
            .maximumSize(AVAILABILITY_CACHE_MAX_SIZE)
            .expireAfterWrite(Duration.ofMillis(AVAILABILITY_CACHE_TTL_MILLIS))
            .build();

    @Transactional(readOnly = true)
    public AppointmentAvailabilityResponse getAvailability(
            Long branchId,
            Long specialtyId,
            Long doctorId,
            LocalDate visitDate,
            BranchSessionType session
    ) {
        return getAvailability(branchId, specialtyId, doctorId, visitDate, session, false);
    }

    @Transactional(readOnly = true)
    public AppointmentAvailabilityResponse getAvailability(
            Long branchId,
            Long specialtyId,
            Long doctorId,
            LocalDate visitDate,
            BranchSessionType session,
            boolean onlyAvailable
    ) {
        return buildAvailability(branchId, specialtyId, doctorId, visitDate, session, onlyAvailable, null, "public availability", true);
    }

    @Transactional(readOnly = true)
    public AppointmentAvailabilityResponse getAvailabilityExcludingAppointment(
            Long branchId,
            Long specialtyId,
            Long doctorId,
            LocalDate visitDate,
            BranchSessionType session,
            boolean onlyAvailable,
            Long excludedAppointmentId
    ) {
        return buildAvailability(branchId, specialtyId, doctorId, visitDate, session, onlyAvailable, excludedAppointmentId, "reschedule availability", false);
    }

    private AppointmentAvailabilityResponse buildAvailability(
            Long branchId,
            Long specialtyId,
            Long doctorId,
            LocalDate visitDate,
            BranchSessionType session,
            boolean onlyAvailable,
            Long excludedAppointmentId,
            String operation,
            boolean publicSafeDoctorErrors
    ) {
        long started = System.nanoTime();
        AvailabilityCacheKey cacheKey = new AvailabilityCacheKey(branchId, specialtyId, doctorId, visitDate, session, onlyAvailable);
        boolean cacheable = excludedAppointmentId == null;

        BranchSpecialty branchSpecialty = branchSpecialtyService.getActiveBranchSpecialtyEntity(branchId, specialtyId);

        DoctorProfile doctor = doctorProfileRepository.findByIdAndBranch_Id(doctorId, branchId)
                                                      .orElseThrow(() -> new ApiException(ErrorCode.DOCTOR_NOT_FOUND));

        assertDoctorBookable(doctor, publicSafeDoctorErrors);

        if (!doctorProfileRepository.existsDoctorSpecialty(doctorId, specialtyId)) {
            throw new ApiException(ErrorCode.DOCTOR_NOT_IN_SPECIALTY, "Bác sĩ không thuộc chuyên khoa đã chọn");
        }

        if (cacheable) {
            AppointmentAvailabilityResponse cached = getCachedAvailability(cacheKey);
            if (cached != null) {
                logAvailabilityDuration(operation + " cache_hit", started, branchId, specialtyId, doctorId, visitDate, session, cached.getSlots().size());
                return cached;
            }
        }

        BranchSession branchSession = branchSessionRepository
                .findByBranch_IdAndSessionAndStatus(branchId, session, "ACTIVE")
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REQUEST, "Chi nhánh chưa mở buổi khám này"));

        doctorWorkScheduleRepository
                .findByDoctor_IdAndWorkDateAndSession(doctorId, visitDate, session)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.APPOINTMENT_SLOT_NOT_AVAILABLE,
                        "Bác sĩ không có lịch làm việc trong buổi đã chọn"
                ));

        boolean onApprovedLeave = doctorLeaveRequestRepository
                .findByDoctor_IdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        doctorId,
                        DoctorLeaveRequestStatus.APPROVED,
                        visitDate,
                        visitDate
                )
                .stream()
                .anyMatch(leave -> isSessionCovered(
                        visitDate,
                        session,
                        leave.getStartDate(),
                        leave.getEndDate(),
                        leave.getStartSession(),
                        leave.getEndSession()
                ));

        if (onApprovedLeave) {
            throw new ApiException(ErrorCode.APPOINTMENT_SLOT_NOT_AVAILABLE, "Bác sĩ đang nghỉ trong buổi đã chọn");
        }

        int slotMinutes = resolveSlotMinutes(branchSpecialty);
        int slotCapacity = resolveSlotCapacity(branchSession);

        List<Appointment> candidates = appointmentRepository
                .findByDoctor_IdAndVisitDateAndSessionAndStatusInOrderByEtaStartAsc(
                        doctorId, visitDate, session, BLOCKING_STATUSES
                );
        if (excludedAppointmentId != null) {
            candidates = candidates.stream()
                                   .filter(appointment -> !excludedAppointmentId.equals(appointment.getId()))
                                   .toList();
        }
        List<AppointmentSlotHold> activeHolds = appointmentSlotHoldRepository.findActiveByDoctorAndDateRange(
                doctorId,
                visitDate,
                visitDate,
                AppointmentSlotAvailabilityGuard.BLOCKING_HOLD_STATUSES,
                LocalDateTime.now()
        );

        List<BookableSlotResponse> slots = buildSlots(branchSession, visitDate, slotMinutes, slotCapacity, candidates, activeHolds, onlyAvailable);

        int bookedSlots = (int) slots.stream().filter(slot -> !slot.isAvailable()).count();
        int remainingSlots = slots.stream().mapToInt(BookableSlotResponse::getRemainingSlots).sum();

        var selectedSpecialty = doctor.getDoctorSpecialties() == null
                ? null
                : doctor.getDoctorSpecialties().stream()
                        .filter(doctorSpecialty -> doctorSpecialty.getSpecialty() != null
                                && specialtyId.equals(doctorSpecialty.getSpecialty().getId()))
                        .map(doctorSpecialty -> doctorSpecialty.getSpecialty())
                        .findFirst()
                        .orElse(null);

        AppointmentAvailabilityResponse response = AppointmentAvailabilityResponse.builder()
                .branchId(branchId)
                .branchNameVn(doctor.getBranch().getNameVn())
                .branchNameEn(doctor.getBranch().getNameEn())
                .specialtyId(specialtyId)
                .specialtyNameVn(selectedSpecialty != null ? selectedSpecialty.getNameVn() : null)
                .specialtyNameEn(selectedSpecialty != null ? selectedSpecialty.getNameEn() : null)
                .doctorId(doctorId)
                .doctorName(doctor.getFullName())
                .visitDate(visitDate)
                .session(session)
                .slotMinutes(slotMinutes)
                .totalSlots(slots.size())
                .bookedSlots(bookedSlots)
                .remainingSlots(remainingSlots)
                .slots(slots)
                .build();

        if (cacheable) {
            putCachedAvailability(cacheKey, response);
        }
        logAvailabilityDuration(operation, started, branchId, specialtyId, doctorId, visitDate, session, slots.size());
        return response;
    }

    @Transactional(readOnly = true)
    public AvailabilityPreviewResponse getAvailabilityPreview(
            Long branchId,
            Long specialtyId,
            Long doctorId,
            LocalDate fromDate,
            int days
    ) {
        long started = System.nanoTime();
        int safeDays = Math.max(1, Math.min(days, 14));
        LocalDate toDate = fromDate.plusDays(safeDays - 1L);

        BranchSpecialty branchSpecialty = branchSpecialtyService.getActiveBranchSpecialtyEntity(branchId, specialtyId);
        DoctorProfile doctor = doctorProfileRepository.findByIdAndBranch_Id(doctorId, branchId)
                                                      .orElseThrow(() -> new ApiException(ErrorCode.DOCTOR_NOT_FOUND));

        doctorOperationalGuardService.assertDoctorPublicBookable(doctor);

        if (!doctorProfileRepository.existsDoctorSpecialty(doctorId, specialtyId)) {
            throw new ApiException(ErrorCode.DOCTOR_NOT_IN_SPECIALTY, "Bác sĩ không thuộc chuyên khoa đã chọn");
        }

        Map<BranchSessionType, BranchSession> branchSessionsByType = new LinkedHashMap<>();
        branchSessionRepository.findByBranch_IdAndStatusOrderBySessionAsc(branchId, "ACTIVE")
                               .forEach(branchSession -> branchSessionsByType.put(branchSession.getSession(), branchSession));
        if (branchSessionsByType.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Chi nhánh chưa mở buổi khám");
        }

        Set<AvailabilityKey> workScheduleKeys = doctorWorkScheduleRepository
                .findByDoctor_IdAndWorkDateBetweenOrderByWorkDateAscSessionAsc(doctorId, fromDate, toDate)
                .stream()
                .map(schedule -> new AvailabilityKey(schedule.getWorkDate(), schedule.getSession()))
                .collect(Collectors.toSet());

        List<DoctorLeaveRequest> approvedLeaves = doctorLeaveRequestRepository
                .findByDoctor_IdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        doctorId,
                        DoctorLeaveRequestStatus.APPROVED,
                        toDate,
                        fromDate
                );

        Map<AvailabilityKey, List<Appointment>> appointmentsByGroup = new LinkedHashMap<>();
        appointmentRepository.findByDoctor_IdAndVisitDateBetweenAndStatusIn(
                doctorId,
                fromDate,
                toDate,
                List.copyOf(BLOCKING_STATUSES)
        ).forEach(appointment -> appointmentsByGroup
                .computeIfAbsent(new AvailabilityKey(appointment.getVisitDate(), appointment.getSession()), ignored -> new ArrayList<>())
                .add(appointment));
        Map<AvailabilityKey, List<AppointmentSlotHold>> holdsByGroup = new LinkedHashMap<>();
        appointmentSlotHoldRepository.findActiveByDoctorAndDateRange(
                doctorId,
                fromDate,
                toDate,
                AppointmentSlotAvailabilityGuard.BLOCKING_HOLD_STATUSES,
                LocalDateTime.now()
        ).forEach(hold -> holdsByGroup
                .computeIfAbsent(new AvailabilityKey(hold.getVisitDate(), hold.getSession()), ignored -> new ArrayList<>())
                .add(hold));

        int slotMinutes = resolveSlotMinutes(branchSpecialty);
        List<AvailabilityPreviewResponse.DayPreview> dayPreviews = new ArrayList<>();
        for (int dayOffset = 0; dayOffset < safeDays; dayOffset++) {
            LocalDate visitDate = fromDate.plusDays(dayOffset);
            List<AvailabilityPreviewResponse.SessionPreview> sessionPreviews = new ArrayList<>();

            for (BranchSession branchSession : branchSessionsByType.values()) {
                AvailabilityKey key = new AvailabilityKey(visitDate, branchSession.getSession());
                List<BookableSlotResponse> slots = List.of();

                if (workScheduleKeys.contains(key) && !isDoctorOnApprovedLeave(approvedLeaves, visitDate, branchSession.getSession())) {
                    slots = buildSlots(
                            branchSession,
                            visitDate,
                            slotMinutes,
                            resolveSlotCapacity(branchSession),
                            appointmentsByGroup.getOrDefault(key, List.of()),
                            holdsByGroup.getOrDefault(key, List.of()),
                            true
                    );
                }

                sessionPreviews.add(AvailabilityPreviewResponse.SessionPreview.builder()
                        .session(branchSession.getSession())
                        .availableCount(slots.size())
                        .firstAvailableSlot(slots.isEmpty() ? null : slots.get(0).getStartTime())
                        .build());
            }

            dayPreviews.add(AvailabilityPreviewResponse.DayPreview.builder()
                    .visitDate(visitDate)
                    .sessions(sessionPreviews)
                    .build());
        }

        AvailabilityPreviewResponse response = AvailabilityPreviewResponse.builder()
                .days(dayPreviews)
                .build();
        log.info("public availability preview durationMs={} branchId={} specialtyId={} doctorId={} days={}",
                durationMs(started), branchId, specialtyId, doctorId, safeDays);
        return response;
    }

    public int resolveSlotMinutes(Long branchId, Long specialtyId) {
        return branchSpecialtyService.resolveSlotMinutes(branchId, specialtyId, FIXED_SLOT_MINUTES);
    }

    private int resolveSlotMinutes(BranchSpecialty branchSpecialty) {
        Integer override = branchSpecialty != null ? branchSpecialty.getSlotMinutesOverride() : null;
        return override != null && override > 0 ? override : FIXED_SLOT_MINUTES;
    }

    public int resolveSlotCapacity(BranchSession branchSession) {
        // branchSession.capacityOverride khong dai dien cho so benh nhan toi da trong tung khung gio cua mot bac si.
        // Voi mo hinh hien tai, mot bac si trong mot slot chi nhan mot benh nhan. Neu sau nay can overbooking
        // theo slot, hay them field rieng nhu doctorSlotCapacity hoac slotCapacityOverride.
        log.debug("Resolved appointment slot capacity fixedSlotCapacity={}", DEFAULT_SLOT_CAPACITY);
        return DEFAULT_SLOT_CAPACITY;
    }

    public long countOverlappingAppointments(List<Appointment> appointments, LocalTime slotStart, LocalTime slotEnd) {
        if (appointments == null || appointments.isEmpty() || slotStart == null || slotEnd == null || !slotEnd.isAfter(slotStart)) {
            return 0;
        }

        return toIntervals(appointments).stream()
                                        .filter(interval -> interval.start().isBefore(slotEnd) && interval.end().isAfter(slotStart))
                                        .count();
    }

    private void assertDoctorBookable(DoctorProfile doctor, boolean publicSafeDoctorErrors) {
        if (publicSafeDoctorErrors) {
            doctorOperationalGuardService.assertDoctorPublicBookable(doctor);
            return;
        }
        doctorOperationalGuardService.assertDoctorBookable(doctor);
    }

    private List<BookableSlotResponse> buildSlots(
            BranchSession branchSession,
            LocalDate visitDate,
            int slotMinutes,
            int slotCapacity,
            List<Appointment> candidates,
            List<AppointmentSlotHold> activeHolds,
            boolean onlyAvailable
    ) {
        List<AppointmentInterval> intervals = new ArrayList<>();
        intervals.addAll(toIntervals(candidates));
        intervals.addAll(toHoldIntervals(activeHolds));
        intervals = intervals.stream()
                             .sorted(Comparator.comparing(AppointmentInterval::start)
                                               .thenComparing(AppointmentInterval::end))
                             .toList();
        List<BookableSlotResponse> slots = new ArrayList<>();
        LocalTime cursor = branchSession.getStartTime();
        int firstPossibleOverlapIndex = 0;

        while (!cursor.plusMinutes(slotMinutes).isAfter(branchSession.getEndTime())) {
            LocalTime end = cursor.plusMinutes(slotMinutes);

            while (firstPossibleOverlapIndex < intervals.size()
                    && !intervals.get(firstPossibleOverlapIndex).end().isAfter(cursor)) {
                firstPossibleOverlapIndex++;
            }

            int bookedCount = 0;
            for (int i = firstPossibleOverlapIndex; i < intervals.size(); i++) {
                AppointmentInterval interval = intervals.get(i);
                if (!interval.start().isBefore(end)) {
                    break;
                }
                if (interval.end().isAfter(cursor)) {
                    bookedCount++;
                }
            }

            int remaining = Math.max(slotCapacity - bookedCount, 0);
            boolean available = remaining > 0;

            if (!isSlotStillBookable(visitDate, cursor)) {
                available = false;
                remaining = 0;
            }

            BookableSlotResponse slot = BookableSlotResponse.builder()
                    .startTime(cursor)
                    .endTime(end)
                    .available(available)
                    .capacity(slotCapacity)
                    .bookedCount(bookedCount)
                    .remainingSlots(remaining)
                    .build();

            if (!onlyAvailable || slot.isAvailable()) {
                slots.add(slot);
            }

            cursor = end;
        }

        return slots;
    }

    private List<AppointmentInterval> toIntervals(List<Appointment> appointments) {
        if (appointments == null || appointments.isEmpty()) {
            return List.of();
        }

        return appointments.stream()
                           .map(this::toInterval)
                           .filter(java.util.Objects::nonNull)
                           .sorted(Comparator.comparing(AppointmentInterval::start)
                                             .thenComparing(AppointmentInterval::end))
                           .toList();
    }

    private List<AppointmentInterval> toHoldIntervals(List<AppointmentSlotHold> holds) {
        if (holds == null || holds.isEmpty()) {
            return List.of();
        }
        return holds.stream()
                    .filter(hold -> hold.getSlotStart() != null
                            && hold.getSlotEnd() != null
                            && hold.getSlotEnd().isAfter(hold.getSlotStart()))
                    .map(hold -> new AppointmentInterval(hold.getSlotStart(), hold.getSlotEnd()))
                    .toList();
    }

    private AppointmentInterval toInterval(Appointment appointment) {
        if (appointment == null || appointment.getEtaStart() == null) {
            return null;
        }

        LocalTime appointmentStart = appointment.getEtaStart();
        LocalTime appointmentEnd = resolveAppointmentEnd(appointment);
        if (appointmentEnd == null || !appointmentEnd.isAfter(appointmentStart)) {
            return null;
        }

        return new AppointmentInterval(appointmentStart, appointmentEnd);
    }

    private LocalTime resolveAppointmentEnd(Appointment appointment) {
        if (appointment == null || appointment.getEtaStart() == null) {
            return null;
        }

        if (appointment.getEtaEnd() != null && appointment.getEtaEnd().isAfter(appointment.getEtaStart())) {
            return appointment.getEtaEnd();
        }

        Integer slotMinutes = appointment.getSlotMinutes();
        if (slotMinutes != null && slotMinutes > 0) {
            return appointment.getEtaStart().plusMinutes(slotMinutes);
        }

        return appointment.getEtaStart().plusMinutes(FIXED_SLOT_MINUTES);
    }

    public boolean isSlotStillBookable(LocalDate visitDate, LocalTime slotStart) {
        if (visitDate == null || slotStart == null) {
            return false;
        }

        LocalDate today = LocalDate.now();
        if (visitDate.isAfter(today)) {
            return true;
        }
        if (visitDate.isBefore(today)) {
            return false;
        }

        return slotStart.isAfter(LocalTime.now());
    }

    public void assertSlotStillBookable(LocalDate visitDate, LocalTime slotStart) {
        if (!isSlotStillBookable(visitDate, slotStart)) {
            throw new ApiException(
                    ErrorCode.APPOINTMENT_SLOT_NOT_AVAILABLE,
                    "Khung giờ đã qua hoặc không còn khả dụng"
            );
        }
    }

    public void evictAvailabilityCacheForDoctorDateSession(Long doctorId, LocalDate visitDate, BranchSessionType session) {
        availabilityCache.asMap().keySet().removeIf(key -> key.doctorId().equals(doctorId)
                && key.visitDate().equals(visitDate)
                && key.session() == session);
    }

    private boolean isDoctorOnApprovedLeave(
            List<DoctorLeaveRequest> approvedLeaves,
            LocalDate visitDate,
            BranchSessionType session
    ) {
        if (approvedLeaves == null || approvedLeaves.isEmpty()) {
            return false;
        }

        return approvedLeaves.stream()
                             .anyMatch(leave -> isSessionCovered(
                                     visitDate,
                                     session,
                                     leave.getStartDate(),
                                     leave.getEndDate(),
                                     leave.getStartSession(),
                                     leave.getEndSession()
                             ));
    }

    private boolean isSessionCovered(
            LocalDate targetDate,
            BranchSessionType targetSession,
            LocalDate startDate,
            LocalDate endDate,
            BranchSessionType startSession,
            BranchSessionType endSession
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

    private AppointmentAvailabilityResponse getCachedAvailability(AvailabilityCacheKey key) {
        return availabilityCache.getIfPresent(key);
    }

    private void putCachedAvailability(AvailabilityCacheKey key, AppointmentAvailabilityResponse response) {
        availabilityCache.put(key, response);
    }

    private void logAvailabilityDuration(
            String operation,
            long started,
            Long branchId,
            Long specialtyId,
            Long doctorId,
            LocalDate visitDate,
            BranchSessionType session,
            int slotCount
    ) {
        log.info("{} durationMs={} branchId={} specialtyId={} doctorId={} visitDate={} session={} slots={}",
                operation, durationMs(started), branchId, specialtyId, doctorId, visitDate, session, slotCount);
    }

    private long durationMs(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }

    private record AvailabilityKey(LocalDate visitDate, BranchSessionType session) {
    }

    private record AppointmentInterval(LocalTime start, LocalTime end) {
    }

    private record AvailabilityCacheKey(
            Long branchId,
            Long specialtyId,
            Long doctorId,
            LocalDate visitDate,
            BranchSessionType session,
            boolean onlyAvailable
    ) {
    }
}
