package com.PrimeCare.PrimeCare.shared.email;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTemplateRendererTest {

    @Test
    void renderEscapesDynamicHtmlAndKeepsVietnameseText() {
        String html = EmailTemplateRenderer.render(new EmailTemplate(
                "PrimeCare đã nhận yêu cầu đặt lịch của bạn",
                "Xin chào Nguyễn Thị Ánh Tuyết <script>alert('x')</script>,",
                "Lịch khám của bạn đã được xác nhận.",
                List.of(
                        new EmailTemplate.Row("Địa chỉ", "123 Trần Hưng Đạo, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh"),
                        new EmailTemplate.Row("Ghi chú", "Bệnh nhân có tiền sử dị ứng kháng sinh nhóm Penicillin <b>nguy cơ</b>")
                ),
                List.of("Vui lòng đến trước giờ hẹn 10-15 phút."),
                new EmailTemplate.Cta("Tra cứu lịch hẹn", "https://primecare.example/lookup?code=<bad>"),
                "PrimeCare bảo vệ thông tin y tế cá nhân của bạn."
        ));

        assertThat(html).contains("PrimeCare đã nhận yêu cầu đặt lịch của bạn");
        assertThat(html).contains("Nguyễn Thị Ánh Tuyết");
        assertThat(html).contains("Trần Hưng Đạo");
        assertThat(html).contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;");
        assertThat(html).contains("&lt;b&gt;nguy cơ&lt;/b&gt;");
        assertThat(html).contains("code=&lt;bad&gt;");
        assertThat(html).doesNotContain("<script>");
        assertThat(html).doesNotContain("<b>nguy cơ</b>");
    }
}
