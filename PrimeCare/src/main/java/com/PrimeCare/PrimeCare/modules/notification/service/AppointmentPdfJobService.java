package com.PrimeCare.PrimeCare.modules.notification.service;

import com.PrimeCare.PrimeCare.config.RabbitMqConfig;
import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.notification.entity.AppointmentPdfJob;
import com.PrimeCare.PrimeCare.modules.notification.messaging.event.AppointmentConfirmedPdfRequestedEvent;
import com.PrimeCare.PrimeCare.modules.notification.repository.AppointmentPdfJobRepository;
import com.PrimeCare.PrimeCare.modules.realtime.service.AfterCommitExecutor;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentPdfJobStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentPdfJobService {

    private final AppointmentPdfJobRepository jobRepository;
    private final RabbitTemplate rabbitTemplate;
    private final AfterCommitExecutor afterCommitExecutor;
    private final InternalNotificationService internalNotificationService;

    @Transactional
    public AppointmentPdfJob requestGenerate(Appointment appointment) {
        AppointmentPdfJob job = jobRepository.findByAppointmentId(appointment.getId())
                                             .orElseGet(() -> jobRepository.save(
                                                     AppointmentPdfJob.builder()
                                                                      .appointmentId(appointment.getId())
                                                                      .status(AppointmentPdfJobStatus.PENDING)
                                                                      .build()
                                             ));

        job.setStatus(AppointmentPdfJobStatus.PENDING);
        job.setFilePath(null);
        job.setErrorMessage(null);
        jobRepository.save(job);

        afterCommitExecutor.execute(() -> {
            try {
                rabbitTemplate.convertAndSend(
                        RabbitMqConfig.APPOINTMENT_EXCHANGE,
                        RabbitMqConfig.APPOINTMENT_CONFIRMED_PDF_ROUTING_KEY,
                        new AppointmentConfirmedPdfRequestedEvent(
                                appointment.getId(),
                                appointment.getPatientEmail(),
                                appointment.getPatientFullName(),
                                appointment.getCode(),
                                appointment.getBranch().getNameVn(),
                                appointment.getDoctor().getFullName(),
                                appointment.getSpecialty().getNameVn(),
                                appointment.getVisitDate().toString(),
                                appointment.getSession().name(),
                                appointment.getEtaStart() != null ? appointment.getEtaStart().toString() : "",
                                appointment.getEtaEnd() != null ? appointment.getEtaEnd().toString() : ""
                        )
                );
            } catch (Exception ex) {
                log.error("Publish appointment PDF request failed for appointment {}", appointment.getId(), ex);
                jobRepository.findById(job.getId()).ifPresent(failedJob -> {
                    failedJob.setStatus(AppointmentPdfJobStatus.FAILED);
                    failedJob.setErrorMessage(ex.getMessage());
                    jobRepository.save(failedJob);
                });
                internalNotificationService.notifyAdminRolesOnceRequiresNew(
                        "APPOINTMENT_PDF_JOB_FAILED",
                        InternalNotificationService.SEVERITY_CRITICAL,
                        "Tạo PDF phiếu hẹn thất bại",
                        "Không thể đưa lịch hẹn " + appointment.getCode() + " vào hàng đợi tạo PDF.",
                        "/app/admin/notifications",
                        "APPOINTMENT",
                        appointment.getId(),
                        java.util.Map.of("appointmentCode", appointment.getCode(), "error", ex.getMessage() != null ? ex.getMessage() : "")
                );
            }
        });

        return job;
    }
}
