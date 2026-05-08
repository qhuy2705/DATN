package com.PrimeCare.PrimeCare.modules.notification.messaging;

import com.PrimeCare.PrimeCare.config.RabbitMqConfig;
import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.appointment.service.AppointmentCheckInTokenService;
import com.PrimeCare.PrimeCare.modules.file.service.FileStorageService;
import com.PrimeCare.PrimeCare.modules.realtime.service.AfterCommitExecutor;
import com.PrimeCare.PrimeCare.modules.notification.entity.AppointmentPdfJob;
import com.PrimeCare.PrimeCare.modules.notification.messaging.event.AppointmentConfirmedMailReadyEvent;
import com.PrimeCare.PrimeCare.modules.notification.messaging.event.AppointmentConfirmedPdfRequestedEvent;
import com.PrimeCare.PrimeCare.modules.notification.repository.AppointmentPdfJobRepository;
import com.PrimeCare.PrimeCare.modules.notification.service.AppointmentPdfService;
import com.PrimeCare.PrimeCare.modules.notification.service.InternalNotificationService;
import com.PrimeCare.PrimeCare.modules.notification.service.QrCodeService;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentPdfJobStatus;
import com.PrimeCare.PrimeCare.shared.enums.FileOwnerType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class AppointmentPdfConsumer {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentPdfJobRepository jobRepository;
    private final AppointmentCheckInTokenService appointmentCheckInTokenService;
    private final QrCodeService qrCodeService;
    private final AppointmentPdfService appointmentPdfService;
    private final RabbitTemplate rabbitTemplate;
    private final FileStorageService fileStorageService;
    private final AfterCommitExecutor afterCommitExecutor;
    private final InternalNotificationService internalNotificationService;

    @RabbitListener(queues = RabbitMqConfig.APPOINTMENT_CONFIRMED_PDF_QUEUE)
    @Transactional
    public void consume(AppointmentConfirmedPdfRequestedEvent event) {
        Appointment appointment = appointmentRepository.findById(event.appointmentId())
                                                       .orElseThrow(() -> new IllegalStateException("Appointment not found: " + event.appointmentId()));

        AppointmentPdfJob job = jobRepository.findByAppointmentId(event.appointmentId())
                                             .orElseThrow(() -> new IllegalStateException("Appointment PDF job not found for appointment: " + event.appointmentId()));

        try {
            job.setStatus(AppointmentPdfJobStatus.PROCESSING);
            job.setErrorMessage(null);
            jobRepository.save(job);

            String qrToken = appointmentCheckInTokenService.issueToken(appointment);
            byte[] qrPng = qrCodeService.generatePng(qrToken, 280, 280);
            byte[] pdf = appointmentPdfService.generateAppointmentPdf(appointment, qrPng);

            String s3Key = fileStorageService.uploadPdfBytes(
                    pdf,
                    FileOwnerType.APPOINTMENT,
                    appointment.getId(),
                    "appointment-" + event.appointmentCode() + ".pdf"
            );

            job.setStatus(AppointmentPdfJobStatus.COMPLETED);
            job.setFilePath(s3Key);
            job.setErrorMessage(null);
            jobRepository.save(job);

            afterCommitExecutor.execute(() -> rabbitTemplate.convertAndSend(
                    RabbitMqConfig.APPOINTMENT_EXCHANGE,
                    RabbitMqConfig.APPOINTMENT_CONFIRMED_MAIL_READY_ROUTING_KEY,
                    new AppointmentConfirmedMailReadyEvent(
                            event.appointmentId(),
                            job.getId(),
                            event.patientEmail(),
                            event.patientFullName(),
                            event.appointmentCode(),
                            event.branchName(),
                            event.doctorName(),
                            event.specialtyName(),
                            event.visitDate(),
                            event.session(),
                            event.slotStart(),
                            event.slotEnd(),
                            s3Key
                    )
            ));
        } catch (Exception ex) {
            log.error("Generate appointment pdf failed for appointment {}", event.appointmentId(), ex);
            job.setStatus(AppointmentPdfJobStatus.FAILED);
            job.setErrorMessage(ex.getMessage());
            jobRepository.save(job);
            internalNotificationService.notifyAdminRolesOnceRequiresNew(
                    "APPOINTMENT_PDF_GENERATION_FAILED",
                    InternalNotificationService.SEVERITY_CRITICAL,
                    "Tạo PDF phiếu hẹn thất bại",
                    "Tạo PDF cho lịch hẹn " + appointment.getCode() + " thất bại.",
                    "/app/admin/notifications",
                    "APPOINTMENT",
                    appointment.getId(),
                    java.util.Map.of("appointmentCode", appointment.getCode(), "error", ex.getMessage() != null ? ex.getMessage() : "")
            );
            throw ex;
        }
    }
}
