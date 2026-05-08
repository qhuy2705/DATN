package com.PrimeCare.PrimeCare.modules.appointment.service;

import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppointmentSelfServiceCancellationPolicyTest {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 4, 26, 8, 0);
    private static final LocalDate TODAY = NOW.toLocalDate();

    private AppointmentSelfServiceCancellationPolicy policy;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        policy = new AppointmentSelfServiceCancellationPolicy(clock);
    }

    @Test
    void assertCanCancelBlocksSameDayAppointmentAtExactlyFourHourCutoff() {
        Appointment appointment = cancellableAppointment(LocalTime.NOON);

        assertThat(policy.getBlockedReason(appointment)).contains("4 tiếng");
        assertThatThrownBy(() -> policy.assertCanCancel(appointment))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PATIENT_SELF_SERVICE_NOT_ALLOWED);
                    assertThat(ex.getMessage()).isEqualTo(policy.getBlockedReason(appointment));
                });
    }

    @Test
    void assertCanCancelAllowsSameDayAppointmentAfterFourHourCutoff() {
        Appointment appointment = cancellableAppointment(LocalTime.of(12, 1));

        assertThat(policy.getBlockedReason(appointment)).isNull();
        assertThatCode(() -> policy.assertCanCancel(appointment)).doesNotThrowAnyException();
    }

    @Test
    void assertCanCancelBlocksStatusesOutsideRequestedAndConfirmed() {
        Appointment appointment = cancellableAppointment(LocalTime.of(12, 1));
        appointment.setStatus(AppointmentStatus.CHECKED_IN);

        assertThatThrownBy(() -> policy.assertCanCancel(appointment))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PATIENT_SELF_SERVICE_NOT_ALLOWED)
                );
    }

    @Test
    void applyCancellationUsesSameSideEffectsForSelfServiceFlows() {
        User cancelledBy = User.builder().id(42L).build();
        User processingBy = User.builder().id(99L).build();
        Appointment appointment = cancellableAppointment(LocalTime.of(12, 1));
        appointment.setQueueNo(5);
        appointment.setReceptionQueueNo(7);
        appointment.setProcessingBy(processingBy);
        appointment.setProcessingStartedAt(NOW.minusMinutes(10));
        appointment.setProcessingExpiresAt(NOW.plusMinutes(5));

        policy.applyCancellation(appointment, cancelledBy, " Patient request ", "Default reason");

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(appointment.getCancelledBy()).isSameAs(cancelledBy);
        assertThat(appointment.getCancelledAt()).isEqualTo(NOW);
        assertThat(appointment.getCancelReason()).isEqualTo("Patient request");
        assertThat(appointment.getQueueNo()).isNull();
        assertThat(appointment.getReceptionQueueNo()).isNull();
        assertThat(appointment.getProcessingBy()).isNull();
        assertThat(appointment.getProcessingStartedAt()).isNull();
        assertThat(appointment.getProcessingExpiresAt()).isNull();
    }

    private Appointment cancellableAppointment(LocalTime etaStart) {
        return Appointment.builder()
                          .status(AppointmentStatus.CONFIRMED)
                          .visitDate(TODAY)
                          .etaStart(etaStart)
                          .build();
    }
}
