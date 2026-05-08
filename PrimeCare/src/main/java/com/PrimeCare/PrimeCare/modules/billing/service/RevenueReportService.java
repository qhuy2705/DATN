package com.PrimeCare.PrimeCare.modules.billing.service;

import com.PrimeCare.PrimeCare.modules.billing.dto.response.RevenueReportResponse;
import com.PrimeCare.PrimeCare.modules.billing.repository.InvoiceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Revenue reporting service with aggregation by period and service.
 */
@Service
@RequiredArgsConstructor
public class RevenueReportService {

    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public RevenueReportResponse getReport(LocalDate from, LocalDate to, String groupBy) {
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.atTime(LocalTime.MAX);

        // Summary
        Query summaryQuery = entityManager.createNativeQuery(
                "SELECT " +
                "  COALESCE(SUM(CASE WHEN payment_status = 'PAID' THEN total_amount ELSE 0 END), 0) as total_revenue, " +
                "  COALESCE(SUM(CASE WHEN payment_status = 'REFUNDED' THEN total_amount ELSE 0 END), 0) as total_refunded, " +
                "  COUNT(*) as total_invoices, " +
                "  SUM(CASE WHEN payment_status = 'PAID' THEN 1 ELSE 0 END) as paid_invoices, " +
                "  SUM(CASE WHEN payment_status = 'REFUNDED' THEN 1 ELSE 0 END) as refunded_invoices " +
                "FROM invoices WHERE created_at BETWEEN ?1 AND ?2 AND deleted = false");
        summaryQuery.setParameter(1, fromDt);
        summaryQuery.setParameter(2, toDt);

        Object[] summary = (Object[]) summaryQuery.getSingleResult();
        long totalRevenue = ((Number) summary[0]).longValue();
        long totalRefunded = ((Number) summary[1]).longValue();
        long totalInvoices = ((Number) summary[2]).longValue();
        long paidInvoices = ((Number) summary[3]).longValue();
        long refundedInvoices = ((Number) summary[4]).longValue();

        // By period
        String dateFormat = switch (groupBy != null ? groupBy.toUpperCase() : "DAY") {
            case "WEEK" -> "%Y-W%u";
            case "MONTH" -> "%Y-%m";
            default -> "%Y-%m-%d";
        };

        Query periodQuery = entityManager.createNativeQuery(
                "SELECT DATE_FORMAT(paid_at, '" + dateFormat + "') as period, " +
                "  COALESCE(SUM(total_amount), 0) as revenue, COUNT(*) as invoice_count " +
                "FROM invoices WHERE payment_status = 'PAID' AND paid_at BETWEEN ?1 AND ?2 AND deleted = false " +
                "GROUP BY period ORDER BY period");
        periodQuery.setParameter(1, fromDt);
        periodQuery.setParameter(2, toDt);

        @SuppressWarnings("unchecked")
        List<Object[]> periodRows = periodQuery.getResultList();
        List<RevenueReportResponse.RevenueByPeriod> byPeriod = new ArrayList<>();
        for (Object[] row : periodRows) {
            byPeriod.add(RevenueReportResponse.RevenueByPeriod.builder()
                    .period(row[0] != null ? row[0].toString() : "N/A")
                    .revenue(((Number) row[1]).longValue())
                    .invoiceCount(((Number) row[2]).longValue())
                    .build());
        }

        // By service
        Query serviceQuery = entityManager.createNativeQuery(
                "SELECT ii.name_snapshot, COALESCE(SUM(ii.total_amount), 0) as revenue, COALESCE(SUM(ii.quantity), 0) as qty " +
                "FROM invoice_items ii JOIN invoices i ON ii.invoice_id = i.id " +
                "WHERE i.payment_status = 'PAID' AND i.paid_at BETWEEN ?1 AND ?2 AND i.deleted = false AND ii.deleted = false " +
                "GROUP BY ii.name_snapshot ORDER BY revenue DESC LIMIT 20");
        serviceQuery.setParameter(1, fromDt);
        serviceQuery.setParameter(2, toDt);

        @SuppressWarnings("unchecked")
        List<Object[]> serviceRows = serviceQuery.getResultList();
        List<RevenueReportResponse.RevenueByService> byService = new ArrayList<>();
        for (Object[] row : serviceRows) {
            byService.add(RevenueReportResponse.RevenueByService.builder()
                    .serviceName(row[0] != null ? row[0].toString() : "N/A")
                    .revenue(((Number) row[1]).longValue())
                    .quantity(((Number) row[2]).longValue())
                    .build());
        }

        return RevenueReportResponse.builder()
                .totalRevenue(totalRevenue)
                .totalRefunded(totalRefunded)
                .netRevenue(totalRevenue - totalRefunded)
                .totalInvoices(totalInvoices)
                .paidInvoices(paidInvoices)
                .refundedInvoices(refundedInvoices)
                .byPeriod(byPeriod)
                .byService(byService)
                .build();
    }
}
