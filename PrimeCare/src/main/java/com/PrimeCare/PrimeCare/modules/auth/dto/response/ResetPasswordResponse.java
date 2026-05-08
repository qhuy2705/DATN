package com.PrimeCare.PrimeCare.modules.auth.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class ResetPasswordResponse {
    private Long userId;
    private String email;
    private String phone;
    private String status;
    private String deliveryChannel;
    private String deliveryTarget;
    private boolean deliverySent;
    private String setupUrl;
    private LocalDateTime expiresAt;
}
