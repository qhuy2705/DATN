package com.PrimeCare.PrimeCare.modules.auth.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CreateUserResponse {
    private Long id;
    private String fullName;
    private String email;
    private String phone;
    private String role;
    private String status;
    private Long doctorProfileId;
    private Long staffProfileId;
    private boolean setupRequired;
    private String deliveryChannel;
    private String deliveryTarget;
    private boolean deliverySent;
    private String setupUrl;
    private LocalDateTime setupExpiresAt;
}
