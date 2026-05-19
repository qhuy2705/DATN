package com.PrimeCare.PrimeCare.modules.bookingemailotp.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class BookingEmailOtpVerifyResponse {
    private String bookingEmailVerificationToken;
    private String expiresAt;
}
