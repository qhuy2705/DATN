import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  AlertTriangle,
  Clock,
  Eye,
  MoreHorizontal,
  UserCheck,
} from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { DataTable, type Column } from '@/components/DataTable';
import { FilterBar } from '@/components/FilterBar';
import { PageHeader } from '@/components/PageHeader';
import { StatusBadge } from '@/components/StatusBadge';
import { Button } from '@/components/ui/button';
import { AppointmentSummaryCards } from '@/components/AppointmentSummaryCards';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { Input } from '@/components/ui/input';
import {
  useAdminAppointments,
  useAppointmentAction,
  useAppointmentSummary,
  useAppointmentSummaryRealtime,
} from '@/hooks/use-admin-data';
import { useBranches, useDoctors } from '@/hooks/use-public-data';
import { useDebouncedValue } from '@/hooks/use-debounced-value';
import type { Appointment, AppointmentListQueryParams } from '@/types/api';
import { buildAppointmentStatusOptions } from '@/lib/filter-options';
import { useAuthStore } from '@/stores/auth-store';
import { isAppointmentOverdue } from '@/lib/appointment-utils';
import { OverdueBadge } from '@/components/OverdueBadge';

type ActionType = 'claim';

type SummaryStatusKey =
  | '__all__'
  | 'REQUESTED'
  | 'CONFIRMED'
  | 'CHECKED_IN'
  | 'COMPLETED'
  | 'NO_SHOW'
  | 'CANCELLED';

type WorkflowFilterKey = '__all__' | 'overdue' | 'noShowFollowUp';

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

function readInitialAppointmentFilters(searchParams: URLSearchParams): {
  status: SummaryStatusKey;
  workflow: WorkflowFilterKey;
} {
  if (
    searchParams.get('status') === 'NO_SHOW' &&
    searchParams.get('followUpPending') === 'true'
  ) {
    return { status: '__all__', workflow: 'noShowFollowUp' };
  }

  return {
    status: parseStatusFilter(searchParams.get('status')) ?? '__all__',
    workflow: '__all__',
  };
}

export default function AppointmentsPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const currentUser = useAuthStore((s) => s.user);
  const initialFilters = useMemo(
    () => readInitialAppointmentFilters(searchParams),
    [searchParams],
  );

  const isClaimExpired = (row: Appointment) => {
    if (!row.processingExpiresAt) return false;
    const expiresAt = new Date(row.processingExpiresAt);
    return !Number.isNaN(expiresAt.getTime()) && expiresAt.getTime() <= Date.now();
  };

  const isNoShowFollowUp = (row: Appointment) =>
    row.status === 'NO_SHOW' && row.followUpPending === true;

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

  const canActOnClaimedAppointment = (row: Appointment) => {
    if (isClaimExpired(row)) return false;
    if (!row.processingById || !currentUser?.id) return false;
    const actionable =
      ['REQUESTED', 'CONFIRMED', 'CHECKED_IN'].includes(row.status) ||
      isNoShowFollowUp(row);
    return actionable && String(row.processingById) === String(currentUser.id);
  };

  const canClaimAppointment = (row: Appointment) => {
    const claimable =
      ['REQUESTED', 'CONFIRMED'].includes(row.status) ||
      isNoShowFollowUp(row);
    return claimable && (!row.processingById || isClaimExpired(row));
  };

  const statusOptions = useMemo(() => buildAppointmentStatusOptions(t), [t]);

  const [search, setSearch] = useState('');
  const [page, setPage] = useState(1);
  const [statusFilter, setStatusFilter] = useState<SummaryStatusKey>(initialFilters.status);
  const [workflowFilter, setWorkflowFilter] = useState<WorkflowFilterKey>(initialFilters.workflow);
  const [branchFilter, setBranchFilter] = useState('__all__');
  const [doctorFilter, setDoctorFilter] = useState('__all__');
  const [checkInToken, setCheckInToken] = useState('');
  const [checkInOpen, setCheckInOpen] = useState(false);
  const [actionDialog, setActionDialog] = useState<{
    type: ActionType;
    row: Appointment;
  } | null>(null);
  const debouncedSearch = useDebouncedValue(search.trim(), 400);
  const [now, setNow] = useState(() => new Date());
  const queryStatus = searchParams.get('status');
  const queryFollowUpPending = searchParams.get('followUpPending');

  const { data: branches = [] } = useBranches();
  const doctorParams = useMemo(
    () => ({
      size: '100',
      ...(branchFilter !== '__all__' ? { branchId: branchFilter } : {}),
    }),
    [branchFilter],
  );
  const { data: doctorsPage } = useDoctors(doctorParams);
  const doctors = useMemo(() => doctorsPage?.items ?? [], [doctorsPage?.items]);

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
  }, [statusFilter, workflowFilter, doctorFilter, debouncedSearch]);

  useEffect(() => {
    if (!queryStatus && !queryFollowUpPending) return;

    if (queryStatus === 'NO_SHOW' && queryFollowUpPending === 'true') {
      setStatusFilter('__all__');
      setWorkflowFilter('noShowFollowUp');
      setPage(1);
      return;
    }

    const nextStatus = parseStatusFilter(queryStatus);
    if (nextStatus) {
      setStatusFilter(nextStatus);
      setWorkflowFilter('__all__');
      setPage(1);
    }
  }, [queryFollowUpPending, queryStatus]);

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
      ...(workflowFilter === 'noShowFollowUp'
        ? { status: 'NO_SHOW', followUpPending: true }
        : statusFilter !== '__all__'
          ? { status: statusFilter }
          : {}),
      ...(workflowFilter === 'overdue' ? { overdue: 'true' } : {}),
      ...(branchFilter !== '__all__' ? { branchId: branchFilter } : {}),
      ...(doctorFilter !== '__all__' ? { doctorId: doctorFilter } : {}),
    }),
    [branchFilter, debouncedSearch, doctorFilter, page, statusFilter, workflowFilter],
  );

  const { data, isLoading, isError, refetch } = useAdminAppointments(
    appointmentParams,
    {
      refetchInterval: isRealtimeConnected ? false : 30000,
      refetchOnWindowFocus: !isRealtimeConnected,
    },
  );

  const appointmentAction = useAppointmentAction();

  const appointments = useMemo(() => data?.items ?? [], [data?.items]);

  const openActionDialog = (type: ActionType, row: Appointment) => {
    setActionDialog({ type, row });
  };

  const submitAction = async () => {
    if (!actionDialog) return;
    const { row } = actionDialog;

    try {
      await appointmentAction.mutateAsync({
        id: row.id,
        action: 'claim',
      });
      setActionDialog(null);
      navigate(`/app/appointments/${row.id}/process`);
    } catch {
      // useAppointmentAction already shows the backend message.
    }
  };

  const handleCheckIn = async () => {
    try {
      await appointmentAction.mutateAsync({
        action: 'check-in',
        body: { qrToken: checkInToken },
      });
      setCheckInOpen(false);
      setCheckInToken('');
    } catch {
      // useAppointmentAction already shows the backend message.
    }
  };

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
      cell: (r) => (
        <div>
          <StatusBadge status={r.status} />
          {isAppointmentOverdue(r, now) ? <span className="ml-2"><OverdueBadge /></span> : null}
          {r.status === 'NO_SHOW' && r.followUpPending ? (
            <span className="ml-2 inline-flex items-center whitespace-nowrap rounded-full border border-warning/25 bg-warning/10 px-2.5 py-1 text-xs font-medium leading-none text-warning">
              Cần follow-up
            </span>
          ) : null}
          <p className="text-xs text-muted-foreground mt-1">
            {renderClaimStatus(r)}
          </p>
        </div>
      ),
    },
  ];

  return (
    <div>
      <PageHeader
        title={t('modules.appointments.title')}
        description={t('modules.appointments.desc')}
        actions={
          <Button variant="outline" onClick={() => setCheckInOpen(true)}>
            <UserCheck className="h-4 w-4 mr-2" />
            {t('modules.appointments.scanToken')}
          </Button>
        }
      />

      <FilterBar
        filters={[
          {
            key: 'status',
            label: t('common.status'),
            options: statusOptions,
            value: statusFilter,
            onChange: (v) => {
              setStatusFilter(v as SummaryStatusKey);
              setWorkflowFilter('__all__');
            },
          },
          {
            key: 'workflow',
            label: 'Cần xử lý',
            options: [
              { value: 'overdue', label: 'Quá giờ' },
              { value: 'noShowFollowUp', label: 'No-show cần xử lý' },
            ],
            value: workflowFilter,
            onChange: (v) => {
              setWorkflowFilter(v as WorkflowFilterKey);
              if (v !== '__all__') setStatusFilter('__all__');
            },
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
              label: doctor.fullName,
              value: doctor.id,
            })),
            value: doctorFilter,
            onChange: setDoctorFilter,
          },
        ]}
      />

      <AppointmentSummaryCards
        summary={summary}
        activeStatus={statusFilter}
        onSelect={(value) => {
          setStatusFilter(value);
          setWorkflowFilter('__all__');
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
        emptyMessage={
          workflowFilter === 'noShowFollowUp'
            ? 'Không có lịch no-show cần xử lý.'
            : undefined
        }
        keyExtractor={(r) => r.id}
        actions={(row) => {
          const canClaim = canClaimAppointment(row);
          const canContinue = canActOnClaimedAppointment(row);

          return (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="sm">
                  <MoreHorizontal className="h-4 w-4" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem onClick={() => navigate(`/app/appointments/${row.id}/process`)}>
                  <Eye className="h-4 w-4 mr-2" />
                  Xem chi tiết
                </DropdownMenuItem>

                {canClaim ? (
                  <DropdownMenuItem onClick={() => openActionDialog('claim', row)}>
                    {isNoShowFollowUp(row) ? (
                      <AlertTriangle className="h-4 w-4 mr-2" />
                    ) : (
                      <Clock className="h-4 w-4 mr-2" />
                    )}
                    {isNoShowFollowUp(row) ? 'Xử lý follow-up' : 'Nhận xử lý'}
                  </DropdownMenuItem>
                ) : null}

                {canContinue ? (
                  <DropdownMenuItem onClick={() => navigate(`/app/appointments/${row.id}/process`)}>
                    <UserCheck className="h-4 w-4 mr-2" />
                    {isNoShowFollowUp(row) ? 'Tiếp tục follow-up' : 'Tiếp tục xử lý'}
                  </DropdownMenuItem>
                ) : null}
              </DropdownMenuContent>
            </DropdownMenu>
          );
        }}
      />

      <Dialog open={checkInOpen} onOpenChange={setCheckInOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('modules.appointments.scanToken')}</DialogTitle>
            <DialogDescription>
              Dán mã trên phiếu hẹn hoặc quét bằng thiết bị scanner.
            </DialogDescription>
          </DialogHeader>
          <Input
            autoFocus
            value={checkInToken}
            onChange={(e) => setCheckInToken(e.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' && checkInToken.trim() && !appointmentAction.isPending) {
                event.preventDefault();
                void handleCheckIn();
              }
            }}
            placeholder="Nhập mã QR / token check-in"
          />
          <DialogFooter>
            <Button
              onClick={handleCheckIn}
              disabled={appointmentAction.isPending || !checkInToken.trim()}
            >
              {appointmentAction.isPending ? 'Đang check-in...' : t('modules.appointments.checkIn')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={!!actionDialog} onOpenChange={() => setActionDialog(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {actionDialog?.row && isNoShowFollowUp(actionDialog.row)
                ? 'Xử lý follow-up'
                : t('modules.appointments.claim')}
            </DialogTitle>
            <DialogDescription>
              Hệ thống tự giữ quyền xử lý trong 5 phút và tự gia hạn khi bạn còn thao tác trên màn này.
            </DialogDescription>
          </DialogHeader>

          <DialogFooter>
            <Button variant="outline" onClick={() => setActionDialog(null)}>
              {t('common.cancel')}
            </Button>
            <Button onClick={submitAction} disabled={appointmentAction.isPending}>
              {appointmentAction.isPending
                ? 'Đang nhận...'
                : actionDialog?.row && isNoShowFollowUp(actionDialog.row)
                  ? 'Xử lý follow-up'
                  : t('modules.appointments.claim')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
