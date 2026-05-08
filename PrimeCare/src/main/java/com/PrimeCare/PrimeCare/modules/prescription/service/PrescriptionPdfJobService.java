package com.PrimeCare.PrimeCare.modules.prescription.service;

import com.PrimeCare.PrimeCare.config.RabbitMqConfig;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.file.service.FileStorageService;
import com.PrimeCare.PrimeCare.modules.realtime.service.AfterCommitExecutor;
import com.PrimeCare.PrimeCare.modules.prescription.entity.Prescription;
import com.PrimeCare.PrimeCare.modules.prescription.entity.PrescriptionPdfJob;
import com.PrimeCare.PrimeCare.modules.prescription.messaging.PrescriptionPdfRequestedMessage;
import com.PrimeCare.PrimeCare.modules.prescription.repository.PrescriptionPdfJobRepository;
import com.PrimeCare.PrimeCare.modules.prescription.repository.PrescriptionRepository;
import com.PrimeCare.PrimeCare.shared.enums.PrescriptionPdfJobStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PrescriptionPdfJobService {

    private final PrescriptionPdfJobRepository prescriptionPdfJobRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final UserRepository userRepository;
    private final RabbitTemplate rabbitTemplate;
    private final FileStorageService fileStorageService;
    private final PrescriptionPdfProcessorService prescriptionPdfProcessorService;
    private final AfterCommitExecutor afterCommitExecutor;

    @Transactional
    public PrescriptionPdfJob requestGenerate(Long prescriptionId, Long requestedByUserId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                                                          .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REQUEST, "Không tìm thấy đơn thuốc"));

        User requestedBy = userRepository.findById(requestedByUserId)
                                         .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));

        PrescriptionPdfJob job = PrescriptionPdfJob.builder()
                                                   .prescriptionId(prescription.getId())
                                                   .requestedBy(requestedBy)
                                                   .status(PrescriptionPdfJobStatus.PENDING)
                                                   .build();

        job = prescriptionPdfJobRepository.saveAndFlush(job);

        Long jobId = job.getId();
        Long prescriptionDbId = prescription.getId();
        afterCommitExecutor.execute(() -> {
            try {
                rabbitTemplate.convertAndSend(
                        RabbitMqConfig.PRESCRIPTION_PDF_EXCHANGE,
                        RabbitMqConfig.PRESCRIPTION_PDF_ROUTING_KEY,
                        new PrescriptionPdfRequestedMessage(jobId, prescriptionDbId)
                );
            } catch (Exception ex) {
                prescriptionPdfProcessorService.process(jobId, prescriptionDbId);
            }
        });

        return job;
    }

    @Transactional(readOnly = true)
    public PrescriptionPdfJob getJob(Long jobId, Long requestedByUserId) {
        PrescriptionPdfJob job = prescriptionPdfJobRepository.findById(jobId)
                                                             .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REQUEST, "Không tìm thấy job tạo PDF"));

        if (job.getRequestedBy() == null || !job.getRequestedBy().getId().equals(requestedByUserId)) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }

        return job;
    }

    @Transactional(readOnly = true)
    public byte[] download(Long jobId, Long requestedByUserId) {
        PrescriptionPdfJob job = getJob(jobId, requestedByUserId);

        if (job.getStatus() != PrescriptionPdfJobStatus.COMPLETED || job.getFilePath() == null || job.getFilePath().isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "PDF đơn thuốc chưa sẵn sàng");
        }

        return fileStorageService.downloadAsBytes(job.getFilePath());
    }
}