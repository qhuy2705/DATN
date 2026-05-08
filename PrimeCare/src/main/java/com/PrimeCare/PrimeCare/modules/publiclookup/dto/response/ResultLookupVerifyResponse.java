package com.PrimeCare.PrimeCare.modules.publiclookup.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResultLookupVerifyResponse {
    private String accessToken;
    private String expiresAt;
    private ResultLookupSummaryResponse result;
}