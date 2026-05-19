package com.PrimeCare.PrimeCare.modules.appointment.messaging;

import com.PrimeCare.PrimeCare.config.RabbitMqConfig;
import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.messaging.event.AppointmentResponseFallbackEmailEvent;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.notification.service.EmailNotificationService;
import com.PrimeCare.PrimeCare.shared.email.EmailTemplateRenderer;
import com.PrimeCare.PrimeCare.shared.pdf.PdfFormatters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentResponseFallbackEmailConsumer {

    private static final String SUBJECT = "Xác nhận lại lịch hẹn PrimeCare";
    private static final String TEMPLATE_CODE = "APPOINTMENT_RESPONSE_FALLBACK";

    private final AppointmentRepository appointmentRepository;
    private final EmailNotificationService emailNotificationService;

    @RabbitListener(queues = RabbitMqConfig.APPOINTMENT_RESPONSE_FALLBACK_EMAIL_QUEUE)
    public void consume(AppointmentResponseFallbackEmailEvent event) {
        try {
            validate(event);
            Appointment appointment = appointmentRepository.findById(event.appointmentId()).orElse(null);
            String html = renderFallbackEmail(event);
            emailNotificationService.sendHtmlEmail(
                    appointment,
                    event.patientEmail(),
                    SUBJECT,
                    html,
                    TEMPLATE_CODE,
                    null,
                    null
            );
        } catch (RuntimeException ex) {
            log.error(
                    "Send appointment response fallback email failed appointmentId={} appointmentCode={} toEmail={} error={}",
                    event != null ? event.appointmentId() : null,
                    event != null ? event.appointmentCode() : null,
                    event != null ? maskEmail(event.patientEmail()) : null,
                    ex.getMessage(),
                    ex
            );
            throw ex;
        }
    }

    private String renderFallbackEmail(AppointmentResponseFallbackEmailEvent event) {
        String keepUrl = actionUrl(event.responseUrl(), "keep");
        String recallUrl = actionUrl(event.responseUrl(), "request-recall");
        String updatePhoneUrl = actionUrl(event.responseUrl(), "update-phone");
        String cancelUrl = actionUrl(event.responseUrl(), "cancel");
        String greeting = "Xin chào " + safeText(event.patientName(), "Quý khách") + ",";

        return """
                <!doctype html>
                <html lang="vi">
                <body style="margin:0;background:#f1f5f9;font-family:Arial,'Helvetica Neue',sans-serif;color:#0f172a">
                  <div style="max-width:680px;margin:0 auto;padding:24px 12px">
                    <div style="background:#0f766e;color:#ffffff;padding:18px 22px;border-radius:8px 8px 0 0">
                      <div style="font-size:22px;font-weight:800;letter-spacing:.2px">PrimeCare</div>
                      <div style="font-size:13px;margin-top:4px;color:#d1fae5">Chăm sóc sức khỏe rõ ràng, an toàn và đúng hẹn</div>
                    </div>
                    <div style="background:#ffffff;border:1px solid #e2e8f0;border-top:0;padding:24px 22px;border-radius:0 0 8px 8px">
                      <h1 style="font-size:22px;line-height:1.35;margin:0 0 14px;color:#0f172a">Xác nhận lại lịch hẹn PrimeCare</h1>
                      <p style="margin:0 0 12px;line-height:1.7;color:#334155">%s</p>
                      <p style="margin:0 0 18px;line-height:1.7;color:#334155">Chúng tôi chưa liên hệ được với bạn qua số điện thoại đã cung cấp. Vui lòng xác nhận lại để giữ lịch hẹn hoặc cập nhật số điện thoại.</p>
                      <table role="presentation" cellspacing="0" cellpadding="0" style="width:100%%;border-collapse:collapse;border:1px solid #e2e8f0;border-radius:6px;overflow:hidden;margin-top:8px">
                        %s
                      </table>
                      <div style="margin:22px 0 8px">
                        <a href="%s" style="display:block;background:#0f766e;color:#ffffff;text-decoration:none;text-align:center;padding:12px 18px;border-radius:6px;font-weight:700;margin-bottom:10px">Tôi vẫn muốn giữ lịch</a>
                        <a href="%s" style="display:block;background:#e0f2fe;color:#075985;text-decoration:none;text-align:center;padding:12px 18px;border-radius:6px;font-weight:700;margin-bottom:10px">Số điện thoại này đúng, vui lòng gọi lại</a>
                        <a href="%s" style="display:block;background:#f8fafc;color:#0f172a;text-decoration:none;text-align:center;padding:12px 18px;border:1px solid #cbd5e1;border-radius:6px;font-weight:700;margin-bottom:10px">Tôi muốn đổi số điện thoại</a>
                        <a href="%s" style="display:block;background:#fff1f2;color:#be123c;text-decoration:none;text-align:center;padding:12px 18px;border:1px solid #fecdd3;border-radius:6px;font-weight:700">Hủy lịch hẹn</a>
                      </div>
                      <ul style="margin:16px 0 0 20px;padding:0;color:#334155;line-height:1.7">
                        <li>Liên kết phản hồi có thời hạn và chỉ dùng một lần cho thao tác thay đổi trạng thái.</li>
                        <li>Email xác nhận không được tính là xác minh điện thoại bởi nhân viên.</li>
                        <li>Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email hoặc liên hệ PrimeCare.</li>
                      </ul>
                      <div style="margin-top:22px;padding-top:16px;border-top:1px solid #e2e8f0;color:#64748b;font-size:13px;line-height:1.6">
                        PrimeCare chỉ gửi thông tin cần thiết trong email này để bảo vệ quyền riêng tư của bạn.<br>
                        Hotline: %s
                      </div>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(
                EmailTemplateRenderer.escapeHtml(greeting),
                rows(event),
                EmailTemplateRenderer.escapeAttribute(keepUrl),
                EmailTemplateRenderer.escapeAttribute(recallUrl),
                EmailTemplateRenderer.escapeAttribute(updatePhoneUrl),
                EmailTemplateRenderer.escapeAttribute(cancelUrl),
                EmailTemplateRenderer.escapeHtml(safeText(event.branchHotline(), "PrimeCare"))
        );
    }

    private String rows(AppointmentResponseFallbackEmailEvent event) {
        return row("Mã lịch hẹn", event.appointmentCode())
                + row("Ngày khám", PdfFormatters.formatDate(event.visitDate()))
                + row("Giờ dự kiến", timeWindow(event))
                + row("Cơ sở khám", event.branchName())
                + row("Số điện thoại hiện tại", event.maskedPatientPhone())
                + row("Hạn phản hồi", PdfFormatters.formatDateTime(event.tokenExpiresAt()));
    }

    private String row(String label, String value) {
        return """
                <tr>
                  <td style="padding:10px 12px;border-bottom:1px solid #e2e8f0;color:#475569;width:42%%">%s</td>
                  <td style="padding:10px 12px;border-bottom:1px solid #e2e8f0;color:#0f172a;font-weight:600">%s</td>
                </tr>
                """.formatted(
                EmailTemplateRenderer.escapeHtml(label),
                EmailTemplateRenderer.escapeHtml(safeText(value, "Đang cập nhật"))
        );
    }

    private String actionUrl(String responseUrl, String action) {
        String separator = responseUrl.contains("?") ? "&" : "?";
        return responseUrl + separator + "action=" + action;
    }

    private String timeWindow(AppointmentResponseFallbackEmailEvent event) {
        String start = PdfFormatters.formatTime(event.etaStart());
        String end = PdfFormatters.formatTime(event.etaEnd());
        if (start == null && end == null) {
            return "Đang cập nhật";
        }
        if (start == null) {
            return end;
        }
        if (end == null) {
            return start;
        }
        return start + " - " + end;
    }

    private void validate(AppointmentResponseFallbackEmailEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Appointment response fallback email event is null");
        }
        if (event.appointmentId() == null) {
            throw new IllegalArgumentException("Appointment response fallback email event missing appointmentId");
        }
        if (!hasText(event.patientEmail())) {
            throw new IllegalArgumentException("Appointment response fallback email event missing patientEmail");
        }
        if (!hasText(event.responseUrl())) {
            throw new IllegalArgumentException("Appointment response fallback email event missing responseUrl");
        }
        if (event.tokenExpiresAt() == null) {
            throw new IllegalArgumentException("Appointment response fallback email event missing tokenExpiresAt");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }

    private String safeText(String value, String fallback) {
        return value != null && !value.trim().isBlank() ? value.trim() : fallback;
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(Math.max(0, at));
        }
        String prefix = email.substring(0, at);
        String domain = email.substring(at);
        if (prefix.length() <= 2) {
            return prefix.charAt(0) + "***" + domain;
        }
        return prefix.substring(0, 2) + "***" + domain;
    }
}
