package com.PrimeCare.PrimeCare.modules.booking_restriction.enums;

public enum BookingRestrictionLevel {
    WARNING,
    VERIFY_REQUIRED,
    STAFF_ONLY;

    public boolean blocksPublicBooking() {
        return this == VERIFY_REQUIRED || this == STAFF_ONLY;
    }
}
