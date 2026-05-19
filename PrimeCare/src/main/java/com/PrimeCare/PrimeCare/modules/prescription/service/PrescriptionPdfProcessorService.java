package com.PrimeCare.PrimeCare.modules.prescription.service;

import com.PrimeCare.PrimeCare.modules.file.service.FileStorageService;
import com.PrimeCare.PrimeCare.modules.notification.service.InternalNotificationService;
import com.PrimeCare.PrimeCare.modules.prescription.entity.Prescription;
import com.PrimeCare.PrimeCare.modules.prescription.entity.PrescriptionPdfJob;
import com.PrimeCare.PrimeCare.modules.prescription.repository.PrescriptionPdfJobRepository;
import com.PrimeCare.PrimeCare.modules.prescription.repository.PrescriptionRepository;
import com.PrimeCare.PrimeCare.shared.enums.FileOwnerType;
import com.PrimeCare.PrimeCare.shared.enums.PrescriptionPdfJobStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrescriptionPdfProcessorService {

    private final PrescriptionPdfJobRepository prescriptionPdfJobRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionPdfService prescriptionPdfService;
    private final FileStorageService fileStorageService;
    private final InternalNotificationService internalNotificationService;

    @Transactional
    public void process(Long jobId, Long prescriptionId) {
        PrescriptionPdfJob job = prescriptionPdfJobRepository.findById(jobId)
                                                             .orElseThrow(() -> new IllegalArgumentException("Prescription PDF job not found"));

        try {
            job.setStatus(PrescriptionPdfJobStatus.PROCESSING);
            prescriptionPdfJobRepository.save(job);

            Prescription prescription = prescriptionRepository.findById(prescriptionId)
                                                              .orElseThrow(() -> new IllegalArgumentException("Prescription not found"));

            byte[] pdfBytes = prescriptionPdfService.generate(prescription);

            String storageKey = fileStorageService.uploadPdfBytes(
                    pdfBytes,
                    FileOwnerType.PRESCRIPTION,
                    prescription.getId(),
                    "prescription-" + prescription.getCode() + ".pdf"
            );

            job.setFilePath(storageKey);
            job.setStatus(PrescriptionPdfJobStatus.COMPLETED);
            job.setErrorMessage(null);
            prescriptionPdfJobRepository.save(job);
        } catch (Exception e) {
            log.error("Failed to generate prescription pdf for job {}", jobId, e);
            job.setStatus(PrescriptionPdfJobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            prescriptionPdfJobRepository.save(job);
            internalNotificationService.notifyAdminRolesOnceRequiresNew(
                    "PRESCRIPTION_PDF_GENERATION_FAILED",
                    InternalNotificationService.SEVERITY_CRITICAL,
                    "Tạo PDF đơn thuốc thất bại",
                    "Tạo PDF đơn thuốc thất bại cho job " + jobId + ".",
                    "/app/admin/dashboard",
                    "PRESCRIPTION",
                    prescriptionId,
                    Map.of("jobId", jobId, "error", e.getMessage() != null ? e.getMessage() : "")
            );
            throw new IllegalStateException("Prescription PDF generation failed for job " + jobId, e);
        }
    }
}
