package com.PrimeCare.PrimeCare.modules.billing.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CashierSummaryResponse {
    private long invoicesCreatedInRange;
    private long invoiceCount;
    private long pendingInvoices;
    private long unpaidInvoiceCount;
    private long pendingConfirmationInvoiceCount;
    private long paymentReviewInvoiceCount;
    private long paidInvoicesInRange;
    private long paidInvoiceCount;
    private long refundedInvoiceCount;
    private long grossPaidRevenueInRange;
    private long refundedAmountForPaidInvoicesInRange;
    private long netPaidRevenueInRange;
    private long refundsProcessedInRange;
    private long paidRevenueInRange;
    private long paidRevenue;
    private long serviceOrderCount;
    private long uninvoicedServiceOrderCount;
    private long unpaidServiceOrderCount;
}
