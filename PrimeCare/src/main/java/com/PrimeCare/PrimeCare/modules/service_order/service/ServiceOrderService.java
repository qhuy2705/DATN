package com.PrimeCare.PrimeCare.modules.service_order.service;

import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.encounter.entity.Encounter;
import com.PrimeCare.PrimeCare.modules.encounter.repository.EncounterRepository;
import com.PrimeCare.PrimeCare.modules.medical_service.entity.MedicalService;
import com.PrimeCare.PrimeCare.modules.medical_service.repository.MedicalServiceRepository;
import com.PrimeCare.PrimeCare.modules.notification.service.InternalNotificationService;
import com.PrimeCare.PrimeCare.modules.realtime.service.AfterCommitExecutor;
import com.PrimeCare.PrimeCare.modules.realtime.service.RealtimeEventPublisher;
import com.PrimeCare.PrimeCare.modules.service_order.dto.request.CreateServiceOrderRequest;
import com.PrimeCare.PrimeCare.modules.service_order.dto.response.ServiceOrderItemResponse;
import com.PrimeCare.PrimeCare.modules.service_order.dto.response.ServiceOrderResponse;
import com.PrimeCare.PrimeCare.modules.service_order.entity.ServiceOrder;
import com.PrimeCare.PrimeCare.modules.service_order.entity.ServiceOrderItem;
import com.PrimeCare.PrimeCare.modules.service_order.repository.ServiceOrderRepository;
import com.PrimeCare.PrimeCare.modules.service_result.entity.ServiceResult;
import com.PrimeCare.PrimeCare.modules.service_result.repository.ServiceResultRepository;
import com.PrimeCare.PrimeCare.modules.service_result.support.ServiceResultTemplateSupport;
import com.PrimeCare.PrimeCare.shared.enums.EncounterStatus;
import com.PrimeCare.PrimeCare.shared.enums.MedicalServiceStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceOrderStatus;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import com.PrimeCare.PrimeCare.shared.utils.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServiceOrderService {

    private final ServiceOrderRepository serviceOrderRepository;
    private final EncounterRepository encounterRepository;
    private final MedicalServiceRepository medicalServiceRepository;
    private final ServiceResultRepository serviceResultRepository;
    private final UserRepository userRepository;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final AfterCommitExecutor afterCommitExecutor;
    private final InternalNotificationService internalNotificationService;

    @Transactional(readOnly = true)
    public List<ServiceOrderResponse> listByEncounter(Long encounterId, Long doctorUserId) {
        Encounter encounter = getEncounterForDoctor(encounterId, doctorUserId);

        Map<Long, ServiceResult> resultByItemId = serviceResultRepository
                .findByServiceOrderItem_ServiceOrder_Encounter_Id(encounter.getId())
                .stream()
                .collect(Collectors.toMap(r -> r.getServiceOrderItem().getId(), Function.identity()));

        return serviceOrderRepository.findByEncounter_IdOrderByCreatedAtDesc(encounter.getId())
                                     .stream()
                                     .map(order -> toResponse(order, resultByItemId))
                                     .toList();
    }

    @Transactional
    public ServiceOrderResponse create(Long encounterId, Long doctorUserId, CreateServiceOrderRequest req) {
        Encounter encounter = getEncounterForDoctor(encounterId, doctorUserId);

        if (encounter.getStatus() == EncounterStatus.COMPLETED || encounter.getStatus() == EncounterStatus.CANCELLED) {
            throw new ApiException(ErrorCode.ENCOUNTER_INVALID_STATUS);
        }

        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Phiếu chỉ định phải có ít nhất 1 dịch vụ");
        }

        if (serviceOrderRepository.existsByEncounter_IdAndStatusIn(
                encounterId,
                EnumSet.of(ServiceOrderStatus.PENDING_PAYMENT, ServiceOrderStatus.PAID, ServiceOrderStatus.IN_PROGRESS)
        )) {
            throw new ApiException(ErrorCode.SERVICE_ORDER_INVALID_STATUS, "Đang có phiếu chỉ định chưa hoàn tất cho lần khám này");
        }

        User doctor = userRepository.findById(doctorUserId)
                                    .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));

        ServiceOrder order = ServiceOrder.builder()
                                         .code(generateCode(encounter))
                                         .encounter(encounter)
                                         .orderedByDoctor(doctor)
                                         .branch(encounter.getBranch())
                                         .note(StringUtil.trimToNull(req.getNote()))
                                         .items(new ArrayList<>())
                                         .build();

        long total = 0L;
        for (var i : req.getItems()) {
            MedicalService service = medicalServiceRepository.findById(i.getMedicalServiceId())
                                                             .orElseThrow(() -> new ApiException(ErrorCode.MEDICAL_SERVICE_NOT_FOUND));

            if (service.getStatus() != MedicalServiceStatus.ACTIVE) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "Dịch vụ đang ngừng hoạt động");
            }

            long quantity = i.getQuantity() == null ? 1 : i.getQuantity();
            long lineTotal = service.getBasePrice() * quantity;

            ServiceOrderItem item = ServiceOrderItem.builder()
                                                    .serviceOrder(order)
                                                    .medicalService(service)
                                                    .serviceCodeSnapshot(service.getCode())
                                                    .serviceNameVnSnapshot(service.getNameVn())
                                                    .serviceNameEnSnapshot(service.getNameEn())
                                                    .priceSnapshot(service.getBasePrice())
                                                    .quantity((int) quantity)
                                                    .lineTotalAmount(lineTotal)
                                                    .assignedDepartmentCode(normalizeDepartmentCode(service.getDepartmentCode()))
                                                    .note(StringUtil.trimToNull(i.getNote()))
                                                    .build();

            order.getItems().add(item);
            total += lineTotal;
        }

        order.setEstimatedTotalAmount(total);
        encounter.setStatus(EncounterStatus.WAITING_PAYMENT);

        ServiceOrder saved = serviceOrderRepository.save(order);

        Long savedId = saved.getId();
        String code = saved.getCode();
        String patientName = saved.getEncounter().getPatientFullNameSnapshot();
        Long amount = saved.getEstimatedTotalAmount();
        Long encounterDoctorId = saved.getEncounter().getDoctor().getId();
        Long savedEncounterId = saved.getEncounter().getId();
        Long branchId = saved.getBranch() != null ? saved.getBranch().getId() : null;

        afterCommitExecutor.execute(() -> {
            realtimeEventPublisher.publishCashierOrderCreated(branchId, savedId, code, patientName, amount);
            realtimeEventPublisher.publishDoctorEncounterUpdated(encounterDoctorId, savedEncounterId, EncounterStatus.WAITING_PAYMENT.name());
            realtimeEventPublisher.publishEncounterChannel(savedEncounterId, "SERVICE_ORDER_CREATED");
            internalNotificationService.notifyRole(
                    UserRole.CASHIER,
                    "SERVICE_ORDER_CREATED",
                    InternalNotificationService.SEVERITY_INFO,
                    "Có phiếu chỉ định mới",
                    "Phiếu chỉ định " + code + " của bệnh nhân " + patientName + " đang chờ tạo hóa đơn.",
                    "/app/cashier/invoices",
                    "SERVICE_ORDER",
                    savedId
            );
        });

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ServiceOrderResponse get(Long id) {
        return toResponse(
                serviceOrderRepository.findById(id)
                                      .orElseThrow(() -> new ApiException(ErrorCode.SERVICE_ORDER_NOT_FOUND))
        );
    }

    private Encounter getEncounterForDoctor(Long encounterId, Long doctorUserId) {
        User doctorUser = userRepository.findById(doctorUserId)
                                        .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));

        if (doctorUser.getDoctorProfile() == null || doctorUser.getDoctorProfile().getId() == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Bạn không thuộc bác sĩ phụ trách lần khám này");
        }

        return encounterRepository.findByIdAndDoctor_Id(encounterId, doctorUser.getDoctorProfile().getId())
                                  .orElseThrow(() -> new ApiException(ErrorCode.ENCOUNTER_NOT_FOUND));
    }

    private String generateCode(Encounter encounter) {
        return "SO" + encounter.getStartedAt().format(DateTimeFormatter.ofPattern("yyMMddHHmmss"))
                + String.format("%04d", encounter.getId() % 10000);
    }

    private String normalizeDepartmentCode(String departmentCode) {
        String trimmed = StringUtil.trimToNull(departmentCode);
        return trimmed != null ? trimmed.toUpperCase(Locale.ROOT) : null;
    }

    private ServiceOrderResponse toResponse(ServiceOrder order) {
        Map<Long, ServiceResult> resultByItemId = serviceResultRepository
                .findByServiceOrderItem_ServiceOrder_Encounter_Id(order.getEncounter().getId())
                .stream()
                .collect(Collectors.toMap(r -> r.getServiceOrderItem().getId(), Function.identity()));

        return toResponse(order, resultByItemId);
    }

    private ServiceOrderResponse toResponse(ServiceOrder order, Map<Long, ServiceResult> resultByItemId) {
        return ServiceOrderResponse.builder()
                                   .id(order.getId())
                                   .code(order.getCode())
                                   .encounterId(order.getEncounter().getId())
                                   .status(order.getStatus())
                                   .paymentStatus(order.getPaymentStatus())
                                   .estimatedTotalAmount(order.getEstimatedTotalAmount())
                                   .note(order.getNote())
                                   .orderedAt(order.getOrderedAt())
                                   .paidAt(order.getPaidAt())
                                   .items(order.getItems().stream().map(i -> {
                                       ServiceResult result = resultByItemId.get(i.getId());
                                       var templateCode = result != null && result.getTemplateCode() != null
                                               ? result.getTemplateCode()
                                               : ServiceResultTemplateSupport.resolveTemplateCode(i.getMedicalService());
                                       var templateSchemaJson = result != null && result.getTemplateSchemaJson() != null && !result.getTemplateSchemaJson().isBlank()
                                               ? result.getTemplateSchemaJson()
                                               : (i.getMedicalService() != null && i.getMedicalService().getResultTemplateSchemaJson() != null && !i.getMedicalService().getResultTemplateSchemaJson().isBlank()
                                               ? i.getMedicalService().getResultTemplateSchemaJson()
                                               : ServiceResultTemplateSupport.defaultSchemaJson(templateCode));
                                       var reportTitle = result != null && result.getReportTitle() != null && !result.getReportTitle().isBlank()
                                               ? result.getReportTitle()
                                               : ServiceResultTemplateSupport.resolveReportTitle(
                                               i.getMedicalService() != null ? i.getMedicalService().getResultReportTitle() : null,
                                               i.getServiceNameVnSnapshot()
                                       );
                                       return ServiceOrderItemResponse.builder()
                                                                      .id(i.getId())
                                                                      .medicalServiceId(i.getMedicalService().getId())
                                                                      .serviceCode(i.getServiceCodeSnapshot())
                                                                      .serviceNameVn(i.getServiceNameVnSnapshot())
                                                                      .serviceNameEn(i.getServiceNameEnSnapshot())
                                                                      .price(i.getPriceSnapshot())
                                                                      .quantity(i.getQuantity())
                                                                      .lineTotalAmount(i.getLineTotalAmount())
                                                                      .departmentCode(i.getAssignedDepartmentCode())
                                                                      .queueNo(i.getQueueNo())
                                                                      .status(i.getStatus())
                                                                      .resultStatus(i.getResultStatus())
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
                                                                      .reportPdfUrl(result != null && result.getReportPdfPath() != null && !result.getReportPdfPath().isBlank() ? "/api/service-desk/results/" + i.getId() + "/pdf" : null)
                                                                      .reportPdfStatus(result != null ? result.getReportPdfStatus() : null)
                                                                      .reportPdfErrorMessage(result != null ? result.getReportPdfErrorMessage() : null)
                                                                      .reportPdfGeneratedAt(result != null ? result.getReportPdfGeneratedAt() : null)
                                                                      .resultPerformedAt(result != null ? result.getPerformedAt() : null)
                                                                      .build();
                                   }).toList())
                                   .build();
    }
}
