package com.PrimeCare.PrimeCare.modules.bookingemailotp.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class BookingEmailOtpRequestResponse {
    private String verificationId;
    private String channel;
    private String maskedDestination;
    private int expiresInSeconds;
    private int resendAvailableInSeconds;
}
