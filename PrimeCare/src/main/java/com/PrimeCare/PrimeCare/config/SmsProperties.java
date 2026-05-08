package com.PrimeCare.PrimeCare.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.sms")
public class SmsProperties {
    private boolean enabled = false;
    private String provider = "mock";
    private boolean logOnly = true;
    private String fromNumber;
    private String twilioAccountSid;
    private String twilioAuthToken;
}
