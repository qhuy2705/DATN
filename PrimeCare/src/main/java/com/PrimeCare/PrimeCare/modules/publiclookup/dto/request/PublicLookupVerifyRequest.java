package com.PrimeCare.PrimeCare.modules.publiclookup.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PublicLookupVerifyRequest {
    @NotBlank
    private String code;
    @NotBlank
    private String otp;
}
