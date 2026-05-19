package com.PrimeCare.PrimeCare.config;

import com.PrimeCare.PrimeCare.shared.enums.AppointmentSourceType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class AppointmentSourceTypeConverter implements Converter<String, AppointmentSourceType> {

    @Override
    public AppointmentSourceType convert(String source) {
        return AppointmentSourceType.fromRequestValue(source);
    }
}
