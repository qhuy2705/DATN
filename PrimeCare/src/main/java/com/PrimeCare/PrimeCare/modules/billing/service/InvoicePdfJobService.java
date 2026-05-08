package com.PrimeCare.PrimeCare.modules.billing.service;

import com.PrimeCare.PrimeCare.config.RabbitMqConfig;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.billing.entity.Invoice;
import com.PrimeCare.PrimeCare.modules.billing.entity.InvoicePdfJob;
import com.PrimeCare.PrimeCare.modules.billing.messaging.InvoicePdfRequestedMessage;
import com.PrimeCare.PrimeCare.modules.billing.repository.InvoicePdfJobRepository;
import com.PrimeCare.PrimeCare.modules.billing.repository.InvoiceRepository;
import com.PrimeCare.PrimeCare.modules.file.service.FileStorageService;
import com.PrimeCare.PrimeCare.modules.realtime.service.AfterCommitExecutor;
import com.PrimeCare.PrimeCare.shared.enums.InvoicePdfJobStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvoicePdfJobService {

    private final InvoicePdfJobRepository invoicePdfJobRepository;
    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;
    private final RabbitTemplate rabbitTemplate;
    private final FileStorageService fileStorageService;
    private final InvoicePdfProcessorService invoicePdfProcessorService;
    private final AfterCommitExecutor afterCommitExecutor;

    @Transactional
    public InvoicePdfJob requestGenerate(Long invoiceId, Long requestedByUserId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                                           .orElseThrow(() -> new ApiException(ErrorCode.INVOICE_NOT_FOUND, "Không tìm thấy hóa đơn"));

        User requestedBy = userRepository.findById(requestedByUserId)
                                         .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));

        InvoicePdfJob job = InvoicePdfJob.builder()
                                         .invoiceId(invoice.getId())
                                         .requestedBy(requestedBy)
                                         .status(InvoicePdfJobStatus.PENDING)
                                         .build();

        job = invoicePdfJobRepository.saveAndFlush(job);

        Long jobId = job.getId();
        Long invoiceDbId = invoice.getId();
        afterCommitExecutor.execute(() -> {
            try {
                rabbitTemplate.convertAndSend(
                        RabbitMqConfig.INVOICE_PDF_EXCHANGE,
                        RabbitMqConfig.INVOICE_PDF_ROUTING_KEY,
                        new InvoicePdfRequestedMessage(jobId, invoiceDbId)
                );
            } catch (Exception ex) {
                invoicePdfProcessorService.process(jobId, invoiceDbId);
            }
        });

        return job;
    }

    @Transactional(readOnly = true)
    public InvoicePdfJob getJob(Long jobId, Long requestedByUserId) {
        InvoicePdfJob job = invoicePdfJobRepository.findById(jobId)
                                                   .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REQUEST, "Không tìm thấy job tạo PDF"));

        if (job.getRequestedBy() == null || !job.getRequestedBy().getId().equals(requestedByUserId)) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }

        return job;
    }

    @Transactional(readOnly = true)
    public byte[] download(Long jobId, Long requestedByUserId) {
        InvoicePdfJob job = getJob(jobId, requestedByUserId);

        if (job.getStatus() != InvoicePdfJobStatus.COMPLETED || job.getFilePath() == null || job.getFilePath().isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "PDF hóa đơn chưa sẵn sàng");
        }

        return fileStorageService.downloadAsBytes(job.getFilePath());
    }
}