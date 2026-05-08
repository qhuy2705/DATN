package com.PrimeCare.PrimeCare.modules.masterdata.doctor.service;

import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.shared.enums.BranchStatus;
import com.PrimeCare.PrimeCare.shared.enums.DoctorStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import org.springframework.stereotype.Service;

@Service
public class DoctorOperationalGuardService {

    public void assertDoctorBookable(DoctorProfile doctor) {
        DoctorStatus currentStatus = doctor.getStatus() != null ? doctor.getStatus() : DoctorStatus.ACTIVE;
        if (currentStatus != DoctorStatus.ACTIVE) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Bác sĩ đang tạm ngưng hoạt động");
        }

        if (doctor.getBranch() == null || doctor.getBranch().getStatus() != BranchStatus.ACTIVE) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Chi nhánh tạm ngưng hoạt động");
        }
    }
}