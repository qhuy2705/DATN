package com.PrimeCare.PrimeCare.modules.appointment.service;

import com.PrimeCare.PrimeCare.modules.appointment.config.AppointmentProperties;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.AppointmentAvailabilityResponse;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.BookableSlotResponse;
import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.doctor_leave.repository.DoctorLeaveRequestRepository;
import com.PrimeCare.PrimeCare.modules.doctor_schedule.entity.DoctorWorkSchedule;
import com.PrimeCare.PrimeCare.modules.doctor_schedule.repository.DoctorWorkScheduleRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.BranchSession;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.repository.BranchSessionRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorSpecialty;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.repository.DoctorProfileRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.service.DoctorOperationalGuardService;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.entity.BranchSpecialty;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.entity.Specialty;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.service.BranchSpecialtyService;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import com.PrimeCare.PrimeCare.shared.enums.BranchSpecialtyStatus;
import com.PrimeCare.PrimeCare.shared.enums.BranchStatus;
import com.PrimeCare.PrimeCare.shared.enums.DoctorLeaveRequestStatus;
import com.PrimeCare.PrimeCare.shared.enums.DoctorStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentAvailabilityServiceTest {

    private static final Long BRANCH_ID = 1L;
    private static final Long SPECIALTY_ID = 2L;
    private static final Long DOCTOR_ID = 3L;
    private static final BranchSessionType SESSION = BranchSessionType.AM;

    @Mock
    private DoctorProfileRepository doctorProfileRepository;
    @Mock
    private BranchSessionRepository branchSessionRepository;
    @Mock
    private DoctorWorkScheduleRepository doctorWorkScheduleRepository;
    @Mock
    private DoctorLeaveRequestRepository doctorLeaveRequestRepository;
    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private BranchSpecialtyService branchSpecialtyService;
    @Mock
    private DoctorOperationalGuardService doctorOperationalGuardService;

    @InjectMocks
    private AppointmentAvailabilityService service;

    @Test
    void capacityOverrideThirtyDoesNotIncreasePerSlotCapacity() {
        LocalDate visitDate = LocalDate.now().plusDays(1);
        stubAvailabilityContext(visitDate, List.of());

        AppointmentAvailabilityResponse response = service.getAvailability(
                BRANCH_ID,
                SPECIALTY_ID,
                DOCTOR_ID,
                visitDate,
                SESSION
        );

        BookableSlotResponse slot = slotAt(response, LocalTime.of(8, 0));
        assertThat(slot.isAvailable()).isTrue();
        assertThat(slot.getCapacity()).isEqualTo(1);
        assertThat(slot.getRemainingSlots()).isEqualTo(1);
        assertThat(slot.getBookedCount()).isZero();
    }

    @Test
    void confirmedAppointmentConsumesTheSingleDoctorSlot() {
        LocalDate visitDate = LocalDate.now().plusDays(1);
        stubAvailabilityContext(
                visitDate,
                List.of(appointment(AppointmentStatus.CONFIRMED, LocalTime.of(8, 0)))
        );

        AppointmentAvailabilityResponse response = service.getAvailability(
                BRANCH_ID,
                SPECIALTY_ID,
                DOCTOR_ID,
                visitDate,
                SESSION
        );

        BookableSlotResponse slot = slotAt(response, LocalTime.of(8, 0));
        assertThat(slot.isAvailable()).isFalse();
        assertThat(slot.getCapacity()).isEqualTo(1);
        assertThat(slot.getRemainingSlots()).isZero();
        assertThat(slot.getBookedCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"REQUESTED", "CONFIRMED", "CHECKED_IN"})
    void blockingAppointmentStatusesConsumeTheSingleDoctorSlot(AppointmentStatus status) {
        LocalDate visitDate = LocalDate.now().plusDays(1);
        stubAvailabilityContext(
                visitDate,
                List.of(appointment(status, LocalTime.of(8, 0)))
        );

        AppointmentAvailabilityResponse response = service.getAvailability(
                BRANCH_ID,
                SPECIALTY_ID,
                DOCTOR_ID,
                visitDate,
                SESSION
        );

        BookableSlotResponse slot = slotAt(response, LocalTime.of(8, 0));
        assertThat(slot.isAvailable()).isFalse();
        assertThat(slot.getCapacity()).isEqualTo(1);
        assertThat(slot.getRemainingSlots()).isZero();
        assertThat(slot.getBookedCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"CANCELLED", "NO_SHOW", "COMPLETED"})
    void terminalAppointmentStatusesDoNotConsumeDoctorSlot(AppointmentStatus status) {
        LocalDate visitDate = LocalDate.now().plusDays(1);
        stubAvailabilityContext(
                visitDate,
                List.of(appointment(status, LocalTime.of(8, 0)))
        );

        AppointmentAvailabilityResponse response = service.getAvailability(
                BRANCH_ID,
                SPECIALTY_ID,
                DOCTOR_ID,
                visitDate,
                SESSION
        );

        BookableSlotResponse slot = slotAt(response, LocalTime.of(8, 0));
        assertThat(slot.isAvailable()).isTrue();
        assertThat(slot.getCapacity()).isEqualTo(1);
        assertThat(slot.getRemainingSlots()).isEqualTo(1);
        assertThat(slot.getBookedCount()).isZero();
    }

    @Test
    void cancelledAppointmentDoesNotConsumeSlotCapacity() {
        LocalDate visitDate = LocalDate.now().plusDays(1);
        stubAvailabilityContext(
                visitDate,
                List.of(appointment(AppointmentStatus.CANCELLED, LocalTime.of(8, 0)))
        );

        AppointmentAvailabilityResponse response = service.getAvailability(
                BRANCH_ID,
                SPECIALTY_ID,
                DOCTOR_ID,
                visitDate,
                SESSION
        );

        BookableSlotResponse slot = slotAt(response, LocalTime.of(8, 0));
        assertThat(slot.isAvailable()).isTrue();
        assertThat(slot.getCapacity()).isEqualTo(1);
        assertThat(slot.getRemainingSlots()).isEqualTo(1);
        assertThat(slot.getBookedCount()).isZero();
    }

    @Test
    void appointmentPropertiesClampDefaultSlotCapacityToAtLeastOne() {
        AppointmentProperties properties = new AppointmentProperties();

        properties.setDefaultSlotCapacity(null);
        assertThat(properties.effectiveDefaultSlotCapacity()).isEqualTo(1);

        properties.setDefaultSlotCapacity(0);
        assertThat(properties.effectiveDefaultSlotCapacity()).isEqualTo(1);

        properties.setDefaultSlotCapacity(2);
        assertThat(properties.effectiveDefaultSlotCapacity()).isEqualTo(2);
    }

    private void stubAvailabilityContext(LocalDate visitDate, List<Appointment> storedAppointments) {
        Branch branch = Branch.builder()
                              .id(BRANCH_ID)
                              .nameVn("PrimeCare Q1")
                              .nameEn("PrimeCare District 1")
                              .status(BranchStatus.ACTIVE)
                              .build();
        Specialty specialty = Specialty.builder()
                                       .id(SPECIALTY_ID)
                                       .nameVn("Noi tong quat")
                                       .nameEn("Internal medicine")
                                       .status("ACTIVE")
                                       .build();
        DoctorProfile doctor = DoctorProfile.builder()
                                            .id(DOCTOR_ID)
                                            .fullName("BS. Nguyen Van A")
                                            .branch(branch)
                                            .status(DoctorStatus.ACTIVE)
                                            .build();
        doctor.getDoctorSpecialties().add(DoctorSpecialty.builder()
                                                         .doctor(doctor)
                                                         .specialty(specialty)
                                                         .build());

        BranchSpecialty branchSpecialty = BranchSpecialty.builder()
                                                         .branch(branch)
                                                         .specialty(specialty)
                                                         .status(BranchSpecialtyStatus.ACTIVE)
                                                         .build();
        BranchSession branchSession = BranchSession.builder()
                                                   .branch(branch)
                                                   .session(SESSION)
                                                   .startTime(LocalTime.of(8, 0))
                                                   .endTime(LocalTime.of(9, 0))
                                                   .capacityOverride(30)
                                                   .status("ACTIVE")
                                                   .build();
        DoctorWorkSchedule workSchedule = DoctorWorkSchedule.builder()
                                                            .doctor(doctor)
                                                            .workDate(visitDate)
                                                            .session(SESSION)
                                                            .build();

        when(branchSpecialtyService.getActiveBranchSpecialtyEntity(BRANCH_ID, SPECIALTY_ID)).thenReturn(branchSpecialty);
        when(doctorProfileRepository.findByIdAndBranch_Id(DOCTOR_ID, BRANCH_ID)).thenReturn(Optional.of(doctor));
        when(doctorProfileRepository.existsDoctorSpecialty(DOCTOR_ID, SPECIALTY_ID)).thenReturn(true);
        when(branchSessionRepository.findByBranch_IdAndSessionAndStatus(BRANCH_ID, SESSION, "ACTIVE"))
                .thenReturn(Optional.of(branchSession));
        when(doctorWorkScheduleRepository.findByDoctor_IdAndWorkDateAndSession(DOCTOR_ID, visitDate, SESSION))
                .thenReturn(Optional.of(workSchedule));
        when(doctorLeaveRequestRepository.findByDoctor_IdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                DOCTOR_ID,
                DoctorLeaveRequestStatus.APPROVED,
                visitDate,
                visitDate
        )).thenReturn(List.of());
        when(appointmentRepository.findByDoctor_IdAndVisitDateAndSessionAndStatusInOrderByEtaStartAsc(
                eq(DOCTOR_ID),
                eq(visitDate),
                eq(SESSION),
                any()
        )).thenAnswer(invocation -> {
            Collection<AppointmentStatus> statuses = invocation.getArgument(3);
            return storedAppointments.stream()
                                     .filter(appointment -> statuses.contains(appointment.getStatus()))
                                     .toList();
        });
    }

    private Appointment appointment(AppointmentStatus status, LocalTime etaStart) {
        return Appointment.builder()
                          .status(status)
                          .visitDate(LocalDate.now().plusDays(1))
                          .session(SESSION)
                          .slotMinutes(AppointmentAvailabilityService.FIXED_SLOT_MINUTES)
                          .etaStart(etaStart)
                          .etaEnd(etaStart.plusMinutes(AppointmentAvailabilityService.FIXED_SLOT_MINUTES))
                          .build();
    }

    private BookableSlotResponse slotAt(AppointmentAvailabilityResponse response, LocalTime startTime) {
        return response.getSlots().stream()
                       .filter(slot -> startTime.equals(slot.getStartTime()))
                       .findFirst()
                       .orElseThrow();
    }
}
