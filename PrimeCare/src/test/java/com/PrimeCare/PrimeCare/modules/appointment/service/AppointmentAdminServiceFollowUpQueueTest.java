package com.PrimeCare.PrimeCare.modules.appointment.service;

import com.PrimeCare.PrimeCare.modules.appointment.dto.response.FollowUpQueueItemResponse;
import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentCallLogRepository;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentSlotHoldRepository;
import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
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
import com.PrimeCare.PrimeCare.modules.triage.TriageAuditService;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentContactStatus;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentFollowUpType;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentPatientResponseStatus;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import com.PrimeCare.PrimeCare.shared.enums.FollowUpQueueCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentAdminServiceFollowUpQueueTest {

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private AppointmentSlotHoldRepository appointmentSlotHoldRepository;
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
    void noShowCategoryFiltersOnlyNoShowFollowUpsIncludingLegacyNoShow() {
        Pageable pageable = PageRequest.of(0, 10);
        when(appointmentRepository.searchFollowUpQueue(any(), eq(true), eq(true), isNull(), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.getFollowUpQueue(FollowUpQueueCategory.NO_SHOW, null, null, pageable);

        Collection<AppointmentFollowUpType> types = capturedFollowUpTypes();
        assertThat(types).containsExactly(AppointmentFollowUpType.NO_SHOW);
    }

    @Test
    void doctorCancellationCategoryIncludesBothDoctorCancellationFollowUpTypes() {
        Pageable pageable = PageRequest.of(0, 10);
        when(appointmentRepository.searchFollowUpQueue(any(), eq(true), eq(false), isNull(), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.getFollowUpQueue(FollowUpQueueCategory.DOCTOR_CANCELLATION, null, null, pageable);

        Collection<AppointmentFollowUpType> types = capturedFollowUpTypes();
        assertThat(types).containsExactlyInAnyOrder(
                AppointmentFollowUpType.DOCTOR_CANCELLATION_NO_RESPONSE,
                AppointmentFollowUpType.DOCTOR_CANCELLATION_CONTACT_REQUESTED
        );
    }

    @Test
    void contactRequestCategoryIncludesPatientAndDoctorContactRequests() {
        Pageable pageable = PageRequest.of(0, 10);
        when(appointmentRepository.searchFollowUpQueue(any(), eq(true), eq(false), isNull(), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.getFollowUpQueue(FollowUpQueueCategory.CONTACT_REQUEST, null, null, pageable);

        Collection<AppointmentFollowUpType> types = capturedFollowUpTypes();
        assertThat(types).containsExactlyInAnyOrder(
                AppointmentFollowUpType.PATIENT_CONTACT_REQUESTED,
                AppointmentFollowUpType.DOCTOR_CANCELLATION_CONTACT_REQUESTED
        );
    }

    @Test
    void searchKeywordIsTrimmedAndLowercasedForRepositorySearch() {
        Pageable pageable = PageRequest.of(0, 10);
        when(appointmentRepository.searchFollowUpQueue(any(), eq(false), eq(true), eq("APT-10"), eq("apt-10"), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.getFollowUpQueue(FollowUpQueueCategory.ALL, null, "  APT-10  ", pageable);

        verify(appointmentRepository).searchFollowUpQueue(any(), eq(false), eq(true), eq("APT-10"), eq("apt-10"), eq(pageable));
    }

    @Test
    void responseIncludesFollowUpCategoryAndContactState() {
        Pageable pageable = PageRequest.of(0, 10);
        Appointment appointment = appointment(AppointmentFollowUpType.PATIENT_CONTACT_REQUESTED);
        when(appointmentRepository.searchFollowUpQueue(any(), eq(true), eq(false), isNull(), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(appointment), pageable, 1));
        when(appointmentSlotHoldRepository.findLatestByOriginalAppointmentIds(List.of(appointment.getId())))
                .thenReturn(List.of());

        PageResponse<FollowUpQueueItemResponse> response = service.getFollowUpQueue(
                FollowUpQueueCategory.CONTACT_REQUEST,
                null,
                null,
                pageable
        );

        FollowUpQueueItemResponse item = response.getItems().get(0);
        assertThat(item.getFollowUpType()).isEqualTo("PATIENT_CONTACT_REQUESTED");
        assertThat(item.getFollowUpCategory()).isEqualTo("CONTACT_REQUEST");
        assertThat(item.getFollowUpPending()).isTrue();
        assertThat(item.getContactStatus()).isEqualTo("PHONE_UPDATED_BY_PATIENT");
        assertThat(item.getPatientResponseStatus()).isEqualTo("WAITING_STAFF_RECALL");
    }

    @SuppressWarnings("unchecked")
    private Collection<AppointmentFollowUpType> capturedFollowUpTypes() {
        ArgumentCaptor<Collection<AppointmentFollowUpType>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(appointmentRepository).searchFollowUpQueue(
                captor.capture(),
                anyBoolean(),
                anyBoolean(),
                any(),
                any(),
                any(Pageable.class)
        );
        return captor.getValue();
    }

    private Appointment appointment(AppointmentFollowUpType followUpType) {
        Branch branch = Branch.builder().id(1L).nameVn("PrimeCare").build();
        Specialty specialty = Specialty.builder().id(2L).nameVn("Nội tổng quát").build();
        DoctorProfile doctor = DoctorProfile.builder().id(3L).fullName("Dr Test").build();
        return Appointment.builder()
                .id(10L)
                .code("APT-10")
                .status(AppointmentStatus.REQUESTED)
                .branch(branch)
                .specialty(specialty)
                .doctor(doctor)
                .visitDate(LocalDate.now().plusDays(1))
                .session(BranchSessionType.AM)
                .etaStart(LocalTime.of(8, 0))
                .etaEnd(LocalTime.of(8, 30))
                .patientFullName("Nguyen Van A")
                .patientPhone("0911222333")
                .patientEmail("patient@example.test")
                .contactStatus(AppointmentContactStatus.PHONE_UPDATED_BY_PATIENT)
                .patientResponseStatus(AppointmentPatientResponseStatus.WAITING_STAFF_RECALL)
                .followUpPending(true)
                .followUpType(followUpType)
                .build();
    }
}
