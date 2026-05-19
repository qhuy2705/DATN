package com.PrimeCare.PrimeCare.modules.doctor_schedule.service;

import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.doctor_leave.service.DoctorLeaveRequestService;
import com.PrimeCare.PrimeCare.modules.doctor_schedule.dto.request.UpsertDoctorWorkScheduleRequest;
import com.PrimeCare.PrimeCare.modules.doctor_schedule.entity.DoctorWorkSchedule;
import com.PrimeCare.PrimeCare.modules.doctor_schedule.repository.DoctorWorkScheduleRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.repository.BranchSessionRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.repository.DoctorProfileRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.service.DoctorOperationalGuardService;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import com.PrimeCare.PrimeCare.shared.enums.BranchStatus;
import com.PrimeCare.PrimeCare.shared.enums.DoctorStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoctorWorkScheduleServiceTest {

    @Mock
    private DoctorWorkScheduleRepository doctorWorkScheduleRepository;
    @Mock
    private DoctorProfileRepository doctorProfileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DoctorLeaveRequestService doctorLeaveRequestService;
    @Mock
    private BranchSessionRepository branchSessionRepository;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private DoctorOperationalGuardService doctorOperationalGuardService;

    @InjectMocks
    private DoctorWorkScheduleService service;

    @Test
    void getDoctorOptionsUsesBookableDoctorSearch() {
        DoctorProfile doctor = doctor(3L);
        when(doctorProfileRepository.search(
                isNull(),
                isNull(),
                isNull(),
                eq(DoctorStatus.ACTIVE),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(doctor)));

        var options = service.getDoctorOptions();

        assertThat(options).hasSize(1);
        assertThat(options.getFirst().getId()).isEqualTo(3L);
        verify(doctorProfileRepository).search(
                isNull(),
                isNull(),
                isNull(),
                eq(DoctorStatus.ACTIVE),
                any(Pageable.class)
        );
    }

    @Test
    void upsertRejectsDoctorWithoutActiveDoctorAccount() {
        DoctorProfile doctor = doctor(3L);
        UpsertDoctorWorkScheduleRequest request = new UpsertDoctorWorkScheduleRequest();
        request.setDoctorId(3L);
        request.setWorkDate(LocalDate.now().plusDays(1));
        request.setSession(BranchSessionType.AM);
        when(doctorProfileRepository.findById(3L)).thenReturn(Optional.of(doctor));
        doThrow(new ApiException(
                ErrorCode.INVALID_REQUEST,
                DoctorOperationalGuardService.NO_ACTIVE_DOCTOR_ACCOUNT_MESSAGE
        )).when(doctorOperationalGuardService).assertDoctorBookable(doctor);

        assertThatThrownBy(() -> service.upsert(request))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getMessage()).isEqualTo(DoctorOperationalGuardService.NO_ACTIVE_DOCTOR_ACCOUNT_MESSAGE)
                );

        verify(doctorWorkScheduleRepository, never()).save(any(DoctorWorkSchedule.class));
    }

    private DoctorProfile doctor(Long id) {
        return DoctorProfile.builder()
                .id(id)
                .fullName("Dr Test")
                .branch(Branch.builder()
                        .id(1L)
                        .nameVn("PrimeCare")
                        .status(BranchStatus.ACTIVE)
                        .build())
                .status(DoctorStatus.ACTIVE)
                .build();
    }
}
