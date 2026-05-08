package com.PrimeCare.PrimeCare.modules.notification.messaging;

import com.PrimeCare.PrimeCare.config.RabbitMqConfig;
import com.PrimeCare.PrimeCare.modules.notification.messaging.event.AppointmentCreatedMailEvent;
import com.PrimeCare.PrimeCare.modules.notification.messaging.event.AppointmentReminderMailEvent;
import com.PrimeCare.PrimeCare.modules.notification.messaging.event.ResultReadyMailEvent;
import com.PrimeCare.PrimeCare.modules.realtime.service.AfterCommitExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppointmentMailEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final AfterCommitExecutor afterCommitExecutor;

    public void publishCreated(AppointmentCreatedMailEvent event) {
        afterCommitExecutor.execute(() -> rabbitTemplate.convertAndSend(
                RabbitMqConfig.APPOINTMENT_EXCHANGE,
                RabbitMqConfig.APPOINTMENT_CREATED_ROUTING_KEY,
                event
        ));
    }

    public void publishReminder(AppointmentReminderMailEvent event) {
        afterCommitExecutor.execute(() -> rabbitTemplate.convertAndSend(
                RabbitMqConfig.APPOINTMENT_EXCHANGE,
                RabbitMqConfig.APPOINTMENT_REMINDER_ROUTING_KEY,
                event
        ));
    }

    public void publishResultReady(ResultReadyMailEvent event) {
        afterCommitExecutor.execute(() -> rabbitTemplate.convertAndSend(
                RabbitMqConfig.APPOINTMENT_EXCHANGE,
                RabbitMqConfig.RESULT_READY_MAIL_ROUTING_KEY,
                event
        ));
    }
}
