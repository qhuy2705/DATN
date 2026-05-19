package com.PrimeCare.PrimeCare.modules.appointment.service;

import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.enums.ArrivalStatus;

import java.time.Duration;
import java.time.LocalDateTime;

final class AppointmentTimingPolicy {

    static final long LATE_CHECK_IN_GRACE_MINUTES = 15L;

    private AppointmentTimingPolicy() {
    }

    static LocalDateTime noShowEligibleAt(Appointment appointment) {
        return slotEndAt(appointment);
    }

    static String noShowBlockedReason(Appointment appointment, LocalDateTime now) {
        if (appointment == null) {
            return "Không tìm thấy lịch hẹn";
        }
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            return "Chỉ có thể đánh dấu no-show cho lịch đã xác nhận";
        }
        if (appointment.getArrivalStatus() == ArrivalStatus.ARRIVED || appointment.getCheckedInAt() != null) {
            return "Bệnh nhân đã đến hoặc đã check-in";
        }

        LocalDateTime eligibleAt = noShowEligibleAt(appointment);
        if (eligibleAt == null) {
            return "Lịch hẹn chưa có giờ kết thúc khung khám";
        }
        if (now == null || !now.isAfter(eligibleAt)) {
            return "Chỉ có thể đánh dấu no-show sau " + eligibleAt.toLocalTime();
        }
        return null;
    }

    static void applyCheckInTiming(Appointment appointment, LocalDateTime checkedInAt) {
        if (appointment == null || checkedInAt == null) {
            return;
        }

        LocalDateTime slotStartAt = slotStartAt(appointment);
        LocalDateTime slotEndAt = slotEndAt(appointment);
        if (slotStartAt == null || slotEndAt == null) {
            markOnTimeOrUnknown(appointment);
            return;
        }

        LocalDateTime lateAfter = slotStartAt.plusMinutes(LATE_CHECK_IN_GRACE_MINUTES);
        if (checkedInAt.isAfter(lateAfter) && !checkedInAt.isAfter(slotEndAt)) {
            appointment.setCheckedInLate(true);
            appointment.setLateMinutes(Math.toIntExact(Duration.between(slotStartAt, checkedInAt).toMinutes()));
            return;
        }

        markOnTimeOrUnknown(appointment);
    }

    private static void markOnTimeOrUnknown(Appointment appointment) {
        appointment.setCheckedInLate(false);
        appointment.setLateMinutes(0);
    }

    private static LocalDateTime slotStartAt(Appointment appointment) {
        if (appointment == null || appointment.getVisitDate() == null || appointment.getEtaStart() == null) {
            return null;
        }
        return LocalDateTime.of(appointment.getVisitDate(), appointment.getEtaStart());
    }

    private static LocalDateTime slotEndAt(Appointment appointment) {
        if (appointment == null || appointment.getVisitDate() == null) {
            return null;
        }
        if (appointment.getEtaEnd() != null) {
            return LocalDateTime.of(appointment.getVisitDate(), appointment.getEtaEnd());
        }
        if (appointment.getEtaStart() != null
                && appointment.getSlotMinutes() != null
                && appointment.getSlotMinutes() > 0) {
            return LocalDateTime.of(appointment.getVisitDate(), appointment.getEtaStart())
                                .plusMinutes(appointment.getSlotMinutes());
        }
        return null;
    }
}
