package com.PrimeCare.PrimeCare.modules.billing.messaging;

import com.PrimeCare.PrimeCare.config.RabbitMqConfig;
import com.PrimeCare.PrimeCare.modules.billing.service.InvoicePdfProcessorService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InvoicePdfConsumer {

    private final InvoicePdfProcessorService invoicePdfProcessorService;

    @RabbitListener(queues = RabbitMqConfig.INVOICE_PDF_QUEUE)
    public void consume(InvoicePdfRequestedMessage message) {
        invoicePdfProcessorService.process(message.jobId(), message.invoiceId());
    }
}
