package com.PrimeCare.PrimeCare.modules.realtime.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RealtimeEventPublisher {

    private final SimpMessagingTemplate simpMessagingTemplate;
    private final AfterCommitExecutor afterCommitExecutor;

    private void send(String destination, Object payload) {
        afterCommitExecutor.execute(() -> simpMessagingTemplate.convertAndSend(destination, payload));
    }

    private void sendBranchEvent(Long branchId, String destinationSuffix, Object payload, String event, Long entityId) {
        if (branchId == null) {
            log.warn("Skip branch realtime publish because branchId is null. event={} entityId={}", event, entityId);
            return;
        }
        send("/topic/branches/" + branchId + destinationSuffix, payload);
    }

    public void publishCashierOrderCreated(Long branchId, Long serviceOrderId, String code, String patientName, Long amount) {
        publishCashierOrderEvent(branchId, "SERVICE_ORDER_CREATED", serviceOrderId, code, patientName, amount, null, null);
    }

    public void publishCashierOrderEvent(
            Long branchId,
            String event,
            Long serviceOrderId,
            String code,
            String patientName,
            Long amount,
            Long invoiceId,
            String invoiceCode
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", event);
        payload.put("serviceOrderId", serviceOrderId);
        payload.put("code", code);
        payload.put("patientName", patientName);
        payload.put("amount", amount);
        payload.put("invoiceId", invoiceId);
        payload.put("invoiceCode", invoiceCode);
        payload.put("branchId", branchId);
        payload.put("publishedAt", LocalDateTime.now().toString());

        sendBranchEvent(branchId, "/cashier/orders", payload, event, serviceOrderId);
    }

    public void publishDepartmentQueueUpdated(Long branchId, List<String> departmentCodes) {
        if (departmentCodes == null || departmentCodes.isEmpty()) {
            return;
        }
        if (branchId == null) {
            log.warn("Skip branch realtime publish because branchId is null. event={} entityId={}", "QUEUE_UPDATED", null);
            return;
        }

        for (String code : departmentCodes) {
            if (code == null || code.isBlank()) {
                continue;
            }
            sendBranchEvent(
                    branchId,
                    "/service-desk/" + code,
                    Map.of(
                            "event", "QUEUE_UPDATED",
                            "branchId", branchId,
                            "departmentCode", code,
                            "publishedAt", LocalDateTime.now().toString()
                    ),
                    "QUEUE_UPDATED",
                    null
            );
        }

        sendBranchEvent(
                branchId,
                "/service-desk/updates",
                Map.of(
                        "event", "QUEUE_UPDATED",
                        "branchId", branchId,
                        "departmentCodes", departmentCodes,
                        "publishedAt", LocalDateTime.now().toString()
                ),
                "QUEUE_UPDATED",
                null
        );
    }

    public void publishDoctorEncounterUpdated(Long doctorId, Long encounterId, String status) {
        send(
                "/topic/doctor/" + doctorId + "/encounters",
                Map.of(
                        "event", "ENCOUNTER_UPDATED",
                        "encounterId", encounterId,
                        "status", status
                )
        );
    }

    public void publishEncounterChannel(Long encounterId, String event) {
        publishEncounterChannel(encounterId, event, Map.of());
    }

    public void publishEncounterChannel(Long encounterId, String event, Map<String, Object> extras) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", event);
        payload.put("encounterId", encounterId);
        if (extras != null && !extras.isEmpty()) {
            payload.putAll(extras);
        }

        send("/topic/encounter/" + encounterId, payload);
    }

    public void publishPublicAvailabilityChanged(Long branchId, Long specialtyId, Long doctorId, LocalDate visitDate, String session) {
        if (doctorId == null || visitDate == null || session == null || session.isBlank()) {
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "PUBLIC_AVAILABILITY_CHANGED");
        payload.put("branchId", branchId);
        payload.put("specialtyId", specialtyId);
        payload.put("doctorId", doctorId);
        payload.put("visitDate", String.valueOf(visitDate));
        payload.put("session", session);
        payload.put("publishedAt", LocalDateTime.now().toString());

        send("/topic/public/availability/" + doctorId + "/" + visitDate + "/" + session, payload);
    }

    public void publishAppointmentSummaryChanged(Long branchId, LocalDate visitDate) {
        if (branchId == null) {
            log.warn("Skip branch realtime publish because branchId is null. event={} entityId={}", "APPOINTMENT_SUMMARY_CHANGED", null);
            return;
        }
        sendBranchEvent(
                branchId,
                "/appointments/summary",
                Map.of(
                        "event", "APPOINTMENT_SUMMARY_CHANGED",
                        "branchId", branchId,
                        "visitDate", String.valueOf(visitDate),
                        "publishedAt", LocalDateTime.now().toString()
                ),
                "APPOINTMENT_SUMMARY_CHANGED",
                null
        );
    }

    public void publishAppointmentProcessingChanged(
            Long appointmentId,
            Long branchId,
            Long doctorId,
            LocalDate visitDate,
            Long processingById,
            String processingByName,
            String processingStartedAt,
            String processingExpiresAt
    ) {
        if (branchId == null) {
            log.warn("Skip branch realtime publish because branchId is null. event={} entityId={}",
                    "APPOINTMENT_PROCESSING_CHANGED", appointmentId);
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "APPOINTMENT_PROCESSING_CHANGED");
        payload.put("appointmentId", appointmentId);
        payload.put("branchId", branchId);
        payload.put("doctorId", doctorId);
        payload.put("visitDate", String.valueOf(visitDate));
        payload.put("processingById", processingById);
        payload.put("processingByName", processingByName);
        payload.put("processingStartedAt", processingStartedAt);
        payload.put("processingExpiresAt", processingExpiresAt);
        payload.put("publishedAt", LocalDateTime.now().toString());

        sendBranchEvent(branchId, "/appointments/summary", payload, "APPOINTMENT_PROCESSING_CHANGED", appointmentId);
        sendBranchEvent(branchId, "/reception/queue", payload, "APPOINTMENT_PROCESSING_CHANGED", appointmentId);
    }

    public void publishAppointmentUpdated(
            Long appointmentId,
            Long branchId,
            Long doctorId,
            LocalDate visitDate,
            String session,
            String previousStatus,
            String status,
            String arrivalStatus,
            Integer queueNo,
            Integer receptionQueueNo,
            String arrivedAt,
            String arrivedByName,
            String checkedInAt,
            String checkedInByName,
            String confirmedAt,
            String confirmedByName
    ) {
        if (branchId == null) {
            log.warn("Skip branch realtime publish because branchId is null. event={} entityId={}",
                    "APPOINTMENT_UPDATED", appointmentId);
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "APPOINTMENT_UPDATED");
        payload.put("appointmentId", appointmentId);
        payload.put("branchId", branchId);
        payload.put("doctorId", doctorId);
        payload.put("visitDate", String.valueOf(visitDate));
        payload.put("session", session);
        payload.put("previousStatus", previousStatus);
        payload.put("status", status);
        payload.put("arrivalStatus", arrivalStatus);
        payload.put("queueNo", queueNo);
        payload.put("receptionQueueNo", receptionQueueNo);
        payload.put("arrivedAt", arrivedAt);
        payload.put("arrivedByName", arrivedByName);
        payload.put("checkedInAt", checkedInAt);
        payload.put("checkedInByName", checkedInByName);
        payload.put("confirmedAt", confirmedAt);
        payload.put("confirmedByName", confirmedByName);
        payload.put("publishedAt", LocalDateTime.now().toString());

        sendBranchEvent(branchId, "/appointments/summary", payload, "APPOINTMENT_UPDATED", appointmentId);
        sendBranchEvent(branchId, "/reception/queue", payload, "APPOINTMENT_UPDATED", appointmentId);

        publishPublicAvailabilityChanged(branchId, null, doctorId, visitDate, session);
    }

    public void publishRoleNotification(
            String role,
            String title,
            String message,
            String route,
            String entityType,
            Long entityId
    ) {
        if (role == null || role.isBlank()) {
            return;
        }
        send("/topic/notifications/role/" + role, buildNotificationPayload(title, message, route, entityType, entityId));
    }

    public void publishUserNotification(
            Long userId,
            String title,
            String message,
            String route,
            String entityType,
            Long entityId
    ) {
        if (userId == null) {
            return;
        }
        send("/topic/notifications/user/" + userId, buildNotificationPayload(title, message, route, entityType, entityId));
    }

    public void publishInternalNotification(Long userId, Map<String, Object> payload) {
        if (userId == null || payload == null || payload.isEmpty()) {
            return;
        }
        send("/topic/notifications/user/" + userId, payload);
    }

    private Map<String, Object> buildNotificationPayload(
            String title,
            String message,
            String route,
            String entityType,
            Long entityId
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "INTERNAL_NOTIFICATION");
        payload.put("title", title);
        payload.put("message", message);
        payload.put("route", route);
        payload.put("entityType", entityType);
        payload.put("entityId", entityId);
        payload.put("createdAt", LocalDateTime.now().toString());
        return payload;
    }
}
