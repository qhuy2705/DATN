package com.PrimeCare.PrimeCare.modules.bookingemailotp.messaging;

import com.PrimeCare.PrimeCare.modules.bookingemailotp.messaging.event.BookingEmailOtpEmailEvent;
import com.PrimeCare.PrimeCare.modules.bookingemailotp.service.BookingEmailOtpMailService;
import com.PrimeCare.PrimeCare.modules.notification.service.EmailNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class BookingEmailOtpEmailConsumerTest {

    @Mock
    private EmailNotificationService emailNotificationService;

    private BookingEmailOtpEmailConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new BookingEmailOtpEmailConsumer(
                new BookingEmailOtpMailService(),
                emailNotificationService
        );
    }

    @Test
    void consumeSendsBookingOtpViaEmailNotificationService() {
        BookingEmailOtpEmailEvent event = event();

        consumer.consume(event);

        ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailNotificationService).sendHtmlEmail(
                isNull(),
                eq("patient@example.test"),
                eq("Mã xác thực đặt lịch PrimeCare"),
                htmlCaptor.capture(),
                eq("BOOKING_EMAIL_OTP"),
                isNull(),
                isNull()
        );
        assertThat(htmlCaptor.getValue()).contains("123456");
        assertThat(htmlCaptor.getValue()).contains("xác thực email để đặt lịch khám");
        assertThat(htmlCaptor.getValue()).contains("Nếu bạn không thực hiện thao tác này");
    }

    @Test
    void consumeInvalidEventThrowsForRetryAndDlq() {
        BookingEmailOtpEmailEvent event = BookingEmailOtpEmailEvent.builder()
                .toEmail("patient@example.test")
                .ttlSeconds(300)
                .resendCooldownSeconds(30)
                .build();

        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(emailNotificationService);
    }

    private BookingEmailOtpEmailEvent event() {
        return BookingEmailOtpEmailEvent.builder()
                .otpId(7L)
                .verificationId("verification")
                .toEmail("patient@example.test")
                .maskedEmail("pa***@example.test")
                .otpCode("123456")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .ttlSeconds(300)
                .resendCooldownSeconds(30)
                .requestedAt(Instant.now())
                .build();
    }
}
