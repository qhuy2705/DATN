package com.PrimeCare.PrimeCare.modules.appointment.service;

import com.PrimeCare.PrimeCare.modules.appointment.dto.request.AppointmentConfirmRequest;
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
import com.PrimeCare.PrimeCare.shared.enums.AppointmentContactStatus;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentPatientResponseStatus;
import com.PrimeCare.PrimeCare.shared.enums.ArrivalStatus;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentAdminServiceConfirmTriageTest {

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
    void confirmWithTriagePrioritySetsOfficialPriority() {
        Appointment appointment = appointment("URGENT");
        AppointmentConfirmRequest request = request("P1", null, null);

        AppointmentAdminResponse response = confirm(appointment, request);

        assertThat(response.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(appointment.getContactStatus()).isEqualTo(AppointmentContactStatus.PHONE_STAFF_VERIFIED);
        assertThat(appointment.getPatientResponseStatus()).isEqualTo(AppointmentPatientResponseStatus.NONE);
        assertThat(appointment.getTriagePriority()).isEqualTo("URGENT");
        assertThat(appointment.getTriageReviewedBy().getId()).isEqualTo(10L);
        assertThat(appointment.getTriageReviewedAt()).isNotNull();
    }

    @Test
    void confirmAcceptedStoresAccepted() {
        Appointment appointment = appointment("PRIORITY");
        AppointmentConfirmRequest request = request("P2", "ACCEPTED", null);

        confirm(appointment, request);

        assertThat(appointment.getTriageReviewStatus()).isEqualTo("ACCEPTED");
    }

    @Test
    void confirmOverrideStoresOverriddenAndReason() {
        Appointment appointment = appointment("ROUTINE");
        AppointmentConfirmRequest request = request("P1", null, "Đau tăng nhanh khi gọi xác nhận");

        confirm(appointment, request);

        assertThat(appointment.getTriagePriority()).isEqualTo("URGENT");
        assertThat(appointment.getTriageReviewStatus()).isEqualTo("OVERRIDDEN");
        assertThat(appointment.getTriageOverrideReason()).isEqualTo("Đau tăng nhanh khi gọi xác nhận");
    }

    @Test
    void confirmWithoutNoteDoesNotFail() {
        Appointment appointment = appointment("ROUTINE");
        AppointmentConfirmRequest request = request("P3", "ACCEPTED", null);
        request.setNote(null);

        AppointmentAdminResponse response = confirm(appointment, request);

        assertThat(response.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(appointment.getTriagePriority()).isEqualTo("ROUTINE");
    }

    @Test
    void confirmWithoutSuggestionStoresManualReviewStatus() {
        Appointment appointment = appointment(null);
        AppointmentConfirmRequest request = request("P2", null, null);

        confirm(appointment, request);

        assertThat(appointment.getTriagePriority()).isEqualTo("PRIORITY");
        assertThat(appointment.getTriageReviewStatus()).isEqualTo("MANUAL");
    }

    @ParameterizedTest
    @CsvSource({
            "P1,URGENT",
            "P2,PRIORITY",
            "P3,ROUTINE",
            "NORMAL,ROUTINE",
            "HIGH,PRIORITY"
    })
    void confirmNormalizesPriorityAliases(String rawPriority, String normalizedPriority) {
        Appointment appointment = appointment(normalizedPriority);
        AppointmentConfirmRequest request = request(rawPriority, null, null);

        confirm(appointment, request);

        assertThat(appointment.getTriagePriority()).isEqualTo(normalizedPriority);
    }

    private AppointmentAdminResponse confirm(Appointment appointment, AppointmentConfirmRequest request) {
        User staff = User.builder()
                .id(10L)
                .role(UserRole.STAFF)
                .staffProfile(StaffProfile.builder().fullName("Lễ tân A").build())
                .build();
        appointment.setProcessingBy(staff);
        appointment.setProcessingStartedAt(LocalDateTime.now());
        appointment.setProcessingExpiresAt(LocalDateTime.now().plusMinutes(5));

        when(userRepository.findById(10L)).thenReturn(Optional.of(staff));
        when(userRepository.findByDoctorProfile_Id(3L)).thenReturn(Optional.empty());
        when(appointmentRepository.findById(appointment.getId())).thenReturn(Optional.of(appointment));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appointmentRepository.findFirstByRescheduledFromAppointment_IdOrderByCreatedAtDesc(appointment.getId()))
                .thenReturn(Optional.empty());

        return service.confirm(appointment.getId(), 10L, request);
    }

    private AppointmentConfirmRequest request(String priority, String reviewStatus, String overrideReason) {
        AppointmentConfirmRequest request = new AppointmentConfirmRequest();
        request.setNote("Đã gọi xác nhận");
        request.setTriagePriority(priority);
        request.setTriageNote("Staff xác nhận ưu tiên");
        request.setTriageReviewStatus(reviewStatus);
        request.setTriageOverrideReason(overrideReason);
        return request;
    }

    private Appointment appointment(String preTriagePriority) {
        Branch branch = Branch.builder().id(1L).nameVn("PrimeCare").build();
        Specialty specialty = Specialty.builder().id(2L).nameVn("Nội tổng quát").build();
        DoctorProfile doctor = DoctorProfile.builder().id(3L).fullName("Dr Test").branch(branch).build();
        return Appointment.builder()
                .id(1L)
                .code("APT-1")
                .status(AppointmentStatus.REQUESTED)
                .branch(branch)
                .specialty(specialty)
                .doctor(doctor)
                .visitDate(LocalDate.now().plusDays(1))
                .session(BranchSessionType.AM)
                .sourceType(com.PrimeCare.PrimeCare.shared.enums.AppointmentSourceType.PUBLIC_BOOKING)
                .arrivalStatus(ArrivalStatus.NOT_ARRIVED)
                .slotMinutes(30)
                .etaStart(LocalTime.of(8, 0))
                .etaEnd(LocalTime.of(8, 30))
                .patientFullName("Nguyen Van A")
                .patientPhone("0900000000")
                .preTriagePriority(preTriagePriority)
                .preTriageLevel("WATCH")
                .build();
    }
}
