package com.PrimeCare.PrimeCare.modules.auth.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class CredentialSetupDeliveryResponse {
    private String deliveryChannel;
    private String maskedDestination;
    private boolean delivered;
    private String setupUrl;
    private LocalDateTime expiresAt;
}
