package com.PrimeCare.PrimeCare.modules.booking_restriction.service;

public record BookingIdentity(
        String identityKeyHash,
        String canonicalPhone,
        String normalizedEmail
) {

    public boolean hasIdentityKeyHash() {
        return identityKeyHash != null && !identityKeyHash.isBlank();
    }
}
