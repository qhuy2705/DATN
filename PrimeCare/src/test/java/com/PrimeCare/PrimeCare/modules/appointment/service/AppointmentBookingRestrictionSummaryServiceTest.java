package com.PrimeCare.PrimeCare.modules.appointment.service;

import com.PrimeCare.PrimeCare.modules.appointment.dto.response.AppointmentBookingRestrictionSummaryResponse;
import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.booking_restriction.entity.PatientBookingRestriction;
import com.PrimeCare.PrimeCare.modules.booking_restriction.entity.PatientViolationEvent;
import com.PrimeCare.PrimeCare.modules.booking_restriction.enums.BookingRestrictionLevel;
import com.PrimeCare.PrimeCare.modules.booking_restriction.enums.BookingRestrictionStatus;
import com.PrimeCare.PrimeCare.modules.booking_restriction.enums.ViolationEventStatus;
import com.PrimeCare.PrimeCare.modules.booking_restriction.enums.ViolationEventType;
import com.PrimeCare.PrimeCare.modules.booking_restriction.repository.PatientBookingRestrictionRepository;
import com.PrimeCare.PrimeCare.modules.booking_restriction.repository.PatientViolationEventRepository;
import com.PrimeCare.PrimeCare.modules.booking_restriction.service.BookingIdentity;
import com.PrimeCare.PrimeCare.modules.booking_restriction.service.BookingIdentityService;
import com.PrimeCare.PrimeCare.modules.booking_restriction.service.BookingRestrictionPolicyService;
import com.PrimeCare.PrimeCare.modules.patient.entity.Patient;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentBookingRestrictionSummaryServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private PatientBookingRestrictionRepository restrictionRepository;
    @Mock
    private PatientViolationEventRepository violationEventRepository;
    @Mock
    private AuditLogService auditLogService;

    private BookingIdentityService bookingIdentityService;
    private BookingRestrictionPolicyService bookingRestrictionPolicyService;
    private AppointmentBookingRestrictionSummaryService service;

    @BeforeEach
    void setUp() {
        bookingIdentityService = new BookingIdentityService("test-pepper");
        bookingRestrictionPolicyService = new BookingRestrictionPolicyService(
                violationEventRepository,
                restrictionRepository,
                appointmentRepository,
                bookingIdentityService,
                auditLogService
        );
        service = new AppointmentBookingRestrictionSummaryService(
                appointmentRepository,
                bookingIdentityService,
                bookingRestrictionPolicyService,
                restrictionRepository,
                violationEventRepository
        );
    }

    @Test
    void summaryReturnsClearForPatientWithNoEventsOrRestriction() {
        Appointment appointment = appointment();
        BookingIdentity identity = identity(appointment);
        String periodMonth = bookingRestrictionPolicyService.periodMonth(LocalDate.now());

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));
        when(violationEventRepository.sumActivePointsByIdentityKeyHashAndPeriodMonth(identity.identityKeyHash(), periodMonth))
                .thenReturn(0);
        when(restrictionRepository.findByIdentityKeyHashAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(
                eq(identity.identityKeyHash()),
                eq(BookingRestrictionStatus.ACTIVE),
                any(LocalDateTime.class)
        )).thenReturn(List.of());
        when(violationEventRepository.findFirstByIdentityKeyHashAndPeriodMonthAndStatusOrderByCreatedAtDesc(
                identity.identityKeyHash(),
                periodMonth,
                ViolationEventStatus.ACTIVE
        )).thenReturn(Optional.empty());

        AppointmentBookingRestrictionSummaryResponse response = service.getSummary(1L);

        assertThat(response.getAppointmentId()).isEqualTo(1L);
        assertThat(response.getPatientId()).isEqualTo(11L);
        assertThat(response.getMonthlyScore()).isZero();
        assertThat(response.getLevel()).isEqualTo(BookingRestrictionPolicyService.CLEAR);
        assertThat(response.getStatus()).isNull();
        assertThat(response.getRestricted()).isFalse();
        assertThat(response.getMessage()).isEqualTo("Không có hạn chế đặt lịch trong tháng này.");
        verifyNoMutation();
    }

    @ParameterizedTest
    @CsvSource({
            "3,WARNING,false",
            "5,VERIFY_REQUIRED,true",
            "7,STAFF_ONLY,true"
    })
    void summaryLevelFollowsMonthlyScore(int score, String expectedLevel, boolean expectedRestricted) {
        Appointment appointment = appointment();
        BookingIdentity identity = identity(appointment);
        String periodMonth = bookingRestrictionPolicyService.periodMonth(LocalDate.now());
        PatientViolationEvent latest = PatientViolationEvent.builder()
                .identityKeyHash(identity.identityKeyHash())
                .periodMonth(periodMonth)
                .type(ViolationEventType.MANUAL)
                .status(ViolationEventStatus.ACTIVE)
                .points(score)
                .build();
        latest.setCreatedAt(LocalDateTime.now().minusHours(1));

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));
        when(violationEventRepository.sumActivePointsByIdentityKeyHashAndPeriodMonth(identity.identityKeyHash(), periodMonth))
                .thenReturn(score);
        when(restrictionRepository.findByIdentityKeyHashAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(
                eq(identity.identityKeyHash()),
                eq(BookingRestrictionStatus.ACTIVE),
                any(LocalDateTime.class)
        )).thenReturn(List.of());
        when(violationEventRepository.findFirstByIdentityKeyHashAndPeriodMonthAndStatusOrderByCreatedAtDesc(
                identity.identityKeyHash(),
                periodMonth,
                ViolationEventStatus.ACTIVE
        )).thenReturn(Optional.of(latest));

        AppointmentBookingRestrictionSummaryResponse response = service.getSummary(1L);

        assertThat(response.getMonthlyScore()).isEqualTo(score);
        assertThat(response.getLevel()).isEqualTo(expectedLevel);
        assertThat(response.getRestricted()).isEqualTo(expectedRestricted);
        assertThat(response.getLatestViolationType()).isEqualTo(ViolationEventType.MANUAL.name());
        assertThat(response.getLatestViolationAt()).isEqualTo(latest.getCreatedAt());
        verifyNoMutation();
    }

    @Test
    void summaryIncludesExistingActiveRestrictionWithoutCreatingOne() {
        Appointment appointment = appointment();
        BookingIdentity identity = identity(appointment);
        String periodMonth = bookingRestrictionPolicyService.periodMonth(LocalDate.now());
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(5);
        PatientBookingRestriction restriction = PatientBookingRestriction.builder()
                .id(9L)
                .identityKeyHash(identity.identityKeyHash())
                .periodMonth(periodMonth)
                .level(BookingRestrictionLevel.VERIFY_REQUIRED)
                .status(BookingRestrictionStatus.ACTIVE)
                .scoreSnapshot(5)
                .startsAt(LocalDateTime.now().minusDays(1))
                .expiresAt(expiresAt)
                .build();

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));
        when(violationEventRepository.sumActivePointsByIdentityKeyHashAndPeriodMonth(identity.identityKeyHash(), periodMonth))
                .thenReturn(0);
        when(restrictionRepository.findByIdentityKeyHashAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(
                eq(identity.identityKeyHash()),
                eq(BookingRestrictionStatus.ACTIVE),
                any(LocalDateTime.class)
        )).thenReturn(List.of(restriction));
        when(violationEventRepository.findFirstByIdentityKeyHashAndPeriodMonthAndStatusOrderByCreatedAtDesc(
                identity.identityKeyHash(),
                periodMonth,
                ViolationEventStatus.ACTIVE
        )).thenReturn(Optional.empty());

        AppointmentBookingRestrictionSummaryResponse response = service.getSummary(1L);

        assertThat(response.getLevel()).isEqualTo(BookingRestrictionLevel.VERIFY_REQUIRED.name());
        assertThat(response.getStatus()).isEqualTo(BookingRestrictionStatus.ACTIVE.name());
        assertThat(response.getRestricted()).isTrue();
        assertThat(response.getRestrictionExpiresAt()).isEqualTo(expiresAt);
        verifyNoMutation();
    }

    private Appointment appointment() {
        Patient patient = Patient.builder()
                .id(11L)
                .fullName("Nguyen Van A")
                .phone("0900000000")
                .email("patient@example.test")
                .build();
        return Appointment.builder()
                .id(1L)
                .code("APT-1")
                .status(AppointmentStatus.REQUESTED)
                .visitDate(LocalDate.now().plusDays(1))
                .session(BranchSessionType.AM)
                .etaStart(LocalTime.of(8, 0))
                .etaEnd(LocalTime.of(8, 30))
                .patient(patient)
                .patientFullName("Nguyen Van A")
                .patientPhone("0900000000")
                .patientEmail("patient@example.test")
                .patientDob(LocalDate.of(1990, 1, 1))
                .build();
    }

    private BookingIdentity identity(Appointment appointment) {
        return bookingIdentityService.resolveAppointmentIdentity(appointment);
    }

    private void verifyNoMutation() {
        verify(restrictionRepository, never()).save(any(PatientBookingRestriction.class));
        verify(violationEventRepository, never()).save(any(PatientViolationEvent.class));
    }
}
