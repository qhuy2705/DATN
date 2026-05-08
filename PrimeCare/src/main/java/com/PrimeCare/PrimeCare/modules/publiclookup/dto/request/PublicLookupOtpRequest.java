package com.PrimeCare.PrimeCare.modules.publiclookup.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PublicLookupOtpRequest {
    @NotBlank
    private String code;

    private String channel;
}
