package com.PrimeCare.PrimeCare.modules.billing.service;

import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.billing.dto.request.ChangeInvoicePaymentMethodRequest;
import com.PrimeCare.PrimeCare.modules.billing.dto.response.VnpayPaymentInit;
import com.PrimeCare.PrimeCare.modules.billing.entity.Invoice;
import com.PrimeCare.PrimeCare.modules.billing.entity.PaymentIntent;
import com.PrimeCare.PrimeCare.modules.billing.repository.BankTransactionRepository;
import com.PrimeCare.PrimeCare.modules.billing.repository.InvoiceRepository;
import com.PrimeCare.PrimeCare.modules.billing.repository.PaymentIntentRepository;
import com.PrimeCare.PrimeCare.modules.billing.repository.RefundRecordItemRepository;
import com.PrimeCare.PrimeCare.modules.billing.repository.RefundRecordRepository;
import com.PrimeCare.PrimeCare.modules.encounter.service.EncounterWorkflowService;
import com.PrimeCare.PrimeCare.modules.notification.service.InternalNotificationService;
import com.PrimeCare.PrimeCare.modules.prescription.entity.Prescription;
import com.PrimeCare.PrimeCare.modules.prescription.repository.PrescriptionItemRepository;
import com.PrimeCare.PrimeCare.modules.prescription.repository.PrescriptionRepository;
import com.PrimeCare.PrimeCare.modules.realtime.service.AfterCommitExecutor;
import com.PrimeCare.PrimeCare.modules.realtime.service.RealtimeEventPublisher;
import com.PrimeCare.PrimeCare.modules.service_order.repository.ServiceOrderItemRepository;
import com.PrimeCare.PrimeCare.modules.service_order.repository.ServiceOrderRepository;
import com.PrimeCare.PrimeCare.modules.service_order.service.DepartmentQueueAllocator;
import com.PrimeCare.PrimeCare.modules.service_result.repository.ServiceResultRepository;
import com.PrimeCare.PrimeCare.shared.enums.PaymentIntentProvider;
import com.PrimeCare.PrimeCare.shared.enums.PaymentIntentStatus;
import com.PrimeCare.PrimeCare.shared.enums.PaymentMethod;
import com.PrimeCare.PrimeCare.shared.enums.PaymentStatus;
import com.PrimeCare.PrimeCare.shared.enums.PrescriptionStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingServiceChangePaymentMethodTest {

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
    void cashUnpaidInvoiceCanChangeToBankTransferAndCreatesPendingIntent() {
        Invoice invoice = invoice(PaymentMethod.CASH, PaymentStatus.UNPAID);
        stubMutableInvoice(invoice);
        allowNoExistingMoney();
        when(billingQrService.buildPaymentContent(invoice)).thenReturn("HD PC123456789");

        var response = service.changeInvoicePaymentMethod(1L, 7L, request(PaymentMethod.BANK_TRANSFER));

        assertThat(response.getPaymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING_CONFIRMATION);
        assertThat(invoice.getPaymentReference()).isNotBlank();
        assertThat(invoice.getTransferContent()).isEqualTo("HD PC123456789");
        assertThat(invoice.getPaidAt()).isNull();
        assertThat(invoice.getVnpTxnRef()).isNull();
        assertThat(invoice.getVnpPaymentUrl()).isNull();

        ArgumentCaptor<PaymentIntent> intentCaptor = ArgumentCaptor.forClass(PaymentIntent.class);
        verify(paymentIntentRepository).save(intentCaptor.capture());
        PaymentIntent intent = intentCaptor.getValue();
        assertThat(intent.getStatus()).isEqualTo(PaymentIntentStatus.PENDING);
        assertThat(intent.getPaymentReference()).isEqualTo(invoice.getPaymentReference());
        assertThat(intent.getTransferContent()).isEqualTo("HD PC123456789");
        assertThat(intent.getProvider()).isEqualTo(PaymentIntentProvider.VIETQR);

        verifySuccessfulAudit(PaymentMethod.CASH, PaymentStatus.UNPAID, PaymentMethod.BANK_TRANSFER, PaymentStatus.PENDING_CONFIRMATION);
    }

    @Test
    void pendingBankTransferCanChangeToCashAndClearsBankArtifacts() {
        Invoice invoice = invoice(PaymentMethod.BANK_TRANSFER, PaymentStatus.PENDING_CONFIRMATION);
        invoice.setPaymentReference("PCOLD");
        invoice.setTransferContent("HD PCOLD");
        PaymentIntent intent = PaymentIntent.builder()
                .invoice(invoice)
                .provider(PaymentIntentProvider.VIETQR)
                .paymentReference("PCOLD")
                .transferContent("HD PCOLD")
                .expectedAmount(invoice.getTotalAmount())
                .status(PaymentIntentStatus.PENDING)
                .build();
        stubMutableInvoice(invoice);
        when(paymentIntentRepository.findWithLockByInvoice_Id(1L)).thenReturn(Optional.of(intent));

        var response = service.changeInvoicePaymentMethod(1L, 7L, request(PaymentMethod.CASH));

        assertThat(response.getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(invoice.getPaymentReference()).isNull();
        assertThat(invoice.getTransferContent()).isNull();
        assertThat(invoice.getPaymentDetectedAt()).isNull();
        assertThat(invoice.getPaymentReviewReason()).isNull();
        assertThat(intent.getStatus()).isEqualTo(PaymentIntentStatus.CANCELLED);
        verify(paymentIntentRepository).save(intent);
    }

    @Test
    void vnpayUnpaidInvoiceCanChangeToCashAndClearsVnpayArtifacts() {
        Invoice invoice = invoice(PaymentMethod.VNPAY, PaymentStatus.UNPAID);
        invoice.setVnpTxnRef("VNP-OLD");
        invoice.setVnpPaymentUrl("https://pay.test/old");
        stubMutableInvoice(invoice);
        allowNoExistingMoney();

        var response = service.changeInvoicePaymentMethod(1L, 7L, request(PaymentMethod.CASH));

        assertThat(response.getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(invoice.getVnpTxnRef()).isNull();
        assertThat(invoice.getVnpPaymentUrl()).isNull();
        assertThat(invoice.getPaidAt()).isNull();
    }

    @Test
    void cashUnpaidInvoiceCanChangeToVnpayAndInitializesPaymentUrl() {
        Invoice invoice = invoice(PaymentMethod.CASH, PaymentStatus.UNPAID);
        stubMutableInvoice(invoice);
        allowNoExistingMoney();
        ChangeInvoicePaymentMethodRequest request = request(PaymentMethod.VNPAY);
        request.setReturnUrl("http://localhost:8081/app/cashier/invoices");
        when(vnpayPaymentService.init("INV-1", 100L, request.getReturnUrl()))
                .thenReturn(new VnpayPaymentInit("VNP-NEW", "https://pay.test/new", "https://backend.test/return"));

        var response = service.changeInvoicePaymentMethod(1L, 7L, request);

        assertThat(response.getPaymentMethod()).isEqualTo(PaymentMethod.VNPAY);
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(invoice.getVnpTxnRef()).isEqualTo("VNP-NEW");
        assertThat(invoice.getVnpPaymentUrl()).isEqualTo("https://pay.test/new");
        assertThat(invoice.getPaymentReference()).isNull();
        assertThat(invoice.getTransferContent()).isNull();
        assertThat(invoice.getPaidAt()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"PAID", "PARTIALLY_REFUNDED", "REFUNDED", "PAYMENT_REVIEW", "VOID"})
    void finalizedOrReviewInvoicesCannotChangePaymentMethod(PaymentStatus status) {
        Invoice invoice = invoice(PaymentMethod.CASH, status);
        when(invoiceRepository.findWithLockById(1L)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> service.changeInvoicePaymentMethod(1L, 7L, request(PaymentMethod.BANK_TRANSFER)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVOICE_INVALID_STATUS);
                    assertThat(ex.getMessage()).isEqualTo(
                            "Cannot change payment method after payment has been confirmed or is under review."
                    );
                });

        verify(invoiceRepository, never()).save(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any(), any());
    }

    @Test
    void samePaymentMethodRequestIsRejected() {
        Invoice invoice = invoice(PaymentMethod.CASH, PaymentStatus.UNPAID);
        when(invoiceRepository.findWithLockById(1L)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> service.changeInvoicePaymentMethod(1L, 7L, request(PaymentMethod.CASH)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("Payment method is already selected.");
                });

        verify(invoiceRepository, never()).save(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any(), any());
    }

    private void stubMutableInvoice(Invoice invoice) {
        when(invoiceRepository.findWithLockById(1L)).thenReturn(Optional.of(invoice));
        when(userRepository.findById(7L)).thenReturn(Optional.of(cashier()));
        when(invoiceRepository.save(invoice)).thenReturn(invoice);
    }

    private void allowNoExistingMoney() {
        when(paymentIntentRepository.findWithLockByInvoice_Id(1L)).thenReturn(Optional.empty());
    }

    private ChangeInvoicePaymentMethodRequest request(PaymentMethod paymentMethod) {
        ChangeInvoicePaymentMethodRequest request = new ChangeInvoicePaymentMethodRequest();
        request.setPaymentMethod(paymentMethod);
        return request;
    }

    private User cashier() {
        return User.builder().id(7L).email("cashier@example.test").build();
    }

    private Invoice invoice(PaymentMethod method, PaymentStatus status) {
        Prescription prescription = Prescription.builder()
                .id(11L)
                .code("RX-1")
                .status(status == PaymentStatus.PAID ? PrescriptionStatus.PAID : PrescriptionStatus.ISSUED)
                .build();
        Invoice invoice = Invoice.builder()
                .id(1L)
                .code("INV-1")
                .prescription(prescription)
                .paymentMethod(method)
                .paymentStatus(status)
                .subtotalAmount(100L)
                .discountAmount(0L)
                .taxAmount(0L)
                .totalAmount(100L)
                .items(new ArrayList<>())
                .build();
        if (status == PaymentStatus.PAID) {
            invoice.setPaidAt(LocalDateTime.now());
        }
        if (status == PaymentStatus.PAYMENT_REVIEW) {
            invoice.setPaymentReviewReason("Needs review");
        }
        return invoice;
    }

    @SuppressWarnings("unchecked")
    private void verifySuccessfulAudit(
            PaymentMethod oldMethod,
            PaymentStatus oldStatus,
            PaymentMethod newMethod,
            PaymentStatus newStatus
    ) {
        ArgumentCaptor<Map<String, Object>> beforeCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, Object>> afterCaptor = ArgumentCaptor.forClass(Map.class);

        verify(auditLogService).log(
                any(User.class),
                eq("CHANGE_INVOICE_PAYMENT_METHOD"),
                eq("INVOICE"),
                eq(1L),
                beforeCaptor.capture(),
                afterCaptor.capture()
        );

        assertThat(beforeCaptor.getValue())
                .containsEntry("invoiceId", 1L)
                .containsEntry("invoiceCode", "INV-1")
                .containsEntry("oldPaymentMethod", oldMethod.name())
                .containsEntry("oldPaymentStatus", oldStatus.name());
        assertThat(afterCaptor.getValue())
                .containsEntry("invoiceId", 1L)
                .containsEntry("invoiceCode", "INV-1")
                .containsEntry("newPaymentMethod", newMethod.name())
                .containsEntry("newPaymentStatus", newStatus.name());
    }
}
