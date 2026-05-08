package com.PrimeCare.PrimeCare.modules.notification.dto.response;

import com.PrimeCare.PrimeCare.shared.enums.NotificationChannel;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NotificationPreferenceResponse {
    private Long userId;
    private Boolean allowEmail;
    private Boolean allowSms;
    private Boolean appointmentReminders;
    private Boolean resultReadyAlerts;
    private Boolean invoiceUpdates;
    private Boolean securityAlerts;
    private NotificationChannel preferredChannel;
    private String maskedEmail;
    private String maskedPhone;
}
