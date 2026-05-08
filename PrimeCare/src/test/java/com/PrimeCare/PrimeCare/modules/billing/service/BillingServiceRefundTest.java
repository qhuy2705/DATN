package com.PrimeCare.PrimeCare.modules.billing.service;

import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.billing.entity.Invoice;
import com.PrimeCare.PrimeCare.modules.billing.entity.RefundRecord;
import com.PrimeCare.PrimeCare.modules.billing.repository.BankTransactionRepository;
import com.PrimeCare.PrimeCare.modules.billing.repository.InvoiceRepository;
import com.PrimeCare.PrimeCare.modules.billing.repository.PaymentIntentRepository;
import com.PrimeCare.PrimeCare.modules.billing.repository.RefundRecordRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.notification.service.InternalNotificationService;
import com.PrimeCare.PrimeCare.modules.patient.entity.Patient;
import com.PrimeCare.PrimeCare.modules.realtime.service.AfterCommitExecutor;
import com.PrimeCare.PrimeCare.modules.realtime.service.RealtimeEventPublisher;
import com.PrimeCare.PrimeCare.modules.service_order.entity.ServiceOrder;
import com.PrimeCare.PrimeCare.modules.service_order.repository.ServiceOrderRepository;
import com.PrimeCare.PrimeCare.modules.service_order.service.DepartmentQueueAllocator;
import com.PrimeCare.PrimeCare.modules.encounter.entity.Encounter;
import com.PrimeCare.PrimeCare.modules.encounter.service.EncounterWorkflowService;
import com.PrimeCare.PrimeCare.shared.enums.EncounterStatus;
import com.PrimeCare.PrimeCare.shared.enums.PaymentMethod;
import com.PrimeCare.PrimeCare.shared.enums.PaymentStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceOrderStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    private EncounterWorkflowService encounterWorkflowService;
    @Mock
    private InternalNotificationService internalNotificationService;

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
                encounterWorkflowService,
                internalNotificationService
        );
    }

    @Test
    void refundInvoiceRejectsNullAmount() {
        when(invoiceRepository.findWithLockById(1L)).thenReturn(Optional.of(paidInvoice(100L)));

        assertThatThrownBy(() -> service.refundInvoice(1L, null, "reason", 7L))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR)
                );

        verifyNoInteractions(userRepository, refundRecordRepository);
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
    void refundInvoiceRejectsAmountGreaterThanTotal() {
        when(invoiceRepository.findWithLockById(1L)).thenReturn(Optional.of(paidInvoice(100L)));

        assertThatThrownBy(() -> service.refundInvoice(1L, 101L, "reason", 7L))
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

        var response = service.refundInvoice(1L, 50L, "reason", 7L);

        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(order.getStatus()).isEqualTo(ServiceOrderStatus.CANCELLED);
        verify(refundRecordRepository).save(any(RefundRecord.class));
        verify(invoiceStatusHistoryService).record(any(), any(), any(), any(), any());
        verify(auditLogService).log(any(), any(), any(), any(), any(), any());
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
}
