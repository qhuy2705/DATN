package com.PrimeCare.PrimeCare.modules.appointment.service;

import com.PrimeCare.PrimeCare.modules.appointment.dto.query.AppointmentStatusCountRow;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.encounter.repository.EncounterRepository;
import com.PrimeCare.PrimeCare.modules.encounter.service.EncounterWorkflowService;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoctorAppointmentServiceSummaryTest {

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EncounterRepository encounterRepository;
    @Mock
    private EncounterWorkflowService encounterWorkflowService;

    @InjectMocks
    private DoctorAppointmentService service;

    @Test
    void summaryCountsOnlyCurrentDoctorsAppointmentsWithoutFetchingFullList() {
        LocalDate today = LocalDate.now();
        User doctorUser = User.builder()
                .id(10L)
                .role(UserRole.DOCTOR)
                .doctorProfile(DoctorProfile.builder().id(3L).fullName("Dr Test").build())
                .build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(doctorUser));
        when(appointmentRepository.countDoctorSummaryByStatus(eq(3L), eq(today), eq(today), anyCollection()))
                .thenReturn(List.of(
                        row(AppointmentStatus.CONFIRMED, 2),
                        row(AppointmentStatus.CHECKED_IN, 1),
                        row(AppointmentStatus.COMPLETED, 3),
                        row(AppointmentStatus.NO_SHOW, 1)
                ));

        var response = service.myAppointmentSummary(10L, today, null, null);

        assertThat(response.getConfirmed()).isEqualTo(2);
        assertThat(response.getCheckedIn()).isEqualTo(1);
        assertThat(response.getWaitingExam()).isEqualTo(1);
        assertThat(response.getCompletedToday()).isEqualTo(3);
        assertThat(response.getNoShow()).isEqualTo(1);
        assertThat(response.getTotalToday()).isEqualTo(7);
        verify(appointmentRepository).countDoctorSummaryByStatus(eq(3L), eq(today), eq(today), anyCollection());
    }

    private AppointmentStatusCountRow row(AppointmentStatus status, long count) {
        return new AppointmentStatusCountRow() {
            @Override
            public AppointmentStatus getStatus() {
                return status;
            }

            @Override
            public long getCount() {
                return count;
            }
        };
    }
}
