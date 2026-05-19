import { type ElementType, type ReactNode, useMemo, useState } from 'react';
import {
  Banknote,
  Building2,
  CalendarCheck2,
  CheckCircle2,
  Clock3,
  Stethoscope,
  UserRoundCheck,
  UsersRound,
} from 'lucide-react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { useDashboardBreakdown, useDashboardKpis, useDashboardOverview } from '@/hooks/use-admin-data';
import { cn } from '@/lib/utils';
import { toLocalDateInputValue } from '@/lib/date';
import type {
  DashboardBreakdownItem,
  DashboardBranchRevenue,
  DashboardDoctorKpi,
  DashboardSpecialtyKpi,
} from '@/types/api';

type DashboardPeriod = 'today' | 'month' | 'year' | 'custom';

type DashboardFilterState = {
  period: DashboardPeriod;
  fromDate: string;
  toDate: string;
};

type DashboardResolvedRange = {
  period?: string;
  fromDate?: string;
  toDate?: string;
};

const pieColors = [
  'hsl(var(--primary))',
  'hsl(var(--accent))',
  'hsl(var(--success))',
  'hsl(var(--warning))',
  'hsl(var(--muted-foreground))',
];

const statusLabels: Record<string, string> = {
  REQUESTED: 'Chờ xác nhận',
  CONFIRMED: 'Đã xác nhận',
  ARRIVED: 'Đã đến',
  CHECKED_IN: 'Đã check-in',
  COMPLETED: 'Đã hoàn tất',
  CANCELLED: 'Đã hủy',
  NO_SHOW: 'Không đến',
};

const dashboardCardClass =
  'border-border/80 bg-card shadow-[0_1px_2px_hsl(var(--shadow-color)_/_0.04),0_10px_24px_hsl(var(--shadow-color)_/_0.055)]';
const dashboardSecondaryCardClass =
  'border-border/75 bg-card shadow-[0_1px_2px_hsl(var(--shadow-color)_/_0.035),0_6px_18px_hsl(var(--shadow-color)_/_0.045)]';
const dashboardCardHeaderClass = 'border-b border-border/70 px-ui-lg py-ui-md';
const dashboardPanelClass = 'rounded-lg border border-border/75 bg-background/90';

function formatCompactCurrency(value: number) {
  if (value >= 1_000_000_000) return `${(value / 1_000_000_000).toFixed(1)} tỷ`;
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(1)} triệu`;
  return `${Math.round(value).toLocaleString('vi-VN')} đ`;
}

function formatCurrency(value: number) {
  return `${Math.round(value).toLocaleString('vi-VN')} đ`;
}

function formatShortDate(value: string) {
  if (!value) return '—';
  return String(value).slice(5);
}

function getPeriodLabel(period: DashboardPeriod) {
  if (period === 'today') return 'hôm nay';
  if (period === 'month') return 'tháng này';
  if (period === 'year') return 'năm nay';
  return 'khoảng thời gian đã chọn';
}

function getDashboardPresetRange(period: Exclude<DashboardPeriod, 'custom'>) {
  const now = new Date();
  const today = toLocalDateInputValue(now);

  if (period === 'month') {
    return {
      fromDate: toLocalDateInputValue(new Date(now.getFullYear(), now.getMonth(), 1)),
      toDate: today,
    };
  }

  if (period === 'year') {
    return {
      fromDate: toLocalDateInputValue(new Date(now.getFullYear(), 0, 1)),
      toDate: today,
    };
  }

  return { fromDate: today, toDate: today };
}

function getSelectedDashboardRange(filter: DashboardFilterState) {
  if (filter.period === 'custom') {
    return {
      fromDate: filter.fromDate,
      toDate: filter.toDate,
    };
  }

  return getDashboardPresetRange(filter.period);
}

function buildDashboardRangeParams(filter: DashboardFilterState): Record<string, string> {
  if (filter.period === 'custom') {
    return {
      period: filter.period,
      fromDate: filter.fromDate,
      toDate: filter.toDate,
    };
  }

  return { period: filter.period };
}

function formatActiveDashboardRange(range: DashboardResolvedRange, today: string) {
  const fromDate = range.fromDate?.slice(0, 10);
  const toDate = range.toDate?.slice(0, 10);

  if (fromDate === today && toDate === today) {
    return 'Đang xem dữ liệu hôm nay';
  }

  if (fromDate && toDate) {
    return `Đang xem dữ liệu từ ${fromDate} đến ${toDate}`;
  }

  return '';
}

export default function DashboardPage() {
  const today = useMemo(() => toLocalDateInputValue(), []);
  const [dashboardFilter, setDashboardFilter] = useState<DashboardFilterState>(() => ({
    period: 'today',
    fromDate: today,
    toDate: today,
  }));

  const dashboardRangeParams = useMemo(
    () => buildDashboardRangeParams(dashboardFilter),
    [dashboardFilter],
  );
  const breakdownParams = useMemo(() => ({
    ...dashboardRangeParams,
    topN: '5',
  }), [dashboardRangeParams]);
  const kpisParams = useMemo(() => ({
    ...dashboardRangeParams,
    topN: '8',
  }), [dashboardRangeParams]);

  const { data: overviewData, isLoading: overviewLoading } = useDashboardOverview(dashboardRangeParams);
  const { data: breakdownData, isLoading: breakdownLoading } = useDashboardBreakdown(breakdownParams);
  const { data: kpisData, isLoading: kpisLoading } = useDashboardKpis(kpisParams);

  const overview =
    overviewData ??
    {
      totalAppointmentsToday: 0,
      totalPatientsToday: 0,
      totalRevenue: 0,
      completionRate: 0,
      arrivedAppointments: 0,
      checkedInAppointments: 0,
      noShowAppointments: 0,
      inProgressEncounters: 0,
      waitingServiceItems: 0,
      appointmentsByStatus: {},
      revenueBySpecialty: [],
      appointmentsTrend: [],
      revenueTrend: [],
      noShowTrend: [],
    };

  const breakdown =
    breakdownData ?? {
      branches: [],
      specialties: [],
      topDoctors: [],
      topServices: [],
    };

  const kpis =
    kpisData ?? {
      doctorKpis: [],
      specialtyKpis: [],
      branchRevenue: [],
    };

  const pieData = Object.entries(overview.appointmentsByStatus)
    .filter(([, value]) => Number(value) > 0)
    .map(([name, value]) => ({
      name: statusLabels[name] || name,
      value: Number(value),
    }));

  const selectedDashboardRange = useMemo(
    () => getSelectedDashboardRange(dashboardFilter),
    [dashboardFilter],
  );
  const backendResolvedRange =
    overviewData?.resolvedRange ?? breakdownData?.resolvedRange ?? kpisData?.resolvedRange;
  const displayRange =
    backendResolvedRange?.fromDate && backendResolvedRange.toDate
      ? backendResolvedRange
      : selectedDashboardRange;
  const activeRangeLabel = formatActiveDashboardRange(displayRange, today);
  const periodLabel = getPeriodLabel(dashboardFilter.period);
  const netRevenue = overview.netRevenue ?? overview.totalRevenue;
  const grossRevenue = overview.grossRevenue ?? netRevenue;
  const refundedAmount = overview.refundedAmount ?? Math.max(0, grossRevenue - netRevenue);
  const patientMetricTitle = overview.patientMetricLabel ?? 'Lượt khám';
  const patientMetricDescription = overview.patientMetricDescription ?? periodLabel;
  const completionColor = overview.completionRate >= 85 ? 'text-success' : overview.completionRate >= 60 ? 'text-warning' : 'text-destructive';
  const completionFill = overview.completionRate >= 85 ? 'bg-success' : overview.completionRate >= 60 ? 'bg-warning' : 'bg-destructive';

  const updateDashboardPeriod = (nextPeriod: DashboardPeriod) => {
    setDashboardFilter((current) => ({
      ...current,
      period: nextPeriod,
      fromDate: current.fromDate || today,
      toDate: current.toDate || today,
    }));
  };

  const updateCustomFromDate = (nextFromDate: string) => {
    if (!nextFromDate) return;
    setDashboardFilter((current) => ({
      ...current,
      fromDate: nextFromDate,
      toDate: current.toDate && current.toDate >= nextFromDate ? current.toDate : nextFromDate,
    }));
  };

  const updateCustomToDate = (nextToDate: string) => {
    if (!nextToDate) return;
    setDashboardFilter((current) => ({
      ...current,
      fromDate: current.fromDate && current.fromDate <= nextToDate ? current.fromDate : nextToDate,
      toDate: nextToDate,
    }));
  };

  return (
    <div className="space-y-ui-lg">
      <section className="rounded-lg border border-border/80 bg-card/95 px-ui-lg py-ui-md shadow-[0_1px_2px_hsl(var(--shadow-color)_/_0.04)]">
        <div className="flex flex-col gap-ui-md xl:flex-row xl:items-end xl:justify-between">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground">
              Bảng điều hành nội bộ
            </p>
            <h1 className="mt-ui-xs text-2xl font-semibold tracking-tight text-foreground">
              Dashboard vận hành
            </h1>
            <p className="mt-ui-xs max-w-3xl text-sm leading-6 text-muted-foreground">
              Theo dõi lịch hẹn, luồng tiếp nhận, ca đang khám và doanh thu theo {periodLabel}.
            </p>
          </div>

          <div className="flex flex-col gap-ui-xs xl:items-end">
            <div className="flex flex-wrap items-end gap-ui-sm xl:justify-end">
              <div className="inline-flex rounded-lg border border-border bg-muted/25 p-1">
                {[
                  { value: 'today', label: 'Hôm nay' },
                  { value: 'month', label: 'Tháng này' },
                  { value: 'year', label: 'Năm nay' },
                  { value: 'custom', label: 'Tùy chọn' },
                ].map((option) => (
                  <Button
                    key={option.value}
                    type="button"
                    variant={dashboardFilter.period === option.value ? 'default' : 'ghost'}
                    size="sm"
                    className={cn('h-8 rounded-md px-3', dashboardFilter.period !== option.value && 'text-muted-foreground')}
                    onClick={() => updateDashboardPeriod(option.value as DashboardPeriod)}
                  >
                    {option.label}
                  </Button>
                ))}
              </div>
              {dashboardFilter.period === 'custom' && (
                <div className="flex flex-wrap items-end gap-ui-xs rounded-lg border border-border bg-background p-ui-xs shadow-sm">
                  <div>
                    <label htmlFor="dashboard-from-date" className="mb-1 block text-xs font-medium text-muted-foreground">Từ ngày</label>
                    <Input
                      id="dashboard-from-date"
                      type="date"
                      value={dashboardFilter.fromDate}
                      max={dashboardFilter.toDate}
                      onChange={(e) => updateCustomFromDate(e.target.value)}
                      className="h-8 min-w-[145px]"
                    />
                  </div>
                  <div>
                    <label htmlFor="dashboard-to-date" className="mb-1 block text-xs font-medium text-muted-foreground">Đến ngày</label>
                    <Input
                      id="dashboard-to-date"
                      type="date"
                      value={dashboardFilter.toDate}
                      min={dashboardFilter.fromDate}
                      onChange={(e) => updateCustomToDate(e.target.value)}
                      className="h-8 min-w-[145px]"
                    />
                  </div>
                </div>
              )}
            </div>
            {activeRangeLabel ? (
              <p className="text-xs leading-5 text-muted-foreground xl:text-right">
                {activeRangeLabel}
              </p>
            ) : null}
          </div>
        </div>
      </section>

      <div className="grid grid-cols-1 gap-ui-sm min-[380px]:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-6">
        <OperationalKpiCard title="Lịch hẹn" value={overviewLoading ? '...' : overview.totalAppointmentsToday.toLocaleString('vi-VN')} label={periodLabel} icon={CalendarCheck2} />
        <OperationalKpiCard title={patientMetricTitle} value={overviewLoading ? '...' : overview.totalPatientsToday.toLocaleString('vi-VN')} label={patientMetricDescription} icon={Stethoscope} />
        <OperationalKpiCard title="Check-in" value={overviewLoading ? '...' : overview.checkedInAppointments.toLocaleString('vi-VN')} label="đã xác nhận" icon={UserRoundCheck} />
        <OperationalKpiCard title="Đang khám" value={overviewLoading ? '...' : overview.inProgressEncounters.toLocaleString('vi-VN')} label="cần theo dõi" icon={UsersRound} />
        <OperationalKpiCard title="Chờ dịch vụ" value={overviewLoading ? '...' : overview.waitingServiceItems.toLocaleString('vi-VN')} label="hàng đợi CLS" icon={Clock3} />
        <OperationalKpiCard title="Doanh thu ròng" value={overviewLoading ? '...' : formatCompactCurrency(netRevenue)} label={periodLabel} icon={Banknote} />
      </div>

      <div className="grid grid-cols-1 gap-ui-lg xl:grid-cols-[minmax(0,1fr)_24rem]">
        <Card className={dashboardCardClass}>
          <CardHeader className={dashboardCardHeaderClass}>
            <div className="flex flex-col gap-ui-xs sm:flex-row sm:items-center sm:justify-between">
              <div>
                <CardTitle className="text-base font-semibold leading-5">Trạng thái lịch hẹn</CardTitle>
                <CardDescription className="leading-5">Phân bổ theo các mốc vận hành chính trong {periodLabel}.</CardDescription>
              </div>
              <Badge variant="outline">{pieData.reduce((sum, item) => sum + item.value, 0).toLocaleString('vi-VN')} lượt</Badge>
            </div>
          </CardHeader>
          <CardContent className="p-ui-lg">
            {pieData.length === 0 ? (
              <DashboardEmptyState
                label="Chưa có dữ liệu trạng thái trong khoảng thời gian này."
                helper="Dữ liệu sẽ xuất hiện khi có lịch hẹn phát sinh trong bộ lọc hiện tại."
              />
            ) : (
              <div className="grid gap-ui-lg lg:grid-cols-[minmax(0,1fr)_220px]">
                <ResponsiveContainer width="100%" height={260}>
                  <PieChart>
                    <Pie
                      data={pieData}
                      dataKey="value"
                      nameKey="name"
                      cx="50%"
                      cy="50%"
                      outerRadius={88}
                      innerRadius={56}
                      paddingAngle={2}
                    >
                      {pieData.map((entry, index) => (
                        <Cell key={`${entry.name}-${entry.value}`} fill={pieColors[index % pieColors.length]} />
                      ))}
                    </Pie>
                    <Tooltip formatter={(value) => Number(value).toLocaleString('vi-VN')} />
                  </PieChart>
                </ResponsiveContainer>
                <div className={cn(dashboardPanelClass, 'divide-y divide-border/70')}>
                  {pieData.map((entry, index) => (
                    <div key={entry.name} className="flex items-center justify-between gap-ui-sm px-ui-md py-ui-sm">
                      <div className="flex min-w-0 items-center gap-ui-xs">
                        <span
                          className="h-2.5 w-2.5 shrink-0 rounded-full"
                          style={{ backgroundColor: pieColors[index % pieColors.length] }}
                        />
                        <span className="truncate text-sm font-medium text-foreground">{entry.name}</span>
                      </div>
                      <span className="text-sm font-semibold tabular-nums text-foreground">
                        {entry.value.toLocaleString('vi-VN')}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </CardContent>
        </Card>

        <Card className={dashboardCardClass}>
          <CardHeader className={dashboardCardHeaderClass}>
            <div className="flex items-start justify-between gap-ui-md">
              <div>
                <CardTitle className="text-base font-semibold leading-5">Nhịp vận hành</CardTitle>
                <CardDescription className="leading-5">Các chỉ số cần quét nhanh trong ca trực.</CardDescription>
              </div>
              <Badge variant="secondary" className="shrink-0">
                {dashboardFilter.period === 'today' ? 'Theo ngày' : dashboardFilter.period === 'custom' ? 'Khoảng chọn' : 'Lũy kế'}
              </Badge>
            </div>
          </CardHeader>
          <CardContent className="space-y-ui-md p-ui-lg">
            <div className={cn(dashboardPanelClass, 'p-ui-md')}>
              <div className="flex items-start justify-between gap-ui-md">
                <div>
                  <p className="text-sm font-medium text-muted-foreground">Tỷ lệ hoàn tất</p>
                  <p className={cn('mt-ui-xs text-[2rem] font-semibold leading-none tracking-tight', completionColor)}>
                    {overviewLoading ? '...' : `${overview.completionRate}%`}
                  </p>
                </div>
                <span className="rounded-md bg-muted/50 px-ui-xs py-1 text-xs font-medium text-muted-foreground">
                  Ca trực
                </span>
              </div>
              <div className="mt-ui-sm h-2 overflow-hidden rounded-full bg-muted">
                <div
                  className={cn('h-full rounded-full transition-all', completionFill)}
                  style={{ width: `${Math.min(Math.max(overview.completionRate, 0), 100)}%` }}
                />
              </div>
              <p className="mt-ui-xs text-xs leading-5 text-muted-foreground">
                Khám/tiếp nhận so với tổng lịch hẹn trong {periodLabel}.
              </p>
            </div>
            <div className={cn(dashboardPanelClass, 'divide-y divide-border/70')}>
              <MetricRow label="Đã đến" value={overview.arrivedAppointments.toLocaleString('vi-VN')} />
              <MetricRow label="Đã check-in" value={overview.checkedInAppointments.toLocaleString('vi-VN')} />
              <MetricRow label="Đang khám" value={overview.inProgressEncounters.toLocaleString('vi-VN')} tone="primary" />
              <MetricRow label="Không đến" value={overview.noShowAppointments.toLocaleString('vi-VN')} tone="danger" />
              <MetricRow label="Tổng đã thu" value={formatCompactCurrency(grossRevenue)} />
              <MetricRow label="Đã hoàn" value={formatCompactCurrency(refundedAmount)} tone="danger" />
              {typeof overview.refundsProcessedInRange === 'number' ? (
                <MetricRow label="Hoàn phát sinh" value={formatCompactCurrency(overview.refundsProcessedInRange)} tone="danger" />
              ) : null}
              <MetricRow label="Doanh thu ròng" value={formatCompactCurrency(netRevenue)} tone="primary" />
            </div>
          </CardContent>
        </Card>
      </div>

      <div className="grid grid-cols-1 gap-ui-lg xl:grid-cols-3">
        <ChartCard
          title="Xu hướng lịch hẹn"
          description={`Số lịch hẹn được tạo hoặc ghi nhận theo ${periodLabel}.`}
          loading={overviewLoading}
          empty={overview.appointmentsTrend.length === 0}
          emptyLabel="Chưa có lịch hẹn trong chuỗi thời gian này."
          emptyHelper="Thử đổi bộ lọc thời gian nếu cần kiểm tra giai đoạn khác."
        >
          <ResponsiveContainer width="100%" height={280}>
            <BarChart data={overview.appointmentsTrend}>
              <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
              <XAxis dataKey="date" tick={{ fontSize: 12 }} tickFormatter={formatShortDate} />
              <YAxis tick={{ fontSize: 12 }} allowDecimals={false} />
              <Tooltip formatter={(value) => Number(value).toLocaleString('vi-VN')} labelFormatter={(value) => `Ngày ${value}`} />
              <Bar dataKey="count" fill="hsl(var(--primary))" radius={[8, 8, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </ChartCard>

        <ChartCard
          title="Doanh thu sau hoàn tiền"
          description={`Theo dõi doanh thu ròng theo ${periodLabel}.`}
          loading={overviewLoading}
          empty={overview.revenueTrend.length === 0}
          emptyLabel="Chưa có doanh thu để hiển thị."
          emptyHelper="Doanh thu ròng sẽ xuất hiện khi có hóa đơn đã thu trong bộ lọc hiện tại."
        >
          <ResponsiveContainer width="100%" height={280}>
            <LineChart data={overview.revenueTrend}>
              <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
              <XAxis dataKey="date" tick={{ fontSize: 12 }} tickFormatter={formatShortDate} />
              <YAxis tick={{ fontSize: 12 }} tickFormatter={(value) => `${Math.round(Number(value) / 1000)}k`} />
              <Tooltip formatter={(value) => formatCurrency(Number(value))} labelFormatter={(value) => `Ngày ${value}`} />
              <Line type="monotone" dataKey="value" stroke="hsl(var(--accent))" strokeWidth={3} dot={{ r: 3 }} />
            </LineChart>
          </ResponsiveContainer>
        </ChartCard>

        <ChartCard
          title="Số ca không đến"
          description="Tín hiệu sớm cho việc tối ưu xác nhận lịch và nhắc hẹn."
          loading={overviewLoading}
          empty={overview.noShowTrend.length === 0}
          emptyLabel="Chưa có dữ liệu không đến trong chuỗi thời gian này."
          emptyHelper="Không có lượt không đến nào được ghi nhận cho bộ lọc hiện tại."
        >
          <ResponsiveContainer width="100%" height={280}>
            <BarChart data={overview.noShowTrend}>
              <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
              <XAxis dataKey="date" tick={{ fontSize: 12 }} tickFormatter={formatShortDate} />
              <YAxis tick={{ fontSize: 12 }} allowDecimals={false} />
              <Tooltip formatter={(value) => Number(value).toLocaleString('vi-VN')} labelFormatter={(value) => `Ngày ${value}`} />
              <Bar dataKey="value" fill="hsl(var(--warning))" radius={[8, 8, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </ChartCard>
      </div>

      <div className="grid grid-cols-1 gap-ui-lg xl:grid-cols-4">
        <InsightListCard
          title="Chi nhánh nổi bật"
          description={`Top ${breakdown.branches.length || 5} chi nhánh theo số lịch hẹn.`}
          items={breakdown.branches}
          loading={breakdownLoading}
          icon={Building2}
        />
        <InsightListCard
          title="Chuyên khoa nổi bật"
          description="Các chuyên khoa có lượng lịch hẹn cao nhất."
          items={breakdown.specialties}
          loading={breakdownLoading}
          icon={Stethoscope}
        />
        <InsightListCard
          title="Bác sĩ nhiều lịch"
          description="Giúp điều phối nhân lực trong khung giờ cao điểm."
          items={breakdown.topDoctors}
          loading={breakdownLoading}
          icon={UsersRound}
        />
        <InsightListCard
          title="Dịch vụ dùng nhiều"
          description="Nhận diện nhóm dịch vụ phát sinh cao gần đây."
          items={breakdown.topServices}
          loading={breakdownLoading}
          icon={CheckCircle2}
        />
      </div>

      <div className="grid grid-cols-1 gap-ui-lg xl:grid-cols-3">
        <DoctorKpiCard items={kpis.doctorKpis} loading={kpisLoading} />
        <SpecialtyKpiCard items={kpis.specialtyKpis} loading={kpisLoading} />
        <BranchRevenueCard items={kpis.branchRevenue} loading={kpisLoading} />
      </div>
    </div>
  );
}

function OperationalKpiCard({
  title,
  value,
  label,
  icon: Icon,
}: {
  title: string;
  value: string;
  label: string;
  icon: ElementType;
}) {
  return (
    <Card className="h-full overflow-hidden border-border/80 bg-card shadow-[0_1px_2px_hsl(var(--shadow-color)_/_0.05),0_12px_26px_hsl(var(--shadow-color)_/_0.065)]">
      <div className="flex min-h-[7rem] flex-col justify-between px-ui-md py-ui-md">
        <div className="flex items-start justify-between gap-ui-sm">
          <p className="min-w-0 truncate text-sm font-medium leading-5 text-muted-foreground">{title}</p>
          <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md bg-primary/[0.06] text-primary/70">
            <Icon className="h-3.5 w-3.5" />
          </div>
        </div>
        <div className="min-w-0 pt-ui-xs">
          <p className="truncate text-[1.75rem] font-semibold leading-none tracking-tight text-foreground tabular-nums">
            {value}
          </p>
          <p className="mt-1 truncate text-[13px] leading-5 text-muted-foreground">{label}</p>
        </div>
      </div>
    </Card>
  );
}

function DashboardEmptyState({ label, helper }: { label: string; helper?: string }) {
  return (
    <div className="flex h-44 items-center justify-center rounded-lg border border-border/80 bg-surface-alt/55 px-ui-md text-center">
      <div className="flex max-w-sm flex-col items-center gap-ui-xs">
        <div className="flex h-8 w-8 items-center justify-center rounded-md bg-background text-primary/70 shadow-[0_1px_2px_hsl(var(--shadow-color)_/_0.04)]">
          <CheckCircle2 className="h-4 w-4" />
        </div>
        <p className="text-sm font-medium leading-5 text-foreground">{label}</p>
        {helper && <p className="text-xs leading-5 text-muted-foreground">{helper}</p>}
      </div>
    </div>
  );
}

function DashboardLoadingState({ label }: { label: string }) {
  return (
    <div className="flex h-44 items-center justify-center rounded-lg border border-border/80 bg-surface-alt/55 px-ui-md" aria-live="polite">
      <div className="w-full max-w-sm">
        <p className="text-sm font-medium leading-5 text-muted-foreground">{label}</p>
        <div className="mt-ui-sm space-y-ui-xs">
          <div className="h-2 w-3/4 rounded-full bg-background" />
          <div className="h-2 w-1/2 rounded-full bg-background" />
        </div>
      </div>
    </div>
  );
}

function LoadingLine({ label }: { label: string }) {
  return (
    <div className="rounded-lg border border-border/80 bg-surface-alt/55 px-ui-md py-ui-md" aria-live="polite">
      <p className="text-sm font-medium leading-5 text-muted-foreground">{label}</p>
      <div className="mt-ui-sm space-y-ui-xs">
        <div className="h-1.5 w-3/4 rounded-full bg-background" />
        <div className="h-1.5 w-1/2 rounded-full bg-background" />
      </div>
    </div>
  );
}

function EmptyLine({ label }: { label: string }) {
  return (
    <div className="rounded-lg border border-border/80 bg-surface-alt/55 px-ui-md py-ui-md">
      <div className="flex items-start gap-ui-xs">
        <span className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-primary/45" />
        <p className="text-sm leading-6 text-muted-foreground">{label}</p>
      </div>
    </div>
  );
}

function SectionIcon({ icon: Icon }: { icon: ElementType }) {
  return (
    <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-muted/60 text-primary/75">
      <Icon className="h-3.5 w-3.5" />
    </div>
  );
}

function MetricRow({
  label,
  value,
  tone,
}: {
  label: string;
  value: string;
  tone?: 'primary' | 'danger';
}) {
  return (
    <div className="flex min-h-10 items-center justify-between gap-ui-sm px-ui-md py-ui-xs text-sm">
      <span className="text-muted-foreground">{label}</span>
      <span
        className={cn(
          'font-semibold tabular-nums text-foreground',
          tone === 'primary' && 'text-primary',
          tone === 'danger' && 'text-destructive',
        )}
      >
        {value}
      </span>
    </div>
  );
}

function ChartCard({
  title,
  description,
  loading,
  empty,
  emptyLabel,
  emptyHelper,
  children,
}: {
  title: string;
  description: string;
  loading?: boolean;
  empty?: boolean;
  emptyLabel: string;
  emptyHelper?: string;
  children: ReactNode;
}) {
  return (
    <Card className={dashboardSecondaryCardClass}>
      <CardHeader className={dashboardCardHeaderClass}>
        <CardTitle className="text-[0.95rem] font-semibold leading-5">{title}</CardTitle>
        <CardDescription className="leading-5">{description}</CardDescription>
      </CardHeader>
      <CardContent className="p-ui-md">
        {loading ? (
          <DashboardLoadingState label="Đang tải dữ liệu biểu đồ..." />
        ) : empty ? (
          <DashboardEmptyState label={emptyLabel} helper={emptyHelper} />
        ) : (
          children
        )}
      </CardContent>
    </Card>
  );
}

function InsightListCard({
  title,
  description,
  items,
  loading,
  icon: Icon,
}: {
  title: string;
  description: string;
  items: DashboardBreakdownItem[];
  loading?: boolean;
  icon: ElementType;
}) {
  const maxValue = Math.max(...items.map((item) => item.value), 1);

  return (
    <Card className={dashboardSecondaryCardClass}>
      <CardHeader className={dashboardCardHeaderClass}>
        <div className="flex items-start justify-between gap-ui-md">
          <div>
            <CardTitle className="text-[0.95rem] font-semibold leading-5">{title}</CardTitle>
            <CardDescription className="leading-5">{description}</CardDescription>
          </div>
          <SectionIcon icon={Icon} />
        </div>
      </CardHeader>
      <CardContent className="p-ui-md">
        {loading ? (
          <LoadingLine label="Đang tải dữ liệu..." />
        ) : items.length === 0 ? (
          <EmptyLine label="Chưa có dữ liệu trong giai đoạn này." />
        ) : (
          <div className="space-y-ui-sm">
            {items.map((item, index) => (
              <div key={`${item.id || item.name}-${index}`} className="space-y-1.5">
                <div className="flex items-center justify-between gap-ui-sm">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium text-foreground">{item.name}</p>
                    <p className="text-xs text-muted-foreground">#{index + 1}{item.code ? ` · ${item.code}` : ''}</p>
                  </div>
                  <span className="text-sm font-semibold tabular-nums text-foreground">{item.value.toLocaleString('vi-VN')}</span>
                </div>
                <div className="h-1.5 overflow-hidden rounded-full bg-muted">
                  <div className="h-full rounded-full bg-primary" style={{ width: `${Math.max((item.value / maxValue) * 100, 6)}%` }} />
                </div>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function DoctorKpiCard({ items, loading }: { items: DashboardDoctorKpi[]; loading?: boolean }) {
  return (
    <Card className={dashboardSecondaryCardClass}>
      <CardHeader className={dashboardCardHeaderClass}>
        <CardTitle className="text-[0.95rem] font-semibold leading-5">Hiệu suất bác sĩ</CardTitle>
        <CardDescription className="leading-5">Tỷ lệ lấp đầy lịch và tỷ lệ không đến theo bác sĩ.</CardDescription>
      </CardHeader>
      <CardContent className="p-ui-md">
        {loading ? (
          <LoadingLine label="Đang tải KPI..." />
        ) : items.length === 0 ? (
          <EmptyLine label="Chưa có dữ liệu KPI bác sĩ." />
        ) : (
          <div className={cn(dashboardPanelClass, 'divide-y divide-border/70')}>
            {items.slice(0, 5).map((item) => (
              <div key={`${item.doctorId || item.doctorName}`} className="px-ui-md py-ui-sm">
                <div className="flex items-start justify-between gap-ui-sm">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium text-foreground">{item.doctorName}</p>
                    <p className="text-xs text-muted-foreground">{item.totalAppointments.toLocaleString('vi-VN')} lịch hẹn</p>
                  </div>
                  <Badge variant="secondary">Fill {item.fillRate.toFixed(0)}%</Badge>
                </div>
                <div className="mt-ui-xs grid grid-cols-2 gap-ui-xs text-xs">
                  <MiniMetric label="Check-in" value={item.checkedInAppointments.toLocaleString('vi-VN')} />
                  <MiniMetric label="Không đến" value={`${item.noShowAppointments.toLocaleString('vi-VN')} · ${item.noShowRate.toFixed(0)}%`} danger={item.noShowRate >= 20} />
                </div>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function SpecialtyKpiCard({ items, loading }: { items: DashboardSpecialtyKpi[]; loading?: boolean }) {
  return (
    <Card className={dashboardSecondaryCardClass}>
      <CardHeader className={dashboardCardHeaderClass}>
        <CardTitle className="text-[0.95rem] font-semibold leading-5">Rủi ro theo chuyên khoa</CardTitle>
        <CardDescription className="leading-5">Chuyên khoa có tỷ lệ không đến cao để ưu tiên nhắc hẹn.</CardDescription>
      </CardHeader>
      <CardContent className="p-ui-md">
        {loading ? (
          <LoadingLine label="Đang tải KPI..." />
        ) : items.length === 0 ? (
          <EmptyLine label="Chưa có dữ liệu KPI chuyên khoa." />
        ) : (
          <div className="space-y-ui-sm">
            {items.slice(0, 5).map((item) => (
              <div key={`${item.specialtyId || item.specialtyName}`} className="space-y-1.5">
                <div className="flex items-center justify-between gap-ui-sm">
                  <p className="truncate text-sm font-medium text-foreground">{item.specialtyName}</p>
                  <Badge variant="outline">{item.totalAppointments.toLocaleString('vi-VN')} lịch</Badge>
                </div>
                <div className="h-1.5 overflow-hidden rounded-full bg-muted">
                  <div
                    className={cn(
                      'h-full rounded-full',
                      item.noShowRate >= 20 ? 'bg-destructive' : item.noShowRate >= 10 ? 'bg-warning' : 'bg-success',
                    )}
                    style={{ width: `${Math.min(Math.max(item.noShowRate, 4), 100)}%` }}
                  />
                </div>
                <div className="flex items-center justify-between gap-ui-sm text-xs">
                  <span className="text-muted-foreground">Không đến</span>
                  <span className="font-semibold tabular-nums text-foreground">
                    {item.noShowAppointments.toLocaleString('vi-VN')} · {item.noShowRate.toFixed(0)}%
                  </span>
                </div>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function BranchRevenueCard({ items, loading }: { items: DashboardBranchRevenue[]; loading?: boolean }) {
  const getNetRevenue = (item: DashboardBranchRevenue) => item.netRevenue ?? item.paidRevenue;
  const maxValue = Math.max(...items.map(getNetRevenue), 1);

  return (
    <Card className={dashboardSecondaryCardClass}>
      <CardHeader className={dashboardCardHeaderClass}>
        <CardTitle className="text-[0.95rem] font-semibold leading-5">Doanh thu ròng theo chi nhánh</CardTitle>
        <CardDescription className="leading-5">Xem doanh thu sau hoàn tiền để tối ưu quầy thu ngân.</CardDescription>
      </CardHeader>
      <CardContent className="p-ui-md">
        {loading ? (
          <LoadingLine label="Đang tải doanh thu..." />
        ) : items.length === 0 ? (
          <EmptyLine label="Chưa có dữ liệu doanh thu chi nhánh." />
        ) : (
          <div className="space-y-ui-sm">
            {items.slice(0, 6).map((item) => {
              const net = getNetRevenue(item);
              const gross = item.grossRevenue ?? net;
              const refunded = item.refundedAmount ?? Math.max(0, gross - net);

              return (
                <div key={`${item.branchId || item.branchName}`} className="space-y-1.5">
                  <div className="flex items-center justify-between gap-ui-sm">
                    <p className="truncate text-sm font-medium text-foreground">{item.branchName}</p>
                    <span className="text-sm font-semibold tabular-nums text-foreground">{formatCompactCurrency(net)}</span>
                  </div>
                  <div className="h-1.5 overflow-hidden rounded-full bg-muted">
                    <div className="h-full rounded-full bg-accent" style={{ width: `${Math.max((net / maxValue) * 100, 8)}%` }} />
                  </div>
                  <p className="text-xs text-muted-foreground">
                    Tổng đã thu {formatCurrency(gross)} · Đã hoàn {formatCurrency(refunded)}
                  </p>
                </div>
              );
            })}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function MiniMetric({
  label,
  value,
  danger,
}: {
  label: string;
  value: string;
  danger?: boolean;
}) {
  return (
    <div className="min-w-0 rounded-md bg-muted/25 px-ui-xs py-1.5">
      <p className="truncate text-xs font-medium text-muted-foreground">{label}</p>
      <p className={cn('mt-0.5 truncate font-semibold tabular-nums text-foreground', danger && 'text-destructive')}>
        {value}
      </p>
    </div>
  );
}
