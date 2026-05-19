package com.PrimeCare.PrimeCare.modules.billing.service;

import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.billing.dto.request.RefundInvoiceItemsRequest;
import com.PrimeCare.PrimeCare.modules.billing.dto.response.RefundableInvoiceItemsResponse;
import com.PrimeCare.PrimeCare.modules.billing.entity.Invoice;
import com.PrimeCare.PrimeCare.modules.billing.entity.InvoiceItem;
import com.PrimeCare.PrimeCare.modules.billing.entity.RefundRecord;
import com.PrimeCare.PrimeCare.modules.billing.repository.BankTransactionRepository;
import com.PrimeCare.PrimeCare.modules.billing.repository.InvoiceRepository;
import com.PrimeCare.PrimeCare.modules.billing.repository.PaymentIntentRepository;
import com.PrimeCare.PrimeCare.modules.billing.repository.RefundRecordItemRepository;
import com.PrimeCare.PrimeCare.modules.billing.repository.RefundRecordRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.medication.entity.Medication;
import com.PrimeCare.PrimeCare.modules.notification.service.InternalNotificationService;
import com.PrimeCare.PrimeCare.modules.patient.entity.Patient;
import com.PrimeCare.PrimeCare.modules.prescription.entity.Prescription;
import com.PrimeCare.PrimeCare.modules.prescription.entity.PrescriptionItem;
import com.PrimeCare.PrimeCare.modules.prescription.repository.PrescriptionItemRepository;
import com.PrimeCare.PrimeCare.modules.prescription.repository.PrescriptionRepository;
import com.PrimeCare.PrimeCare.modules.realtime.service.AfterCommitExecutor;
import com.PrimeCare.PrimeCare.modules.realtime.service.RealtimeEventPublisher;
import com.PrimeCare.PrimeCare.modules.service_order.entity.ServiceOrder;
import com.PrimeCare.PrimeCare.modules.service_order.entity.ServiceOrderItem;
import com.PrimeCare.PrimeCare.modules.service_order.repository.ServiceOrderItemRepository;
import com.PrimeCare.PrimeCare.modules.service_order.repository.ServiceOrderRepository;
import com.PrimeCare.PrimeCare.modules.service_order.service.DepartmentQueueAllocator;
import com.PrimeCare.PrimeCare.modules.encounter.entity.Encounter;
import com.PrimeCare.PrimeCare.modules.service_result.repository.ServiceResultRepository;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.modules.encounter.service.EncounterWorkflowService;
import com.PrimeCare.PrimeCare.shared.enums.EncounterStatus;
import com.PrimeCare.PrimeCare.shared.enums.InvoiceItemSourceType;
import com.PrimeCare.PrimeCare.shared.enums.PaymentMethod;
import com.PrimeCare.PrimeCare.shared.enums.PaymentStatus;
import com.PrimeCare.PrimeCare.shared.enums.PrescriptionItemStatus;
import com.PrimeCare.PrimeCare.shared.enums.PrescriptionStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceOrderItemStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceOrderStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceResultStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingServiceRefundTest {

    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private ServiceOrderRepository orderRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private VnpayPaymentService vnpayPaymentService;
    @Mock
    private BillingQrService billingQrService;
    @Mock
    private PaymentIntentRepository paymentIntentRepository;
    @Mock
    private BankTransactionRepository bankTransactionRepository;
    @Mock
    private DepartmentQueueAllocator departmentQueueAllocator;
    @Mock
    private RealtimeEventPublisher realtimeEventPublisher;
    @Mock
    private AfterCommitExecutor afterCommitExecutor;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private InvoiceStatusHistoryService invoiceStatusHistoryService;
    @Mock
    private RefundRecordRepository refundRecordRepository;
    @Mock
    private RefundRecordItemRepository refundRecordItemRepository;
    @Mock
    private EncounterWorkflowService encounterWorkflowService;
    @Mock
    private InternalNotificationService internalNotificationService;
    @Mock
    private PrescriptionRepository prescriptionRepository;
    @Mock
    private PrescriptionItemRepository prescriptionItemRepository;
    @Mock
    private ServiceOrderItemRepository serviceOrderItemRepository;
    @Mock
    private ServiceResultRepository serviceResultRepository;

    private BillingService service;

    @BeforeEach
    void setUp() {
        service = new BillingService(
                invoiceRepository,
                orderRepository,
                userRepository,
                vnpayPaymentService,
                billingQrService,
                paymentIntentRepository,
                bankTransactionRepository,
                departmentQueueAllocator,
                realtimeEventPublisher,
                afterCommitExecutor,
                auditLogService,
                invoiceStatusHistoryService,
                refundRecordRepository,
                refundRecordItemRepository,
                encounterWorkflowService,
                internalNotificationService,
                prescriptionRepository,
                prescriptionItemRepository,
                serviceOrderItemRepository,
                serviceResultRepository
        );
    }

    @Test
    void refundInvoiceDefaultsNullAmountToFullInvoiceAmount() {
        Invoice invoice = paidInvoice(100L);
        ServiceOrder order = invoice.getServiceOrder();
        User cashier = User.builder().id(7L).email("cashier@example.test").build();
        when(invoiceRepository.findWithLockById(1L)).thenReturn(Optional.of(invoice));
        when(userRepository.findById(7L)).thenReturn(Optional.of(cashier));
        when(orderRepository.findWithLockById(order.getId())).thenReturn(Optional.of(order));
        when(encounterWorkflowService.resolveStatus(order.getEncounter())).thenReturn(EncounterStatus.IN_PROGRESS);
        when(invoiceRepository.save(invoice)).thenReturn(invoice);

        var response = service.refundInvoice(1L, null, "reason", 7L);

        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(refundRecordRepository).save(any(RefundRecord.class));
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void refundInvoiceRejectsNonPositiveAmount(Long refundAmount) {
        when(invoiceRepository.findWithLockById(1L)).thenReturn(Optional.of(paidInvoice(100L)));

        assertThatThrownBy(() -> service.refundInvoice(1L, refundAmount, "reason", 7L))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR)
                );

        verifyNoInteractions(userRepository, refundRecordRepository);
    }

    @Test
    void refundInvoiceRejectsNonFullAmount() {
        when(invoiceRepository.findWithLockById(1L)).thenReturn(Optional.of(paidInvoice(100L)));

        assertThatThrownBy(() -> service.refundInvoice(1L, 50L, "reason", 7L))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.REFUND_NOT_ALLOWED)
                );

        verifyNoInteractions(userRepository, refundRecordRepository);
    }

    @Test
    void refundInvoiceRejectsUnpaidInvoice() {
        Invoice invoice = paidInvoice(100L);
        invoice.setPaymentStatus(PaymentStatus.UNPAID);
        when(invoiceRepository.findWithLockById(1L)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> service.refundInvoice(1L, 50L, "reason", 7L))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.REFUND_NOT_ALLOWED)
                );

        verifyNoInteractions(userRepository, refundRecordRepository);
    }

    @Test
    void paymentStatusVoidRemainsBackendEnumValue() {
        assertThat(PaymentStatus.VOID).isNotNull();
    }

    @Test
    void refundInvoiceAppliesValidRefund() {
        Invoice invoice = paidInvoice(100L);
        ServiceOrder order = invoice.getServiceOrder();
        User cashier = User.builder().id(7L).email("cashier@example.test").build();
        when(invoiceRepository.findWithLockById(1L)).thenReturn(Optional.of(invoice));
        when(userRepository.findById(7L)).thenReturn(Optional.of(cashier));
        when(orderRepository.findWithLockById(order.getId())).thenReturn(Optional.of(order));
        when(encounterWorkflowService.resolveStatus(order.getEncounter())).thenReturn(EncounterStatus.IN_PROGRESS);
        when(invoiceRepository.save(invoice)).thenReturn(invoice);

        var response = service.refundInvoice(1L, 100L, "reason", 7L);

        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(order.getStatus()).isEqualTo(ServiceOrderStatus.CANCELLED);
        verify(refundRecordRepository).save(any(RefundRecord.class));
        verify(invoiceStatusHistoryService).record(any(), any(), any(), any(), any());
        verify(auditLogService).log(any(), any(), any(), any(), any(), any());
    }

    @Test
    void fullRefundRejectsCompletedClinicalItemAndKeepsCompletedEncounterUntouched() {
        Invoice invoice = paidInvoiceWithCompletedClinicalLifecycle(100L);
        ServiceOrder order = invoice.getServiceOrder();
        Encounter encounter = order.getEncounter();
        Appointment appointment = encounter.getAppointment();
        LocalDateTime appointmentCompletedAt = appointment.getCompletedAt();
        LocalDateTime encounterCompletedAt = encounter.getCompletedAt();
        User cashier = User.builder().id(7L).email("cashier@example.test").build();
        when(invoiceRepository.findWithLockById(1L)).thenReturn(Optional.of(invoice));
        when(userRepository.findById(7L)).thenReturn(Optional.of(cashier));
        when(orderRepository.findWithLockById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.refundInvoice(1L, 100L, "reason", 7L))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.REFUND_NOT_ALLOWED)
                );

        assertThat(invoice.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getStatus()).isEqualTo(ServiceOrderStatus.COMPLETED);
        assertThat(order.getItems())
                .extracting(ServiceOrderItem::getStatus)
                .containsExactly(ServiceOrderItemStatus.DONE);
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
        assertThat(appointment.getCompletedAt()).isEqualTo(appointmentCompletedAt);
        assertThat(encounter.getStatus()).isEqualTo(EncounterStatus.COMPLETED);
        assertThat(encounter.getCompletedAt()).isEqualTo(encounterCompletedAt);
        verify(invoiceRepository, never()).save(any(Invoice.class));
        verify(auditLogService, never()).log(any(), any(), any(), any(), any(), any());
    }

    @Test
    void refundableItemsReturnsServiceItemAsRefundableWhenWaitingExecutionAndDraftResult() {
        ServiceOrderItem sourceItem = serviceItem(17L, ServiceOrderItemStatus.WAITING_EXECUTION, ServiceResultStatus.DRAFT, 100L);
        Invoice invoice = paidServiceInvoiceWithItems(sourceItem);
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));
        when(serviceOrderItemRepository.findById(17L)).thenReturn(Optional.of(sourceItem));
        when(serviceResultRepository.findByServiceOrderItem_Id(17L)).thenReturn(Optional.empty());

        RefundableInvoiceItemsResponse response = service.getRefundableItems(1L);

        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(response.getRefundedAmount()).isZero();
        assertThat(response.getRemainingAmount()).isEqualTo(100L);
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().getFirst().isRefundable()).isTrue();
        assertThat(response.getItems().getFirst().getSourceItemType()).isEqualTo("SERVICE_ORDER_ITEM");
        assertThat(response.getItems().getFirst().getSourceItemId()).isEqualTo(17L);
        assertThat(response.getItems().getFirst().getCurrentStatus()).isEqualTo("WAITING_EXECUTION");
        assertThat(response.getItems().getFirst().getResultStatus()).isEqualTo("DRAFT");
        assertThat(response.getItems().getFirst().getGroup()).isEqualTo("SERVICE");
    }

    @Test
    void refundableItemsReturnsServiceItemNotRefundableWhenInProgress() {
        ServiceOrderItem sourceItem = serviceItem(17L, ServiceOrderItemStatus.IN_PROGRESS, ServiceResultStatus.DRAFT, 100L);
        Invoice invoice = paidServiceInvoiceWithItems(sourceItem);
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));
        when(serviceOrderItemRepository.findById(17L)).thenReturn(Optional.of(sourceItem));
        when(serviceResultRepository.findByServiceOrderItem_Id(17L)).thenReturn(Optional.empty());

        RefundableInvoiceItemsResponse response = service.getRefundableItems(1L);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().getFirst().isRefundable()).isFalse();
        assertThat(response.getItems().getFirst().getNotRefundableReason()).isEqualTo("Service item has already started.");
        assertThat(response.getItems().getFirst().getRefundableAmount()).isZero();
    }

    @Test
    void refundableItemsReturnsServiceItemNotRefundableWhenResultCompleted() {
        ServiceOrderItem sourceItem = serviceItem(17L, ServiceOrderItemStatus.WAITING_EXECUTION, ServiceResultStatus.COMPLETED, 100L);
        Invoice invoice = paidServiceInvoiceWithItems(sourceItem);
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));
        when(serviceOrderItemRepository.findById(17L)).thenReturn(Optional.of(sourceItem));
        when(serviceResultRepository.findByServiceOrderItem_Id(17L)).thenReturn(Optional.empty());

        RefundableInvoiceItemsResponse response = service.getRefundableItems(1L);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().getFirst().isRefundable()).isFalse();
        assertThat(response.getItems().getFirst().getNotRefundableReason())
                .isEqualTo("Service result is already completed or under verification.");
    }

    @Test
    void refundSelectedServiceItemCancelsSourceCreatesRecordAndMarksInvoicePartiallyRefunded() {
        ServiceOrderItem selectedSource = serviceItem(17L, ServiceOrderItemStatus.WAITING_EXECUTION, ServiceResultStatus.DRAFT, 100L);
        ServiceOrderItem remainingSource = serviceItem(18L, ServiceOrderItemStatus.WAITING_EXECUTION, ServiceResultStatus.DRAFT, 200L);
        Invoice invoice = paidServiceInvoiceWithItems(selectedSource, remainingSource);
        InvoiceItem selectedInvoiceItem = invoice.getItems().getFirst();
        User cashier = User.builder().id(7L).email("cashier@example.test").build();
        when(invoiceRepository.findWithLockById(1L)).thenReturn(Optional.of(invoice));
        when(serviceOrderItemRepository.findWithLockById(17L)).thenReturn(Optional.of(selectedSource));
        when(serviceResultRepository.findByServiceOrderItem_Id(17L)).thenReturn(Optional.empty());
        when(userRepository.findById(7L)).thenReturn(Optional.of(cashier));
        when(invoiceRepository.save(invoice)).thenReturn(invoice);

        var response = service.refundInvoiceItems(
                1L,
                refundItemsRequest("Patient changed mind", selectedInvoiceItem.getId()),
                7L
        );

        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(response.getRefundedAmount()).isEqualTo(100L);
        assertThat(response.getRemainingAmount()).isEqualTo(200L);
        assertThat(invoice.getTotalAmount()).isEqualTo(300L);
        assertThat(invoice.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(invoice.getServiceOrder().getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(selectedInvoiceItem.getRefundedAmount()).isEqualTo(100L);
        assertThat(selectedSource.getStatus()).isEqualTo(ServiceOrderItemStatus.CANCELLED);
        assertThat(selectedSource.getRefundReason()).isEqualTo("Patient changed mind");
        assertThat(selectedSource.getRefundedByUser()).isEqualTo(cashier);
        assertThat(remainingSource.getStatus()).isEqualTo(ServiceOrderItemStatus.WAITING_EXECUTION);
        verify(refundRecordRepository).save(any(RefundRecord.class));
        verify(refundRecordItemRepository).saveAll(any());
        verify(auditLogService).log(eq(cashier), eq("REFUND_INVOICE_ITEMS"), eq("INVOICE"), eq(1L), any(), any());
    }

    @Test
    void refundSelectedServiceItemsMarksInvoiceFullyRefundedWhenAllItemsRefunded() {
        ServiceOrderItem first = serviceItem(17L, ServiceOrderItemStatus.WAITING_EXECUTION, ServiceResultStatus.DRAFT, 100L);
        ServiceOrderItem second = serviceItem(18L, ServiceOrderItemStatus.WAITING_EXECUTION, ServiceResultStatus.DRAFT, 200L);
        Invoice invoice = paidServiceInvoiceWithItems(first, second);
        User cashier = User.builder().id(7L).email("cashier@example.test").build();
        when(invoiceRepository.findWithLockById(1L)).thenReturn(Optional.of(invoice));
        when(serviceOrderItemRepository.findWithLockById(17L)).thenReturn(Optional.of(first));
        when(serviceOrderItemRepository.findWithLockById(18L)).thenReturn(Optional.of(second));
        when(serviceResultRepository.findByServiceOrderItem_Id(17L)).thenReturn(Optional.empty());
        when(serviceResultRepository.findByServiceOrderItem_Id(18L)).thenReturn(Optional.empty());
        when(userRepository.findById(7L)).thenReturn(Optional.of(cashier));
        when(invoiceRepository.save(invoice)).thenReturn(invoice);

        var response = service.refundInvoiceItems(
                1L,
                refundItemsRequest("All items cancelled", invoice.getItems().get(0).getId(), invoice.getItems().get(1).getId()),
                7L
        );

        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(response.getRefundedAmount()).isEqualTo(300L);
        assertThat(response.getRemainingAmount()).isZero();
        assertThat(invoice.getServiceOrder().getStatus()).isEqualTo(ServiceOrderStatus.CANCELLED);
        assertThat(invoice.getServiceOrder().getItems())
                .extracting(ServiceOrderItem::getStatus)
                .containsExactly(ServiceOrderItemStatus.CANCELLED, ServiceOrderItemStatus.CANCELLED);
    }

    @Test
    void refundSelectedPrescriptionItemBeforeDispenseMarksItemRefundedAndKeepsRemainingPrescriptionPaid() {
        PrescriptionItem selected = prescriptionItem(31L, 501L, 100L, 1, PrescriptionItemStatus.PAID);
        PrescriptionItem remaining = prescriptionItem(32L, 502L, 200L, 1, PrescriptionItemStatus.PAID);
        Invoice invoice = paidPrescriptionInvoiceWithItems(selected, remaining);
        User cashier = User.builder().id(7L).email("cashier@example.test").build();
        when(invoiceRepository.findWithLockById(1L)).thenReturn(Optional.of(invoice));
        when(prescriptionItemRepository.findWithLockById(31L)).thenReturn(Optional.of(selected));
        when(userRepository.findById(7L)).thenReturn(Optional.of(cashier));
        when(invoiceRepository.save(invoice)).thenReturn(invoice);

        var response = service.refundInvoiceItems(
                1L,
                refundItemsRequest("Medication not needed", invoice.getItems().getFirst().getId()),
                7L
        );

        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(response.getRefundedAmount()).isEqualTo(100L);
        assertThat(selected.getStatus()).isEqualTo(PrescriptionItemStatus.REFUNDED);
        assertThat(selected.getRefundReason()).isEqualTo("Medication not needed");
        assertThat(selected.getRefundedByUser()).isEqualTo(cashier);
        assertThat(remaining.getStatus()).isEqualTo(PrescriptionItemStatus.PAID);
        assertThat(invoice.getPrescription().getStatus()).isEqualTo(PrescriptionStatus.PAID);
        verify(refundRecordItemRepository).saveAll(any());
        verify(auditLogService).log(eq(cashier), eq("REFUND_INVOICE_ITEMS"), eq("INVOICE"), eq(1L), any(), any());
    }

    @Test
    void refundSelectedPrescriptionItemRejectsDispensedItemAndDoesNotAuditSuccess() {
        PrescriptionItem dispensed = prescriptionItem(31L, 501L, 100L, 1, PrescriptionItemStatus.DISPENSED);
        Invoice invoice = paidPrescriptionInvoiceWithItems(dispensed);
        when(invoiceRepository.findWithLockById(1L)).thenReturn(Optional.of(invoice));
        when(prescriptionItemRepository.findWithLockById(31L)).thenReturn(Optional.of(dispensed));

        assertThatThrownBy(() -> service.refundInvoiceItems(
                1L,
                refundItemsRequest("Medication not needed", invoice.getItems().getFirst().getId()),
                7L
        )).isInstanceOfSatisfying(ApiException.class, ex ->
                assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.REFUND_NOT_ALLOWED)
        );

        assertThat(dispensed.getStatus()).isEqualTo(PrescriptionItemStatus.DISPENSED);
        verify(refundRecordRepository, never()).save(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any(), any());
    }

    @Test
    void refundSelectedItemsRejectsDuplicateInvoiceItemIds() {
        ServiceOrderItem sourceItem = serviceItem(17L, ServiceOrderItemStatus.WAITING_EXECUTION, ServiceResultStatus.DRAFT, 100L);
        Invoice invoice = paidServiceInvoiceWithItems(sourceItem);
        Long invoiceItemId = invoice.getItems().getFirst().getId();

        assertThatThrownBy(() -> service.refundInvoiceItems(
                1L,
                refundItemsRequest("duplicate", invoiceItemId, invoiceItemId),
                7L
        )).isInstanceOfSatisfying(ApiException.class, ex ->
                assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR)
        );

        verifyNoInteractions(invoiceRepository, refundRecordRepository, auditLogService);
    }

    private RefundInvoiceItemsRequest refundItemsRequest(String reason, Long... invoiceItemIds) {
        RefundInvoiceItemsRequest request = new RefundInvoiceItemsRequest();
        request.setReason(reason);
        List<RefundInvoiceItemsRequest.Item> items = new ArrayList<>();
        for (Long invoiceItemId : invoiceItemIds) {
            RefundInvoiceItemsRequest.Item item = new RefundInvoiceItemsRequest.Item();
            item.setInvoiceItemId(invoiceItemId);
            items.add(item);
        }
        request.setItems(items);
        return request;
    }

    private ServiceOrderItem serviceItem(
            Long id,
            ServiceOrderItemStatus status,
            ServiceResultStatus resultStatus,
            Long totalAmount
    ) {
        return ServiceOrderItem.builder()
                .id(id)
                .serviceCodeSnapshot("SVC-" + id)
                .serviceNameVnSnapshot("Service " + id)
                .priceSnapshot(totalAmount)
                .quantity(1)
                .lineTotalAmount(totalAmount)
                .status(status)
                .resultStatus(resultStatus)
                .assignedDepartmentCode("LAB")
                .build();
    }

    private Invoice paidServiceInvoiceWithItems(ServiceOrderItem... sourceItems) {
        long totalAmount = List.of(sourceItems).stream()
                .mapToLong(item -> item.getLineTotalAmount() != null ? item.getLineTotalAmount() : 0L)
                .sum();
        Invoice invoice = paidInvoice(totalAmount);
        ServiceOrder order = invoice.getServiceOrder();
        order.setStatus(ServiceOrderStatus.PAID);
        order.getItems().clear();
        invoice.setItems(new ArrayList<>());

        for (ServiceOrderItem sourceItem : sourceItems) {
            sourceItem.setServiceOrder(order);
            order.getItems().add(sourceItem);
            InvoiceItem invoiceItem = InvoiceItem.builder()
                    .id(1000L + sourceItem.getId())
                    .invoice(invoice)
                    .referenceType(InvoiceItem.ReferenceType.CLINICAL_SERVICE)
                    .referenceId(9000L + sourceItem.getId())
                    .sourceItemType(InvoiceItemSourceType.SERVICE_ORDER_ITEM)
                    .sourceItemId(sourceItem.getId())
                    .nameSnapshot(sourceItem.getServiceNameVnSnapshot())
                    .unitPrice(sourceItem.getPriceSnapshot())
                    .quantity(sourceItem.getQuantity())
                    .subtotalAmount(sourceItem.getLineTotalAmount())
                    .taxAmount(0L)
                    .totalAmount(sourceItem.getLineTotalAmount())
                    .refundedAmount(0L)
                    .build();
            invoice.getItems().add(invoiceItem);
        }
        return invoice;
    }

    private PrescriptionItem prescriptionItem(
            Long id,
            Long medicationId,
            Long unitPrice,
            Integer quantity,
            PrescriptionItemStatus status
    ) {
        Medication medication = Medication.builder()
                .id(medicationId)
                .code("MED-" + medicationId)
                .name("Medication " + medicationId)
                .unit("tablet")
                .unitPrice(unitPrice)
                .build();
        return PrescriptionItem.builder()
                .id(id)
                .medication(medication)
                .medicationCodeSnapshot(medication.getCode())
                .medicationNameSnapshot(medication.getName())
                .unitSnapshot(medication.getUnit())
                .quantity(quantity)
                .status(status)
                .build();
    }

    private Invoice paidPrescriptionInvoiceWithItems(PrescriptionItem... sourceItems) {
        long totalAmount = List.of(sourceItems).stream()
                .mapToLong(item -> item.getMedication().getUnitPrice() * item.getQuantity())
                .sum();
        Encounter encounter = Encounter.builder()
                .id(44L)
                .code("ENC-RX")
                .patientFullNameSnapshot("Prescription Patient")
                .status(EncounterStatus.IN_PROGRESS)
                .build();
        Prescription prescription = Prescription.builder()
                .id(45L)
                .code("RX-1")
                .encounter(encounter)
                .doctorUser(User.builder().id(46L).build())
                .issuedDate(LocalDate.now())
                .status(PrescriptionStatus.PAID)
                .items(new ArrayList<>())
                .build();
        Invoice invoice = Invoice.builder()
                .id(1L)
                .code("INV-RX-1")
                .prescription(prescription)
                .paymentMethod(PaymentMethod.CASH)
                .paymentStatus(PaymentStatus.PAID)
                .subtotalAmount(totalAmount)
                .discountAmount(0L)
                .taxAmount(0L)
                .totalAmount(totalAmount)
                .items(new ArrayList<>())
                .build();

        for (PrescriptionItem sourceItem : sourceItems) {
            sourceItem.setPrescription(prescription);
            prescription.getItems().add(sourceItem);
            Long lineTotal = sourceItem.getMedication().getUnitPrice() * sourceItem.getQuantity();
            InvoiceItem invoiceItem = InvoiceItem.builder()
                    .id(2000L + sourceItem.getId())
                    .invoice(invoice)
                    .referenceType(InvoiceItem.ReferenceType.MEDICATION)
                    .referenceId(sourceItem.getMedication().getId())
                    .sourceItemType(InvoiceItemSourceType.PRESCRIPTION_ITEM)
                    .sourceItemId(sourceItem.getId())
                    .nameSnapshot(sourceItem.getMedicationNameSnapshot())
                    .unitPrice(sourceItem.getMedication().getUnitPrice())
                    .quantity(sourceItem.getQuantity())
                    .subtotalAmount(lineTotal)
                    .taxAmount(0L)
                    .totalAmount(lineTotal)
                    .refundedAmount(0L)
                    .build();
            invoice.getItems().add(invoiceItem);
        }
        return invoice;
    }

    private Invoice paidInvoice(Long totalAmount) {
        Branch branch = Branch.builder().id(11L).nameVn("PrimeCare").build();
        DoctorProfile doctor = DoctorProfile.builder().id(12L).fullName("Dr Test").build();
        Patient patient = Patient.builder().id(13L).fullName("Patient Test").build();
        Encounter encounter = Encounter.builder()
                .id(14L)
                .code("ENC-1")
                .doctor(doctor)
                .patient(patient)
                .patientFullNameSnapshot(patient.getFullName())
                .status(EncounterStatus.IN_PROGRESS)
                .build();
        ServiceOrder order = ServiceOrder.builder()
                .id(15L)
                .code("SO-1")
                .branch(branch)
                .encounter(encounter)
                .items(new ArrayList<>())
                .status(ServiceOrderStatus.COMPLETED)
                .paymentStatus(PaymentStatus.PAID)
                .build();

        return Invoice.builder()
                .id(1L)
                .code("INV-1")
                .serviceOrder(order)
                .paymentMethod(PaymentMethod.CASH)
                .paymentStatus(PaymentStatus.PAID)
                .subtotalAmount(totalAmount)
                .discountAmount(0L)
                .taxAmount(0L)
                .totalAmount(totalAmount)
                .items(new ArrayList<>())
                .build();
    }

    private Invoice paidInvoiceWithCompletedClinicalLifecycle(Long totalAmount) {
        Invoice invoice = paidInvoice(totalAmount);
        LocalDateTime completedAt = LocalDateTime.now().minusHours(1);
        Appointment appointment = Appointment.builder()
                .id(16L)
                .code("APT-1")
                .status(AppointmentStatus.COMPLETED)
                .completedAt(completedAt)
                .build();
        Encounter encounter = invoice.getServiceOrder().getEncounter();
        encounter.setAppointment(appointment);
        encounter.setStatus(EncounterStatus.COMPLETED);
        encounter.setCompletedAt(completedAt);
        invoice.getServiceOrder().getItems().add(ServiceOrderItem.builder()
                .id(17L)
                .serviceOrder(invoice.getServiceOrder())
                .status(ServiceOrderItemStatus.DONE)
                .build());
        return invoice;
    }
}
