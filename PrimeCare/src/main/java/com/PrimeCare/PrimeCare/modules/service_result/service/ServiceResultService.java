package com.PrimeCare.PrimeCare.modules.service_result.service;

import com.PrimeCare.PrimeCare.config.RabbitMqConfig;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.encounter.entity.Encounter;
import com.PrimeCare.PrimeCare.modules.encounter.service.EncounterWorkflowService;
import com.PrimeCare.PrimeCare.modules.file.service.FileStorageService;
import com.PrimeCare.PrimeCare.modules.medical_service.entity.MedicalService;
import com.PrimeCare.PrimeCare.modules.notification.service.InternalNotificationService;
import com.PrimeCare.PrimeCare.modules.realtime.service.AfterCommitExecutor;
import com.PrimeCare.PrimeCare.modules.realtime.service.RealtimeEventPublisher;
import com.PrimeCare.PrimeCare.modules.service_order.entity.ServiceOrder;
import com.PrimeCare.PrimeCare.modules.service_order.entity.ServiceOrderItem;
import com.PrimeCare.PrimeCare.modules.service_order.repository.ServiceOrderItemRepository;
import com.PrimeCare.PrimeCare.modules.service_result.dto.request.SubmitServiceResultRequest;
import com.PrimeCare.PrimeCare.modules.service_result.dto.response.ServiceDeskQueueItemResponse;
import com.PrimeCare.PrimeCare.modules.service_result.dto.response.ServiceDeskSummaryResponse;
import com.PrimeCare.PrimeCare.modules.service_result.dto.response.ServiceResultResponse;
import com.PrimeCare.PrimeCare.modules.service_result.entity.ServiceResult;
import com.PrimeCare.PrimeCare.modules.service_result.messaging.event.ServiceResultPdfRequestedEvent;
import com.PrimeCare.PrimeCare.modules.service_result.repository.ServiceDeskMetricsRepository;
import com.PrimeCare.PrimeCare.modules.service_result.repository.ServiceResultRepository;
import com.PrimeCare.PrimeCare.modules.service_result.support.ServiceResultTemplateSupport;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import com.PrimeCare.PrimeCare.shared.enums.EncounterStatus;
import com.PrimeCare.PrimeCare.shared.enums.PdfGenerationStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceOrderItemStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceOrderStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceResultStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceResultTemplateCode;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import com.PrimeCare.PrimeCare.shared.utils.StringUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ServiceResultService {

    private static final EnumSet<ServiceOrderItemStatus> DEFAULT_QUEUE_STATUSES = EnumSet.of(
            ServiceOrderItemStatus.WAITING_EXECUTION,
            ServiceOrderItemStatus.IN_PROGRESS,
            ServiceOrderItemStatus.DONE
    );

    private final ServiceOrderItemRepository itemRepository;
    private final ServiceResultRepository resultRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final AfterCommitExecutor afterCommitExecutor;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final FileStorageService fileStorageService;
    private final ServiceResultStatusHistoryService serviceResultStatusHistoryService;
    private final ServiceDeskMetricsRepository serviceDeskMetricsRepository;
    private final EncounterWorkflowService encounterWorkflowService;
    private final InternalNotificationService internalNotificationService;

    @Transactional(readOnly = true)
    public ServiceDeskSummaryResponse summary(String departmentCode) {
        long started = System.nanoTime();
        String safeDepartmentCode = normalizeDepartmentCode(departmentCode);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfDay = now.with(LocalTime.MIN);
        LocalDateTime endOfDay = now.with(LocalTime.MAX);
        LocalDateTime sevenDaysAgo = now.minusDays(6).with(LocalTime.MIN);
        var summary = serviceDeskMetricsRepository.summarize(safeDepartmentCode, startOfDay, endOfDay, now, sevenDaysAgo);

        ServiceDeskSummaryResponse response = ServiceDeskSummaryResponse.builder()
                .waitingCount(summary.waitingCount())
                .inProgressCount(summary.inProgressCount())
                .completedTodayCount(summary.completedTodayCount())
                .overdueCount(summary.overdueCount())
                .readyForDoctorCount(summary.readyForDoctorCount())
                .pdfPendingCount(summary.pdfPendingCount())
                .averageTurnaroundMinutes(roundOneDecimal(summary.averageTurnaroundMinutes()))
                .turnaroundBreachRate(roundOneDecimal(summary.turnaroundBreachRate()))
                .build();
        log.info("service desk summary durationMs={} departmentCode={}", durationMs(started), safeDepartmentCode);
        return response;
    }

    @Transactional(readOnly = true)
    public List<com.PrimeCare.PrimeCare.modules.audit.dto.response.AuditLogResponse> history(Long itemId) {
        if (itemId == null) {
            return List.of();
        }
        return resultRepository.findByServiceOrderItem_Id(itemId)
                .map(result -> auditLogService.listEntityHistory("SERVICE_RESULT", result.getId()))
                .orElseGet(List::of);
    }

    @Transactional(readOnly = true)
    public byte[] downloadReportPdf(Long itemId, Long userId) {
        ServiceResult result = resultRepository.findByServiceOrderItem_Id(itemId)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REQUEST, "Chưa có kết quả cho chỉ định này"));

        if (StringUtil.isBlank(result.getReportPdfPath())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "PDF kết quả chưa sẵn sàng để xem");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));

        if (user.getRole() == UserRole.DOCTOR) {
            Long currentDoctorId = user.getDoctorProfile() != null ? user.getDoctorProfile().getId() : null;
            Long encounterDoctorId = result.getServiceOrderItem().getServiceOrder().getEncounter().getDoctor() != null
                    ? result.getServiceOrderItem().getServiceOrder().getEncounter().getDoctor().getId()
                    : null;

            if (currentDoctorId == null || !Objects.equals(currentDoctorId, encounterDoctorId)) {
                throw new ApiException(ErrorCode.UNAUTHORIZED, "Bạn không thuộc bác sĩ phụ trách kết quả này");
            }
        } else if (user.getRole() != UserRole.SERVICE_TECHNICIAN && user.getRole() != UserRole.OPERATIONS_ADMIN) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }

        return fileStorageService.downloadAsBytes(result.getReportPdfPath());
    }

    @Transactional(readOnly = true)
    public PageResponse<ServiceDeskQueueItemResponse> searchQueue(
            String departmentCode,
            ServiceOrderItemStatus itemStatus,
            ServiceResultStatus resultStatus,
            String q,
            Pageable pageable
    ) {
        long started = System.nanoTime();
        String keyword = StringUtil.trimToNull(q);
        var statuses = itemStatus != null ? List.of(itemStatus) : List.copyOf(DEFAULT_QUEUE_STATUSES);
        Page<ServiceOrderItem> page = itemRepository.searchServiceDeskQueue(
                normalizeDepartmentCode(departmentCode),
                statuses,
                resultStatus,
                keyword,
                keyword != null ? keyword.toLowerCase(Locale.ROOT) : null,
                pageable
        );

        List<Long> itemIds = page.getContent().stream().map(ServiceOrderItem::getId).toList();
        Map<Long, ServiceResult> resultByItemId = itemIds.isEmpty()
                ? Map.of()
                : resultRepository.findByServiceOrderItem_IdIn(itemIds)
                .stream()
                .collect(Collectors.toMap(r -> r.getServiceOrderItem().getId(), Function.identity()));

        PageResponse<ServiceDeskQueueItemResponse> response = PageResponse.<ServiceDeskQueueItemResponse>builder()
                .items(page.getContent().stream().map(item -> toQueueResponse(item, resultByItemId.get(item.getId()))).toList())
                .meta(PageResponse.Meta.builder()
                        .page(page.getNumber())
                        .size(page.getSize())
                        .totalItems(page.getTotalElements())
                        .totalPages(page.getTotalPages())
                        .hasNext(page.hasNext())
                        .hasPrev(page.hasPrevious())
                        .sort(pageable.getSort().toString())
                        .build())
                .build();
        log.info("service desk queue durationMs={} itemStatus={} resultStatus={} size={}",
                durationMs(started), itemStatus, resultStatus, page.getNumberOfElements());
        return response;
    }

    @Transactional
    public ServiceResultResponse submit(Long itemId, Long userId, SubmitServiceResultRequest req) {
        ServiceOrderItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ApiException(ErrorCode.SERVICE_ORDER_ITEM_NOT_FOUND));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));

        if (item.getStatus() == ServiceOrderItemStatus.PENDING_PAYMENT) {
            throw new ApiException(ErrorCode.SERVICE_ORDER_INVALID_STATUS, "Chỉ định chưa được thanh toán");
        }
        if (item.getStatus() == ServiceOrderItemStatus.CANCELLED) {
            throw new ApiException(ErrorCode.SERVICE_ORDER_INVALID_STATUS, "Chỉ định đã bị hủy");
        }

        ServiceResult result = resultRepository.findByServiceOrderItem_Id(itemId)
                .orElse(ServiceResult.builder().serviceOrderItem(item).build());

        Map<String, Object> before = result.getId() != null ? snapshotResult(result) : null;
        ServiceResultStatus previousStatus = result.getStatus();

        if (result.getStatus() == ServiceResultStatus.VERIFIED) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Kết quả đã được xác nhận, không thể sửa trực tiếp");
        }

        LocalDateTime now = LocalDateTime.now();
        item.setStatus(ServiceOrderItemStatus.DONE);
        item.setCompletedAt(now);
        item.setResultStatus(ServiceResultStatus.COMPLETED);

        String resultDataJson = StringUtil.trimToNull(req.getResultDataJson());
        String fieldValuesJson = StringUtil.trimToNull(req.getFieldValuesJson());
        if (fieldValuesJson == null) {
            fieldValuesJson = resultDataJson;
        }
        if (resultDataJson == null) {
            resultDataJson = fieldValuesJson;
        }

        ServiceResultTemplateCode templateCode = req.getTemplateCode() != null
                ? req.getTemplateCode()
                : ServiceResultTemplateSupport.resolveTemplateCode(item.getMedicalService());
        String templateSchemaJson = resolveTemplateSchemaJson(req.getTemplateSchemaJson(), item.getMedicalService(), templateCode);
        String attachmentUrlsJson = resolveAttachmentUrlsJson(req);
        String attachmentUrl = resolveLegacyAttachmentUrl(req.getAttachmentUrl(), attachmentUrlsJson);
        String reportTitle = resolveReportTitle(req.getReportTitle(), item);

        result.setResultTextVn(StringUtil.trimToNull(req.getResultTextVn()));
        result.setResultTextEn(StringUtil.trimToNull(req.getResultTextEn()));
        result.setResultDataJson(resultDataJson);
        result.setFieldValuesJson(fieldValuesJson);
        result.setAttachmentUrl(attachmentUrl);
        result.setAttachmentMimeType(StringUtil.trimToNull(req.getAttachmentMimeType()));
        result.setAttachmentUrlsJson(attachmentUrlsJson);
        result.setTemplateCode(templateCode);
        result.setTemplateSchemaJson(templateSchemaJson);
        result.setConclusionText(StringUtil.trimToNull(req.getConclusionText()));
        result.setImpressionText(StringUtil.trimToNull(req.getImpressionText()));
        result.setReportTitle(reportTitle);
        result.setPerformedByUser(user);
        result.setPerformedAt(now);
        result.setVerifiedAt(null);
        result.setVerifiedByUser(null);
        result.setStatus(ServiceResultStatus.COMPLETED);
        resetReportPdfState(result);

        ServiceResult saved = resultRepository.save(result);

        ServiceOrder order = item.getServiceOrder();
        Encounter encounter = order.getEncounter();
        boolean allDone = order.getItems().stream()
                .allMatch(i -> i.getStatus() == ServiceOrderItemStatus.DONE || i.getStatus() == ServiceOrderItemStatus.CANCELLED);

        if (allDone) {
            order.setStatus(ServiceOrderStatus.COMPLETED);
        } else {
            order.setStatus(ServiceOrderStatus.IN_PROGRESS);
        }

        EncounterStatus nextStatus = encounterWorkflowService.refreshStatus(encounter);

        publishRealtimeForResultChange(
                item,
                encounter,
                nextStatus == EncounterStatus.READY_FOR_CONCLUSION,
                "SERVICE_RESULT_COMPLETED",
                nextStatus.name()
        );
        requestPdfGeneration(saved);
        serviceResultStatusHistoryService.record(saved, previousStatus, saved.getStatus(), user, before == null ? "Initial result submission" : "Result updated by technician");
        auditLogService.log(user, before == null ? "CREATE" : "UPDATE", "SERVICE_RESULT", saved.getId(), before, snapshotResult(saved));
        return toResponse(saved);
    }

    @Transactional
    public ServiceResultResponse verify(Long itemId, Long userId) {
        ServiceOrderItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ApiException(ErrorCode.SERVICE_ORDER_ITEM_NOT_FOUND));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));

        ServiceResult result = resultRepository.findByServiceOrderItem_Id(itemId)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REQUEST, "Chưa có kết quả để xác nhận"));

        Map<String, Object> before = snapshotResult(result);
        ServiceResultStatus previousStatus = result.getStatus();

        if (item.getStatus() != ServiceOrderItemStatus.DONE) {
            throw new ApiException(ErrorCode.SERVICE_ORDER_INVALID_STATUS, "Chỉ có thể xác nhận kết quả đã hoàn tất");
        }

        if (result.getStatus() == ServiceResultStatus.VERIFIED) {
            return toResponse(result);
        }

        LocalDateTime now = LocalDateTime.now();
        item.setResultStatus(ServiceResultStatus.VERIFIED);
        result.setStatus(ServiceResultStatus.VERIFIED);
        result.setVerifiedByUser(user);
        result.setVerifiedAt(now);
        resetReportPdfState(result);

        ServiceResult saved = resultRepository.save(result);
        Encounter encounter = item.getServiceOrder().getEncounter();
        boolean readyForConclusion = encounter.getStatus() == EncounterStatus.READY_FOR_CONCLUSION;

        publishRealtimeForResultChange(item, encounter, readyForConclusion, "SERVICE_RESULT_VERIFIED", encounter.getStatus().name());
        requestPdfGeneration(saved);
        serviceResultStatusHistoryService.record(saved, previousStatus, saved.getStatus(), user, "Result verified");
        auditLogService.log(user, "VERIFY", "SERVICE_RESULT", saved.getId(), before, snapshotResult(saved));
        return toResponse(saved);
    }

    private void requestPdfGeneration(ServiceResult result) {
        Long resultId = result.getId();
        Long encounterId = result.getServiceOrderItem().getServiceOrder().getEncounter().getId();
        Long itemId = result.getServiceOrderItem().getId();

        afterCommitExecutor.execute(() -> rabbitTemplate.convertAndSend(
                RabbitMqConfig.SERVICE_RESULT_EXCHANGE,
                RabbitMqConfig.SERVICE_RESULT_PDF_ROUTING_KEY,
                new ServiceResultPdfRequestedEvent(resultId, encounterId, itemId)
        ));
    }

    private void resetReportPdfState(ServiceResult result) {
        result.setReportPdfPath(null);
        result.setReportPdfGeneratedAt(null);
        result.setReportPdfErrorMessage(null);
        result.setReportPdfStatus(PdfGenerationStatus.PENDING);
    }

    private void publishRealtimeForResultChange(
            ServiceOrderItem item,
            Encounter encounter,
            boolean notifyConclusionReady,
            String event,
            String encounterStatus
    ) {
        Long doctorId = encounter.getDoctor().getId();
        Long encounterId = encounter.getId();
        Long itemId = item.getId();
        String departmentCode = item.getAssignedDepartmentCode();
        String patientName = encounter.getPatientFullNameSnapshot();
        String serviceName = item.getServiceNameVnSnapshot();
        Long branchId = encounter.getBranch() != null ? encounter.getBranch().getId() : null;

        afterCommitExecutor.execute(() -> {
            realtimeEventPublisher.publishDepartmentQueueUpdated(
                    branchId,
                    departmentCode == null || departmentCode.isBlank() ? List.of() : List.of(departmentCode)
            );
            realtimeEventPublisher.publishDoctorEncounterUpdated(doctorId, encounterId, encounterStatus);
            realtimeEventPublisher.publishEncounterChannel(
                    encounterId,
                    event,
                    buildEncounterPayload(encounterStatus, itemId, item.getResultStatus())
            );

            if (notifyConclusionReady) {
                var doctorUserId = userRepository.findByDoctorProfile_Id(doctorId)
                        .map(User::getId)
                        .orElse(null);
                internalNotificationService.notifyUser(
                        doctorUserId,
                        "RESULTS_READY_FOR_CONCLUSION",
                        InternalNotificationService.SEVERITY_INFO,
                        "Đã có đủ kết quả cận lâm sàng",
                        "Bệnh nhân " + patientName + " đã có đủ kết quả cho dịch vụ " + serviceName + ".",
                        "/app/doctor/encounters/" + encounterId,
                        "ENCOUNTER",
                        encounterId
                );
            }
        });
    }

    private Map<String, Object> buildEncounterPayload(
            String encounterStatus,
            Long itemId,
            ServiceResultStatus resultStatus
    ) {
        var payload = new java.util.LinkedHashMap<String, Object>();
        payload.put("status", encounterStatus);
        payload.put("serviceOrderItemId", itemId);
        if (resultStatus != null) {
            payload.put("resultStatus", resultStatus.name());
        }
        return payload;
    }

    private ServiceDeskQueueItemResponse toQueueResponse(ServiceOrderItem item, ServiceResult result) {
        ServiceOrder order = item.getServiceOrder();
        Encounter encounter = order.getEncounter();
        MedicalService medicalService = item.getMedicalService();

        ServiceResultTemplateCode templateCode = result != null && result.getTemplateCode() != null
                ? result.getTemplateCode()
                : ServiceResultTemplateSupport.resolveTemplateCode(medicalService);
        String templateSchemaJson = result != null && !StringUtil.isBlank(result.getTemplateSchemaJson())
                ? result.getTemplateSchemaJson()
                : resolveTemplateSchemaJson(null, medicalService, templateCode);
        String reportTitle = result != null && !StringUtil.isBlank(result.getReportTitle())
                ? result.getReportTitle()
                : resolveReportTitle(null, item);

        return ServiceDeskQueueItemResponse.builder()
                .itemId(item.getId())
                .serviceOrderId(order.getId())
                .serviceOrderCode(order.getCode())
                .encounterId(encounter.getId())
                .encounterCode(encounter.getCode())
                .appointmentCode(encounter.getAppointment() != null ? encounter.getAppointment().getCode() : null)
                .patientName(encounter.getPatientFullNameSnapshot())
                .doctorName(encounter.getDoctor() != null ? encounter.getDoctor().getFullName() : null)
                .branchName(encounter.getBranch() != null ? encounter.getBranch().getNameVn() : null)
                .departmentCode(item.getAssignedDepartmentCode())
                .serviceCode(item.getServiceCodeSnapshot())
                .serviceNameVn(item.getServiceNameVnSnapshot())
                .serviceNameEn(item.getServiceNameEnSnapshot())
                .queueNo(item.getQueueNo())
                .itemStatus(item.getStatus())
                .resultStatus(item.getResultStatus())
                .queuedAt(item.getQueuedAt())
                .startedAt(item.getStartedAt())
                .completedAt(item.getCompletedAt())
                .resultId(result != null ? result.getId() : null)
                .resultTextVn(result != null ? result.getResultTextVn() : null)
                .resultTextEn(result != null ? result.getResultTextEn() : null)
                .resultDataJson(result != null ? result.getResultDataJson() : null)
                .fieldValuesJson(result != null ? result.getFieldValuesJson() : null)
                .attachmentUrl(result != null ? result.getAttachmentUrl() : null)
                .attachmentMimeType(result != null ? result.getAttachmentMimeType() : null)
                .attachmentUrlsJson(result != null ? result.getAttachmentUrlsJson() : null)
                .templateCode(templateCode)
                .templateSchemaJson(templateSchemaJson)
                .conclusionText(result != null ? result.getConclusionText() : null)
                .impressionText(result != null ? result.getImpressionText() : null)
                .reportTitle(reportTitle)
                .reportPdfUrl(buildReportPdfUrl(result))
                .reportPdfStatus(result != null ? result.getReportPdfStatus() : null)
                .reportPdfErrorMessage(result != null ? result.getReportPdfErrorMessage() : null)
                .reportPdfGeneratedAt(result != null ? result.getReportPdfGeneratedAt() : null)
                .performedAt(result != null ? result.getPerformedAt() : null)
                .performedByName(result != null && result.getPerformedByUser() != null ? resolveUserDisplayName(result.getPerformedByUser()) : null)
                .verifiedAt(result != null ? result.getVerifiedAt() : null)
                .verifiedByName(result != null && result.getVerifiedByUser() != null ? resolveUserDisplayName(result.getVerifiedByUser()) : null)
                .turnaroundTargetMinutes(resolveTurnaroundTargetMinutes(item))
                .dueAt(calculateDueAt(item))
                .elapsedMinutes(calculateElapsedMinutes(item))
                .turnaroundMinutes(calculateTurnaroundMinutes(item))
                .overdue(isOverdue(item))
                .build();
    }

    private ServiceResultResponse toResponse(ServiceResult r) {
        return ServiceResultResponse.builder()
                .id(r.getId())
                .serviceOrderItemId(r.getServiceOrderItem().getId())
                .resultTextVn(r.getResultTextVn())
                .resultTextEn(r.getResultTextEn())
                .resultDataJson(r.getResultDataJson())
                .fieldValuesJson(r.getFieldValuesJson())
                .attachmentUrl(r.getAttachmentUrl())
                .attachmentMimeType(r.getAttachmentMimeType())
                .attachmentUrlsJson(r.getAttachmentUrlsJson())
                .templateCode(r.getTemplateCode())
                .templateSchemaJson(r.getTemplateSchemaJson())
                .conclusionText(r.getConclusionText())
                .impressionText(r.getImpressionText())
                .reportTitle(r.getReportTitle())
                .reportPdfUrl(buildReportPdfUrl(r))
                .reportPdfStatus(r.getReportPdfStatus())
                .reportPdfGeneratedAt(r.getReportPdfGeneratedAt())
                .reportPdfErrorMessage(r.getReportPdfErrorMessage())
                .status(r.getStatus())
                .turnaroundTargetMinutes(resolveTurnaroundTargetMinutes(r.getServiceOrderItem()))
                .dueAt(calculateDueAt(r.getServiceOrderItem()))
                .elapsedMinutes(calculateElapsedMinutes(r.getServiceOrderItem()))
                .turnaroundMinutes(calculateTurnaroundMinutes(r.getServiceOrderItem()))
                .overdue(isOverdue(r.getServiceOrderItem()))
                .build();
    }

    private Integer resolveTurnaroundTargetMinutes(ServiceOrderItem item) {
        if (item == null || item.getMedicalService() == null) {
            return null;
        }
        return item.getMedicalService().getDefaultTurnaroundMinutes();
    }

    private LocalDateTime calculateDueAt(ServiceOrderItem item) {
        Integer turnaroundTargetMinutes = resolveTurnaroundTargetMinutes(item);
        if (item == null || item.getQueuedAt() == null || turnaroundTargetMinutes == null || turnaroundTargetMinutes <= 0) {
            return null;
        }
        return item.getQueuedAt().plusMinutes(turnaroundTargetMinutes);
    }

    private Long calculateElapsedMinutes(ServiceOrderItem item) {
        if (item == null || item.getQueuedAt() == null || item.getStatus() == ServiceOrderItemStatus.DONE) {
            return null;
        }
        return ChronoUnit.MINUTES.between(item.getQueuedAt(), LocalDateTime.now());
    }

    private Long calculateTurnaroundMinutes(ServiceOrderItem item) {
        if (item == null || item.getCompletedAt() == null) {
            return null;
        }
        LocalDateTime started = item.getStartedAt() != null ? item.getStartedAt() : item.getQueuedAt();
        if (started == null) {
            return null;
        }
        return ChronoUnit.MINUTES.between(started, item.getCompletedAt());
    }

    private Boolean isOverdue(ServiceOrderItem item) {
        LocalDateTime dueAt = calculateDueAt(item);
        if (dueAt == null) {
            return null;
        }
        LocalDateTime comparisonTime = item.getCompletedAt() != null ? item.getCompletedAt() : LocalDateTime.now();
        return comparisonTime.isAfter(dueAt);
    }

    private double roundOneDecimal(double value) {
        return Math.round(value * 10.0d) / 10.0d;
    }

    private String normalizeDepartmentCode(String departmentCode) {
        String trimmed = StringUtil.trimToNull(departmentCode);
        return trimmed != null ? trimmed.toUpperCase(Locale.ROOT) : null;
    }

    private long durationMs(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }

    private Map<String, Object> snapshotResult(ServiceResult result) {
        if (result == null) {
            return null;
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", result.getId());
        snapshot.put("serviceOrderItemId", result.getServiceOrderItem() != null ? result.getServiceOrderItem().getId() : null);
        snapshot.put("status", result.getStatus() != null ? result.getStatus().name() : null);
        snapshot.put("templateCode", result.getTemplateCode() != null ? result.getTemplateCode().name() : null);
        snapshot.put("reportTitle", result.getReportTitle());
        snapshot.put("resultTextVn", result.getResultTextVn());
        snapshot.put("resultTextEn", result.getResultTextEn());
        snapshot.put("conclusionText", result.getConclusionText());
        snapshot.put("impressionText", result.getImpressionText());
        snapshot.put("fieldValues", parseJsonToObject(result.getFieldValuesJson()));
        snapshot.put("attachments", parseJsonToObject(result.getAttachmentUrlsJson()));
        snapshot.put("performedAt", result.getPerformedAt());
        snapshot.put("performedBy", result.getPerformedByUser() != null ? resolveUserDisplayName(result.getPerformedByUser()) : null);
        snapshot.put("verifiedAt", result.getVerifiedAt());
        snapshot.put("verifiedBy", result.getVerifiedByUser() != null ? resolveUserDisplayName(result.getVerifiedByUser()) : null);
        snapshot.put("reportPdfStatus", result.getReportPdfStatus() != null ? result.getReportPdfStatus().name() : null);
        snapshot.put("reportPdfGeneratedAt", result.getReportPdfGeneratedAt());
        snapshot.put("reportPdfPath", result.getReportPdfPath());
        return snapshot;
    }

    private Object parseJsonToObject(String json) {
        if (StringUtil.isBlank(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception ex) {
            return json;
        }
    }

    private String buildReportPdfUrl(ServiceResult result) {
        if (result == null || result.getServiceOrderItem() == null || StringUtil.isBlank(result.getReportPdfPath())) {
            return null;
        }
        return "/api/service-desk/results/" + result.getServiceOrderItem().getId() + "/pdf";
    }

    private String resolveUserDisplayName(User user) {
        if (user.getStaffProfile() != null && user.getStaffProfile().getFullName() != null) {
            return user.getStaffProfile().getFullName();
        }
        if (user.getDoctorProfile() != null && user.getDoctorProfile().getFullName() != null) {
            return user.getDoctorProfile().getFullName();
        }
        return user.getEmail();
    }

    private String resolveTemplateSchemaJson(
            String requestedSchemaJson,
            MedicalService medicalService,
            ServiceResultTemplateCode templateCode
    ) {
        String schemaJson = StringUtil.trimToNull(requestedSchemaJson);
        if (schemaJson != null) {
            return schemaJson;
        }
        if (medicalService != null && !StringUtil.isBlank(medicalService.getResultTemplateSchemaJson())) {
            return medicalService.getResultTemplateSchemaJson();
        }
        return ServiceResultTemplateSupport.defaultSchemaJson(templateCode);
    }

    private String resolveReportTitle(String requestedTitle, ServiceOrderItem item) {
        String reportTitle = StringUtil.trimToNull(requestedTitle);
        if (reportTitle != null) {
            return reportTitle;
        }
        if (item.getMedicalService() != null) {
            return ServiceResultTemplateSupport.resolveReportTitle(item.getMedicalService());
        }
        return ServiceResultTemplateSupport.resolveReportTitle(null, item.getServiceNameVnSnapshot());
    }

    private String resolveAttachmentUrlsJson(SubmitServiceResultRequest req) {
        String attachmentUrlsJson = StringUtil.trimToNull(req.getAttachmentUrlsJson());
        if (attachmentUrlsJson != null) {
            return attachmentUrlsJson;
        }

        String attachmentUrl = StringUtil.trimToNull(req.getAttachmentUrl());
        if (attachmentUrl == null) {
            return null;
        }

        try {
            Map<String, Object> attachment = new LinkedHashMap<>();
            attachment.put("url", attachmentUrl);
            if (!StringUtil.isBlank(req.getAttachmentMimeType())) {
                attachment.put("mimeType", req.getAttachmentMimeType().trim());
            }
            return objectMapper.writeValueAsString(List.of(attachment));
        } catch (Exception ex) {
            return null;
        }
    }

    private String resolveLegacyAttachmentUrl(String requestedAttachmentUrl, String attachmentUrlsJson) {
        String attachmentUrl = StringUtil.trimToNull(requestedAttachmentUrl);
        if (attachmentUrl != null) {
            return attachmentUrl;
        }
        if (StringUtil.isBlank(attachmentUrlsJson)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(attachmentUrlsJson);
            if (root.isArray() && root.size() > 0) {
                JsonNode first = root.get(0);
                if (first != null && first.hasNonNull("url")) {
                    return StringUtil.trimToNull(first.get("url").asText());
                }
            }
        } catch (Exception ignored) {
            // ignore malformed attachment payload and gracefully fallback to null
        }
        return null;
    }
}
