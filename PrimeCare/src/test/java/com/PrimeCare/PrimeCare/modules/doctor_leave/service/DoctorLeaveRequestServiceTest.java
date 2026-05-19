package com.PrimeCare.PrimeCare.modules.doctor_leave.service;

import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.doctor_leave.dto.request.ReviewDoctorLeaveRequest;
import com.PrimeCare.PrimeCare.modules.doctor_leave.entity.DoctorLeaveRequest;
import com.PrimeCare.PrimeCare.modules.doctor_leave.repository.DoctorLeaveRequestRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import com.PrimeCare.PrimeCare.shared.enums.DoctorLeaveRequestStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoctorLeaveRequestServiceTest {

    @Mock
    private DoctorLeaveRequestRepository doctorLeaveRequestRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AppointmentRepository appointmentRepository;

    @InjectMocks
    private DoctorLeaveRequestService service;

    @Test
    void shouldRejectApproveWhenCheckedInAppointmentConflictsWithLeave() {
        LocalDate leaveDate = LocalDate.now().plusDays(1);
        DoctorProfile doctor = DoctorProfile.builder().id(3L).fullName("Dr Test").build();
        DoctorLeaveRequest leave = DoctorLeaveRequest.builder()
                .id(1L)
                .doctor(doctor)
                .startDate(leaveDate)
                .endDate(leaveDate)
                .startSession(BranchSessionType.AM)
                .endSession(BranchSessionType.PM)
                .status(DoctorLeaveRequestStatus.PENDING)
                .build();
        Appointment checkedInAppointment = Appointment.builder()
                .id(2L)
                .doctor(doctor)
                .visitDate(leaveDate)
                .session(BranchSessionType.AM)
                .etaStart(LocalTime.of(8, 0))
                .status(AppointmentStatus.CHECKED_IN)
                .build();

        when(userRepository.findById(99L)).thenReturn(Optional.of(User.builder().id(99L).build()));
        when(doctorLeaveRequestRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(appointmentRepository.findByDoctor_IdAndVisitDateBetweenAndStatusIn(
                eq(3L),
                eq(leaveDate),
                eq(leaveDate),
                ArgumentMatchers.<List<AppointmentStatus>>argThat(statuses -> statuses.contains(AppointmentStatus.CHECKED_IN))
        )).thenReturn(List.of(checkedInAppointment));

        ReviewDoctorLeaveRequest request = new ReviewDoctorLeaveRequest();
        request.setReviewNote("OK");

        assertThatThrownBy(() -> service.approve(1L, request, 99L))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DOCTOR_LEAVE_HAS_APPOINTMENT_CONFLICT)
                );

        verify(doctorLeaveRequestRepository, never()).save(leave);
    }
}
