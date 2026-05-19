package com.PrimeCare.PrimeCare.modules.appointment.service;

import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.enums.ArrivalStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class AppointmentTimingPolicyTest {

    private static final LocalDate VISIT_DATE = LocalDate.of(2026, 5, 15);

    @Test
    void confirmedAppointmentTenMinutesAfterEtaStartBeforeEtaEndCannotBeMarkedNoShow() {
        Appointment appointment = confirmedAppointment(LocalTime.of(8, 0), LocalTime.of(8, 30));

        String reason = AppointmentTimingPolicy.noShowBlockedReason(
                appointment,
                LocalDateTime.of(VISIT_DATE, LocalTime.of(8, 10))
        );

        assertThat(reason).isEqualTo("Chỉ có thể đánh dấu no-show sau 08:30");
        assertThat(AppointmentTimingPolicy.noShowEligibleAt(appointment))
                .isEqualTo(LocalDateTime.of(VISIT_DATE, LocalTime.of(8, 30)));
    }

    @Test
    void confirmedAppointmentTwentyMinutesAfterEtaStartBeforeEtaEndCannotBeMarkedNoShow() {
        Appointment appointment = confirmedAppointment(LocalTime.of(8, 0), LocalTime.of(8, 30));

        String reason = AppointmentTimingPolicy.noShowBlockedReason(
                appointment,
                LocalDateTime.of(VISIT_DATE, LocalTime.of(8, 20))
        );

        assertThat(reason).isEqualTo("Chỉ có thể đánh dấu no-show sau 08:30");
    }

    @Test
    void confirmedAppointmentAfterEtaEndCanBeMarkedNoShow() {
        Appointment appointment = confirmedAppointment(LocalTime.of(8, 0), LocalTime.of(8, 30));

        String reason = AppointmentTimingPolicy.noShowBlockedReason(
                appointment,
                LocalDateTime.of(VISIT_DATE, LocalTime.of(8, 31))
        );

        assertThat(reason).isNull();
    }

    @Test
    void checkedInAppointmentCannotBeMarkedNoShow() {
        Appointment appointment = confirmedAppointment(LocalTime.of(8, 0), LocalTime.of(8, 30));
        appointment.setStatus(AppointmentStatus.CHECKED_IN);

        String reason = AppointmentTimingPolicy.noShowBlockedReason(
                appointment,
                LocalDateTime.of(VISIT_DATE, LocalTime.of(8, 31))
        );

        assertThat(reason).isEqualTo("Chỉ có thể đánh dấu no-show cho lịch đã xác nhận");
    }

    @Test
    void arrivedAppointmentCannotBeMarkedNoShow() {
        Appointment appointment = confirmedAppointment(LocalTime.of(8, 0), LocalTime.of(8, 30));
        appointment.setArrivalStatus(ArrivalStatus.ARRIVED);

        String reason = AppointmentTimingPolicy.noShowBlockedReason(
                appointment,
                LocalDateTime.of(VISIT_DATE, LocalTime.of(8, 31))
        );

        assertThat(reason).isEqualTo("Bệnh nhân đã đến hoặc đã check-in");
    }

    @Test
    void lateCheckInBetweenGraceAndEtaEndSetsLateMetadata() {
        Appointment appointment = confirmedAppointment(LocalTime.of(8, 0), LocalTime.of(8, 30));

        AppointmentTimingPolicy.applyCheckInTiming(
                appointment,
                LocalDateTime.of(VISIT_DATE, LocalTime.of(8, 20))
        );

        assertThat(appointment.getCheckedInLate()).isTrue();
        assertThat(appointment.getLateMinutes()).isEqualTo(20);
    }

    @Test
    void checkInBeforeLateGraceDoesNotSetLateMetadata() {
        Appointment appointment = confirmedAppointment(LocalTime.of(8, 0), LocalTime.of(8, 30));

        AppointmentTimingPolicy.applyCheckInTiming(
                appointment,
                LocalDateTime.of(VISIT_DATE, LocalTime.of(8, 10))
        );

        assertThat(appointment.getCheckedInLate()).isFalse();
        assertThat(appointment.getLateMinutes()).isEqualTo(0);
    }

    @Test
    void noShowFallsBackToSlotMinutesOnlyWhenEtaEndIsMissing() {
        Appointment appointment = confirmedAppointment(LocalTime.of(8, 0), null);
        appointment.setSlotMinutes(30);

        assertThat(AppointmentTimingPolicy.noShowEligibleAt(appointment))
                .isEqualTo(LocalDateTime.of(VISIT_DATE, LocalTime.of(8, 30)));
    }

    @Test
    void noShowBlocksWhenEtaEndAndSlotDurationAreMissing() {
        Appointment appointment = confirmedAppointment(LocalTime.of(8, 0), null);
        appointment.setSlotMinutes(null);

        String reason = AppointmentTimingPolicy.noShowBlockedReason(
                appointment,
                LocalDateTime.of(VISIT_DATE, LocalTime.of(8, 31))
        );

        assertThat(reason).isEqualTo("Lịch hẹn chưa có giờ kết thúc khung khám");
    }

    private Appointment confirmedAppointment(LocalTime etaStart, LocalTime etaEnd) {
        return Appointment.builder()
                          .status(AppointmentStatus.CONFIRMED)
                          .visitDate(VISIT_DATE)
                          .arrivalStatus(ArrivalStatus.NOT_ARRIVED)
                          .slotMinutes(30)
                          .etaStart(etaStart)
                          .etaEnd(etaEnd)
                          .build();
    }
}
