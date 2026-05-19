package com.PrimeCare.PrimeCare.modules.booking_restriction.service;

import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.booking_restriction.entity.PatientViolationEvent;
import com.PrimeCare.PrimeCare.modules.booking_restriction.enums.ViolationEventSource;
import com.PrimeCare.PrimeCare.modules.booking_restriction.enums.ViolationEventStatus;
import com.PrimeCare.PrimeCare.modules.booking_restriction.enums.ViolationEventType;
import com.PrimeCare.PrimeCare.modules.booking_restriction.repository.PatientViolationEventRepository;
import com.PrimeCare.PrimeCare.modules.patient.entity.Patient;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentCancellationReasonType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientViolationEventServiceTest {

    @Mock
    private PatientViolationEventRepository violationEventRepository;
    @Mock
    private BookingRestrictionPolicyService bookingRestrictionPolicyService;
    @Mock
    private AuditLogService auditLogService;

    private BookingIdentityService bookingIdentityService;
    private PatientViolationEventService service;

    @BeforeEach
    void setUp() {
        bookingIdentityService = new BookingIdentityService("test-pepper");
        service = new PatientViolationEventService(
                violationEventRepository,
                bookingIdentityService,
                bookingRestrictionPolicyService,
                auditLogService
        );
    }

    @Test
    void recordNoShowCreatesActiveNoShowEventWithThreePoints() {
        Appointment appointment = appointment();
        User staff = User.builder().id(10L).build();
        when(violationEventRepository.findFirstByAppointment_IdAndTypeAndStatusOrderByCreatedAtDesc(
                1L,
                ViolationEventType.NO_SHOW,
                ViolationEventStatus.ACTIVE
        )).thenReturn(Optional.empty());
        when(violationEventRepository.save(any(PatientViolationEvent.class))).thenAnswer(invocation -> {
            PatientViolationEvent event = invocation.getArgument(0);
            event.setId(50L);
            return event;
        });
        when(bookingRestrictionPolicyService.periodMonth(LocalDate.of(2026, 5, 15))).thenReturn("2026-05");

        PatientViolationEvent saved = service.recordNoShow(appointment, staff, "Không đến");

        assertThat(saved.getId()).isEqualTo(50L);
        PatientViolationEvent event = savedEvent();
        assertThat(event.getType()).isEqualTo(ViolationEventType.NO_SHOW);
        assertThat(event.getSource()).isEqualTo(ViolationEventSource.SYSTEM);
        assertThat(event.getStatus()).isEqualTo(ViolationEventStatus.ACTIVE);
        assertThat(event.getPoints()).isEqualTo(3);
        assertThat(event.getIdentityKeyHash()).hasSize(64);
        assertThat(event.getPeriodMonth()).isEqualTo("2026-05");
        verify(bookingRestrictionPolicyService).recalculateAfterScoreChange(
                eq(event.getIdentityKeyHash()),
                eq(event.getPatient()),
                eq(staff),
                eq("Tự động kích hoạt sau sự kiện no-show")
        );
    }

    @Test
    void duplicateNoShowReturnsExistingEventWithoutCreatingAnotherOne() {
        Appointment appointment = appointment();
        PatientViolationEvent existing = PatientViolationEvent.builder()
                .id(99L)
                .appointment(appointment)
                .type(ViolationEventType.NO_SHOW)
                .status(ViolationEventStatus.ACTIVE)
                .points(3)
                .build();
        when(violationEventRepository.findFirstByAppointment_IdAndTypeAndStatusOrderByCreatedAtDesc(
                1L,
                ViolationEventType.NO_SHOW,
                ViolationEventStatus.ACTIVE
        )).thenReturn(Optional.of(existing));

        PatientViolationEvent returned = service.recordNoShow(appointment, User.builder().id(10L).build(), "Không đến");

        assertThat(returned).isSameAs(existing);
        verify(violationEventRepository, never()).save(any());
        verifyNoInteractions(bookingRestrictionPolicyService);
    }

    @Test
    void lateCancelCreatesTwoPointEventOnlyWhenStaffCountsPatientRequestWithinFourHours() {
        Appointment appointment = appointment();
        LocalDateTime start = LocalDateTime.now().plusHours(2);
        appointment.setVisitDate(start.toLocalDate());
        appointment.setEtaStart(start.toLocalTime());
        appointment.setCancelledAt(LocalDateTime.now());
        User staff = User.builder().id(10L).build();

        when(violationEventRepository.findFirstByAppointment_IdAndTypeAndStatusOrderByCreatedAtDesc(
                1L,
                ViolationEventType.LATE_CANCEL,
                ViolationEventStatus.ACTIVE
        )).thenReturn(Optional.empty());
        when(violationEventRepository.save(any(PatientViolationEvent.class))).thenAnswer(invocation -> {
            PatientViolationEvent event = invocation.getArgument(0);
            event.setId(51L);
            return event;
        });
        when(bookingRestrictionPolicyService.periodMonth(any(LocalDate.class))).thenReturn("2026-05");

        PatientViolationEvent saved = service.recordLateCancelIfEligible(
                appointment,
                staff,
                AppointmentCancellationReasonType.PATIENT_REQUEST,
                true,
                "Hủy sát giờ"
        );

        assertThat(saved.getId()).isEqualTo(51L);
        PatientViolationEvent event = savedEvent();
        assertThat(event.getType()).isEqualTo(ViolationEventType.LATE_CANCEL);
        assertThat(event.getSource()).isEqualTo(ViolationEventSource.STAFF);
        assertThat(event.getPoints()).isEqualTo(2);
        verify(bookingRestrictionPolicyService).recalculateAfterScoreChange(
                eq(event.getIdentityKeyHash()),
                eq(event.getPatient()),
                eq(staff),
                eq("Tự động cập nhật hạn chế sau hủy trễ do bệnh nhân yêu cầu")
        );
    }

    @Test
    void patientCancelWithoutViolationFlagDoesNotCreateEvent() {
        Appointment appointment = appointment();

        PatientViolationEvent event = service.recordLateCancelIfEligible(
                appointment,
                User.builder().id(10L).build(),
                AppointmentCancellationReasonType.PATIENT_REQUEST,
                false,
                "Không tính điểm"
        );

        assertThat(event).isNull();
        verify(violationEventRepository, never()).save(any());
        verifyNoInteractions(bookingRestrictionPolicyService, auditLogService);
    }

    @Test
    void doctorCancellationNeverCreatesPatientViolation() {
        Appointment appointment = appointment();

        PatientViolationEvent event = service.recordLateCancelIfEligible(
                appointment,
                User.builder().id(10L).build(),
                AppointmentCancellationReasonType.DOCTOR_UNAVAILABLE,
                true,
                "Bác sĩ bận"
        );

        assertThat(event).isNull();
        verify(violationEventRepository, never()).save(any());
        verifyNoInteractions(bookingRestrictionPolicyService, auditLogService);
    }

    @Test
    void successfulVisitCreatesCreditWhenMonthlyScoreIsPositive() {
        Appointment appointment = appointment();
        appointment.setStatus(AppointmentStatus.COMPLETED);
        appointment.setCompletedAt(LocalDateTime.of(2026, 5, 20, 10, 0));
        User doctor = User.builder().id(20L).build();

        when(violationEventRepository.findFirstByAppointment_IdAndTypeAndStatusOrderByCreatedAtDesc(
                1L,
                ViolationEventType.SUCCESSFUL_VISIT_CREDIT,
                ViolationEventStatus.ACTIVE
        )).thenReturn(Optional.empty());
        when(bookingRestrictionPolicyService.periodMonth(LocalDate.of(2026, 5, 20))).thenReturn("2026-05");
        when(bookingRestrictionPolicyService.monthlyScore(any(String.class), eq("2026-05"))).thenReturn(3);
        when(violationEventRepository.save(any(PatientViolationEvent.class))).thenAnswer(invocation -> {
            PatientViolationEvent event = invocation.getArgument(0);
            event.setId(52L);
            return event;
        });

        PatientViolationEvent saved = service.recordSuccessfulVisitCredit(appointment, doctor);

        assertThat(saved.getId()).isEqualTo(52L);
        PatientViolationEvent event = savedEvent();
        assertThat(event.getType()).isEqualTo(ViolationEventType.SUCCESSFUL_VISIT_CREDIT);
        assertThat(event.getPoints()).isEqualTo(-1);
        verify(bookingRestrictionPolicyService).recalculateAfterScoreChange(
                eq(event.getIdentityKeyHash()),
                eq(event.getPatient()),
                eq(doctor),
                eq("Tự động cập nhật hạn chế sau lần khám hoàn tất")
        );
    }

    @Test
    void successfulVisitDoesNotCreateDuplicateCredit() {
        Appointment appointment = appointment();
        PatientViolationEvent existing = PatientViolationEvent.builder()
                .id(77L)
                .appointment(appointment)
                .type(ViolationEventType.SUCCESSFUL_VISIT_CREDIT)
                .status(ViolationEventStatus.ACTIVE)
                .points(-1)
                .build();
        when(violationEventRepository.findFirstByAppointment_IdAndTypeAndStatusOrderByCreatedAtDesc(
                1L,
                ViolationEventType.SUCCESSFUL_VISIT_CREDIT,
                ViolationEventStatus.ACTIVE
        )).thenReturn(Optional.of(existing));

        PatientViolationEvent returned = service.recordSuccessfulVisitCredit(appointment, User.builder().id(20L).build());

        assertThat(returned).isSameAs(existing);
        verify(violationEventRepository, never()).save(any());
        verifyNoInteractions(bookingRestrictionPolicyService);
    }

    @Test
    void staffPardonStoresNegativePointsAndRecalculates() {
        BookingIdentity identity = bookingIdentityService.resolvePublicIdentity(
                "0900000000",
                "Nguyen Van A",
                LocalDate.of(1990, 1, 1),
                "patient@example.test"
        );
        Patient patient = Patient.builder().id(7L).fullName("Nguyen Van A").phone("0900000000").build();
        User staff = User.builder().id(10L).build();
        when(bookingRestrictionPolicyService.periodMonth(any(LocalDate.class))).thenReturn("2026-05");
        when(violationEventRepository.save(any(PatientViolationEvent.class))).thenAnswer(invocation -> {
            PatientViolationEvent event = invocation.getArgument(0);
            event.setId(53L);
            return event;
        });

        PatientViolationEvent saved = service.createStaffPardon(identity, patient, null, 3, "Nhập sai no-show", staff);

        assertThat(saved.getPoints()).isEqualTo(-3);
        assertThat(saved.getType()).isEqualTo(ViolationEventType.STAFF_PARDON);
        verify(bookingRestrictionPolicyService).recalculateAfterScoreChange(
                eq(identity.identityKeyHash()),
                eq(patient),
                eq(staff),
                eq("Nhân viên ân xá điểm vi phạm")
        );
    }

    @Test
    void wrongContactManualStaffActionCreatesTwoPointViolation() {
        Appointment appointment = appointment();
        User staff = User.builder().id(10L).build();
        when(violationEventRepository.findFirstByAppointment_IdAndTypeAndStatusOrderByCreatedAtDesc(
                1L,
                ViolationEventType.WRONG_CONTACT,
                ViolationEventStatus.ACTIVE
        )).thenReturn(Optional.empty());
        when(bookingRestrictionPolicyService.periodMonth(any(LocalDate.class))).thenReturn("2026-05");
        when(violationEventRepository.save(any(PatientViolationEvent.class))).thenAnswer(invocation -> {
            PatientViolationEvent event = invocation.getArgument(0);
            event.setId(54L);
            return event;
        });

        PatientViolationEvent saved = service.recordWrongContact(appointment, staff, "Sai số sau nhiều lần xác minh");

        assertThat(saved.getType()).isEqualTo(ViolationEventType.WRONG_CONTACT);
        assertThat(saved.getSource()).isEqualTo(ViolationEventSource.STAFF);
        assertThat(saved.getPoints()).isEqualTo(2);
        verify(bookingRestrictionPolicyService).recalculateAfterScoreChange(
                eq(saved.getIdentityKeyHash()),
                eq(saved.getPatient()),
                eq(staff),
                eq("Nhân viên xác nhận sai thông tin liên hệ")
        );
    }

    @Test
    void voidActiveEventMarksVoidedAndRecalculates() {
        User staff = User.builder().id(10L).build();
        Patient patient = Patient.builder().id(7L).build();
        PatientViolationEvent active = PatientViolationEvent.builder()
                .id(60L)
                .patient(patient)
                .identityKeyHash("hash")
                .periodMonth("2026-05")
                .type(ViolationEventType.NO_SHOW)
                .status(ViolationEventStatus.ACTIVE)
                .points(3)
                .build();
        when(violationEventRepository.findById(60L)).thenReturn(Optional.of(active));
        when(violationEventRepository.save(active)).thenReturn(active);

        PatientViolationEvent saved = service.voidViolationEvent(60L, "Đánh dấu nhầm", staff);

        assertThat(saved.getStatus()).isEqualTo(ViolationEventStatus.VOIDED);
        assertThat(saved.getVoidedBy()).isSameAs(staff);
        assertThat(saved.getVoidedAt()).isNotNull();
        assertThat(saved.getVoidReason()).isEqualTo("Đánh dấu nhầm");
        verify(bookingRestrictionPolicyService).recalculateAfterScoreChange(
                "hash",
                patient,
                staff,
                "Void sự kiện vi phạm: Đánh dấu nhầm"
        );
    }

    private PatientViolationEvent savedEvent() {
        ArgumentCaptor<PatientViolationEvent> captor = ArgumentCaptor.forClass(PatientViolationEvent.class);
        verify(violationEventRepository).save(captor.capture());
        return captor.getValue();
    }

    private Appointment appointment() {
        Patient patient = Patient.builder()
                .id(7L)
                .fullName("Nguyen Van A")
                .phone("0900000000")
                .dob(LocalDate.of(1990, 1, 1))
                .build();
        return Appointment.builder()
                .id(1L)
                .status(AppointmentStatus.NO_SHOW)
                .patient(patient)
                .patientFullName("Nguyen Van A")
                .patientPhone("0900000000")
                .patientDob(LocalDate.of(1990, 1, 1))
                .patientEmail("patient@example.test")
                .noShowMarkedAt(LocalDateTime.of(2026, 5, 15, 9, 0))
                .build();
    }
}
