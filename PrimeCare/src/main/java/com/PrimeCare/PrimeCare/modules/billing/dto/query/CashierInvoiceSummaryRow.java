package com.PrimeCare.PrimeCare.modules.billing.dto.query;

public interface CashierInvoiceSummaryRow {
    long getInvoiceCount();

    long getUnpaidInvoiceCount();

    long getPendingConfirmationInvoiceCount();

    long getPaymentReviewInvoiceCount();

    long getPaidInvoiceCount();

    long getRefundedInvoiceCount();

    long getPaidRevenue();
}
