package com.PrimeCare.PrimeCare.shared.enums;

public enum AppointmentSourceType {
    PUBLIC_BOOKING,
    STAFF_BOOKING,
    WALK_IN;

    public static AppointmentSourceType fromRequestValue(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String normalized = rawValue.trim().toUpperCase();
        if ("ONLINE".equals(normalized)) {
            return PUBLIC_BOOKING;
        }
        return AppointmentSourceType.valueOf(normalized);
    }
}
