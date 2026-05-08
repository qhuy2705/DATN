package com.PrimeCare.PrimeCare.modules.auth.service;

import com.PrimeCare.PrimeCare.modules.auth.dto.request.ProvisionAccountRequest;
import com.PrimeCare.PrimeCare.modules.auth.dto.response.CreateUserResponse;
import com.PrimeCare.PrimeCare.modules.auth.dto.response.CredentialSetupDeliveryResponse;
import com.PrimeCare.PrimeCare.modules.auth.dto.response.ResetPasswordResponse;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.mapper.UserMapper;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.repository.DoctorProfileRepository;
import com.PrimeCare.PrimeCare.modules.staff.entity.StaffProfile;
import com.PrimeCare.PrimeCare.modules.staff.repository.StaffProfileRepository;
import com.PrimeCare.PrimeCare.shared.enums.CredentialSetupTokenPurpose;
import com.PrimeCare.PrimeCare.shared.enums.DoctorStatus;
import com.PrimeCare.PrimeCare.shared.enums.StaffStatus;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import com.PrimeCare.PrimeCare.shared.enums.UserStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import com.PrimeCare.PrimeCare.shared.utils.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountProvisionService {

    private static final java.util.Set<UserRole> ALLOWED_STAFF_ACCOUNT_ROLES = java.util.EnumSet.of(
            UserRole.STAFF,
            UserRole.CASHIER,
            UserRole.SERVICE_TECHNICIAN
    );

    private final UserRepository userRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final CredentialSetupService credentialSetupService;

    @Transactional
    public CreateUserResponse provisionDoctorAccount(Long doctorProfileId, ProvisionAccountRequest req) {
        DoctorProfile profile = doctorProfileRepository.findById(doctorProfileId)
                .orElseThrow(() -> new ApiException(ErrorCode.DOCTOR_PROFILE_NOT_FOUND));

        if (userRepository.existsByDoctorProfile_Id(doctorProfileId)) {
            throw new ApiException(ErrorCode.PROFILE_ALREADY_HAS_ACCOUNT);
        }

        String email = StringUtil.trimToNull(req.getEmail());
        String phone = StringUtil.trimToNull(req.getPhone());

        if (StringUtil.isBlank(email) && StringUtil.isBlank(phone)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Email hoặc phone là bắt buộc");
        }

        if (!StringUtil.isBlank(email) && userRepository.existsByEmail(email)) {
            throw new ApiException(ErrorCode.AUTH_EMAIL_EXISTS);
        }
        if (!StringUtil.isBlank(phone) && userRepository.existsByPhone(phone)) {
            throw new ApiException(ErrorCode.AUTH_PHONE_EXISTS);
        }

        User user = User.builder()
                .email(email)
                .phone(phone)
                .passwordHash(passwordEncoder.encode(java.util.UUID.randomUUID().toString()))
                .role(UserRole.DOCTOR)
                .status(resolveNewAccountStatus(profile.getStatus() == DoctorStatus.ACTIVE))
                .doctorProfile(profile)
                .build();

        userRepository.save(user);
        CredentialSetupDeliveryResponse delivery = credentialSetupService.createAndDispatch(
                user,
                CredentialSetupTokenPurpose.ACCOUNT_SETUP,
                null
        );
        return enrichProvisionedResponse(UserMapper.toResponse(user), delivery);
    }

    @Transactional
    public ResetPasswordResponse resetDoctorAccountPassword(Long doctorProfileId) {
        User user = userRepository.findByDoctorProfile_Id(doctorProfileId)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS, "Bác sĩ chưa có tài khoản để reset mật khẩu"));

        CredentialSetupDeliveryResponse delivery = credentialSetupService.createAndDispatch(
                user,
                CredentialSetupTokenPurpose.PASSWORD_RESET,
                null
        );

        return ResetPasswordResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .phone(user.getPhone())
                .status(user.getStatus().name())
                .deliveryChannel(delivery.getDeliveryChannel())
                .deliveryTarget(delivery.getMaskedDestination())
                .deliverySent(delivery.isDelivered())
                .setupUrl(delivery.getSetupUrl())
                .expiresAt(delivery.getExpiresAt())
                .build();
    }

    @Transactional
    public void syncDoctorAccountStatus(Long doctorProfileId, DoctorStatus doctorStatus) {
        userRepository.findByDoctorProfile_Id(doctorProfileId).ifPresent(user -> {
            if (doctorStatus == DoctorStatus.INACTIVE) {
                user.setStatus(UserStatus.INACTIVE);
            } else if (user.getStatus() != UserStatus.PENDING_ACTIVATION) {
                user.setStatus(UserStatus.ACTIVE);
            }
            userRepository.save(user);
        });
    }

    @Transactional
    public ResetPasswordResponse resetStaffAccountPassword(Long staffProfileId) {
        User user = userRepository.findByStaffProfile_Id(staffProfileId)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS, "Nhân sự chưa có tài khoản để reset mật khẩu"));

        CredentialSetupDeliveryResponse delivery = credentialSetupService.createAndDispatch(
                user,
                CredentialSetupTokenPurpose.PASSWORD_RESET,
                null
        );

        return ResetPasswordResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .phone(user.getPhone())
                .status(user.getStatus().name())
                .deliveryChannel(delivery.getDeliveryChannel())
                .deliveryTarget(delivery.getMaskedDestination())
                .deliverySent(delivery.isDelivered())
                .setupUrl(delivery.getSetupUrl())
                .expiresAt(delivery.getExpiresAt())
                .build();
    }

    @Transactional
    public void syncStaffAccountStatus(Long staffProfileId, StaffStatus staffStatus) {
        userRepository.findByStaffProfile_Id(staffProfileId).ifPresent(user -> {
            if (staffStatus == StaffStatus.INACTIVE) {
                user.setStatus(UserStatus.INACTIVE);
            } else if (user.getStatus() != UserStatus.PENDING_ACTIVATION) {
                user.setStatus(UserStatus.ACTIVE);
            }
            userRepository.save(user);
        });
    }

    @Transactional
    public CreateUserResponse provisionStaffAccount(Long staffProfileId, ProvisionAccountRequest req) {
        StaffProfile profile = staffProfileRepository.findById(staffProfileId)
                .orElseThrow(() -> new ApiException(ErrorCode.STAFF_PROFILE_NOT_FOUND));

        if (userRepository.existsByStaffProfile_Id(staffProfileId)) {
            throw new ApiException(ErrorCode.PROFILE_ALREADY_HAS_ACCOUNT);
        }

        String email = StringUtil.trimToNull(req.getEmail());
        String phone = StringUtil.trimToNull(req.getPhone());

        if (StringUtil.isBlank(email) && StringUtil.isBlank(phone)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Email hoặc phone là bắt buộc");
        }
        if (!StringUtil.isBlank(email) && userRepository.existsByEmail(email)) {
            throw new ApiException(ErrorCode.AUTH_EMAIL_EXISTS);
        }
        if (!StringUtil.isBlank(phone) && userRepository.existsByPhone(phone)) {
            throw new ApiException(ErrorCode.AUTH_PHONE_EXISTS);
        }

        UserRole role = resolveStaffAccountRole(req.getRole());

        User user = User.builder()
                .email(email)
                .phone(phone)
                .passwordHash(passwordEncoder.encode(java.util.UUID.randomUUID().toString()))
                .role(role)
                .status(resolveNewAccountStatus(profile.getStatus() == StaffStatus.ACTIVE))
                .staffProfile(profile)
                .build();

        userRepository.save(user);
        CredentialSetupDeliveryResponse delivery = credentialSetupService.createAndDispatch(
                user,
                CredentialSetupTokenPurpose.ACCOUNT_SETUP,
                null
        );
        return enrichProvisionedResponse(UserMapper.toResponse(user), delivery);
    }

    private UserStatus resolveNewAccountStatus(boolean profileActive) {
        return profileActive ? UserStatus.PENDING_ACTIVATION : UserStatus.INACTIVE;
    }

    private CreateUserResponse enrichProvisionedResponse(CreateUserResponse response,
                                                         CredentialSetupDeliveryResponse delivery) {
        response.setSetupRequired(true);
        response.setDeliveryChannel(delivery.getDeliveryChannel());
        response.setDeliveryTarget(delivery.getMaskedDestination());
        response.setDeliverySent(delivery.isDelivered());
        response.setSetupUrl(delivery.getSetupUrl());
        response.setSetupExpiresAt(delivery.getExpiresAt());
        return response;
    }

    private UserRole resolveStaffAccountRole(UserRole requestedRole) {
        if (requestedRole == null) {
            return UserRole.STAFF;
        }
        if (!ALLOWED_STAFF_ACCOUNT_ROLES.contains(requestedRole)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Vai trò tài khoản nhân sự không hợp lệ");
        }
        return requestedRole;
    }
}
