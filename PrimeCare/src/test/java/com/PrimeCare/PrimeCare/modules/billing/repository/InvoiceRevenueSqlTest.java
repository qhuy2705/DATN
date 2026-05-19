package com.PrimeCare.PrimeCare.modules.billing.repository;

import com.PrimeCare.PrimeCare.shared.enums.PaymentStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceRevenueSqlTest {

    @Test
    void netRevenueUsesPaidPartialRefundedAndRefundedDefinitions() {
        assertThat(InvoiceRevenueSql.netPaidRevenue(PaymentStatus.PAID, 1_000_000L, 300_000L))
                .isEqualTo(1_000_000L);
        assertThat(InvoiceRevenueSql.netPaidRevenue(PaymentStatus.PARTIALLY_REFUNDED, 1_000_000L, 300_000L))
                .isEqualTo(700_000L);
        assertThat(InvoiceRevenueSql.netPaidRevenue(PaymentStatus.REFUNDED, 1_000_000L, 1_000_000L))
                .isZero();
    }

    @Test
    void unpaidPendingReviewAndVoidDoNotContributeRevenue() {
        assertThat(InvoiceRevenueSql.netPaidRevenue(PaymentStatus.UNPAID, 1_000_000L, 0L)).isZero();
        assertThat(InvoiceRevenueSql.netPaidRevenue(PaymentStatus.PENDING_CONFIRMATION, 1_000_000L, 0L)).isZero();
        assertThat(InvoiceRevenueSql.netPaidRevenue(PaymentStatus.PAYMENT_REVIEW, 1_000_000L, 0L)).isZero();
        assertThat(InvoiceRevenueSql.netPaidRevenue(PaymentStatus.VOID, 1_000_000L, 0L)).isZero();
    }

    @Test
    void refundedAmountForFullyRefundedInvoiceFallsBackToTotalWhenItemRefundsAreMissing() {
        assertThat(InvoiceRevenueSql.refundedAmountForPaidInvoice(PaymentStatus.REFUNDED, 1_000_000L, 0L))
                .isEqualTo(1_000_000L);
        assertThat(InvoiceRevenueSql.refundedAmountForPaidInvoice(PaymentStatus.REFUNDED, 1_000_000L, 1_000_000L))
                .isEqualTo(1_000_000L);
        assertThat(InvoiceRevenueSql.refundedAmountForPaidInvoice(PaymentStatus.PARTIALLY_REFUNDED, 1_000_000L, 300_000L))
                .isEqualTo(300_000L);
    }

    @Test
    void netPaidRevenueSqlDoesNotConcatenateThenAndNestedCase() {
        assertThat(compactSql(InvoiceRevenueSql.NET_PAID_REVENUE_CASE)).doesNotContain("thencase");
        assertThat(compactSql(InvoiceRevenueSql.NET_PAID_REVENUE_CASE)).contains("then(case");
    }

    private String compactSql(String sql) {
        return sql.replaceAll("\\s+", "").toLowerCase();
    }
}
