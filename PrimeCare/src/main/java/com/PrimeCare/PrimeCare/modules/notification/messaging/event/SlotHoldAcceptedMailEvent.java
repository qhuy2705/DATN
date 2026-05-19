package com.PrimeCare.PrimeCare.modules.notification.messaging.event;

public record SlotHoldAcceptedMailEvent(
        Long slotHoldId,
        Long newAppointmentId
) {
}
