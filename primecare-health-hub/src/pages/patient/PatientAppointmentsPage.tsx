import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  AlertTriangle,
  CalendarClock,
  CalendarPlus,
  Clock3,
  History,
  MapPin,
  Search,
  ShieldCheck,
  Stethoscope,
  UserRound,
  XCircle,
} from 'lucide-react';
import { AppPagination } from '@/components/AppPagination';
import PatientStatusHistoryDialog from '@/components/patient/PatientStatusHistoryDialog';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Textarea } from '@/components/ui/textarea';
import {
  useCancelPatientAppointment,
  usePatientAppointmentStatusHistory,
  usePatientAppointments,
} from '@/hooks/use-patient-portal';
import { useDebouncedValue } from '@/hooks/use-debounced-value';
import { cn } from '@/lib/utils';
import type { PatientAppointmentHistoryItem } from '@/types/api';

const PAGE_SIZE = 10;
const EMPTY_APPOINTMENTS: PatientAppointmentHistoryItem[] = [];

type AppointmentTab = 'upcoming' | 'past' | 'cancelled' | 'all';

interface StatusInfo {
  badgeClassName: string;
  description: string;
  label: string;
}

const statusCopy: Record<string, StatusInfo> = {
  REQUESTED: {
    label: 'Chờ xác nhận',
    description: 'PrimeCare đã nhận yêu cầu và đang chờ cơ sở tiếp nhận xác nhận.',
    badgeClassName: 'border-primary/20 bg-primary/10 text-primary hover:bg-primary/10',
  },
  CONFIRMED: {
    label: 'Đã xác nhận',
    description: 'Cơ sở đã xác nhận lịch. Vui lòng đến đúng thời gian đã hẹn.',
    badgeClassName: 'border-emerald-500/20 bg-emerald-50 text-emerald-700 hover:bg-emerald-50',
  },
  CHECKED_IN: {
    label: 'Đã check-in',
    description: 'Bạn đã được ghi nhận có mặt tại cơ sở.',
    badgeClassName: 'border-emerald-500/20 bg-emerald-50 text-emerald-700 hover:bg-emerald-50',
  },
  COMPLETED: {
    label: 'Đã hoàn tất',
    description: 'Lượt khám đã kết thúc. Bạn có thể xem kết quả hoặc hóa đơn nếu đã phát sinh.',
    badgeClassName: 'border-slate-200 bg-slate-100 text-slate-700 hover:bg-slate-100',
  },
  CANCELLED: {
    label: 'Đã hủy',
    description: 'Lịch hẹn đã được hủy và không còn giữ khung giờ này.',
    badgeClassName: 'border-rose-500/20 bg-rose-50 text-rose-700 hover:bg-rose-50',
  },
  NO_SHOW: {
    label: 'Không đến khám',
    description: 'Lịch đã qua nhưng hệ thống không ghi nhận bạn đến cơ sở.',
    badgeClassName: 'border-amber-500/20 bg-amber-50 text-amber-700 hover:bg-amber-50',
  },
};

function getStatusInfo(status?: string): StatusInfo {
  if (!status) {
    return {
      label: 'Khởi tạo',
      description: 'Lịch hẹn vừa được tạo hoặc chưa có trạng thái chi tiết.',
      badgeClassName: 'border-border bg-background text-muted-foreground',
    };
  }

  return statusCopy[status] ?? {
    label: status,
    description: 'PrimeCare đang cập nhật mô tả trạng thái này.',
    badgeClassName: 'border-border bg-muted/40 text-muted-foreground',
  };
}

function getSessionLabel(session?: string) {
  switch (session) {
    case 'AM':
    case 'MORNING':
      return 'Buổi sáng';
    case 'PM':
    case 'AFTERNOON':
      return 'Buổi chiều';
    default:
      return session || 'Đang cập nhật';
  }
}

function normalizeSessionParam(session?: string) {
  if (session === 'MORNING') return 'AM';
  if (session === 'AFTERNOON') return 'PM';
  return session;
}

function formatDate(value?: string) {
  if (!value) return 'Đang cập nhật ngày khám';
  const date = new Date(`${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('vi-VN', {
    weekday: 'long',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(date);
}

function formatDateTime(value?: string) {
  if (!value) return 'Đang cập nhật';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}

function isPastVisitDate(value?: string) {
  if (!value) return false;
  const visitDate = new Date(`${value}T00:00:00`);
  if (Number.isNaN(visitDate.getTime())) return false;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return visitDate.getTime() < today.getTime();
}

function getAppointmentTab(row: PatientAppointmentHistoryItem): AppointmentTab {
  if (row.status === 'CANCELLED' || row.status === 'NO_SHOW') return 'cancelled';
  if (row.status === 'COMPLETED' || isPastVisitDate(row.visitDate)) return 'past';
  return 'upcoming';
}

type AppointmentWithOptionalBookingContext = PatientAppointmentHistoryItem & {
  branchId?: string;
  doctorId?: string;
  specialtyId?: string;
  slotStart?: string;
};

function buildBookingUrl(row: PatientAppointmentHistoryItem) {
  const context = row as AppointmentWithOptionalBookingContext;
  const params = new URLSearchParams();

  if (context.branchId) params.set('branchId', context.branchId);
  if (context.specialtyId) params.set('specialtyId', context.specialtyId);
  if (context.doctorId) params.set('doctorId', context.doctorId);
  if (context.visitDate) params.set('date', context.visitDate);

  const normalizedSession = normalizeSessionParam(context.session);
  if (normalizedSession) params.set('session', normalizedSession);
  if (context.slotStart) params.set('slot', context.slotStart);

  const query = params.toString();
  return query ? `/booking?${query}` : '/booking';
}

function formatStatusForHistory(status?: string) {
  const info = getStatusInfo(status);
  return { description: info.description, label: info.label };
}

function AppointmentStatusBadge({ status }: { status?: string }) {
  const info = getStatusInfo(status);

  return (
    <div className="space-y-1.5">
      <Badge variant="outline" className={cn('rounded-full px-3 py-1 font-medium', info.badgeClassName)}>
        {info.label}
      </Badge>
      <p className="max-w-xl text-xs leading-5 text-muted-foreground">{info.description}</p>
    </div>
  );
}

function DetailItem({
  icon: Icon,
  label,
  value,
}: {
  icon: typeof CalendarClock;
  label: string;
  value?: string;
}) {
  return (
    <div className="rounded-2xl border border-border/70 bg-muted/15 px-4 py-3">
      <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.16em] text-primary">
        <Icon className="h-3.5 w-3.5" />
        {label}
      </div>
      <p className="mt-2 text-sm font-medium leading-6 text-foreground">{value || 'Đang cập nhật'}</p>
    </div>
  );
}

function AppointmentCard({
  appointment,
  cancelPending,
  onCancel,
  onViewHistory,
}: {
  appointment: PatientAppointmentHistoryItem;
  cancelPending: boolean;
  onCancel: (appointment: PatientAppointmentHistoryItem) => void;
  onViewHistory: (appointmentId: string) => void;
}) {
  const tab = getAppointmentTab(appointment);
  const canCancel = Boolean(appointment.canCancel);
  const canOfferBookingLink = tab !== 'upcoming' || !canCancel;

  return (
    <article className="rounded-[1.5rem] border border-border/70 bg-background p-5 shadow-sm">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div className="min-w-0">
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">
            Phiếu hẹn {appointment.code || 'đang cập nhật'}
          </p>
          <h2 className="mt-2 text-xl font-semibold tracking-tight text-foreground">
            {appointment.specialtyName || 'Chuyên khoa đang cập nhật'}
          </h2>
          <p className="mt-1 text-sm leading-6 text-muted-foreground">
            {appointment.doctorName || 'Bác sĩ đang cập nhật'} · {appointment.branchName || 'Cơ sở đang cập nhật'}
          </p>
        </div>

        <AppointmentStatusBadge status={appointment.status} />
      </div>

      <div className="mt-5 grid gap-3 md:grid-cols-2 xl:grid-cols-4">
        <DetailItem icon={CalendarClock} label="Ngày khám" value={formatDate(appointment.visitDate)} />
        <DetailItem
          icon={Clock3}
          label="Buổi / giờ dự kiến"
          value={`${getSessionLabel(appointment.session)}${appointment.etaStart ? ` · ${appointment.etaStart}${appointment.etaEnd ? ` - ${appointment.etaEnd}` : ''}` : ''}`}
        />
        <DetailItem icon={MapPin} label="Cơ sở" value={appointment.branchName} />
        <DetailItem icon={UserRound} label="Bác sĩ" value={appointment.doctorName} />
      </div>

      <div className="mt-5 rounded-2xl border border-primary/10 bg-primary/[0.04] px-4 py-3 text-sm leading-6 text-muted-foreground">
        {tab === 'upcoming'
          ? 'Nếu cần thay đổi kế hoạch, bạn có thể hủy lịch còn đủ điều kiện hoặc mở luồng đặt lịch để chọn khung giờ mới.'
          : tab === 'cancelled'
            ? 'Lịch này không còn giữ khung giờ. Bạn có thể đặt lại lịch mới nếu vẫn cần tiếp tục thăm khám.'
            : 'Lịch này đã qua. Bạn có thể xem lịch sử trạng thái hoặc đặt lịch mới nếu cần tái khám.'}
      </div>

      <div className="mt-5 flex flex-col gap-3 sm:flex-row sm:flex-wrap">
        <Button
          type="button"
          variant="outline"
          className="h-11 rounded-2xl"
          onClick={() => onViewHistory(appointment.id)}
        >
          <History className="mr-2 h-4 w-4" />
          Xem lịch sử trạng thái
        </Button>

        <Button asChild variant="outline" className="h-11 rounded-2xl">
          <Link to={buildBookingUrl(appointment)}>
            <CalendarPlus className="mr-2 h-4 w-4" />
            {tab === 'upcoming' ? 'Đặt lịch thay thế' : 'Đặt lịch mới'}
          </Link>
        </Button>

        {canCancel ? (
          <Button
            type="button"
            variant="outline"
            className="h-11 rounded-2xl border-rose-200 text-rose-700 hover:bg-rose-50 hover:text-rose-700"
            disabled={cancelPending}
            onClick={() => onCancel(appointment)}
          >
            <XCircle className="mr-2 h-4 w-4" />
            Hủy lịch
          </Button>
        ) : (
          <div className="flex min-h-11 items-center rounded-2xl bg-muted/20 px-4 text-sm text-muted-foreground">
            {canOfferBookingLink
              ? appointment.cancelBlockedReason || 'Lịch này không thể hủy trực tuyến.'
              : 'Lịch này hiện không thể hủy trực tuyến.'}
          </div>
        )}
      </div>
    </article>
  );
}

function CancelAppointmentDialog({
  appointment,
  isPending,
  onConfirm,
  onOpenChange,
  reason,
  setReason,
}: {
  appointment: PatientAppointmentHistoryItem | null;
  isPending: boolean;
  onConfirm: () => void;
  onOpenChange: (open: boolean) => void;
  reason: string;
  setReason: (value: string) => void;
}) {
  const statusInfo = getStatusInfo(appointment?.status);

  return (
    <Dialog open={Boolean(appointment)} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[92vh] overflow-y-auto rounded-[1.75rem] p-0 sm:max-w-2xl">
        <div className="border-b border-border/70 bg-primary/[0.04] px-6 py-6">
          <DialogHeader>
            <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-2xl bg-rose-50 text-rose-700">
              <AlertTriangle className="h-6 w-6" />
            </div>
            <DialogTitle className="text-2xl tracking-tight">Xác nhận hủy lịch hẹn</DialogTitle>
            <DialogDescription className="leading-6">
              Hủy lịch sẽ giải phóng khung giờ này cho bệnh nhân khác. Nếu bạn vẫn cần khám, hãy đặt lịch thay thế sau khi hủy hoặc giữ lịch hiện tại.
            </DialogDescription>
          </DialogHeader>
        </div>

        {appointment ? (
          <div className="space-y-5 px-6 py-6">
            <div className="rounded-[1.25rem] border border-border/70 bg-background p-4">
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">
                Phiếu hẹn {appointment.code}
              </p>
              <h3 className="mt-2 text-lg font-semibold text-foreground">
                {appointment.specialtyName || 'Chuyên khoa đang cập nhật'}
              </h3>
              <p className="mt-1 text-sm leading-6 text-muted-foreground">
                {appointment.doctorName || 'Bác sĩ đang cập nhật'} · {appointment.branchName || 'Cơ sở đang cập nhật'}
              </p>

              <div className="mt-4 grid gap-3 sm:grid-cols-2">
                <DetailItem icon={CalendarClock} label="Ngày khám" value={formatDate(appointment.visitDate)} />
                <DetailItem
                  icon={Clock3}
                  label="Buổi / giờ dự kiến"
                  value={`${getSessionLabel(appointment.session)}${appointment.etaStart ? ` · ${appointment.etaStart}${appointment.etaEnd ? ` - ${appointment.etaEnd}` : ''}` : ''}`}
                />
              </div>

              <div className="mt-4">
                <Badge variant="outline" className={cn('rounded-full px-3 py-1 font-medium', statusInfo.badgeClassName)}>
                  {statusInfo.label}
                </Badge>
                <p className="mt-2 text-sm leading-6 text-muted-foreground">{statusInfo.description}</p>
              </div>
            </div>

            <div className="rounded-[1.25rem] border border-rose-100 bg-rose-50/70 px-4 py-3 text-sm leading-6 text-rose-800">
              Sau khi hủy, lịch sẽ không tự chuyển sang khung giờ khác. PrimeCare sẽ cập nhật danh sách lịch hẹn của bạn, và bạn có thể mở luồng đặt lịch để chọn thời gian mới nếu cần.
            </div>

            <div className="space-y-2">
              <label htmlFor="cancel-reason" className="text-sm font-semibold text-foreground">
                Lý do hủy lịch
              </label>
              <Textarea
                id="cancel-reason"
                value={reason}
                onChange={(event) => setReason(event.target.value)}
                placeholder="Ví dụ: Tôi cần đổi ngày khám hoặc đã sắp xếp lịch khác."
                className="min-h-28 rounded-2xl border-border/70 bg-background"
                disabled={isPending}
              />
              <p className="text-xs leading-5 text-muted-foreground">
                Không bắt buộc, nhưng lý do ngắn sẽ giúp nhân sự phòng khám xử lý lịch tốt hơn.
              </p>
            </div>
          </div>
        ) : null}

        <DialogFooter className="border-t border-border/70 px-6 py-5 sm:gap-3 sm:space-x-0">
          <Button
            type="button"
            variant="outline"
            className="h-11 rounded-2xl"
            disabled={isPending}
            onClick={() => onOpenChange(false)}
          >
            Giữ lịch hẹn
          </Button>
          <Button
            type="button"
            variant="destructive"
            className="h-11 rounded-2xl"
            disabled={isPending}
            onClick={onConfirm}
          >
            {isPending ? 'Đang hủy lịch...' : 'Xác nhận hủy lịch'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export default function PatientAppointmentsPage() {
  const [activeTab, setActiveTab] = useState<AppointmentTab>('upcoming');
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [selectedAppointmentId, setSelectedAppointmentId] = useState<string | null>(null);
  const [cancelAppointment, setCancelAppointment] = useState<PatientAppointmentHistoryItem | null>(null);
  const [cancelReason, setCancelReason] = useState('');
  const debouncedSearch = useDebouncedValue(search.trim(), 400);

  useEffect(() => {
    setPage(0);
  }, [activeTab, debouncedSearch]);

  const appointmentParams = useMemo(
    () => ({
      page: String(page),
      size: String(PAGE_SIZE),
      ...(debouncedSearch ? { q: debouncedSearch } : {}),
      ...(activeTab !== 'all' ? { tab: activeTab } : {}),
    }),
    [activeTab, debouncedSearch, page],
  );

  const { data, isLoading } = usePatientAppointments(appointmentParams);
  const cancelMutation = useCancelPatientAppointment();

  const appointments = data?.items ?? EMPTY_APPOINTMENTS;
  const meta = data?.meta;
  const totalItems = meta?.totalItems ?? 0;
  const totalPages = meta?.totalPages ?? 0;

  const historyQuery = usePatientAppointmentStatusHistory(selectedAppointmentId ?? undefined, Boolean(selectedAppointmentId));

  const selectedAppointment = useMemo(
    () => appointments.find((item) => item.id === selectedAppointmentId),
    [appointments, selectedAppointmentId],
  );

  const visibleAppointments = useMemo(() => appointments, [appointments]);

  const handleOpenCancel = (appointment: PatientAppointmentHistoryItem) => {
    setCancelReason('');
    setCancelAppointment(appointment);
  };

  const handleCancelDialogOpenChange = (open: boolean) => {
    if (open) return;
    if (cancelMutation.isPending) return;
    setCancelAppointment(null);
    setCancelReason('');
  };

  const handleConfirmCancel = async () => {
    if (!cancelAppointment) return;
    await cancelMutation.mutateAsync({
      appointmentId: cancelAppointment.id,
      body: { reason: cancelReason.trim() || undefined },
    });
    setCancelAppointment(null);
    setCancelReason('');
  };

  const hasAnyAppointments = totalItems > 0 || Boolean(debouncedSearch) || activeTab !== 'all';
  const pageLabel = totalPages > 1 ? `Trang ${(meta?.page ?? page) + 1} / ${totalPages}` : 'Trang hiện tại';

  return (
    <>
      <div className="space-y-6">
        <section className="rounded-[1.75rem] border border-primary/10 bg-primary/[0.04] p-5 shadow-sm md:p-6">
          <div className="grid gap-5 xl:grid-cols-[1fr_0.9fr] xl:items-end">
            <div>
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">
                Phiếu hẹn của tôi
              </p>
              <h1 className="mt-2 text-2xl font-semibold tracking-tight text-foreground md:text-3xl">
                Theo dõi lịch hẹn, trạng thái và thay đổi cần thiết
              </h1>
              <p className="mt-3 max-w-3xl text-sm leading-7 text-muted-foreground">
                Các lịch hẹn được sắp xếp để bạn dễ kiểm tra bước tiếp theo. Nếu cần đổi kế hoạch, hãy hủy lịch còn đủ điều kiện rồi đặt lại lịch mới trong luồng đặt lịch PrimeCare.
              </p>
            </div>

            <div className="grid gap-3 sm:grid-cols-2">
              <div className="rounded-2xl border border-border/70 bg-white/85 px-4 py-4 shadow-sm">
                <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.16em] text-primary">
                  <CalendarClock className="h-4 w-4" />
                  Tổng lịch hẹn
                </div>
                <p className="mt-3 text-2xl font-semibold text-foreground">{totalItems}</p>
                <p className="mt-1 text-sm leading-6 text-muted-foreground">{pageLabel}</p>
              </div>

              <div className="rounded-2xl border border-border/70 bg-white/85 px-4 py-4 shadow-sm">
                <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.16em] text-primary">
                  <ShieldCheck className="h-4 w-4" />
                  Hỗ trợ thao tác
                </div>
                <p className="mt-3 text-sm font-semibold text-foreground">Hủy lịch có xác nhận</p>
                <p className="mt-1 text-sm leading-6 text-muted-foreground">
                  Bạn sẽ luôn thấy lại thông tin lịch trước khi xác nhận hủy.
                </p>
              </div>
            </div>
          </div>
        </section>

        <section className="rounded-[1.5rem] border border-border/70 bg-background p-4 shadow-sm md:p-5">
          <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(260px,0.42fr)] lg:items-end">
            <Tabs value={activeTab} onValueChange={(value) => setActiveTab(value as AppointmentTab)}>
              <TabsList className="grid h-auto w-full grid-cols-2 rounded-2xl bg-muted/25 p-1 md:grid-cols-4">
                <TabsTrigger value="upcoming" className="rounded-xl py-2.5">
                  Sắp tới
                </TabsTrigger>
                <TabsTrigger value="past" className="rounded-xl py-2.5">
                  Đã qua
                </TabsTrigger>
                <TabsTrigger value="cancelled" className="rounded-xl py-2.5">
                  Đã hủy
                </TabsTrigger>
                <TabsTrigger value="all" className="rounded-xl py-2.5">
                  Tất cả
                </TabsTrigger>
              </TabsList>
            </Tabs>

            <div className="space-y-2">
              <label htmlFor="appointment-code-search" className="text-sm font-medium text-foreground">
                Tìm theo mã phiếu hẹn
              </label>
              <div className="relative">
                <Search className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                <Input
                  id="appointment-code-search"
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  placeholder="Ví dụ: PC-..."
                  className="h-12 rounded-2xl border-border/70 bg-background pl-11 pr-11 shadow-none focus-visible:ring-2 focus-visible:ring-primary/20"
                />
                {search ? (
                  <button
                    type="button"
                    aria-label="Xóa tìm kiếm"
                    className="absolute right-3 top-1/2 inline-flex h-8 w-8 -translate-y-1/2 items-center justify-center rounded-full text-muted-foreground transition-colors hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20"
                    onClick={() => setSearch('')}
                  >
                    <XCircle className="h-4 w-4" />
                  </button>
                ) : null}
              </div>
            </div>
          </div>
        </section>

        {isLoading ? (
          <div className="rounded-[1.5rem] border border-dashed border-border/70 bg-background px-6 py-14 text-center text-sm text-muted-foreground shadow-sm">
            Đang tải lịch hẹn của bạn...
          </div>
        ) : !hasAnyAppointments ? (
          <div className="rounded-[1.75rem] border border-dashed border-border/70 bg-background px-6 py-14 text-center shadow-sm">
            <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-primary/10 text-primary">
              <CalendarPlus className="h-7 w-7" />
            </div>
            <h2 className="mt-5 text-xl font-semibold tracking-tight text-foreground">
              Bạn chưa có lịch hẹn nào trong tài khoản này
            </h2>
            <p className="mx-auto mt-3 max-w-2xl text-sm leading-7 text-muted-foreground">
              Không cần thao tác gì nếu bạn chưa muốn đặt lịch. Khi bạn gửi yêu cầu đặt lịch thành công, PrimeCare sẽ hiển thị lịch tại đây để bạn theo dõi và quản lý.
            </p>
            <div className="mt-6 flex flex-col items-center justify-center gap-3 sm:flex-row">
              <Button asChild className="h-11 rounded-2xl">
                <Link to="/booking">
                  <CalendarPlus className="mr-2 h-4 w-4" />
                  Đặt lịch khám
                </Link>
              </Button>
              <Button asChild variant="outline" className="h-11 rounded-2xl">
                <Link to="/appointments/lookup">Tra cứu phiếu hẹn công khai</Link>
              </Button>
            </div>
          </div>
        ) : visibleAppointments.length === 0 ? (
          <div className="rounded-[1.5rem] border border-dashed border-border/70 bg-background px-6 py-12 text-center shadow-sm">
            <h2 className="text-lg font-semibold text-foreground">
              Không có lịch hẹn phù hợp
            </h2>
            <p className="mx-auto mt-3 max-w-xl text-sm leading-6 text-muted-foreground">
              Hãy đổi nhóm trạng thái hoặc xóa mã tìm kiếm để tải lại danh sách từ hệ thống.
            </p>
            <Button
              type="button"
              variant="outline"
              className="mt-5 h-11 rounded-2xl"
              onClick={() => {
                setActiveTab('all');
                setSearch('');
              }}
            >
              Xóa bộ lọc hiển thị
            </Button>
          </div>
        ) : (
          <div className="space-y-4">
            {visibleAppointments.map((appointment) => (
              <AppointmentCard
                key={appointment.id}
                appointment={appointment}
                cancelPending={cancelMutation.isPending}
                onCancel={handleOpenCancel}
                onViewHistory={setSelectedAppointmentId}
              />
            ))}
          </div>
        )}

        <AppPagination
          page={meta?.page ?? page}
          totalPages={totalPages}
          onPageChange={setPage}
          className="mt-8"
        />
      </div>

      <CancelAppointmentDialog
        appointment={cancelAppointment}
        isPending={cancelMutation.isPending}
        reason={cancelReason}
        setReason={setCancelReason}
        onConfirm={() => void handleConfirmCancel()}
        onOpenChange={handleCancelDialogOpenChange}
      />

      <PatientStatusHistoryDialog
        open={Boolean(selectedAppointmentId)}
        onOpenChange={(open) => {
          if (!open) setSelectedAppointmentId(null);
        }}
        title={selectedAppointment ? `Lịch sử trạng thái - ${selectedAppointment.code}` : 'Lịch sử trạng thái'}
        items={historyQuery.data}
        isLoading={historyQuery.isLoading}
        formatStatus={formatStatusForHistory}
      />
    </>
  );
}
