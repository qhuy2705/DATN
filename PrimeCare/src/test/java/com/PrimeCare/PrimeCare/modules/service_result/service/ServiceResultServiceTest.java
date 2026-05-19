package com.PrimeCare.PrimeCare.modules.service_result.service;

import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.encounter.entity.Encounter;
import com.PrimeCare.PrimeCare.modules.encounter.service.EncounterWorkflowService;
import com.PrimeCare.PrimeCare.modules.file.service.FileStorageService;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.medical_service.entity.MedicalService;
import com.PrimeCare.PrimeCare.modules.notification.service.InternalNotificationService;
import com.PrimeCare.PrimeCare.modules.realtime.service.AfterCommitExecutor;
import com.PrimeCare.PrimeCare.modules.realtime.service.RealtimeEventPublisher;
import com.PrimeCare.PrimeCare.modules.service_order.entity.ServiceOrder;
import com.PrimeCare.PrimeCare.modules.service_order.entity.ServiceOrderItem;
import com.PrimeCare.PrimeCare.modules.service_order.repository.ServiceOrderItemRepository;
import com.PrimeCare.PrimeCare.modules.service_result.dto.request.SubmitServiceResultRequest;
import com.PrimeCare.PrimeCare.modules.service_result.entity.ServiceResult;
import com.PrimeCare.PrimeCare.modules.service_result.repository.ServiceDeskMetricsRepository;
import com.PrimeCare.PrimeCare.modules.service_result.repository.ServiceResultRepository;
import com.PrimeCare.PrimeCare.shared.enums.EncounterStatus;
import com.PrimeCare.PrimeCare.shared.enums.MedicalServiceStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceOrderItemStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceOrderStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceResultStatus;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceResultServiceTest {

    @Mock
    private ServiceOrderItemRepository itemRepository;
    @Mock
    private ServiceResultRepository resultRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private RealtimeEventPublisher realtimeEventPublisher;
    @Mock
    private AfterCommitExecutor afterCommitExecutor;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private ServiceResultStatusHistoryService serviceResultStatusHistoryService;
    @Mock
    private ServiceDeskMetricsRepository serviceDeskMetricsRepository;
    @Mock
    private EncounterWorkflowService encounterWorkflowService;
    @Mock
    private InternalNotificationService internalNotificationService;

    private ServiceResultService service;

    @BeforeEach
    void setUp() {
        service = new ServiceResultService(
                itemRepository,
                resultRepository,
                userRepository,
                auditLogService,
                realtimeEventPublisher,
                afterCommitExecutor,
                rabbitTemplate,
                new ObjectMapper(),
                fileStorageService,
                serviceResultStatusHistoryService,
                serviceDeskMetricsRepository,
                encounterWorkflowService,
                internalNotificationService
        );
    }

    @Test
    void shouldRejectSubmitWhenItemIsPendingPayment() {
        ServiceOrderItem item = item(ServiceOrderItemStatus.PENDING_PAYMENT);
        when(itemRepository.findWithLockById(13L)).thenReturn(Optional.of(item));
        when(userRepository.findById(99L)).thenReturn(Optional.of(technician()));

        assertThatThrownBy(() -> service.submit(13L, 99L, new SubmitServiceResultRequest()))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SERVICE_ORDER_INVALID_STATUS)
                );

        verify(resultRepository, never()).save(any());
    }

    @Test
    void shouldRejectSubmitWhenItemIsCancelled() {
        ServiceOrderItem item = item(ServiceOrderItemStatus.CANCELLED);
        when(itemRepository.findWithLockById(13L)).thenReturn(Optional.of(item));
        when(userRepository.findById(99L)).thenReturn(Optional.of(technician()));

        assertThatThrownBy(() -> service.submit(13L, 99L, new SubmitServiceResultRequest()))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SERVICE_ORDER_INVALID_STATUS)
                );

        verify(resultRepository, never()).save(any());
    }

    @Test
    void shouldSubmitWaitingExecutionItemAndCreateCompletedResultUnderItemLock() {
        ServiceOrderItem item = item(ServiceOrderItemStatus.WAITING_EXECUTION);
        SubmitServiceResultRequest request = new SubmitServiceResultRequest();
        request.setResultTextVn("Kết quả bình thường");
        ArgumentCaptor<ServiceResult> resultCaptor = ArgumentCaptor.forClass(ServiceResult.class);

        when(itemRepository.findWithLockById(13L)).thenReturn(Optional.of(item));
        when(userRepository.findById(99L)).thenReturn(Optional.of(technician()));
        when(resultRepository.findByServiceOrderItem_Id(13L)).thenReturn(Optional.empty());
        when(resultRepository.save(resultCaptor.capture())).thenAnswer(invocation -> {
            ServiceResult result = invocation.getArgument(0);
            result.setId(21L);
            return result;
        });
        when(encounterWorkflowService.refreshStatus(item.getServiceOrder().getEncounter()))
                .thenReturn(EncounterStatus.READY_FOR_CONCLUSION);

        var response = service.submit(13L, 99L, request);

        assertThat(response.getStatus()).isEqualTo(ServiceResultStatus.COMPLETED);
        assertThat(item.getStatus()).isEqualTo(ServiceOrderItemStatus.DONE);
        assertThat(item.getResultStatus()).isEqualTo(ServiceResultStatus.COMPLETED);
        assertThat(item.getServiceOrder().getStatus()).isEqualTo(ServiceOrderStatus.COMPLETED);
        assertThat(resultCaptor.getValue().getStatus()).isEqualTo(ServiceResultStatus.COMPLETED);
        verify(itemRepository).findWithLockById(13L);
    }

    @Test
    void shouldRejectSubmitWhenExistingResultIsVerified() {
        ServiceOrderItem item = item(ServiceOrderItemStatus.DONE);
        ServiceResult result = result(item, ServiceResultStatus.VERIFIED);
        when(itemRepository.findWithLockById(13L)).thenReturn(Optional.of(item));
        when(userRepository.findById(99L)).thenReturn(Optional.of(technician()));
        when(resultRepository.findByServiceOrderItem_Id(13L)).thenReturn(Optional.of(result));

        assertThatThrownBy(() -> service.submit(13L, 99L, new SubmitServiceResultRequest()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("Verified service results cannot be edited.");
                });

        verify(resultRepository, never()).save(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any(), any());
        verify(serviceResultStatusHistoryService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void shouldUpdateExistingNonVerifiedResultAndKeepAudit() {
        ServiceOrderItem item = item(ServiceOrderItemStatus.DONE);
        ServiceResult result = result(item, ServiceResultStatus.COMPLETED);
        User technician = technician();
        SubmitServiceResultRequest request = new SubmitServiceResultRequest();
        request.setResultTextVn("Kết quả đã cập nhật");

        when(itemRepository.findWithLockById(13L)).thenReturn(Optional.of(item));
        when(userRepository.findById(99L)).thenReturn(Optional.of(technician));
        when(resultRepository.findByServiceOrderItem_Id(13L)).thenReturn(Optional.of(result));
        when(resultRepository.save(result)).thenReturn(result);
        when(encounterWorkflowService.refreshStatus(item.getServiceOrder().getEncounter()))
                .thenReturn(EncounterStatus.READY_FOR_CONCLUSION);

        var response = service.submit(13L, 99L, request);

        assertThat(response.getStatus()).isEqualTo(ServiceResultStatus.COMPLETED);
        assertThat(result.getResultTextVn()).isEqualTo("Kết quả đã cập nhật");
        assertThat(item.getResultStatus()).isEqualTo(ServiceResultStatus.COMPLETED);
        verify(auditLogService).log(eq(technician), eq("UPDATE_SERVICE_RESULT"), eq("SERVICE_RESULT"), eq(21L), any(), any());
        verify(serviceResultStatusHistoryService).record(eq(result), eq(ServiceResultStatus.COMPLETED), eq(ServiceResultStatus.COMPLETED), eq(technician), eq("Result updated by technician"));
    }

    @Test
    void shouldRejectVerifyWhenItemIsNotDone() {
        ServiceOrderItem item = item(ServiceOrderItemStatus.IN_PROGRESS);
        ServiceResult result = result(item, ServiceResultStatus.COMPLETED);
        when(itemRepository.findWithLockById(13L)).thenReturn(Optional.of(item));
        when(userRepository.findById(99L)).thenReturn(Optional.of(technician()));
        when(resultRepository.findByServiceOrderItem_Id(13L)).thenReturn(Optional.of(result));

        assertThatThrownBy(() -> service.verify(13L, 99L))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SERVICE_ORDER_INVALID_STATUS)
                );

        verify(resultRepository, never()).save(any());
    }

    @Test
    void shouldVerifyCompletedResultUnderItemLock() {
        ServiceOrderItem item = item(ServiceOrderItemStatus.DONE);
        ServiceResult result = result(item, ServiceResultStatus.COMPLETED);
        when(itemRepository.findWithLockById(13L)).thenReturn(Optional.of(item));
        when(userRepository.findById(99L)).thenReturn(Optional.of(technician()));
        when(resultRepository.findByServiceOrderItem_Id(13L)).thenReturn(Optional.of(result));
        when(resultRepository.save(result)).thenReturn(result);

        var response = service.verify(13L, 99L);

        assertThat(response.getStatus()).isEqualTo(ServiceResultStatus.VERIFIED);
        assertThat(item.getResultStatus()).isEqualTo(ServiceResultStatus.VERIFIED);
        assertThat(result.getVerifiedByUser().getId()).isEqualTo(99L);
        assertThat(result.getVerifiedAt()).isNotNull();
        verify(itemRepository).findWithLockById(13L);
    }

    @Test
    void shouldReturnCurrentResultWhenVerifyingAlreadyVerifiedResult() {
        ServiceOrderItem item = item(ServiceOrderItemStatus.DONE);
        item.setResultStatus(ServiceResultStatus.VERIFIED);
        ServiceResult result = result(item, ServiceResultStatus.VERIFIED);
        when(itemRepository.findWithLockById(13L)).thenReturn(Optional.of(item));
        when(userRepository.findById(99L)).thenReturn(Optional.of(technician()));
        when(resultRepository.findByServiceOrderItem_Id(13L)).thenReturn(Optional.of(result));

        var response = service.verify(13L, 99L);

        assertThat(response.getStatus()).isEqualTo(ServiceResultStatus.VERIFIED);
        verify(resultRepository, never()).save(any());
    }

    @Test
    void shouldListVerifiedResultWithoutMutatingIt() {
        ServiceOrderItem item = item(ServiceOrderItemStatus.DONE);
        item.setResultStatus(ServiceResultStatus.VERIFIED);
        ServiceResult result = result(item, ServiceResultStatus.VERIFIED);
        Pageable pageable = PageRequest.of(0, 10);
        when(itemRepository.searchServiceDeskQueue(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(item), pageable, 1));
        when(resultRepository.findByServiceOrderItem_IdIn(List.of(13L))).thenReturn(List.of(result));

        var response = service.searchQueue(null, null, ServiceResultStatus.VERIFIED, null, pageable);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().getFirst().getResultStatus()).isEqualTo(ServiceResultStatus.VERIFIED);
        assertThat(response.getItems().getFirst().getResultTextVn()).isEqualTo("Kết quả");
        verify(resultRepository, never()).save(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any(), any());
    }

    private ServiceOrderItem item(ServiceOrderItemStatus status) {
        Branch branch = Branch.builder().id(1L).nameVn("PrimeCare").build();
        DoctorProfile doctor = DoctorProfile.builder().id(3L).fullName("Dr Test").build();
        Encounter encounter = Encounter.builder()
                .id(5L)
                .code("ENC-1")
                .doctor(doctor)
                .branch(branch)
                .patientFullNameSnapshot("Patient Test")
                .status(EncounterStatus.WAITING_RESULTS)
                .build();
        ServiceOrder order = ServiceOrder.builder()
                .id(7L)
                .code("SO-1")
                .encounter(encounter)
                .branch(branch)
                .status(ServiceOrderStatus.IN_PROGRESS)
                .items(new ArrayList<>())
                .build();
        MedicalService medicalService = MedicalService.builder()
                .id(11L)
                .code("CBC")
                .nameVn("Công thức máu")
                .basePrice(100_000L)
                .status(MedicalServiceStatus.ACTIVE)
                .build();
        ServiceOrderItem item = ServiceOrderItem.builder()
                .id(13L)
                .serviceOrder(order)
                .medicalService(medicalService)
                .serviceCodeSnapshot("CBC")
                .serviceNameVnSnapshot("Công thức máu")
                .priceSnapshot(100_000L)
                .quantity(1)
                .lineTotalAmount(100_000L)
                .assignedDepartmentCode("LAB")
                .status(status)
                .resultStatus(ServiceResultStatus.DRAFT)
                .build();
        order.setItems(new ArrayList<>(List.of(item)));
        return item;
    }

    private ServiceResult result(ServiceOrderItem item, ServiceResultStatus status) {
        return ServiceResult.builder()
                .id(21L)
                .serviceOrderItem(item)
                .resultTextVn("Kết quả")
                .status(status)
                .build();
    }

    private User technician() {
        return User.builder()
                .id(99L)
                .role(UserRole.SERVICE_TECHNICIAN)
                .email("tech@example.test")
                .build();
    }
}
