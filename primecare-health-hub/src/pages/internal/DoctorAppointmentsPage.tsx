import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { addDays, format } from 'date-fns';
import { vi } from 'date-fns/locale';
import {
  AlertTriangle,
  ChevronLeft,
  ChevronRight,
  Play,
  RefreshCw,
} from 'lucide-react';
import { PageHeader } from '@/components/PageHeader';
import { StatusBadge } from '@/components/StatusBadge';
import { EmptyState } from '@/components/EmptyState';
import { ErrorState } from '@/components/ErrorState';
import { LoadingSkeleton } from '@/components/LoadingSkeleton';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import type { Appointment, EncounterStatus } from '@/types/api';
import {
  useCreateEncounterFromAppointment,
  useDoctorAppointmentSummary,
  useDoctorAppointments,
  useDoctorWaitingAppointments,
} from '@/hooks/use-doctor-data';
import { formatTriagePriority, getPreTriageLevelClass, getPreTriageLevelDisplay, getPriorityClass } from '@/lib/triage';

type ClinicalFilterStatus =
  | '__all__'
  | 'WAITING_EXAM'
  | 'IN_CARE'
  | 'WAITING_EXTERNAL'
  | 'NEEDS_RETURN'
  | 'DONE';

type WorklistBucket = Exclude<ClinicalFilterStatus, '__all__'> | 'OTHER';

const FILTERS: Array<{ key: ClinicalFilterStatus; label: string }> = [
  { key: '__all__', label: 'Tất cả' },
  { key: 'WAITING_EXAM', label: 'Chờ khám' },
  { key: 'IN_CARE', label: 'Đang khám' },
  { key: 'WAITING_EXTERNAL', label: 'Chờ bên khác' },
  { key: 'NEEDS_RETURN', label: 'Cần quay lại' },
  { key: 'DONE', label: 'Đã xong' },
];

const ENCOUNTER_ACTION_LABEL: Partial<Record<EncounterStatus, string>> = {
  IN_PROGRESS: 'Tiếp tục khám',
  REOPENED: 'Tiếp tục khám',
  WAITING_PAYMENT: 'Mở hồ sơ',
  WAITING_RESULTS: 'Mở hồ sơ',
  READY_FOR_CONCLUSION: 'Kết luận',
  COMPLETED: 'Xem hồ sơ',
  CANCELLED: 'Xem hồ sơ',
};

function getSessionLabel(session?: string, t?: (key: string) => string) {
  if (session === 'MORNING') return t?.('modules.doctorSchedules.AM') ?? 'Buổi sáng';
  if (session === 'AFTERNOON') return t?.('modules.doctorSchedules.PM') ?? 'Buổi chiều';
  return session ?? '-';
}

function getSecondaryMeta(apt: Appointment) {
  if (apt.checkedInAt) return `Đã check-in ${apt.checkedInAt}`;
  if (typeof apt.queueNo === 'number') return `Số thứ tự ${apt.queueNo}`;
  return apt.code ? `Mã lịch ${apt.code}` : '';
}

function getWorklistBucket(apt: Appointment): WorklistBucket {
  if (apt.status === 'CHECKED_IN' && !apt.activeEncounterId) return 'WAITING_EXAM';

  switch (apt.activeEncounterStatus) {
    case 'IN_PROGRESS':
    case 'REOPENED':
      return 'IN_CARE';
    case 'WAITING_PAYMENT':
    case 'WAITING_RESULTS':
      return 'WAITING_EXTERNAL';
    case 'READY_FOR_CONCLUSION':
      return 'NEEDS_RETURN';
    case 'COMPLETED':
      return 'DONE';
    default:
      return 'OTHER';
  }
}

function getEncounterActionLabel(apt: Appointment) {
  if (!apt.activeEncounterId) return 'Bắt đầu khám';
  return ENCOUNTER_ACTION_LABEL[apt.activeEncounterStatus ?? 'IN_PROGRESS'] ?? 'Mở hồ sơ';
}

function buildClinicalContext(apt: Appointment) {
  const items: string[] = [];

  if (apt.activeEncounterCode) items.push(`Hồ sơ: ${apt.activeEncounterCode}`);
  if ((apt.serviceOrderCount ?? 0) > 0) items.push(`${apt.serviceOrderCount} chỉ định`);
  if (apt.hasPendingPayment || (apt.pendingPaymentOrderCount ?? 0) > 0) {
    items.push(
      (apt.pendingPaymentOrderCount ?? 0) > 0
        ? `${apt.pendingPaymentOrderCount} chỉ định chờ thanh toán`
        : 'Chờ thanh toán',
    );
  }
  if (apt.hasWaitingResults || (apt.waitingResultItemCount ?? 0) > 0) {
    items.push(
      (apt.waitingResultItemCount ?? 0) > 0
        ? `${apt.waitingResultItemCount} mục chờ kết quả`
        : 'Chờ kết quả',
    );
  }
  if ((apt.completedResultItemCount ?? 0) > 0) {
    items.push(`${apt.completedResultItemCount} kết quả hoàn tất`);
  }
  if ((apt.issuedPrescriptionCount ?? 0) > 0) {
    items.push(`${apt.issuedPrescriptionCount} đơn thuốc`);
  }

  return items;
}

export default function DoctorAppointmentsPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [selectedDate, setSelectedDate] = useState(() => format(new Date(), 'yyyy-MM-dd'));
  const [activeStatus, setActiveStatus] = useState<ClinicalFilterStatus>('__all__');

  const queryParams = useMemo(
    () => ({
      from: selectedDate,
      to: selectedDate,
      page: '0',
      size: '50',
    }),
    [selectedDate],
  );
  const waitingParams = useMemo(
    () => ({
      visitDate: selectedDate,
      page: '0',
      size: '50',
    }),
    [selectedDate],
  );

  const { data, isLoading, isError, isFetching, refetch } = useDoctorAppointments(queryParams);
  const waitingQuery = useDoctorWaitingAppointments(waitingParams, activeStatus === 'WAITING_EXAM');
  const summaryQuery = useDoctorAppointmentSummary(queryParams);
  const startEncounter = useCreateEncounterFromAppointment();

  const appointments = useMemo(() => data?.items ?? [], [data?.items]);
  const totalAppointments = data?.meta.totalItems ?? appointments.length;
  const hasMoreAppointments = totalAppointments > appointments.length;

  const fallbackStats = useMemo(() => {
    const byBucket = appointments.reduce(
      (acc, apt) => {
        const bucket = getWorklistBucket(apt);
        if (bucket !== 'OTHER') acc[bucket] += 1;
        return acc;
      },
      {
        WAITING_EXAM: 0,
        IN_CARE: 0,
        WAITING_EXTERNAL: 0,
        NEEDS_RETURN: 0,
        DONE: 0,
      } as Record<Exclude<WorklistBucket, 'OTHER'>, number>,
    );

    return {
      total: totalAppointments,
      ...byBucket,
    };
  }, [appointments, totalAppointments]);
  const stats = summaryQuery.data
    ? {
        total: summaryQuery.data.total,
        WAITING_EXAM: summaryQuery.data.waitingExam,
        IN_CARE: summaryQuery.data.inCare,
        WAITING_EXTERNAL: summaryQuery.data.waitingExternal,
        NEEDS_RETURN: summaryQuery.data.needsReturn,
        DONE: summaryQuery.data.done,
      }
    : fallbackStats;

  const filteredAppointments = useMemo(() => {
    if (activeStatus === 'WAITING_EXAM' && waitingQuery.data) {
      return waitingQuery.data.items;
    }
    if (activeStatus === '__all__') return appointments;
    return appointments.filter((apt) => getWorklistBucket(apt) === activeStatus);
  }, [activeStatus, appointments, waitingQuery.data]);
  const listIsLoading =
    activeStatus === 'WAITING_EXAM' ? waitingQuery.isLoading && !waitingQuery.data : isLoading;
  const listIsError = activeStatus === 'WAITING_EXAM' ? waitingQuery.isError : isError;
  const listRefetch = activeStatus === 'WAITING_EXAM' ? waitingQuery.refetch : refetch;

  const handlePrimaryAction = async (apt: Appointment) => {
    if (apt.activeEncounterId) {
      navigate(`/app/doctor/encounters/${apt.activeEncounterId}`);
      return;
    }

    if (apt.status !== 'CHECKED_IN') return;

    try {
      const encounter = await startEncounter.mutateAsync(apt.id);
      navigate(`/app/doctor/encounters/${encounter.id}`);
    } catch {
      // mutation hook already shows the API error
    }
  };

  return (
    <div className="space-y-5">
      <PageHeader
        title={t('modules.doctorAppointments.title')}
        description={t('modules.doctorAppointments.desc')}
      />

      <div className="flex flex-col gap-3 rounded-xl border bg-card p-4 md:flex-row md:items-center md:justify-between">
        <div>
          <p className="text-sm font-medium">Lịch khám theo ngày</p>
          <p className="text-sm text-muted-foreground">
            {format(new Date(selectedDate), 'EEEE, dd/MM/yyyy', { locale: vi })}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Button
            variant="outline"
            size="icon"
            onClick={() => setSelectedDate(format(addDays(new Date(selectedDate), -1), 'yyyy-MM-dd'))}
          >
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <input
            type="date"
            value={selectedDate}
            onChange={(event) => setSelectedDate(event.target.value)}
            className="h-9 rounded-md border bg-background px-3 text-sm"
          />
          <Button
            variant="outline"
            size="icon"
            onClick={() => setSelectedDate(format(addDays(new Date(selectedDate), 1), 'yyyy-MM-dd'))}
          >
            <ChevronRight className="h-4 w-4" />
          </Button>
          <Button variant="outline" onClick={() => setSelectedDate(format(new Date(), 'yyyy-MM-dd'))}>
            Hôm nay
          </Button>
          <Button
            variant="outline"
            onClick={() => {
              void refetch();
              if (activeStatus === 'WAITING_EXAM') void waitingQuery.refetch();
              void summaryQuery.refetch();
            }}
            disabled={isFetching || (activeStatus === 'WAITING_EXAM' && waitingQuery.isFetching) || summaryQuery.isFetching}
          >
            <RefreshCw
              className={cn(
                'mr-2 h-4 w-4',
                (isFetching ||
                  (activeStatus === 'WAITING_EXAM' && waitingQuery.isFetching) ||
                  summaryQuery.isFetching) &&
                  'animate-spin',
              )}
            />
            Làm mới
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-2 md:grid-cols-3 xl:grid-cols-6">
        {FILTERS.map((filter) => {
          const active = activeStatus === filter.key;
          const value = filter.key === '__all__' ? stats.total : stats[filter.key];

          return (
            <button
              key={filter.key}
              type="button"
              onClick={() => setActiveStatus(filter.key)}
              className={cn(
                'rounded-lg border bg-card px-4 py-3 text-left transition-colors hover:border-primary/40 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                active && 'border-primary bg-primary/5',
              )}
            >
              <p className="text-xs font-medium text-muted-foreground">{filter.label}</p>
              <p className="mt-1 text-2xl font-semibold text-foreground">{value}</p>
            </button>
          );
        })}
      </div>

      {hasMoreAppointments ? (
        <div className="flex items-start gap-3 rounded-xl border border-warning/20 bg-warning/10 px-4 py-3 text-sm text-warning">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
          <p>
            Đang hiển thị 50 lượt đầu tiên trong tổng {totalAppointments} lượt của ngày này.
            Dùng bộ lọc trạng thái hoặc làm mới sau khi backend hỗ trợ phân trang worklist bác sĩ.
          </p>
        </div>
      ) : null}

      <div className="grid gap-3">
        {listIsError ? (
          <ErrorState
            title="Không tải được lịch khám"
            description="Vui lòng thử lại hoặc kiểm tra quyền truy cập lịch khám của bác sĩ."
            onRetry={() => void listRefetch()}
          />
        ) : listIsLoading ? (
          <LoadingSkeleton variant="list" count={6} />
        ) : filteredAppointments.length > 0 ? (
          filteredAppointments.map((apt) => {
            const hasEncounter = Boolean(apt.activeEncounterId);
            const canStart = apt.status === 'CHECKED_IN' && !hasEncounter;
            const contextItems = buildClinicalContext(apt);

            return (
              <div
                key={apt.id}
                className="flex flex-col gap-4 rounded-xl border bg-card p-4 transition-shadow hover:shadow-md md:flex-row md:items-center md:justify-between"
              >
                <div className="flex items-start gap-4">
                  <div className="min-w-[72px] text-center">
                    <p className="text-xl font-bold text-primary tabular-nums">{apt.slotStart}</p>
                    <p className="text-xs text-muted-foreground">{getSessionLabel(apt.session, t)}</p>
                  </div>
                  <div className="space-y-2">
                    <div>
                      <p className="font-semibold text-foreground">{apt.patientFullName}</p>
                      <p className="text-sm text-muted-foreground">
                        {apt.specialtyName} · {apt.branchName}
                      </p>
                    </div>
                    {!!getSecondaryMeta(apt) && (
                      <p className="text-xs text-muted-foreground">{getSecondaryMeta(apt)}</p>
                    )}
                    {contextItems.length > 0 && (
                      <div className="flex flex-wrap gap-2">
                        {contextItems.map((item) => (
                          <span
                            key={item}
                            className="rounded-full bg-muted px-2.5 py-1 text-xs text-muted-foreground"
                          >
                            {item}
                          </span>
                        ))}
                      </div>
                    )}
                    {apt.patientNote && (
                      <p className="text-xs italic text-muted-foreground">"{apt.patientNote}"</p>
                    )}
                    {activeStatus === 'WAITING_EXAM' && apt.triageNote ? (
                      <p className="text-xs text-muted-foreground">Ưu tiên: {apt.triageNote}</p>
                    ) : activeStatus === 'WAITING_EXAM' && apt.preTriageSummary ? (
                      <p className="text-xs text-muted-foreground">Sàng lọc sơ bộ: {apt.preTriageSummary}</p>
                    ) : null}
                  </div>
                </div>
                <div className="flex flex-wrap items-center gap-3 md:justify-end">
                  {apt.triagePriority ? (
                    <span
                      className={cn(
                        'inline-flex items-center whitespace-nowrap rounded-full border px-2.5 py-1 text-xs font-medium leading-none',
                        getPriorityClass(apt.triagePriority),
                      )}
                    >
                      {formatTriagePriority(apt.triagePriority)}
                    </span>
                  ) : null}
                  {activeStatus === 'WAITING_EXAM' && apt.preTriageLevel === 'RED_FLAG' ? (
                    <span
                      className={cn(
                        'inline-flex items-center whitespace-nowrap rounded-full border px-2.5 py-1 text-xs font-medium leading-none',
                        getPreTriageLevelClass(apt.preTriageLevel),
                      )}
                    >
                      {getPreTriageLevelDisplay(apt.preTriageLevel)}
                    </span>
                  ) : null}
                  <StatusBadge status={apt.status} />
                  {apt.activeEncounterStatus && <StatusBadge status={apt.activeEncounterStatus} />}
                  {(canStart || hasEncounter) && (
                    <Button
                      onClick={() => void handlePrimaryAction(apt)}
                      disabled={startEncounter.isPending && canStart}
                    >
                      {canStart && <Play className="mr-2 h-4 w-4" />}
                      {getEncounterActionLabel(apt)}
                    </Button>
                  )}
                </div>
              </div>
            );
          })
        ) : (
          <EmptyState
            title="Không có lịch khám phù hợp"
            description="Không có lượt khám nào trong ngày này với bộ lọc hiện tại."
            className="min-h-48"
          />
        )}
      </div>
    </div>
  );
}
