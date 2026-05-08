package com.PrimeCare.PrimeCare.modules.staff.dto.response;

import com.PrimeCare.PrimeCare.shared.enums.StaffStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StaffProfileResponse {
    private Long id;
    private String fullName;
    private Long branchId;
    private String branchNameVn;
    private String branchNameEn;
    private StaffStatus status;
    private boolean hasAccount;
    private Long accountId;
    private String accountEmail;
    private String accountPhone;
    private String accountRole;
    private String accountStatus;
}
