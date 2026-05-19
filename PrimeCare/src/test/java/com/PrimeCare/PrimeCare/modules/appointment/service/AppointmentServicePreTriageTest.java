package com.PrimeCare.PrimeCare.modules.appointment.service;

import com.PrimeCare.PrimeCare.modules.appointment.dto.request.CreateAppointmentRequest;
import com.PrimeCare.PrimeCare.modules.appointment.dto.request.PreTriageInputRequest;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.AppointmentAvailabilityResponse;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.BookableSlotResponse;
import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.bookingemailotp.entity.BookingEmailOtp;
import com.PrimeCare.PrimeCare.modules.bookingemailotp.service.BookingEmailOtpService;
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
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.service.DoctorOperationalGuardService;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.entity.Specialty;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.repository.SpecialtyRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.service.BranchSpecialtyService;
import com.PrimeCare.PrimeCare.modules.patient.entity.Patient;
import com.PrimeCare.PrimeCare.modules.patient.service.PatientService;
import com.PrimeCare.PrimeCare.modules.triage.PreTriageResult;
import com.PrimeCare.PrimeCare.modules.triage.PreTriageService;
import com.PrimeCare.PrimeCare.modules.triage.TriageAuditService;
import com.PrimeCare.PrimeCare.modules.triage.TriageJsonSupport;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import com.PrimeCare.PrimeCare.shared.enums.Gender;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServicePreTriageTest {

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private AppointmentAvailabilityService availabilityService;
    @Mock
    private AppointmentSlotAvailabilityGuard slotAvailabilityGuard;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private SpecialtyRepository specialtyRepository;
    @Mock
    private DoctorProfileRepository doctorProfileRepository;
    @Mock
    private BranchSpecialtyService branchSpecialtyService;
    @Mock
    private DoctorWorkScheduleRepository doctorWorkScheduleRepository;
    @Mock
    private BranchSessionRepository branchSessionRepository;
    @Mock
    private AppointmentCodeGenerator appointmentCodeGenerator;
    @Mock
    private PatientService patientService;
    @Mock
    private DoctorOperationalGuardService doctorOperationalGuardService;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private PreTriageService preTriageService;
    @Mock
    private TriageAuditService triageAuditService;
    @Mock
    private BookingIdentityService bookingIdentityService;
    @Mock
    private BookingRestrictionPolicyService bookingRestrictionPolicyService;
    @Mock
    private BookingEmailOtpService bookingEmailOtpService;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AppointmentService service;

    private final LocalDate visitDate = LocalDate.now().plusDays(1);
    private final LocalTime slotStart = LocalTime.of(8, 0);
    private BookingEmailOtpService.VerifiedBookingEmailToken verifiedBookingEmailToken;

    @BeforeEach
    void setUp() {
        Branch branch = Branch.builder().id(1L).nameVn("PrimeCare").nameEn("PrimeCare").build();
        Specialty specialty = Specialty.builder().id(2L).nameVn("Nội tổng quát").nameEn("Internal medicine").build();
        DoctorProfile doctor = DoctorProfile.builder().id(3L).fullName("Dr Test").branch(branch).build();
        BranchSession branchSession = BranchSession.builder()
                .branch(branch)
                .session(BranchSessionType.AM)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(12, 0))
                .status("ACTIVE")
                .build();

        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));
        when(specialtyRepository.findById(2L)).thenReturn(Optional.of(specialty));
        when(doctorProfileRepository.findByIdAndBranch_Id(3L, 1L)).thenReturn(Optional.of(doctor));
        when(doctorProfileRepository.existsDoctorSpecialty(3L, 2L)).thenReturn(true);
        when(doctorWorkScheduleRepository.findWithLockByDoctor_IdAndWorkDateAndSession(3L, visitDate, BranchSessionType.AM))
                .thenReturn(Optional.of(DoctorWorkSchedule.builder().doctor(doctor).workDate(visitDate).session(BranchSessionType.AM).build()));
        when(branchSessionRepository.findByBranch_IdAndSessionAndStatus(1L, BranchSessionType.AM, "ACTIVE"))
                .thenReturn(Optional.of(branchSession));
        when(availabilityService.getAvailability(1L, 2L, 3L, visitDate, BranchSessionType.AM, false))
                .thenReturn(AppointmentAvailabilityResponse.builder()
                        .slotMinutes(30)
                        .slots(List.of(BookableSlotResponse.builder()
                                .startTime(slotStart)
                                .endTime(slotStart.plusMinutes(30))
                                .available(true)
                                .capacity(1)
                                .remainingSlots(1)
                                .build()))
                        .build());
        when(availabilityService.resolveSlotCapacity(branchSession)).thenReturn(1);
        when(appointmentRepository.findWithLockByDoctor_IdAndVisitDateAndSessionAndStatusIn(
                eq(3L),
                eq(visitDate),
                eq(BranchSessionType.AM),
                anyCollection()
        )).thenReturn(List.of());
        when(availabilityService.countOverlappingAppointments(any(), eq(slotStart), eq(slotStart.plusMinutes(30)))).thenReturn(0L);
        lenient().when(patientService.findOrCreateFromAppointmentData(anyString(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(Patient.builder().id(9L).build());
        lenient().when(bookingIdentityService.resolvePublicIdentity(anyString(), anyString(), any(), anyString()))
                .thenReturn(new BookingIdentity("abc123", "84900000000", "patient@example.com"));
        verifiedBookingEmailToken = new BookingEmailOtpService.VerifiedBookingEmailToken(
                BookingEmailOtp.builder()
                        .verificationId("booking-email-verification")
                        .normalizedEmail("patient@example.com")
                        .build()
        );
        lenient().when(bookingEmailOtpService.normalizeEmail(anyString())).thenReturn("patient@example.com");
        lenient().when(bookingEmailOtpService.validateTokenForBooking(anyString(), eq("patient@example.com")))
                .thenReturn(verifiedBookingEmailToken);
        lenient().when(appointmentRepository.existsByDoctor_IdAndVisitDateAndSessionAndEtaStartAndPatientPhoneAndStatusIn(
                eq(3L),
                eq(visitDate),
                eq(BranchSessionType.AM),
                eq(slotStart),
                eq("0900000000"),
                anyCollection()
        )).thenReturn(false);
        lenient().when(appointmentCodeGenerator.generate(visitDate, 3L, slotStart)).thenReturn("APT-TEST");
        lenient().when(preTriageService.assess(any(), any(), any(), any(), any())).thenReturn(PreTriageResult.builder()
                .level("WATCH")
                .priority("PRIORITY")
                .flags(List.of("HIGH_FEVER", "CHRONIC_DIABETES"))
                .reasons(List.of("Có bệnh nền đái tháo đường"))
                .summary("Gợi ý P2 vì có yếu tố nguy cơ hoặc ảnh hưởng sinh hoạt đáng kể.")
                .confidence(0.8)
                .source("RULE")
                .build());
        lenient().when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> {
            Appointment appointment = invocation.getArgument(0);
            appointment.setId(99L);
            return appointment;
        });
    }

    @Test
    void createAppointmentStoresPreTriageFields() {
        service.create(request());

        Appointment saved = savedAppointment();
        assertThat(saved.getPreTriageLevel()).isEqualTo("WATCH");
        assertThat(saved.getPreTriagePriority()).isEqualTo("PRIORITY");
        assertThat(TriageJsonSupport.readStringList(saved.getPreTriageFlagsJson()))
                .containsExactly("HIGH_FEVER", "CHRONIC_DIABETES");
        assertThat(saved.getPreTriageAssessedAt()).isNotNull();
    }

    @Test
    void createAppointmentDoesNotSetOfficialTriagePriority() {
        service.create(request());

        assertThat(savedAppointment().getTriagePriority()).isNull();
    }

    @Test
    void guestCannotCreateAppointmentWithoutBookingEmailVerificationToken() {
        CreateAppointmentRequest request = request();
        request.setBookingEmailVerificationToken(null);
        doThrow(new ApiException(ErrorCode.BOOKING_EMAIL_VERIFICATION_REQUIRED))
                .when(bookingEmailOtpService)
                .validateTokenForBooking(isNull(), eq("patient@example.com"));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BOOKING_EMAIL_VERIFICATION_REQUIRED)
                );

        verify(bookingRestrictionPolicyService, never()).assertPublicBookingAllowed(any(), any(), any(), any(), any());
        verify(patientService, never()).findOrCreateFromAppointmentData(anyString(), anyString(), anyString(), any(), any(), any());
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void guestCanCreateAppointmentAfterValidEmailOtpAndConsumesToken() {
        service.create(request());

        verify(bookingEmailOtpService).validateTokenForBooking("booking-token", "patient@example.com");
        verify(bookingEmailOtpService).consumeForBooking(verifiedBookingEmailToken);
        assertThat(savedAppointment().getPatientEmail()).isEqualTo("patient@example.com");
    }

    @Test
    void loggedInVerifiedUserCanBookWithAccountEmailWithoutOtp() {
        CreateAppointmentRequest request = request();
        request.setBookingEmailVerificationToken(null);
        User user = User.builder()
                .id(10L)
                .email("PATIENT@example.com")
                .emailVerifiedAt(LocalDate.now().atStartOfDay())
                .build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        service.create(request, 10L);

        verify(bookingEmailOtpService, never()).validateTokenForBooking(any(), anyString());
        verify(bookingEmailOtpService, never()).consumeForBooking(any());
        assertThat(savedAppointment().getPatientEmail()).isEqualTo("patient@example.com");
    }

    @Test
    void loggedInUnverifiedUserRequiresEmailVerification() {
        CreateAppointmentRequest request = request();
        request.setBookingEmailVerificationToken(null);
        User user = User.builder()
                .id(10L)
                .email("patient@example.com")
                .build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        doThrow(new ApiException(ErrorCode.BOOKING_EMAIL_VERIFICATION_REQUIRED))
                .when(bookingEmailOtpService)
                .validateTokenForBooking(isNull(), eq("patient@example.com"));

        assertThatThrownBy(() -> service.create(request, 10L))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BOOKING_EMAIL_VERIFICATION_REQUIRED)
                );

        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void loggedInUserUsingDifferentContactEmailRequiresContactEmailVerification() {
        CreateAppointmentRequest request = request();
        request.setBookingEmailVerificationToken(null);
        User user = User.builder()
                .id(10L)
                .email("account@example.com")
                .emailVerifiedAt(LocalDate.now().atStartOfDay())
                .build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        doThrow(new ApiException(ErrorCode.BOOKING_EMAIL_VERIFICATION_REQUIRED))
                .when(bookingEmailOtpService)
                .validateTokenForBooking(isNull(), eq("patient@example.com"));

        assertThatThrownBy(() -> service.create(request, 10L))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BOOKING_EMAIL_VERIFICATION_REQUIRED)
                );

        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void createAppointmentStoresCardSelections() {
        service.create(request());

        Appointment saved = savedAppointment();
        assertThat(saved.getSymptomOnset()).isEqualTo("TODAY");
        assertThat(saved.getFunctionalImpact()).isEqualTo("MILD_DIFFICULTY");
        assertThat(TriageJsonSupport.readStringList(saved.getChronicConditionsJson()))
                .containsExactly("DIABETES");
        assertThat(TriageJsonSupport.readStringList(saved.getChronicConditionOthersJson()))
                .containsExactly("Suy thận mạn");
        assertThat(TriageJsonSupport.readStringList(saved.getRedFlagSelectionsJson()))
                .containsExactly("HIGH_FEVER");
    }

    @Test
    void createAppointmentRejectsSlotHeldByRecoveryFlow() {
        doThrow(new ApiException(ErrorCode.APPOINTMENT_SLOT_NOT_AVAILABLE, "Khung giờ đang được giữ tạm"))
                .when(slotAvailabilityGuard)
                .assertSlotAvailable(
                        eq(3L),
                        eq(visitDate),
                        eq(BranchSessionType.AM),
                        eq(slotStart),
                        eq(slotStart.plusMinutes(30)),
                        eq(null),
                        eq(null)
                );

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.APPOINTMENT_SLOT_NOT_AVAILABLE)
                );

        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void createAppointmentBlockedByRestrictionDoesNotCreateRequestedAppointment() {
        doThrow(new ApiException(ErrorCode.BOOKING_REQUIRES_STAFF_ASSISTANCE))
                .when(bookingRestrictionPolicyService)
                .assertPublicBookingAllowed(
                        any(BookingIdentity.class),
                        eq(visitDate),
                        eq(3L),
                        eq(BranchSessionType.AM),
                        eq(slotStart)
                );

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BOOKING_REQUIRES_STAFF_ASSISTANCE)
                );

        verify(patientService, never()).findOrCreateFromAppointmentData(anyString(), anyString(), anyString(), any(), any(), any());
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    private Appointment savedAppointment() {
        ArgumentCaptor<Appointment> captor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).save(captor.capture());
        return captor.getValue();
    }

    private CreateAppointmentRequest request() {
        CreateAppointmentRequest request = new CreateAppointmentRequest();
        request.setBranchId(1L);
        request.setSpecialtyId(2L);
        request.setDoctorId(3L);
        request.setVisitDate(visitDate);
        request.setSession(BranchSessionType.AM);
        request.setSlotStart(slotStart);
        request.setPatientFullName("Nguyen Van A");
        request.setPatientPhone("0900000000");
        request.setPatientEmail("patient@example.com");
        request.setPatientDob(LocalDate.of(1990, 1, 1));
        request.setPatientGender(Gender.MALE);
        request.setVisitType("new");
        request.setBookingEmailVerificationToken("booking-token");
        request.setReasonForVisit("Sốt cao");

        PreTriageInputRequest preTriage = new PreTriageInputRequest();
        preTriage.setSymptomOnset("TODAY");
        preTriage.setChronicConditions(List.of("DIABETES"));
        preTriage.setChronicConditionOthers(List.of("Suy thận mạn"));
        preTriage.setFunctionalImpact("MILD_DIFFICULTY");
        preTriage.setRedFlags(List.of("HIGH_FEVER"));
        request.setPreTriage(preTriage);
        return request;
    }
}
