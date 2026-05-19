package com.PrimeCare.PrimeCare.modules.billing.service;

import com.PrimeCare.PrimeCare.modules.billing.entity.Invoice;
import com.PrimeCare.PrimeCare.modules.billing.entity.InvoicePdfJob;
import com.PrimeCare.PrimeCare.modules.billing.repository.InvoicePdfJobRepository;
import com.PrimeCare.PrimeCare.modules.billing.repository.InvoiceRepository;
import com.PrimeCare.PrimeCare.modules.file.service.FileStorageService;
import com.PrimeCare.PrimeCare.modules.notification.service.InternalNotificationService;
import com.PrimeCare.PrimeCare.shared.enums.FileOwnerType;
import com.PrimeCare.PrimeCare.shared.enums.InvoicePdfJobStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoicePdfProcessorService {

    private final InvoicePdfJobRepository invoicePdfJobRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoicePdfService invoicePdfService;
    private final FileStorageService fileStorageService;
    private final BillingQrService billingQrService;
    private final InternalNotificationService internalNotificationService;

    @Transactional
    public void process(Long jobId, Long invoiceId) {
        InvoicePdfJob job = invoicePdfJobRepository.findById(jobId)
                                                   .orElseThrow(() -> new IllegalArgumentException("Invoice PDF job not found"));

        try {
            job.setStatus(InvoicePdfJobStatus.PROCESSING);
            invoicePdfJobRepository.save(job);

            Invoice invoice = invoiceRepository.findById(invoiceId)
                                               .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));

            byte[] qrPng = billingQrService.generatePaymentQr(invoice);
            byte[] pdfBytes = invoicePdfService.generate(
                                        invoice,
                                        qrPng,
                                        billingQrService.bankCode(),
                                        billingQrService.accountNo(),
                                        billingQrService.accountName(),
                                        billingQrService.buildPaymentContent(invoice)
            );

            String storageKey = fileStorageService.uploadPdfBytes(
                    pdfBytes,
                    FileOwnerType.INVOICE,
                    invoice.getId(),
                    "invoice-" + invoice.getCode() + ".pdf"
            );

            job.setFilePath(storageKey);
            job.setStatus(InvoicePdfJobStatus.COMPLETED);
            job.setErrorMessage(null);
            invoicePdfJobRepository.save(job);
        } catch (Exception e) {
            log.error("Failed to generate invoice pdf for job {}", jobId, e);
            job.setStatus(InvoicePdfJobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            invoicePdfJobRepository.save(job);
            internalNotificationService.notifyAdminRolesOnceRequiresNew(
                    "INVOICE_PDF_GENERATION_FAILED",
                    InternalNotificationService.SEVERITY_CRITICAL,
                    "Tạo PDF hóa đơn thất bại",
                    "Tạo PDF hóa đơn thất bại cho job " + jobId + ".",
                    "/app/admin/dashboard",
                    "INVOICE",
                    invoiceId,
                    java.util.Map.of("jobId", jobId, "error", e.getMessage() != null ? e.getMessage() : "")
            );
            throw new IllegalStateException("Invoice PDF generation failed for job " + jobId, e);
        }
    }
}
