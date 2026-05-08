package com.PrimeCare.PrimeCare.modules.notification.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InternalNotificationUnreadCountResponse {
    private long unreadCount;
}
