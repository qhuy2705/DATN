package com.PrimeCare.PrimeCare.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.booking-email-otp")
public class BookingEmailOtpProperties {
    private int ttlSeconds = 300;
    private int tokenTtlSeconds = 600;
    private int resendCooldownSeconds = 30;
    private int maxAttempts = 5;
}
