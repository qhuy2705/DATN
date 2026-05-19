package com.PrimeCare.PrimeCare.modules.notification.messaging;

import com.PrimeCare.PrimeCare.config.RabbitMqConfig;
import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.entity.AppointmentSlotHold;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentSlotHoldRepository;
import com.PrimeCare.PrimeCare.modules.file.service.FileStorageService;
import com.PrimeCare.PrimeCare.modules.notification.messaging.event.AppointmentConfirmedMailReadyEvent;
import com.PrimeCare.PrimeCare.modules.notification.messaging.event.AppointmentCreatedMailEvent;
import com.PrimeCare.PrimeCare.modules.notification.messaging.event.AppointmentRescheduleOfferMailEvent;
import com.PrimeCare.PrimeCare.modules.notification.messaging.event.AppointmentReminderMailEvent;
import com.PrimeCare.PrimeCare.modules.notification.messaging.event.ResultReadyMailEvent;
import com.PrimeCare.PrimeCare.modules.notification.messaging.event.SlotHoldAcceptedMailEvent;
import com.PrimeCare.PrimeCare.modules.notification.messaging.event.SlotHoldCancelledMailEvent;
import com.PrimeCare.PrimeCare.modules.notification.messaging.event.SlotHoldReminderMailEvent;
import com.PrimeCare.PrimeCare.modules.notification.service.EmailNotificationService;
import com.PrimeCare.PrimeCare.modules.notification.service.InternalNotificationService;
import com.PrimeCare.PrimeCare.shared.email.EmailTemplate;
import com.PrimeCare.PrimeCare.shared.email.EmailTemplateRenderer;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentSlotHoldStatus;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.pdf.PdfFormatters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentMailConsumer {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentSlotHoldRepository slotHoldRepository;
    private final EmailNotificationService emailNotificationService;
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
                        "Email này xác nhận PrimeCare đã nhận yêu cầu đặt lịch, chưa phải xác nhận cuối cùng nếu lịch cần nhân viên duyệt.",
                        "Bạn sẽ nhận email xác nhận khi lịch được xác nhận.",
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

        LocalDateTime appointmentStart = appointmentStart(appointment);
        String cancellationDeadline = cancellationDeadlineText(appointmentStart);

        List<String> notes = new ArrayList<>();
        notes.add("Vui lòng đến trước giờ hẹn 10-15 phút.");
        notes.add("Mang theo CCCD/thẻ BHYT và hồ sơ y tế liên quan nếu có.");
        if (appointmentStart != null && cancellationDeadline != null) {
            LocalDateTime deadline = appointmentStart.minusHours(4);
            if (!LocalDateTime.now().isBefore(deadline)) {
                notes.add("Lịch khám này không còn trong thời gian hỗ trợ tự hủy online. Nếu cần thay đổi, vui lòng liên hệ cơ sở khám.");
            } else {
                notes.add("Bạn có thể tự hủy lịch trước " + cancellationDeadline + ". Sau thời điểm này, vui lòng liên hệ cơ sở khám để được hỗ trợ.");
            }
        } else {
            notes.add("Nếu cần đổi hoặc hủy lịch, vui lòng liên hệ hotline của cơ sở.");
        }
        notes.add("Thông tin y tế chi tiết sẽ không được gửi qua email để bảo vệ quyền riêng tư của bạn.");

        String html = EmailTemplateRenderer.render(new EmailTemplate(
                "Lịch khám PrimeCare của bạn đã được xác nhận",
                "Xin chào " + PdfFormatters.safeText(event.patientFullName(), "Quý khách") + ",",
                "Lịch khám của bạn đã được xác nhận. Phiếu hẹn PDF được đính kèm trong email này để bạn sử dụng khi đến cơ sở khám.",
                appointmentRows(appointment, "Đã xác nhận", cancellationDeadline),
                notes,
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
            log.warn("Skip APPOINTMENT_REMINDER email: event hoặc appointmentId bị null. event={}", event);
            return;
        }

        Appointment appointment = appointmentRepository.findById(event.appointmentId()).orElse(null);
        if (appointment == null) {
            log.warn("Skip APPOINTMENT_REMINDER email: không tìm thấy appointment id={}", event.appointmentId());
            return;
        }

        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            log.warn("Skip APPOINTMENT_REMINDER email: appointment id={} status={} không còn CONFIRMED",
                    event.appointmentId(), appointment.getStatus());
            return;
        }

        String recipientEmail = PdfFormatters.firstNonBlank(event.patientEmail(), appointment.getPatientEmail());
        if (recipientEmail == null) {
            log.warn("Skip APPOINTMENT_REMINDER email: thiếu patientEmail cho appointment id={}", event.appointmentId());
            return;
        }

        if (!isSupportedReminderTemplate(event.templateCode())) {
            log.warn("Skip APPOINTMENT_REMINDER email: templateCode không hợp lệ cho appointment id={}, templateCode={}",
                    event.appointmentId(), event.templateCode());
            return;
        }

        if (appointment.getVisitDate() == null || appointment.getEtaStart() == null) {
            log.warn("Skip APPOINTMENT_REMINDER email: thiếu visitDate/etaStart cho appointment id={}", event.appointmentId());
            return;
        }

        String reminderKind = reminderKind(event);
        String subject = "6H".equals(reminderKind)
                ? "PrimeCare nhắc bạn có lịch khám trong khoảng 6 giờ tới"
                : "PrimeCare nhắc bạn có lịch khám vào ngày mai";
        String intro = "6H".equals(reminderKind)
                ? "PrimeCare nhắc bạn có lịch khám trong khoảng 6 giờ tới. Nếu không thể đến khám, vui lòng hủy hoặc liên hệ phòng khám sớm."
                : "PrimeCare nhắc bạn có lịch khám vào ngày mai. Vui lòng kiểm tra thời gian, địa điểm và chuẩn bị giấy tờ cần thiết.";
        List<String> notes = "6H".equals(reminderKind)
                ? List.of(
                        "Hệ thống chỉ hỗ trợ tự hủy lịch trước giờ khám ít nhất 4 tiếng.",
                        "Nếu không thể đến khám, vui lòng xử lý sớm trước hạn tự hủy.",
                        "Sau hạn tự hủy, vui lòng liên hệ cơ sở khám để được hỗ trợ."
                )
                : List.of(
                        "Vui lòng đến trước giờ hẹn 10-15 phút.",
                        "Mang theo CCCD/thẻ BHYT và hồ sơ y tế liên quan nếu có.",
                        "Bạn có thể tự hủy lịch trước hạn cuối hiển thị bên trên."
                );

        String html = EmailTemplateRenderer.render(new EmailTemplate(
                subject,
                "Xin chào " + PdfFormatters.safeText(event.patientFullName(), "Quý khách") + ",",
                intro,
                appointmentReminderRows(appointment, event),
                notes,
                new EmailTemplate.Cta("Xem chi tiết lịch hẹn", lookupUrl(appointment.getCode())),
                "Đây là email nhắc lịch tự động từ PrimeCare."
        ));

        emailNotificationService.sendHtmlEmail(
                appointment,
                recipientEmail,
                subject,
                html,
                event.templateCode(),
                null,
                null
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

    @RabbitListener(queues = RabbitMqConfig.APPOINTMENT_RESCHEDULE_OFFER_QUEUE)
    public void handleRescheduleOffer(AppointmentRescheduleOfferMailEvent event) {
        if (event == null || event.slotHoldId() == null || event.responseToken() == null || event.responseToken().isBlank()) {
            log.warn("Skip RESCHEDULE_OFFER mail: event thiếu dữ liệu. event={}", event);
            return;
        }
        AppointmentSlotHold hold = slotHoldRepository.findDetailById(event.slotHoldId()).orElse(null);
        if (hold == null || hold.getOriginalAppointment() == null) {
            log.warn("Skip RESCHEDULE_OFFER mail: không tìm thấy slotHold id={}", event.slotHoldId());
            return;
        }
        Appointment original = hold.getOriginalAppointment();
        String recipientEmail = PdfFormatters.firstNonBlank(original.getPatientEmail(), hold.getPatient() != null ? hold.getPatient().getEmail() : null);
        if (recipientEmail == null) {
            log.warn("Skip RESCHEDULE_OFFER mail: thiếu patientEmail cho slotHold id={}", event.slotHoldId());
            return;
        }

        String html = EmailTemplateRenderer.render(new EmailTemplate(
                "PrimeCare đề xuất lịch khám thay thế",
                "Xin chào " + PdfFormatters.safeText(original.getPatientFullName(), "Quý khách") + ",",
                "Lịch khám cũ của bạn bị ảnh hưởng do bác sĩ/phòng khám không thể phục vụ khung giờ đó. PrimeCare đã tạm giữ một slot thay thế trong 12 giờ để bạn lựa chọn.",
                rescheduleRows(original, hold),
                List.of(
                        "Bạn có thể chấp nhận lịch mới, yêu cầu lễ tân liên hệ hoặc hủy khám trên trang xác nhận.",
                        "Slot thay thế chỉ được giữ đến hạn phản hồi hiển thị trong email này.",
                        "Nếu bạn không phản hồi trước hạn, PrimeCare sẽ chuyển case sang lễ tân để follow-up, không coi là bạn tự hủy khám."
                ),
                new EmailTemplate.Cta("Xem lựa chọn dời lịch", rescheduleUrl(event.responseToken())),
                "PrimeCare chỉ gửi thông tin cần thiết trong email này để bảo vệ quyền riêng tư của bạn."
        ));

        emailNotificationService.sendHtmlEmail(
                original,
                recipientEmail,
                "PrimeCare đề xuất lịch khám thay thế",
                html,
                "APPOINTMENT_RESCHEDULE_OFFER",
                null,
                null
        );
    }

    @RabbitListener(queues = RabbitMqConfig.SLOT_HOLD_REMINDER_QUEUE)
    public void handleSlotHoldReminder(SlotHoldReminderMailEvent event) {
        if (event == null || event.slotHoldId() == null || event.responseToken() == null || event.responseToken().isBlank()) {
            log.warn("Skip SLOT_HOLD_REMINDER mail: event thiếu dữ liệu. event={}", event);
            return;
        }
        AppointmentSlotHold hold = slotHoldRepository.findDetailById(event.slotHoldId()).orElse(null);
        if (hold == null || hold.getOriginalAppointment() == null) {
            log.warn("Skip SLOT_HOLD_REMINDER mail: không tìm thấy slotHold id={}", event.slotHoldId());
            return;
        }
        if (hold.getStatus() != AppointmentSlotHoldStatus.HELD) {
            log.warn("Skip SLOT_HOLD_REMINDER mail: slotHold id={} status={}", hold.getId(), hold.getStatus());
            return;
        }
        Appointment original = hold.getOriginalAppointment();
        String recipientEmail = PdfFormatters.firstNonBlank(original.getPatientEmail(), hold.getPatient() != null ? hold.getPatient().getEmail() : null);
        if (recipientEmail == null) {
            log.warn("Skip SLOT_HOLD_REMINDER mail: thiếu patientEmail cho slotHold id={}", event.slotHoldId());
            return;
        }

        String html = EmailTemplateRenderer.render(new EmailTemplate(
                "Slot thay thế PrimeCare sắp hết hạn",
                "Xin chào " + PdfFormatters.safeText(original.getPatientFullName(), "Quý khách") + ",",
                "PrimeCare nhắc bạn slot khám thay thế đang được giữ sẽ hết hạn trong khoảng 2 giờ tới. Vui lòng xác nhận lựa chọn để chúng tôi hỗ trợ đúng nhu cầu của bạn.",
                rescheduleRows(original, hold),
                List.of(
                        "Bạn có thể chấp nhận lịch mới, yêu cầu lễ tân liên hệ hoặc hủy khám trên trang xác nhận.",
                        "Nếu hết hạn mà chưa có phản hồi, lễ tân PrimeCare sẽ follow-up case này."
                ),
                new EmailTemplate.Cta("Phản hồi lịch thay thế", rescheduleUrl(event.responseToken())),
                "Đây là email nhắc tự động từ PrimeCare."
        ));

        emailNotificationService.sendHtmlEmail(
                original,
                recipientEmail,
                "Slot thay thế PrimeCare sắp hết hạn",
                html,
                "SLOT_HOLD_REMINDER",
                null,
                null
        );
    }

    @RabbitListener(queues = RabbitMqConfig.SLOT_HOLD_ACCEPTED_QUEUE)
    public void handleSlotHoldAccepted(SlotHoldAcceptedMailEvent event) {
        if (event == null || event.newAppointmentId() == null) {
            log.warn("Skip SLOT_HOLD_ACCEPTED mail: event thiếu dữ liệu. event={}", event);
            return;
        }
        Appointment appointment = appointmentRepository.findById(event.newAppointmentId()).orElse(null);
        if (appointment == null || appointment.getPatientEmail() == null || appointment.getPatientEmail().isBlank()) {
            log.warn("Skip SLOT_HOLD_ACCEPTED mail: không tìm thấy appointment/email id={}", event.newAppointmentId());
            return;
        }

        String html = EmailTemplateRenderer.render(new EmailTemplate(
                "Lịch khám thay thế đã được xác nhận",
                "Xin chào " + PdfFormatters.safeText(appointment.getPatientFullName(), "Quý khách") + ",",
                "PrimeCare đã tạo lịch khám mới theo slot thay thế bạn vừa chấp nhận.",
                appointmentRows(appointment, "Đã xác nhận"),
                List.of(
                        "Vui lòng đến trước giờ hẹn 10-15 phút.",
                        "Mang theo CCCD/thẻ BHYT và hồ sơ y tế liên quan nếu có."
                ),
                new EmailTemplate.Cta("Xem chi tiết lịch hẹn", lookupUrl(appointment.getCode())),
                "Cảm ơn bạn đã phản hồi lịch thay thế của PrimeCare."
        ));

        emailNotificationService.sendHtmlEmail(
                appointment,
                appointment.getPatientEmail(),
                "Lịch khám thay thế đã được xác nhận",
                html,
                "SLOT_HOLD_ACCEPTED",
                null,
                null
        );
    }

    @RabbitListener(queues = RabbitMqConfig.SLOT_HOLD_CANCELLED_QUEUE)
    public void handleSlotHoldCancelled(SlotHoldCancelledMailEvent event) {
        if (event == null || event.slotHoldId() == null) {
            log.warn("Skip SLOT_HOLD_CANCELLED mail: event thiếu dữ liệu. event={}", event);
            return;
        }
        AppointmentSlotHold hold = slotHoldRepository.findDetailById(event.slotHoldId()).orElse(null);
        if (hold == null || hold.getOriginalAppointment() == null) {
            log.warn("Skip SLOT_HOLD_CANCELLED mail: không tìm thấy slotHold id={}", event.slotHoldId());
            return;
        }
        Appointment original = hold.getOriginalAppointment();
        String recipientEmail = PdfFormatters.firstNonBlank(original.getPatientEmail(), hold.getPatient() != null ? hold.getPatient().getEmail() : null);
        if (recipientEmail == null) {
            log.warn("Skip SLOT_HOLD_CANCELLED mail: thiếu patientEmail cho slotHold id={}", event.slotHoldId());
            return;
        }

        String html = EmailTemplateRenderer.render(new EmailTemplate(
                "PrimeCare đã hủy slot giữ tạm",
                "Xin chào " + PdfFormatters.safeText(original.getPatientFullName(), "Quý khách") + ",",
                "PrimeCare đã ghi nhận yêu cầu hủy khám và nhả slot thay thế đang được giữ tạm.",
                rescheduleRows(original, hold),
                List.of("Lịch cũ vẫn đang ở trạng thái đã hủy do bác sĩ/phòng khám không thể phục vụ."),
                null,
                "Nếu bạn cần đặt lịch mới, vui lòng truy cập kênh đặt lịch PrimeCare hoặc liên hệ cơ sở khám."
        ));

        emailNotificationService.sendHtmlEmail(
                original,
                recipientEmail,
                "PrimeCare đã hủy slot giữ tạm",
                html,
                "SLOT_HOLD_CANCELLED",
                null,
                null
        );
    }

    private List<EmailTemplate.Row> appointmentRows(Appointment appointment, String status) {
        return appointmentRows(appointment, status, null);
    }

    private List<EmailTemplate.Row> appointmentRows(Appointment appointment, String status, String cancellationDeadline) {
        String specialty = appointment.getSpecialty() != null
                ? PdfFormatters.firstNonBlank(appointment.getSpecialty().getNameVn(), appointment.getSpecialty().getNameEn())
                : null;
        String branchName = appointment.getBranch() != null
                ? PdfFormatters.firstNonBlank(appointment.getBranch().getNameVn(), appointment.getBranch().getNameEn())
                : null;
        String address = appointment.getBranch() != null
                ? PdfFormatters.firstNonBlank(appointment.getBranch().getAddressVn(), appointment.getBranch().getAddressEn())
                : null;
        List<EmailTemplate.Row> rows = new ArrayList<>(List.of(
                new EmailTemplate.Row("Mã lịch hẹn", appointment.getCode()),
                new EmailTemplate.Row("Ngày khám", PdfFormatters.formatDate(appointment.getVisitDate())),
                new EmailTemplate.Row("Giờ dự kiến", timeWindow(appointment)),
                new EmailTemplate.Row("Chuyên khoa", specialty),
                new EmailTemplate.Row("Bác sĩ", appointment.getDoctor() != null ? appointment.getDoctor().getFullName() : "Đang cập nhật"),
                new EmailTemplate.Row("Cơ sở khám", branchName),
                new EmailTemplate.Row("Địa chỉ", address),
                new EmailTemplate.Row("Hotline", appointment.getBranch() != null ? appointment.getBranch().getPhone() : null),
                new EmailTemplate.Row("Trạng thái", status)
        ));
        if (cancellationDeadline != null && !cancellationDeadline.isBlank()) {
            rows.add(new EmailTemplate.Row("Hạn cuối tự hủy", cancellationDeadline));
        }
        return rows;
    }

    private List<EmailTemplate.Row> appointmentReminderRows(Appointment appointment, AppointmentReminderMailEvent event) {
        String branchName = event.branchName() != null && !event.branchName().isBlank()
                ? event.branchName()
                : (appointment.getBranch() != null
                        ? PdfFormatters.firstNonBlank(appointment.getBranch().getNameVn(), appointment.getBranch().getNameEn())
                        : null);
        String address = appointment.getBranch() != null
                ? PdfFormatters.firstNonBlank(appointment.getBranch().getAddressVn(), appointment.getBranch().getAddressEn())
                : null;
        return List.of(
                new EmailTemplate.Row("Mã lịch hẹn", PdfFormatters.firstNonBlank(event.appointmentCode(), appointment.getCode())),
                new EmailTemplate.Row("Ngày khám", PdfFormatters.formatDate(appointment.getVisitDate())),
                new EmailTemplate.Row("Giờ dự kiến", timeWindow(appointment)),
                new EmailTemplate.Row("Chuyên khoa", PdfFormatters.firstNonBlank(event.specialtyName(), specialtyName(appointment))),
                new EmailTemplate.Row("Bác sĩ", PdfFormatters.firstNonBlank(event.doctorName(), appointment.getDoctor() != null ? appointment.getDoctor().getFullName() : null)),
                new EmailTemplate.Row("Cơ sở khám", branchName),
                new EmailTemplate.Row("Địa chỉ", address),
                new EmailTemplate.Row("Hotline", appointment.getBranch() != null ? appointment.getBranch().getPhone() : null),
                new EmailTemplate.Row("Hạn cuối tự hủy", event.cancellationDeadline())
        );
    }

    private String specialtyName(Appointment appointment) {
        return appointment.getSpecialty() != null
                ? PdfFormatters.firstNonBlank(appointment.getSpecialty().getNameVn(), appointment.getSpecialty().getNameEn())
                : null;
    }

    private LocalDateTime appointmentStart(Appointment appointment) {
        if (appointment == null || appointment.getVisitDate() == null || appointment.getEtaStart() == null) {
            return null;
        }
        return LocalDateTime.of(appointment.getVisitDate(), appointment.getEtaStart());
    }

    private String cancellationDeadlineText(LocalDateTime appointmentStart) {
        return appointmentStart == null ? null : PdfFormatters.formatDateTime(appointmentStart.minusHours(4));
    }

    private boolean isSupportedReminderTemplate(String templateCode) {
        return "APPOINTMENT_REMINDER_24H".equals(templateCode)
                || "APPOINTMENT_REMINDER_6H".equals(templateCode);
    }

    private String reminderKind(AppointmentReminderMailEvent event) {
        if ("APPOINTMENT_REMINDER_6H".equals(event.templateCode()) || "6H".equalsIgnoreCase(event.reminderKind())) {
            return "6H";
        }
        return "24H";
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

    private List<EmailTemplate.Row> rescheduleRows(Appointment original, AppointmentSlotHold hold) {
        List<EmailTemplate.Row> rows = new ArrayList<>();
        rows.add(new EmailTemplate.Row("Mã lịch hẹn cũ", original.getCode()));
        rows.add(new EmailTemplate.Row("Lịch cũ", PdfFormatters.formatDate(original.getVisitDate()) + " " + timeWindow(original)));
        rows.add(new EmailTemplate.Row("Bác sĩ cũ", original.getDoctor() != null ? original.getDoctor().getFullName() : null));
        rows.add(new EmailTemplate.Row("Lịch thay thế", PdfFormatters.formatDate(hold.getVisitDate()) + " " + holdTimeWindow(hold)));
        rows.add(new EmailTemplate.Row("Bác sĩ thay thế", hold.getDoctor() != null ? hold.getDoctor().getFullName() : null));
        rows.add(new EmailTemplate.Row("Chuyên khoa", hold.getSpecialty() != null ? PdfFormatters.firstNonBlank(hold.getSpecialty().getNameVn(), hold.getSpecialty().getNameEn()) : null));
        rows.add(new EmailTemplate.Row("Cơ sở khám", hold.getBranch() != null ? PdfFormatters.firstNonBlank(hold.getBranch().getNameVn(), hold.getBranch().getNameEn()) : null));
        rows.add(new EmailTemplate.Row("Hạn phản hồi", PdfFormatters.formatDateTime(hold.getExpiresAt())));
        return rows;
    }

    private String holdTimeWindow(AppointmentSlotHold hold) {
        String start = PdfFormatters.formatTime(hold.getSlotStart());
        String end = PdfFormatters.formatTime(hold.getSlotEnd());
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

    private String rescheduleUrl(String token) {
        String base = frontendBaseUrl == null || frontendBaseUrl.isBlank()
                ? "http://localhost:8081"
                : frontendBaseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/reschedule/" + URLEncoder.encode(token, StandardCharsets.UTF_8);
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
                "/app/admin/dashboard",
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
