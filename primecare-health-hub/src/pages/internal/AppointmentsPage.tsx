import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Eye } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { DataTable, type Column } from '@/components/DataTable';
import { FilterBar } from '@/components/FilterBar';
import { PageHeader } from '@/components/PageHeader';
import { StatusBadge } from '@/components/StatusBadge';
import { Button } from '@/components/ui/button';
import { AppointmentSummaryCards } from '@/components/AppointmentSummaryCards';
import {
  useAdminAppointments,
  useAdminDoctorOptions,
  useAppointmentSummary,
  useAppointmentSummaryRealtime,
} from '@/hooks/use-admin-data';
import { useBranches } from '@/hooks/use-public-data';
import { useDebouncedValue } from '@/hooks/use-debounced-value';
import type { Appointment, AppointmentListQueryParams } from '@/types/api';
import { buildAppointmentStatusOptions } from '@/lib/filter-options';
import { getInternalDoctorOptionNote } from '@/lib/doctor-readiness';
import {
  getCheckedInLateLabel,
  isAppointmentOverdue,
  isLateForCheckIn,
} from '@/lib/appointment-utils';
import {
  CheckedInLateBadge,
  LateCheckInBadge,
  NoShowEligibleBadge,
} from '@/components/OverdueBadge';

type SummaryStatusKey =
  | '__all__'
  | 'REQUESTED'
  | 'CONFIRMED'
  | 'CHECKED_IN'
  | 'COMPLETED'
  | 'NO_SHOW'
  | 'CANCELLED';

function parseStatusFilter(value: string | null): SummaryStatusKey | null {
  if (!value) return null;
  const normalized = value === 'PENDING' ? 'REQUESTED' : value;
  const allowed: SummaryStatusKey[] = [
    'REQUESTED',
    'CONFIRMED',
    'CHECKED_IN',
    'COMPLETED',
    'NO_SHOW',
    'CANCELLED',
  ];
  return allowed.includes(normalized as SummaryStatusKey)
    ? (normalized as SummaryStatusKey)
    : null;
}

function readInitialAppointmentStatus(searchParams: URLSearchParams): SummaryStatusKey {
  return parseStatusFilter(searchParams.get('status')) ?? '__all__';
}

export default function AppointmentsPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const initialStatus = useMemo(
    () => readInitialAppointmentStatus(searchParams),
    [searchParams],
  );

  const isClaimExpired = (row: Appointment) => {
    if (!row.processingExpiresAt) return false;
    const expiresAt = new Date(row.processingExpiresAt);
    return !Number.isNaN(expiresAt.getTime()) && expiresAt.getTime() <= Date.now();
  };

  const hasActiveClaim = (row: Appointment) =>
    Boolean(row.processingById && !isClaimExpired(row));

  const formatClaimExpiresAt = (value?: string) => {
    if (!value) return '';
    const expiresAt = new Date(value);
    if (Number.isNaN(expiresAt.getTime())) return value;
    return expiresAt.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
  };

  const renderClaimStatus = (row: Appointment) => {
    if (row.processingById && isClaimExpired(row)) {
      return 'Claim đã hết hạn';
    }

    if (hasActiveClaim(row)) {
      const expiresLabel = formatClaimExpiresAt(row.processingExpiresAt);
      return `Đang xử lý bởi: ${row.claimedBy || 'nhân viên khác'}${expiresLabel ? ` đến ${expiresLabel}` : ''}`;
    }

    return 'Chưa nhận xử lý';
  };

  const statusOptions = useMemo(() => buildAppointmentStatusOptions(t), [t]);

  const [search, setSearch] = useState('');
  const [page, setPage] = useState(1);
  const [statusFilter, setStatusFilter] = useState<SummaryStatusKey>(initialStatus);
  const [branchFilter, setBranchFilter] = useState('__all__');
  const [doctorFilter, setDoctorFilter] = useState('__all__');
  const debouncedSearch = useDebouncedValue(search.trim(), 400);
  const [now, setNow] = useState(() => new Date());
  const queryStatus = searchParams.get('status');

  const { data: branches = [] } = useBranches();
  const doctorParams = useMemo(
    () => ({
      ...(branchFilter !== '__all__' ? { branchId: branchFilter } : {}),
    }),
    [branchFilter],
  );
  const {
    data: doctors = [],
    isError: isDoctorsError,
    isLoading: isDoctorsLoading,
  } = useAdminDoctorOptions(doctorParams);

  useEffect(() => {
    setDoctorFilter('__all__');
    setPage(1);
  }, [branchFilter]);

  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 60_000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    setPage(1);
  }, [statusFilter, doctorFilter, debouncedSearch]);

  useEffect(() => {
    const nextStatus = parseStatusFilter(queryStatus);
    if (nextStatus) {
      setStatusFilter(nextStatus);
      setPage(1);
    }
  }, [queryStatus]);

  const summaryParams = useMemo(
    () => ({
      ...(branchFilter !== '__all__' ? { branchId: branchFilter } : {}),
      ...(doctorFilter !== '__all__' ? { doctorId: doctorFilter } : {}),
    }),
    [branchFilter, doctorFilter],
  );

  const { isConnected: isRealtimeConnected } = useAppointmentSummaryRealtime(summaryParams);

  const { data: summary } = useAppointmentSummary(summaryParams, {
    refetchInterval: isRealtimeConnected ? false : 30000,
    refetchOnWindowFocus: !isRealtimeConnected,
  });

  const appointmentParams = useMemo<AppointmentListQueryParams>(
    () => ({
      page: String(page - 1),
      size: '20',
      ...(debouncedSearch ? { q: debouncedSearch } : {}),
      ...(statusFilter !== '__all__' ? { status: statusFilter } : {}),
      ...(branchFilter !== '__all__' ? { branchId: branchFilter } : {}),
      ...(doctorFilter !== '__all__' ? { doctorId: doctorFilter } : {}),
    }),
    [branchFilter, debouncedSearch, doctorFilter, page, statusFilter],
  );

  const { data, isLoading, isError, refetch } = useAdminAppointments(
    appointmentParams,
    {
      refetchInterval: isRealtimeConnected ? false : 30000,
      refetchOnWindowFocus: !isRealtimeConnected,
    },
  );
  const appointments = useMemo(() => data?.items ?? [], [data?.items]);

  const columns: Column<Appointment>[] = [
    {
      key: 'code',
      header: t('modules.appointments.code'),
      cell: (r) => <span className="font-medium">{r.code}</span>,
    },
    {
      key: 'patientFullName',
      header: t('common.patient'),
      cell: (r) => (
        <div>
          <p className="font-medium">{r.patientFullName}</p>
          <p className="text-xs text-muted-foreground">{r.patientPhone}</p>
        </div>
      ),
    },
    {
      key: 'doctorName',
      header: t('common.doctor'),
      cell: (r) => (
        <div>
          <p className="text-sm">{r.doctorName}</p>
          <p className="text-xs text-muted-foreground">{r.specialtyName}</p>
        </div>
      ),
    },
    {
      key: 'visitDate',
      header: t('modules.appointments.visitDate'),
      cell: (r) => (
        <div>
          <p className="tabular-nums">{r.visitDate}</p>
          <p className="text-xs text-muted-foreground">
            {r.slotStart} · {r.branchName}
          </p>
        </div>
      ),
    },
    {
      key: 'status',
      header: t('common.status'),
      cell: (r) => {
        const noShowEligible = isAppointmentOverdue(r, now);
        const lateForCheckIn = !noShowEligible && isLateForCheckIn(r, now);
        const checkedInLateLabel = getCheckedInLateLabel(r);

        return (
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <StatusBadge status={r.status} />
              {noShowEligible ? <NoShowEligibleBadge /> : null}
              {lateForCheckIn ? <LateCheckInBadge /> : null}
              {checkedInLateLabel ? <CheckedInLateBadge label={checkedInLateLabel} /> : null}
            </div>
            <p className="mt-1 text-xs text-muted-foreground">
              {renderClaimStatus(r)}
            </p>
          </div>
        );
      },
    },
  ];

  return (
    <div>
      <PageHeader
        title={t('modules.appointments.title')}
        description={t('modules.appointments.desc')}
      />

      <FilterBar
        filters={[
          {
            key: 'status',
            label: t('common.status'),
            options: statusOptions,
            value: statusFilter,
            onChange: (v) => setStatusFilter(v as SummaryStatusKey),
          },
          {
            key: 'branch',
            label: t('common.branch'),
            options: branches.map((branch) => ({
              label: branch.name,
              value: branch.id,
            })),
            value: branchFilter,
            onChange: setBranchFilter,
          },
          {
            key: 'doctor',
            label: t('common.doctor'),
            options: doctors.map((doctor) => ({
              label: [doctor.fullName, getInternalDoctorOptionNote(doctor)]
                .filter(Boolean)
                .join(' · '),
              value: doctor.id,
            })),
            value: doctorFilter,
            onChange: setDoctorFilter,
          },
        ]}
      />

      {isDoctorsError ? (
        <p className="mb-3 text-sm text-destructive">
          Không tải được danh sách bác sĩ. Vui lòng thử lại.
        </p>
      ) : !isDoctorsLoading && doctors.length === 0 ? (
        <p className="mb-3 text-sm text-muted-foreground">
          Không có bác sĩ phù hợp.
        </p>
      ) : null}

      <AppointmentSummaryCards
        summary={summary}
        activeStatus={statusFilter}
        onSelect={(value) => {
          setStatusFilter(value);
          setPage(1);
        }}
      />

      <DataTable
        columns={columns}
        data={appointments}
        searchValue={search}
        onSearchChange={setSearch}
        searchPlaceholder="Tìm trên toàn bộ lịch hẹn..."
        page={page}
        totalPages={Math.max(data?.meta.totalPages ?? 1, 1)}
        onPageChange={setPage}
        isLoading={isLoading}
        isError={isError}
        onRetry={() => void refetch()}
        keyExtractor={(r) => r.id}
        actions={(row) => (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => navigate(`/app/appointments/${row.id}/process`)}
          >
            <Eye className="mr-2 h-4 w-4" />
            Xem chi tiết
          </Button>
        )}
      />
    </div>
  );
}
