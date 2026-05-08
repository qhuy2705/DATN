package com.PrimeCare.PrimeCare.modules.appointment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.appointment")
public class AppointmentProperties {

    private Integer defaultSlotCapacity = 1;

    public int effectiveDefaultSlotCapacity() {
        return Math.max(1, defaultSlotCapacity != null ? defaultSlotCapacity : 1);
    }
}
