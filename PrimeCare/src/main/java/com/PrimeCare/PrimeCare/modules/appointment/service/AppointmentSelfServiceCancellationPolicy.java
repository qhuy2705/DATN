package com.PrimeCare.PrimeCare.modules.appointment.service;

import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
public class AppointmentSelfServiceCancellationPolicy {

    private static final long MIN_CANCEL_NOTICE_HOURS = 4L;
    private final Clock clock;

    public AppointmentSelfServiceCancellationPolicy() {
        this(Clock.systemDefaultZone());
    }

    AppointmentSelfServiceCancellationPolicy(Clock clock) {
        this.clock = clock;
    }

    public void assertCanCancel(Appointment appointment) {
        String blockedReason = getBlockedReason(appointment);
        if (blockedReason != null) {
            throw new ApiException(ErrorCode.PATIENT_SELF_SERVICE_NOT_ALLOWED, blockedReason);
        }
    }

    public String getBlockedReason(Appointment appointment) {
        if (appointment == null) {
            return "Không tìm thấy lịch hẹn";
        }
        if (appointment.getStatus() != AppointmentStatus.REQUESTED
                && appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            return "Chỉ có thể tự hủy lịch đang chờ xác nhận hoặc đã xác nhận";
        }
        if (appointment.getVisitDate() == null) {
            return "Lịch hẹn thiếu thông tin ngày khám";
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (appointment.getVisitDate().isBefore(now.toLocalDate())) {
            return "Không thể tự hủy lịch hẹn đã qua";
        }

        if (appointment.getVisitDate().isAfter(now.toLocalDate())) {
            return null;
        }

        LocalTime visitTime = appointment.getEtaStart();
        if (visitTime == null) {
            return "Không thể tự hủy lịch trong ngày khi chưa xác định giờ khám";
        }

        LocalDateTime visitDateTime = LocalDateTime.of(appointment.getVisitDate(), visitTime);
        if (!visitDateTime.isAfter(now.plusHours(MIN_CANCEL_NOTICE_HOURS))) {
            return "Chỉ có thể tự hủy trước giờ khám ít nhất 4 tiếng";
        }
        return null;
    }

    public void applyCancellation(Appointment appointment, User cancelledBy, String reason, String defaultReason) {
        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setCancelledBy(cancelledBy);
        appointment.setCancelledAt(LocalDateTime.now(clock));
        appointment.setCancelReason(resolveReason(reason, defaultReason));
        appointment.setQueueNo(null);
        appointment.setReceptionQueueNo(null);
        appointment.setProcessingBy(null);
        appointment.setProcessingStartedAt(null);
        appointment.setProcessingExpiresAt(null);
    }

    private String resolveReason(String reason, String defaultReason) {
        if (reason != null && !reason.isBlank()) {
            return reason.trim();
        }
        return defaultReason;
    }
}
