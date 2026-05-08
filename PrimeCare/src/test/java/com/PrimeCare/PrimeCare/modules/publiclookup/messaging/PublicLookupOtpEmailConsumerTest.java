package com.PrimeCare.PrimeCare.modules.publiclookup.messaging;

import com.PrimeCare.PrimeCare.modules.notification.service.InternalNotificationService;
import com.PrimeCare.PrimeCare.modules.publiclookup.messaging.event.PublicLookupOtpEmailEvent;
import com.PrimeCare.PrimeCare.modules.publiclookup.service.PublicLookupMailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PublicLookupOtpEmailConsumerTest {

    @Mock
    private PublicLookupMailService mailService;
    @Mock
    private InternalNotificationService internalNotificationService;

    private PublicLookupOtpEmailConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new PublicLookupOtpEmailConsumer(mailService, internalNotificationService);
    }

    @Test
    void consumeAppointmentEventSendsAppointmentOtpEmail() {
        PublicLookupOtpEmailEvent event = event("APPOINTMENT");

        consumer.consume(event);

        verify(mailService).sendAppointmentLookupOtp(
                "patient@example.test",
                "Nguyen Van A",
                "APT001",
                "123456",
                300,
                30
        );
    }

    @Test
    void consumeResultEventSendsResultOtpEmail() {
        PublicLookupOtpEmailEvent event = event("RESULT");

        consumer.consume(event);

        verify(mailService).sendResultLookupOtp(
                "patient@example.test",
                "Nguyen Van A",
                "APT001",
                "123456",
                300,
                30
        );
    }

    @Test
    void consumeInvalidLookupTypeThrowsForRetryAndDlq() {
        PublicLookupOtpEmailEvent event = event("BROKEN");

        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(mailService);
    }

    private PublicLookupOtpEmailEvent event(String lookupType) {
        return PublicLookupOtpEmailEvent.builder()
                .lookupType(lookupType)
                .referenceId(7L)
                .referenceCode("APT001")
                .patientName("Nguyen Van A")
                .toEmail("patient@example.test")
                .otpCode("123456")
                .ttlSeconds(300)
                .resendCooldownSeconds(30)
                .requestedAt(Instant.now())
                .build();
    }
}
