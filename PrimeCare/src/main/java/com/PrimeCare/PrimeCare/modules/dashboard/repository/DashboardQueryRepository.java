package com.PrimeCare.PrimeCare.modules.dashboard.repository;

import com.PrimeCare.PrimeCare.modules.dashboard.dto.query.DashboardAggregateRow;
import com.PrimeCare.PrimeCare.modules.dashboard.dto.query.DashboardBranchRevenueRow;
import com.PrimeCare.PrimeCare.modules.dashboard.dto.query.DashboardDoctorKpiRow;
import com.PrimeCare.PrimeCare.modules.dashboard.dto.query.DashboardSpecialtyKpiRow;
import com.PrimeCare.PrimeCare.modules.billing.repository.InvoiceRevenueSql;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class DashboardQueryRepository {

    @PersistenceContext
    private EntityManager em;

    public long countAppointmentsOnDate(LocalDate date) {
        String sql = """
                select count(a.id)
                from appointments a
                where a.visit_date = ?1
                """;

        Object result = em.createNativeQuery(sql)
                          .setParameter(1, Date.valueOf(date))
                          .getSingleResult();

        return toLong(result);
    }

    public long countArrivedAppointmentsOnDate(LocalDate date) {
        String sql = """
                select count(a.id)
                from appointments a
                where a.visit_date = ?1
                  and a.arrival_status = 'ARRIVED'
                """;

        Object result = em.createNativeQuery(sql)
                          .setParameter(1, Date.valueOf(date))
                          .getSingleResult();

        return toLong(result);
    }

    public long countAppointmentsByStatusOnDate(LocalDate date, String status) {
        String sql = """
                select count(a.id)
                from appointments a
                where a.visit_date = ?1
                  and a.status = ?2
                """;

        Object result = em.createNativeQuery(sql)
                          .setParameter(1, Date.valueOf(date))
                          .setParameter(2, status)
                          .getSingleResult();

        return toLong(result);
    }


    public long countAppointmentsBetween(LocalDate fromDate, LocalDate toDate) {
        String sql = """
                select count(a.id)
                from appointments a
                where a.visit_date between ?1 and ?2
                """;

        Object result = em.createNativeQuery(sql)
                          .setParameter(1, Date.valueOf(fromDate))
                          .setParameter(2, Date.valueOf(toDate))
                          .getSingleResult();

        return toLong(result);
    }

    public long countArrivedAppointmentsBetween(LocalDate fromDate, LocalDate toDate) {
        String sql = """
                select count(a.id)
                from appointments a
                where a.visit_date between ?1 and ?2
                  and a.arrival_status = 'ARRIVED'
                """;

        Object result = em.createNativeQuery(sql)
                          .setParameter(1, Date.valueOf(fromDate))
                          .setParameter(2, Date.valueOf(toDate))
                          .getSingleResult();

        return toLong(result);
    }

    public long countAppointmentsByStatusBetween(LocalDate fromDate, LocalDate toDate, String status) {
        String sql = """
                select count(a.id)
                from appointments a
                where a.visit_date between ?1 and ?2
                  and a.status = ?3
                """;

        Object result = em.createNativeQuery(sql)
                          .setParameter(1, Date.valueOf(fromDate))
                          .setParameter(2, Date.valueOf(toDate))
                          .setParameter(3, status)
                          .getSingleResult();

        return toLong(result);
    }

    public long countEncountersStartedBetween(LocalDateTime from, LocalDateTime to) {
        String sql = """
                select count(e.id)
                from encounters e
                where e.started_at between ?1 and ?2
                """;

        Object result = em.createNativeQuery(sql)
                          .setParameter(1, Timestamp.valueOf(from))
                          .setParameter(2, Timestamp.valueOf(to))
                          .getSingleResult();

        return toLong(result);
    }

    public long countEncountersByStatusBetween(String status, LocalDateTime from, LocalDateTime to) {
        String sql = """
                select count(e.id)
                from encounters e
                where e.status = ?1
                  and e.started_at between ?2 and ?3
                """;

        Object result = em.createNativeQuery(sql)
                          .setParameter(1, status)
                          .setParameter(2, Timestamp.valueOf(from))
                          .setParameter(3, Timestamp.valueOf(to))
                          .getSingleResult();

        return toLong(result);
    }

    public long countUniquePatientsBetween(LocalDate fromDate, LocalDate toDate, LocalDateTime from, LocalDateTime to) {
        String sql = """
                select count(distinct patient_id)
                from (
                    select a.patient_id as patient_id
                    from appointments a
                    where a.patient_id is not null
                      and a.visit_date between ?1 and ?2
                    union
                    select e.patient_id as patient_id
                    from encounters e
                    where e.patient_id is not null
                      and e.started_at between ?3 and ?4
                ) dashboard_patients
                """;

        Object result = em.createNativeQuery(sql)
                          .setParameter(1, Date.valueOf(fromDate))
                          .setParameter(2, Date.valueOf(toDate))
                          .setParameter(3, Timestamp.valueOf(from))
                          .setParameter(4, Timestamp.valueOf(to))
                          .getSingleResult();

        return toLong(result);
    }

    public long countServiceOrderItemsByStatusBetween(String status, LocalDateTime from, LocalDateTime to) {
        String sql = """
                select count(i.id)
                from service_order_items i
                where i.status = ?1
                  and i.created_at between ?2 and ?3
                """;

        Object result = em.createNativeQuery(sql)
                          .setParameter(1, status)
                          .setParameter(2, Timestamp.valueOf(from))
                          .setParameter(3, Timestamp.valueOf(to))
                          .getSingleResult();

        return toLong(result);
    }

    public long sumPaidRevenueBetween(LocalDateTime from, LocalDateTime to) {
        String sql = """
                select coalesce(sum(
                """ + InvoiceRevenueSql.NET_PAID_REVENUE_CASE + """
                ), 0)
                from invoices i
                """ + InvoiceRevenueSql.INVOICE_ITEM_REFUNDS_JOIN + """
                where i.deleted = false
                  and i.paid_at between ?1 and ?2
                """;

        Object result = em.createNativeQuery(sql)
                          .setParameter(1, Timestamp.valueOf(from))
                          .setParameter(2, Timestamp.valueOf(to))
                          .getSingleResult();

        return toLong(result);
    }

    public long sumGrossPaidRevenueBetween(LocalDateTime from, LocalDateTime to) {
        String sql = """
                select coalesce(sum(
                """ + InvoiceRevenueSql.GROSS_PAID_REVENUE_CASE + """
                ), 0)
                from invoices i
                """ + InvoiceRevenueSql.INVOICE_ITEM_REFUNDS_JOIN + """
                where i.deleted = false
                  and i.paid_at between ?1 and ?2
                """;

        Object result = em.createNativeQuery(sql)
                          .setParameter(1, Timestamp.valueOf(from))
                          .setParameter(2, Timestamp.valueOf(to))
                          .getSingleResult();

        return toLong(result);
    }

    public long sumRefundedAmountForPaidInvoicesBetween(LocalDateTime from, LocalDateTime to) {
        String sql = """
                select coalesce(sum(
                """ + InvoiceRevenueSql.REFUNDED_AMOUNT_FOR_PAID_INVOICE_CASE + """
                ), 0)
                from invoices i
                """ + InvoiceRevenueSql.INVOICE_ITEM_REFUNDS_JOIN + """
                where i.deleted = false
                  and i.paid_at between ?1 and ?2
                """;

        Object result = em.createNativeQuery(sql)
                          .setParameter(1, Timestamp.valueOf(from))
                          .setParameter(2, Timestamp.valueOf(to))
                          .getSingleResult();

        return toLong(result);
    }

    public long sumRefundsProcessedBetween(LocalDateTime from, LocalDateTime to) {
        String sql = """
                select coalesce(sum(coalesce(rr.refund_amount, 0)), 0)
                from refund_records rr
                where rr.deleted = false
                  and rr.refunded_at between ?1 and ?2
                """;

        Object result = em.createNativeQuery(sql)
                          .setParameter(1, Timestamp.valueOf(from))
                          .setParameter(2, Timestamp.valueOf(to))
                          .getSingleResult();

        return toLong(result);
    }

    public List<DashboardAggregateRow> appointmentByBranch(LocalDate fromDate, LocalDate toDate) {
        String sql = """
                select
                    a.branch_id as id,
                    cast(a.branch_id as char) as code,
                    b.name_vn as name,
                    count(a.id) as value
                from appointments a
                join branches b on b.id = a.branch_id
                where a.visit_date between ?1 and ?2
                group by a.branch_id, b.name_vn
                order by count(a.id) desc
                """;

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql)
                                .setParameter(1, Date.valueOf(fromDate))
                                .setParameter(2, Date.valueOf(toDate))
                                .getResultList();

        return rows.stream().map(this::toAggregateRow).toList();
    }

    public List<DashboardAggregateRow> appointmentBySpecialty(LocalDate fromDate, LocalDate toDate) {
        String sql = """
                select
                    a.specialty_id as id,
                    cast(a.specialty_id as char) as code,
                    s.name_vn as name,
                    count(a.id) as value
                from appointments a
                join specialties s on s.id = a.specialty_id
                where a.visit_date between ?1 and ?2
                group by a.specialty_id, s.name_vn
                order by count(a.id) desc
                """;

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql)
                                .setParameter(1, Date.valueOf(fromDate))
                                .setParameter(2, Date.valueOf(toDate))
                                .getResultList();

        return rows.stream().map(this::toAggregateRow).toList();
    }

    public List<DashboardAggregateRow> topDoctors(LocalDateTime from, LocalDateTime to) {
        String sql = """
                select
                    e.doctor_id as id,
                    cast(e.doctor_id as char) as code,
                    d.full_name as name,
                    count(e.id) as value
                from encounters e
                join doctor_profiles d on d.id = e.doctor_id
                where e.started_at between ?1 and ?2
                group by e.doctor_id, d.full_name
                order by count(e.id) desc
                """;

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql)
                                .setParameter(1, Timestamp.valueOf(from))
                                .setParameter(2, Timestamp.valueOf(to))
                                .getResultList();

        return rows.stream().map(this::toAggregateRow).toList();
    }

    public List<DashboardAggregateRow> topServices(LocalDateTime from, LocalDateTime to) {
        String sql = """
                select
                    soi.medical_service_id as id,
                    soi.service_code_snapshot as code,
                    soi.service_name_vn_snapshot as name,
                    count(soi.id) as value
                from service_order_items soi
                where soi.created_at between ?1 and ?2
                group by soi.medical_service_id, soi.service_code_snapshot, soi.service_name_vn_snapshot
                order by count(soi.id) desc
                """;

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql)
                                .setParameter(1, Timestamp.valueOf(from))
                                .setParameter(2, Timestamp.valueOf(to))
                                .getResultList();

        return rows.stream().map(this::toAggregateRow).toList();
    }

    public List<DashboardDoctorKpiRow> doctorKpis(LocalDate fromDate, LocalDate toDate) {
        String sql = """
                select
                    a.doctor_id as doctor_id,
                    d.full_name as doctor_name,
                    count(a.id) as total_appointments,
                    sum(case when a.status = 'CHECKED_IN' then 1 else 0 end) as checked_in_appointments,
                    sum(case when a.status = 'NO_SHOW' then 1 else 0 end) as no_show_appointments
                from appointments a
                join doctor_profiles d on d.id = a.doctor_id
                where a.visit_date between ?1 and ?2
                group by a.doctor_id, d.full_name
                order by count(a.id) desc
                """;

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql)
                                .setParameter(1, Date.valueOf(fromDate))
                                .setParameter(2, Date.valueOf(toDate))
                                .getResultList();

        return rows.stream()
                   .map(r -> new DashboardDoctorKpiRow(
                           toLongObject(r[0]),
                           toStringSafe(r[1]),
                           toNumber(r[2]),
                           toNumber(r[3]),
                           toNumber(r[4])
                   ))
                   .toList();
    }

    public List<DashboardSpecialtyKpiRow> specialtyKpis(LocalDate fromDate, LocalDate toDate) {
        String sql = """
                select
                    a.specialty_id as specialty_id,
                    s.name_vn as specialty_name,
                    count(a.id) as total_appointments,
                    sum(case when a.status = 'NO_SHOW' then 1 else 0 end) as no_show_appointments
                from appointments a
                join specialties s on s.id = a.specialty_id
                where a.visit_date between ?1 and ?2
                group by a.specialty_id, s.name_vn
                order by count(a.id) desc
                """;

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql)
                                .setParameter(1, Date.valueOf(fromDate))
                                .setParameter(2, Date.valueOf(toDate))
                                .getResultList();

        return rows.stream()
                   .map(r -> new DashboardSpecialtyKpiRow(
                           toLongObject(r[0]),
                           toStringSafe(r[1]),
                           toNumber(r[2]),
                           toNumber(r[3])
                   ))
                   .toList();
    }

    public List<DashboardBranchRevenueRow> revenueByBranch(LocalDateTime from, LocalDateTime to) {
        String sql = revenueByBranchSql();

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql)
                                .setParameter(1, Timestamp.valueOf(from))
                                .setParameter(2, Timestamp.valueOf(to))
                                .getResultList();

        return rows.stream()
                   .map(r -> new DashboardBranchRevenueRow(
                           toLongObject(r[0]),
                           toStringSafe(r[1]),
                           toNumber(r[2])
                   ))
                   .toList();
    }

    static String revenueByBranchSql() {
        return """
                select
                    b.id as branch_id,
                    b.name_vn as branch_name,
                    coalesce(sum(
                    """ + InvoiceRevenueSql.NET_PAID_REVENUE_CASE + """
                    ), 0) as paid_revenue
                from invoices i
                """ + InvoiceRevenueSql.INVOICE_ITEM_REFUNDS_JOIN + """
                left join service_orders so on so.id = i.service_order_id
                left join prescriptions p on p.id = i.prescription_id
                left join encounters e on e.id = p.encounter_id
                join branches b on b.id = coalesce(so.branch_id, e.branch_id)
                where i.deleted = false
                  and i.paid_at between ?1 and ?2
                group by b.id, b.name_vn
                order by paid_revenue desc
                """;
    }

    private DashboardAggregateRow toAggregateRow(Object[] r) {
        return new DashboardAggregateRow(
                toLongObject(r[0]),
                toStringSafe(r[1]),
                toStringSafe(r[2]),
                toLongObject(r[3])
        );
    }

    private long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private Long toLongObject(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private Number toNumber(Object value) {
        return value instanceof Number n ? n : 0L;
    }

    private String toStringSafe(Object value) {
        return value == null ? null : value.toString();
    }
}
