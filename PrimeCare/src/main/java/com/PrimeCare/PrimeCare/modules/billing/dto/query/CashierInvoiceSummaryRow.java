package com.PrimeCare.PrimeCare.modules.billing.dto.query;

public interface CashierInvoiceSummaryRow {
    long getInvoicesCreatedInRange();

    long getUnpaidInvoiceCount();

    long getPendingConfirmationInvoiceCount();

    long getPaymentReviewInvoiceCount();

    long getPaidInvoicesInRange();

    long getRefundedInvoiceCount();

    long getGrossPaidRevenueInRange();

    long getRefundedAmountForPaidInvoicesInRange();

    long getNetPaidRevenueInRange();

    long getPaidRevenueInRange();

    long getRefundsProcessedInRange();

    default long getInvoiceCount() {
        return getInvoicesCreatedInRange();
    }

    default long getPaidInvoiceCount() {
        return getPaidInvoicesInRange();
    }

    default long getPaidRevenue() {
        return getPaidRevenueInRange();
    }
}
