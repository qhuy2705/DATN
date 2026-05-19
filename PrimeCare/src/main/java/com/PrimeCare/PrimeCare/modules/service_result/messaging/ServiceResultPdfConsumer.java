package com.PrimeCare.PrimeCare.modules.service_result.messaging;

import com.PrimeCare.PrimeCare.config.RabbitMqConfig;
import com.PrimeCare.PrimeCare.modules.encounter.entity.Encounter;
import com.PrimeCare.PrimeCare.modules.file.service.FileStorageService;
import com.PrimeCare.PrimeCare.modules.notification.service.InternalNotificationService;
import com.PrimeCare.PrimeCare.modules.realtime.service.AfterCommitExecutor;
import com.PrimeCare.PrimeCare.modules.realtime.service.RealtimeEventPublisher;
import com.PrimeCare.PrimeCare.modules.service_order.entity.ServiceOrderItem;
import com.PrimeCare.PrimeCare.modules.service_result.entity.ServiceResult;
import com.PrimeCare.PrimeCare.modules.service_result.messaging.event.ServiceResultPdfRequestedEvent;
import com.PrimeCare.PrimeCare.modules.service_result.repository.ServiceResultRepository;
import com.PrimeCare.PrimeCare.modules.service_result.service.ServiceResultPdfService;
import com.PrimeCare.PrimeCare.shared.enums.FileOwnerType;
import com.PrimeCare.PrimeCare.shared.enums.PdfGenerationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ServiceResultPdfConsumer {

    private final ServiceResultRepository serviceResultRepository;
    private final ServiceResultPdfService serviceResultPdfService;
    private final FileStorageService fileStorageService;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final AfterCommitExecutor afterCommitExecutor;
    private final InternalNotificationService internalNotificationService;

    @RabbitListener(queues = RabbitMqConfig.SERVICE_RESULT_PDF_QUEUE)
    @Transactional
    public void consume(ServiceResultPdfRequestedEvent event) {
        ServiceResult result = serviceResultRepository.findById(event.serviceResultId())
                .orElseThrow(() -> new IllegalStateException("Service result not found: " + event.serviceResultId()));

        try {
            result.setReportPdfStatus(PdfGenerationStatus.PROCESSING);
            result.setReportPdfErrorMessage(null);
            serviceResultRepository.save(result);

            byte[] pdfBytes = serviceResultPdfService.generate(result);
            String storageKey = fileStorageService.uploadPdfBytes(
                    pdfBytes,
                    FileOwnerType.SERVICE_RESULT,
                    result.getId(),
                    buildFileName(result)
            );

            result.setReportPdfPath(storageKey);
            result.setReportPdfStatus(PdfGenerationStatus.COMPLETED);
            result.setReportPdfGeneratedAt(LocalDateTime.now());
            result.setReportPdfErrorMessage(null);
            serviceResultRepository.save(result);

            publishRealtime(result, "SERVICE_RESULT_PDF_READY");
        } catch (Exception ex) {
            log.error("Generate service result pdf failed for result {}", event.serviceResultId(), ex);
            result.setReportPdfStatus(PdfGenerationStatus.FAILED);
            result.setReportPdfErrorMessage(ex.getMessage());
            serviceResultRepository.save(result);
            publishRealtime(result, "SERVICE_RESULT_PDF_FAILED");
            internalNotificationService.notifyAdminRolesOnceRequiresNew(
                    "SERVICE_RESULT_PDF_GENERATION_FAILED",
                    InternalNotificationService.SEVERITY_CRITICAL,
                    "Tạo PDF kết quả thất bại",
                    "Tạo PDF kết quả cận lâm sàng thất bại cho result " + result.getId() + ".",
                    "/app/admin/dashboard",
                    "SERVICE_RESULT",
                    result.getId(),
                    Map.of("serviceResultId", result.getId(), "error", ex.getMessage() != null ? ex.getMessage() : "")
            );
            throw ex;
        }
    }

    private void publishRealtime(ServiceResult result, String event) {
        ServiceOrderItem item = result.getServiceOrderItem();
        Encounter encounter = item.getServiceOrder().getEncounter();
        Long encounterId = encounter.getId();
        Long doctorId = encounter.getDoctor() != null ? encounter.getDoctor().getId() : null;
        String departmentCode = item.getAssignedDepartmentCode();
        Long branchId = encounter.getBranch() != null ? encounter.getBranch().getId() : null;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("serviceOrderItemId", item.getId());
        payload.put("serviceResultId", result.getId());
        payload.put("reportPdfStatus", result.getReportPdfStatus() != null ? result.getReportPdfStatus().name() : null);
        payload.put("encounterStatus", encounter.getStatus() != null ? encounter.getStatus().name() : null);

        afterCommitExecutor.execute(() -> {
            if (departmentCode != null && !departmentCode.isBlank()) {
                realtimeEventPublisher.publishDepartmentQueueUpdated(branchId, List.of(departmentCode));
            }
            if (doctorId != null) {
                realtimeEventPublisher.publishDoctorEncounterUpdated(
                        doctorId,
                        encounterId,
                        encounter.getStatus() != null ? encounter.getStatus().name() : null
                );
            }
            realtimeEventPublisher.publishEncounterChannel(encounterId, event, payload);
        });
    }

    private String buildFileName(ServiceResult result) {
        ServiceOrderItem item = result.getServiceOrderItem();
        String serviceCode = item.getServiceCodeSnapshot() != null && !item.getServiceCodeSnapshot().isBlank()
                ? item.getServiceCodeSnapshot().trim()
                : "service-result";
        return serviceCode.toLowerCase() + "-" + result.getId() + ".pdf";
    }
}
