package com.PrimeCare.PrimeCare.modules.masterdata.doctor.dto.response;

import com.PrimeCare.PrimeCare.shared.enums.DoctorStatus;
import com.PrimeCare.PrimeCare.shared.enums.UserStatus;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DoctorOptionResponse {
    private Long id;
    private String fullName;
    private Long branchId;
    private String branchName;
    private List<Long> specialtyIds;
    private Long primarySpecialtyId;
    private List<String> specialtyNames;
    private DoctorStatus profileStatus;
    private boolean hasAccount;
    private UserStatus accountStatus;
    private boolean operationalReady;
    private boolean bookable;
    private String notReadyReason;
}
