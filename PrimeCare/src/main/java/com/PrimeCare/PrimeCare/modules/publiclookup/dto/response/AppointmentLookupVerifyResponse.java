package com.PrimeCare.PrimeCare.modules.publiclookup.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AppointmentLookupVerifyResponse {
    private String accessToken;
    private String expiresAt;
    private AppointmentLookupSummaryResponse appointment;
}