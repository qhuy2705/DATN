package com.PrimeCare.PrimeCare.modules.publiclookup.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PublicLookupOtpResponse {
    private String channel;
    private String maskedDestination;
    private Integer expiresInSeconds;
    private Integer resendAvailableInSeconds;
}
