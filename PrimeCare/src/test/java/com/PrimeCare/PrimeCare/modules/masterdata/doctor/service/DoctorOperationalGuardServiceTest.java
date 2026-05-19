package com.PrimeCare.PrimeCare.modules.masterdata.doctor.service;

import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.shared.enums.BranchStatus;
import com.PrimeCare.PrimeCare.shared.enums.DoctorStatus;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import com.PrimeCare.PrimeCare.shared.enums.UserStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoctorOperationalGuardServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DoctorOperationalGuardService service;

    @Test
    void assertDoctorBookableAllowsActiveProfileWithActiveDoctorAccount() {
        DoctorProfile doctor = activeDoctor();
        when(userRepository.findByDoctorProfile_Id(3L)).thenReturn(Optional.of(account(UserRole.DOCTOR, UserStatus.ACTIVE)));

        assertThatCode(() -> service.assertDoctorBookable(doctor)).doesNotThrowAnyException();
        assertThat(service.getReadiness(doctor).operationalReady()).isTrue();
    }

    @Test
    void assertDoctorBookableRejectsDoctorWithoutAccount() {
        DoctorProfile doctor = activeDoctor();
        when(userRepository.findByDoctorProfile_Id(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assertDoctorBookable(doctor))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo(DoctorOperationalGuardService.NO_ACTIVE_DOCTOR_ACCOUNT_MESSAGE);
                });
    }

    @Test
    void assertDoctorBookableRejectsInactiveDoctorAccount() {
        DoctorProfile doctor = activeDoctor();
        when(userRepository.findByDoctorProfile_Id(3L)).thenReturn(Optional.of(account(UserRole.DOCTOR, UserStatus.INACTIVE)));

        assertThatThrownBy(() -> service.assertDoctorBookable(doctor))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo(DoctorOperationalGuardService.INACTIVE_DOCTOR_ACCOUNT_MESSAGE);
                });
    }

    @Test
    void assertDoctorBookableRejectsNonDoctorLinkedAccount() {
        DoctorProfile doctor = activeDoctor();
        when(userRepository.findByDoctorProfile_Id(3L)).thenReturn(Optional.of(account(UserRole.STAFF, UserStatus.ACTIVE)));

        assertThatThrownBy(() -> service.assertDoctorBookable(doctor))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo(DoctorOperationalGuardService.NO_ACTIVE_DOCTOR_ACCOUNT_MESSAGE);
                });
    }

    @Test
    void assertDoctorPublicBookableUsesGenericPublicMessage() {
        DoctorProfile doctor = activeDoctor();
        when(userRepository.findByDoctorProfile_Id(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assertDoctorPublicBookable(doctor))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo(DoctorOperationalGuardService.PUBLIC_DOCTOR_NOT_AVAILABLE_MESSAGE);
                });
    }

    @Test
    void publicNotReadyReasonDoesNotExposeInvalidAccountRole() {
        assertThat(DoctorOperationalGuardService.toPublicNotReadyReason(DoctorOperationalGuardService.REASON_ACCOUNT_ROLE_INVALID))
                .isEqualTo(DoctorOperationalGuardService.PUBLIC_REASON_NO_ACTIVE_DOCTOR_ACCOUNT);
    }

    private DoctorProfile activeDoctor() {
        return DoctorProfile.builder()
                .id(3L)
                .fullName("Dr Test")
                .branch(Branch.builder()
                        .id(1L)
                        .nameVn("PrimeCare")
                        .status(BranchStatus.ACTIVE)
                        .build())
                .status(DoctorStatus.ACTIVE)
                .build();
    }

    private User account(UserRole role, UserStatus status) {
        return User.builder()
                .id(10L)
                .role(role)
                .status(status)
                .build();
    }
}
