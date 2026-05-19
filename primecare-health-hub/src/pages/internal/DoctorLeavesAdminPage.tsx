import { useMemo, useState } from 'react';
import { addMonths, endOfMonth, format, startOfMonth, subMonths } from 'date-fns';
import { enUS, vi } from 'date-fns/locale';
import {
  CalendarDays,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Filter,
  MessageSquareText,
  Search,
  ShieldCheck,
  XCircle,
} from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { PageHeader } from '@/components/PageHeader';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import {
  useAdminDoctorOptions,
  useAdminDoctorLeaves,
  useAffectedAppointmentsForLeave,
  useReviewDoctorLeave,
} from '@/hooks/use-admin-data';
import { getInternalDoctorOptionNote } from '@/lib/doctor-readiness';
import {
  LEAVE_STATUS_STYLES,
  countUniqueLeaveDays,
  getLeaveSessionSummary,
  getLeaveStatusLabel,
} from '@/lib/doctor-leave-utils';
import { cn } from '@/lib/utils';
import { statusTextClasses } from '@/lib/status-style-classes';
import type {
  DoctorCancellationAffectedAppointment,
  DoctorCancellationRecoverySummary,
  LeaveRequest,
} from '@/types/api';

const LEAVE_SECTION_PAGE_SIZE = 2;

function getTimeValue(value?: string | null, fallback = 0) {
  if (!value) return fallback;
  const time = new Date(value).getTime();
  return Number.isFinite(time) ? time : fallback;
}

function comparePendingLeaves(a: LeaveRequest, b: LeaveRequest) {
  const startDateComparison = a.startDate.localeCompare(b.startDate);
  if (startDateComparison !== 0) return startDateComparison;
  return getTimeValue(a.createdAt) - getTimeValue(b.createdAt);
}

function getHandledSortTime(leave: LeaveRequest) {
  return getTimeValue(
    leave.reviewedAt,
    getTimeValue(leave.updatedAt, getTimeValue(leave.createdAt)),
  );
}

function compareHandledLeaves(a: LeaveRequest, b: LeaveRequest) {
  return getHandledSortTime(b) - getHandledSortTime(a);
}

export default function DoctorLeavesAdminPage() {
  const { i18n } = useTranslation();
  const isEn = i18n.language?.startsWith('en');
  const locale = isEn ? enUS : vi;

  const [currentMonth, setCurrentMonth] = useState(() => startOfMonth(new Date()));
  const [doctorFilter, setDoctorFilter] = useState('__all__');
  const [statusFilter, setStatusFilter] = useState<'__all__' | LeaveRequest['status']>('__all__');
  const [search, setSearch] = useState('');
  const [pendingPage, setPendingPage] = useState(0);
  const [handledPage, setHandledPage] = useState(0);
  const [reviewTarget, setReviewTarget] = useState<LeaveRequest | null>(null);
  const [reviewAction, setReviewAction] = useState<'approve' | 'reject'>('approve');
  const [reviewNote, setReviewNote] = useState('');
  const [lastRecoverySummary, setLastRecoverySummary] =
    useState<DoctorCancellationRecoverySummary | null>(null);

  const from = format(startOfMonth(currentMonth), 'yyyy-MM-dd');
  const to = format(endOfMonth(currentMonth), 'yyyy-MM-dd');

  const {
    data: doctors = [],
    isError: isDoctorsError,
    isLoading: isDoctorsLoading,
  } = useAdminDoctorOptions();
  const reviewMutation = useReviewDoctorLeave();
  const affectedQuery = useAffectedAppointmentsForLeave(
    reviewTarget?.id,
    Boolean(reviewTarget && reviewAction === 'approve'),
  );

  const { data, isLoading } = useAdminDoctorLeaves({
    from,
    to,
    page: '0',
    size: '300',
    ...(doctorFilter !== '__all__' ? { doctorId: doctorFilter } : {}),
    ...(statusFilter !== '__all__' ? { status: statusFilter } : {}),
  });

  const leaves = useMemo(() => data?.items ?? [], [data?.items]);

  const filteredLeaves = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    if (!keyword) return leaves;

    return leaves.filter((leave) =>
      [leave.doctorName, leave.reason, leave.reviewNote]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(keyword)),
    );
  }, [leaves, search]);

  const pendingLeaves = useMemo(
    () => filteredLeaves.filter((leave) => leave.status === 'PENDING').sort(comparePendingLeaves),
    [filteredLeaves],
  );

  const handledLeaves = useMemo(
    () => filteredLeaves.filter((leave) => leave.status !== 'PENDING').sort(compareHandledLeaves),
    [filteredLeaves],
  );

  const stats = useMemo(() => {
    const byStatus = {
      PENDING: 0,
      APPROVED: 0,
      REJECTED: 0,
      CANCELLED: 0,
    } satisfies Record<LeaveRequest['status'], number>;

    filteredLeaves.forEach((leave) => {
      byStatus[leave.status] += 1;
    });

    return {
      totalDays: countUniqueLeaveDays(filteredLeaves, ['PENDING', 'APPROVED']),
      ...byStatus,
    };
  }, [filteredLeaves]);

  const resetLeaveSectionPages = () => {
    setPendingPage(0);
    setHandledPage(0);
  };

  const handleDoctorFilterChange = (value: string) => {
    resetLeaveSectionPages();
    setDoctorFilter(value);
  };

  const handleStatusFilterChange = (value: string) => {
    resetLeaveSectionPages();
    setStatusFilter(value as '__all__' | LeaveRequest['status']);
  };

  const handleSearchChange = (value: string) => {
    resetLeaveSectionPages();
    setSearch(value);
  };

  const handleMonthChange = (updater: (month: Date) => Date) => {
    resetLeaveSectionPages();
    setCurrentMonth((prev) => updater(prev));
  };

  const openReview = (leave: LeaveRequest, action: 'approve' | 'reject') => {
    setReviewTarget(leave);
    setReviewAction(action);
    setReviewNote(leave.reviewNote ?? '');
  };

  const closeReview = () => {
    setReviewTarget(null);
    setReviewNote('');
    setReviewAction('approve');
  };

  const submitReview = async (enableRecoveryFlow?: boolean) => {
    if (!reviewTarget) return;
    const body = {
      ...(reviewNote.trim() ? { reviewNote: reviewNote.trim() } : {}),
      ...(reviewAction === 'approve' && typeof enableRecoveryFlow === 'boolean'
        ? {
            enableRecoveryFlow,
            resolveConflictsWithRecovery: enableRecoveryFlow,
          }
        : {}),
    };
    const result = await reviewMutation.mutateAsync({
      id: reviewTarget.id,
      action: reviewAction,
      body: Object.keys(body).length ? body : undefined,
    });
    if (result.recoverySummary) setLastRecoverySummary(result.recoverySummary);
    closeReview();
  };

  const monthLabel = format(currentMonth, isEn ? 'MMMM yyyy' : "'Tháng' M 'năm' yyyy", { locale });

  return (
    <div className="space-y-6">
      <PageHeader
        title={isEn ? 'Doctor leave management' : 'Quản lý nghỉ phép bác sĩ'}
        description={
          isEn
            ? 'Review leave requests in a lightweight list view so operations can approve or reject quickly without loading a heavy calendar.'
            : 'Duyệt đơn nghỉ theo giao diện danh sách nhẹ hơn để bộ phận vận hành xử lý nhanh mà không phải tải lịch nặng.'
        }
      />

      <Card className="border-border/70 shadow-sm">
        <CardContent className="grid gap-4 p-5 lg:grid-cols-[minmax(0,260px)_minmax(0,220px)_minmax(0,1fr)_auto]">
          <div>
            <p className="mb-1.5 text-sm font-medium text-foreground">{isEn ? 'Doctor' : 'Bác sĩ'}</p>
            <Select value={doctorFilter} onValueChange={handleDoctorFilterChange}>
              <SelectTrigger className="h-11">
                <SelectValue placeholder={isEn ? 'All doctors' : 'Tất cả bác sĩ'} />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="__all__">{isEn ? 'All doctors' : 'Tất cả bác sĩ'}</SelectItem>
                {isDoctorsError ? (
                  <SelectItem value="__doctor_options_error__" disabled>
                    Không tải được danh sách bác sĩ
                  </SelectItem>
                ) : !isDoctorsLoading && doctors.length === 0 ? (
                  <SelectItem value="__doctor_options_empty__" disabled>
                    Không có bác sĩ phù hợp.
                  </SelectItem>
                ) : null}
                {doctors.map((doctor) => (
                  <SelectItem key={doctor.id} value={doctor.id}>
                    {[doctor.fullName, getInternalDoctorOptionNote(doctor, isEn)]
                      .filter(Boolean)
                      .join(' · ')}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {isDoctorsError ? (
              <p className="mt-1 text-xs text-destructive">
                Không tải được danh sách bác sĩ. Vui lòng thử lại.
              </p>
            ) : !isDoctorsLoading && doctors.length === 0 ? (
              <p className="mt-1 text-xs text-muted-foreground">
                Không có bác sĩ phù hợp.
              </p>
            ) : null}
          </div>

          <div>
            <p className="mb-1.5 text-sm font-medium text-foreground">{isEn ? 'Status' : 'Trạng thái'}</p>
            <Select value={statusFilter} onValueChange={handleStatusFilterChange}>
              <SelectTrigger className="h-11">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="__all__">{isEn ? 'All statuses' : 'Tất cả trạng thái'}</SelectItem>
                {(Object.keys(LEAVE_STATUS_STYLES) as LeaveRequest['status'][]).map((status) => (
                  <SelectItem key={status} value={status}>
                    {getLeaveStatusLabel(status, isEn)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div>
            <p className="mb-1.5 text-sm font-medium text-foreground">{isEn ? 'Search' : 'Tìm kiếm'}</p>
            <div className="relative">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                value={search}
                onChange={(event) => handleSearchChange(event.target.value)}
                className="h-11 pl-9"
                placeholder={
                  isEn ? 'Search doctor name, reason, or review note' : 'Tìm theo tên bác sĩ, lý do, hoặc ghi chú duyệt'
                }
              />
            </div>
          </div>

          <div className="min-w-[210px]">
            <p className="mb-1.5 text-sm font-medium text-foreground">{isEn ? 'Month' : 'Tháng'}</p>
            <div className="flex items-center gap-2">
              <Button variant="outline" size="icon" onClick={() => handleMonthChange((prev) => subMonths(prev, 1))}>
                <ChevronLeft className="h-4 w-4" />
              </Button>
              <Button variant="outline" className="flex-1 justify-center" onClick={() => handleMonthChange(() => startOfMonth(new Date()))}>
                {isEn ? 'Today' : 'Hôm nay'}
              </Button>
              <Button variant="outline" size="icon" onClick={() => handleMonthChange((prev) => addMonths(prev, 1))}>
                <ChevronRight className="h-4 w-4" />
              </Button>
            </div>
            <p className="mt-2 text-sm font-medium text-foreground">{monthLabel}</p>
          </div>
        </CardContent>
      </Card>

      <div className="grid gap-4 md:grid-cols-4">
        <StatCard icon={CalendarDays} label={isEn ? 'Leave days in month' : 'Ngày nghỉ trong tháng'} value={stats.totalDays} tone="text-primary" />
        <StatCard icon={Filter} label={isEn ? 'Pending review' : 'Đơn chờ duyệt'} value={stats.PENDING} tone={statusTextClasses.warning} />
        <StatCard icon={ShieldCheck} label={isEn ? 'Approved' : 'Đã duyệt'} value={stats.APPROVED} tone={statusTextClasses.success} />
        <StatCard icon={XCircle} label={isEn ? 'Rejected / cancelled' : 'Từ chối / hủy'} value={stats.REJECTED + stats.CANCELLED} tone={statusTextClasses.destructive} />
      </div>

      {lastRecoverySummary ? (
        <Card className="border-success/20 bg-success/5 shadow-sm">
          <CardContent className="grid gap-3 p-4 text-sm sm:grid-cols-4">
            <RecoverySummaryItem label="Lịch bị ảnh hưởng" value={lastRecoverySummary.affectedAppointments} />
            <RecoverySummaryItem label="Slot đã giữ" value={lastRecoverySummary.slotHoldsCreated} />
            <RecoverySummaryItem label="Email đã queue" value={lastRecoverySummary.emailsQueued} />
            <RecoverySummaryItem label="Cần staff xử lý" value={lastRecoverySummary.staffFollowUpRequired} />
          </CardContent>
        </Card>
      ) : null}

      <div className="grid gap-6 xl:grid-cols-2">
        <LeaveListSection
          title={isEn ? 'Needs review' : 'Đơn cần duyệt'}
          description={
            isEn
              ? 'Approve only the requests that should block work schedules.'
              : 'Chỉ duyệt những đơn thực sự cần chặn lịch làm việc.'
          }
          emptyText={
            isEn ? 'No pending request matches the current filters.' : 'Không có đơn chờ duyệt nào khớp bộ lọc hiện tại.'
          }
          leaves={pendingLeaves}
          page={pendingPage}
          onPageChange={setPendingPage}
          variant="pending"
          isEn={isEn}
          onApprove={(leave) => openReview(leave, 'approve')}
          onReject={(leave) => openReview(leave, 'reject')}
        />

        <LeaveListSection
          title={isEn ? 'Processed / cancelled' : 'Đã xử lý / đã hủy'}
          description={
            isEn
              ? 'Review approved, rejected, and cancelled requests without loading a calendar.'
              : 'Theo dõi các đơn đã duyệt, từ chối, hoặc đã hủy mà không cần mở lịch.'
          }
          emptyText={
            isEn ? 'No processed or cancelled request matches the current filters.' : 'Không có đơn đã xử lý hoặc đã hủy nào khớp bộ lọc hiện tại.'
          }
          leaves={handledLeaves}
          page={handledPage}
          onPageChange={setHandledPage}
          variant="handled"
          isEn={isEn}
        />
      </div>

      <Dialog open={!!reviewTarget} onOpenChange={(open) => !open && closeReview()}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>
              {reviewAction === 'approve'
                ? isEn
                  ? 'Approve leave request'
                  : 'Duyệt đơn nghỉ phép'
                : isEn
                  ? 'Reject leave request'
                  : 'Từ chối đơn nghỉ phép'}
            </DialogTitle>
          </DialogHeader>

          <div className="space-y-4">
            {reviewTarget && (
              <div className="rounded-2xl border bg-muted/20 p-4 text-sm text-muted-foreground">
                <p className="font-medium text-foreground">{reviewTarget.doctorName}</p>
                <p className="mt-1">
                  {reviewTarget.startDate === reviewTarget.endDate
                    ? reviewTarget.startDate
                    : `${reviewTarget.startDate} → ${reviewTarget.endDate}`}
                </p>
                <p>{getLeaveSessionSummary(reviewTarget, isEn)}</p>
              </div>
            )}
            {reviewAction === 'approve' && reviewTarget ? (
              <AffectedAppointmentsSection
                appointments={affectedQuery.data ?? []}
                affectedCount={affectedQuery.data?.length || reviewTarget.affectedAppointmentCount || 0}
                isLoading={affectedQuery.isLoading}
                isError={affectedQuery.isError}
              />
            ) : null}
            <div>
              <label className="mb-1.5 block text-sm font-medium text-foreground">
                {isEn ? 'Review note' : 'Ghi chú duyệt'}
              </label>
              <Textarea
                rows={4}
                value={reviewNote}
                onChange={(event) => setReviewNote(event.target.value)}
                placeholder={
                  reviewAction === 'approve'
                    ? isEn
                      ? 'Optional note for the doctor or operations timeline.'
                      : 'Ghi chú thêm cho bác sĩ hoặc mốc vận hành.'
                    : isEn
                      ? 'Explain why the request cannot be approved.'
                      : 'Giải thích lý do không thể duyệt đơn này.'
                }
              />
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={closeReview}>{isEn ? 'Cancel' : 'Hủy thao tác'}</Button>
            {reviewAction === 'approve' && (affectedQuery.data?.length || reviewTarget?.affectedAppointmentCount) ? (
              <>
                <Button
                  variant="outline"
                  onClick={() => void submitReview(false)}
                  disabled={reviewMutation.isPending || affectedQuery.isLoading}
                >
                  Để lễ tân xử lý thủ công
                </Button>
                <Button
                  onClick={() => void submitReview(true)}
                  disabled={reviewMutation.isPending || affectedQuery.isLoading}
                >
                  Kích hoạt hỗ trợ dời lịch
                </Button>
              </>
            ) : (
              <Button
                onClick={() => void submitReview()}
                disabled={reviewMutation.isPending}
                variant={reviewAction === 'approve' ? 'default' : 'destructive'}
              >
                {reviewAction === 'approve'
                  ? isEn
                    ? 'Confirm approval'
                    : 'Xác nhận duyệt'
                  : isEn
                    ? 'Confirm rejection'
                    : 'Xác nhận từ chối'}
              </Button>
            )}
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function AffectedAppointmentsSection({
  appointments,
  affectedCount,
  isLoading,
  isError,
}: {
  appointments: DoctorCancellationAffectedAppointment[];
  affectedCount: number;
  isLoading: boolean;
  isError: boolean;
}) {
  if (isLoading) {
    return (
      <div className="rounded-2xl border bg-muted/20 p-4 text-sm text-muted-foreground">
        Đang kiểm tra lịch hẹn bị ảnh hưởng...
      </div>
    );
  }

  if (!affectedCount && !appointments.length) return null;

  return (
    <div className="rounded-2xl border border-warning/25 bg-warning/5 p-4">
      <div className="mb-3 flex items-start gap-3">
        <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-warning/10 text-warning">
          <MessageSquareText className="h-4 w-4" />
        </span>
        <div>
          <p className="text-sm font-semibold text-foreground">
            Khoảng nghỉ này ảnh hưởng đến {affectedCount} lịch hẹn.
          </p>
          <p className="mt-1 text-xs leading-5 text-muted-foreground">
            Khi kích hoạt hỗ trợ dời lịch, hệ thống sẽ giữ slot thay thế và gửi email để bệnh nhân phản hồi.
          </p>
          {isError ? (
            <p className="mt-1 text-xs text-warning">
              Không tải được danh sách chi tiết, vẫn có thể duyệt và để lễ tân xử lý theo dữ liệu backend.
            </p>
          ) : null}
        </div>
      </div>

      {appointments.length ? (
        <div className="max-h-64 space-y-2 overflow-y-auto pr-1">
          {appointments.map((appointment) => (
            <div key={appointment.id} className="rounded-xl border bg-background px-3 py-2 text-sm">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="font-medium text-foreground">{appointment.patientFullName}</p>
                {appointment.status ? <Badge variant="outline">{appointment.status}</Badge> : null}
              </div>
              <p className="mt-1 text-xs text-muted-foreground">
                {appointment.doctorName || 'Bác sĩ'} · {appointment.specialtyName || 'Chuyên khoa'} ·{' '}
                {appointment.visitDate || '-'} {appointment.slotStart || ''}
                {appointment.slotEnd ? ` - ${appointment.slotEnd}` : ''}
              </p>
              <p className="mt-1 text-xs text-muted-foreground">
                {[appointment.patientPhone, appointment.patientEmail].filter(Boolean).join(' · ') || 'Chưa có liên hệ'}
              </p>
            </div>
          ))}
        </div>
      ) : null}
    </div>
  );
}

function RecoverySummaryItem({ label, value }: { label: string; value?: number }) {
  return (
    <div>
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="mt-1 text-lg font-semibold text-foreground">{value ?? 0}</p>
    </div>
  );
}

function LeaveListSection({
  title,
  description,
  emptyText,
  leaves,
  page,
  onPageChange,
  variant,
  isEn,
  onApprove,
  onReject,
}: {
  title: string;
  description: string;
  emptyText: string;
  leaves: LeaveRequest[];
  page: number;
  onPageChange: (page: number) => void;
  variant: 'pending' | 'handled';
  isEn: boolean;
  onApprove?: (leave: LeaveRequest) => void;
  onReject?: (leave: LeaveRequest) => void;
}) {
  const totalCount = leaves.length;
  const pageCount = Math.max(1, Math.ceil(totalCount / LEAVE_SECTION_PAGE_SIZE));
  const safePage = Math.min(Math.max(page, 0), pageCount - 1);
  const startIndex = safePage * LEAVE_SECTION_PAGE_SIZE;
  const visibleLeaves = leaves.slice(startIndex, startIndex + LEAVE_SECTION_PAGE_SIZE);
  const rangeStart = totalCount === 0 ? 0 : startIndex + 1;
  const rangeEnd = Math.min(startIndex + LEAVE_SECTION_PAGE_SIZE, totalCount);
  const hiddenPendingCount = variant === 'pending' ? totalCount - rangeEnd : 0;
  const hasPagination = totalCount > LEAVE_SECTION_PAGE_SIZE;
  const previousLabel = isEn ? 'Previous' : 'Trước';
  const nextLabel = variant === 'pending'
    ? isEn
      ? 'Next'
      : 'Xem tiếp'
    : isEn
      ? 'Next'
      : 'Sau';

  return (
    <Card className="border-border/70 shadow-sm">
      <CardHeader className="border-b bg-muted/30 pb-4">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <CardTitle className="text-lg">{title} ({totalCount})</CardTitle>
            <p className="mt-1 text-sm text-muted-foreground">{description}</p>
          </div>
          {totalCount > 0 ? (
            <div className="flex flex-col items-start gap-2 sm:items-end">
              <p className="text-xs font-medium text-muted-foreground">
                {isEn
                  ? `Showing ${rangeStart}-${rangeEnd} / ${totalCount}`
                  : `Hiển thị ${rangeStart}–${rangeEnd} / ${totalCount}`}
              </p>
              {hasPagination ? (
                <div className="flex items-center gap-2">
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    className="h-8 px-2.5 text-xs"
                    disabled={safePage === 0}
                    onClick={() => onPageChange(safePage - 1)}
                  >
                    {previousLabel}
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    className="h-8 px-2.5 text-xs"
                    disabled={safePage >= pageCount - 1}
                    onClick={() => onPageChange(safePage + 1)}
                  >
                    {nextLabel}
                  </Button>
                </div>
              ) : null}
            </div>
          ) : null}
        </div>
      </CardHeader>
      <CardContent className="space-y-4 p-5">
        {totalCount === 0 ? (
          <p className="text-sm text-muted-foreground">{emptyText}</p>
        ) : (
          <>
            {hiddenPendingCount > 0 ? (
              <p className="rounded-md border border-warning/25 bg-warning/5 px-3 py-2 text-xs font-medium text-warning">
                {isEn
                  ? `${hiddenPendingCount} pending request${hiddenPendingCount === 1 ? '' : 's'} remaining`
                  : `Còn ${hiddenPendingCount} đơn cần duyệt`}
              </p>
            ) : null}

            {visibleLeaves.map((leave) => {
              const config = LEAVE_STATUS_STYLES[leave.status];
              return (
                <div key={leave.id} className="rounded-2xl border bg-background p-4 shadow-sm">
                  <div className="mb-3 flex flex-wrap items-center gap-2">
                    <Badge className={cn('rounded-full', config.badgeClass)}>
                      {getLeaveStatusLabel(leave.status, isEn)}
                    </Badge>
                    <Badge variant="outline" className="rounded-full">
                      {leave.doctorName}
                    </Badge>
                  </div>

                  <div className="space-y-2 text-sm text-muted-foreground">
                    <div className="flex items-center justify-between gap-3">
                      <span>{isEn ? 'Range' : 'Khoảng nghỉ'}</span>
                      <span className="font-medium text-foreground">
                        {leave.startDate === leave.endDate
                          ? leave.startDate
                          : `${leave.startDate} → ${leave.endDate}`}
                      </span>
                    </div>

                    <div className="flex items-center justify-between gap-3">
                      <span>{isEn ? 'Sessions' : 'Buổi nghỉ'}</span>
                      <span className="font-medium text-foreground">
                        {getLeaveSessionSummary(leave, isEn)}
                      </span>
                    </div>

                    <div className="rounded-xl border bg-muted/25 px-3 py-2 text-foreground">
                      {leave.reason || (isEn ? 'No reason provided.' : 'Không có lý do.')}
                    </div>

                    {leave.reviewedAt && (
                      <div className="rounded-xl border bg-muted/20 px-3 py-2 text-xs leading-5">
                        <p>
                          <span className="font-medium text-foreground">
                            {isEn ? 'Reviewed by:' : 'Người duyệt:'}
                          </span>{' '}
                          {leave.reviewedByName || '-'}
                        </p>
                        <p>
                          <span className="font-medium text-foreground">
                            {isEn ? 'Reviewed at:' : 'Duyệt lúc:'}
                          </span>{' '}
                          {leave.reviewedAt}
                        </p>
                        {leave.reviewNote && (
                          <p>
                            <span className="font-medium text-foreground">
                              {isEn ? 'Review note:' : 'Ghi chú duyệt:'}
                            </span>{' '}
                            {leave.reviewNote}
                          </p>
                        )}
                      </div>
                    )}
                  </div>

                  {leave.status === 'PENDING' && onApprove && onReject && (
                    <div className="mt-4 grid gap-2 sm:grid-cols-2">
                      <Button onClick={() => onApprove(leave)}>
                        <CheckCircle2 className="mr-2 h-4 w-4" />
                        {isEn ? 'Approve' : 'Duyệt'}
                      </Button>
                      <Button
                        variant="outline"
                        className="border-destructive/20 text-destructive hover:bg-destructive/5"
                        onClick={() => onReject(leave)}
                      >
                        <XCircle className="mr-2 h-4 w-4" />
                        {isEn ? 'Reject' : 'Từ chối'}
                      </Button>
                    </div>
                  )}
                </div>
              );
            })}
          </>
        )}
      </CardContent>
    </Card>
  );
}

function StatCard({
  icon: Icon,
  label,
  value,
  tone,
}: {
  icon: typeof CalendarDays;
  label: string;
  value: number;
  tone?: string;
}) {
  return (
    <Card className="border-border/70 shadow-sm">
      <CardContent className="flex items-center justify-between p-5">
        <div>
          <p className="text-sm text-muted-foreground">{label}</p>
          <p className="mt-2 text-2xl font-semibold tracking-tight text-foreground">{value}</p>
        </div>
        <div className={cn('rounded-2xl bg-muted/50 p-3', tone)}>
          <Icon className="h-5 w-5" />
        </div>
      </CardContent>
    </Card>
  );
}
