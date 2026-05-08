package com.PrimeCare.PrimeCare.modules.appointment.listener;

import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.event.AppointmentCreatedSpringEvent;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.notification.messaging.AppointmentMailEventPublisher;
import com.PrimeCare.PrimeCare.modules.notification.messaging.event.AppointmentCreatedMailEvent;
import com.PrimeCare.PrimeCare.modules.notification.service.InternalNotificationService;
import com.PrimeCare.PrimeCare.modules.realtime.service.RealtimeEventPublisher;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class AppointmentPostCommitListener {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentMailEventPublisher appointmentMailEventPublisher;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final InternalNotificationService internalNotificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAppointmentCreated(AppointmentCreatedSpringEvent event) {
        if (event == null || event.appointmentId() == null) {
            log.warn("Skip appointment-created post-commit event because appointmentId is missing. event={}", event);
            return;
        }

        Appointment appointment = appointmentRepository.findById(event.appointmentId())
                .orElse(null);
        if (appointment == null) {
            log.warn("Skip appointment-created post-commit event because appointment was not found. appointmentId={}",
                    event.appointmentId());
            return;
        }

        publishRealtime(appointment);
        notifyStaff(appointment);
        publishCreatedMail(appointment);
    }

    private void publishRealtime(Appointment appointment) {
        try {
            if (appointment.getBranch() == null || appointment.getVisitDate() == null) {
                log.warn("Skip appointment created realtime because branch/date is missing. appointmentId={}",
                        appointment.getId());
                return;
            }

            Long branchId = appointment.getBranch().getId();
            realtimeEventPublisher.publishAppointmentSummaryChanged(branchId, appointment.getVisitDate());
            realtimeEventPublisher.publishAppointmentUpdated(
                    appointment.getId(),
                    branchId,
                    appointment.getDoctor() != null ? appointment.getDoctor().getId() : null,
                    appointment.getVisitDate(),
                    appointment.getSession() != null ? appointment.getSession().name() : null,
                    null,
                    appointment.getStatus() != null ? appointment.getStatus().name() : null,
                    appointment.getArrivalStatus() != null ? appointment.getArrivalStatus().name() : null,
                    appointment.getQueueNo(),
                    appointment.getReceptionQueueNo(),
                    appointment.getArrivedAt() != null ? appointment.getArrivedAt().toString() : null,
                    null,
                    null,
                    null,
                    appointment.getConfirmedAt() != null ? appointment.getConfirmedAt().toString() : null,
                    null
            );
        } catch (Exception ex) {
            log.error("Failed to publish appointment-created realtime. appointmentId={}", appointment.getId(), ex);
        }
    }

    private void notifyStaff(Appointment appointment) {
        try {
            internalNotificationService.notifyRole(
                    UserRole.STAFF,
                    "APPOINTMENT_REQUESTED",
                    InternalNotificationService.SEVERITY_INFO,
                    "Có lịch hẹn mới cần xác nhận",
                    "Bệnh nhân " + appointment.getPatientFullName() + " vừa đặt lịch công khai.",
                    "/app/appointments/" + appointment.getId() + "/process",
                    "APPOINTMENT",
                    appointment.getId()
            );
            internalNotificationService.notifyRole(
                    UserRole.OPERATIONS_ADMIN,
                    "APPOINTMENT_REQUESTED",
                    InternalNotificationService.SEVERITY_INFO,
                    "Có lịch hẹn mới cần xác nhận",
                    "Bệnh nhân " + appointment.getPatientFullName() + " vừa đặt lịch công khai.",
                    "/app/appointments/" + appointment.getId() + "/process",
                    "APPOINTMENT",
                    appointment.getId()
            );
        } catch (Exception ex) {
            log.error("Failed to create appointment-created internal notification. appointmentId={}",
                    appointment.getId(), ex);
        }
    }

    private void publishCreatedMail(Appointment appointment) {
        if (appointment.getPatientEmail() == null || appointment.getPatientEmail().isBlank()) {
            return;
        }

        try {
            appointmentMailEventPublisher.publishCreated(
                    AppointmentCreatedMailEvent.builder()
                            .appointmentId(appointment.getId())
                            .patientEmail(appointment.getPatientEmail())
                            .patientFullName(appointment.getPatientFullName())
                            .appointmentCode(appointment.getCode())
                            .branchName(appointment.getBranch() != null ? appointment.getBranch().getNameVn() : null)
                            .doctorName(appointment.getDoctor() != null ? appointment.getDoctor().getFullName() : null)
                            .visitDate(appointment.getVisitDate() != null ? appointment.getVisitDate().toString() : null)
                            .session(appointment.getSession() != null ? appointment.getSession().name() : null)
                            .slotStart(appointment.getEtaStart() != null ? appointment.getEtaStart().toString() : "")
                            .slotEnd(appointment.getEtaEnd() != null ? appointment.getEtaEnd().toString() : "")
                            .build()
            );
        } catch (Exception ex) {
            log.error("Failed to publish appointment-created mail event. appointmentId={}",
                    appointment.getId(), ex);
        }
    }
}
