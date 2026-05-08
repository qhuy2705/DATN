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
import { useAdminDoctorLeaves, useReviewDoctorLeave } from '@/hooks/use-admin-data';
import { useDoctors } from '@/hooks/use-public-data';
import {
  LEAVE_STATUS_STYLES,
  countUniqueLeaveDays,
  getLeaveSessionSummary,
  getLeaveStatusLabel,
} from '@/lib/doctor-leave-utils';
import { cn } from '@/lib/utils';
import type { LeaveRequest } from '@/types/api';

export default function DoctorLeavesAdminPage() {
  const { i18n } = useTranslation();
  const isEn = i18n.language?.startsWith('en');
  const locale = isEn ? enUS : vi;

  const [currentMonth, setCurrentMonth] = useState(() => startOfMonth(new Date()));
  const [doctorFilter, setDoctorFilter] = useState('__all__');
  const [statusFilter, setStatusFilter] = useState<'__all__' | LeaveRequest['status']>('__all__');
  const [search, setSearch] = useState('');
  const [reviewTarget, setReviewTarget] = useState<LeaveRequest | null>(null);
  const [reviewAction, setReviewAction] = useState<'approve' | 'reject'>('approve');
  const [reviewNote, setReviewNote] = useState('');

  const from = format(startOfMonth(currentMonth), 'yyyy-MM-dd');
  const to = format(endOfMonth(currentMonth), 'yyyy-MM-dd');

  const { data: doctorsPage } = useDoctors();
  const doctors = doctorsPage?.items ?? [];
  const reviewMutation = useReviewDoctorLeave();

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
    const sorted = [...leaves].sort((a, b) => {
      const aPending = a.status === 'PENDING' ? 0 : 1;
      const bPending = b.status === 'PENDING' ? 0 : 1;
      if (aPending !== bPending) return aPending - bPending;
      const aTime = new Date(a.createdAt ?? a.startDate).getTime();
      const bTime = new Date(b.createdAt ?? b.startDate).getTime();
      return bTime - aTime;
    });

    if (!keyword) return sorted;

    return sorted.filter((leave) =>
      [leave.doctorName, leave.reason, leave.reviewNote]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(keyword)),
    );
  }, [leaves, search]);

  const pendingLeaves = useMemo(
    () => filteredLeaves.filter((leave) => leave.status === 'PENDING'),
    [filteredLeaves],
  );

  const handledLeaves = useMemo(
    () => filteredLeaves.filter((leave) => leave.status !== 'PENDING'),
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

  const submitReview = async () => {
    if (!reviewTarget) return;
    await reviewMutation.mutateAsync({
      id: reviewTarget.id,
      action: reviewAction,
      body: reviewNote.trim() ? { reviewNote: reviewNote.trim() } : undefined,
    });
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
            <Select value={doctorFilter} onValueChange={setDoctorFilter}>
              <SelectTrigger className="h-11">
                <SelectValue placeholder={isEn ? 'All doctors' : 'Tất cả bác sĩ'} />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="__all__">{isEn ? 'All doctors' : 'Tất cả bác sĩ'}</SelectItem>
                {doctors.map((doctor) => (
                  <SelectItem key={doctor.id} value={doctor.id}>
                    {doctor.fullName}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div>
            <p className="mb-1.5 text-sm font-medium text-foreground">{isEn ? 'Status' : 'Trạng thái'}</p>
            <Select value={statusFilter} onValueChange={(value) => setStatusFilter(value as '__all__' | LeaveRequest['status'])}>
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
                onChange={(event) => setSearch(event.target.value)}
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
              <Button variant="outline" size="icon" onClick={() => setCurrentMonth((prev) => subMonths(prev, 1))}>
                <ChevronLeft className="h-4 w-4" />
              </Button>
              <Button variant="outline" className="flex-1 justify-center" onClick={() => setCurrentMonth(startOfMonth(new Date()))}>
                {isEn ? 'Today' : 'Hôm nay'}
              </Button>
              <Button variant="outline" size="icon" onClick={() => setCurrentMonth((prev) => addMonths(prev, 1))}>
                <ChevronRight className="h-4 w-4" />
              </Button>
            </div>
            <p className="mt-2 text-sm font-medium text-foreground">{monthLabel}</p>
          </div>
        </CardContent>
      </Card>

      <div className="grid gap-4 md:grid-cols-4">
        <StatCard icon={CalendarDays} label={isEn ? 'Leave days in month' : 'Ngày nghỉ trong tháng'} value={stats.totalDays} tone="text-primary" />
        <StatCard icon={Filter} label={isEn ? 'Pending review' : 'Đơn chờ duyệt'} value={stats.PENDING} tone="text-violet-600" />
        <StatCard icon={ShieldCheck} label={isEn ? 'Approved' : 'Đã duyệt'} value={stats.APPROVED} tone="text-rose-600" />
        <StatCard icon={XCircle} label={isEn ? 'Rejected / cancelled' : 'Từ chối / hủy'} value={stats.REJECTED + stats.CANCELLED} tone="text-slate-600" />
      </div>

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
            isEn ? 'No processed request matches the current filters.' : 'Không có đơn đã xử lý nào khớp bộ lọc hiện tại.'
          }
          leaves={handledLeaves}
          isEn={isEn}
        />
      </div>

      <Dialog open={!!reviewTarget} onOpenChange={(open) => !open && closeReview()}>
        <DialogContent className="max-w-lg">
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
            <Button variant="outline" onClick={closeReview}>{isEn ? 'Cancel' : 'Hủy'}</Button>
            <Button
              onClick={submitReview}
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
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function LeaveListSection({
  title,
  description,
  emptyText,
  leaves,
  isEn,
  onApprove,
  onReject,
}: {
  title: string;
  description: string;
  emptyText: string;
  leaves: LeaveRequest[];
  isEn: boolean;
  onApprove?: (leave: LeaveRequest) => void;
  onReject?: (leave: LeaveRequest) => void;
}) {
  return (
    <Card className="border-border/70 shadow-sm">
      <CardHeader className="border-b bg-muted/30 pb-4">
        <CardTitle className="text-lg">{title}</CardTitle>
        <p className="text-sm text-muted-foreground">{description}</p>
      </CardHeader>
      <CardContent className="space-y-4 p-5">
        {leaves.length === 0 ? (
          <p className="text-sm text-muted-foreground">{emptyText}</p>
        ) : (
          leaves.map((leave) => {
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
          })
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
