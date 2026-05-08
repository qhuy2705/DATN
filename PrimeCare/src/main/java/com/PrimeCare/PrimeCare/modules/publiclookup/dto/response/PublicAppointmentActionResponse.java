package com.PrimeCare.PrimeCare.modules.publiclookup.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PublicAppointmentActionResponse {
    private String code;
    private String status;
    private String message;
}
