package com.PrimeCare.PrimeCare.modules.publiccontact.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminPublicContactSubmissionResponse {
    private Long id;
    private String referenceCode;
    private String fullName;
    private String email;
    private String phone;
    private String message;
    private String sourcePage;
    private String requesterIp;
    private String userAgent;
    private String status;
    private LocalDateTime emailedAt;
    private String emailError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
