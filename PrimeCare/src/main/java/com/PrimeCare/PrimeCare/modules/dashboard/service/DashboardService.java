package com.PrimeCare.PrimeCare.modules.dashboard.service;

import com.PrimeCare.PrimeCare.modules.dashboard.dto.query.DashboardAggregateRow;
import com.PrimeCare.PrimeCare.modules.dashboard.dto.query.DashboardBranchRevenueRow;
import com.PrimeCare.PrimeCare.modules.dashboard.dto.query.DashboardDoctorKpiRow;
import com.PrimeCare.PrimeCare.modules.dashboard.dto.query.DashboardSpecialtyKpiRow;
import com.PrimeCare.PrimeCare.modules.dashboard.dto.response.*;
import com.PrimeCare.PrimeCare.modules.dashboard.repository.DashboardQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DashboardQueryRepository dashboardQueryRepository;

    @Transactional(readOnly = true)
    public DashboardOverviewResponse overview(String period, LocalDate fromDate, LocalDate toDate) {
        DateRange range = resolveDateRange(period, fromDate, toDate);
        LocalDateTime from = range.from().atStartOfDay();
        LocalDateTime to = range.toDate().plusDays(1).atStartOfDay().minusNanos(1);

        long totalAppointments = dashboardQueryRepository.countAppointmentsBetween(range.from(), range.toDate());
        long arrivedAppointments = dashboardQueryRepository.countArrivedAppointmentsBetween(range.from(), range.toDate());
        long checkedInAppointments = dashboardQueryRepository.countAppointmentsByStatusBetween(range.from(), range.toDate(), "CHECKED_IN");
        long noShowAppointments = dashboardQueryRepository.countAppointmentsByStatusBetween(range.from(), range.toDate(), "NO_SHOW");

        long totalEncounters = dashboardQueryRepository.countEncountersStartedBetween(from, to);
        long inProgressEncounters = dashboardQueryRepository.countEncountersByStatusBetween("IN_PROGRESS", from, to);

        long waitingServiceItems = dashboardQueryRepository.countServiceOrderItemsByStatusBetween("WAITING_EXECUTION", from, to);
        long paidRevenue = dashboardQueryRepository.sumPaidRevenueBetween(from, to);

        return DashboardOverviewResponse.builder()
                                        .today(DashboardTodaySummaryResponse.builder()
                                                                            .totalAppointments(totalAppointments)
                                                                            .arrivedAppointments(arrivedAppointments)
                                                                            .checkedInAppointments(checkedInAppointments)
                                                                            .noShowAppointments(noShowAppointments)
                                                                            .totalEncounters(totalEncounters)
                                                                            .inProgressEncounters(inProgressEncounters)
                                                                            .waitingServiceItems(waitingServiceItems)
                                                                            .paidRevenue(paidRevenue)
                                                                            .build())
                                        .appointmentSeries(buildAppointmentSeries(range.toDate(), 7))
                                        .revenueSeries(buildRevenueSeries(range.toDate(), 7))
                                        .noShowSeries(buildNoShowSeries(range.toDate(), 7))
                                        .build();
    }

    private DateRange resolveDateRange(String period, LocalDate fromDate, LocalDate toDate) {
        LocalDate today = LocalDate.now();
        String normalizedPeriod = period == null ? "today" : period.trim().toLowerCase();

        return switch (normalizedPeriod) {
            case "month" -> new DateRange(today.withDayOfMonth(1), today);
            case "year" -> new DateRange(today.withDayOfYear(1), today);
            case "custom" -> {
                LocalDate safeFrom = fromDate != null ? fromDate : today;
                LocalDate safeTo = toDate != null ? toDate : safeFrom;
                if (safeTo.isBefore(safeFrom)) {
                    safeTo = safeFrom;
                }
                yield new DateRange(safeFrom, safeTo);
            }
            default -> new DateRange(today, today);
        };
    }

    @Transactional(readOnly = true)
    public DashboardBreakdownResponse breakdown(int days, int topN) {
        int safeDays = Math.max(days, 1);
        int safeTopN = Math.max(topN, 1);

        LocalDate toDate = LocalDate.now();
        LocalDate fromDate = toDate.minusDays(safeDays - 1L);

        LocalDateTime fromDateTime = fromDate.atStartOfDay();
        LocalDateTime toDateTime = toDate.plusDays(1).atStartOfDay().minusNanos(1);

        var branches = dashboardQueryRepository.appointmentByBranch(fromDate, toDate).stream()
                                               .limit(safeTopN)
                                               .map(this::toBreakdownItem)
                                               .toList();

        var specialties = dashboardQueryRepository.appointmentBySpecialty(fromDate, toDate).stream()
                                                  .limit(safeTopN)
                                                  .map(this::toBreakdownItem)
                                                  .toList();

        var topDoctors = dashboardQueryRepository.topDoctors(fromDateTime, toDateTime).stream()
                                                 .limit(safeTopN)
                                                 .map(this::toBreakdownItem)
                                                 .toList();

        var topServices = dashboardQueryRepository.topServices(fromDateTime, toDateTime).stream()
                                                  .limit(safeTopN)
                                                  .map(this::toBreakdownItem)
                                                  .toList();

        return DashboardBreakdownResponse.builder()
                                         .branches(branches)
                                         .specialties(specialties)
                                         .topDoctors(topDoctors)
                                         .topServices(topServices)
                                         .build();
    }

    @Transactional(readOnly = true)
    public DashboardKpiResponse kpis(int days, int topN) {
        int safeDays = Math.max(days, 1);
        int safeTopN = Math.max(topN, 1);

        LocalDate toDate = LocalDate.now();
        LocalDate fromDate = toDate.minusDays(safeDays - 1L);

        LocalDateTime fromDateTime = fromDate.atStartOfDay();
        LocalDateTime toDateTime = toDate.plusDays(1).atStartOfDay().minusNanos(1);

        var doctorKpis = dashboardQueryRepository.doctorKpis(fromDate, toDate).stream()
                                                 .limit(safeTopN)
                                                 .map(this::toDoctorKpi)
                                                 .toList();

        var specialtyKpis = dashboardQueryRepository.specialtyKpis(fromDate, toDate).stream()
                                                    .limit(safeTopN)
                                                    .map(this::toSpecialtyKpi)
                                                    .toList();

        var branchRevenue = dashboardQueryRepository.revenueByBranch(fromDateTime, toDateTime).stream()
                                                    .limit(safeTopN)
                                                    .map(this::toBranchRevenue)
                                                    .toList();

        return DashboardKpiResponse.builder()
                                   .doctorKpis(doctorKpis)
                                   .specialtyKpis(specialtyKpis)
                                   .branchRevenue(branchRevenue)
                                   .build();
    }

    private List<DashboardDailyMetricPointResponse> buildAppointmentSeries(LocalDate endDate, int days) {
        List<DashboardDailyMetricPointResponse> result = new ArrayList<>();
        LocalDate start = endDate.minusDays(days - 1L);

        for (LocalDate d = start; !d.isAfter(endDate); d = d.plusDays(1)) {
            long value = dashboardQueryRepository.countAppointmentsOnDate(d);
            result.add(DashboardDailyMetricPointResponse.builder()
                                                        .date(d.toString())
                                                        .value(value)
                                                        .build());
        }

        return result;
    }

    private List<DashboardDailyMetricPointResponse> buildNoShowSeries(LocalDate endDate, int days) {
        List<DashboardDailyMetricPointResponse> result = new ArrayList<>();
        LocalDate start = endDate.minusDays(days - 1L);

        for (LocalDate d = start; !d.isAfter(endDate); d = d.plusDays(1)) {
            long value = dashboardQueryRepository.countAppointmentsByStatusOnDate(d, "NO_SHOW");
            result.add(DashboardDailyMetricPointResponse.builder()
                                                        .date(d.toString())
                                                        .value(value)
                                                        .build());
        }

        return result;
    }

    private List<DashboardDailyMetricPointResponse> buildRevenueSeries(LocalDate endDate, int days) {
        List<DashboardDailyMetricPointResponse> result = new ArrayList<>();
        LocalDate start = endDate.minusDays(days - 1L);

        for (LocalDate d = start; !d.isAfter(endDate); d = d.plusDays(1)) {
            LocalDateTime from = d.atStartOfDay();
            LocalDateTime to = d.plusDays(1).atStartOfDay().minusNanos(1);

            long value = dashboardQueryRepository.sumPaidRevenueBetween(from, to);

            result.add(DashboardDailyMetricPointResponse.builder()
                                                        .date(d.toString())
                                                        .value(value)
                                                        .build());
        }

        return result;
    }

    private DashboardBreakdownItemResponse toBreakdownItem(DashboardAggregateRow row) {
        return DashboardBreakdownItemResponse.builder()
                                             .id(row.getId())
                                             .code(row.getCode())
                                             .name(row.getName())
                                             .value(row.getValue() != null ? row.getValue() : 0L)
                                             .build();
    }

    private DashboardDoctorKpiResponse toDoctorKpi(DashboardDoctorKpiRow row) {
        long total = row.getTotalAppointments() != null ? row.getTotalAppointments() : 0L;
        long checkedIn = row.getCheckedInAppointments() != null ? row.getCheckedInAppointments() : 0L;
        long noShow = row.getNoShowAppointments() != null ? row.getNoShowAppointments() : 0L;

        double fillRate = total == 0 ? 0.0 : round2((checkedIn * 100.0) / total);
        double noShowRate = total == 0 ? 0.0 : round2((noShow * 100.0) / total);

        return DashboardDoctorKpiResponse.builder()
                                         .doctorId(row.getDoctorId())
                                         .doctorName(row.getDoctorName())
                                         .totalAppointments(total)
                                         .checkedInAppointments(checkedIn)
                                         .noShowAppointments(noShow)
                                         .fillRate(fillRate)
                                         .noShowRate(noShowRate)
                                         .build();
    }

    private DashboardSpecialtyKpiResponse toSpecialtyKpi(DashboardSpecialtyKpiRow row) {
        long total = row.getTotalAppointments() != null ? row.getTotalAppointments() : 0L;
        long noShow = row.getNoShowAppointments() != null ? row.getNoShowAppointments() : 0L;

        double noShowRate = total == 0 ? 0.0 : round2((noShow * 100.0) / total);

        return DashboardSpecialtyKpiResponse.builder()
                                            .specialtyId(row.getSpecialtyId())
                                            .specialtyName(row.getSpecialtyName())
                                            .totalAppointments(total)
                                            .noShowAppointments(noShow)
                                            .noShowRate(noShowRate)
                                            .build();
    }

    private DashboardBranchRevenueResponse toBranchRevenue(DashboardBranchRevenueRow row) {
        return DashboardBranchRevenueResponse.builder()
                                             .branchId(row.getBranchId())
                                             .branchName(row.getBranchName())
                                             .paidRevenue(row.getPaidRevenue() != null ? row.getPaidRevenue() : 0L)
                                             .build();
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }


    private record DateRange(LocalDate from, LocalDate toDate) {}
}
