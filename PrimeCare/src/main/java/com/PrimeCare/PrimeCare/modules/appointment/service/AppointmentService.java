package com.PrimeCare.PrimeCare.modules.appointment.service;

import com.PrimeCare.PrimeCare.modules.appointment.dto.request.CreateAppointmentRequest;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.AppointmentAvailabilityResponse;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.AppointmentResponse;
import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.event.AppointmentCreatedSpringEvent;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.bookingemailotp.service.BookingEmailOtpService;
import com.PrimeCare.PrimeCare.modules.bookingemailotp.service.BookingEmailOtpService.VerifiedBookingEmailToken;
import com.PrimeCare.PrimeCare.modules.booking_restriction.service.BookingIdentity;
import com.PrimeCare.PrimeCare.modules.booking_restriction.service.BookingIdentityService;
import com.PrimeCare.PrimeCare.modules.booking_restriction.service.BookingRestrictionPolicyService;
import com.PrimeCare.PrimeCare.modules.doctor_schedule.entity.DoctorWorkSchedule;
import com.PrimeCare.PrimeCare.modules.doctor_schedule.repository.DoctorWorkScheduleRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.BranchSession;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.repository.BranchRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.repository.BranchSessionRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.repository.DoctorProfileRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.entity.Specialty;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.service.DoctorOperationalGuardService;
import com.PrimeCare.PrimeCare.modules.patient.entity.Patient;
import com.PrimeCare.PrimeCare.modules.patient.service.PatientService;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.repository.SpecialtyRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.service.BranchSpecialtyService;
import com.PrimeCare.PrimeCare.modules.triage.PreTriageResult;
import com.PrimeCare.PrimeCare.modules.triage.PreTriageService;
import com.PrimeCare.PrimeCare.modules.triage.TriageAuditService;
import com.PrimeCare.PrimeCare.modules.triage.TriageJsonSupport;
import com.PrimeCare.PrimeCare.modules.triage.TriagePriorityNormalizer;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentSourceType;
import com.PrimeCare.PrimeCare.shared.enums.ArrivalStatus;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import com.PrimeCare.PrimeCare.shared.utils.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private static final EnumSet<AppointmentStatus> BLOCKING_STATUSES = EnumSet.of(
            AppointmentStatus.REQUESTED,
            AppointmentStatus.CONFIRMED,
            AppointmentStatus.CHECKED_IN
    );

    private final AppointmentRepository appointmentRepository;
    private final AppointmentAvailabilityService availabilityService;
    private final AppointmentSlotAvailabilityGuard slotAvailabilityGuard;
    private final BranchRepository branchRepository;
    private final SpecialtyRepository specialtyRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final BranchSpecialtyService branchSpecialtyService;
    private final DoctorWorkScheduleRepository doctorWorkScheduleRepository;
    private final BranchSessionRepository branchSessionRepository;
    private final AppointmentCodeGenerator appointmentCodeGenerator;
    private final PatientService patientService;
    private final DoctorOperationalGuardService doctorOperationalGuardService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final PreTriageService preTriageService;
    private final TriageAuditService triageAuditService;
    private final BookingIdentityService bookingIdentityService;
    private final BookingRestrictionPolicyService bookingRestrictionPolicyService;
    private final BookingEmailOtpService bookingEmailOtpService;
    private final UserRepository userRepository;

    @Transactional
    public AppointmentResponse create(CreateAppointmentRequest request) {
        return createInternal(request, resolveAuthenticatedUserIdFromContext());
    }

    @Transactional
    public AppointmentResponse create(CreateAppointmentRequest request, Long currentUserId) {
        return createInternal(request, currentUserId);
    }

    private AppointmentResponse createInternal(CreateAppointmentRequest request, Long currentUserId) {
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

        branchSpecialtyService.validateBranchSpecialtyActive(request.getBranchId(), request.getSpecialtyId());

        doctorOperationalGuardService.assertDoctorPublicBookable(doctor);

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

        AppointmentAvailabilityResponse availability = availabilityService.getAvailability(
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

        if (request.getPatientDob() == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Ngày sinh không được để trống");
        }
        if (request.getPatientGender() == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Giới tính không được để trống");
        }
        if (StringUtil.trimToNull(request.getVisitType()) == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Loại lượt khám không được để trống");
        }
        if (StringUtil.trimToNull(request.getPatientEmail()) == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Vui lòng nhập email để nhận mã OTP tra cứu/hủy lịch.");
        }
        String patientEmail = bookingEmailOtpService.normalizeEmail(request.getPatientEmail());
        User currentUser = resolveCurrentUser(currentUserId);
        VerifiedBookingEmailToken bookingEmailVerification = resolveBookingEmailVerification(
                request,
                currentUser,
                patientEmail
        );

        BookingIdentity bookingIdentity = bookingIdentityService.resolvePublicIdentity(
                request.getPatientPhone(),
                request.getPatientFullName(),
                request.getPatientDob(),
                patientEmail
        );
        bookingRestrictionPolicyService.assertPublicBookingAllowed(
                bookingIdentity,
                request.getVisitDate(),
                doctor.getId(),
                request.getSession(),
                request.getSlotStart()
        );

        Patient patient = patientService.findOrCreateFromAppointmentData(
                request.getPatientFullName(),
                request.getPatientPhone(),
                patientEmail,
                request.getPatientDob(),
                request.getPatientGender(),
                request.getPatientNote()
        );

        boolean duplicateBooking = appointmentRepository.existsByDoctor_IdAndVisitDateAndSessionAndEtaStartAndPatientPhoneAndStatusIn(
                doctor.getId(),
                request.getVisitDate(),
                request.getSession(),
                request.getSlotStart(),
                request.getPatientPhone().trim(),
                BLOCKING_STATUSES
        );
        if (duplicateBooking || hasSamePatientSameSlotBooking(
                blockingAppointments,
                patient,
                request.getPatientFullName(),
                request.getPatientDob(),
                request.getPatientGender(),
                request.getSlotStart()
        )) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Ban da co lich hen trung khung gio voi bac si nay");
        }

        String code = appointmentCodeGenerator.generate(request.getVisitDate(), doctor.getId(), request.getSlotStart());

        Appointment entity = Appointment.builder()
                                        .code(code)
                                        .status(AppointmentStatus.REQUESTED)
                                        .branch(branch)
                                        .specialty(specialty)
                                        .doctor(doctor)
                                        .visitDate(request.getVisitDate())
                                        .session(request.getSession())
                                        .queueNo(null)
                                        .slotMinutes(availability.getSlotMinutes())
                                        .etaStart(selectedSlot.getStartTime())
                                        .etaEnd(selectedSlot.getEndTime())
                                        .sourceType(AppointmentSourceType.PUBLIC_BOOKING)
                                        .arrivalStatus(ArrivalStatus.NOT_ARRIVED)
                                        .patientFullName(request.getPatientFullName().trim())
                                        .patientPhone(request.getPatientPhone().trim())
                                        .patientEmail(patientEmail)
                                        .patient(patient)
                                        .patientDob(request.getPatientDob())
                                        .patientGender(request.getPatientGender())
                                        .patientNote(StringUtil.trimToNull(request.getPatientNote()))
                                        .reasonForVisit(StringUtil.trimToNull(request.getReasonForVisit()))
                                        .visitType(StringUtil.trimToNull(request.getVisitType()) != null ? request.getVisitType().trim().toUpperCase().replace(' ', '_') : null)
                                        .build();

        PreTriageResult preTriageResult = applyPreTriage(entity, request);

        Appointment saved = appointmentRepository.save(entity);
        triageAuditService.recordPreTriageSuggested(saved, preTriageResult, request.getPreTriage());
        consumeBookingEmailVerification(bookingEmailVerification, currentUser, patientEmail);
        availabilityService.evictAvailabilityCacheForDoctorDateSession(
                saved.getDoctor().getId(),
                saved.getVisitDate(),
                saved.getSession()
        );

        applicationEventPublisher.publishEvent(new AppointmentCreatedSpringEvent(
                saved.getId(),
                saved.getBranch() != null ? saved.getBranch().getId() : null,
                saved.getVisitDate(),
                saved.getCode()
        ));

        return toResponse(saved);
    }

    private VerifiedBookingEmailToken resolveBookingEmailVerification(
            CreateAppointmentRequest request,
            User currentUser,
            String patientEmail
    ) {
        if (!requiresBookingEmailVerification(currentUser, patientEmail)) {
            return null;
        }
        return bookingEmailOtpService.validateTokenForBooking(
                request.getBookingEmailVerificationToken(),
                patientEmail
        );
    }

    private boolean requiresBookingEmailVerification(User currentUser, String patientEmail) {
        if (currentUser == null) {
            return true;
        }
        String accountEmail = normalizeEmailOrNull(currentUser.getEmail());
        return currentUser.getEmailVerifiedAt() == null
                || accountEmail == null
                || !accountEmail.equals(patientEmail);
    }

    private void consumeBookingEmailVerification(
            VerifiedBookingEmailToken bookingEmailVerification,
            User currentUser,
            String patientEmail
    ) {
        if (bookingEmailVerification == null) {
            return;
        }
        bookingEmailOtpService.consumeForBooking(bookingEmailVerification);
        markAccountEmailVerifiedIfMatches(currentUser, patientEmail);
    }

    private void markAccountEmailVerifiedIfMatches(User currentUser, String patientEmail) {
        if (currentUser == null || currentUser.getEmailVerifiedAt() != null) {
            return;
        }
        String accountEmail = normalizeEmailOrNull(currentUser.getEmail());
        if (accountEmail != null && accountEmail.equals(patientEmail)) {
            currentUser.setEmailVerifiedAt(LocalDateTime.now());
            userRepository.save(currentUser);
        }
    }

    private User resolveCurrentUser(Long currentUserId) {
        if (currentUserId == null) {
            return null;
        }
        return userRepository.findById(currentUserId).orElse(null);
    }

    private Long resolveAuthenticatedUserIdFromContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String normalizeEmailOrNull(String email) {
        String trimmed = StringUtil.trimToNull(email);
        return trimmed != null ? trimmed.toLowerCase(Locale.ROOT) : null;
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

    private PreTriageResult applyPreTriage(Appointment entity, CreateAppointmentRequest request) {
        var input = request.getPreTriage();
        PreTriageResult result = preTriageService.assess(
                request.getReasonForVisit(),
                request.getPatientNote(),
                input,
                request.getPatientDob(),
                request.getPatientGender()
        );

        entity.setSymptomOnset(TriagePriorityNormalizer.normalizeSymptomOnset(input != null ? input.getSymptomOnset() : null));
        entity.setChronicConditionsJson(TriageJsonSupport.writeStringList(
                input != null ? TriagePriorityNormalizer.normalizeChronicConditions(input.getChronicConditions()) : List.of()
        ));
        entity.setChronicConditionOthersJson(TriageJsonSupport.writeStringList(
                input != null ? TriagePriorityNormalizer.normalizeChronicConditionOthers(input.getChronicConditionOthers()) : List.of()
        ));
        entity.setFunctionalImpact(TriagePriorityNormalizer.normalizeFunctionalImpact(input != null ? input.getFunctionalImpact() : null));
        entity.setRedFlagSelectionsJson(TriageJsonSupport.writeStringList(
                input != null ? TriagePriorityNormalizer.normalizeRedFlags(input.getRedFlags()) : List.of()
        ));
        entity.setPreTriageLevel(result.getLevel());
        entity.setPreTriagePriority(result.getPriority());
        entity.setPreTriageFlagsJson(TriageJsonSupport.writeStringList(result.getFlags()));
        entity.setPreTriageReasonsJson(TriageJsonSupport.writeStringList(result.getReasons()));
        entity.setPreTriageSummary(result.getSummary());
        entity.setPreTriageAssessedAt(LocalDateTime.now());
        entity.setPreTriageMatchedTermsJson(TriageJsonSupport.writeObject(result.getMatchedTerms()));
        entity.setPreTriageMatchedRulesJson(TriageJsonSupport.writeObject(result.getMatchedRules()));
        entity.setPreTriageSource(result.getSource());
        entity.setPreTriageConfidenceLevel(result.getConfidenceLevel());
        entity.setPreTriageKnowledgeBaseVersion(result.getKnowledgeBaseVersion());
        entity.setPreTriageRulesetVersion(result.getRulesetVersion());
        entity.setPreTriageAiModelVersion(result.getAiModelVersion());
        return result;
    }

    private AppointmentResponse toResponse(Appointment entity) {
        return AppointmentResponse.builder()
                                  .id(entity.getId())
                                  .code(entity.getCode())
                                  .status(entity.getStatus())
                                  .branchId(entity.getBranch().getId())
                                  .branchNameVn(entity.getBranch().getNameVn())
                                  .branchNameEn(entity.getBranch().getNameEn())
                                  .specialtyId(entity.getSpecialty().getId())
                                  .specialtyNameVn(entity.getSpecialty().getNameVn())
                                  .specialtyNameEn(entity.getSpecialty().getNameEn())
                                  .doctorId(entity.getDoctor() != null ? entity.getDoctor().getId() : null)
                                  .doctorName(entity.getDoctor() != null ? entity.getDoctor().getFullName() : null)
                                  .visitDate(entity.getVisitDate())
                                  .session(entity.getSession())
                                  .queueNo(entity.getQueueNo())
                                  .slotMinutes(entity.getSlotMinutes())
                                  .etaStart(entity.getEtaStart())
                                  .etaEnd(entity.getEtaEnd())
                                  .sourceType(entity.getSourceType())
                                  .arrivalStatus(entity.getArrivalStatus())
                                  .receptionQueueNo(entity.getReceptionQueueNo())
                                  .arrivedAt(entity.getArrivedAt())
                                  .patientFullName(entity.getPatientFullName())
                                  .patientPhone(entity.getPatientPhone())
                                  .patientEmail(entity.getPatientEmail())
                                  .patientDob(entity.getPatientDob())
                                  .patientId(entity.getPatient() != null ? entity.getPatient().getId() : null)
                                  .patientGender(entity.getPatientGender())
                                  .patientNote(entity.getPatientNote())
                                  .reasonForVisit(entity.getReasonForVisit())
                                  .visitType(entity.getVisitType())
                                  .build();
    }
}
