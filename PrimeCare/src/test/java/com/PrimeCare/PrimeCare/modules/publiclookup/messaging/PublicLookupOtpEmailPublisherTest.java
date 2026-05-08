package com.PrimeCare.PrimeCare.modules.publiclookup.messaging;

import com.PrimeCare.PrimeCare.config.RabbitMqConfig;
import com.PrimeCare.PrimeCare.modules.publiclookup.messaging.event.PublicLookupOtpEmailEvent;
import com.PrimeCare.PrimeCare.modules.realtime.service.AfterCommitExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PublicLookupOtpEmailPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private PublicLookupOtpEmailPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new PublicLookupOtpEmailPublisher(rabbitTemplate, new AfterCommitExecutor());
    }

    @Test
    void publishAfterCommitRoutesToPublicLookupOtpEmailQueue() {
        PublicLookupOtpEmailEvent event = event("APPOINTMENT");

        publisher.publishAfterCommit(event);

        verify(rabbitTemplate).convertAndSend(
                RabbitMqConfig.PUBLIC_LOOKUP_EXCHANGE,
                RabbitMqConfig.PUBLIC_LOOKUP_OTP_EMAIL_ROUTING_KEY,
                event
        );
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
