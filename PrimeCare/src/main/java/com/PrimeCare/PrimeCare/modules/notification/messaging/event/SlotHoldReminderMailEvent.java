package com.PrimeCare.PrimeCare.modules.notification.messaging.event;

public record SlotHoldReminderMailEvent(
        Long slotHoldId,
        String responseToken
) {
}
