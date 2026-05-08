package com.PrimeCare.PrimeCare.modules.publiclookup.service;

import com.PrimeCare.PrimeCare.shared.email.EmailTemplate;
import com.PrimeCare.PrimeCare.shared.email.EmailTemplateRenderer;
import com.PrimeCare.PrimeCare.shared.email.MailSenderIdentity;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PublicLookupMailService {

    private final JavaMailSender mailSender;
    private final MailSenderIdentity mailSenderIdentity;

    @Value("${app.frontend.base-url:http://localhost:8081}")
    private String frontendBaseUrl;

    public void sendAppointmentLookupOtp(
            String toEmail,
            String patientName,
            String appointmentCode,
            String otpCode,
            int ttlSeconds,
            int resendCooldownSeconds
    ) {
        sendHtml(
                toEmail,
                "PrimeCare - Mã OTP tra cứu phiếu hẹn",
                renderAppointmentLookupOtpEmail(toEmail, patientName, appointmentCode, otpCode, ttlSeconds, resendCooldownSeconds)
        );
    }

    public void sendResultLookupOtp(
            String toEmail,
            String patientName,
            String lookupCode,
            String otpCode,
            int ttlSeconds,
            int resendCooldownSeconds
    ) {
        sendHtml(
                toEmail,
                "PrimeCare - Mã OTP tra cứu kết quả",
                renderResultLookupOtpEmail(toEmail, patientName, lookupCode, otpCode, ttlSeconds, resendCooldownSeconds)
        );
    }

    public void sendOtp(String toEmail, String subject, String title, String otpCode) {
        sendOtp(toEmail, subject, title, otpCode, 300, 30);
    }

    public void sendOtp(String toEmail, String subject, String title, String otpCode, int ttlSeconds, int resendCooldownSeconds) {
        String html = EmailTemplateRenderer.render(new EmailTemplate(
                title,
                "Xin chào,",
                "PrimeCare đã nhận yêu cầu xác thực tra cứu. Vui lòng nhập mã OTP bên dưới trên trang tra cứu.",
                List.of(
                        new EmailTemplate.Row("Mã OTP", otpCode),
                        new EmailTemplate.Row("Thời hạn hiệu lực", durationText(ttlSeconds))
                ),
                List.of(
                        "Để bảo vệ thông tin y tế cá nhân, bạn cần xác thực trước khi xem kết quả hoặc phiếu hẹn.",
                        "Bạn có thể yêu cầu gửi lại mã sau " + durationText(resendCooldownSeconds) + ".",
                        "Nếu bạn không thực hiện thao tác này, hãy bỏ qua email."
                ),
                new EmailTemplate.Cta("Mở trang tra cứu", lookupUrl()),
                "PrimeCare không bao giờ yêu cầu bạn cung cấp mật khẩu qua email."
        ));
        sendHtml(toEmail, subject, html);
    }

    private void sendHtml(String toEmail, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(mailSenderIdentity.internetAddress());
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (Exception ex) {
            throw new RuntimeException("Gửi OTP thất bại", ex);
        }
    }

    private String renderAppointmentLookupOtpEmail(
            String toEmail,
            String patientName,
            String appointmentCode,
            String otpCode,
            int ttlSeconds,
            int resendCooldownSeconds
    ) {
        String greeting = hasText(patientName)
                ? "Xin chào " + patientName.trim() + ","
                : "Xin chào,";
        String safeCode = hasText(appointmentCode) ? appointmentCode.trim() : "Đang cập nhật";

        return """
                <!doctype html>
                <html lang="vi">
                <body style="margin:0;background:#f1f5f9;font-family:Arial,'Helvetica Neue',sans-serif;color:#0f172a">
                  <div style="max-width:680px;margin:0 auto;padding:24px 12px">
                    <div style="background:#0f766e;color:#ffffff;padding:18px 22px;border-radius:8px 8px 0 0">
                      <div style="font-size:22px;font-weight:800">PrimeCare</div>
                      <div style="font-size:13px;margin-top:4px;color:#d1fae5">Tra cứu phiếu hẹn an toàn</div>
                    </div>
                    <div style="background:#ffffff;border:1px solid #e2e8f0;border-top:0;padding:24px 22px;border-radius:0 0 8px 8px">
                      <h1 style="font-size:22px;line-height:1.35;margin:0 0 14px;color:#0f172a">Mã OTP tra cứu phiếu hẹn</h1>
                      <p style="margin:0 0 12px;line-height:1.7;color:#334155">%s</p>
                      <p style="margin:0 0 16px;line-height:1.7;color:#334155">PrimeCare đã nhận yêu cầu xác thực tra cứu phiếu hẹn <strong>%s</strong> cho email <strong>%s</strong>.</p>
                      <p style="margin:0 0 8px;line-height:1.7;color:#334155">Mã OTP của bạn là:</p>
                      <div style="display:inline-block;background:#ecfdf5;border:1px solid #99f6e4;border-radius:8px;padding:14px 22px;font-size:30px;font-weight:800;letter-spacing:4px;color:#0f766e">%s</div>
                      <p style="margin:18px 0 0;line-height:1.7;color:#334155">Mã có hiệu lực trong %s.</p>
                      <p style="margin:4px 0 0;line-height:1.7;color:#334155">Bạn có thể yêu cầu gửi lại mã sau %s.</p>
                      <p style="margin:14px 0 0;line-height:1.7;color:#334155">Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email.</p>
                      <div style="margin-top:22px;padding-top:16px;border-top:1px solid #e2e8f0;color:#64748b;font-size:13px;line-height:1.6">
                        PrimeCare<br>
                        Đây là email tự động, vui lòng không chia sẻ mã OTP cho người khác.
                      </div>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(
                EmailTemplateRenderer.escapeHtml(greeting),
                EmailTemplateRenderer.escapeHtml(safeCode),
                EmailTemplateRenderer.escapeHtml(toEmail),
                EmailTemplateRenderer.escapeHtml(otpCode),
                EmailTemplateRenderer.escapeHtml(durationText(ttlSeconds)),
                EmailTemplateRenderer.escapeHtml(durationText(resendCooldownSeconds))
        );
    }

    private String renderResultLookupOtpEmail(
            String toEmail,
            String patientName,
            String lookupCode,
            String otpCode,
            int ttlSeconds,
            int resendCooldownSeconds
    ) {
        String greeting = hasText(patientName)
                ? "Xin chào " + patientName.trim() + ","
                : "Xin chào,";
        String safeCode = hasText(lookupCode) ? lookupCode.trim() : "Đang cập nhật";

        return """
                <!doctype html>
                <html lang="vi">
                <body style="margin:0;background:#f1f5f9;font-family:Arial,'Helvetica Neue',sans-serif;color:#0f172a">
                  <div style="max-width:680px;margin:0 auto;padding:24px 12px">
                    <div style="background:#0f766e;color:#ffffff;padding:18px 22px;border-radius:8px 8px 0 0">
                      <div style="font-size:22px;font-weight:800">PrimeCare</div>
                      <div style="font-size:13px;margin-top:4px;color:#d1fae5">Tra cứu kết quả an toàn</div>
                    </div>
                    <div style="background:#ffffff;border:1px solid #e2e8f0;border-top:0;padding:24px 22px;border-radius:0 0 8px 8px">
                      <h1 style="font-size:22px;line-height:1.35;margin:0 0 14px;color:#0f172a">Mã OTP tra cứu kết quả</h1>
                      <p style="margin:0 0 12px;line-height:1.7;color:#334155">%s</p>
                      <p style="margin:0 0 16px;line-height:1.7;color:#334155">PrimeCare đã nhận yêu cầu xác thực tra cứu kết quả với mã <strong>%s</strong> cho email <strong>%s</strong>.</p>
                      <p style="margin:0 0 8px;line-height:1.7;color:#334155">Mã OTP của bạn là:</p>
                      <div style="display:inline-block;background:#ecfdf5;border:1px solid #99f6e4;border-radius:8px;padding:14px 22px;font-size:30px;font-weight:800;letter-spacing:4px;color:#0f766e">%s</div>
                      <p style="margin:18px 0 0;line-height:1.7;color:#334155">Mã có hiệu lực trong %s.</p>
                      <p style="margin:4px 0 0;line-height:1.7;color:#334155">Bạn có thể yêu cầu gửi lại mã sau %s.</p>
                      <p style="margin:14px 0 0;line-height:1.7;color:#334155">Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email.</p>
                      <div style="margin-top:22px;padding-top:16px;border-top:1px solid #e2e8f0;color:#64748b;font-size:13px;line-height:1.6">
                        PrimeCare<br>
                        Đây là email tự động, vui lòng không chia sẻ mã OTP cho người khác.
                      </div>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(
                EmailTemplateRenderer.escapeHtml(greeting),
                EmailTemplateRenderer.escapeHtml(safeCode),
                EmailTemplateRenderer.escapeHtml(toEmail),
                EmailTemplateRenderer.escapeHtml(otpCode),
                EmailTemplateRenderer.escapeHtml(durationText(ttlSeconds)),
                EmailTemplateRenderer.escapeHtml(durationText(resendCooldownSeconds))
        );
    }

    private String durationText(int seconds) {
        if (seconds > 0 && seconds % 60 == 0) {
            return (seconds / 60) + " phút";
        }
        return seconds + " giây";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }

    private String lookupUrl() {
        String base = frontendBaseUrl == null || frontendBaseUrl.isBlank()
                ? "http://localhost:8081"
                : frontendBaseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/appointments/lookup";
    }
}
