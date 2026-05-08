package com.PrimeCare.PrimeCare.modules.appointment.service;

import com.PrimeCare.PrimeCare.modules.appointment.config.AppointmentNoShowProperties;
import com.PrimeCare.PrimeCare.modules.appointment.dto.request.CreateWalkInAppointmentRequest;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.AppointmentAvailabilityResponse;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.BookableSlotResponse;
import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
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
import com.PrimeCare.PrimeCare.modules.notification.service.InternalNotificationService;
import com.PrimeCare.PrimeCare.modules.patient.service.PatientService;
import com.PrimeCare.PrimeCare.modules.realtime.service.RealtimeEventPublisher;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentSourceType;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.enums.ArrivalStatus;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentReceptionServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private AppointmentAvailabilityService availabilityService;
    @Mock
    private AppointmentCodeGenerator appointmentCodeGenerator;
    @Mock
    private ReceptionQueueAllocator receptionQueueAllocator;
    @Mock
    private PatientService patientService;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private SpecialtyRepository specialtyRepository;
    @Mock
    private DoctorProfileRepository doctorProfileRepository;
    @Mock
    private BranchSpecialtyService branchSpecialtyService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DoctorWorkScheduleRepository doctorWorkScheduleRepository;
    @Mock
    private BranchSessionRepository branchSessionRepository;
    @Mock
    private DoctorOperationalGuardService doctorOperationalGuardService;
    @Mock
    private AppointmentQueueService appointmentQueueService;
    @Mock
    private RealtimeEventPublisher realtimeEventPublisher;
    @Mock
    private AppointmentStatusHistoryService appointmentStatusHistoryService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private AppointmentNoShowProperties noShowProperties;
    @Mock
    private InternalNotificationService internalNotificationService;

    @InjectMocks
    private AppointmentReceptionService service;

    @Test
    void markArrivedRejectsAppointmentsOutsideToday() {
        Appointment appointment = appointment(AppointmentStatus.CONFIRMED, LocalDate.now().plusDays(1));
        User staff = staff();
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));
        when(userRepository.findById(10L)).thenReturn(Optional.of(staff));

        assertThatThrownBy(() -> service.markArrived(1L, 10L))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("Chỉ đánh dấu đã đến cho lịch hẹn trong ngày.");
                });

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void manualCheckInRejectsAppointmentsOutsideToday() {
        Appointment appointment = appointment(AppointmentStatus.CONFIRMED, LocalDate.now().plusDays(1));
        User staff = staff();
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));
        when(userRepository.findById(10L)).thenReturn(Optional.of(staff));

        assertThatThrownBy(() -> service.manualCheckIn(1L, 10L))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("Chỉ check-in được cho lịch hẹn trong ngày.");
                });

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void createWalkInRejectsRaceWhenAnotherRequestTakesTheSingleSlot() {
        LocalDate visitDate = LocalDate.now().plusDays(1);
        LocalTime slotStart = LocalTime.of(8, 0);
        Branch branch = Branch.builder().id(1L).nameVn("PrimeCare").build();
        Specialty specialty = Specialty.builder().id(2L).nameVn("Noi tong quat").build();
        DoctorProfile doctor = DoctorProfile.builder().id(3L).fullName("Dr Test").branch(branch).build();
        CreateWalkInAppointmentRequest request = walkInRequest(visitDate, slotStart);

        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));
        when(specialtyRepository.findById(2L)).thenReturn(Optional.of(specialty));
        when(doctorProfileRepository.findByIdAndBranch_Id(3L, 1L)).thenReturn(Optional.of(doctor));
        when(doctorProfileRepository.existsDoctorSpecialty(3L, 2L)).thenReturn(true);
        when(userRepository.findById(10L)).thenReturn(Optional.of(staff()));
        when(doctorWorkScheduleRepository.findWithLockByDoctor_IdAndWorkDateAndSession(3L, visitDate, BranchSessionType.AM))
                .thenReturn(Optional.of(DoctorWorkSchedule.builder().doctor(doctor).workDate(visitDate).session(BranchSessionType.AM).build()));
        BranchSession branchSession = BranchSession.builder()
                .branch(branch)
                .session(BranchSessionType.AM)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(12, 0))
                .status("ACTIVE")
                .build();
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
        when(appointmentRepository.findByDoctor_IdAndVisitDateAndSessionAndStatusInOrderByEtaStartAsc(
                eq(3L),
                eq(visitDate),
                eq(BranchSessionType.AM),
                anyCollection()
        )).thenReturn(List.of(appointment(AppointmentStatus.REQUESTED, visitDate)));
        when(availabilityService.countOverlappingAppointments(any(), any(), any())).thenReturn(1L);

        assertThatThrownBy(() -> service.createWalkIn(request, 10L))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.APPOINTMENT_SLOT_NOT_AVAILABLE)
                );

        verify(appointmentRepository, never()).save(any());
    }

    private CreateWalkInAppointmentRequest walkInRequest(LocalDate visitDate, LocalTime slotStart) {
        CreateWalkInAppointmentRequest request = new CreateWalkInAppointmentRequest();
        request.setBranchId(1L);
        request.setSpecialtyId(2L);
        request.setDoctorId(3L);
        request.setVisitDate(visitDate);
        request.setSession(BranchSessionType.AM);
        request.setSlotStart(slotStart);
        request.setPatientFullName("Nguyen Van A");
        request.setPatientPhone("0900000000");
        request.setArrived(false);
        return request;
    }

    private Appointment appointment(AppointmentStatus status, LocalDate visitDate) {
        Branch branch = Branch.builder().id(1L).nameVn("PrimeCare").build();
        DoctorProfile doctor = DoctorProfile.builder().id(3L).fullName("Dr Test").branch(branch).build();
        return Appointment.builder()
                .id(1L)
                .code("APT-1")
                .status(status)
                .branch(branch)
                .specialty(Specialty.builder().id(2L).nameVn("Noi tong quat").build())
                .doctor(doctor)
                .visitDate(visitDate)
                .session(BranchSessionType.AM)
                .sourceType(AppointmentSourceType.PUBLIC_BOOKING)
                .arrivalStatus(ArrivalStatus.NOT_ARRIVED)
                .etaStart(LocalTime.of(8, 0))
                .etaEnd(LocalTime.of(8, 30))
                .slotMinutes(30)
                .patientFullName("Nguyen Van A")
                .patientPhone("0900000000")
                .build();
    }

    private User staff() {
        return User.builder().id(10L).role(UserRole.STAFF).email("staff@example.test").build();
    }

}
