package com.PrimeCare.PrimeCare.modules.prescription.messaging;

import com.PrimeCare.PrimeCare.config.RabbitMqConfig;
import com.PrimeCare.PrimeCare.modules.prescription.service.PrescriptionPdfProcessorService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PrescriptionPdfConsumer {

    private final PrescriptionPdfProcessorService prescriptionPdfProcessorService;

    @RabbitListener(queues = RabbitMqConfig.PRESCRIPTION_PDF_QUEUE)
    public void consume(PrescriptionPdfRequestedMessage message) {
        prescriptionPdfProcessorService.process(message.jobId(), message.prescriptionId());
    }
}
