package com.PrimeCare.PrimeCare.modules.appointment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.appointment.no-show")
public class AppointmentNoShowProperties {

    private long graceMinutes = 10;

    public long effectiveGraceMinutes() {
        return Math.max(0, graceMinutes);
    }
}
