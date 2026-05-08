package com.PrimeCare.PrimeCare.modules.service_result.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class ServiceDeskMetricsRepository {

    private final EntityManager em;

    public MetricsSummary summarize(String departmentCode, LocalDateTime startOfDay, LocalDateTime endOfDay, LocalDateTime now, LocalDateTime sevenDaysAgo) {
        String sql = """
                select
                  coalesce(sum(case when i.status = 'WAITING_EXECUTION' then 1 else 0 end), 0) as waiting_count,
                  coalesce(sum(case when i.status = 'IN_PROGRESS' then 1 else 0 end), 0) as in_progress_count,
                  coalesce(sum(case when i.status = 'DONE' and i.completed_at between :startOfDay and :endOfDay then 1 else 0 end), 0) as completed_today_count,
                  coalesce(sum(case
                    when i.status in ('WAITING_EXECUTION', 'IN_PROGRESS')
                     and i.queued_at is not null
                     and coalesce(ms.default_turnaround_minutes, 0) > 0
                     and timestampadd(MINUTE, coalesce(ms.default_turnaround_minutes, 0), i.queued_at) < :nowTime
                    then 1 else 0 end), 0) as overdue_count,
                  count(distinct case when e.status = 'READY_FOR_CONCLUSION' and i.status = 'DONE' then e.id else null end) as ready_for_doctor_count,
                  count(distinct case when sr.report_pdf_status in ('PENDING', 'PROCESSING') then sr.id else null end) as pdf_pending_count,
                  coalesce(avg(case
                    when i.status = 'DONE'
                     and i.completed_at between :sevenDaysAgo and :endOfDay
                     and i.completed_at is not null
                     and coalesce(i.started_at, i.queued_at) is not null
                    then timestampdiff(MINUTE, coalesce(i.started_at, i.queued_at), i.completed_at)
                    else null end), 0) as average_turnaround_minutes,
                  coalesce(avg(case
                    when i.status = 'DONE'
                     and i.completed_at between :sevenDaysAgo and :endOfDay
                    then case
                      when coalesce(ms.default_turnaround_minutes, 0) > 0
                       and i.queued_at is not null
                       and i.completed_at is not null
                       and i.completed_at > timestampadd(MINUTE, coalesce(ms.default_turnaround_minutes, 0), i.queued_at)
                      then 1 else 0 end
                    else null end), 0) as turnaround_breach_rate
                from service_order_items i
                left join medical_services ms on ms.id = i.medical_service_id
                left join service_orders so on so.id = i.service_order_id
                left join encounters e on e.id = so.encounter_id
                left join service_results sr on sr.service_order_item_id = i.id
                where (:departmentCode is null or i.assigned_department_code = :departmentCode)
                """;
        Object[] row = (Object[]) em.createNativeQuery(sql)
                .setParameter("departmentCode", departmentCode)
                .setParameter("startOfDay", Timestamp.valueOf(startOfDay))
                .setParameter("endOfDay", Timestamp.valueOf(endOfDay))
                .setParameter("nowTime", Timestamp.valueOf(now))
                .setParameter("sevenDaysAgo", Timestamp.valueOf(sevenDaysAgo))
                .getSingleResult();

        return new MetricsSummary(
                toLong(row[0]),
                toLong(row[1]),
                toLong(row[2]),
                toLong(row[3]),
                toLong(row[4]),
                toLong(row[5]),
                toDouble(row[6]),
                toDouble(row[7]) * 100.0d
        );
    }

    public long countByItemStatus(String departmentCode, String itemStatus) {
        String sql = """
                select count(i.id)
                from service_order_items i
                where i.status = :itemStatus
                  and (:departmentCode is null or lower(i.assigned_department_code) = lower(:departmentCode))
                """;
        Object result = em.createNativeQuery(sql)
                .setParameter("itemStatus", itemStatus)
                .setParameter("departmentCode", departmentCode)
                .getSingleResult();
        return toLong(result);
    }

    public long countCompletedToday(String departmentCode, LocalDateTime start, LocalDateTime end) {
        String sql = """
                select count(i.id)
                from service_order_items i
                where i.status = 'DONE'
                  and i.completed_at between :startTime and :endTime
                  and (:departmentCode is null or lower(i.assigned_department_code) = lower(:departmentCode))
                """;
        Object result = em.createNativeQuery(sql)
                .setParameter("departmentCode", departmentCode)
                .setParameter("startTime", Timestamp.valueOf(start))
                .setParameter("endTime", Timestamp.valueOf(end))
                .getSingleResult();
        return toLong(result);
    }

    public long countOverdue(String departmentCode, LocalDateTime now) {
        String sql = """
                select count(i.id)
                from service_order_items i
                join medical_services ms on ms.id = i.medical_service_id
                where i.status in ('WAITING_EXECUTION', 'IN_PROGRESS')
                  and i.queued_at is not null
                  and coalesce(ms.default_turnaround_minutes, 0) > 0
                  and timestampadd(MINUTE, coalesce(ms.default_turnaround_minutes, 0), i.queued_at) < :nowTime
                  and (:departmentCode is null or lower(i.assigned_department_code) = lower(:departmentCode))
                """;
        Object result = em.createNativeQuery(sql)
                .setParameter("departmentCode", departmentCode)
                .setParameter("nowTime", Timestamp.valueOf(now))
                .getSingleResult();
        return toLong(result);
    }

    public long countReadyForDoctor(String departmentCode) {
        String sql = """
                select count(distinct e.id)
                from service_order_items i
                join service_orders so on so.id = i.service_order_id
                join encounters e on e.id = so.encounter_id
                where e.status = 'READY_FOR_CONCLUSION'
                  and i.status = 'DONE'
                  and (:departmentCode is null or lower(i.assigned_department_code) = lower(:departmentCode))
                """;
        Object result = em.createNativeQuery(sql)
                .setParameter("departmentCode", departmentCode)
                .getSingleResult();
        return toLong(result);
    }

    public long countPdfPending(String departmentCode) {
        String sql = """
                select count(sr.id)
                from service_results sr
                join service_order_items i on i.id = sr.service_order_item_id
                where sr.report_pdf_status in ('PENDING', 'PROCESSING')
                  and (:departmentCode is null or lower(i.assigned_department_code) = lower(:departmentCode))
                """;
        Object result = em.createNativeQuery(sql)
                .setParameter("departmentCode", departmentCode)
                .getSingleResult();
        return toLong(result);
    }

    public double averageTurnaroundMinutes(String departmentCode, LocalDateTime from, LocalDateTime to) {
        String sql = """
                select coalesce(avg(timestampdiff(MINUTE, coalesce(i.started_at, i.queued_at), i.completed_at)), 0)
                from service_order_items i
                where i.status = 'DONE'
                  and i.completed_at between :fromTime and :toTime
                  and i.completed_at is not null
                  and coalesce(i.started_at, i.queued_at) is not null
                  and (:departmentCode is null or lower(i.assigned_department_code) = lower(:departmentCode))
                """;
        Object result = em.createNativeQuery(sql)
                .setParameter("departmentCode", departmentCode)
                .setParameter("fromTime", Timestamp.valueOf(from))
                .setParameter("toTime", Timestamp.valueOf(to))
                .getSingleResult();
        return toDouble(result);
    }

    public double turnaroundBreachRate(String departmentCode, LocalDateTime from, LocalDateTime to) {
        String sql = """
                select coalesce(
                    avg(
                        case
                            when coalesce(ms.default_turnaround_minutes, 0) > 0
                              and i.queued_at is not null
                              and i.completed_at is not null
                              and i.completed_at > timestampadd(MINUTE, coalesce(ms.default_turnaround_minutes, 0), i.queued_at)
                            then 1
                            else 0
                        end
                    ),
                    0
                )
                from service_order_items i
                join medical_services ms on ms.id = i.medical_service_id
                where i.status = 'DONE'
                  and i.completed_at between :fromTime and :toTime
                  and (:departmentCode is null or lower(i.assigned_department_code) = lower(:departmentCode))
                """;
        Object result = em.createNativeQuery(sql)
                .setParameter("departmentCode", departmentCode)
                .setParameter("fromTime", Timestamp.valueOf(from))
                .setParameter("toTime", Timestamp.valueOf(to))
                .getSingleResult();
        return toDouble(result) * 100.0d;
    }

    private long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(String.valueOf(value));
    }

    private double toDouble(Object value) {
        if (value == null) return 0.0d;
        if (value instanceof Number number) return number.doubleValue();
        return Double.parseDouble(String.valueOf(value));
    }

    public record MetricsSummary(
            long waitingCount,
            long inProgressCount,
            long completedTodayCount,
            long overdueCount,
            long readyForDoctorCount,
            long pdfPendingCount,
            double averageTurnaroundMinutes,
            double turnaroundBreachRate
    ) {
    }
}
