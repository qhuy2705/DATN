import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import type { LucideIcon } from 'lucide-react';
import {
  Activity,
  AlertTriangle,
  CheckCircle2,
  Clock3,
  DoorOpen,
  Phone,
  ShieldAlert,
  UserCheck,
  Users,
} from 'lucide-react';
import { PageHeader } from '@/components/PageHeader';
import { StatusBadge } from '@/components/StatusBadge';
import { Button } from '@/components/ui/button';
import { FilterBar } from '@/components/FilterBar';
import { Input } from '@/components/ui/input';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { LivePulseBadge } from '@/components/LivePulseBadge';
import {
  useMarkArrived,
  useManualCheckIn,
  useReceptionQueue,
  useReceptionQueueSummary,
} from '@/hooks/use-reception-data';
import { useBranchSpecialties, useBranches, useDoctors, useSpecialties } from '@/hooks/use-public-data';
import {
  buildArrivalStatusOptions,
  buildSourceTypeOptions,
  getArrivalStatusLabel,
  getSourceTypeLabel,
} from '@/lib/filter-options';
import { toLocalDateInputValue } from '@/lib/date';
import { useDebouncedValue } from '@/hooks/use-debounced-value';
import type { Appointment, ReceptionQueueSummary } from '@/types/api';
import { isAppointmentOverdue } from '@/lib/appointment-utils';
import { OverdueBadge } from '@/components/OverdueBadge';
import { EmptyState } from '@/components/EmptyState';
import { ErrorState } from '@/components/ErrorState';
import { LoadingSkeleton } from '@/components/LoadingSkeleton';

type WorkflowFilterKey = '__all__' | 'overdue';

const QUEUE_LIST_STATUSES = new Set(['REQUESTED', 'CONFIRMED', 'CHECKED_IN']);

function renderPriority(value?: string) {
  switch (value) {
    case 'ROUTINE':
      return 'Thông thường';
    case 'PRIORITY':
      return 'Ưu tiên';
    case 'URGENT':
      return 'Khẩn';
    default:
      return value || '';
  }
}

function summaryValue(
  summary: ReceptionQueueSummary | undefined,
  key: keyof ReceptionQueueSummary,
  isLoading: boolean,
) {
  if (summary && summary[key] != null) return summary[key] as number;
  return isLoading ? '...' : 0;
}

function QueueMetricCard({
  icon: Icon,
  title,
  value,
  to,
}: {
  icon: LucideIcon;
  title: string;
  value: number | string;
  to?: string;
}) {
  const content = (
    <div className="flex items-center justify-between gap-3">
      <div>
        <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{title}</p>
        <p className="mt-1 text-2xl font-semibold tracking-tight text-foreground">{value}</p>
      </div>
      <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-primary/10 bg-primary/10 text-primary">
        <Icon className="h-4 w-4" />
      </span>
    </div>
  );
  const className =
    'block rounded-lg border border-border bg-card px-4 py-3 shadow-sm transition-colors';

  if (to) {
    return (
      <Link to={to} className={`${className} hover:border-primary/40 hover:bg-primary/5`}>
        {content}
      </Link>
    );
  }

  return <div className={className}>{content}</div>;
}

function QueueRow({
  appointment,
  isActionPending,
  onMarkArrived,
  onManualCheckIn,
  t,
  now,
}: {
  appointment: Appointment;
  isActionPending: boolean;
  onMarkArrived: (id: string) => void;
  onManualCheckIn: (id: string) => void;
  t: ReturnType<typeof useTranslation>['t'];
  now: Date;
}) {
  const canMarkArrived =
    appointment.status === 'CONFIRMED' && appointment.arrivalStatus !== 'ARRIVED';
  const canCheckIn = appointment.status === 'CONFIRMED';
  const overdue = isAppointmentOverdue(appointment, now);

  return (
    <div className="grid gap-4 rounded-lg border border-border/70 bg-card p-4 lg:grid-cols-[minmax(0,1fr)_auto] lg:items-center">
      <div className="flex min-w-0 items-start gap-4">
        <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-lg font-bold text-primary">
          {appointment.receptionQueueNo ?? appointment.queueNo ?? '-'}
        </div>
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <p className="font-semibold text-foreground">{appointment.patientFullName}</p>
            <StatusBadge status={appointment.status} />
            {overdue ? <OverdueBadge /> : null}
          </div>
          <p className="mt-1 flex items-center gap-2 text-sm text-muted-foreground">
            <Phone className="h-3.5 w-3.5" />
            {appointment.patientPhone}
          </p>
          <p className="mt-1 text-xs text-muted-foreground">
            {appointment.code} · {appointment.doctorName} · {appointment.specialtyName} · {appointment.slotStart}
          </p>
          <div className="mt-2 flex flex-wrap gap-2 text-xs">
            <span className="rounded-full bg-muted px-2.5 py-1 text-muted-foreground">
              {appointment.branchName}
            </span>
            <span className="rounded-full bg-muted px-2.5 py-1 text-muted-foreground">
              {getSourceTypeLabel(appointment.sourceType, t)}
            </span>
            <span className="rounded-full bg-muted px-2.5 py-1 text-muted-foreground">
              {getArrivalStatusLabel(appointment.arrivalStatus, t)}
            </span>
            {appointment.triagePriority ? (
              <span className="rounded-full border border-amber-300 bg-amber-50 px-2.5 py-1 text-amber-700">
                Ưu tiên tiếp nhận: {renderPriority(appointment.triagePriority)}
              </span>
            ) : null}
          </div>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-2 lg:justify-end">
        {canMarkArrived ? (
          <Button
            size="sm"
            variant="outline"
            onClick={() => onMarkArrived(appointment.id)}
            disabled={isActionPending}
          >
            Đánh dấu đã đến
          </Button>
        ) : null}
        {canCheckIn ? (
          <Button size="sm" onClick={() => onManualCheckIn(appointment.id)} disabled={isActionPending}>
            <UserCheck className="mr-1 h-4 w-4" />
            Check-in
          </Button>
        ) : null}
        <Button asChild size="sm" variant="ghost">
          <Link to={`/app/appointments/${appointment.id}/process`}>Xử lý lịch hẹn</Link>
        </Button>
      </div>
    </div>
  );
}

export default function ReceptionQueuePage() {
  const { t } = useTranslation();
  const [page, setPage] = useState(1);
  const [search, setSearch] = useState('');
  const [branchFilter, setBranchFilter] = useState('__all__');
  const [doctorFilter, setDoctorFilter] = useState('__all__');
  const [specialtyFilter, setSpecialtyFilter] = useState('__all__');
  const [arrivalFilter, setArrivalFilter] = useState('__all__');
  const [sourceFilter, setSourceFilter] = useState('__all__');
  const [workflowFilter, setWorkflowFilter] = useState<WorkflowFilterKey>('__all__');
  const debouncedSearch = useDebouncedValue(search.trim(), 400);
  const [now, setNow] = useState(() => new Date());

  const today = toLocalDateInputValue();

  const { data: branches = [] } = useBranches();
  const { data: allSpecialties = [] } = useSpecialties();
  const { data: branchSpecialties = [] } = useBranchSpecialties(
    branchFilter !== '__all__' ? branchFilter : '',
  );
  const specialties = branchFilter === '__all__' ? allSpecialties : branchSpecialties;

  const doctorParams = useMemo(
    () => ({
      size: '100',
      ...(branchFilter !== '__all__' ? { branchId: branchFilter } : {}),
      ...(specialtyFilter !== '__all__' ? { specialtyId: specialtyFilter } : {}),
    }),
    [branchFilter, specialtyFilter],
  );
  const { data: doctorsPage } = useDoctors(doctorParams);
  const doctors = useMemo(() => doctorsPage?.items ?? [], [doctorsPage?.items]);

  const arrivalOptions = useMemo(() => buildArrivalStatusOptions(t), [t]);
  const sourceOptions = useMemo(() => buildSourceTypeOptions(t), [t]);

  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 60_000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    setDoctorFilter('__all__');
    setPage(1);
  }, [branchFilter, specialtyFilter]);

  useEffect(() => {
    if (specialtyFilter === '__all__') return;
    if (specialties.some((item) => item.id === specialtyFilter)) return;
    setSpecialtyFilter('__all__');
  }, [specialties, specialtyFilter]);

  useEffect(() => {
    setPage(1);
  }, [doctorFilter, arrivalFilter, sourceFilter, workflowFilter, debouncedSearch]);

  const summaryParams = useMemo(
    () => ({
      visitDate: today,
      ...(debouncedSearch ? { q: debouncedSearch } : {}),
      ...(branchFilter !== '__all__' ? { branchId: branchFilter } : {}),
      ...(doctorFilter !== '__all__' ? { doctorId: doctorFilter } : {}),
      ...(specialtyFilter !== '__all__' ? { specialtyId: specialtyFilter } : {}),
      ...(arrivalFilter !== '__all__' ? { arrivalStatus: arrivalFilter } : {}),
      ...(sourceFilter !== '__all__' ? { sourceType: sourceFilter } : {}),
      ...(workflowFilter === 'overdue' ? { overdue: 'true' } : {}),
    }),
    [
      arrivalFilter,
      branchFilter,
      debouncedSearch,
      doctorFilter,
      sourceFilter,
      specialtyFilter,
      today,
      workflowFilter,
    ],
  );

  const queueParams = useMemo(
    () => ({
      page: String(page - 1),
      size: '50',
      ...summaryParams,
    }),
    [page, summaryParams],
  );

  const {
    data,
    isLoading,
    isError,
    refetch,
    dataUpdatedAt,
  } = useReceptionQueue(queueParams);
  const { data: summary, isLoading: isSummaryLoading } = useReceptionQueueSummary(summaryParams);

  const markArrived = useMarkArrived();
  const manualCheckIn = useManualCheckIn();

  const queue = useMemo(
    () =>
      (data?.items ?? []).filter((appointment) =>
        QUEUE_LIST_STATUSES.has(String(appointment.status)),
      ),
    [data?.items],
  );
  const metrics = useMemo(() => {
    const base: Array<{
      title: string;
      value: number | string;
      icon: LucideIcon;
      to?: string;
    }> = [
      {
        title: 'Tổng hàng chờ',
        value: summaryValue(summary, 'total', isSummaryLoading),
        icon: Users,
      },
      {
        title: 'Chờ xác nhận',
        value: summaryValue(summary, 'requested', isSummaryLoading),
        icon: Clock3,
      },
      {
        title: 'Đã xác nhận',
        value: summaryValue(summary, 'confirmed', isSummaryLoading),
        icon: CheckCircle2,
      },
      {
        title: 'Đã đến',
        value: summaryValue(summary, 'arrived', isSummaryLoading),
        icon: DoorOpen,
      },
      {
        title: 'Đã check-in',
        value: summaryValue(summary, 'checkedIn', isSummaryLoading),
        icon: Activity,
      },
      {
        title: 'Chưa đến',
        value: summaryValue(summary, 'notArrived', isSummaryLoading),
        icon: UserCheck,
      },
      {
        title: 'Walk-in',
        value: summaryValue(summary, 'walkIn', isSummaryLoading),
        icon: Users,
      },
    ];

    if (summary?.priority != null) {
      base.push({
        title: 'Ưu tiên',
        value: summary.priority,
        icon: ShieldAlert,
      });
    }

    if (summary?.overdueCount != null) {
      base.push({
        title: 'Quá giờ',
        value: summary.overdueCount,
        icon: Clock3,
      });
    }

    if (summary?.noShowFollowUpPending != null) {
      base.push({
        title: 'No-show cần xử lý',
        value: summary.noShowFollowUpPending,
        icon: AlertTriangle,
        to: '/app/appointments?status=NO_SHOW&followUpPending=true',
      });
    }

    return base;
  }, [isSummaryLoading, summary]);

  const updatedAtLabel = dataUpdatedAt ? new Date(dataUpdatedAt).toLocaleTimeString('vi-VN') : '—';
  const isActionPending = markArrived.isPending || manualCheckIn.isPending;

  return (
    <div className="space-y-5">
      <PageHeader
        title={t('modules.receptionQueue.title')}
        description="Danh sách lịch hẹn tại quầy, lọc nhanh và thao tác tiếp nhận."
        actions={<LivePulseBadge label={`Realtime · cập nhật ${updatedAtLabel}`} />}
      />

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {metrics.map((metric) => (
          <QueueMetricCard
            key={metric.title}
            title={metric.title}
            value={metric.value}
            icon={metric.icon}
            to={metric.to}
          />
        ))}
      </div>

      <div className="space-y-3 rounded-lg border border-border bg-card p-4 shadow-sm">
        <FilterBar
          filters={[
            {
              key: 'branch',
              label: t('common.branch'),
              options: branches.map((b) => ({ label: b.name, value: b.id })),
              value: branchFilter,
              onChange: setBranchFilter,
            },
            {
              key: 'specialty',
              label: t('common.specialty'),
              options: specialties.map((s) => ({ label: s.name, value: s.id })),
              value: specialtyFilter,
              onChange: setSpecialtyFilter,
            },
            {
              key: 'doctor',
              label: t('common.doctor'),
              options: doctors.map((d) => ({ label: d.fullName, value: d.id })),
              value: doctorFilter,
              onChange: setDoctorFilter,
            },
            {
              key: 'arrival',
              label: t('filters.arrivalStatus.label'),
              options: arrivalOptions,
              value: arrivalFilter,
              onChange: setArrivalFilter,
            },
            {
              key: 'source',
              label: t('filters.sourceType.label'),
              options: sourceOptions,
              value: sourceFilter,
              onChange: setSourceFilter,
            },
            {
              key: 'workflow',
              label: 'Cần xử lý',
              options: [{ value: 'overdue', label: 'Quá giờ' }],
              value: workflowFilter,
              onChange: (value) => setWorkflowFilter(value as WorkflowFilterKey),
            },
          ]}
        />

        <Input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Tìm theo mã lịch hẹn, tên bệnh nhân, số điện thoại, bác sĩ..."
          className="max-w-md"
        />
      </div>

      <Card className="border-border/70 shadow-sm">
        <CardHeader className="pb-3">
          <CardTitle className="text-base">Danh sách tiếp nhận</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          {isError ? (
            <ErrorState
              title="Không tải được hàng đợi tiếp nhận"
              description="Vui lòng thử lại hoặc kiểm tra quyền truy cập quầy tiếp nhận."
              onRetry={() => void refetch()}
              className="min-h-48"
            />
          ) : isLoading ? (
            <LoadingSkeleton variant="list" count={5} />
          ) : queue.length > 0 ? (
            queue.map((appointment) => (
              <QueueRow
                key={appointment.id}
                appointment={appointment}
                isActionPending={isActionPending}
                onMarkArrived={(appointmentId) => markArrived.mutate(appointmentId)}
                onManualCheckIn={(appointmentId) => manualCheckIn.mutate(appointmentId)}
                t={t}
                now={now}
              />
            ))
          ) : (
            <EmptyState
              title={t('common.noData')}
              description="Không có bệnh nhân nào trong hàng đợi tiếp nhận với bộ lọc hiện tại."
              className="min-h-48 border-0 bg-transparent"
            />
          )}
        </CardContent>
      </Card>

      {(data?.meta.totalPages ?? 1) > 1 && (
        <div className="mt-4 flex items-center justify-between">
          <p className="text-sm text-muted-foreground">
            {t('common.page')} {page} {t('common.of')} {data?.meta.totalPages ?? 1}
          </p>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={page <= 1}
              onClick={() => setPage((p) => p - 1)}
            >
              Trước
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={page >= (data?.meta.totalPages ?? 1)}
              onClick={() => setPage((p) => p + 1)}
            >
              Sau
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
