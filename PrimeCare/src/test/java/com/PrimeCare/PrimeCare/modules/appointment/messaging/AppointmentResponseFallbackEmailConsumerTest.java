package com.PrimeCare.PrimeCare.modules.appointment.messaging;

import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.messaging.event.AppointmentResponseFallbackEmailEvent;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.notification.service.EmailNotificationService;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentResponseFallbackEmailConsumerTest {

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private EmailNotificationService emailNotificationService;

    private AppointmentResponseFallbackEmailConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AppointmentResponseFallbackEmailConsumer(appointmentRepository, emailNotificationService);
    }

    @Test
    void consumeSendsFallbackEmailThroughEmailNotificationService() {
        AppointmentResponseFallbackEmailEvent event = event();
        Appointment appointment = Appointment.builder()
                .id(10L)
                .code("APT-10")
                .status(AppointmentStatus.REQUESTED)
                .build();
        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(appointment));

        consumer.consume(event);

        ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailNotificationService).sendHtmlEmail(
                eq(appointment),
                eq("patient@example.test"),
                eq("Xác nhận lại lịch hẹn PrimeCare"),
                htmlCaptor.capture(),
                eq("APPOINTMENT_RESPONSE_FALLBACK"),
                isNull(),
                isNull()
        );
        assertThat(htmlCaptor.getValue())
                .contains("Chúng tôi chưa liên hệ được với bạn qua số điện thoại đã cung cấp")
                .contains("Tôi vẫn muốn giữ lịch")
                .contains("Số điện thoại này đúng, vui lòng gọi lại")
                .contains("Tôi muốn đổi số điện thoại")
                .contains("Hủy lịch hẹn")
                .contains("https://primecare.test/appointment-response/raw-token?action=keep")
                .contains("Email xác nhận không được tính là xác minh điện thoại bởi nhân viên");
    }

    @Test
    void consumeInvalidEventThrowsForRetryAndDlq() {
        AppointmentResponseFallbackEmailEvent event = AppointmentResponseFallbackEmailEvent.builder()
                .appointmentId(10L)
                .patientEmail("patient@example.test")
                .tokenExpiresAt(LocalDateTime.now().plusHours(1))
                .build();

        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(emailNotificationService);
    }

    private AppointmentResponseFallbackEmailEvent event() {
        return AppointmentResponseFallbackEmailEvent.builder()
                .appointmentId(10L)
                .appointmentCode("APT-10")
                .patientEmail("patient@example.test")
                .patientName("Nguyen Van A")
                .maskedPatientPhone("***0000")
                .visitDate(LocalDate.of(2026, 5, 20))
                .etaStart(LocalTime.of(8, 0))
                .etaEnd(LocalTime.of(8, 30))
                .responseUrl("https://primecare.test/appointment-response/raw-token")
                .tokenExpiresAt(LocalDateTime.of(2026, 5, 19, 18, 0))
                .branchName("PrimeCare")
                .branchHotline("1900 0000")
                .createdAt(Instant.now())
                .build();
    }
}
