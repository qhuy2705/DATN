package com.PrimeCare.PrimeCare.modules.notification.service;

import com.PrimeCare.PrimeCare.config.SmsProperties;
import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.notification.entity.Notification;
import com.PrimeCare.PrimeCare.modules.notification.entity.NotificationAttempt;
import com.PrimeCare.PrimeCare.modules.notification.repository.NotificationAttemptRepository;
import com.PrimeCare.PrimeCare.modules.notification.repository.NotificationRepository;
import com.PrimeCare.PrimeCare.shared.enums.NotificationChannel;
import com.PrimeCare.PrimeCare.shared.enums.NotificationEntityType;
import com.PrimeCare.PrimeCare.shared.enums.NotificationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmsNotificationService {

    private static final int NOTIFICATION_CONTENT_MAX_LENGTH = 240;
    private static final int ERROR_MESSAGE_MAX_LENGTH = 250;
    private static final int RESPONSE_EXCERPT_MAX_LENGTH = 500;

    private final NotificationRepository notificationRepository;
    private final NotificationAttemptRepository notificationAttemptRepository;
    private final SmsProperties smsProperties;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public void sendSms(Appointment appointment, String toPhone, String content, String templateCode) {
        String normalizedPhone = normalizePhoneNumber(toPhone);
        if (normalizedPhone == null || normalizedPhone.isBlank()) {
            throw new IllegalArgumentException("So dien thoai nhan SMS khong hop le");
        }

        Notification notification = Notification.builder()
                .channel(NotificationChannel.SMS)
                .templateCode(templateCode)
                .appointment(appointment)
                .entityType(resolveEntityType(templateCode, appointment))
                .entityId(appointment != null ? appointment.getId() : null)
                .toPhone(normalizedPhone)
                .content(abbreviate(content, NOTIFICATION_CONTENT_MAX_LENGTH))
                .status(NotificationStatus.PENDING)
                .provider(resolveProviderName())
                .scheduledAt(LocalDateTime.now())
                .build();

        notification = notificationRepository.save(notification);
        int attemptNo = (notification.getAttemptCount() == null ? 0 : notification.getAttemptCount()) + 1;
        notification.setAttemptCount(attemptNo);
        notification.setLastAttemptAt(LocalDateTime.now());
        notification = notificationRepository.save(notification);

        try {
            if (!smsProperties.isEnabled() || smsProperties.isLogOnly() || isMockProvider()) {
                log.info("[MOCK_SMS] to={} template={} content={}", normalizedPhone, templateCode, content);
                notification.setStatus(NotificationStatus.SENT);
                notification.setProviderMessageId("mock-" + System.currentTimeMillis());
                notification.setSentAt(LocalDateTime.now());
                notificationRepository.save(notification);
                saveAttempt(notification, attemptNo, NotificationStatus.SENT, notification.getProviderMessageId(), null, "mock-sent");
                return;
            }

            if (isTwilioProvider()) {
                sendViaTwilio(notification, attemptNo, normalizedPhone, content);
                return;
            }

            throw new IllegalStateException("Unsupported SMS provider: " + smsProperties.getProvider());
        } catch (Exception ex) {
            notification.setStatus(NotificationStatus.FAILED);
            notification.setErrorMessage(abbreviate(ex.getMessage(), ERROR_MESSAGE_MAX_LENGTH));
            notificationRepository.save(notification);
            saveAttempt(notification, attemptNo, NotificationStatus.FAILED, null, ex.getMessage(), ex.getClass().getSimpleName());
            throw new RuntimeException("Gui SMS that bai", ex);
        }
    }

    private void sendViaTwilio(Notification notification, int attemptNo, String toPhone, String content) throws Exception {
        if (blank(smsProperties.getTwilioAccountSid()) || blank(smsProperties.getTwilioAuthToken()) || blank(smsProperties.getFromNumber())) {
            throw new IllegalStateException("Twilio SMS is not fully configured");
        }

        String accountSid = smsProperties.getTwilioAccountSid().trim();
        String endpoint = "https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json";

        Map<String, String> form = new LinkedHashMap<>();
        form.put("To", toPhone);
        form.put("From", normalizePhoneNumber(smsProperties.getFromNumber()));
        form.put("Body", content);

        StringJoiner joiner = new StringJoiner("&");
        form.forEach((key, value) -> joiner.add(urlEncode(key) + "=" + urlEncode(value)));

        String auth = Base64.getEncoder()
                .encodeToString((accountSid + ":" + smsProperties.getTwilioAuthToken().trim()).getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .header("Authorization", "Basic " + auth)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(joiner.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Twilio response status=" + response.statusCode() + " body=" + abbreviate(response.body(), 180));
        }

        notification.setStatus(NotificationStatus.SENT);
        notification.setSentAt(LocalDateTime.now());
        notification.setProviderMessageId(extractJsonValue(response.body(), "sid"));
        notificationRepository.save(notification);
        saveAttempt(notification, attemptNo, NotificationStatus.SENT, notification.getProviderMessageId(), null, response.body());
    }

    private NotificationEntityType resolveEntityType(String templateCode, Appointment appointment) {
        if (appointment != null) {
            return NotificationEntityType.APPOINTMENT;
        }
        String safeTemplateCode = templateCode == null ? "" : templateCode.toLowerCase();
        if (safeTemplateCode.contains("otp")) {
            return NotificationEntityType.OTP;
        }
        if (safeTemplateCode.contains("account") || safeTemplateCode.contains("password") || safeTemplateCode.contains("credential")) {
            return NotificationEntityType.ACCOUNT;
        }
        return NotificationEntityType.GENERAL;
    }

    private void saveAttempt(Notification notification,
                             int attemptNo,
                             NotificationStatus status,
                             String providerMessageId,
                             String errorMessage,
                             String responseExcerpt) {
        notificationAttemptRepository.save(NotificationAttempt.builder()
                .notification(notification)
                .attemptNo(attemptNo)
                .status(status)
                .providerMessageId(providerMessageId)
                .errorMessage(abbreviate(errorMessage, ERROR_MESSAGE_MAX_LENGTH))
                .responseExcerpt(abbreviate(responseExcerpt, RESPONSE_EXCERPT_MAX_LENGTH))
                .attemptedAt(LocalDateTime.now())
                .build());
    }

    public String normalizePhoneNumber(String value) {
        if (value == null) return null;
        String digits = value.replaceAll("\\s+", "").trim();
        if (digits.isBlank()) return null;
        if (digits.startsWith("+")) return digits;
        if (digits.startsWith("84")) return "+" + digits;
        if (digits.startsWith("0") && digits.length() >= 10) return "+84" + digits.substring(1);
        return digits;
    }

    private String extractJsonValue(String body, String fieldName) {
        if (body == null || body.isBlank()) return null;
        String marker = "\"" + fieldName + "\"";
        int markerIndex = body.indexOf(marker);
        if (markerIndex < 0) return null;
        int colonIndex = body.indexOf(':', markerIndex + marker.length());
        if (colonIndex < 0) return null;
        int firstQuote = body.indexOf('"', colonIndex + 1);
        if (firstQuote < 0) return null;
        int secondQuote = body.indexOf('"', firstQuote + 1);
        if (secondQuote < 0) return null;
        return body.substring(firstQuote + 1, secondQuote);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String resolveProviderName() {
        if (!smsProperties.isEnabled() || smsProperties.isLogOnly()) {
            return "mock-sms";
        }
        return blank(smsProperties.getProvider()) ? "sms" : smsProperties.getProvider().trim().toLowerCase();
    }

    private boolean isMockProvider() {
        return blank(smsProperties.getProvider()) || "mock".equalsIgnoreCase(smsProperties.getProvider());
    }

    private boolean isTwilioProvider() {
        return "twilio".equalsIgnoreCase(smsProperties.getProvider());
    }

    private boolean blank(String value) {
        return value == null || value.trim().isBlank();
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) return null;
        if (value.length() <= maxLength) return value;
        if (maxLength <= 3) return value.substring(0, maxLength);
        return value.substring(0, maxLength - 3) + "...";
    }
}
