package com.PrimeCare.PrimeCare.modules.dashboard.repository;

import com.PrimeCare.PrimeCare.modules.dashboard.dto.query.DashboardAggregateRow;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardQueryRepositoryRevenueSqlTest {

    @Test
    void branchRevenueResolvesServiceAndPrescriptionInvoiceBranches() {
        String sql = normalizedRevenueByBranchSql();

        assertThat(sql).contains("left join service_orders so on so.id = i.service_order_id");
        assertThat(sql).contains("left join prescriptions p on p.id = i.prescription_id");
        assertThat(sql).contains("left join encounters e on e.id = p.encounter_id");
        assertThat(sql).contains("join branches b on b.id = coalesce(so.branch_id, e.branch_id)");
    }

    @Test
    void branchRevenueUsesNetPaidRevenueAfterRefunds() {
        String sql = normalizedRevenueByBranchSql();

        assertThat(sql).contains("i.payment_status = 'PAID'");
        assertThat(sql).contains("i.payment_status = 'PARTIALLY_REFUNDED'");
        assertThat(sql).contains("i.payment_status = 'REFUNDED'");
        assertThat(sql).contains("coalesce(i.total_amount, 0) - coalesce(invoice_refunds.refunded_amount, 0)");
        assertThat(sql).contains("from invoice_items");
        assertThat(sql).contains("invoice_refunds.refunded_amount");
        assertThat(sql).contains("i.paid_at between ?1 and ?2");
    }

    @Test
    void netPaidRevenueSqlDoesNotConcatenateThenAndNestedCase() {
        assertThat(compactSql(normalizedRevenueByBranchSql())).doesNotContain("thencase");
    }

    @Test
    void aggregateRowMapsNativeNumericCountValues() throws Exception {
        assertThat(toAggregateRow(BigInteger.valueOf(12)).getValue()).isEqualTo(12L);
        assertThat(toAggregateRow(BigDecimal.valueOf(34)).getValue()).isEqualTo(34L);
    }

    private String normalizedRevenueByBranchSql() {
        return DashboardQueryRepository.revenueByBranchSql().replaceAll("\\s+", " ");
    }

    private String compactSql(String sql) {
        return sql.replaceAll("\\s+", "").toLowerCase();
    }

    private DashboardAggregateRow toAggregateRow(Number value) throws Exception {
        Method method = DashboardQueryRepository.class.getDeclaredMethod("toAggregateRow", Object[].class);
        method.setAccessible(true);
        Object[] row = {1L, "code", "name", value};

        return (DashboardAggregateRow) method.invoke(new DashboardQueryRepository(), (Object) row);
    }
}
