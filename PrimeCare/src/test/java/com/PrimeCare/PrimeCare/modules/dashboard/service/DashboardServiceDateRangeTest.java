package com.PrimeCare.PrimeCare.modules.dashboard.service;

import com.PrimeCare.PrimeCare.modules.dashboard.repository.DashboardQueryRepository;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceDateRangeTest {

    @Mock
    private DashboardQueryRepository dashboardQueryRepository;

    @InjectMocks
    private DashboardService service;

    @Test
    void overviewWithCustomRangeUsesExactRangeForCardsAndSeries() {
        LocalDate from = LocalDate.of(2026, 5, 1);
        LocalDate to = LocalDate.of(2026, 5, 7);

        var response = service.overview(null, from, to, null);

        assertThat(response.getFromDate()).isEqualTo(from);
        assertThat(response.getToDate()).isEqualTo(to);
        assertThat(response.getAppointmentSeries())
                .extracting(point -> point.getDate())
                .containsExactly(
                        "2026-05-01",
                        "2026-05-02",
                        "2026-05-03",
                        "2026-05-04",
                        "2026-05-05",
                        "2026-05-06",
                        "2026-05-07"
                );
        verify(dashboardQueryRepository).countAppointmentsBetween(from, to);
        verify(dashboardQueryRepository).countArrivedAppointmentsBetween(from, to);
        verify(dashboardQueryRepository).countAppointmentsByStatusBetween(from, to, "CHECKED_IN");
        verify(dashboardQueryRepository).countAppointmentsByStatusBetween(from, to, "NO_SHOW");
        verify(dashboardQueryRepository).countUniquePatientsBetween(from, to, startOfDay(from), endOfDay(to));
        verify(dashboardQueryRepository).sumGrossPaidRevenueBetween(startOfDay(from), endOfDay(to));
        verify(dashboardQueryRepository).sumRefundedAmountForPaidInvoicesBetween(startOfDay(from), endOfDay(to));
        verify(dashboardQueryRepository).sumPaidRevenueBetween(startOfDay(from), endOfDay(to));
        verify(dashboardQueryRepository).sumRefundsProcessedBetween(startOfDay(from), endOfDay(to));
    }

    @Test
    void overviewReturnsNetRevenueAndRefundMetrics() {
        LocalDate date = LocalDate.of(2026, 5, 17);
        LocalDateTime from = startOfDay(date);
        LocalDateTime to = endOfDay(date);
        when(dashboardQueryRepository.sumGrossPaidRevenueBetween(from, to)).thenReturn(1_000_000L);
        when(dashboardQueryRepository.sumRefundedAmountForPaidInvoicesBetween(from, to)).thenReturn(300_000L);
        when(dashboardQueryRepository.sumPaidRevenueBetween(from, to)).thenReturn(700_000L);
        when(dashboardQueryRepository.sumRefundsProcessedBetween(from, to)).thenReturn(125_000L);

        var response = service.overview(null, date, date, null);

        assertThat(response.getToday().getGrossPaidRevenue()).isEqualTo(1_000_000L);
        assertThat(response.getToday().getRefundedAmountForPaidInvoices()).isEqualTo(300_000L);
        assertThat(response.getToday().getNetPaidRevenue()).isEqualTo(700_000L);
        assertThat(response.getToday().getPaidRevenue()).isEqualTo(700_000L);
        assertThat(response.getToday().getRefundsProcessed()).isEqualTo(125_000L);
    }

    @Test
    void overviewReturnsUniquePatientCountForResolvedRange() {
        LocalDate fromDate = LocalDate.of(2026, 5, 1);
        LocalDate toDate = LocalDate.of(2026, 5, 7);
        when(dashboardQueryRepository.countUniquePatientsBetween(
                fromDate,
                toDate,
                startOfDay(fromDate),
                endOfDay(toDate)
        )).thenReturn(4L);

        var response = service.overview(null, fromDate, toDate, null);

        assertThat(response.getToday().getUniquePatientsInRange()).isEqualTo(4L);
    }

    @Test
    void dashboardEndpointsShareSameDefaultDateRange() {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(6);
        stubBreakdownQueries(from, to);
        stubKpiQueries(from, to);

        var overview = service.overview(null, null, null, null);
        var breakdown = service.breakdown(null, null, null, null, 5);
        var kpis = service.kpis(null, null, null, null, 10);

        assertThat(overview.getFromDate()).isEqualTo(from);
        assertThat(overview.getToDate()).isEqualTo(to);
        assertThat(breakdown.getFromDate()).isEqualTo(from);
        assertThat(breakdown.getToDate()).isEqualTo(to);
        assertThat(kpis.getFromDate()).isEqualTo(from);
        assertThat(kpis.getToDate()).isEqualTo(to);
    }

    @Test
    void breakdownWithCustomRangeUsesExactRangeAndIgnoresDays() {
        LocalDate from = LocalDate.of(2026, 5, 1);
        LocalDate to = LocalDate.of(2026, 5, 7);
        stubBreakdownQueries(from, to);

        var response = service.breakdown("today", from, to, 30, 5);

        assertThat(response.getFromDate()).isEqualTo(from);
        assertThat(response.getToDate()).isEqualTo(to);
        verify(dashboardQueryRepository).appointmentByBranch(from, to);
        verify(dashboardQueryRepository).appointmentBySpecialty(from, to);
        verify(dashboardQueryRepository).topDoctors(startOfDay(from), endOfDay(to));
        verify(dashboardQueryRepository).topServices(startOfDay(from), endOfDay(to));
    }

    @Test
    void kpisWithCustomRangeUsesExactRange() {
        LocalDate from = LocalDate.of(2026, 5, 1);
        LocalDate to = LocalDate.of(2026, 5, 7);
        stubKpiQueries(from, to);

        var response = service.kpis(null, from, to, null, 10);

        assertThat(response.getFromDate()).isEqualTo(from);
        assertThat(response.getToDate()).isEqualTo(to);
        verify(dashboardQueryRepository).doctorKpis(from, to);
        verify(dashboardQueryRepository).specialtyKpis(from, to);
        verify(dashboardQueryRepository).revenueByBranch(startOfDay(from), endOfDay(to));
    }

    @Test
    void invalidCustomRangeIsRejectedBeforeQueriesRun() {
        LocalDate from = LocalDate.of(2026, 5, 7);
        LocalDate to = LocalDate.of(2026, 5, 1);

        assertThatThrownBy(() -> service.overview(null, from, to, null))
                .isInstanceOf(ApiException.class)
                .hasMessage("fromDate must be before or equal to toDate.");

        verifyNoInteractions(dashboardQueryRepository);
    }

    @Test
    void periodAndDaysWithoutExplicitDatesIsRejected() {
        assertThatThrownBy(() -> service.kpis("month", null, null, 7, 10))
                .isInstanceOf(ApiException.class)
                .hasMessage("Use either period or days, not both.");

        verifyNoInteractions(dashboardQueryRepository);
    }

    private void stubBreakdownQueries(LocalDate from, LocalDate to) {
        when(dashboardQueryRepository.appointmentByBranch(from, to)).thenReturn(List.of());
        when(dashboardQueryRepository.appointmentBySpecialty(from, to)).thenReturn(List.of());
        when(dashboardQueryRepository.topDoctors(startOfDay(from), endOfDay(to))).thenReturn(List.of());
        when(dashboardQueryRepository.topServices(startOfDay(from), endOfDay(to))).thenReturn(List.of());
    }

    private void stubKpiQueries(LocalDate from, LocalDate to) {
        when(dashboardQueryRepository.doctorKpis(from, to)).thenReturn(List.of());
        when(dashboardQueryRepository.specialtyKpis(from, to)).thenReturn(List.of());
        when(dashboardQueryRepository.revenueByBranch(startOfDay(from), endOfDay(to))).thenReturn(List.of());
    }

    private LocalDateTime startOfDay(LocalDate date) {
        return date.atStartOfDay();
    }

    private LocalDateTime endOfDay(LocalDate date) {
        return date.plusDays(1).atStartOfDay().minusNanos(1);
    }
}
