package com.PrimeCare.PrimeCare.modules.auth.service;

import com.PrimeCare.PrimeCare.modules.auth.dto.request.ProvisionAccountRequest;
import com.PrimeCare.PrimeCare.modules.auth.dto.response.CredentialSetupDeliveryResponse;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.repository.DoctorProfileRepository;
import com.PrimeCare.PrimeCare.modules.staff.entity.StaffProfile;
import com.PrimeCare.PrimeCare.modules.staff.repository.StaffProfileRepository;
import com.PrimeCare.PrimeCare.shared.enums.CredentialSetupTokenPurpose;
import com.PrimeCare.PrimeCare.shared.enums.StaffStatus;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import com.PrimeCare.PrimeCare.shared.enums.UserStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountProvisionServiceTest {

    private static final Long STAFF_PROFILE_ID = 10L;
    private static final String EMAIL = "staff@example.test";

    @Mock
    private UserRepository userRepository;
    @Mock
    private DoctorProfileRepository doctorProfileRepository;
    @Mock
    private StaffProfileRepository staffProfileRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private CredentialSetupService credentialSetupService;

    @InjectMocks
    private AccountProvisionService service;

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"STAFF", "CASHIER", "SERVICE_TECHNICIAN", "PHARMACIST"})
    void provisionStaffAccountAllowsOperationalStaffRoles(UserRole role) {
        StaffProfile profile = stubProvisionableStaffProfile();

        var response = service.provisionStaffAccount(STAFF_PROFILE_ID, request(role));

        assertThat(response.getRole()).isEqualTo(role.name());
        assertThat(response.getStatus()).isEqualTo(UserStatus.PENDING_ACTIVATION.name());
        assertThat(response.getStaffProfileId()).isEqualTo(STAFF_PROFILE_ID);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getRole()).isEqualTo(role);
        assertThat(savedUser.getStatus()).isEqualTo(UserStatus.PENDING_ACTIVATION);
        assertThat(savedUser.getStaffProfile()).isSameAs(profile);
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"SYSTEM_ADMIN", "OPERATIONS_ADMIN", "DOCTOR", "PATIENT"})
    void provisionStaffAccountRejectsNonStaffAccountRoles(UserRole role) {
        stubStaffProfileLookup();
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);

        assertThatThrownBy(() -> service.provisionStaffAccount(STAFF_PROFILE_ID, request(role)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("Vai trò tài khoản nhân sự không hợp lệ");
                });

        verify(userRepository, never()).save(any(User.class));
        verify(credentialSetupService, never()).createAndDispatch(any(), any(), any());
    }

    @Test
    void provisionStaffAccountDefaultsNullRoleToStaff() {
        stubProvisionableStaffProfile();

        var response = service.provisionStaffAccount(STAFF_PROFILE_ID, request(null));

        assertThat(response.getRole()).isEqualTo(UserRole.STAFF.name());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getRole()).isEqualTo(UserRole.STAFF);
    }

    private StaffProfile stubProvisionableStaffProfile() {
        StaffProfile profile = stubStaffProfileLookup();
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");
        when(credentialSetupService.createAndDispatch(
                any(User.class),
                eq(CredentialSetupTokenPurpose.ACCOUNT_SETUP),
                isNull()
        )).thenReturn(CredentialSetupDeliveryResponse.builder()
                .deliveryChannel("EMAIL")
                .maskedDestination("s***f@example.test")
                .delivered(true)
                .setupUrl("https://primecare.test/setup")
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build());
        return profile;
    }

    private StaffProfile stubStaffProfileLookup() {
        StaffProfile profile = StaffProfile.builder()
                .id(STAFF_PROFILE_ID)
                .fullName("Nguyen Van A")
                .status(StaffStatus.ACTIVE)
                .build();
        when(staffProfileRepository.findById(STAFF_PROFILE_ID)).thenReturn(Optional.of(profile));
        when(userRepository.existsByStaffProfile_Id(STAFF_PROFILE_ID)).thenReturn(false);
        return profile;
    }

    private ProvisionAccountRequest request(UserRole role) {
        ProvisionAccountRequest request = new ProvisionAccountRequest();
        request.setEmail(EMAIL);
        request.setRole(role);
        return request;
    }
}
