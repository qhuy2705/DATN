package com.PrimeCare.PrimeCare.modules.bookingemailotp.messaging;

import com.PrimeCare.PrimeCare.config.RabbitMqConfig;
import com.PrimeCare.PrimeCare.modules.bookingemailotp.entity.BookingEmailOtp;
import com.PrimeCare.PrimeCare.modules.bookingemailotp.messaging.event.BookingEmailOtpEmailEvent;
import com.PrimeCare.PrimeCare.modules.realtime.service.AfterCommitExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookingEmailOtpEmailPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private BookingEmailOtpEmailPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new BookingEmailOtpEmailPublisher(rabbitTemplate, new AfterCommitExecutor());
    }

    @Test
    void publishAfterCommitRoutesToBookingEmailOtpQueue() {
        BookingEmailOtp otp = BookingEmailOtp.builder()
                .id(7L)
                .verificationId("verification")
                .normalizedEmail("patient@example.test")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();

        publisher.publishAfterCommit(otp, "123456", 300, 30);

        ArgumentCaptor<BookingEmailOtpEmailEvent> eventCaptor =
                ArgumentCaptor.forClass(BookingEmailOtpEmailEvent.class);
        verify(rabbitTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq(RabbitMqConfig.BOOKING_EMAIL_OTP_EXCHANGE),
                org.mockito.ArgumentMatchers.eq(RabbitMqConfig.BOOKING_EMAIL_OTP_ROUTING_KEY),
                eventCaptor.capture()
        );
        BookingEmailOtpEmailEvent event = eventCaptor.getValue();
        assertThat(event.otpId()).isEqualTo(7L);
        assertThat(event.verificationId()).isEqualTo("verification");
        assertThat(event.toEmail()).isEqualTo("patient@example.test");
        assertThat(event.maskedEmail()).isEqualTo("pa***@example.test");
        assertThat(event.otpCode()).isEqualTo("123456");
        assertThat(event.ttlSeconds()).isEqualTo(300);
        assertThat(event.resendCooldownSeconds()).isEqualTo(30);
    }
}
