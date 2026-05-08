package com.PrimeCare.PrimeCare.modules.billing.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CashierSummaryResponse {
    private long invoiceCount;
    private long unpaidInvoiceCount;
    private long pendingConfirmationInvoiceCount;
    private long paymentReviewInvoiceCount;
    private long paidInvoiceCount;
    private long refundedInvoiceCount;
    private long paidRevenue;
    private long serviceOrderCount;
    private long uninvoicedServiceOrderCount;
    private long unpaidServiceOrderCount;
}
