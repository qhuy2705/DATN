package com.PrimeCare.PrimeCare.modules.booking_restriction.service;

import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.booking_restriction.dto.request.CreateBookingRestrictionOverrideRequest;
import com.PrimeCare.PrimeCare.modules.booking_restriction.dto.request.CreateStaffPardonRequest;
import com.PrimeCare.PrimeCare.modules.booking_restriction.dto.request.CreateViolationEventRequest;
import com.PrimeCare.PrimeCare.modules.booking_restriction.dto.request.LiftBookingRestrictionRequest;
import com.PrimeCare.PrimeCare.modules.booking_restriction.dto.request.VoidViolationEventRequest;
import com.PrimeCare.PrimeCare.modules.booking_restriction.dto.response.BookingRestrictionAdminResponse;
import com.PrimeCare.PrimeCare.modules.booking_restriction.dto.response.BookingRestrictionOverrideResponse;
import com.PrimeCare.PrimeCare.modules.booking_restriction.dto.response.BookingRestrictionSummaryResponse;
import com.PrimeCare.PrimeCare.modules.booking_restriction.dto.response.PatientViolationEventResponse;
import com.PrimeCare.PrimeCare.modules.booking_restriction.entity.BookingRestrictionOverride;
import com.PrimeCare.PrimeCare.modules.booking_restriction.entity.PatientBookingRestriction;
import com.PrimeCare.PrimeCare.modules.booking_restriction.entity.PatientViolationEvent;
import com.PrimeCare.PrimeCare.modules.booking_restriction.enums.BookingRestrictionLevel;
import com.PrimeCare.PrimeCare.modules.booking_restriction.enums.BookingRestrictionOverrideStatus;
import com.PrimeCare.PrimeCare.modules.booking_restriction.enums.BookingRestrictionStatus;
import com.PrimeCare.PrimeCare.modules.booking_restriction.enums.ViolationEventType;
import com.PrimeCare.PrimeCare.modules.booking_restriction.repository.BookingRestrictionOverrideRepository;
import com.PrimeCare.PrimeCare.modules.booking_restriction.repository.PatientBookingRestrictionRepository;
import com.PrimeCare.PrimeCare.modules.booking_restriction.repository.PatientViolationEventRepository;
import com.PrimeCare.PrimeCare.modules.patient.entity.Patient;
import com.PrimeCare.PrimeCare.modules.patient.repository.PatientRepository;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import com.PrimeCare.PrimeCare.shared.utils.StringUtil;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BookingRestrictionAdminService {

    private static final int DEFAULT_OVERRIDE_HOURS = 24;
    private static final int MAX_OVERRIDE_HOURS = 72;

    private final PatientBookingRestrictionRepository restrictionRepository;
    private final PatientViolationEventRepository violationEventRepository;
    private final BookingRestrictionOverrideRepository overrideRepository;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final BookingIdentityService bookingIdentityService;
    private final BookingRestrictionPolicyService bookingRestrictionPolicyService;
    private final PatientViolationEventService patientViolationEventService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public PageResponse<BookingRestrictionSummaryResponse> list(
            BookingRestrictionStatus status,
            BookingRestrictionLevel level,
            String periodMonth,
            String q,
            Pageable pageable
    ) {
        Page<PatientBookingRestriction> page = restrictionRepository.findAll(
                restrictionSpecification(status, level, periodMonth, q),
                pageable
        );

        return PageResponse.<BookingRestrictionSummaryResponse>builder()
                .items(page.getContent().stream().map(this::toSummaryResponse).toList())
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
    public BookingRestrictionAdminResponse detail(Long id) {
        PatientBookingRestriction restriction = getRestriction(id);
        return toAdminResponse(restriction);
    }

    @Transactional
    public BookingRestrictionAdminResponse lift(Long id, LiftBookingRestrictionRequest request, Long staffUserId) {
        User staff = getUser(staffUserId);
        PatientBookingRestriction restriction = getRestriction(id);
        PatientBookingRestriction saved = bookingRestrictionPolicyService.liftRestriction(restriction, staff, request.getReason());
        return toAdminResponse(saved);
    }

    @Transactional
    public BookingRestrictionOverrideResponse overrideOnce(
            Long id,
            CreateBookingRestrictionOverrideRequest request,
            Long staffUserId
    ) {
        User staff = getUser(staffUserId);
        String reason = requireReason(request.getReason());
        int validHours = request.getValidHours() != null ? request.getValidHours() : DEFAULT_OVERRIDE_HOURS;
        if (validHours < 1 || validHours > MAX_OVERRIDE_HOURS) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Thời hạn override phải từ 1 đến 72 giờ.");
        }

        PatientBookingRestriction restriction = getRestriction(id);
        BookingRestrictionOverride override = BookingRestrictionOverride.builder()
                .patient(restriction.getPatient())
                .identityKeyHash(restriction.getIdentityKeyHash())
                .restriction(restriction)
                .status(BookingRestrictionOverrideStatus.ACTIVE)
                .expiresAt(LocalDateTime.now().plusHours(validHours))
                .reason(reason)
                .createdBy(staff)
                .build();

        BookingRestrictionOverride saved = overrideRepository.save(override);
        auditLogService.log(staff, "CREATE_BOOKING_RESTRICTION_OVERRIDE", "BOOKING_RESTRICTION_OVERRIDE", saved.getId(), null, overrideSnapshot(saved));
        return toOverrideResponse(saved);
    }

    @Transactional
    public PatientViolationEventResponse createViolation(CreateViolationEventRequest request, Long staffUserId) {
        User staff = getUser(staffUserId);
        if (request.getType() != null && request.getType() != ViolationEventType.MANUAL) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Endpoint này chỉ hỗ trợ sự kiện MANUAL.");
        }
        ViolationTarget target = resolveTarget(
                request.getPatientId(),
                request.getAppointmentId(),
                request.getPhone(),
                request.getFullName(),
                request.getDob(),
                request.getEmail()
        );
        PatientViolationEvent event = patientViolationEventService.createManualEvent(
                target.identity(),
                target.patient(),
                target.appointment(),
                request.getPoints(),
                request.getReason(),
                staff
        );
        return toEventResponse(event);
    }

    @Transactional
    public PatientViolationEventResponse voidViolation(Long eventId, VoidViolationEventRequest request, Long staffUserId) {
        User staff = getUser(staffUserId);
        return toEventResponse(patientViolationEventService.voidViolationEvent(eventId, request.getReason(), staff));
    }

    @Transactional
    public PatientViolationEventResponse pardon(CreateStaffPardonRequest request, Long staffUserId) {
        User staff = getUser(staffUserId);
        ViolationTarget target = resolveTarget(
                request.getPatientId(),
                request.getAppointmentId(),
                request.getPhone(),
                request.getFullName(),
                request.getDob(),
                request.getEmail()
        );
        PatientViolationEvent event = patientViolationEventService.createStaffPardon(
                target.identity(),
                target.patient(),
                target.appointment(),
                request.getPointsToReduce(),
                request.getReason(),
                staff
        );
        return toEventResponse(event);
    }

    private Specification<PatientBookingRestriction> restrictionSpecification(
            BookingRestrictionStatus status,
            BookingRestrictionLevel level,
            String periodMonth,
            String q
    ) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (status != null) {
                predicate = cb.and(predicate, cb.equal(root.get("status"), status));
            }
            if (level != null) {
                predicate = cb.and(predicate, cb.equal(root.get("level"), level));
            }
            String normalizedPeriod = StringUtil.trimToNull(periodMonth);
            if (normalizedPeriod != null) {
                predicate = cb.and(predicate, cb.equal(root.get("periodMonth"), normalizedPeriod));
            }
            String keyword = StringUtil.trimToNull(q);
            if (keyword != null) {
                if (query != null) {
                    query.distinct(true);
                }
                var patient = root.join("patient", JoinType.LEFT);
                String like = "%" + keyword.toLowerCase(Locale.ROOT) + "%";
                predicate = cb.and(predicate, cb.or(
                        cb.like(cb.lower(root.get("identityKeyHash")), like),
                        cb.like(cb.lower(patient.get("fullName")), like),
                        cb.like(cb.lower(patient.get("phone")), like),
                        cb.like(cb.lower(patient.get("email")), like)
                ));
            }
            return predicate;
        };
    }

    private BookingRestrictionAdminResponse toAdminResponse(PatientBookingRestriction restriction) {
        List<PatientViolationEventResponse> events = violationEventRepository
                .findByIdentityKeyHashAndPeriodMonthOrderByCreatedAtDesc(
                        restriction.getIdentityKeyHash(),
                        restriction.getPeriodMonth()
                )
                .stream()
                .map(this::toEventResponse)
                .toList();
        List<BookingRestrictionOverrideResponse> overrides = overrideRepository
                .findByRestriction_IdOrderByCreatedAtDesc(restriction.getId())
                .stream()
                .map(this::toOverrideResponse)
                .toList();

        return BookingRestrictionAdminResponse.builder()
                .restriction(toSummaryResponse(restriction))
                .events(events)
                .overrides(overrides)
                .build();
    }

    private BookingRestrictionSummaryResponse toSummaryResponse(PatientBookingRestriction restriction) {
        Patient patient = restriction.getPatient();
        ActionCapabilities capabilities = actionCapabilities(restriction);
        return BookingRestrictionSummaryResponse.builder()
                .id(restriction.getId())
                .patientId(patient != null ? patient.getId() : null)
                .patientFullName(patient != null ? patient.getFullName() : null)
                .patientPhone(patient != null ? patient.getPhone() : null)
                .patientEmail(patient != null ? patient.getEmail() : null)
                .identityKeyHash(restriction.getIdentityKeyHash())
                .periodMonth(restriction.getPeriodMonth())
                .level(restriction.getLevel())
                .status(restriction.getStatus())
                .scoreSnapshot(restriction.getScoreSnapshot())
                .currentScore(bookingRestrictionPolicyService.monthlyScore(
                        restriction.getIdentityKeyHash(),
                        restriction.getPeriodMonth()
                ))
                .reason(restriction.getReason())
                .startsAt(restriction.getStartsAt())
                .expiresAt(restriction.getExpiresAt())
                .createdById(restriction.getCreatedBy() != null ? restriction.getCreatedBy().getId() : null)
                .liftedById(restriction.getLiftedBy() != null ? restriction.getLiftedBy().getId() : null)
                .liftedAt(restriction.getLiftedAt())
                .liftReason(restriction.getLiftReason())
                .createdAt(restriction.getCreatedAt())
                .updatedAt(restriction.getUpdatedAt())
                .canLift(capabilities.canLift())
                .canOverrideOnce(capabilities.canOverrideOnce())
                .canStaffPardon(capabilities.canStaffPardon())
                .canCreateManualViolation(capabilities.canCreateManualViolation())
                .supportedActions(capabilities.supportedActions())
                .build();
    }

    private ActionCapabilities actionCapabilities(PatientBookingRestriction restriction) {
        boolean active = restriction.getStatus() == BookingRestrictionStatus.ACTIVE;
        boolean canStaffPardon = true;
        boolean canCreateManualViolation = true;

        List<String> supportedActions = new ArrayList<>();
        if (active) {
            supportedActions.add("LIFT");
            supportedActions.add("OVERRIDE_ONCE");
        }
        if (canStaffPardon) {
            supportedActions.add("STAFF_PARDON");
        }
        if (canCreateManualViolation) {
            supportedActions.add("CREATE_MANUAL_VIOLATION");
        }

        return new ActionCapabilities(
                active,
                active,
                canStaffPardon,
                canCreateManualViolation,
                List.copyOf(supportedActions)
        );
    }

    private PatientViolationEventResponse toEventResponse(PatientViolationEvent event) {
        return PatientViolationEventResponse.builder()
                .id(event.getId())
                .patientId(event.getPatient() != null ? event.getPatient().getId() : null)
                .appointmentId(event.getAppointment() != null ? event.getAppointment().getId() : null)
                .identityKeyHash(event.getIdentityKeyHash())
                .periodMonth(event.getPeriodMonth())
                .type(event.getType())
                .source(event.getSource())
                .status(event.getStatus())
                .points(event.getPoints())
                .note(event.getNote())
                .createdById(event.getCreatedBy() != null ? event.getCreatedBy().getId() : null)
                .createdAt(event.getCreatedAt())
                .voidedById(event.getVoidedBy() != null ? event.getVoidedBy().getId() : null)
                .voidedAt(event.getVoidedAt())
                .voidReason(event.getVoidReason())
                .build();
    }

    private BookingRestrictionOverrideResponse toOverrideResponse(BookingRestrictionOverride override) {
        return BookingRestrictionOverrideResponse.builder()
                .id(override.getId())
                .patientId(override.getPatient() != null ? override.getPatient().getId() : null)
                .restrictionId(override.getRestriction() != null ? override.getRestriction().getId() : null)
                .identityKeyHash(override.getIdentityKeyHash())
                .status(override.getStatus())
                .expiresAt(override.getExpiresAt())
                .reason(override.getReason())
                .createdById(override.getCreatedBy() != null ? override.getCreatedBy().getId() : null)
                .usedAt(override.getUsedAt())
                .usedAppointmentId(override.getUsedAppointment() != null ? override.getUsedAppointment().getId() : null)
                .createdAt(override.getCreatedAt())
                .build();
    }

    private ViolationTarget resolveTarget(
            Long patientId,
            Long appointmentId,
            String phone,
            String fullName,
            java.time.LocalDate dob,
            String email
    ) {
        if (appointmentId != null) {
            Appointment appointment = appointmentRepository.findById(appointmentId)
                    .orElseThrow(() -> new ApiException(ErrorCode.APPOINTMENT_NOT_FOUND));
            return new ViolationTarget(
                    appointment.getPatient(),
                    appointment,
                    bookingIdentityService.resolveAppointmentIdentity(appointment)
            );
        }

        if (patientId != null) {
            Patient patient = patientRepository.findById(patientId)
                    .orElseThrow(() -> new ApiException(ErrorCode.PATIENT_NOT_FOUND));
            return new ViolationTarget(
                    patient,
                    null,
                    bookingIdentityService.resolveStaffVerifiedPatientIdentity(patient)
            );
        }

        BookingIdentity identity = bookingIdentityService.resolvePublicIdentity(phone, fullName, dob, email);
        return new ViolationTarget(null, null, identity);
    }

    private PatientBookingRestriction getRestriction(Long id) {
        if (id == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Hạn chế đặt lịch là bắt buộc.");
        }
        return restrictionRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy hạn chế đặt lịch."));
    }

    private User getUser(Long staffUserId) {
        if (staffUserId == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        return userRepository.findById(staffUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
    }

    private String requireReason(String reason) {
        String normalizedReason = StringUtil.trimToNull(reason);
        if (normalizedReason == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Lý do là bắt buộc.");
        }
        return normalizedReason;
    }

    private Map<String, Object> overrideSnapshot(BookingRestrictionOverride override) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", override.getId());
        snapshot.put("patientId", override.getPatient() != null ? override.getPatient().getId() : null);
        snapshot.put("restrictionId", override.getRestriction() != null ? override.getRestriction().getId() : null);
        snapshot.put("identityKeyHash", override.getIdentityKeyHash());
        snapshot.put("status", override.getStatus());
        snapshot.put("expiresAt", override.getExpiresAt());
        snapshot.put("reason", override.getReason());
        snapshot.put("createdBy", override.getCreatedBy() != null ? override.getCreatedBy().getId() : null);
        snapshot.put("usedAt", override.getUsedAt());
        snapshot.put("usedAppointmentId", override.getUsedAppointment() != null ? override.getUsedAppointment().getId() : null);
        return snapshot;
    }

    private record ViolationTarget(
            Patient patient,
            Appointment appointment,
            BookingIdentity identity
    ) {
    }

    private record ActionCapabilities(
            boolean canLift,
            boolean canOverrideOnce,
            boolean canStaffPardon,
            boolean canCreateManualViolation,
            List<String> supportedActions
    ) {
    }
}
