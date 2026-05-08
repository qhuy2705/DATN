package com.PrimeCare.PrimeCare.modules.auth.service;

import com.PrimeCare.PrimeCare.modules.notification.service.EmailNotificationService;
import com.PrimeCare.PrimeCare.modules.notification.service.SmsNotificationService;
import com.PrimeCare.PrimeCare.shared.email.EmailTemplate;
import com.PrimeCare.PrimeCare.shared.email.EmailTemplateRenderer;
import com.PrimeCare.PrimeCare.shared.enums.CredentialSetupTokenPurpose;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountCredentialNotificationService {

    private final EmailNotificationService emailNotificationService;
    private final SmsNotificationService smsNotificationService;

    @Value("${app.frontend.base-url:http://localhost:8081}")
    private String frontendBaseUrl;

    public DeliveryResult sendSetupLink(String fullName,
                                        String email,
                                        String phone,
                                        CredentialSetupTokenPurpose purpose,
                                        String rawToken) {
        String setupUrl = buildSetupUrl(rawToken);
        String subject = purpose == CredentialSetupTokenPurpose.ACCOUNT_SETUP
                ? "Kích hoạt tài khoản PrimeCare"
                : "Đặt lại mật khẩu PrimeCare";

        String heading = purpose == CredentialSetupTokenPurpose.ACCOUNT_SETUP
                ? "Kích hoạt tài khoản PrimeCare"
                : "Đặt lại mật khẩu PrimeCare";

        String bodyText = purpose == CredentialSetupTokenPurpose.ACCOUNT_SETUP
                ? "Tài khoản của bạn đã được tạo. Vui lòng thiết lập mật khẩu để bắt đầu đăng nhập."
                : "Chúng tôi đã nhận yêu cầu đặt lại mật khẩu. Vui lòng thiết lập mật khẩu mới qua liên kết bên dưới.";

        String html = EmailTemplateRenderer.render(new EmailTemplate(
                heading,
                "Xin chào " + safeName(fullName) + ",",
                bodyText,
                List.of(
                        new EmailTemplate.Row("Tài khoản", email != null && !email.isBlank() ? email : phone),
                        new EmailTemplate.Row("Hiệu lực liên kết", "30 phút")
                ),
                List.of(
                        "Không chia sẻ liên kết thiết lập mật khẩu cho người khác.",
                        "Nếu bạn không yêu cầu thao tác này, vui lòng liên hệ quản trị viên PrimeCare."
                ),
                new EmailTemplate.Cta("Mở trang thiết lập mật khẩu", setupUrl),
                "PrimeCare gửi email này để bảo vệ quá trình kích hoạt hoặc khôi phục tài khoản của bạn."
        ));

        if (email != null && !email.isBlank()) {
            try {
                emailNotificationService.sendHtmlEmail(null, email, subject, html,
                        purpose == CredentialSetupTokenPurpose.ACCOUNT_SETUP ? "ACCOUNT_SETUP" : "PASSWORD_RESET",
                        null, null);
                return DeliveryResult.builder()
                        .deliveryChannel("EMAIL")
                        .maskedDestination(maskEmail(email))
                        .delivered(true)
                        .setupUrl(setupUrl)
                        .build();
            } catch (Exception ex) {
                log.warn("Failed to send credential email to {}. Falling back to next channel.", email, ex);
            }
        }

        if (phone != null && !phone.isBlank()) {
            try {
                String content = (purpose == CredentialSetupTokenPurpose.ACCOUNT_SETUP
                        ? "PrimeCare: Tài khoản của bạn đã sẵn sàng. Đặt mật khẩu tại: "
                        : "PrimeCare: Đặt lại mật khẩu tại: ") + setupUrl;
                smsNotificationService.sendSms(null, phone, content,
                        purpose == CredentialSetupTokenPurpose.ACCOUNT_SETUP ? "ACCOUNT_SETUP" : "PASSWORD_RESET");
                return DeliveryResult.builder()
                        .deliveryChannel("SMS")
                        .maskedDestination(maskPhone(phone))
                        .delivered(true)
                        .setupUrl(setupUrl)
                        .build();
            } catch (Exception ex) {
                log.warn("Failed to send credential SMS to {}. Falling back to manual delivery.", phone, ex);
            }
        }

        return DeliveryResult.builder()
                .deliveryChannel("MANUAL")
                .maskedDestination(email != null && !email.isBlank() ? maskEmail(email) : maskPhone(phone))
                .delivered(false)
                .setupUrl(setupUrl)
                .build();
    }

    public String buildSetupUrl(String rawToken) {
        String base = frontendBaseUrl.endsWith("/") ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1) : frontendBaseUrl;
        return base + "/set-password?token=" + rawToken;
    }

    private String safeName(String fullName) {
        return fullName == null || fullName.isBlank() ? "nguoi dung" : fullName;
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return email;
        return email.substring(0, 1) + "***" + email.substring(at - 1);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return phone;
        return "***" + phone.substring(phone.length() - 4);
    }

    @lombok.Builder
    @lombok.Getter
    public static class DeliveryResult {
        private String deliveryChannel;
        private String maskedDestination;
        private boolean delivered;
        private String setupUrl;
    }
}
