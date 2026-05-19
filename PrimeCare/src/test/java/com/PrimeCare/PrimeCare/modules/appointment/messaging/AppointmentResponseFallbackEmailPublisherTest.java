package com.PrimeCare.PrimeCare.modules.appointment.messaging;

import com.PrimeCare.PrimeCare.config.RabbitMqConfig;
import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.entity.AppointmentResponseToken;
import com.PrimeCare.PrimeCare.modules.appointment.messaging.event.AppointmentResponseFallbackEmailEvent;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;
import com.PrimeCare.PrimeCare.modules.realtime.service.AfterCommitExecutor;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AppointmentResponseFallbackEmailPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private AppointmentResponseFallbackEmailPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new AppointmentResponseFallbackEmailPublisher(rabbitTemplate, new AfterCommitExecutor());
        ReflectionTestUtils.setField(publisher, "frontendBaseUrl", "https://primecare.test");
    }

    @Test
    void publishFallbackEmailRoutesToFallbackQueue() {
        Appointment appointment = appointment();
        AppointmentResponseToken token = AppointmentResponseToken.builder()
                .id(9L)
                .appointment(appointment)
                .expiresAt(LocalDateTime.now().plusHours(2))
                .build();

        publisher.publishFallbackEmail(appointment, token, "raw-token");

        ArgumentCaptor<AppointmentResponseFallbackEmailEvent> eventCaptor =
                ArgumentCaptor.forClass(AppointmentResponseFallbackEmailEvent.class);
        verify(rabbitTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq(RabbitMqConfig.APPOINTMENT_RESPONSE_FALLBACK_EXCHANGE),
                org.mockito.ArgumentMatchers.eq(RabbitMqConfig.APPOINTMENT_RESPONSE_FALLBACK_EMAIL_ROUTING_KEY),
                eventCaptor.capture()
        );
        AppointmentResponseFallbackEmailEvent event = eventCaptor.getValue();
        assertThat(event.appointmentId()).isEqualTo(10L);
        assertThat(event.appointmentCode()).isEqualTo("APT-10");
        assertThat(event.patientEmail()).isEqualTo("patient@example.test");
        assertThat(event.maskedPatientPhone()).isEqualTo("***0000");
        assertThat(event.responseUrl()).isEqualTo("https://primecare.test/appointment-response/raw-token");
        assertThat(event.branchHotline()).isEqualTo("1900 0000");
    }

    private Appointment appointment() {
        Branch branch = Branch.builder()
                .id(1L)
                .nameVn("PrimeCare")
                .phone("1900 0000")
                .build();
        return Appointment.builder()
                .id(10L)
                .code("APT-10")
                .status(AppointmentStatus.REQUESTED)
                .branch(branch)
                .visitDate(LocalDate.now().plusDays(1))
                .etaStart(LocalTime.of(8, 0))
                .etaEnd(LocalTime.of(8, 30))
                .patientFullName("Nguyen Van A")
                .patientPhone("0900000000")
                .patientEmail("patient@example.test")
                .build();
    }
}
