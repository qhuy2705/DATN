package com.PrimeCare.PrimeCare.modules.masterdata.doctor.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DoctorAdminSummaryResponse {
    private long total;
    private long active;
    private long inactive;
    private long noAccountDoctors;
    private long inactiveAccountDoctors;
    private long operationalReadyDoctors;
    private long notOperationalReadyDoctors;
}
