package com.PrimeCare.PrimeCare.modules.auth.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class CurrentUserResponse {
    private Long id;
    private String email;
    private Boolean emailVerified;
    private LocalDateTime emailVerifiedAt;
    private String fullName;
    private String role;
    private String avatarUrl;
    private Long branchId;
    private String branchName;
    private Long patientId;
}
