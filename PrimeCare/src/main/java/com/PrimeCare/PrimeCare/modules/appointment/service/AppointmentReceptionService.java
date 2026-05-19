package com.PrimeCare.PrimeCare.modules.appointment.service;

import com.PrimeCare.PrimeCare.modules.appointment.dto.request.CreateWalkInAppointmentRequest;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.AppointmentAdminResponse;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.ReceptionQueueSummaryResponse;
import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.doctor_schedule.entity.DoctorWorkSchedule;
import com.PrimeCare.PrimeCare.modules.doctor_schedule.repository.DoctorWorkScheduleRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.service.DoctorOperationalGuardService;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.BranchSession;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.repository.BranchRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.repository.BranchSessionRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.repository.DoctorProfileRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.entity.Specialty;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.repository.SpecialtyRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.service.BranchSpecialtyService;
import com.PrimeCare.PrimeCare.modules.notification.service.InternalNotificationService;
import com.PrimeCare.PrimeCare.modules.patient.entity.Patient;
import com.PrimeCare.PrimeCare.modules.patient.service.PatientService;
import com.PrimeCare.PrimeCare.modules.realtime.service.RealtimeEventPublisher;
import com.PrimeCare.PrimeCare.modules.triage.TriageJsonSupport;
import com.PrimeCare.PrimeCare.modules.triage.TriageMatchedRule;
import com.PrimeCare.PrimeCare.modules.triage.TriageMatchedTerm;
import com.PrimeCare.PrimeCare.modules.triage.TriagePriorityNormalizer;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentSourceType;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.enums.ArrivalStatus;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import com.PrimeCare.PrimeCare.shared.utils.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AppointmentReceptionService {

    private static final EnumSet<AppointmentStatus> ACTIVE_QUEUE_STATUSES = EnumSet.of(
            AppointmentStatus.REQUESTED,
            AppointmentStatus.CONFIRMED,
            AppointmentStatus.CHECKED_IN
    );

    private static final EnumSet<AppointmentStatus> BLOCKING_STATUSES = EnumSet.of(
            AppointmentStatus.REQUESTED,
            AppointmentStatus.CONFIRMED,
            AppointmentStatus.CHECKED_IN
    );

    private static final EnumSet<AppointmentStatus> MARK_ARRIVED_STATUSES = EnumSet.of(
            AppointmentStatus.REQUESTED,
            AppointmentStatus.CONFIRMED,
            AppointmentStatus.CHECKED_IN
    );

    private final AppointmentRepository appointmentRepository;
    private final AppointmentAvailabilityService availabilityService;
    private final AppointmentSlotAvailabilityGuard slotAvailabilityGuard;
    private final AppointmentCodeGenerator appointmentCodeGenerator;
    private final ReceptionQueueAllocator receptionQueueAllocator;
    private final PatientService patientService;

    private final BranchRepository branchRepository;
    private final SpecialtyRepository specialtyRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final BranchSpecialtyService branchSpecialtyService;
    private final UserRepository userRepository;
    private final DoctorWorkScheduleRepository doctorWorkScheduleRepository;
    private final BranchSessionRepository branchSessionRepository;
    private final DoctorOperationalGuardService doctorOperationalGuardService;
    private final AppointmentQueueService appointmentQueueService;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final AppointmentStatusHistoryService appointmentStatusHistoryService;
    private final AuditLogService auditLogService;
    private final InternalNotificationService internalNotificationService;

    @Transactional
    public AppointmentAdminResponse createWalkIn(CreateWalkInAppointmentRequest request, Long staffUserId) {
        if (request.getVisitDate().isBefore(LocalDate.now())) {
            throw new ApiException(ErrorCode.APPOINTMENT_INVALID_DATE);
        }

        Branch branch = branchRepository.findById(request.getBranchId())
                                        .orElseThrow(() -> new ApiException(ErrorCode.BRANCH_NOT_FOUND));

        Specialty specialty = specialtyRepository.findById(request.getSpecialtyId())
                                                 .orElseThrow(() -> new ApiException(ErrorCode.SPECIALTY_NOT_FOUND));

        DoctorProfile doctor = doctorProfileRepository.findByIdAndBranch_Id(
                                                              request.getDoctorId(),
                                                              request.getBranchId()
                                                      )
                                                      .orElseThrow(() -> new ApiException(ErrorCode.DOCTOR_NOT_FOUND));

        User staff = userRepository.findById(staffUserId)
                                   .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));

        branchSpecialtyService.validateBranchSpecialtyActive(request.getBranchId(), request.getSpecialtyId());

        doctorOperationalGuardService.assertDoctorBookable(doctor);

        if (!doctorProfileRepository.existsDoctorSpecialty(doctor.getId(), specialty.getId())) {
            throw new ApiException(ErrorCode.SPECIALTY_NOT_FOUND, "Bác sĩ không thuộc chuyên khoa đã chọn");
        }

        DoctorWorkSchedule lockedSchedule = doctorWorkScheduleRepository
                .findWithLockByDoctor_IdAndWorkDateAndSession(
                        request.getDoctorId(),
                        request.getVisitDate(),
                        request.getSession()
                )
                .orElseThrow(() -> new ApiException(
                        ErrorCode.APPOINTMENT_SLOT_NOT_AVAILABLE,
                        "Bác sĩ không có lịch làm việc trong buổi đã chọn"
                ));

        BranchSession branchSession = branchSessionRepository
                .findByBranch_IdAndSessionAndStatus(request.getBranchId(), request.getSession(), "ACTIVE")
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REQUEST, "Chi nhánh chưa mở buổi khám này"));

        var availability = availabilityService.getAvailability(
                request.getBranchId(),
                request.getSpecialtyId(),
                request.getDoctorId(),
                request.getVisitDate(),
                request.getSession(),
                false
        );

        var selectedSlot = availability.getSlots().stream()
                                       .filter(slot -> slot.getStartTime().equals(request.getSlotStart()))
                                       .findFirst()
                                       .orElseThrow(() -> new ApiException(
                                               ErrorCode.APPOINTMENT_SLOT_NOT_AVAILABLE,
                                               "Khung giờ không nằm trong buổi khám"
                                       ));

        availabilityService.assertSlotStillBookable(request.getVisitDate(), request.getSlotStart());

        if (!selectedSlot.isAvailable()) {
            throw new ApiException(
                    ErrorCode.APPOINTMENT_SLOT_NOT_AVAILABLE,
                    "Khung giờ đã có lịch hẹn. Vui lòng chọn khung giờ khác."
            );
        }

        int slotCapacity = availabilityService.resolveSlotCapacity(branchSession);

        var blockingAppointments = appointmentRepository.findWithLockByDoctor_IdAndVisitDateAndSessionAndStatusIn(
                doctor.getId(),
                request.getVisitDate(),
                request.getSession(),
                BLOCKING_STATUSES
        );

        long bookingCount = availabilityService.countOverlappingAppointments(
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

        slotAvailabilityGuard.assertSlotAvailable(
                doctor.getId(),
                request.getVisitDate(),
                request.getSession(),
                selectedSlot.getStartTime(),
                selectedSlot.getEndTime(),
                null,
                null
        );

        Patient patient = patientService.findOrCreateFromAppointmentData(
                request.getPatientFullName(),
                request.getPatientPhone(),
                request.getPatientEmail(),
                request.getPatientDob(),
                request.getPatientGender(),
                request.getPatientNote()
        );

        if (hasSamePatientSameSlotBooking(
                blockingAppointments,
                patient,
                request.getPatientFullName(),
                request.getPatientDob(),
                request.getPatientGender(),
                request.getSlotStart()
        )) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Bệnh nhân đã có lịch hẹn trùng khung giờ với bác sĩ này");
        }

        LocalDateTime now = LocalDateTime.now();
        boolean isToday = LocalDate.now().equals(request.getVisitDate());
        boolean shouldArriveNow = request.isArrived() && isToday;

        AppointmentStatus initialStatus = shouldArriveNow
                ? AppointmentStatus.CHECKED_IN
                : AppointmentStatus.CONFIRMED;

        ArrivalStatus initialArrivalStatus = shouldArriveNow
                ? ArrivalStatus.ARRIVED
                : ArrivalStatus.NOT_ARRIVED;

        Integer receptionQueueNo = shouldArriveNow
                ? receptionQueueAllocator.allocateNext(branch.getId(), request.getVisitDate())
                : null;

        Appointment entity = Appointment.builder()
                                        .code(appointmentCodeGenerator.generate(
                                                request.getVisitDate(),
                                                doctor.getId(),
                                                request.getSlotStart()
                                        ))
                                        .status(initialStatus)
                                        .branch(branch)
                                        .specialty(specialty)
                                        .doctor(doctor)
                                        .patient(patient)
                                        .visitDate(request.getVisitDate())
                                        .session(request.getSession())
                                        .sourceType(AppointmentSourceType.WALK_IN)
                                        .arrivalStatus(initialArrivalStatus)
                                        .receptionQueueNo(receptionQueueNo)
                                        .arrivedAt(shouldArriveNow ? now : null)
                                        .arrivedBy(shouldArriveNow ? staff : null)
                                        .queueNo(0)
                                        .slotMinutes(availability.getSlotMinutes())
                                        .etaStart(selectedSlot.getStartTime())
                                        .etaEnd(selectedSlot.getEndTime())
                                        .patientFullName(request.getPatientFullName().trim())
                                        .patientPhone(request.getPatientPhone().trim())
                                        .patientEmail(StringUtil.trimToNull(request.getPatientEmail()))
                                        .patientDob(request.getPatientDob())
                                        .patientGender(request.getPatientGender())
                                        .patientNote(StringUtil.trimToNull(request.getPatientNote()))
                                        .confirmedBy(staff)
                                        .confirmedAt(now)
                                        .build();
        if (shouldArriveNow) {
            AppointmentCheckInState.apply(entity, staff, now);
        }

        Appointment saved = appointmentRepository.save(entity);
        appointmentQueueService.recalculateProjectedQueue(
                saved.getDoctor().getId(),
                saved.getVisitDate(),
                saved.getSession()
        );
        saved = appointmentRepository.findById(saved.getId()).orElse(saved);
        publishSummaryChanged(saved);
        publishAppointmentUpdated(saved, null);
        appointmentStatusHistoryService.record(saved, null, saved.getStatus(), staff, "Tạo walk-in tại quầy");
        auditLogService.log(staff, "CREATE_WALK_IN", "APPOINTMENT", saved.getId(), null, snapshotAppointment(saved));

        if (saved.getStatus() == AppointmentStatus.CHECKED_IN) {
            notifyDoctorAppointmentCheckedIn(saved);
        } else {
            notifyDoctorAppointmentConfirmed(saved);
        }
        notifyStaffWalkInCreated(saved);

        return toAdminResponse(saved);
    }

    private boolean hasSamePatientSameSlotBooking(
            List<Appointment> appointments,
            Patient patient,
            String patientFullName,
            LocalDate patientDob,
            com.PrimeCare.PrimeCare.shared.enums.Gender patientGender,
            java.time.LocalTime slotStart
    ) {
        String normalizedName = normalizePatientName(patientFullName);

        return appointments.stream()
                           .filter(existing -> slotStart.equals(existing.getEtaStart()))
                           .anyMatch(existing -> isSamePatient(existing, patient, normalizedName, patientDob, patientGender));
    }

    private boolean isSamePatient(
            Appointment existing,
            Patient patient,
            String normalizedName,
            LocalDate patientDob,
            com.PrimeCare.PrimeCare.shared.enums.Gender patientGender
    ) {
        if (patient != null
                && patient.getId() != null
                && existing.getPatient() != null
                && patient.getId().equals(existing.getPatient().getId())) {
            return true;
        }

        return normalizedName != null
                && patientDob != null
                && patientGender != null
                && normalizedName.equals(normalizePatientName(existing.getPatientFullName()))
                && patientDob.equals(existing.getPatientDob())
                && patientGender == existing.getPatientGender();
    }

    private String normalizePatientName(String value) {
        String trimmed = StringUtil.trimToNull(value);
        return trimmed != null ? trimmed.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ") : null;
    }

    private String normalizeReceptionPriorityFilter(String value) {
        String token = TriagePriorityNormalizer.normalizeToken(value);
        if (token == null) {
            return null;
        }
        if (TriagePriorityNormalizer.NONE.equals(token) || "UNCLASSIFIED".equals(token)) {
            return TriagePriorityNormalizer.NONE;
        }
        String normalizedPriority = TriagePriorityNormalizer.normalizePriority(token);
        if (normalizedPriority == null) {
            throw new ApiException(
                    ErrorCode.VALIDATION_ERROR,
                    "Invalid triagePriority filter. Supported values: P1, P2, P3, NONE."
            );
        }
        return normalizedPriority;
    }

    @Transactional
    public AppointmentAdminResponse markArrived(Long appointmentId, Long staffUserId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                                                       .orElseThrow(() -> new ApiException(ErrorCode.APPOINTMENT_NOT_FOUND));

        User staff = userRepository.findById(staffUserId)
                                   .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));

        if (!LocalDate.now().equals(appointment.getVisitDate())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Chỉ đánh dấu đã đến cho lịch hẹn trong ngày.");
        }

        if (!MARK_ARRIVED_STATUSES.contains(appointment.getStatus())) {
            throw new ApiException(ErrorCode.APPOINTMENT_INVALID_STATUS, "Không thể đánh dấu đã đến cho lịch hiện tại");
        }

        if (appointment.getArrivalStatus() == ArrivalStatus.ARRIVED) {
            return toAdminResponse(appointment);
        }

        Map<String, Object> before = snapshotAppointment(appointment);
        AppointmentStatus previousStatus = appointment.getStatus();

        appointment.setArrivalStatus(ArrivalStatus.ARRIVED);
        appointment.setArrivedAt(LocalDateTime.now());
        appointment.setArrivedBy(staff);

        if (appointment.getReceptionQueueNo() == null) {
            appointment.setReceptionQueueNo(
                    receptionQueueAllocator.allocateNext(
                            appointment.getBranch().getId(),
                            appointment.getVisitDate()
                    )
            );
        }

        Appointment saved = appointmentRepository.save(appointment);
        publishSummaryChanged(saved);
        publishAppointmentUpdated(saved, previousStatus);
        auditLogService.log(staff, "MARK_ARRIVED", "APPOINTMENT", saved.getId(), before, snapshotAppointment(saved));

        return toAdminResponse(saved);
    }

    @Transactional
    public AppointmentAdminResponse manualCheckIn(Long appointmentId, Long staffUserId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                                                       .orElseThrow(() -> new ApiException(ErrorCode.APPOINTMENT_NOT_FOUND));

        User staff = userRepository.findById(staffUserId)
                                   .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));

        if (appointment.getStatus() == AppointmentStatus.REQUESTED) {
            throw new ApiException(ErrorCode.APPOINTMENT_INVALID_STATUS, "Lịch hẹn cần được xác nhận trước khi check-in.");
        }

        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new ApiException(ErrorCode.APPOINTMENT_INVALID_STATUS, "Chỉ lịch đã xác nhận mới được check-in.");
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
        appointmentQueueService.recalculateProjectedQueue(
                saved.getDoctor().getId(),
                saved.getVisitDate(),
                saved.getSession()
        );
        saved = appointmentRepository.findById(saved.getId()).orElse(saved);
        publishSummaryChanged(saved);
        publishAppointmentUpdated(saved, previousStatus);
        notifyDoctorAppointmentCheckedIn(saved);
        appointmentStatusHistoryService.record(saved, previousStatus, saved.getStatus(), staff, "Check-in thủ công tại quầy");
        auditLogService.log(staff, "MANUAL_CHECK_IN", "APPOINTMENT", saved.getId(), before, snapshotAppointment(saved));

        return toAdminResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<AppointmentAdminResponse> queue(
            LocalDate visitDate,
            Long branchId,
            Long doctorId,
            Long specialtyId,
            ArrivalStatus arrivalStatus,
            AppointmentSourceType sourceType,
            String triagePriority,
            Boolean overdue,
            String q,
            Pageable pageable
    ) {
        String keyword = StringUtil.trimToNull(q);
        String priorityFilter = normalizeReceptionPriorityFilter(triagePriority);
        LocalDateTime now = LocalDateTime.now();
        LocalDate overdueVisitDate = LocalDate.now();
        LocalTime overdueCutoffTime = overdueCutoffTime(now);
        Page<Appointment> page = appointmentRepository.searchReceptionQueue(
                visitDate,
                branchId,
                doctorId,
                specialtyId,
                arrivalStatus,
                sourceType,
                priorityFilter,
                ACTIVE_QUEUE_STATUSES,
                Boolean.TRUE.equals(overdue),
                overdueVisitDate,
                overdueCutoffTime,
                keyword,
                keyword != null ? keyword.toLowerCase(Locale.ROOT) : null,
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
    public ReceptionQueueSummaryResponse queueSummary(
            LocalDate visitDate,
            Long branchId,
            Long doctorId,
            Long specialtyId,
            ArrivalStatus arrivalStatus,
            AppointmentSourceType sourceType,
            String triagePriority,
            Boolean overdue,
            String q
    ) {
        String keyword = StringUtil.trimToNull(q);
        String priorityFilter = normalizeReceptionPriorityFilter(triagePriority);
        LocalDateTime now = LocalDateTime.now();
        LocalDate overdueVisitDate = LocalDate.now();
        LocalTime overdueCutoffTime = overdueCutoffTime(now);
        String keywordLower = keyword != null ? keyword.toLowerCase(Locale.ROOT) : null;
        var row = appointmentRepository.summarizeReceptionQueue(
                visitDate,
                branchId,
                doctorId,
                specialtyId,
                arrivalStatus,
                sourceType,
                priorityFilter,
                ACTIVE_QUEUE_STATUSES,
                Boolean.TRUE.equals(overdue),
                overdueVisitDate,
                overdueCutoffTime,
                keyword,
                keywordLower
        );
        long overdueCount = Boolean.TRUE.equals(overdue)
                ? (row != null ? row.getTotal() : 0)
                : appointmentRepository.countReceptionOverdue(
                        visitDate,
                        branchId,
                        doctorId,
                        specialtyId,
                        arrivalStatus,
                        sourceType,
                        priorityFilter,
                        overdueVisitDate,
                        overdueCutoffTime,
                        keyword,
                        keywordLower
                );
        long noShowFollowUpPending = appointmentRepository.countNoShowFollowUpPending(
                visitDate,
                branchId,
                doctorId,
                specialtyId,
                arrivalStatus,
                sourceType,
                priorityFilter,
                keyword,
                keywordLower
        );

        return ReceptionQueueSummaryResponse.builder()
                                            .total(row != null ? row.getTotal() : 0)
                                            .requested(row != null ? row.getRequested() : 0)
                                            .confirmed(row != null ? row.getConfirmed() : 0)
                                            .checkedIn(row != null ? row.getCheckedIn() : 0)
                                            .arrived(row != null ? row.getArrived() : 0)
                                            .notArrived(row != null ? row.getNotArrived() : 0)
                                            .walkIn(row != null ? row.getWalkIn() : 0)
                                            .priority(row != null ? row.getPriority() : 0)
                                            .urgent(row != null ? row.getUrgent() : 0)
                                            .overdue(overdueCount)
                                            .noShowFollowUpPending(noShowFollowUpPending)
                                            .build();
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
                                       .patientId(entity.getPatient() != null ? entity.getPatient().getId() : null)
                                       .visitDate(entity.getVisitDate())
                                       .session(entity.getSession())
                                       .sourceType(entity.getSourceType())
                                       .arrivalStatus(entity.getArrivalStatus())
                                       .receptionQueueNo(entity.getReceptionQueueNo())
                                       .arrivedAt(entity.getArrivedAt())
                                       .arrivedByName(entity.getArrivedBy() != null ? resolveUserDisplayName(entity.getArrivedBy()) : null)
                                       .queueNo(entity.getQueueNo())
                                       .slotMinutes(entity.getSlotMinutes())
                                       .etaStart(entity.getEtaStart())
                                       .etaEnd(entity.getEtaEnd())
                                       .patientFullName(entity.getPatientFullName())
                                       .patientPhone(entity.getPatientPhone())
                                       .patientEmail(entity.getPatientEmail())
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
                                       .receptionPriority(effectivePriority(entity))
                                       .receptionNote(entity.getTriageNote())
                                       .effectivePriority(effectivePriority(entity))
                                       .effectivePrioritySource(effectivePrioritySource(entity))
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
                                       .confirmedByName(entity.getConfirmedBy() != null ? resolveUserDisplayName(entity.getConfirmedBy()) : null)
                                       .checkedInAt(entity.getCheckedInAt())
                                       .checkedInByName(entity.getCheckedInBy() != null ? resolveUserDisplayName(entity.getCheckedInBy()) : null)
                                       .checkedInLate(Boolean.TRUE.equals(entity.getCheckedInLate()))
                                       .lateMinutes(entity.getLateMinutes() != null ? entity.getLateMinutes() : 0)
                                       .noShowMarkedAt(entity.getNoShowMarkedAt())
                                       .noShowMarkedByName(entity.getNoShowMarkedBy() != null ? resolveUserDisplayName(entity.getNoShowMarkedBy()) : null)
                                       .noShowNote(entity.getNoShowNote())
                                       .canMarkNoShow(noShowBlockedReason(entity) == null)
                                       .noShowEligibleAt(noShowEligibleAt(entity))
                                       .noShowBlockedReason(noShowBlockedReason(entity))
                                       .followUpPending(Boolean.TRUE.equals(entity.getFollowUpPending()))
                                       .rescheduledFromAppointmentId(entity.getRescheduledFromAppointment() != null ? entity.getRescheduledFromAppointment().getId() : null)
                                       .rescheduledToAppointmentId(resolveRescheduledToAppointmentId(entity.getId()))
                                       .build();
    }

    private void publishSummaryChanged(Appointment appointment) {
        if (appointment.getDoctor() != null && appointment.getVisitDate() != null && appointment.getSession() != null) {
            availabilityService.evictAvailabilityCacheForDoctorDateSession(
                    appointment.getDoctor().getId(),
                    appointment.getVisitDate(),
                    appointment.getSession()
            );
        }
        if (appointment.getBranch() != null && appointment.getVisitDate() != null) {
            realtimeEventPublisher.publishAppointmentSummaryChanged(
                    appointment.getBranch().getId(),
                    appointment.getVisitDate()
            );
        }
    }

    private void publishAppointmentUpdated(Appointment appointment, AppointmentStatus previousStatus) {
        if (appointment.getBranch() != null && appointment.getVisitDate() != null) {
            realtimeEventPublisher.publishAppointmentUpdated(
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
            );
        }
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
                              "Có lịch hẹn mới",
                              "Bệnh nhân " + appointment.getPatientFullName() + " đã được thêm vào danh sách khám.",
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
                              "Bệnh nhân đã sẵn sàng khám",
                              "Bệnh nhân " + appointment.getPatientFullName() + " đã check-in tại quầy.",
                              "/app/doctor/appointments",
                              "APPOINTMENT",
                              appointment.getId()
                      ));
    }

    private void notifyStaffWalkInCreated(Appointment appointment) {
        String message = "Lượt walk-in " + appointment.getCode() + " của bệnh nhân "
                + appointment.getPatientFullName() + " vừa được tạo tại quầy.";
        internalNotificationService.notifyRole(
                UserRole.STAFF,
                "WALK_IN_CREATED",
                InternalNotificationService.SEVERITY_INFO,
                "Có lượt walk-in mới",
                message,
                "/app/appointments/" + appointment.getId() + "/process",
                "APPOINTMENT",
                appointment.getId()
        );
        internalNotificationService.notifyRole(
                UserRole.OPERATIONS_ADMIN,
                "WALK_IN_CREATED",
                InternalNotificationService.SEVERITY_INFO,
                "Có lượt walk-in mới",
                message,
                "/app/appointments/" + appointment.getId() + "/process",
                "APPOINTMENT",
                appointment.getId()
        );
    }

    private Long resolveRescheduledToAppointmentId(Long appointmentId) {
        if (appointmentId == null) {
            return null;
        }
        return appointmentRepository.findFirstByRescheduledFromAppointment_IdOrderByCreatedAtDesc(appointmentId)
                                    .map(Appointment::getId)
                                    .orElse(null);
    }

    private String effectivePriority(Appointment appointment) {
        String staffPriority = StringUtil.trimToNull(appointment.getTriagePriority());
        if (staffPriority != null) {
            return staffPriority;
        }
        return StringUtil.trimToNull(appointment.getPreTriagePriority());
    }

    private String effectivePrioritySource(Appointment appointment) {
        if (StringUtil.trimToNull(appointment.getTriagePriority()) != null) {
            return "STAFF_CONFIRMED";
        }
        if (StringUtil.trimToNull(appointment.getPreTriagePriority()) != null) {
            return "SYSTEM_PRE_TRIAGE";
        }
        return "NONE";
    }

    private LocalDateTime noShowEligibleAt(Appointment appointment) {
        return AppointmentTimingPolicy.noShowEligibleAt(appointment);
    }

    private String noShowBlockedReason(Appointment appointment) {
        return AppointmentTimingPolicy.noShowBlockedReason(appointment, LocalDateTime.now());
    }

    private LocalTime overdueCutoffTime(LocalDateTime now) {
        if (!now.toLocalDate().equals(LocalDate.now())) {
            return LocalTime.MIN;
        }
        return now.toLocalTime();
    }

    private Map<String, Object> snapshotAppointment(Appointment appointment) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", appointment.getId());
        data.put("code", appointment.getCode());
        data.put("status", appointment.getStatus() != null ? appointment.getStatus().name() : null);
        data.put("visitDate", appointment.getVisitDate());
        data.put("session", appointment.getSession() != null ? appointment.getSession().name() : null);
        data.put("branchId", appointment.getBranch() != null ? appointment.getBranch().getId() : null);
        data.put("specialtyId", appointment.getSpecialty() != null ? appointment.getSpecialty().getId() : null);
        data.put("doctorId", appointment.getDoctor() != null ? appointment.getDoctor().getId() : null);
        data.put("sourceType", appointment.getSourceType() != null ? appointment.getSourceType().name() : null);
        data.put("arrivalStatus", appointment.getArrivalStatus() != null ? appointment.getArrivalStatus().name() : null);
        data.put("receptionQueueNo", appointment.getReceptionQueueNo());
        data.put("arrivedAt", appointment.getArrivedAt());
        data.put("arrivedById", appointment.getArrivedBy() != null ? appointment.getArrivedBy().getId() : null);
        data.put("checkedInAt", appointment.getCheckedInAt());
        data.put("checkedInById", appointment.getCheckedInBy() != null ? appointment.getCheckedInBy().getId() : null);
        data.put("checkedInLate", appointment.getCheckedInLate());
        data.put("lateMinutes", appointment.getLateMinutes());
        data.put("noShowMarkedAt", appointment.getNoShowMarkedAt());
        data.put("noShowNote", appointment.getNoShowNote());
        data.put("followUpPending", appointment.getFollowUpPending());
        data.put("confirmedAt", appointment.getConfirmedAt());
        data.put("confirmedById", appointment.getConfirmedBy() != null ? appointment.getConfirmedBy().getId() : null);
        data.put("patientId", appointment.getPatient() != null ? appointment.getPatient().getId() : null);
        data.put("patientFullName", appointment.getPatientFullName());
        data.put("patientPhone", appointment.getPatientPhone());
        data.put("triagePriority", appointment.getTriagePriority());
        data.put("triageReviewStatus", appointment.getTriageReviewStatus());
        return data;
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
