package com.PrimeCare.PrimeCare.modules.notification.messaging.event;

public record AppointmentRescheduleOfferMailEvent(
        Long slotHoldId,
        Long originalAppointmentId,
        String responseToken
) {
}
