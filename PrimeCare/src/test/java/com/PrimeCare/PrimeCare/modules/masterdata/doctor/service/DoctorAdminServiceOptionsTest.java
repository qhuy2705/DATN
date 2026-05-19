package com.PrimeCare.PrimeCare.modules.masterdata.doctor.service;

import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.auth.service.AccountProvisionService;
import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.repository.BranchRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.dto.request.DoctorOptionMode;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorSpecialty;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.repository.DoctorProfileRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.entity.Specialty;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.repository.SpecialtyRepository;
import com.PrimeCare.PrimeCare.shared.enums.BranchStatus;
import com.PrimeCare.PrimeCare.shared.enums.DoctorStatus;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import com.PrimeCare.PrimeCare.shared.enums.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoctorAdminServiceOptionsTest {

    @Mock
    private DoctorProfileRepository doctorProfileRepository;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private SpecialtyRepository specialtyRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AccountProvisionService accountProvisionService;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private DoctorAdminService service;

    @Test
    void operationalModeExcludesDoctorWithoutActiveDoctorAccount() {
        DoctorProfile ready = doctor(1L, "Dr Ready", DoctorStatus.ACTIVE, BranchStatus.ACTIVE);
        DoctorProfile noAccount = doctor(2L, "Dr No Account", DoctorStatus.ACTIVE, BranchStatus.ACTIVE);
        when(doctorProfileRepository.findOptions(null, null, null, null)).thenReturn(List.of(ready, noAccount));
        when(userRepository.findByDoctorProfile_IdIn(List.of(1L, 2L)))
                .thenReturn(List.of(account(ready, UserRole.DOCTOR, UserStatus.ACTIVE)));

        var response = service.options(null, null, null, null, null, DoctorOptionMode.OPERATIONAL);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().getId()).isEqualTo(1L);
        assertThat(response.getFirst().isOperationalReady()).isTrue();
    }

    @Test
    void adminModeCanIncludeDoctorWithoutAccount() {
        DoctorProfile noAccount = doctor(2L, "Dr No Account", DoctorStatus.ACTIVE, BranchStatus.ACTIVE);
        when(doctorProfileRepository.findOptions(null, null, null, null)).thenReturn(List.of(noAccount));
        when(userRepository.findByDoctorProfile_IdIn(List.of(2L))).thenReturn(List.of());

        var response = service.options(null, null, null, null, null, DoctorOptionMode.ADMIN);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().isOperationalReady()).isFalse();
        assertThat(response.getFirst().isBookable()).isFalse();
        assertThat(response.getFirst().isHasAccount()).isFalse();
        assertThat(response.getFirst().getNotReadyReason())
                .isEqualTo(DoctorOperationalGuardService.PUBLIC_REASON_NO_ACTIVE_DOCTOR_ACCOUNT);
    }

    @Test
    void adminModeCanFilterForNotOperationalReadyDoctors() {
        DoctorProfile ready = doctor(1L, "Dr Ready", DoctorStatus.ACTIVE, BranchStatus.ACTIVE);
        DoctorProfile noAccount = doctor(2L, "Dr No Account", DoctorStatus.ACTIVE, BranchStatus.ACTIVE);
        when(doctorProfileRepository.findOptions(null, null, null, null)).thenReturn(List.of(ready, noAccount));
        when(userRepository.findByDoctorProfile_IdIn(List.of(1L, 2L)))
                .thenReturn(List.of(account(ready, UserRole.DOCTOR, UserStatus.ACTIVE)));

        var response = service.options(null, null, null, false, null, DoctorOptionMode.ADMIN);

        assertThat(response).extracting("id").containsExactly(2L);
    }

    @Test
    void optionsDelegatesBranchSpecialtyStatusAndKeywordFilters() {
        DoctorProfile doctor = doctor(3L, "Dr Filtered", DoctorStatus.ACTIVE, BranchStatus.ACTIVE);
        when(doctorProfileRepository.findOptions(10L, 20L, "cardio", DoctorStatus.ACTIVE)).thenReturn(List.of(doctor));
        when(userRepository.findByDoctorProfile_IdIn(List.of(3L)))
                .thenReturn(List.of(account(doctor, UserRole.DOCTOR, UserStatus.ACTIVE)));

        var response = service.options(10L, 20L, DoctorStatus.ACTIVE, null, " cardio ", DoctorOptionMode.BOOKABLE);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().getBranchId()).isEqualTo(10L);
        assertThat(response.getFirst().getPrimarySpecialtyId()).isEqualTo(20L);
        assertThat(response.getFirst().getSpecialtyNames()).containsExactly("Cardiology");
        verify(doctorProfileRepository).findOptions(10L, 20L, "cardio", DoctorStatus.ACTIVE);
    }

    private DoctorProfile doctor(Long id, String fullName, DoctorStatus status, BranchStatus branchStatus) {
        Branch branch = Branch.builder()
                .id(id == 3L ? 10L : 1L)
                .nameVn("PrimeCare")
                .status(branchStatus)
                .build();
        Specialty specialty = Specialty.builder()
                .id(id == 3L ? 20L : 2L)
                .nameVn(id == 3L ? "Cardiology" : "General")
                .status("ACTIVE")
                .build();
        DoctorProfile doctor = DoctorProfile.builder()
                .id(id)
                .fullName(fullName)
                .branch(branch)
                .status(status)
                .build();
        doctor.getDoctorSpecialties().add(DoctorSpecialty.builder()
                .doctor(doctor)
                .specialty(specialty)
                .build());
        return doctor;
    }

    private User account(DoctorProfile doctor, UserRole role, UserStatus status) {
        return User.builder()
                .id(100L + doctor.getId())
                .doctorProfile(doctor)
                .role(role)
                .status(status)
                .build();
    }
}
