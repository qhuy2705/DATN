package com.PrimeCare.PrimeCare.modules.billing.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceRepositorySummaryQueryTest {

    @Test
    void paidRevenueAndPaidInvoiceCountUsePaidAtRange() throws Exception {
        String query = summarizeQuery();

        String paidInvoiceCountCase = aggregateCase(query, "paidInvoicesInRange");
        assertThat(paidInvoiceCountCase).contains("'PAID'");
        assertThat(paidInvoiceCountCase).contains("'PARTIALLY_REFUNDED'");
        assertThat(paidInvoiceCountCase).contains("i.paid_at is not null");
        assertThat(paidInvoiceCountCase).contains("i.paid_at >= :fromTime");
        assertThat(paidInvoiceCountCase).contains("i.paid_at < :toTime");
        assertThat(paidInvoiceCountCase).doesNotContain("i.created_at");

        String paidRevenueCase = aggregateCase(query, "paidRevenueInRange");
        assertThat(paidRevenueCase).contains("i.payment_status = 'PAID'");
        assertThat(paidRevenueCase).contains("i.payment_status = 'PARTIALLY_REFUNDED'");
        assertThat(paidRevenueCase).contains("i.payment_status = 'REFUNDED'");
        assertThat(paidRevenueCase).contains("invoice_refunds.refunded_amount");
        assertThat(paidRevenueCase).contains("i.paid_at is not null");
        assertThat(paidRevenueCase).contains("i.paid_at >= :fromTime");
        assertThat(paidRevenueCase).contains("i.paid_at < :toTime");
        assertThat(paidRevenueCase).contains("i.total_amount");
        assertThat(paidRevenueCase).doesNotContain("i.created_at");
    }

    @Test
    void createdInvoiceAndPendingMetricsUseCreatedAtRange() throws Exception {
        String query = summarizeQuery();

        String createdCountCase = aggregateCase(query, "invoicesCreatedInRange");
        assertThat(createdCountCase).contains("i.created_at >= :fromTime");
        assertThat(createdCountCase).contains("i.created_at < :toTime");
        assertThat(createdCountCase).doesNotContain("i.paid_at");

        String unpaidCountCase = aggregateCase(query, "unpaidInvoiceCount");
        assertThat(unpaidCountCase).contains("i.payment_status = 'UNPAID'");
        assertThat(unpaidCountCase).contains("i.created_at >= :fromTime");
        assertThat(unpaidCountCase).contains("i.created_at < :toTime");
        assertThat(unpaidCountCase).doesNotContain("i.paid_at");
    }

    @Test
    void cashierSummaryQueryExposesGrossRefundNetAndRefundProcessedMetrics() throws Exception {
        Query annotation = summarizeQueryAnnotation();
        String query = annotation.value().replaceAll("\\s+", " ");

        assertThat(annotation.nativeQuery()).isTrue();
        assertThat(query).contains("as grossPaidRevenueInRange");
        assertThat(query).contains("as refundedAmountForPaidInvoicesInRange");
        assertThat(query).contains("as netPaidRevenueInRange");
        assertThat(query).contains("as refundsProcessedInRange");
        assertThat(query).contains("from refund_records rr");
        assertThat(query).contains("rr.refunded_at >= :fromTime");
        assertThat(query).contains("rr.refunded_at < :toTime");
        assertThat(query).contains("from invoice_items");
        assertThat(query).contains("invoice_refunds.refunded_amount");
    }

    private String summarizeQuery() throws Exception {
        return summarizeQueryAnnotation().value().replaceAll("\\s+", " ");
    }

    private Query summarizeQueryAnnotation() throws Exception {
        Method method = InvoiceRepository.class.getMethod(
                "summarizeCashierInvoices",
                LocalDateTime.class,
                LocalDateTime.class
        );

        return method.getAnnotation(Query.class);
    }

    private String aggregateCase(String query, String alias) {
        int aliasIndex = query.indexOf(" as " + alias);
        assertThat(aliasIndex).isGreaterThanOrEqualTo(0);

        int startIndex = query.lastIndexOf("coalesce(sum(case", aliasIndex);
        assertThat(startIndex).isGreaterThanOrEqualTo(0);

        return query.substring(startIndex, aliasIndex);
    }
}
