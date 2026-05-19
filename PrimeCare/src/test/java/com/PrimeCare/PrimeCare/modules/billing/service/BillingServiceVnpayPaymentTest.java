package com.PrimeCare.PrimeCare.modules.billing.service;

import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.billing.entity.Invoice;
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
import com.PrimeCare.PrimeCare.shared.enums.PaymentMethod;
import com.PrimeCare.PrimeCare.shared.enums.PaymentStatus;
import com.PrimeCare.PrimeCare.shared.enums.PrescriptionStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingServiceVnpayPaymentTest {

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
    void markPaidAllowsCashUnpaidInvoice() {
        Invoice invoice = invoice(PaymentMethod.CASH, PaymentStatus.UNPAID, 100L);
        when(invoiceRepository.findWithLockById(1L)).thenReturn(Optional.of(invoice));
        when(userRepository.findById(7L)).thenReturn(Optional.of(cashier()));
        when(invoiceRepository.save(invoice)).thenReturn(invoice);

        var response = service.markPaid(1L, 7L);

        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(invoice.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(invoice.getPaidAt()).isNotNull();
        verify(invoiceRepository).save(invoice);
    }

    @Test
    void markPaidRejectsVnpayInvoice() {
        Invoice invoice = invoice(PaymentMethod.VNPAY, PaymentStatus.UNPAID, 100L);
        when(invoiceRepository.findWithLockById(1L)).thenReturn(Optional.of(invoice));
        when(userRepository.findById(7L)).thenReturn(Optional.of(cashier()));

        assertThatThrownBy(() -> service.markPaid(1L, 7L))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVOICE_INVALID_STATUS);
                    assertThat(ex.getMessage()).isEqualTo("Hóa đơn VNPAY chỉ được xác nhận qua callback/IPN hợp lệ");
                });

        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void markPaidRejectsBankTransferInvoice() {
        Invoice invoice = invoice(PaymentMethod.BANK_TRANSFER, PaymentStatus.PENDING_CONFIRMATION, 100L);
        when(invoiceRepository.findWithLockById(1L)).thenReturn(Optional.of(invoice));
        when(userRepository.findById(7L)).thenReturn(Optional.of(cashier()));

        assertThatThrownBy(() -> service.markPaid(1L, 7L))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVOICE_INVALID_STATUS);
                    assertThat(ex.getMessage()).isEqualTo("Hóa đơn chuyển khoản cần xác nhận qua luồng đối soát");
                });

        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void confirmVnpayPaymentRejectsMissingTxnRefInvoice() {
        when(invoiceRepository.findWithLockByVnpTxnRef("TXN-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmVnpayPayment("TXN-1", 100L, "14123456", "20260102030405"))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVOICE_NOT_FOUND)
                );
    }

    @Test
    void confirmVnpayPaymentRejectsNonVnpayInvoice() {
        when(invoiceRepository.findWithLockByVnpTxnRef("TXN-1"))
                .thenReturn(Optional.of(invoice(PaymentMethod.CASH, PaymentStatus.UNPAID, 100L)));

        assertThatThrownBy(() -> service.confirmVnpayPayment("TXN-1", 100L, "14123456", "20260102030405"))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVOICE_INVALID_STATUS)
                );
    }

    @Test
    void confirmVnpayPaymentRejectsNullPaidAmount() {
        when(invoiceRepository.findWithLockByVnpTxnRef("TXN-1"))
                .thenReturn(Optional.of(invoice(PaymentMethod.VNPAY, PaymentStatus.UNPAID, 100L)));

        assertThatThrownBy(() -> service.confirmVnpayPayment("TXN-1", null, "14123456", "20260102030405"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVOICE_INVALID_STATUS);
                    assertThat(ex.getMessage()).isEqualTo("Thiếu số tiền thanh toán VNPAY");
                });
    }

    @Test
    void confirmVnpayPaymentRejectsAmountMismatch() {
        when(invoiceRepository.findWithLockByVnpTxnRef("TXN-1"))
                .thenReturn(Optional.of(invoice(PaymentMethod.VNPAY, PaymentStatus.UNPAID, 100L)));

        assertThatThrownBy(() -> service.confirmVnpayPayment("TXN-1", 99L, "14123456", "20260102030405"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVOICE_INVALID_STATUS);
                    assertThat(ex.getMessage()).isEqualTo("Số tiền thanh toán VNPAY không khớp hóa đơn");
                });
    }

    @Test
    void confirmVnpayPaymentReturnsAlreadyPaidWhenInvoiceAlreadyPaid() {
        Invoice invoice = invoice(PaymentMethod.VNPAY, PaymentStatus.PAID, 100L);
        when(invoiceRepository.findWithLockByVnpTxnRef("TXN-1")).thenReturn(Optional.of(invoice));

        var result = service.confirmVnpayPayment("TXN-1", 100L, "14123456", "20260102030405");

        assertThat(result.alreadyPaid()).isTrue();
        assertThat(result.invoice().getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void confirmVnpayPaymentAppliesPaidStateWhenAmountMatches() {
        Invoice invoice = invoice(PaymentMethod.VNPAY, PaymentStatus.UNPAID, 100L);
        when(invoiceRepository.findWithLockByVnpTxnRef("TXN-1")).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(invoice)).thenReturn(invoice);

        var result = service.confirmVnpayPayment("TXN-1", 100L, "14123456", "20260102030405");

        assertThat(result.alreadyPaid()).isFalse();
        assertThat(result.invoice().getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(invoice.getPaidAt()).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 4, 5));
        assertThat(invoice.getNote()).contains("14123456", "2026-01-02T03:04:05");
        verify(invoiceRepository).save(invoice);
        verify(invoiceStatusHistoryService).record(any(), any(), any(), any(), any());
        verify(auditLogService).log(any(), any(), any(), any(), any(), any());
    }

    private User cashier() {
        return User.builder().id(7L).email("cashier@example.test").build();
    }

    private Invoice invoice(PaymentMethod method, PaymentStatus status, Long totalAmount) {
        Prescription prescription = Prescription.builder()
                .id(11L)
                .code("RX-1")
                .status(status == PaymentStatus.PAID ? PrescriptionStatus.PAID : PrescriptionStatus.ISSUED)
                .build();
        return Invoice.builder()
                .id(1L)
                .code("INV-1")
                .prescription(prescription)
                .paymentMethod(method)
                .paymentStatus(status)
                .subtotalAmount(totalAmount)
                .discountAmount(0L)
                .taxAmount(0L)
                .totalAmount(totalAmount)
                .items(new ArrayList<>())
                .vnpTxnRef("TXN-1")
                .build();
    }
}
