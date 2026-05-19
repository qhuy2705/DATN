package com.PrimeCare.PrimeCare.modules.appointment.service;

import com.PrimeCare.PrimeCare.modules.appointment.dto.request.AppointmentRescheduleRequest;
import com.PrimeCare.PrimeCare.modules.appointment.dto.request.UpdateAppointmentIntakeRequest;
import com.PrimeCare.PrimeCare.modules.appointment.dto.record.CheckInTokenPayload;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.AppointmentAdminResponse;
import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentCallLogRepository;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.booking_restriction.service.PatientViolationEventService;
import com.PrimeCare.PrimeCare.modules.doctor_schedule.repository.DoctorWorkScheduleRepository;
import com.PrimeCare.PrimeCare.modules.encounter.repository.EncounterRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.repository.BranchSessionRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.entity.Specialty;
import com.PrimeCare.PrimeCare.modules.notification.messaging.AppointmentMailEventPublisher;
import com.PrimeCare.PrimeCare.modules.notification.service.AppointmentPdfJobService;
import com.PrimeCare.PrimeCare.modules.notification.service.InternalNotificationService;
import com.PrimeCare.PrimeCare.modules.realtime.service.AfterCommitExecutor;
import com.PrimeCare.PrimeCare.modules.realtime.service.RealtimeEventPublisher;
import com.PrimeCare.PrimeCare.modules.staff.entity.StaffProfile;
import com.PrimeCare.PrimeCare.modules.triage.TriageAuditService;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentCancellationReasonType;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentContactStatus;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentPatientResponseStatus;
import com.PrimeCare.PrimeCare.shared.enums.ArrivalStatus;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import com.PrimeCare.PrimeCare.shared.enums.CallOutcome;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentAdminServiceClaimTest {

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AppointmentCallLogRepository appointmentCallLogRepository;
    @Mock
    private AppointmentMailEventPublisher appointmentMailEventPublisher;
    @Mock
    private AppointmentCheckInTokenService appointmentCheckInTokenService;
    @Mock
    private AppointmentAvailabilityService appointmentAvailabilityService;
    @Mock
    private DoctorWorkScheduleRepository doctorWorkScheduleRepository;
    @Mock
    private BranchSessionRepository branchSessionRepository;
    @Mock
    private AppointmentCodeGenerator appointmentCodeGenerator;
    @Mock
    private AppointmentStatusHistoryService appointmentStatusHistoryService;
    @Mock
    private AppointmentPdfJobService appointmentPdfJobService;
    @Mock
    private ReceptionQueueAllocator receptionQueueAllocator;
    @Mock
    private AppointmentQueueService appointmentQueueService;
    @Mock
    private EncounterRepository encounterRepository;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private RealtimeEventPublisher realtimeEventPublisher;
    @Mock
    private AfterCommitExecutor afterCommitExecutor;
    @Mock
    private InternalNotificationService internalNotificationService;
    @Mock
    private TriageAuditService triageAuditService;
    @Mock
    private DoctorCancellationRecoveryService doctorCancellationRecoveryService;
    @Mock
    private PatientViolationEventService patientViolationEventService;
    @Mock
    private AppointmentResponseService appointmentResponseService;

    @InjectMocks
    private AppointmentAdminService service;

    @Test
    void claimAllowsNoShowFollowUpPending() {
        User staff = staff(10L, "Lễ tân A");
        Appointment appointment = appointment(AppointmentStatus.NO_SHOW, true);
        appointment.setProcessingBy(staff);
        appointment.setProcessingStartedAt(LocalDateTime.now());
        appointment.setProcessingExpiresAt(LocalDateTime.now().plusMinutes(5));

        when(userRepository.findById(10L)).thenReturn(Optional.of(staff));
        when(appointmentRepository.claimForProcessing(eq(1L), same(staff), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(1);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));
        when(appointmentRepository.findFirstByRescheduledFromAppointment_IdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.empty());

        AppointmentAdminResponse response = service.claim(1L, 10L);

        assertThat(response.getStatus()).isEqualTo(AppointmentStatus.NO_SHOW);
        assertThat(response.getFollowUpPending()).isTrue();
        assertThat(response.getProcessingById()).isEqualTo(10L);
        verify(auditLogService).log(eq(staff), eq("CLAIM"), eq("APPOINTMENT"), eq(1L), eq(null), any());
    }

    @Test
    void claimNoShowAlreadyFollowedUpReturnsSpecificMessage() {
        User staff = staff(10L, "Lễ tân A");
        Appointment appointment = appointment(AppointmentStatus.NO_SHOW, false);

        when(userRepository.findById(10L)).thenReturn(Optional.of(staff));
        when(appointmentRepository.claimForProcessing(eq(1L), same(staff), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(0);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> service.claim(1L, 10L))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.APPOINTMENT_INVALID_STATUS);
                    assertThat(ex.getMessage()).isEqualTo("Follow-up của lịch hẹn này đã được xử lý.");
                });

        verify(auditLogService, never()).log(any(), any(), any(), anyLong(), any(), any());
    }

    @Test
    void claimActiveClaimByAnotherUserReturnsHolderName() {
        User staff = staff(10L, "Lễ tân A");
        User otherStaff = staff(20L, "Lễ tân B");
        Appointment appointment = appointment(AppointmentStatus.REQUESTED, false);
        appointment.setProcessingBy(otherStaff);
        appointment.setProcessingStartedAt(LocalDateTime.now());
        appointment.setProcessingExpiresAt(LocalDateTime.now().plusMinutes(5));

        when(userRepository.findById(10L)).thenReturn(Optional.of(staff));
        when(appointmentRepository.claimForProcessing(eq(1L), same(staff), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(0);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> service.claim(1L, 10L))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.APPOINTMENT_ALREADY_CLAIMED);
                    assertThat(ex.getMessage()).contains("Lịch hẹn đang được xử lý bởi Lễ tân B đến ");
                });
    }

    @Test
    void claimAlreadyOwnedByCurrentUserReturnsCurrentAppointment() {
        User staff = staff(10L, "Lễ tân A");
        Appointment appointment = appointment(AppointmentStatus.CONFIRMED, false);
        appointment.setProcessingBy(staff);
        appointment.setProcessingStartedAt(LocalDateTime.now());
        appointment.setProcessingExpiresAt(LocalDateTime.now().plusMinutes(5));

        when(userRepository.findById(10L)).thenReturn(Optional.of(staff));
        when(appointmentRepository.claimForProcessing(eq(1L), same(staff), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(0);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));
        when(appointmentRepository.findFirstByRescheduledFromAppointment_IdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.empty());

        AppointmentAdminResponse response = service.claim(1L, 10L);

        assertThat(response.getProcessingById()).isEqualTo(10L);
        verify(auditLogService, never()).log(any(), any(), any(), anyLong(), any(), any());
    }

    @Test
    void heartbeatExtendsOwnedClaim() {
        User staff = staff(10L, "Lễ tân A");
        Appointment appointment = appointment(AppointmentStatus.CONFIRMED, false);
        appointment.setProcessingBy(staff);
        appointment.setProcessingStartedAt(LocalDateTime.now());
        appointment.setProcessingExpiresAt(LocalDateTime.now().plusMinutes(5));

        when(appointmentRepository.heartbeatClaim(eq(1L), eq(10L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(1);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));
        when(appointmentRepository.findFirstByRescheduledFromAppointment_IdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.empty());

        AppointmentAdminResponse response = service.heartbeat(1L, 10L);

        assertThat(response.getProcessingById()).isEqualTo(10L);
    }

    @Test
    void heartbeatWithoutOwnedClaimReturnsClaimRequired() {
        when(appointmentRepository.heartbeatClaim(eq(1L), eq(10L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(0);

        assertThatThrownBy(() -> service.heartbeat(1L, 10L))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.APPOINTMENT_CLAIM_REQUIRED);
                    assertThat(ex.getErrorCode()).isNotEqualTo(ErrorCode.UNAUTHORIZED);
                });
    }

    @Test
    void claimRaceAfterRetryReturnsRefreshMessage() {
        User staff = staff(10L, "Lễ tân A");
        Appointment appointment = appointment(AppointmentStatus.CONFIRMED, false);

        when(userRepository.findById(10L)).thenReturn(Optional.of(staff));
        when(appointmentRepository.claimForProcessing(eq(1L), same(staff), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(0, 0);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> service.claim(1L, 10L))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.APPOINTMENT_CLAIM_CONFLICT);
                    assertThat(ex.getMessage()).isEqualTo("Không thể nhận xử lý lịch hẹn lúc này. Vui lòng tải lại dữ liệu.");
                });
    }

    @Test
    void rescheduleNoShowFollowUpRequiresClaimOwnership() {
        User staff = staff(10L, "Lễ tân A");
        Appointment appointment = appointment(AppointmentStatus.NO_SHOW, true);
        AppointmentRescheduleRequest request = new AppointmentRescheduleRequest();
        request.setVisitDate(LocalDate.now().plusDays(1));
        request.setSession(BranchSessionType.AM);
        request.setSlotStart(LocalTime.of(9, 0));
        request.setNote("Đổi lịch sau no-show");

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));
        when(userRepository.findById(10L)).thenReturn(Optional.of(staff));

        assertThatThrownBy(() -> service.reschedule(1L, 10L, request))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.APPOINTMENT_CLAIM_REQUIRED);
                    assertThat(ex.getMessage()).isEqualTo("Bạn không giữ quyền xử lý lịch hẹn này.");
                });

        verifyNoInteractions(doctorWorkScheduleRepository, branchSessionRepository, appointmentAvailabilityService);
    }

    @Test
    void resolveNoShowRequiresClaimOwnership() {
        User staff = staff(10L, "Lễ tân A");
        Appointment appointment = appointment(AppointmentStatus.NO_SHOW, true);

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));
        when(userRepository.findById(10L)).thenReturn(Optional.of(staff));

        assertThatThrownBy(() -> service.resolveNoShowAsClosed(1L, 10L, "Đóng follow-up"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.APPOINTMENT_CLAIM_REQUIRED);
                    assertThat(ex.getMessage()).isEqualTo("Bạn không giữ quyền xử lý lịch hẹn này.");
                });

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void resolveNoShowClearsClaimAfterFollowUpClosed() {
        User staff = staff(10L, "Lễ tân A");
        Appointment appointment = appointment(AppointmentStatus.NO_SHOW, true);
        appointment.setProcessingBy(staff);
        appointment.setProcessingStartedAt(LocalDateTime.now());
        appointment.setProcessingExpiresAt(LocalDateTime.now().plusMinutes(5));

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));
        when(userRepository.findById(10L)).thenReturn(Optional.of(staff));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appointmentRepository.findFirstByRescheduledFromAppointment_IdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.empty());

        AppointmentAdminResponse response = service.resolveNoShowAsClosed(1L, 10L, "Đóng follow-up");

        assertThat(response.getFollowUpPending()).isFalse();
        assertThat(response.getProcessingById()).isNull();
        assertThat(appointment.getProcessingBy()).isNull();
        assertThat(appointment.getProcessingStartedAt()).isNull();
        assertThat(appointment.getProcessingExpiresAt()).isNull();
        verify(auditLogService).log(eq(staff), eq("RESOLVE_NO_SHOW"), eq("APPOINTMENT"), eq(1L), any(), any());
    }

    @Test
    void markNoShowRequiresClaimOwnership() {
        User staff = staff(10L, "Lễ tân A");
        Appointment appointment = appointment(AppointmentStatus.CONFIRMED, false);
        appointment.setVisitDate(LocalDate.now().minusDays(1));

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));
        when(userRepository.findById(10L)).thenReturn(Optional.of(staff));

        assertThatThrownBy(() -> service.markNoShow(1L, 10L, "Không đến"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.APPOINTMENT_CLAIM_REQUIRED);
                    assertThat(ex.getMessage()).isEqualTo("Bạn không giữ quyền xử lý lịch hẹn này.");
                });

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void markNoShowSucceedsWhenClaimOwned() {
        User staff = staff(10L, "Lễ tân A");
        Appointment appointment = appointment(AppointmentStatus.CONFIRMED, false);
        appointment.setVisitDate(LocalDate.now().minusDays(1));
        appointment.setProcessingBy(staff);
        appointment.setProcessingStartedAt(LocalDateTime.now());
        appointment.setProcessingExpiresAt(LocalDateTime.now().plusMinutes(5));

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));
        when(userRepository.findById(10L)).thenReturn(Optional.of(staff));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appointmentRepository.findFirstByRescheduledFromAppointment_IdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.empty());

        AppointmentAdminResponse response = service.markNoShow(1L, 10L, "Không đến");

        assertThat(response.getStatus()).isEqualTo(AppointmentStatus.NO_SHOW);
        assertThat(response.getFollowUpPending()).isTrue();
        assertThat(appointment.getNoShowMarkedBy()).isSameAs(staff);
        verify(auditLogService).log(eq(staff), eq("MARK_NO_SHOW"), eq("APPOINTMENT"), eq(1L), any(), any());
        verify(patientViolationEventService).recordNoShow(appointment, staff, "Không đến");
    }

    @Test
    void markNoShowRejectsCheckedInAppointment() {
        User staff = staff(10L, "Lễ tân A");
        Appointment appointment = appointment(AppointmentStatus.CHECKED_IN, false);
        appointment.setVisitDate(LocalDate.now().minusDays(1));

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));
        when(userRepository.findById(10L)).thenReturn(Optional.of(staff));

        assertThatThrownBy(() -> service.markNoShow(1L, 10L, "Không đến"))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.APPOINTMENT_INVALID_STATUS)
                );

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void markNoShowRejectsArrivedAppointment() {
        User staff = staff(10L, "Lễ tân A");
        Appointment appointment = appointment(AppointmentStatus.CONFIRMED, false);
        appointment.setVisitDate(LocalDate.now().minusDays(1));
        appointment.setArrivalStatus(ArrivalStatus.ARRIVED);

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));
        when(userRepository.findById(10L)).thenReturn(Optional.of(staff));

        assertThatThrownBy(() -> service.markNoShow(1L, 10L, "Không đến"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.APPOINTMENT_INVALID_STATUS);
                    assertThat(ex.getMessage()).isEqualTo("Bệnh nhân đã đến hoặc đã check-in");
                });

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void cancelPatientRequestWithViolationFlagDelegatesLateCancelScoring() {
        User staff = staff(10L, "Lễ tân A");
        Appointment appointment = appointment(AppointmentStatus.REQUESTED, false);
        LocalDateTime start = LocalDateTime.now().plusHours(2);
        appointment.setVisitDate(start.toLocalDate());
        appointment.setEtaStart(start.toLocalTime());
        appointment.setProcessingBy(staff);
        appointment.setProcessingStartedAt(LocalDateTime.now());
        appointment.setProcessingExpiresAt(LocalDateTime.now().plusMinutes(5));

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));
        when(userRepository.findById(10L)).thenReturn(Optional.of(staff));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appointmentRepository.findFirstByRescheduledFromAppointment_IdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.empty());

        AppointmentAdminResponse response = service.cancel(
                1L,
                10L,
                " Bệnh nhân xin hủy ",
                AppointmentCancellationReasonType.PATIENT_REQUEST,
                false,
                true,
                "Hủy sát giờ"
        );

        assertThat(response.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(appointment.getCancelReason()).isEqualTo("Bệnh nhân xin hủy");
        verify(patientViolationEventService).recordLateCancelIfEligible(
                appointment,
                staff,
                AppointmentCancellationReasonType.PATIENT_REQUEST,
                true,
                "Hủy sát giờ"
        );
        ArgumentCaptor<Object> beforeCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> afterCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditLogService).log(
                eq(staff),
                eq("CANCEL_APPOINTMENT"),
                eq("APPOINTMENT"),
                eq(1L),
                beforeCaptor.capture(),
                afterCaptor.capture()
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> before = (Map<String, Object>) beforeCaptor.getValue();
        @SuppressWarnings("unchecked")
        Map<String, Object> after = (Map<String, Object>) afterCaptor.getValue();
        assertThat(before)
                .containsEntry("appointmentId", 1L)
                .containsEntry("status", "REQUESTED");
        assertThat(after)
                .containsEntry("appointmentId", 1L)
                .containsEntry("status", "CANCELLED")
                .containsEntry("cancelReason", "Bệnh nhân xin hủy")
                .containsEntry("cancellationReasonType", "PATIENT_REQUEST");
    }

    @Test
    void cancelBlankReasonFailsBeforeCancellationSideEffects() {
        assertThatThrownBy(() -> service.cancel(
                1L,
                10L,
                "   ",
                AppointmentCancellationReasonType.PATIENT_REQUEST,
                true,
                true,
                "Không được tạo vi phạm"
        ))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(ex.getMessage()).isEqualTo("Cancellation reason is required.");
                });

        verifyNoInteractions(
                appointmentRepository,
                userRepository,
                auditLogService,
                doctorCancellationRecoveryService,
                patientViolationEventService
        );
    }

    @Test
    void cancelPatientRequestWithoutViolationFlagPassesFalseToScoringGuard() {
        User staff = staff(10L, "Lễ tân A");
        Appointment appointment = appointment(AppointmentStatus.REQUESTED, false);
        appointment.setProcessingBy(staff);
        appointment.setProcessingStartedAt(LocalDateTime.now());
        appointment.setProcessingExpiresAt(LocalDateTime.now().plusMinutes(5));

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));
        when(userRepository.findById(10L)).thenReturn(Optional.of(staff));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appointmentRepository.findFirstByRescheduledFromAppointment_IdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.empty());

        service.cancel(
                1L,
                10L,
                "Bệnh nhân xin hủy",
                AppointmentCancellationReasonType.PATIENT_REQUEST,
                false,
                false,
                null
        );

        verify(patientViolationEventService).recordLateCancelIfEligible(
                appointment,
                staff,
                AppointmentCancellationReasonType.PATIENT_REQUEST,
                false,
                "Bệnh nhân xin hủy"
        );
    }

    @Test
    void doctorCancellationRecoveryDoesNotRecordPatientViolation() {
        User staff = staff(10L, "Lễ tân A");
        Appointment appointment = appointment(AppointmentStatus.REQUESTED, false);
        appointment.setProcessingBy(staff);
        appointment.setProcessingStartedAt(LocalDateTime.now());
        appointment.setProcessingExpiresAt(LocalDateTime.now().plusMinutes(5));

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));
        when(userRepository.findById(10L)).thenReturn(Optional.of(staff));
        when(appointmentRepository.findFirstByRescheduledFromAppointment_IdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.empty());

        service.cancel(
                1L,
                10L,
                "Bác sĩ bận",
                AppointmentCancellationReasonType.DOCTOR_UNAVAILABLE,
                true,
                true,
                "Không được tính"
        );

        verify(doctorCancellationRecoveryService).recoverSingleDoctorCancellation(appointment, staff, "Bác sĩ bận");
        verify(patientViolationEventService, never()).recordLateCancelIfEligible(any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void callResultNoAnswerSetsFallbackStateAndSendsEmail() {
        User staff = staff(10L, "Lễ tân A");
        Appointment appointment = appointment(AppointmentStatus.REQUESTED, false);
        appointment.setPatientEmail("patient@example.test");
        appointment.setProcessingBy(staff);
        appointment.setProcessingStartedAt(LocalDateTime.now());
        appointment.setProcessingExpiresAt(LocalDateTime.now().plusMinutes(5));

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));
        when(userRepository.findById(10L)).thenReturn(Optional.of(staff));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appointmentRepository.findFirstByRescheduledFromAppointment_IdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.empty());

        AppointmentAdminResponse response = service.recordCallResult(
                1L,
                10L,
                CallOutcome.NO_ANSWER,
                "Không nghe máy",
                true
        );

        assertThat(response.getContactStatus()).isEqualTo(AppointmentContactStatus.PHONE_UNREACHABLE);
        assertThat(response.getPatientResponseStatus()).isEqualTo(AppointmentPatientResponseStatus.NEED_PATIENT_RESPONSE);
        assertThat(appointment.getContactStatus()).isEqualTo(AppointmentContactStatus.PHONE_UNREACHABLE);
        assertThat(appointment.getPatientResponseStatus()).isEqualTo(AppointmentPatientResponseStatus.NEED_PATIENT_RESPONSE);
        verify(appointmentResponseService).sendFallbackEmail(appointment, staff, "Không nghe máy");
        verify(patientViolationEventService, never()).recordWrongContact(any(), any(), any());
    }

    @Test
    void wrongContactViolationIsManualStaffAction() {
        User staff = staff(10L, "Lễ tân A");
        Appointment appointment = appointment(AppointmentStatus.REQUESTED, false);
        appointment.setProcessingBy(staff);
        appointment.setProcessingStartedAt(LocalDateTime.now());
        appointment.setProcessingExpiresAt(LocalDateTime.now().plusMinutes(5));

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));
        when(userRepository.findById(10L)).thenReturn(Optional.of(staff));
        when(appointmentRepository.findFirstByRescheduledFromAppointment_IdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.empty());

        service.markWrongContactViolation(1L, 10L, "Sau nhiều lần xác minh, số điện thoại sai");

        verify(patientViolationEventService).recordWrongContact(
                appointment,
                staff,
                "Sau nhiều lần xác minh, số điện thoại sai"
        );
    }

    @Test
    void releaseClaimWithoutClaimIsIdempotentSuccess() {
        Appointment appointment = appointment(AppointmentStatus.CONFIRMED, false);

        when(appointmentRepository.releaseClaim(1L, 10L)).thenReturn(0);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));
        when(appointmentRepository.findFirstByRescheduledFromAppointment_IdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.empty());

        AppointmentAdminResponse response = service.releaseClaim(1L, 10L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getProcessingById()).isNull();
        verify(userRepository, never()).findById(anyLong());
        verify(auditLogService, never()).log(any(), any(), any(), anyLong(), any(), any());
    }

    @Test
    void releaseClaimHeldByAnotherUserUsesClaimErrorNotUnauthorized() {
        User otherStaff = staff(20L, "Lễ tân B");
        Appointment appointment = appointment(AppointmentStatus.CONFIRMED, false);
        appointment.setProcessingBy(otherStaff);
        appointment.setProcessingStartedAt(LocalDateTime.now());
        appointment.setProcessingExpiresAt(LocalDateTime.now().plusMinutes(5));

        when(appointmentRepository.releaseClaim(1L, 10L)).thenReturn(0);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> service.releaseClaim(1L, 10L))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.APPOINTMENT_CLAIM_REQUIRED);
                    assertThat(ex.getErrorCode()).isNotEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(ex.getMessage()).isEqualTo("Bạn không giữ quyền xử lý lịch hẹn này.");
                });

        verify(auditLogService, never()).log(any(), any(), any(), anyLong(), any(), any());
    }

    @Test
    void qrCheckInSetsArrivalAndCheckInAuditFields() {
        User staff = staff(10L, "Lễ tân A");
        Appointment appointment = appointment(AppointmentStatus.CONFIRMED, false);

        when(appointmentCheckInTokenService.verify("qr-token"))
                .thenReturn(new CheckInTokenPayload(1L, "APT-001", LocalDate.now().toString()));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));
        when(userRepository.findById(10L)).thenReturn(Optional.of(staff));
        when(receptionQueueAllocator.allocateNext(1L, appointment.getVisitDate())).thenReturn(9);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appointmentRepository.findFirstByRescheduledFromAppointment_IdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.empty());

        AppointmentAdminResponse response = service.checkInByQrToken("qr-token", 10L);

        assertThat(response.getStatus()).isEqualTo(AppointmentStatus.CHECKED_IN);
        assertThat(appointment.getArrivalStatus()).isEqualTo(ArrivalStatus.ARRIVED);
        assertThat(appointment.getArrivedAt()).isNotNull();
        assertThat(appointment.getArrivedBy()).isSameAs(staff);
        assertThat(appointment.getCheckedInAt()).isNotNull();
        assertThat(appointment.getCheckedInBy()).isSameAs(staff);
        assertThat(appointment.getReceptionQueueNo()).isEqualTo(9);
    }

    @Test
    void shouldUpdateOnlyProvidedIntakeFieldsWhenPatchRequestContainsNulls() {
        User staff = staff(10L, "Lễ tân A");
        Appointment appointment = appointment(AppointmentStatus.CONFIRMED, false);
        appointment.setProcessingBy(staff);
        appointment.setProcessingStartedAt(LocalDateTime.now());
        appointment.setProcessingExpiresAt(LocalDateTime.now().plusMinutes(5));
        appointment.setReasonForVisit("Khám đau bụng");
        appointment.setVisitType("NEW");
        appointment.setTriagePriority("NORMAL");
        appointment.setTriageNote("Ghi chú phân loại cũ");
        appointment.setInsuranceNote("Có BHYT");
        appointment.setEmergencyContactName("Người nhà");
        appointment.setEmergencyContactPhone("0900000001");
        appointment.setHeightCm(170.0);
        appointment.setWeightKg(65.0);
        appointment.setTemperatureC(36.8);
        appointment.setPulse(78);
        appointment.setSystolicBp(120);
        appointment.setDiastolicBp(80);
        appointment.setRespiratoryRate(18);
        appointment.setSpo2(99);
        UpdateAppointmentIntakeRequest request = new UpdateAppointmentIntakeRequest();
        request.setTriageNote("Ghi chú phân loại mới");

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));
        when(userRepository.findById(10L)).thenReturn(Optional.of(staff));
        when(appointmentRepository.save(appointment)).thenReturn(appointment);
        when(appointmentRepository.findFirstByRescheduledFromAppointment_IdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.empty());

        AppointmentAdminResponse response = service.updateIntake(1L, 10L, request);

        assertThat(response.getTriageNote()).isEqualTo("Ghi chú phân loại mới");
        assertThat(appointment.getReasonForVisit()).isEqualTo("Khám đau bụng");
        assertThat(appointment.getVisitType()).isEqualTo("NEW");
        assertThat(appointment.getTriagePriority()).isEqualTo("NORMAL");
        assertThat(appointment.getInsuranceNote()).isEqualTo("Có BHYT");
        assertThat(appointment.getEmergencyContactName()).isEqualTo("Người nhà");
        assertThat(appointment.getEmergencyContactPhone()).isEqualTo("0900000001");
        assertThat(appointment.getHeightCm()).isEqualTo(170.0);
        assertThat(appointment.getWeightKg()).isEqualTo(65.0);
        assertThat(appointment.getTemperatureC()).isEqualTo(36.8);
        assertThat(appointment.getPulse()).isEqualTo(78);
        assertThat(appointment.getSystolicBp()).isEqualTo(120);
        assertThat(appointment.getDiastolicBp()).isEqualTo(80);
        assertThat(appointment.getRespiratoryRate()).isEqualTo(18);
        assertThat(appointment.getSpo2()).isEqualTo(99);
    }

    private Appointment appointment(AppointmentStatus status, Boolean followUpPending) {
        Branch branch = Branch.builder()
                              .id(1L)
                              .nameVn("PrimeCare Quận 1")
                              .build();
        Specialty specialty = Specialty.builder()
                                       .id(2L)
                                       .nameVn("Nội tổng quát")
                                       .build();
        DoctorProfile doctor = DoctorProfile.builder()
                                            .id(3L)
                                            .fullName("BS. Nguyễn Văn A")
                                            .branch(branch)
                                            .build();
        return Appointment.builder()
                          .id(1L)
                          .code("APT-001")
                          .status(status)
                          .branch(branch)
                          .specialty(specialty)
                          .doctor(doctor)
                          .visitDate(LocalDate.now())
                          .session(BranchSessionType.AM)
                          .sourceType(com.PrimeCare.PrimeCare.shared.enums.AppointmentSourceType.PUBLIC_BOOKING)
                          .arrivalStatus(ArrivalStatus.NOT_ARRIVED)
                          .slotMinutes(30)
                          .etaStart(LocalTime.of(8, 0))
                          .etaEnd(LocalTime.of(8, 30))
                          .patientFullName("Nguyễn Thị B")
                          .patientPhone("0900000000")
                          .followUpPending(followUpPending)
                          .build();
    }

    private User staff(Long id, String fullName) {
        return User.builder()
                   .id(id)
                   .email("staff" + id + "@primecare.test")
                   .role(UserRole.STAFF)
                   .staffProfile(StaffProfile.builder()
                                             .fullName(fullName)
                                             .build())
                   .build();
    }
}
