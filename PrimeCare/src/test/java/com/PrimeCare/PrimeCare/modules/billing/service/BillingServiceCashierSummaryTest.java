package com.PrimeCare.PrimeCare.modules.billing.service;

import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.billing.dto.query.CashierInvoiceSummaryRow;
import com.PrimeCare.PrimeCare.modules.billing.repository.BankTransactionRepository;
import com.PrimeCare.PrimeCare.modules.billing.repository.InvoiceRepository;
import com.PrimeCare.PrimeCare.modules.billing.repository.PaymentIntentRepository;
import com.PrimeCare.PrimeCare.modules.billing.repository.RefundRecordItemRepository;
import com.PrimeCare.PrimeCare.modules.billing.repository.RefundRecordRepository;
import com.PrimeCare.PrimeCare.modules.dashboard.repository.DashboardQueryRepository;
import com.PrimeCare.PrimeCare.modules.dashboard.service.DashboardService;
import com.PrimeCare.PrimeCare.modules.encounter.service.EncounterWorkflowService;
import com.PrimeCare.PrimeCare.modules.notification.service.InternalNotificationService;
import com.PrimeCare.PrimeCare.modules.prescription.repository.PrescriptionItemRepository;
import com.PrimeCare.PrimeCare.modules.prescription.repository.PrescriptionRepository;
import com.PrimeCare.PrimeCare.modules.realtime.service.AfterCommitExecutor;
import com.PrimeCare.PrimeCare.modules.realtime.service.RealtimeEventPublisher;
import com.PrimeCare.PrimeCare.modules.service_order.repository.ServiceOrderItemRepository;
import com.PrimeCare.PrimeCare.modules.service_order.repository.ServiceOrderRepository;
import com.PrimeCare.PrimeCare.modules.service_order.service.DepartmentQueueAllocator;
import com.PrimeCare.PrimeCare.modules.service_result.repository.ServiceResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingServiceCashierSummaryTest {

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
    @Mock
    private DashboardQueryRepository dashboardQueryRepository;

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
    void summarySeparatesCreatedInvoiceMetricsFromPaidAtRevenueMetrics() {
        LocalDate date = LocalDate.of(2026, 5, 17);
        LocalDateTime fromTime = date.atStartOfDay();
        LocalDateTime toTime = date.plusDays(1).atStartOfDay();

        CashierInvoiceSummaryRow invoiceSummary = mock(CashierInvoiceSummaryRow.class);
        when(invoiceSummary.getInvoicesCreatedInRange()).thenReturn(2L);
        when(invoiceSummary.getUnpaidInvoiceCount()).thenReturn(1L);
        when(invoiceSummary.getPendingConfirmationInvoiceCount()).thenReturn(1L);
        when(invoiceSummary.getPaymentReviewInvoiceCount()).thenReturn(0L);
        when(invoiceSummary.getPaidInvoicesInRange()).thenReturn(1L);
        when(invoiceSummary.getGrossPaidRevenueInRange()).thenReturn(200_000L);
        when(invoiceSummary.getRefundedAmountForPaidInvoicesInRange()).thenReturn(50_000L);
        when(invoiceSummary.getNetPaidRevenueInRange()).thenReturn(150_000L);
        when(invoiceSummary.getRefundsProcessedInRange()).thenReturn(25_000L);
        when(invoiceSummary.getRefundedInvoiceCount()).thenReturn(0L);
        when(invoiceRepository.summarizeCashierInvoices(fromTime, toTime)).thenReturn(invoiceSummary);

        var response = service.summary(date, date);

        assertThat(response.getInvoicesCreatedInRange()).isEqualTo(2L);
        assertThat(response.getInvoiceCount()).isEqualTo(2L);
        assertThat(response.getPendingInvoices()).isEqualTo(2L);
        assertThat(response.getPaidInvoicesInRange()).isEqualTo(1L);
        assertThat(response.getPaidInvoiceCount()).isEqualTo(1L);
        assertThat(response.getGrossPaidRevenueInRange()).isEqualTo(200_000L);
        assertThat(response.getRefundedAmountForPaidInvoicesInRange()).isEqualTo(50_000L);
        assertThat(response.getNetPaidRevenueInRange()).isEqualTo(150_000L);
        assertThat(response.getRefundsProcessedInRange()).isEqualTo(25_000L);
        assertThat(response.getPaidRevenueInRange()).isEqualTo(150_000L);
        assertThat(response.getPaidRevenue()).isEqualTo(150_000L);
        verify(invoiceRepository).summarizeCashierInvoices(fromTime, toTime);
    }

    @Test
    void cashierSummaryAndDashboardUseSameNetRevenueForSamePaidAtRange() {
        LocalDate date = LocalDate.of(2026, 5, 17);
        LocalDateTime cashierFromTime = date.atStartOfDay();
        LocalDateTime cashierToTime = date.plusDays(1).atStartOfDay();
        LocalDateTime dashboardFromTime = date.atStartOfDay();
        LocalDateTime dashboardToTime = date.plusDays(1).atStartOfDay().minusNanos(1);

        CashierInvoiceSummaryRow invoiceSummary = mock(CashierInvoiceSummaryRow.class);
        when(invoiceSummary.getGrossPaidRevenueInRange()).thenReturn(1_000_000L);
        when(invoiceSummary.getRefundedAmountForPaidInvoicesInRange()).thenReturn(300_000L);
        when(invoiceSummary.getNetPaidRevenueInRange()).thenReturn(700_000L);
        when(invoiceRepository.summarizeCashierInvoices(cashierFromTime, cashierToTime)).thenReturn(invoiceSummary);
        when(dashboardQueryRepository.sumGrossPaidRevenueBetween(dashboardFromTime, dashboardToTime)).thenReturn(1_000_000L);
        when(dashboardQueryRepository.sumRefundedAmountForPaidInvoicesBetween(dashboardFromTime, dashboardToTime)).thenReturn(300_000L);
        when(dashboardQueryRepository.sumPaidRevenueBetween(dashboardFromTime, dashboardToTime)).thenReturn(700_000L);

        var cashierSummary = service.summary(date, date);
        var dashboardOverview = new DashboardService(dashboardQueryRepository).overview(null, date, date, null);

        assertThat(cashierSummary.getGrossPaidRevenueInRange())
                .isEqualTo(dashboardOverview.getToday().getGrossPaidRevenue());
        assertThat(cashierSummary.getRefundedAmountForPaidInvoicesInRange())
                .isEqualTo(dashboardOverview.getToday().getRefundedAmountForPaidInvoices());
        assertThat(cashierSummary.getNetPaidRevenueInRange())
                .isEqualTo(dashboardOverview.getToday().getNetPaidRevenue());
        assertThat(cashierSummary.getPaidRevenueInRange())
                .isEqualTo(dashboardOverview.getToday().getPaidRevenue())
                .isEqualTo(700_000L);
    }
}
