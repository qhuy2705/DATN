package com.PrimeCare.PrimeCare.shared.enums;

// Backend is the source of truth; frontend must use these exact appointment statuses.
public enum AppointmentStatus {
    REQUESTED,
    CONFIRMED,
    CHECKED_IN,
    COMPLETED,
    NO_SHOW,
    CANCELLED
}
