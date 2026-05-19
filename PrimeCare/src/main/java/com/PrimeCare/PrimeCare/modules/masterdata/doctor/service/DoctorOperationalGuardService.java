package com.PrimeCare.PrimeCare.modules.masterdata.doctor.service;

import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.shared.enums.BranchStatus;
import com.PrimeCare.PrimeCare.shared.enums.DoctorStatus;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import com.PrimeCare.PrimeCare.shared.enums.UserStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DoctorOperationalGuardService {

    public static final String REASON_NO_ACCOUNT = "NO_ACCOUNT";
    public static final String REASON_ACCOUNT_INACTIVE = "ACCOUNT_INACTIVE";
    public static final String REASON_ACCOUNT_ROLE_INVALID = "ACCOUNT_ROLE_INVALID";
    public static final String REASON_PROFILE_INACTIVE = "PROFILE_INACTIVE";
    public static final String REASON_BRANCH_INACTIVE = "BRANCH_INACTIVE";
    public static final String PUBLIC_REASON_NO_ACTIVE_DOCTOR_ACCOUNT = "NO_ACTIVE_DOCTOR_ACCOUNT";
    public static final String PUBLIC_REASON_NOT_AVAILABLE = "NOT_AVAILABLE";

    public static final String NO_ACTIVE_DOCTOR_ACCOUNT_MESSAGE =
            "Doctor is not ready to receive appointments because no active doctor account exists.";
    public static final String INACTIVE_DOCTOR_ACCOUNT_MESSAGE =
            "Doctor is not ready to receive appointments because the doctor account is inactive.";
    public static final String PUBLIC_DOCTOR_NOT_AVAILABLE_MESSAGE =
            "Doctor is not available for booking.";

    private final UserRepository userRepository;

    public void assertDoctorBookable(DoctorProfile doctor) {
        DoctorReadiness readiness = getReadiness(doctor);
        if (readiness.operationalReady()) {
            return;
        }

        if (REASON_PROFILE_INACTIVE.equals(readiness.notReadyReason())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Bác sĩ đang tạm ngưng hoạt động");
        }

        if (REASON_BRANCH_INACTIVE.equals(readiness.notReadyReason())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Chi nhánh tạm ngưng hoạt động");
        }

        if (REASON_ACCOUNT_INACTIVE.equals(readiness.notReadyReason())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, INACTIVE_DOCTOR_ACCOUNT_MESSAGE);
        }

        throw new ApiException(ErrorCode.INVALID_REQUEST, NO_ACTIVE_DOCTOR_ACCOUNT_MESSAGE);
    }

    public void assertDoctorPublicBookable(DoctorProfile doctor) {
        if (!getReadiness(doctor).operationalReady()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, PUBLIC_DOCTOR_NOT_AVAILABLE_MESSAGE);
        }
    }

    public boolean isDoctorBookable(DoctorProfile doctor) {
        return getReadiness(doctor).operationalReady();
    }

    public DoctorReadiness getReadiness(DoctorProfile doctor) {
        User account = doctor != null && doctor.getId() != null
                ? userRepository.findByDoctorProfile_Id(doctor.getId()).orElse(null)
                : null;
        return evaluateReadiness(doctor, account);
    }

    public static DoctorReadiness evaluateReadiness(DoctorProfile doctor, User account) {
        if (doctor == null) {
            return new DoctorReadiness(false, REASON_PROFILE_INACTIVE);
        }

        DoctorStatus currentStatus = doctor.getStatus() != null ? doctor.getStatus() : DoctorStatus.ACTIVE;
        if (currentStatus != DoctorStatus.ACTIVE) {
            return new DoctorReadiness(false, REASON_PROFILE_INACTIVE);
        }

        if (doctor.getBranch() == null || doctor.getBranch().getStatus() != BranchStatus.ACTIVE) {
            return new DoctorReadiness(false, REASON_BRANCH_INACTIVE);
        }

        if (account == null) {
            return new DoctorReadiness(false, REASON_NO_ACCOUNT);
        }

        if (account.getStatus() != UserStatus.ACTIVE) {
            return new DoctorReadiness(false, REASON_ACCOUNT_INACTIVE);
        }

        if (account.getRole() != UserRole.DOCTOR) {
            return new DoctorReadiness(false, REASON_ACCOUNT_ROLE_INVALID);
        }

        return new DoctorReadiness(true, null);
    }

    public static String toPublicNotReadyReason(String notReadyReason) {
        if (notReadyReason == null || notReadyReason.isBlank()) {
            return PUBLIC_REASON_NOT_AVAILABLE;
        }

        return switch (notReadyReason) {
            case REASON_NO_ACCOUNT, REASON_ACCOUNT_ROLE_INVALID -> PUBLIC_REASON_NO_ACTIVE_DOCTOR_ACCOUNT;
            case REASON_ACCOUNT_INACTIVE -> REASON_ACCOUNT_INACTIVE;
            case REASON_PROFILE_INACTIVE -> REASON_PROFILE_INACTIVE;
            case REASON_BRANCH_INACTIVE -> REASON_BRANCH_INACTIVE;
            default -> PUBLIC_REASON_NOT_AVAILABLE;
        };
    }

    public record DoctorReadiness(boolean operationalReady, String notReadyReason) {
    }
}
