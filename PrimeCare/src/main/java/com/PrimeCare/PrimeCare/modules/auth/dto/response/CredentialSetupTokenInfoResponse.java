package com.PrimeCare.PrimeCare.modules.auth.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class CredentialSetupTokenInfoResponse {
    private String purpose;
    private String email;
    private String phone;
    private String fullName;
    private String status;
    private boolean expired;
    private boolean used;
    private LocalDateTime expiresAt;
}
