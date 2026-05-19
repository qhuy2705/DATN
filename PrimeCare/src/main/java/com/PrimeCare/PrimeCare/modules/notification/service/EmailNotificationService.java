package com.PrimeCare.PrimeCare.modules.notification.service;

import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.notification.entity.Notification;
import com.PrimeCare.PrimeCare.modules.notification.entity.NotificationAttempt;
import com.PrimeCare.PrimeCare.modules.notification.repository.NotificationAttemptRepository;
import com.PrimeCare.PrimeCare.modules.notification.repository.NotificationRepository;
import com.PrimeCare.PrimeCare.shared.enums.NotificationChannel;
import com.PrimeCare.PrimeCare.shared.enums.NotificationEntityType;
import com.PrimeCare.PrimeCare.shared.enums.NotificationStatus;
import com.PrimeCare.PrimeCare.shared.email.MailSenderIdentity;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmailNotificationService {

    private static final int NOTIFICATION_CONTENT_MAX_LENGTH = 240;
    private static final int ERROR_MESSAGE_MAX_LENGTH = 250;
    private static final int RESPONSE_EXCERPT_MAX_LENGTH = 500;

    private final JavaMailSender mailSender;
    private final MailSenderIdentity mailSenderIdentity;
    private final NotificationRepository notificationRepository;
    private final NotificationAttemptRepository notificationAttemptRepository;
    private final InternalNotificationService internalNotificationService;

    public void sendHtmlEmail(
            Appointment appointment,
            String toEmail,
            String subject,
            String htmlContent,
            String templateCode,
            byte[] attachmentBytes,
            String attachmentFilename
    ) {
        Notification notification = Notification.builder()
                .channel(NotificationChannel.EMAIL)
                .templateCode(templateCode)
                .appointment(appointment)
                .entityType(resolveEntityType(templateCode, appointment))
                .entityId(appointment != null ? appointment.getId() : null)
                .toEmail(toEmail)
                .content(toNotificationPreview(htmlContent))
                .status(NotificationStatus.PENDING)
                .provider("smtp")
                .scheduledAt(LocalDateTime.now())
                .build();

        notification = notificationRepository.save(notification);
        int attemptNo = (notification.getAttemptCount() == null ? 0 : notification.getAttemptCount()) + 1;
        notification.setAttemptCount(attemptNo);
        notification.setLastAttemptAt(LocalDateTime.now());
        notification = notificationRepository.save(notification);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, attachmentBytes != null, StandardCharsets.UTF_8.name());
            helper.setFrom(mailSenderIdentity.internetAddress());
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            if (attachmentBytes != null && attachmentFilename != null) {
                helper.addAttachment(
                        attachmentFilename,
                        new org.springframework.core.io.ByteArrayResource(attachmentBytes)
                );
            }

            mailSender.send(message);

            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(LocalDateTime.now());
            notificationRepository.save(notification);
            saveAttempt(notification, attemptNo, NotificationStatus.SENT, null, null, "smtp:accepted");
        } catch (Exception ex) {
            notification.setStatus(NotificationStatus.FAILED);
            notification.setErrorMessage(abbreviate(ex.getMessage(), ERROR_MESSAGE_MAX_LENGTH));
            notificationRepository.save(notification);
            saveAttempt(notification, attemptNo, NotificationStatus.FAILED, null, ex.getMessage(), ex.getClass().getSimpleName());
            notifyOperationsEmailFailed(notification, templateCode, toEmail, ex);
            throw new RuntimeException("Gửi mail thất bại", ex);
        }
    }

    private void notifyOperationsEmailFailed(
            Notification notification,
            String templateCode,
            String toEmail,
            Exception ex
    ) {
        String entityType = notification.getEntityType() != null ? notification.getEntityType().name() : "EMAIL";
        Long entityId = notification.getEntityId() != null ? notification.getEntityId() : notification.getId();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("templateCode", templateCode);
        metadata.put("toEmail", toEmail);
        metadata.put("notificationId", notification.getId());
        metadata.put("error", abbreviate(ex.getMessage(), ERROR_MESSAGE_MAX_LENGTH));

        internalNotificationService.notifyAdminRolesOnceRequiresNew(
                "EMAIL_DELIVERY_FAILED",
                InternalNotificationService.SEVERITY_CRITICAL,
                "Job email thất bại",
                "Gửi email " + templateCode + " thất bại. Vui lòng kiểm tra cấu hình SMTP hoặc hàng đợi.",
                "/app/admin/dashboard",
                entityType,
                entityId,
                metadata
        );
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

    private String toNotificationPreview(String htmlContent) {
        if (htmlContent == null || htmlContent.isBlank()) {
            return "";
        }

        String plainText = htmlContent
                .replaceAll("(?i)<br\\s*/?>", " ")
                .replaceAll("(?i)</p>", " ")
                .replaceAll("(?i)</div>", " ")
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();

        return abbreviate(plainText, NOTIFICATION_CONTENT_MAX_LENGTH);
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) return null;
        if (value.length() <= maxLength) return value;
        if (maxLength <= 3) return value.substring(0, maxLength);
        return value.substring(0, maxLength - 3) + "...";
    }
}
