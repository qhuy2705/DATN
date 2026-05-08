package com.PrimeCare.PrimeCare.modules.notification.messaging;

import com.PrimeCare.PrimeCare.config.RabbitMqConfig;
import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.file.service.FileStorageService;
import com.PrimeCare.PrimeCare.modules.notification.messaging.event.AppointmentConfirmedMailReadyEvent;
import com.PrimeCare.PrimeCare.modules.notification.messaging.event.AppointmentCreatedMailEvent;
import com.PrimeCare.PrimeCare.modules.notification.messaging.event.AppointmentReminderMailEvent;
import com.PrimeCare.PrimeCare.modules.notification.messaging.event.ResultReadyMailEvent;
import com.PrimeCare.PrimeCare.modules.notification.service.EmailNotificationService;
import com.PrimeCare.PrimeCare.modules.notification.service.InternalNotificationService;
import com.PrimeCare.PrimeCare.modules.notification.service.SmsNotificationService;
import com.PrimeCare.PrimeCare.shared.email.EmailTemplate;
import com.PrimeCare.PrimeCare.shared.email.EmailTemplateRenderer;
import com.PrimeCare.PrimeCare.shared.pdf.PdfFormatters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentMailConsumer {

    private final AppointmentRepository appointmentRepository;
    private final EmailNotificationService emailNotificationService;
    private final SmsNotificationService smsNotificationService;
    private final FileStorageService fileStorageService;
    private final InternalNotificationService internalNotificationService;

    @Value("${app.frontend.base-url:http://localhost:8081}")
    private String frontendBaseUrl;

    @RabbitListener(queues = RabbitMqConfig.APPOINTMENT_CREATED_QUEUE)
    public void handleAppointmentCreated(AppointmentCreatedMailEvent event) {
        if (event == null || event.appointmentId() == null) {
            log.warn("Skip APPOINTMENT_CREATED mail: event hoặc appointmentId bị null. event={}", event);
            return;
        }

        Appointment appointment = appointmentRepository.findById(event.appointmentId()).orElse(null);
        if (appointment == null) {
            log.warn("Skip APPOINTMENT_CREATED mail: không tìm thấy appointment id={}", event.appointmentId());
            return;
        }

        if (event.patientEmail() == null || event.patientEmail().isBlank()) {
            log.warn("Skip APPOINTMENT_CREATED mail: thiếu patientEmail cho appointment id={}", event.appointmentId());
            return;
        }

        String html = EmailTemplateRenderer.render(new EmailTemplate(
                "PrimeCare đã nhận yêu cầu đặt lịch của bạn",
                "Xin chào " + PdfFormatters.safeText(event.patientFullName(), "Quý khách") + ",",
                "PrimeCare đã nhận yêu cầu đặt lịch khám của bạn. Nhân viên PrimeCare sẽ liên hệ xác nhận lịch hẹn nếu cần.",
                appointmentRows(appointment, "Chờ xác nhận"),
                List.of(
                        "Vui lòng kiểm tra điện thoại/email để nhận cập nhật mới nhất.",
                        "Thông tin y tế chi tiết sẽ không được gửi qua email để bảo vệ quyền riêng tư của bạn."
                ),
                new EmailTemplate.Cta("Tra cứu lịch hẹn", lookupUrl(appointment.getCode())),
                "Cảm ơn bạn đã tin tưởng PrimeCare."
        ));

        emailNotificationService.sendHtmlEmail(
                appointment,
                event.patientEmail(),
                "PrimeCare đã nhận yêu cầu đặt lịch của bạn",
                html,
                "APPOINTMENT_CREATED",
                null,
                null
        );
    }

    @RabbitListener(queues = RabbitMqConfig.APPOINTMENT_CONFIRMED_MAIL_READY_QUEUE)
    public void handleAppointmentConfirmedMailReady(AppointmentConfirmedMailReadyEvent event) {
        if (event == null || event.appointmentId() == null) {
            log.warn("Skip APPOINTMENT_CONFIRMED mail: event hoặc appointmentId bị null. event={}", event);
            return;
        }

        Appointment appointment = appointmentRepository.findById(event.appointmentId()).orElse(null);
        if (appointment == null) {
            log.warn("Skip APPOINTMENT_CONFIRMED mail: không tìm thấy appointment id={}", event.appointmentId());
            return;
        }

        if (event.patientEmail() == null || event.patientEmail().isBlank()) {
            log.warn("Skip APPOINTMENT_CONFIRMED mail: thiếu patientEmail cho appointment id={}", event.appointmentId());
            return;
        }

        if (event.pdfS3Key() == null || event.pdfS3Key().isBlank()) {
            log.warn("Skip APPOINTMENT_CONFIRMED mail: thiếu pdfS3Key cho appointment id={}", event.appointmentId());
            return;
        }

        byte[] pdf;
        try {
            pdf = fileStorageService.downloadAsBytes(event.pdfS3Key());
        } catch (Exception ex) {
            log.error("Không tải được PDF từ storage cho appointment id={}, key={}",
                    event.appointmentId(), event.pdfS3Key(), ex);
            notifyAppointmentMailAttachmentFailed(appointment, event.pdfS3Key(), ex.getMessage());
            throw new IllegalStateException("Không thể tải tệp PDF từ storage để gửi mail", ex);
        }

        if (pdf == null || pdf.length == 0) {
            log.warn("Skip APPOINTMENT_CONFIRMED mail: PDF rỗng cho appointment id={}, key={}",
                    event.appointmentId(), event.pdfS3Key());
            notifyAppointmentMailAttachmentFailed(appointment, event.pdfS3Key(), "PDF đính kèm rỗng hoặc không hợp lệ");
            throw new IllegalStateException("PDF đính kèm rỗng hoặc không hợp lệ");
        }

        String html = EmailTemplateRenderer.render(new EmailTemplate(
                "Lịch khám PrimeCare của bạn đã được xác nhận",
                "Xin chào " + PdfFormatters.safeText(event.patientFullName(), "Quý khách") + ",",
                "Lịch khám của bạn đã được xác nhận. Phiếu hẹn PDF được đính kèm trong email này để bạn sử dụng khi đến cơ sở khám.",
                appointmentRows(appointment, "Đã xác nhận"),
                List.of(
                        "Vui lòng đến trước giờ hẹn 10-15 phút.",
                        "Mang theo CCCD/thẻ BHYT và hồ sơ y tế liên quan nếu có.",
                        "Nếu cần đổi hoặc hủy lịch, vui lòng liên hệ hotline của cơ sở."
                ),
                new EmailTemplate.Cta("Xem chi tiết lịch hẹn", lookupUrl(appointment.getCode())),
                "PrimeCare chỉ gửi thông tin cần thiết trong email này để bảo vệ quyền riêng tư của bạn."
        ));

        emailNotificationService.sendHtmlEmail(
                appointment,
                event.patientEmail(),
                "Lịch khám PrimeCare của bạn đã được xác nhận",
                html,
                "APPOINTMENT_CONFIRMED",
                pdf,
                "phieu-hen-" + safeFilename(event.appointmentCode()) + ".pdf"
        );
    }

    @RabbitListener(queues = RabbitMqConfig.APPOINTMENT_REMINDER_QUEUE)
    public void handleAppointmentReminder(AppointmentReminderMailEvent event) {
        if (event == null || event.appointmentId() == null) {
            log.warn("Skip APPOINTMENT_REMINDER sms: event hoặc appointmentId bị null. event={}", event);
            return;
        }

        Appointment appointment = appointmentRepository.findById(event.appointmentId()).orElse(null);
        if (appointment == null) {
            log.warn("Skip APPOINTMENT_REMINDER sms: không tìm thấy appointment id={}", event.appointmentId());
            return;
        }

        if (appointment.getPatientPhone() == null || appointment.getPatientPhone().isBlank()) {
            log.warn("Skip APPOINTMENT_REMINDER sms: thiếu patientPhone cho appointment id={}", event.appointmentId());
            return;
        }

        String smsContent = "PrimeCare nhắc lịch: %s, bạn có lịch %s với BS %s tại %s vào %s %s-%s. Vui lòng đến sớm 10-15 phút.".formatted(
                PdfFormatters.safeText(event.patientFullName(), "Quý khách"),
                PdfFormatters.safeText(event.appointmentCode(), ""),
                PdfFormatters.safeText(event.doctorName(), "Đang cập nhật"),
                PdfFormatters.safeText(event.branchName(), "PrimeCare"),
                PdfFormatters.safeText(event.visitDate(), "Đang cập nhật"),
                PdfFormatters.safeText(event.slotStart(), ""),
                PdfFormatters.safeText(event.slotEnd(), "")
        );

        smsNotificationService.sendSms(
                appointment,
                appointment.getPatientPhone(),
                smsContent,
                "APPOINTMENT_REMINDER"
        );
    }

    @RabbitListener(queues = RabbitMqConfig.RESULT_READY_MAIL_QUEUE)
    public void handleResultReady(ResultReadyMailEvent event) {
        if (event == null || event.appointmentId() == null) {
            log.warn("Skip RESULT_READY mail: event hoặc appointmentId bị null. event={}", event);
            return;
        }

        Appointment appointment = appointmentRepository.findById(event.appointmentId()).orElse(null);
        if (appointment == null) {
            log.warn("Skip RESULT_READY mail: không tìm thấy appointment id={}", event.appointmentId());
            return;
        }

        if (event.patientEmail() == null || event.patientEmail().isBlank()) {
            log.warn("Skip RESULT_READY mail: thiếu patientEmail cho appointment id={}", event.appointmentId());
            return;
        }

        String lookupCode = event.encounterCode() != null && !event.encounterCode().isBlank()
                ? event.encounterCode()
                : event.appointmentCode();

        String html = EmailTemplateRenderer.render(new EmailTemplate(
                "Kết quả khám/cận lâm sàng của bạn đã sẵn sàng",
                "Xin chào " + PdfFormatters.safeText(event.patientFullName(), "Quý khách") + ",",
                "PrimeCare đã có kết quả cho lượt khám của bạn. Để bảo vệ thông tin y tế cá nhân, bạn cần xác thực trước khi xem kết quả.",
                List.of(
                        new EmailTemplate.Row("Mã tra cứu", lookupCode),
                        new EmailTemplate.Row("Bác sĩ phụ trách", event.doctorName()),
                        new EmailTemplate.Row("Cơ sở khám", event.branchName()),
                        new EmailTemplate.Row("Hình thức xác thực", "OTP gửi đến email hoặc số điện thoại đã đăng ký")
                ),
                List.of(
                        "Không chia sẻ mã tra cứu hoặc mã OTP cho người không liên quan.",
                        "Nội dung kết quả chi tiết chỉ hiển thị sau khi xác thực thành công."
                ),
                new EmailTemplate.Cta("Tra cứu kết quả", lookupUrl(lookupCode)),
                "Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email hoặc liên hệ PrimeCare để được hỗ trợ."
        ));

        emailNotificationService.sendHtmlEmail(
                appointment,
                event.patientEmail(),
                "Kết quả khám/cận lâm sàng của bạn đã sẵn sàng",
                html,
                "RESULT_READY",
                null,
                null
        );
    }

    private List<EmailTemplate.Row> appointmentRows(Appointment appointment, String status) {
        String specialty = appointment.getSpecialty() != null
                ? PdfFormatters.firstNonBlank(appointment.getSpecialty().getNameVn(), appointment.getSpecialty().getNameEn())
                : null;
        String branchName = appointment.getBranch() != null
                ? PdfFormatters.firstNonBlank(appointment.getBranch().getNameVn(), appointment.getBranch().getNameEn())
                : null;
        String address = appointment.getBranch() != null
                ? PdfFormatters.firstNonBlank(appointment.getBranch().getAddressVn(), appointment.getBranch().getAddressEn())
                : null;
        return List.of(
                new EmailTemplate.Row("Mã lịch hẹn", appointment.getCode()),
                new EmailTemplate.Row("Ngày khám", PdfFormatters.formatDate(appointment.getVisitDate())),
                new EmailTemplate.Row("Giờ dự kiến", timeWindow(appointment)),
                new EmailTemplate.Row("Chuyên khoa", specialty),
                new EmailTemplate.Row("Bác sĩ", appointment.getDoctor() != null ? appointment.getDoctor().getFullName() : "Đang cập nhật"),
                new EmailTemplate.Row("Cơ sở khám", branchName),
                new EmailTemplate.Row("Địa chỉ", address),
                new EmailTemplate.Row("Hotline", appointment.getBranch() != null ? appointment.getBranch().getPhone() : null),
                new EmailTemplate.Row("Trạng thái", status)
        );
    }

    private String timeWindow(Appointment appointment) {
        String start = PdfFormatters.formatTime(appointment.getEtaStart());
        String end = PdfFormatters.formatTime(appointment.getEtaEnd());
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

    private String lookupUrl(String code) {
        String base = frontendBaseUrl == null || frontendBaseUrl.isBlank()
                ? "http://localhost:8081"
                : frontendBaseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String url = base + "/appointments/lookup";
        if (code != null && !code.isBlank()) {
            url += "?code=" + URLEncoder.encode(code.trim(), StandardCharsets.UTF_8);
        }
        return url;
    }

    private String safeFilename(String value) {
        String safe = value == null || value.isBlank() ? "lich-hen" : value.trim();
        return safe.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private void notifyAppointmentMailAttachmentFailed(Appointment appointment, String pdfS3Key, String error) {
        internalNotificationService.notifyAdminRolesOnceRequiresNew(
                "APPOINTMENT_MAIL_ATTACHMENT_FAILED",
                InternalNotificationService.SEVERITY_CRITICAL,
                "Gửi email phiếu hẹn thất bại",
                "Gửi email phiếu hẹn thất bại do không tải được PDF đính kèm.",
                "/app/admin/notifications",
                "APPOINTMENT",
                appointment.getId(),
                Map.of(
                        "appointmentCode", appointment.getCode() != null ? appointment.getCode() : "",
                        "pdfS3Key", pdfS3Key != null ? pdfS3Key : "",
                        "error", error != null ? error : ""
                )
        );
    }
}
