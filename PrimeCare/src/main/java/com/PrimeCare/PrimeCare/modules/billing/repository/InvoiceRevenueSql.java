package com.PrimeCare.PrimeCare.modules.billing.repository;

import com.PrimeCare.PrimeCare.shared.enums.PaymentStatus;

public final class InvoiceRevenueSql {

    private InvoiceRevenueSql() {
    }

    public static final String INVOICE_ITEM_REFUNDS_JOIN = """
            left join (
                select
                    invoice_id,
                    coalesce(sum(coalesce(refunded_amount, 0)), 0) as refunded_amount
                from invoice_items
                where deleted = false
                group by invoice_id
            ) invoice_refunds on invoice_refunds.invoice_id = i.id
            """;

    public static final String GROSS_PAID_REVENUE_CASE = """
            case
                when i.payment_status in ('PAID', 'PARTIALLY_REFUNDED')
                then coalesce(i.total_amount, 0)
                else 0
            end
            """;

    public static final String REFUNDED_AMOUNT_FOR_PAID_INVOICE_CASE = """
            case
                when i.payment_status = 'REFUNDED'
                then case
                    when coalesce(invoice_refunds.refunded_amount, 0) > 0
                    then coalesce(invoice_refunds.refunded_amount, 0)
                    else coalesce(i.total_amount, 0)
                end
                when i.payment_status in ('PAID', 'PARTIALLY_REFUNDED')
                then coalesce(invoice_refunds.refunded_amount, 0)
                else 0
            end
            """;

    public static final String PARTIALLY_REFUNDED_NET_REVENUE_CASE = """
            case
                when coalesce(i.total_amount, 0) - coalesce(invoice_refunds.refunded_amount, 0) < 0
                then 0
                else coalesce(i.total_amount, 0) - coalesce(invoice_refunds.refunded_amount, 0)
            end
            """;

    public static final String NET_PAID_REVENUE_CASE = """
            case
                when i.payment_status = 'PAID'
                then coalesce(i.total_amount, 0)
                when i.payment_status = 'PARTIALLY_REFUNDED'
                then (
                    case
                        when coalesce(i.total_amount, 0) - coalesce(invoice_refunds.refunded_amount, 0) < 0
                        then 0
                        else coalesce(i.total_amount, 0) - coalesce(invoice_refunds.refunded_amount, 0)
                    end
                )
                when i.payment_status = 'REFUNDED'
                then 0
                else 0
            end
            """;

    public static final String REFUNDS_PROCESSED_IN_RANGE_SUBQUERY = """
            (
                select coalesce(sum(coalesce(rr.refund_amount, 0)), 0)
                from refund_records rr
                where rr.deleted = false
                  and (:fromTime is null or rr.refunded_at >= :fromTime)
                  and (:toTime is null or rr.refunded_at < :toTime)
            )
            """;

    public static final String CASHIER_SUMMARY_QUERY = """
            select
              coalesce(sum(case
                    when (:fromTime is null or i.created_at >= :fromTime)
                     and (:toTime is null or i.created_at < :toTime)
                    then 1 else 0
              end), 0) as invoicesCreatedInRange,
              coalesce(sum(case
                    when i.payment_status = 'UNPAID'
                     and (:fromTime is null or i.created_at >= :fromTime)
                     and (:toTime is null or i.created_at < :toTime)
                    then 1 else 0
              end), 0) as unpaidInvoiceCount,
              coalesce(sum(case
                    when i.payment_status = 'PENDING_CONFIRMATION'
                     and (:fromTime is null or i.created_at >= :fromTime)
                     and (:toTime is null or i.created_at < :toTime)
                    then 1 else 0
              end), 0) as pendingConfirmationInvoiceCount,
              coalesce(sum(case
                    when i.payment_status = 'PAYMENT_REVIEW'
                     and (:fromTime is null or i.created_at >= :fromTime)
                     and (:toTime is null or i.created_at < :toTime)
                    then 1 else 0
              end), 0) as paymentReviewInvoiceCount,
              coalesce(sum(case
                    when i.payment_status in ('PAID', 'PARTIALLY_REFUNDED')
                     and i.paid_at is not null
                     and (:fromTime is null or i.paid_at >= :fromTime)
                     and (:toTime is null or i.paid_at < :toTime)
                    then 1 else 0
              end), 0) as paidInvoicesInRange,
              coalesce(sum(case
                    when i.payment_status in ('REFUNDED', 'PARTIALLY_REFUNDED')
                     and (:fromTime is null or i.created_at >= :fromTime)
                     and (:toTime is null or i.created_at < :toTime)
                    then 1 else 0
              end), 0) as refundedInvoiceCount,
              coalesce(sum(case
                    when i.paid_at is not null
                     and (:fromTime is null or i.paid_at >= :fromTime)
                     and (:toTime is null or i.paid_at < :toTime)
                    then """ + GROSS_PAID_REVENUE_CASE + """
                    else 0
              end), 0) as grossPaidRevenueInRange,
              coalesce(sum(case
                    when i.paid_at is not null
                     and (:fromTime is null or i.paid_at >= :fromTime)
                     and (:toTime is null or i.paid_at < :toTime)
                    then """ + REFUNDED_AMOUNT_FOR_PAID_INVOICE_CASE + """
                    else 0
              end), 0) as refundedAmountForPaidInvoicesInRange,
              coalesce(sum(case
                    when i.paid_at is not null
                     and (:fromTime is null or i.paid_at >= :fromTime)
                     and (:toTime is null or i.paid_at < :toTime)
                    then """ + NET_PAID_REVENUE_CASE + """
                    else 0
              end), 0) as netPaidRevenueInRange,
              coalesce(sum(case
                    when i.paid_at is not null
                     and (:fromTime is null or i.paid_at >= :fromTime)
                     and (:toTime is null or i.paid_at < :toTime)
                    then """ + NET_PAID_REVENUE_CASE + """
                    else 0
              end), 0) as paidRevenueInRange,
              """ + REFUNDS_PROCESSED_IN_RANGE_SUBQUERY + " as refundsProcessedInRange\n" + """
            from invoices i
            """ + INVOICE_ITEM_REFUNDS_JOIN + """
            where i.deleted = false
            """;

    public static long netPaidRevenue(PaymentStatus status, Long totalAmount, Long refundedAmount) {
        long total = totalAmount != null ? totalAmount : 0L;
        long refunded = refundedAmount != null ? refundedAmount : 0L;
        if (status == PaymentStatus.PAID) {
            return total;
        }
        if (status == PaymentStatus.PARTIALLY_REFUNDED) {
            return Math.max(0L, total - refunded);
        }
        return 0L;
    }

    public static long grossPaidRevenue(PaymentStatus status, Long totalAmount) {
        return status == PaymentStatus.PAID || status == PaymentStatus.PARTIALLY_REFUNDED
                ? (totalAmount != null ? totalAmount : 0L)
                : 0L;
    }

    public static long refundedAmountForPaidInvoice(PaymentStatus status, Long totalAmount, Long refundedAmount) {
        long refunded = refundedAmount != null ? refundedAmount : 0L;
        if (status == PaymentStatus.REFUNDED) {
            return refunded > 0L ? refunded : (totalAmount != null ? totalAmount : 0L);
        }
        if (status == PaymentStatus.PAID || status == PaymentStatus.PARTIALLY_REFUNDED) {
            return refunded;
        }
        return 0L;
    }
}
