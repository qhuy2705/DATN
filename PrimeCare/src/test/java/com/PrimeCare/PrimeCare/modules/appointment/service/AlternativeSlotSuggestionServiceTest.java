package com.PrimeCare.PrimeCare.modules.appointment.service;

import com.PrimeCare.PrimeCare.modules.appointment.dto.response.AppointmentAvailabilityResponse;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.BookableSlotResponse;
import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.repository.DoctorProfileRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.entity.Specialty;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlternativeSlotSuggestionServiceTest {

    @Mock
    private AppointmentAvailabilityService availabilityService;
    @Mock
    private DoctorProfileRepository doctorProfileRepository;

    @InjectMocks
    private AlternativeSlotSuggestionService service;

    @Test
    void morningCancellationPrefersSameDoctorAfternoonSameDay() {
        LocalDate date = LocalDate.now().plusDays(1);
        Appointment original = appointment(10L, date, BranchSessionType.AM);
        DoctorProfile doctor = original.getDoctor();
        DoctorProfile otherDoctor = DoctorProfile.builder().id(11L).fullName("Dr Other").build();

        when(doctorProfileRepository.findActiveByBranchAndSpecialty(1L, 2L)).thenReturn(List.of(doctor, otherDoctor));
        when(availabilityService.getAvailabilityExcludingAppointment(
                eq(1L), eq(2L), eq(10L), eq(date), eq(BranchSessionType.PM), anyBoolean(), eq(99L)
        )).thenReturn(availability(LocalTime.of(13, 30)));
        when(doctorProfileRepository.findById(10L)).thenReturn(Optional.of(doctor));

        Optional<AlternativeSlotSuggestion> suggestion = service.suggest(original);

        assertThat(suggestion).isPresent();
        assertThat(suggestion.get().doctor().getId()).isEqualTo(10L);
        assertThat(suggestion.get().visitDate()).isEqualTo(date);
        assertThat(suggestion.get().session()).isEqualTo(BranchSessionType.PM);
        assertThat(suggestion.get().slotStart()).isEqualTo(LocalTime.of(13, 30));
    }

    @Test
    void fullDayLeavePrefersOtherDoctorSameDay() {
        LocalDate date = LocalDate.now().plusDays(1);
        Appointment original = appointment(10L, date, BranchSessionType.AM);
        DoctorProfile otherDoctor = DoctorProfile.builder().id(11L).fullName("Dr Other").build();

        when(doctorProfileRepository.findActiveByBranchAndSpecialty(1L, 2L)).thenReturn(List.of(original.getDoctor(), otherDoctor));
        when(availabilityService.getAvailabilityExcludingAppointment(
                eq(1L), eq(2L), eq(11L), eq(date), eq(BranchSessionType.AM), anyBoolean(), eq(99L)
        )).thenReturn(availability(LocalTime.of(9, 0)));
        when(doctorProfileRepository.findById(11L)).thenReturn(Optional.of(otherDoctor));

        Optional<AlternativeSlotSuggestion> suggestion = service.suggestForFullDayLeave(original);

        assertThat(suggestion).isPresent();
        assertThat(suggestion.get().doctor().getId()).isEqualTo(11L);
        assertThat(suggestion.get().visitDate()).isEqualTo(date);
        assertThat(suggestion.get().session()).isEqualTo(BranchSessionType.AM);
    }

    private Appointment appointment(Long doctorId, LocalDate date, BranchSessionType session) {
        Branch branch = Branch.builder().id(1L).nameVn("PrimeCare").build();
        Specialty specialty = Specialty.builder().id(2L).nameVn("Nội tổng quát").build();
        DoctorProfile doctor = DoctorProfile.builder().id(doctorId).fullName("Dr Same").branch(branch).build();
        return Appointment.builder()
                .id(99L)
                .code("APT-99")
                .status(AppointmentStatus.CONFIRMED)
                .branch(branch)
                .specialty(specialty)
                .doctor(doctor)
                .visitDate(date)
                .session(session)
                .etaStart(LocalTime.of(8, 0))
                .etaEnd(LocalTime.of(8, 30))
                .patientFullName("Patient")
                .patientPhone("0900000000")
                .build();
    }

    private AppointmentAvailabilityResponse availability(LocalTime start) {
        return AppointmentAvailabilityResponse.builder()
                .slots(List.of(BookableSlotResponse.builder()
                        .startTime(start)
                        .endTime(start.plusMinutes(30))
                        .available(true)
                        .remainingSlots(1)
                        .capacity(1)
                        .build()))
                .build();
    }
}
