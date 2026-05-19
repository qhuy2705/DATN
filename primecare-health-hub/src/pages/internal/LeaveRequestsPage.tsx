import { useMemo, useState } from 'react';
import {
  addMonths,
  eachDayOfInterval,
  endOfMonth,
  endOfWeek,
  format,
  isSameDay,
  isSameMonth,
  isToday,
  startOfMonth,
  startOfWeek,
  subMonths,
} from 'date-fns';
import { enUS, vi } from 'date-fns/locale';
import type { DateRange } from 'react-day-picker';
import {
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  Clock3,
  FileText,
  Plus,
  ShieldCheck,
  XCircle,
} from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { PageHeader } from '@/components/PageHeader';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Calendar } from '@/components/ui/calendar';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { cn } from '@/lib/utils';
import { statusTextClasses } from '@/lib/status-style-classes';
import {
  useCancelDoctorLeaveRequest,
  useCreateDoctorLeaveRequest,
  useDoctorLeaveRequests,
} from '@/hooks/use-doctor-data';
import {
  CALENDAR_SESSIONS,
  LEAVE_STATUS_STYLES,
  countUniqueLeaveDays,
  getLeaveSessionSummary,
  getLeaveStatusLabel,
  getLeavesForDate,
  getSessionLabel,
  toApiSessionValue,
  type SessionKey,
} from '@/lib/doctor-leave-utils';
import type { LeaveRequest } from '@/types/api';

const weekdayLabelsVi = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'];
const weekdayLabelsEn = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

export default function LeaveRequestsPage() {
  const { t, i18n } = useTranslation();
  const isEn = i18n.language?.startsWith('en');
  const locale = isEn ? enUS : vi;

  const [dialogOpen, setDialogOpen] = useState(false);
  const [currentMonth, setCurrentMonth] = useState(() => startOfMonth(new Date()));
  const [selectedDate, setSelectedDate] = useState(() => new Date());
  const [range, setRange] = useState<DateRange | undefined>();
  const [startSession, setStartSession] = useState<SessionKey>('MORNING');
  const [endSession, setEndSession] = useState<SessionKey>('AFTERNOON');
  const [reason, setReason] = useState('');

  const from = format(startOfMonth(currentMonth), 'yyyy-MM-dd');
  const to = format(endOfMonth(currentMonth), 'yyyy-MM-dd');

  const { data, isLoading } = useDoctorLeaveRequests({
    from,
    to,
    page: '0',
    size: '200',
  });
  const createLeave = useCreateDoctorLeaveRequest();
  const cancelLeave = useCancelDoctorLeaveRequest();

  const leaves = useMemo(() => data?.items ?? [], [data?.items]);
  const calendarDays = useMemo(() => {
    const start = startOfWeek(startOfMonth(currentMonth), { weekStartsOn: 1 });
    const end = endOfWeek(endOfMonth(currentMonth), { weekStartsOn: 1 });
    return eachDayOfInterval({ start, end });
  }, [currentMonth]);

  const stats = useMemo(() => {
    const byStatus = {
      PENDING: 0,
      APPROVED: 0,
      REJECTED: 0,
      CANCELLED: 0,
    } satisfies Record<LeaveRequest['status'], number>;

    leaves.forEach((leave) => {
      byStatus[leave.status] += 1;
    });

    return {
      totalDays: countUniqueLeaveDays(leaves, ['PENDING', 'APPROVED']),
      ...byStatus,
    };
  }, [leaves]);

  const selectedDateKey = format(selectedDate, 'yyyy-MM-dd');
  const selectedDayLeaves = useMemo(
    () =>
      getLeavesForDate(leaves, selectedDateKey).sort((a, b) => {
        const priority: Record<LeaveRequest['status'], number> = {
          PENDING: 0,
          APPROVED: 1,
          REJECTED: 2,
          CANCELLED: 3,
        };
        return priority[a.status] - priority[b.status];
      }),
    [leaves, selectedDateKey],
  );

  const resetDialog = () => {
    setRange(undefined);
    setStartSession('MORNING');
    setEndSession('AFTERNOON');
    setReason('');
  };

  const closeDialog = (open: boolean) => {
    setDialogOpen(open);
    if (!open) resetDialog();
  };

  const submit = async () => {
    if (!range?.from || !range.to) {
      toast.error(isEn ? 'Please choose a leave date range' : 'Vui lòng chọn khoảng ngày nghỉ');
      return;
    }

    if (
      format(range.from, 'yyyy-MM-dd') === format(range.to, 'yyyy-MM-dd') &&
      startSession === 'AFTERNOON' &&
      endSession === 'MORNING'
    ) {
      toast.error(
        isEn
          ? 'The start session must be before the end session.'
          : 'Buổi bắt đầu phải trước hoặc bằng buổi kết thúc.',
      );
      return;
    }

    await createLeave.mutateAsync({
      startDate: format(range.from, 'yyyy-MM-dd'),
      endDate: format(range.to, 'yyyy-MM-dd'),
      startSession: toApiSessionValue(startSession),
      endSession: toApiSessionValue(endSession),
      reason: reason.trim() || undefined,
    });
    closeDialog(false);
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title={t('modules.leaveRequests.title')}
        description={
          isEn
            ? 'Plan leave requests in a calendar-first workspace and track each approval clearly.'
            : 'Lên kế hoạch nghỉ phép theo giao diện lịch hiện đại và theo dõi trạng thái phê duyệt rõ ràng.'
        }
        actions={
          <Button onClick={() => setDialogOpen(true)}>
            <Plus className="mr-2 h-4 w-4" />
            {isEn ? 'Create leave request' : 'Tạo đơn nghỉ phép'}
          </Button>
        }
      />

      <div className="grid gap-4 md:grid-cols-4">
        <StatCard icon={CalendarDays} label={isEn ? 'Leave days planned' : 'Ngày nghỉ đã lên kế hoạch'} value={stats.totalDays} tone="text-primary" />
        <StatCard icon={Clock3} label={isEn ? 'Pending requests' : 'Đơn chờ duyệt'} value={stats.PENDING} tone={statusTextClasses.warning} />
        <StatCard icon={ShieldCheck} label={isEn ? 'Approved requests' : 'Đơn đã duyệt'} value={stats.APPROVED} tone={statusTextClasses.success} />
        <StatCard icon={XCircle} label={isEn ? 'Rejected / cancelled' : 'Đơn từ chối / đã hủy'} value={stats.REJECTED + stats.CANCELLED} tone={statusTextClasses.destructive} />
      </div>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1.65fr)_380px]">
        <Card className="border-border/70 shadow-sm">
          <CardHeader className="border-b bg-muted/30 pb-4">
            <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
              <div>
                <CardTitle className="text-lg">
                  {isEn ? 'Monthly leave calendar' : 'Lịch nghỉ phép theo tháng'}
                </CardTitle>
                <p className="mt-1 text-sm text-muted-foreground">
                  {isEn
                    ? 'Every request appears directly on the calendar so you can review the month at a glance.'
                    : 'Mỗi đơn nghỉ sẽ hiện trực tiếp trên lịch để bạn xem nhanh toàn bộ tháng.'}
                </p>
              </div>

              <div className="flex flex-col gap-3 md:flex-row md:items-center">
                <div className="flex items-center gap-2">
                  <Button variant="outline" size="icon" onClick={() => setCurrentMonth((prev) => subMonths(prev, 1))}>
                    <ChevronLeft className="h-4 w-4" />
                  </Button>
                  <Button variant="outline" onClick={() => setCurrentMonth(startOfMonth(new Date()))}>
                    {isEn ? 'Today' : 'Hôm nay'}
                  </Button>
                  <Button variant="outline" size="icon" onClick={() => setCurrentMonth((prev) => addMonths(prev, 1))}>
                    <ChevronRight className="h-4 w-4" />
                  </Button>
                </div>

                <div className="text-left md:text-right">
                  <p className="text-xs font-medium uppercase tracking-[0.22em] text-muted-foreground">
                    {isEn ? 'Display month' : 'Tháng hiển thị'}
                  </p>
                  <p className="text-2xl font-semibold tracking-tight text-foreground">
                    {format(currentMonth, isEn ? 'MMMM yyyy' : "'Tháng' M 'năm' yyyy", { locale })}
                  </p>
                </div>
              </div>
            </div>

            <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
              {(Object.keys(LEAVE_STATUS_STYLES) as LeaveRequest['status'][]).map((status) => {
                const config = LEAVE_STATUS_STYLES[status];
                return (
                  <div key={status} className="inline-flex items-center gap-2 rounded-full border bg-background px-3 py-1.5">
                    <span className={cn('h-2.5 w-2.5 rounded-full', config.dotClass)} />
                    <span>{isEn ? config.labelEn : config.labelVi}</span>
                  </div>
                );
              })}
            </div>
          </CardHeader>
          <CardContent className="p-0">
            <div className="grid grid-cols-7 border-b bg-muted/20">
              {(isEn ? weekdayLabelsEn : weekdayLabelsVi).map((label) => (
                <div
                  key={label}
                  className="border-r px-3 py-3 text-center text-xs font-semibold uppercase tracking-wide text-muted-foreground last:border-r-0"
                >
                  {label}
                </div>
              ))}
            </div>

            {isLoading ? (
              <div className="flex min-h-[420px] items-center justify-center p-8 text-center text-muted-foreground">
                {isEn ? 'Loading leave requests...' : 'Đang tải đơn nghỉ phép...'}
              </div>
            ) : (
              <div className="grid grid-cols-7">
                {calendarDays.map((day) => {
                  const dateKey = format(day, 'yyyy-MM-dd');
                  const dayLeaves = getLeavesForDate(leaves, dateKey);
                  const isSelected = isSameDay(day, selectedDate);
                  const inCurrentMonth = isSameMonth(day, currentMonth);

                  return (
                    <button
                      type="button"
                      key={dateKey}
                      onClick={() => setSelectedDate(day)}
                      className={cn(
                        'group min-h-[144px] border-r border-b p-3 text-left transition-all last:border-r-0',
                        inCurrentMonth ? 'bg-background hover:bg-muted/35' : 'bg-muted/15 text-muted-foreground/70',
                        isSelected && 'bg-primary/5 ring-1 ring-inset ring-primary',
                      )}
                    >
                      <div className="flex items-start justify-between gap-2">
                        <div
                          className={cn(
                            'flex h-8 w-8 items-center justify-center rounded-full text-sm font-semibold',
                            isToday(day) && 'bg-primary text-primary-foreground',
                            !isToday(day) && isSelected && 'bg-primary/10 text-primary',
                          )}
                        >
                          {format(day, 'd')}
                        </div>
                        {dayLeaves.length > 0 && (
                          <Badge variant="secondary" className="rounded-full bg-foreground/[0.06] text-[11px] font-medium text-foreground/80">
                            {dayLeaves.length} {isEn ? 'request' : 'đơn'}
                          </Badge>
                        )}
                      </div>

                      <div className="mt-3 space-y-2">
                        {dayLeaves.slice(0, 2).map((leave) => {
                          const config = LEAVE_STATUS_STYLES[leave.status];
                          return (
                            <div
                              key={leave.id}
                              className={cn('rounded-xl border px-2.5 py-2 text-xs shadow-sm', config.badgeClass)}
                            >
                              <div className="flex items-center gap-2 font-medium">
                                <span className={cn('h-2 w-2 rounded-full', config.dotClass)} />
                                <span>{isEn ? config.shortEn : config.shortVi}</span>
                              </div>
                              <div className="mt-1 line-clamp-1 text-[11px] opacity-85">
                                {getLeaveSessionSummary(leave, isEn)}
                              </div>
                            </div>
                          );
                        })}

                        {dayLeaves.length > 2 && (
                          <div className="rounded-xl border border-dashed px-2.5 py-2 text-[11px] text-muted-foreground">
                            +{dayLeaves.length - 2} {isEn ? 'more requests' : 'đơn khác'}
                          </div>
                        )}
                      </div>
                    </button>
                  );
                })}
              </div>
            )}
          </CardContent>
        </Card>

        <Card className="h-fit border-border/70 shadow-sm xl:sticky xl:top-6">
          <CardHeader className="border-b bg-muted/30 pb-4">
            <div className="flex items-center justify-between gap-3">
              <div>
                <p className="text-xs font-medium uppercase tracking-[0.2em] text-muted-foreground">
                  {isEn ? 'Selected date' : 'Ngày được chọn'}
                </p>
                <CardTitle className="mt-1 text-xl">
                  {format(selectedDate, isEn ? 'EEEE, dd MMM yyyy' : 'EEEE, dd/MM/yyyy', { locale })}
                </CardTitle>
              </div>
              <div className="rounded-2xl border bg-background p-3 shadow-sm">
                <FileText className="h-5 w-5 text-primary" />
              </div>
            </div>
          </CardHeader>
          <CardContent className="space-y-4 p-5">
            {selectedDayLeaves.length === 0 ? (
              <p className="text-sm text-muted-foreground">
                {isEn
                  ? 'No leave request covers this date. You can create a new one from the button above.'
                  : 'Chưa có đơn nghỉ nào cho ngày này. Bạn có thể tạo đơn mới từ nút phía trên.'}
              </p>
            ) : (
              selectedDayLeaves.map((leave) => {
                const config = LEAVE_STATUS_STYLES[leave.status];
                return (
                  <div key={leave.id} className="rounded-2xl border bg-background p-4 shadow-sm">
                    <div className="mb-3 flex flex-wrap items-center gap-2">
                      <Badge className={cn('rounded-full', config.badgeClass)}>
                        {getLeaveStatusLabel(leave.status, isEn)}
                      </Badge>
                      <Badge variant="outline" className="rounded-full">
                        {getLeaveSessionSummary(leave, isEn)}
                      </Badge>
                    </div>

                    <div className="space-y-2 text-sm text-muted-foreground">
                      <div className="flex items-center justify-between gap-3">
                        <span>{isEn ? 'Date range' : 'Khoảng ngày'}</span>
                        <span className="font-medium text-foreground">
                          {leave.startDate === leave.endDate
                            ? leave.startDate
                            : `${leave.startDate} → ${leave.endDate}`}
                        </span>
                      </div>
                      <div className="flex items-center justify-between gap-3">
                        <span>{isEn ? 'Coverage today' : 'Phạm vi trong ngày'}</span>
                        <span className="font-medium text-foreground">
                          {CALENDAR_SESSIONS.filter((session) =>
                            sessionCovered(leave, selectedDateKey, session),
                          )
                            .map((session) => getSessionLabel(session, isEn))
                            .join(', ')}
                        </span>
                      </div>
                      <div className="rounded-xl border bg-muted/25 px-3 py-2 text-foreground">
                        {leave.reason || (isEn ? 'No note provided.' : 'Không có ghi chú.')}
                      </div>
                      {leave.reviewedAt && (
                        <div className="rounded-xl border bg-muted/20 px-3 py-2 text-xs leading-5">
                          <p>
                            <span className="font-medium text-foreground">{isEn ? 'Reviewed by:' : 'Người duyệt:'}</span>{' '}
                            {leave.reviewedByName || '-'}
                          </p>
                          <p>
                            <span className="font-medium text-foreground">{isEn ? 'Reviewed at:' : 'Duyệt lúc:'}</span>{' '}
                            {leave.reviewedAt}
                          </p>
                          {leave.reviewNote && (
                            <p>
                              <span className="font-medium text-foreground">{isEn ? 'Note:' : 'Ghi chú:'}</span>{' '}
                              {leave.reviewNote}
                            </p>
                          )}
                        </div>
                      )}
                    </div>

                    {leave.status === 'PENDING' && (
                      <Button
                        variant="outline"
                        className="mt-4 w-full border-destructive/20 text-destructive hover:bg-destructive/5"
                        onClick={() => cancelLeave.mutate(leave.id)}
                        disabled={cancelLeave.isPending}
                      >
                        {isEn ? 'Cancel request' : 'Hủy đơn nghỉ'}
                      </Button>
                    )}
                  </div>
                );
              })
            )}
          </CardContent>
        </Card>
      </div>

      <Dialog open={dialogOpen} onOpenChange={closeDialog}>
        <DialogContent className="max-w-4xl overflow-hidden p-0">
          <div className="grid gap-0 lg:grid-cols-[1.25fr_0.85fr]">
            <div className="border-b bg-muted/10 p-5 lg:border-b-0 lg:border-r">
              <DialogHeader>
                <DialogTitle>{isEn ? 'Create leave request' : 'Tạo đơn nghỉ phép'}</DialogTitle>
              </DialogHeader>
              <p className="mt-2 text-sm text-muted-foreground">
                {isEn
                  ? 'Pick a date range directly on the calendar, then define the first and last session.'
                  : 'Chọn khoảng ngày nghỉ trực tiếp trên lịch, sau đó xác định buổi bắt đầu và buổi kết thúc.'}
              </p>
              <div className="mt-4 rounded-2xl border bg-background p-3">
                <Calendar
                  mode="range"
                  selected={range}
                  onSelect={setRange}
                  numberOfMonths={2}
                  disabled={{ before: new Date() }}
                  className="w-full"
                />
              </div>
            </div>

            <div className="p-5">
              <div className="space-y-4">
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="mb-1.5 block text-sm font-medium text-foreground">
                      {isEn ? 'Start session' : 'Buổi bắt đầu'}
                    </label>
                    <Select value={startSession} onValueChange={(value) => setStartSession(value as SessionKey)}>
                      <SelectTrigger>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="MORNING">{isEn ? 'Morning' : 'Sáng'}</SelectItem>
                        <SelectItem value="AFTERNOON">{isEn ? 'Afternoon' : 'Chiều'}</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                  <div>
                    <label className="mb-1.5 block text-sm font-medium text-foreground">
                      {isEn ? 'End session' : 'Buổi kết thúc'}
                    </label>
                    <Select value={endSession} onValueChange={(value) => setEndSession(value as SessionKey)}>
                      <SelectTrigger>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="MORNING">{isEn ? 'Morning' : 'Sáng'}</SelectItem>
                        <SelectItem value="AFTERNOON">{isEn ? 'Afternoon' : 'Chiều'}</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                </div>

                <div>
                  <label className="mb-1.5 block text-sm font-medium text-foreground">
                    {isEn ? 'Reason' : 'Lý do nghỉ'}
                  </label>
                  <Textarea
                    rows={5}
                    value={reason}
                    onChange={(event) => setReason(event.target.value)}
                    placeholder={
                      isEn
                        ? 'Add context so the operations team can review the request faster.'
                        : 'Ghi thêm bối cảnh để bộ phận vận hành duyệt nhanh hơn.'
                    }
                  />
                </div>

                <div className="rounded-2xl border bg-muted/20 p-4 text-sm text-muted-foreground">
                  <p className="font-medium text-foreground">{isEn ? 'Summary' : 'Tóm tắt yêu cầu'}</p>
                  <div className="mt-2 space-y-2">
                    <div className="flex items-center justify-between gap-3">
                      <span>{isEn ? 'Date range' : 'Khoảng ngày'}</span>
                      <span className="font-medium text-foreground">
                        {range?.from && range.to
                          ? range.from.getTime() === range.to.getTime()
                            ? format(range.from, 'dd/MM/yyyy')
                            : `${format(range.from, 'dd/MM/yyyy')} → ${format(range.to, 'dd/MM/yyyy')}`
                          : isEn
                            ? 'Not selected'
                            : 'Chưa chọn'}
                      </span>
                    </div>
                    <div className="flex items-center justify-between gap-3">
                      <span>{isEn ? 'Sessions' : 'Buổi nghỉ'}</span>
                      <span className="font-medium text-foreground">
                        {getSessionLabel(startSession, isEn)} → {getSessionLabel(endSession, isEn)}
                      </span>
                    </div>
                  </div>
                </div>
              </div>

              <DialogFooter className="mt-5">
                <Button variant="outline" onClick={() => closeDialog(false)}>
                  {t('common.cancel')}
                </Button>
                <Button onClick={submit} disabled={createLeave.isPending}>
                  {createLeave.isPending
                    ? isEn
                      ? 'Creating...'
                      : 'Đang tạo...'
                    : isEn
                      ? 'Submit request'
                      : 'Gửi đơn nghỉ'}
                </Button>
              </DialogFooter>
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function sessionCovered(leave: LeaveRequest, dateKey: string, session: SessionKey) {
  const targetOrder = session === 'MORNING' ? 0 : 1;
  const startOrder = leave.startSession === 'AFTERNOON' ? 1 : 0;
  const endOrder = leave.endSession === 'MORNING' ? 0 : 1;

  if (dateKey < leave.startDate || dateKey > leave.endDate) return false;
  if (leave.startDate === leave.endDate) return targetOrder >= startOrder && targetOrder <= endOrder;
  if (dateKey === leave.startDate) return targetOrder >= startOrder;
  if (dateKey === leave.endDate) return targetOrder <= endOrder;
  return true;
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
