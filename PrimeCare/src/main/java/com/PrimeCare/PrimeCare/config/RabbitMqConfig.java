package com.PrimeCare.PrimeCare.config;

import org.aopalliance.aop.Advice;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateConfigurer;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String APPOINTMENT_EXCHANGE = "appointment.exchange";
    public static final String DEAD_LETTER_EXCHANGE = "primecare.dead-letter.exchange";

    public static final String APPOINTMENT_CREATED_QUEUE = "appointment.created.queue";
    public static final String APPOINTMENT_CREATED_ROUTING_KEY = "appointment.created";
    public static final String APPOINTMENT_CREATED_DLQ = "appointment.created.queue.dlq";
    public static final String APPOINTMENT_CREATED_DLQ_ROUTING_KEY = "appointment.created.dead";

    public static final String APPOINTMENT_CONFIRMED_PDF_QUEUE = "appointment.confirmed.pdf.queue";
    public static final String APPOINTMENT_CONFIRMED_PDF_ROUTING_KEY = "appointment.confirmed.pdf";
    public static final String APPOINTMENT_CONFIRMED_PDF_DLQ = "appointment.confirmed.pdf.queue.dlq";
    public static final String APPOINTMENT_CONFIRMED_PDF_DLQ_ROUTING_KEY = "appointment.confirmed.pdf.dead";

    public static final String APPOINTMENT_CONFIRMED_MAIL_READY_QUEUE = "appointment.confirmed.mail.ready.queue";
    public static final String APPOINTMENT_CONFIRMED_MAIL_READY_ROUTING_KEY = "appointment.confirmed.mail.ready";
    public static final String APPOINTMENT_CONFIRMED_MAIL_READY_DLQ = "appointment.confirmed.mail.ready.queue.dlq";
    public static final String APPOINTMENT_CONFIRMED_MAIL_READY_DLQ_ROUTING_KEY = "appointment.confirmed.mail.ready.dead";

    public static final String APPOINTMENT_REMINDER_QUEUE = "appointment.reminder.queue";
    public static final String APPOINTMENT_REMINDER_ROUTING_KEY = "appointment.reminder";
    public static final String APPOINTMENT_REMINDER_DLQ = "appointment.reminder.queue.dlq";
    public static final String APPOINTMENT_REMINDER_DLQ_ROUTING_KEY = "appointment.reminder.dead";

    public static final String RESULT_READY_MAIL_QUEUE = "result.ready.mail.queue";
    public static final String RESULT_READY_MAIL_ROUTING_KEY = "result.ready.mail";
    public static final String RESULT_READY_MAIL_DLQ = "result.ready.mail.queue.dlq";
    public static final String RESULT_READY_MAIL_DLQ_ROUTING_KEY = "result.ready.mail.dead";

    public static final String PUBLIC_LOOKUP_EXCHANGE = "public.lookup.exchange";
    public static final String PUBLIC_LOOKUP_OTP_EMAIL_QUEUE = "public.lookup.otp.email.queue";
    public static final String PUBLIC_LOOKUP_OTP_EMAIL_ROUTING_KEY = "public.lookup.otp.email";
    public static final String PUBLIC_LOOKUP_OTP_EMAIL_DLQ = "public.lookup.otp.email.queue.dlq";
    public static final String PUBLIC_LOOKUP_OTP_EMAIL_DLQ_ROUTING_KEY = "public.lookup.otp.email.dead";

    public static final String PRESCRIPTION_PDF_EXCHANGE = "prescription.pdf.exchange";
    public static final String PRESCRIPTION_PDF_QUEUE = "prescription.pdf.generate.queue";
    public static final String PRESCRIPTION_PDF_ROUTING_KEY = "prescription.pdf.generate";
    public static final String PRESCRIPTION_PDF_DLQ = "prescription.pdf.generate.queue.dlq";
    public static final String PRESCRIPTION_PDF_DLQ_ROUTING_KEY = "prescription.pdf.generate.dead";

    public static final String INVOICE_PDF_EXCHANGE = "invoice.pdf.exchange";
    public static final String INVOICE_PDF_QUEUE = "invoice.pdf.generate.queue";
    public static final String INVOICE_PDF_ROUTING_KEY = "invoice.pdf.generate";
    public static final String INVOICE_PDF_DLQ = "invoice.pdf.generate.queue.dlq";
    public static final String INVOICE_PDF_DLQ_ROUTING_KEY = "invoice.pdf.generate.dead";

    public static final String SERVICE_RESULT_EXCHANGE = "service.result.exchange";
    public static final String SERVICE_RESULT_PDF_QUEUE = "service.result.pdf.generate.queue";
    public static final String SERVICE_RESULT_PDF_ROUTING_KEY = "service.result.pdf.generate";
    public static final String SERVICE_RESULT_PDF_DLQ = "service.result.pdf.generate.queue.dlq";
    public static final String SERVICE_RESULT_PDF_DLQ_ROUTING_KEY = "service.result.pdf.generate.dead";

    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
            RabbitTemplateConfigurer configurer,
            ConnectionFactory connectionFactory,
            MessageConverter rabbitMessageConverter
    ) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate();
        configurer.setMessageConverter(rabbitMessageConverter);
        configurer.configure(rabbitTemplate, connectionFactory);
        rabbitTemplate.setMandatory(true);
        return rabbitTemplate;
    }

    @Bean(name = "rabbitListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            MessageConverter rabbitMessageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(rabbitMessageConverter);
        factory.setDefaultRequeueRejected(false);
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(6);
        factory.setPrefetchCount(10);
        factory.setMissingQueuesFatal(false);
        factory.setAdviceChain(rabbitRetryAdvice());
        return factory;
    }

    @Bean
    public Advice rabbitRetryAdvice() {
        return RetryInterceptorBuilder.stateless()
                .maxRetries(2)
                .backOffOptions(1000L, 2.0, 5000L)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    @Bean
    public TopicExchange appointmentExchange() {
        return new TopicExchange(APPOINTMENT_EXCHANGE);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue appointmentCreatedQueue() {
        return durableQueueWithDlq(APPOINTMENT_CREATED_QUEUE, APPOINTMENT_CREATED_DLQ_ROUTING_KEY);
    }

    @Bean
    public Queue appointmentCreatedDlq() {
        return durableDlq(APPOINTMENT_CREATED_DLQ);
    }

    @Bean
    public Binding appointmentCreatedBinding() {
        return BindingBuilder.bind(appointmentCreatedQueue())
                             .to(appointmentExchange())
                             .with(APPOINTMENT_CREATED_ROUTING_KEY);
    }

    @Bean
    public Binding appointmentCreatedDlqBinding() {
        return BindingBuilder.bind(appointmentCreatedDlq())
                             .to(deadLetterExchange())
                             .with(APPOINTMENT_CREATED_DLQ_ROUTING_KEY);
    }

    @Bean
    public Queue appointmentConfirmedPdfQueue() {
        return durableQueueWithDlq(APPOINTMENT_CONFIRMED_PDF_QUEUE, APPOINTMENT_CONFIRMED_PDF_DLQ_ROUTING_KEY);
    }

    @Bean
    public Queue appointmentConfirmedPdfDlq() {
        return durableDlq(APPOINTMENT_CONFIRMED_PDF_DLQ);
    }

    @Bean
    public Binding appointmentConfirmedPdfBinding() {
        return BindingBuilder.bind(appointmentConfirmedPdfQueue())
                             .to(appointmentExchange())
                             .with(APPOINTMENT_CONFIRMED_PDF_ROUTING_KEY);
    }

    @Bean
    public Binding appointmentConfirmedPdfDlqBinding() {
        return BindingBuilder.bind(appointmentConfirmedPdfDlq())
                             .to(deadLetterExchange())
                             .with(APPOINTMENT_CONFIRMED_PDF_DLQ_ROUTING_KEY);
    }

    @Bean
    public Queue appointmentConfirmedMailReadyQueue() {
        return durableQueueWithDlq(APPOINTMENT_CONFIRMED_MAIL_READY_QUEUE, APPOINTMENT_CONFIRMED_MAIL_READY_DLQ_ROUTING_KEY);
    }

    @Bean
    public Queue appointmentConfirmedMailReadyDlq() {
        return durableDlq(APPOINTMENT_CONFIRMED_MAIL_READY_DLQ);
    }

    @Bean
    public Binding appointmentConfirmedMailReadyBinding() {
        return BindingBuilder.bind(appointmentConfirmedMailReadyQueue())
                             .to(appointmentExchange())
                             .with(APPOINTMENT_CONFIRMED_MAIL_READY_ROUTING_KEY);
    }

    @Bean
    public Binding appointmentConfirmedMailReadyDlqBinding() {
        return BindingBuilder.bind(appointmentConfirmedMailReadyDlq())
                             .to(deadLetterExchange())
                             .with(APPOINTMENT_CONFIRMED_MAIL_READY_DLQ_ROUTING_KEY);
    }

    @Bean
    public Queue appointmentReminderQueue() {
        return durableQueueWithDlq(APPOINTMENT_REMINDER_QUEUE, APPOINTMENT_REMINDER_DLQ_ROUTING_KEY);
    }

    @Bean
    public Queue appointmentReminderDlq() {
        return durableDlq(APPOINTMENT_REMINDER_DLQ);
    }

    @Bean
    public Binding appointmentReminderBinding() {
        return BindingBuilder.bind(appointmentReminderQueue())
                             .to(appointmentExchange())
                             .with(APPOINTMENT_REMINDER_ROUTING_KEY);
    }

    @Bean
    public Binding appointmentReminderDlqBinding() {
        return BindingBuilder.bind(appointmentReminderDlq())
                             .to(deadLetterExchange())
                             .with(APPOINTMENT_REMINDER_DLQ_ROUTING_KEY);
    }

    @Bean
    public Queue resultReadyMailQueue() {
        return durableQueueWithDlq(RESULT_READY_MAIL_QUEUE, RESULT_READY_MAIL_DLQ_ROUTING_KEY);
    }

    @Bean
    public Queue resultReadyMailDlq() {
        return durableDlq(RESULT_READY_MAIL_DLQ);
    }

    @Bean
    public Binding resultReadyMailBinding() {
        return BindingBuilder.bind(resultReadyMailQueue())
                             .to(appointmentExchange())
                             .with(RESULT_READY_MAIL_ROUTING_KEY);
    }

    @Bean
    public Binding resultReadyMailDlqBinding() {
        return BindingBuilder.bind(resultReadyMailDlq())
                             .to(deadLetterExchange())
                             .with(RESULT_READY_MAIL_DLQ_ROUTING_KEY);
    }

    @Bean
    public TopicExchange publicLookupExchange() {
        return new TopicExchange(PUBLIC_LOOKUP_EXCHANGE);
    }

    @Bean
    public Queue publicLookupOtpEmailQueue() {
        return durableQueueWithDlq(PUBLIC_LOOKUP_OTP_EMAIL_QUEUE, PUBLIC_LOOKUP_OTP_EMAIL_DLQ_ROUTING_KEY);
    }

    @Bean
    public Queue publicLookupOtpEmailDlq() {
        return durableDlq(PUBLIC_LOOKUP_OTP_EMAIL_DLQ);
    }

    @Bean
    public Binding publicLookupOtpEmailBinding() {
        return BindingBuilder.bind(publicLookupOtpEmailQueue())
                             .to(publicLookupExchange())
                             .with(PUBLIC_LOOKUP_OTP_EMAIL_ROUTING_KEY);
    }

    @Bean
    public Binding publicLookupOtpEmailDlqBinding() {
        return BindingBuilder.bind(publicLookupOtpEmailDlq())
                             .to(deadLetterExchange())
                             .with(PUBLIC_LOOKUP_OTP_EMAIL_DLQ_ROUTING_KEY);
    }

    @Bean
    public TopicExchange prescriptionPdfExchange() {
        return new TopicExchange(PRESCRIPTION_PDF_EXCHANGE);
    }

    @Bean
    public Queue prescriptionPdfQueue() {
        return durableQueueWithDlq(PRESCRIPTION_PDF_QUEUE, PRESCRIPTION_PDF_DLQ_ROUTING_KEY);
    }

    @Bean
    public Queue prescriptionPdfDlq() {
        return durableDlq(PRESCRIPTION_PDF_DLQ);
    }

    @Bean
    public Binding prescriptionPdfBinding() {
        return BindingBuilder.bind(prescriptionPdfQueue())
                             .to(prescriptionPdfExchange())
                             .with(PRESCRIPTION_PDF_ROUTING_KEY);
    }

    @Bean
    public Binding prescriptionPdfDlqBinding() {
        return BindingBuilder.bind(prescriptionPdfDlq())
                             .to(deadLetterExchange())
                             .with(PRESCRIPTION_PDF_DLQ_ROUTING_KEY);
    }

    @Bean
    public TopicExchange invoicePdfExchange() {
        return new TopicExchange(INVOICE_PDF_EXCHANGE);
    }

    @Bean
    public Queue invoicePdfQueue() {
        return durableQueueWithDlq(INVOICE_PDF_QUEUE, INVOICE_PDF_DLQ_ROUTING_KEY);
    }

    @Bean
    public Queue invoicePdfDlq() {
        return durableDlq(INVOICE_PDF_DLQ);
    }

    @Bean
    public Binding invoicePdfBinding() {
        return BindingBuilder.bind(invoicePdfQueue())
                             .to(invoicePdfExchange())
                             .with(INVOICE_PDF_ROUTING_KEY);
    }

    @Bean
    public Binding invoicePdfDlqBinding() {
        return BindingBuilder.bind(invoicePdfDlq())
                             .to(deadLetterExchange())
                             .with(INVOICE_PDF_DLQ_ROUTING_KEY);
    }

    @Bean
    public TopicExchange serviceResultExchange() {
        return new TopicExchange(SERVICE_RESULT_EXCHANGE);
    }

    @Bean
    public Queue serviceResultPdfQueue() {
        return durableQueueWithDlq(SERVICE_RESULT_PDF_QUEUE, SERVICE_RESULT_PDF_DLQ_ROUTING_KEY);
    }

    @Bean
    public Queue serviceResultPdfDlq() {
        return durableDlq(SERVICE_RESULT_PDF_DLQ);
    }

    @Bean
    public Binding serviceResultPdfBinding() {
        return BindingBuilder.bind(serviceResultPdfQueue())
                             .to(serviceResultExchange())
                             .with(SERVICE_RESULT_PDF_ROUTING_KEY);
    }

    @Bean
    public Binding serviceResultPdfDlqBinding() {
        return BindingBuilder.bind(serviceResultPdfDlq())
                             .to(deadLetterExchange())
                             .with(SERVICE_RESULT_PDF_DLQ_ROUTING_KEY);
    }

    private Queue durableQueueWithDlq(String queueName, String deadLetterRoutingKey) {
        return QueueBuilder.durable(queueName)
                           .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                           .withArgument("x-dead-letter-routing-key", deadLetterRoutingKey)
                           .build();
    }

    private Queue durableDlq(String queueName) {
        return QueueBuilder.durable(queueName).build();
    }
}
