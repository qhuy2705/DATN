package com.PrimeCare.PrimeCare.modules.bookingemailotp.service;

import com.PrimeCare.PrimeCare.shared.email.EmailTemplate;
import com.PrimeCare.PrimeCare.shared.email.EmailTemplateRenderer;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BookingEmailOtpMailService {

    public String renderBookingEmailVerificationOtp(
            String toEmail,
            String otpCode,
            int ttlSeconds,
            int resendCooldownSeconds
    ) {
        return EmailTemplateRenderer.render(new EmailTemplate(
                "Mã xác thực đặt lịch PrimeCare",
                "Xin chào,",
                "PrimeCare đã nhận yêu cầu xác thực email để đặt lịch khám. Vui lòng nhập mã OTP bên dưới trước khi gửi yêu cầu đặt lịch.",
                List.of(
                        new EmailTemplate.Row("Email", toEmail),
                        new EmailTemplate.Row("Mã OTP", otpCode),
                        new EmailTemplate.Row("Thời hạn hiệu lực", durationText(ttlSeconds))
                ),
                List.of(
                        "Mã này chỉ dùng để xác thực email đặt lịch, không chứa thông tin lịch hẹn.",
                        "Bạn có thể yêu cầu gửi lại mã sau " + durationText(resendCooldownSeconds) + ".",
                        "Nếu bạn không thực hiện thao tác này, hãy bỏ qua email."
                ),
                null,
                "PrimeCare không bao giờ yêu cầu bạn cung cấp mật khẩu qua email."
        ));
    }

    private String durationText(int seconds) {
        if (seconds > 0 && seconds % 60 == 0) {
            return (seconds / 60) + " phút";
        }
        return seconds + " giây";
    }
}
