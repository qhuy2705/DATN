package com.PrimeCare.PrimeCare.modules.dashboard.service;

import com.PrimeCare.PrimeCare.modules.dashboard.dto.query.DashboardAggregateRow;
import com.PrimeCare.PrimeCare.modules.dashboard.dto.query.DashboardBranchRevenueRow;
import com.PrimeCare.PrimeCare.modules.dashboard.dto.query.DashboardDateRange;
import com.PrimeCare.PrimeCare.modules.dashboard.dto.query.DashboardDoctorKpiRow;
import com.PrimeCare.PrimeCare.modules.dashboard.dto.query.DashboardSpecialtyKpiRow;
import com.PrimeCare.PrimeCare.modules.dashboard.dto.response.*;
import com.PrimeCare.PrimeCare.modules.dashboard.repository.DashboardQueryRepository;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import com.PrimeCare.PrimeCare.shared.utils.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final DefaultDateRange DEFAULT_DATE_RANGE = DefaultDateRange.LAST_7_DAYS;

    private final DashboardQueryRepository dashboardQueryRepository;

    @Transactional(readOnly = true)
    public DashboardOverviewResponse overview(String period, LocalDate fromDate, LocalDate toDate, Integer days) {
        DashboardDateRange range = resolveDateRange(period, fromDate, toDate, days);

        long totalAppointments = dashboardQueryRepository.countAppointmentsBetween(range.getFromDate(), range.getToDate());
        long arrivedAppointments = dashboardQueryRepository.countArrivedAppointmentsBetween(range.getFromDate(), range.getToDate());
        long checkedInAppointments = dashboardQueryRepository.countAppointmentsByStatusBetween(range.getFromDate(), range.getToDate(), "CHECKED_IN");
        long noShowAppointments = dashboardQueryRepository.countAppointmentsByStatusBetween(range.getFromDate(), range.getToDate(), "NO_SHOW");

        long totalEncounters = dashboardQueryRepository.countEncountersStartedBetween(range.getFromDateTime(), range.getToDateTime());
        long inProgressEncounters = dashboardQueryRepository.countEncountersByStatusBetween("IN_PROGRESS", range.getFromDateTime(), range.getToDateTime());
        long uniquePatientsInRange = dashboardQueryRepository.countUniquePatientsBetween(
                range.getFromDate(),
                range.getToDate(),
                range.getFromDateTime(),
                range.getToDateTime()
        );

        long waitingServiceItems = dashboardQueryRepository.countServiceOrderItemsByStatusBetween("WAITING_EXECUTION", range.getFromDateTime(), range.getToDateTime());
        long grossPaidRevenue = dashboardQueryRepository.sumGrossPaidRevenueBetween(range.getFromDateTime(), range.getToDateTime());
        long refundedAmountForPaidInvoices = dashboardQueryRepository.sumRefundedAmountForPaidInvoicesBetween(range.getFromDateTime(), range.getToDateTime());
        long netPaidRevenue = dashboardQueryRepository.sumPaidRevenueBetween(range.getFromDateTime(), range.getToDateTime());
        long refundsProcessed = dashboardQueryRepository.sumRefundsProcessedBetween(range.getFromDateTime(), range.getToDateTime());

        return DashboardOverviewResponse.builder()
                                        .fromDate(range.getFromDate())
                                        .toDate(range.getToDate())
                                        .today(DashboardTodaySummaryResponse.builder()
                                                                            .totalAppointments(totalAppointments)
                                                                            .arrivedAppointments(arrivedAppointments)
                                                                            .checkedInAppointments(checkedInAppointments)
                                                                            .noShowAppointments(noShowAppointments)
                                                                            .totalEncounters(totalEncounters)
                                                                            .inProgressEncounters(inProgressEncounters)
                                                                            .uniquePatientsInRange(uniquePatientsInRange)
                                                                            .waitingServiceItems(waitingServiceItems)
                                                                            .grossPaidRevenue(grossPaidRevenue)
                                                                            .refundedAmountForPaidInvoices(refundedAmountForPaidInvoices)
                                                                            .netPaidRevenue(netPaidRevenue)
                                                                            .refundsProcessed(refundsProcessed)
                                                                            .paidRevenue(netPaidRevenue)
                                                                            .build())
                                        .appointmentSeries(buildAppointmentSeries(range.getFromDate(), range.getToDate()))
                                        .revenueSeries(buildRevenueSeries(range.getFromDate(), range.getToDate()))
                                        .noShowSeries(buildNoShowSeries(range.getFromDate(), range.getToDate()))
                                        .build();
    }

    @Transactional(readOnly = true)
    public DashboardOverviewResponse overview(String period, LocalDate fromDate, LocalDate toDate) {
        return overview(period, fromDate, toDate, null);
    }

    @Transactional(readOnly = true)
    public DashboardBreakdownResponse breakdown(String period, LocalDate fromDate, LocalDate toDate, Integer days, int topN) {
        DashboardDateRange range = resolveDateRange(period, fromDate, toDate, days);
        int safeTopN = Math.max(topN, 1);

        var branches = dashboardQueryRepository.appointmentByBranch(range.getFromDate(), range.getToDate()).stream()
                                               .limit(safeTopN)
                                               .map(this::toBreakdownItem)
                                               .toList();

        var specialties = dashboardQueryRepository.appointmentBySpecialty(range.getFromDate(), range.getToDate()).stream()
                                                  .limit(safeTopN)
                                                  .map(this::toBreakdownItem)
                                                  .toList();

        var topDoctors = dashboardQueryRepository.topDoctors(range.getFromDateTime(), range.getToDateTime()).stream()
                                                 .limit(safeTopN)
                                                 .map(this::toBreakdownItem)
                                                 .toList();

        var topServices = dashboardQueryRepository.topServices(range.getFromDateTime(), range.getToDateTime()).stream()
                                                  .limit(safeTopN)
                                                  .map(this::toBreakdownItem)
                                                  .toList();

        return DashboardBreakdownResponse.builder()
                                         .fromDate(range.getFromDate())
                                         .toDate(range.getToDate())
                                         .branches(branches)
                                         .specialties(specialties)
                                         .topDoctors(topDoctors)
                                         .topServices(topServices)
                                         .build();
    }

    @Transactional(readOnly = true)
    public DashboardBreakdownResponse breakdown(int days, int topN) {
        return breakdown(null, null, null, days, topN);
    }

    @Transactional(readOnly = true)
    public DashboardKpiResponse kpis(String period, LocalDate fromDate, LocalDate toDate, Integer days, int topN) {
        DashboardDateRange range = resolveDateRange(period, fromDate, toDate, days);
        int safeTopN = Math.max(topN, 1);

        var doctorKpis = dashboardQueryRepository.doctorKpis(range.getFromDate(), range.getToDate()).stream()
                                                 .limit(safeTopN)
                                                 .map(this::toDoctorKpi)
                                                 .toList();

        var specialtyKpis = dashboardQueryRepository.specialtyKpis(range.getFromDate(), range.getToDate()).stream()
                                                    .limit(safeTopN)
                                                    .map(this::toSpecialtyKpi)
                                                    .toList();

        var branchRevenue = dashboardQueryRepository.revenueByBranch(range.getFromDateTime(), range.getToDateTime()).stream()
                                                    .limit(safeTopN)
                                                    .map(this::toBranchRevenue)
                                                    .toList();

        return DashboardKpiResponse.builder()
                                   .fromDate(range.getFromDate())
                                   .toDate(range.getToDate())
                                   .doctorKpis(doctorKpis)
                                   .specialtyKpis(specialtyKpis)
                                   .branchRevenue(branchRevenue)
                                   .build();
    }

    @Transactional(readOnly = true)
    public DashboardKpiResponse kpis(int days, int topN) {
        return kpis(null, null, null, days, topN);
    }

    private DashboardDateRange resolveDateRange(
            String period,
            LocalDate fromDate,
            LocalDate toDate,
            Integer days
    ) {
        if (fromDate != null || toDate != null) {
            if (fromDate == null || toDate == null) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "fromDate and toDate must be provided together.");
            }
            return buildDateRange(fromDate, toDate);
        }

        String normalizedPeriod = StringUtil.trimToNull(period);
        if (normalizedPeriod != null) {
            if (days != null) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "Use either period or days, not both.");
            }
            return resolvePeriod(normalizedPeriod);
        }

        if (days != null) {
            return resolveDays(days);
        }

        return switch (DEFAULT_DATE_RANGE) {
            case TODAY -> resolvePeriod("today");
            case LAST_7_DAYS -> resolveDays(7);
        };
    }

    private DashboardDateRange resolvePeriod(String period) {
        LocalDate today = LocalDate.now();
        String normalizedPeriod = period.trim().toLowerCase(Locale.ROOT);

        return switch (normalizedPeriod) {
            case "today" -> buildDateRange(today, today);
            case "month" -> buildDateRange(today.withDayOfMonth(1), today);
            case "year" -> buildDateRange(today.withDayOfYear(1), today);
            case "custom" -> throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "fromDate and toDate are required when period is custom."
            );
            default -> throw new ApiException(ErrorCode.INVALID_REQUEST, "Invalid dashboard period.");
        };
    }

    private DashboardDateRange resolveDays(int days) {
        int safeDays = Math.max(days, 1);
        LocalDate toDate = LocalDate.now();
        LocalDate fromDate = toDate.minusDays(safeDays - 1L);
        return buildDateRange(fromDate, toDate);
    }

    private DashboardDateRange buildDateRange(LocalDate fromDate, LocalDate toDate) {
        if (toDate.isBefore(fromDate)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "fromDate must be before or equal to toDate.");
        }

        return DashboardDateRange.builder()
                                 .fromDate(fromDate)
                                 .toDate(toDate)
                                 .fromDateTime(fromDate.atStartOfDay())
                                 .toDateTime(toDate.plusDays(1).atStartOfDay().minusNanos(1))
                                 .build();
    }

    private List<DashboardDailyMetricPointResponse> buildAppointmentSeries(LocalDate startDate, LocalDate endDate) {
        List<DashboardDailyMetricPointResponse> result = new ArrayList<>();

        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            long value = dashboardQueryRepository.countAppointmentsOnDate(d);
            result.add(DashboardDailyMetricPointResponse.builder()
                                                        .date(d.toString())
                                                        .value(value)
                                                        .build());
        }

        return result;
    }

    private List<DashboardDailyMetricPointResponse> buildNoShowSeries(LocalDate startDate, LocalDate endDate) {
        List<DashboardDailyMetricPointResponse> result = new ArrayList<>();

        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            long value = dashboardQueryRepository.countAppointmentsByStatusOnDate(d, "NO_SHOW");
            result.add(DashboardDailyMetricPointResponse.builder()
                                                        .date(d.toString())
                                                        .value(value)
                                                        .build());
        }

        return result;
    }

    private List<DashboardDailyMetricPointResponse> buildRevenueSeries(LocalDate startDate, LocalDate endDate) {
        List<DashboardDailyMetricPointResponse> result = new ArrayList<>();

        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
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
                                             .netPaidRevenue(row.getPaidRevenue() != null ? row.getPaidRevenue() : 0L)
                                             .paidRevenue(row.getPaidRevenue() != null ? row.getPaidRevenue() : 0L)
                                             .build();
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private enum DefaultDateRange {
        TODAY,
        LAST_7_DAYS
    }
}
