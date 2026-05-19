package com.PrimeCare.PrimeCare.modules.staff.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StaffAdminSummaryResponse {
    private long total;
    private long active;
    private long inactive;
    private long noAccountStaffs;
    private long inactiveAccountStaffs;
}
