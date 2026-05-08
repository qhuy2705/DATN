package com.PrimeCare.PrimeCare.modules.notification.dto.request;

import com.PrimeCare.PrimeCare.shared.enums.NotificationChannel;
import lombok.Data;

@Data
public class UpdateNotificationPreferenceRequest {
    private Boolean allowEmail;
    private Boolean allowSms;
    private Boolean appointmentReminders;
    private Boolean resultReadyAlerts;
    private Boolean invoiceUpdates;
    private Boolean securityAlerts;
    private NotificationChannel preferredChannel;
}
