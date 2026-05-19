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
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingRestrictionPolicyServiceTest {

    @Mock
    private PatientViolationEventRepository violationEventRepository;
    @Mock
    private PatientBookingRestrictionRepository restrictionRepository;
    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private AuditLogService auditLogService;

    private BookingIdentityService bookingIdentityService;
    private BookingRestrictionPolicyService service;

    private BookingIdentity identity;
    private LocalDate visitDate;

    @BeforeEach
    void setUp() {
        bookingIdentityService = new BookingIdentityService("test-pepper");
        service = new BookingRestrictionPolicyService(
                violationEventRepository,
                restrictionRepository,
                appointmentRepository,
                bookingIdentityService,
                auditLogService
        );
        identity = bookingIdentityService.resolvePublicIdentity(
                "0900000000",
                "Nguyen Van A",
                LocalDate.of(1990, 1, 1),
                "patient@example.test"
        );
        visitDate = LocalDate.now().plusDays(1);
    }

    @Test
    void scoreZeroIsClearAndPublicBookingAllowed() {
        allowNoActiveBookings();
        when(restrictionRepository.findByIdentityKeyHashAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(
                eq(identity.identityKeyHash()),
                eq(BookingRestrictionStatus.ACTIVE),
                any(LocalDateTime.class)
        )).thenReturn(List.of());
        when(violationEventRepository.sumActivePointsByIdentityKeyHashAndPeriodMonth(eq(identity.identityKeyHash()), anyString()))
                .thenReturn(0);

        assertThat(service.scoreLevel(0)).isEqualTo(BookingRestrictionPolicyService.CLEAR);
        assertThatCode(() -> service.assertPublicBookingAllowed(
                identity,
                visitDate,
                3L,
                BranchSessionType.AM,
                LocalTime.of(8, 0)
        )).doesNotThrowAnyException();
    }

    @Test
    void scoreThreeIsWarningAndPublicBookingAllowed() {
        allowNoActiveBookings();
        when(restrictionRepository.findByIdentityKeyHashAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(
                eq(identity.identityKeyHash()),
                eq(BookingRestrictionStatus.ACTIVE),
                any(LocalDateTime.class)
        )).thenReturn(List.of());
        when(violationEventRepository.sumActivePointsByIdentityKeyHashAndPeriodMonth(eq(identity.identityKeyHash()), anyString()))
                .thenReturn(3);

        assertThat(service.scoreLevel(3)).isEqualTo(BookingRestrictionPolicyService.WARNING);
        assertThatCode(() -> service.assertPublicBookingAllowed(
                identity,
                visitDate,
                3L,
                BranchSessionType.AM,
                LocalTime.of(8, 0)
        )).doesNotThrowAnyException();
        verify(restrictionRepository, never()).save(any(PatientBookingRestriction.class));
    }

    @Test
    void scoreFiveCreatesVerifyRequiredRestrictionAndBlocksPublicBooking() {
        when(restrictionRepository.findByIdentityKeyHashAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(
                eq(identity.identityKeyHash()),
                eq(BookingRestrictionStatus.ACTIVE),
                any(LocalDateTime.class)
        )).thenReturn(List.of());
        when(violationEventRepository.sumActivePointsByIdentityKeyHashAndPeriodMonth(eq(identity.identityKeyHash()), anyString()))
                .thenReturn(5);
        when(restrictionRepository.save(any(PatientBookingRestriction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.assertPublicBookingAllowed(
                identity,
                visitDate,
                3L,
                BranchSessionType.AM,
                LocalTime.of(8, 0)
        )).isInstanceOfSatisfying(ApiException.class, ex ->
                assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BOOKING_REQUIRES_STAFF_ASSISTANCE)
        );

        PatientBookingRestriction saved = savedRestriction();
        assertThat(saved.getLevel()).isEqualTo(BookingRestrictionLevel.VERIFY_REQUIRED);
        assertThat(saved.getScoreSnapshot()).isEqualTo(5);
    }

    @Test
    void scoreSevenCreatesStaffOnlyRestrictionAndBlocksPublicBooking() {
        when(restrictionRepository.findByIdentityKeyHashAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(
                eq(identity.identityKeyHash()),
                eq(BookingRestrictionStatus.ACTIVE),
                any(LocalDateTime.class)
        )).thenReturn(List.of());
        when(violationEventRepository.sumActivePointsByIdentityKeyHashAndPeriodMonth(eq(identity.identityKeyHash()), anyString()))
                .thenReturn(7);
        when(restrictionRepository.save(any(PatientBookingRestriction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.assertPublicBookingAllowed(
                identity,
                visitDate,
                3L,
                BranchSessionType.AM,
                LocalTime.of(8, 0)
        )).isInstanceOfSatisfying(ApiException.class, ex ->
                assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BOOKING_REQUIRES_STAFF_ASSISTANCE)
        );

        assertThat(savedRestriction().getLevel()).isEqualTo(BookingRestrictionLevel.STAFF_ONLY);
    }

    @Test
    void activeVerifyRequiredFromPreviousMonthBlocksPublicBooking() {
        PatientBookingRestriction restriction = PatientBookingRestriction.builder()
                .identityKeyHash(identity.identityKeyHash())
                .periodMonth(LocalDate.now().minusMonths(1).toString())
                .level(BookingRestrictionLevel.VERIFY_REQUIRED)
                .status(BookingRestrictionStatus.ACTIVE)
                .scoreSnapshot(5)
                .startsAt(LocalDateTime.now().minusDays(10))
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();
        when(restrictionRepository.findByIdentityKeyHashAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(
                eq(identity.identityKeyHash()),
                eq(BookingRestrictionStatus.ACTIVE),
                any(LocalDateTime.class)
        )).thenReturn(List.of(restriction));

        assertThatThrownBy(() -> service.assertPublicBookingAllowed(
                identity,
                visitDate,
                3L,
                BranchSessionType.AM,
                LocalTime.of(8, 0)
        )).isInstanceOfSatisfying(ApiException.class, ex ->
                assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BOOKING_REQUIRES_STAFF_ASSISTANCE)
        );

        verify(violationEventRepository, never()).sumActivePointsByIdentityKeyHashAndPeriodMonth(anyString(), anyString());
    }

    @Test
    void expiredRestrictionDoesNotBlockWhenCurrentScoreIsLow() {
        allowNoActiveBookings();
        when(restrictionRepository.findByIdentityKeyHashAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(
                eq(identity.identityKeyHash()),
                eq(BookingRestrictionStatus.ACTIVE),
                any(LocalDateTime.class)
        )).thenReturn(List.of());
        when(violationEventRepository.sumActivePointsByIdentityKeyHashAndPeriodMonth(eq(identity.identityKeyHash()), anyString()))
                .thenReturn(0);

        assertThatCode(() -> service.assertPublicBookingAllowed(
                identity,
                visitDate,
                3L,
                BranchSessionType.AM,
                LocalTime.of(8, 0)
        )).doesNotThrowAnyException();
    }

    @Test
    void monthlyScoreNeverDisplaysBelowZero() {
        when(violationEventRepository.sumActivePointsByIdentityKeyHashAndPeriodMonth("hash", "2026-05"))
                .thenReturn(-3);

        assertThat(service.monthlyScore("hash", "2026-05")).isZero();
    }

    @Test
    void recalculateAfterScoreDropBelowThresholdLiftsActiveRestriction() {
        User staff = User.builder().id(10L).build();
        PatientBookingRestriction restriction = PatientBookingRestriction.builder()
                .id(5L)
                .identityKeyHash(identity.identityKeyHash())
                .periodMonth("2026-05")
                .level(BookingRestrictionLevel.VERIFY_REQUIRED)
                .status(BookingRestrictionStatus.ACTIVE)
                .scoreSnapshot(5)
                .startsAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(3))
                .build();
        when(violationEventRepository.sumActivePointsByIdentityKeyHashAndPeriodMonth(eq(identity.identityKeyHash()), anyString()))
                .thenReturn(4);
        when(restrictionRepository.findByIdentityKeyHashAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(
                eq(identity.identityKeyHash()),
                eq(BookingRestrictionStatus.ACTIVE),
                any(LocalDateTime.class)
        )).thenReturn(List.of(restriction));
        when(restrictionRepository.save(restriction)).thenReturn(restriction);

        PatientBookingRestriction result = service.recalculateAfterScoreChange(
                identity.identityKeyHash(),
                null,
                staff,
                "Void sự kiện"
        );

        assertThat(result).isNull();
        assertThat(restriction.getStatus()).isEqualTo(BookingRestrictionStatus.LIFTED);
        assertThat(restriction.getLiftedBy()).isSameAs(staff);
        assertThat(restriction.getLiftedAt()).isNotNull();
        assertThat(restriction.getLiftReason()).isEqualTo("Void sự kiện");
    }

    @Test
    void liftRestrictionRequiresReasonAndStoresAuditFields() {
        User staff = User.builder().id(10L).build();
        PatientBookingRestriction restriction = PatientBookingRestriction.builder()
                .id(9L)
                .identityKeyHash(identity.identityKeyHash())
                .periodMonth("2026-05")
                .level(BookingRestrictionLevel.STAFF_ONLY)
                .status(BookingRestrictionStatus.ACTIVE)
                .scoreSnapshot(7)
                .startsAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(5))
                .build();

        assertThatThrownBy(() -> service.liftRestriction(restriction, staff, " "))
                .isInstanceOf(ApiException.class);

        when(restrictionRepository.save(restriction)).thenReturn(restriction);

        PatientBookingRestriction saved = service.liftRestriction(restriction, staff, "Staff reviewed");

        assertThat(saved.getStatus()).isEqualTo(BookingRestrictionStatus.LIFTED);
        assertThat(saved.getLiftedBy()).isSameAs(staff);
        assertThat(saved.getLiftedAt()).isNotNull();
        assertThat(saved.getLiftReason()).isEqualTo("Staff reviewed");
    }

    @Test
    void activeBookingLimitBlocksMoreThanTwoActiveAppointmentsPerCanonicalPhoneInSevenDays() {
        List<String> phoneCandidates = bookingIdentityService.phoneLookupCandidates(identity.canonicalPhone());
        when(restrictionRepository.findByIdentityKeyHashAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(
                eq(identity.identityKeyHash()),
                eq(BookingRestrictionStatus.ACTIVE),
                any(LocalDateTime.class)
        )).thenReturn(List.of());
        when(violationEventRepository.sumActivePointsByIdentityKeyHashAndPeriodMonth(eq(identity.identityKeyHash()), anyString()))
                .thenReturn(0);
        when(appointmentRepository.findActiveByDoctorSlotAndPatientPhoneIn(
                eq(3L),
                eq(visitDate),
                eq(BranchSessionType.AM),
                eq(LocalTime.of(8, 0)),
                anyCollection(),
                eq(phoneCandidates)
        )).thenReturn(List.of());
        when(appointmentRepository.findActiveByPatientPhoneInAndVisitDateBetween(
                eq(phoneCandidates),
                eq(visitDate),
                eq(visitDate),
                anyCollection()
        )).thenReturn(List.of());
        when(appointmentRepository.findActiveByPatientPhoneInAndVisitDateBetween(
                eq(phoneCandidates),
                eq(visitDate.minusDays(6)),
                eq(visitDate.plusDays(6)),
                anyCollection()
        )).thenReturn(List.of(
                activeAppointment(visitDate.plusDays(1), LocalDate.of(1980, 1, 1)),
                activeAppointment(visitDate.plusDays(2), LocalDate.of(1981, 1, 1))
        ));

        assertThatThrownBy(() -> service.assertPublicBookingAllowed(
                identity,
                visitDate,
                3L,
                BranchSessionType.AM,
                LocalTime.of(8, 0)
        )).isInstanceOfSatisfying(ApiException.class, ex ->
                assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BOOKING_ACTIVE_LIMIT_EXCEEDED)
        );
    }

    private void allowNoActiveBookings() {
        when(appointmentRepository.findActiveByDoctorSlotAndPatientPhoneIn(
                any(),
                any(),
                any(),
                any(),
                anyCollection(),
                anyCollection()
        )).thenReturn(List.of());
        when(appointmentRepository.findActiveByPatientPhoneInAndVisitDateBetween(
                anyCollection(),
                any(),
                any(),
                anyCollection()
        )).thenReturn(List.of());
    }

    private PatientBookingRestriction savedRestriction() {
        ArgumentCaptor<PatientBookingRestriction> captor = ArgumentCaptor.forClass(PatientBookingRestriction.class);
        verify(restrictionRepository).save(captor.capture());
        return captor.getValue();
    }

    private Appointment activeAppointment(LocalDate date, LocalDate dob) {
        return Appointment.builder()
                .id(date.toEpochDay())
                .status(AppointmentStatus.CONFIRMED)
                .visitDate(date)
                .session(BranchSessionType.AM)
                .etaStart(LocalTime.of(9, 0))
                .patientPhone("0900000000")
                .patientFullName("Other Patient")
                .patientDob(dob)
                .build();
    }
}
