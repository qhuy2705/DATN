package com.PrimeCare.PrimeCare.shared.email;

import java.util.List;

public final class EmailTemplateRenderer {

    private EmailTemplateRenderer() {
    }

    public static String render(EmailTemplate template) {
        StringBuilder rows = new StringBuilder();
        List<EmailTemplate.Row> rowValues = template.rows() == null ? List.of() : template.rows();
        for (EmailTemplate.Row row : rowValues) {
            rows.append("""
                    <tr>
                      <td style="padding:10px 12px;border-bottom:1px solid #e2e8f0;color:#475569;width:42%%">%s</td>
                      <td style="padding:10px 12px;border-bottom:1px solid #e2e8f0;color:#0f172a;font-weight:600">%s</td>
                    </tr>
                    """.formatted(escapeHtml(row.label()), escapeHtml(fallback(row.value()))));
        }

        StringBuilder notes = new StringBuilder();
        List<String> noteValues = template.notes() == null ? List.of() : template.notes();
        if (!noteValues.isEmpty()) {
            notes.append("<ul style=\"margin:12px 0 0 20px;padding:0;color:#334155;line-height:1.7\">");
            for (String note : noteValues) {
                if (note != null && !note.isBlank()) {
                    notes.append("<li>").append(escapeHtml(note.trim())).append("</li>");
                }
            }
            notes.append("</ul>");
        }

        String cta = "";
        if (template.cta() != null && template.cta().url() != null && !template.cta().url().isBlank()) {
            cta = """
                    <div style="margin:22px 0 8px">
                      <a href="%s" style="display:inline-block;background:#0f766e;color:#ffffff;text-decoration:none;padding:12px 18px;border-radius:6px;font-weight:700">%s</a>
                    </div>
                    """.formatted(escapeAttribute(template.cta().url()), escapeHtml(template.cta().label()));
        }

        return """
                <!doctype html>
                <html lang="vi">
                <body style="margin:0;background:#f1f5f9;font-family:Arial,'Helvetica Neue',sans-serif;color:#0f172a">
                  <div style="max-width:680px;margin:0 auto;padding:24px 12px">
                    <div style="background:#0f766e;color:#ffffff;padding:18px 22px;border-radius:8px 8px 0 0">
                      <div style="font-size:22px;font-weight:800;letter-spacing:.2px">%s</div>
                      <div style="font-size:13px;margin-top:4px;color:#d1fae5">Chăm sóc sức khỏe rõ ràng, an toàn và đúng hẹn</div>
                    </div>
                    <div style="background:#ffffff;border:1px solid #e2e8f0;border-top:0;padding:24px 22px;border-radius:0 0 8px 8px">
                      <h1 style="font-size:22px;line-height:1.35;margin:0 0 14px;color:#0f172a">%s</h1>
                      <p style="margin:0 0 12px;line-height:1.7;color:#334155">%s</p>
                      <p style="margin:0 0 18px;line-height:1.7;color:#334155">%s</p>
                      <table role="presentation" cellspacing="0" cellpadding="0" style="width:100%%;border-collapse:collapse;border:1px solid #e2e8f0;border-radius:6px;overflow:hidden;margin-top:8px">
                        %s
                      </table>
                      %s
                      %s
                      <div style="margin-top:22px;padding-top:16px;border-top:1px solid #e2e8f0;color:#64748b;font-size:13px;line-height:1.6">
                        %s<br>
                        Hotline: %s · Email: %s
                      </div>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(
                EmailTheme.BRAND,
                escapeHtml(template.title()),
                escapeHtml(template.greeting()),
                escapeHtml(template.intro()),
                rows,
                notes,
                cta,
                escapeHtml(fallback(template.footerNote(), "Đây là email tự động từ PrimeCare. Vui lòng không chia sẻ thông tin lịch hẹn hoặc mã tra cứu cho người không liên quan.")),
                EmailTheme.HOTLINE,
                EmailTheme.CONTACT_EMAIL
        );
    }

    public static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    public static String escapeAttribute(String value) {
        return escapeHtml(value).replace("\n", "").replace("\r", "");
    }

    private static String fallback(String value) {
        return fallback(value, "Đang cập nhật");
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
