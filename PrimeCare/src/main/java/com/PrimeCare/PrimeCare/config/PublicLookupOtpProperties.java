package com.PrimeCare.PrimeCare.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.public-lookup.otp")
public class PublicLookupOtpProperties {
    private int ttlSeconds = 300;
    private int resendCooldownSeconds = 30;
    private int maxAttempts = 5;
    private int accessTokenTtlMinutes = 15;
}
