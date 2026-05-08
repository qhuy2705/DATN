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
  parseISO,
  startOfMonth,
  startOfWeek,
  subMonths,
} from 'date-fns';
import { vi, enUS } from 'date-fns/locale';
import { CalendarDays, ChevronLeft, ChevronRight, Clock3, Stethoscope } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { PageHeader } from '@/components/PageHeader';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { cn } from '@/lib/utils';
import { useDoctorSchedules } from '@/hooks/use-doctor-data';
import type { DoctorSchedule } from '@/types/api';

type SessionKey = 'MORNING' | 'AFTERNOON';

const SESSION_CONFIG: Record<SessionKey, { labelVi: string; labelEn: string; badgeClass: string; dotClass: string }> = {
  MORNING: {
    labelVi: 'Sáng',
    labelEn: 'Morning',
    badgeClass: 'border-sky-200 bg-sky-50 text-sky-700',
    dotClass: 'bg-sky-500',
  },
  AFTERNOON: {
    labelVi: 'Chiều',
    labelEn: 'Afternoon',
    badgeClass: 'border-amber-200 bg-amber-50 text-amber-700',
    dotClass: 'bg-amber-500',
  },
};

const weekdayLabelsVi = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'];
const weekdayLabelsEn = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

export default function DoctorSchedulesViewPage() {
  const { t, i18n } = useTranslation();
  const isEn = i18n.language?.startsWith('en');
  const locale = isEn ? enUS : vi;
  const [currentMonth, setCurrentMonth] = useState(() => startOfMonth(new Date()));
  const [selectedDate, setSelectedDate] = useState(() => new Date());

  const from = format(startOfMonth(currentMonth), 'yyyy-MM-dd');
  const to = format(endOfMonth(currentMonth), 'yyyy-MM-dd');

  const { data, isLoading } = useDoctorSchedules({
    from,
    to,
    page: '0',
    size: '200',
  });

  const schedules = useMemo(() => data?.items ?? [], [data?.items]);

  const scheduleMap = useMemo(() => {
    const map = new Map<string, Partial<Record<SessionKey, DoctorSchedule>>>();
    schedules.forEach((schedule) => {
      const dateKey = schedule.workDate;
      if (!dateKey) return;
      const current = map.get(dateKey) ?? {};
      if (schedule.session === 'MORNING' || schedule.session === 'AFTERNOON') {
        current[schedule.session] = schedule;
      }
      map.set(dateKey, current);
    });
    return map;
  }, [schedules]);

  const calendarDays = useMemo(() => {
    const start = startOfWeek(startOfMonth(currentMonth), { weekStartsOn: 1 });
    const end = endOfWeek(endOfMonth(currentMonth), { weekStartsOn: 1 });
    return eachDayOfInterval({ start, end });
  }, [currentMonth]);

  const stats = useMemo(() => {
    const uniqueDays = new Set<string>();
    let morningCount = 0;
    let afternoonCount = 0;

    schedules.forEach((schedule) => {
      if (schedule.workDate) uniqueDays.add(schedule.workDate);
      if (schedule.session === 'MORNING') morningCount += 1;
      if (schedule.session === 'AFTERNOON') afternoonCount += 1;
    });

    return {
      workingDays: uniqueDays.size,
      morningCount,
      afternoonCount,
    };
  }, [schedules]);

  const selectedDateKey = format(selectedDate, 'yyyy-MM-dd');
  const selectedDaySchedules = scheduleMap.get(selectedDateKey) ?? {};

  return (
    <div className="space-y-6">
      <PageHeader
        title={t('modules.doctorSchedule.title')}
        description={
          isEn
            ? 'Review your monthly work plan in a clean calendar view.'
            : 'Theo dõi lịch làm việc theo tháng với giao diện lịch trực quan.'
        }
      />

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1.6fr)_360px]">
        <div className="space-y-6">
          <Card className="overflow-hidden border-border/70">
            <CardContent className="p-4 md:p-5">
              <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
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
                    {isEn ? 'Schedule month' : 'Tháng hiển thị'}
                  </p>
                  <p className="text-2xl font-semibold tracking-tight text-foreground">
                    {format(currentMonth, isEn ? 'MMMM yyyy' : "'Tháng' M 'năm' yyyy", { locale })}
                  </p>
                  <p className="mt-1 text-sm text-muted-foreground">
                    {isEn ? 'Read-only monthly calendar for your planned shifts.' : 'Lịch tháng chỉ đọc cho các ca làm việc đã được lên kế hoạch.'}
                  </p>
                </div>
              </div>
            </CardContent>
          </Card>

          <div className="grid gap-4 md:grid-cols-3">
            <StatCard icon={CalendarDays} label={isEn ? 'Working days' : 'Ngày đi làm'} value={stats.workingDays} tone="text-primary" />
            <StatCard icon={Clock3} label={isEn ? 'Morning sessions' : 'Ca sáng'} value={stats.morningCount} tone="text-sky-600" />
            <StatCard icon={Clock3} label={isEn ? 'Afternoon sessions' : 'Ca chiều'} value={stats.afternoonCount} tone="text-amber-600" />
          </div>

          <Card className="border-border/70 shadow-sm">
            <CardHeader className="space-y-4 border-b bg-muted/30 pb-4">
              <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
                <div>
                  <CardTitle className="text-lg">{isEn ? 'Monthly work calendar' : 'Lịch làm việc theo tháng'}</CardTitle>
                  <p className="mt-1 text-sm text-muted-foreground">
                    {isEn ? 'Click a date to review morning and afternoon shift details.' : 'Chọn một ngày để xem chi tiết ca sáng và ca chiều.'}
                  </p>
                </div>
                <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                  {(['MORNING', 'AFTERNOON'] as SessionKey[]).map((session) => (
                    <div key={session} className="inline-flex items-center gap-2 rounded-full border bg-background px-3 py-1.5">
                      <span className={cn('h-2.5 w-2.5 rounded-full', SESSION_CONFIG[session].dotClass)} />
                      <span>{isEn ? SESSION_CONFIG[session].labelEn : SESSION_CONFIG[session].labelVi}</span>
                    </div>
                  ))}
                </div>
              </div>
            </CardHeader>
            <CardContent className="p-0">
              <div className="grid grid-cols-7 border-b bg-muted/20">
                {(isEn ? weekdayLabelsEn : weekdayLabelsVi).map((label) => (
                  <div key={label} className="border-r px-3 py-3 text-center text-xs font-semibold uppercase tracking-wide text-muted-foreground last:border-r-0">
                    {label}
                  </div>
                ))}
              </div>
              {isLoading ? (
                <div className="flex min-h-[420px] items-center justify-center p-8 text-center text-muted-foreground">
                  {isEn ? 'Loading schedule...' : 'Đang tải lịch làm việc...'}
                </div>
              ) : (
                <div className="grid grid-cols-7">
                  {calendarDays.map((day) => {
                    const dateKey = format(day, 'yyyy-MM-dd');
                    const daySchedules = scheduleMap.get(dateKey) ?? {};
                    const sessionCount = Number(!!daySchedules.MORNING) + Number(!!daySchedules.AFTERNOON);
                    const isSelected = isSameDay(day, selectedDate);
                    const inCurrentMonth = isSameMonth(day, currentMonth);

                    return (
                      <button
                        type="button"
                        key={dateKey}
                        onClick={() => setSelectedDate(day)}
                        className={cn(
                          'group min-h-[138px] border-r border-b p-3 text-left transition-all last:border-r-0',
                          inCurrentMonth ? 'bg-background hover:bg-muted/40' : 'bg-muted/15 text-muted-foreground/70',
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
                          {sessionCount > 0 && (
                            <Badge variant="secondary" className="rounded-full bg-foreground/[0.06] text-[11px] font-medium text-foreground/80">
                              {sessionCount} {isEn ? 'shift' : 'ca'}
                            </Badge>
                          )}
                        </div>

                        <div className="mt-3 space-y-2">
                          {(['MORNING', 'AFTERNOON'] as SessionKey[]).map((session) => {
                            const schedule = daySchedules[session];
                            if (!schedule) return null;
                            return (
                              <div
                                key={session}
                                className={cn(
                                  'rounded-xl border px-2.5 py-2 text-xs shadow-sm',
                                  SESSION_CONFIG[session].badgeClass,
                                  !inCurrentMonth && 'opacity-75',
                                )}
                              >
                                <div className="flex items-center gap-2 font-medium">
                                  <span className={cn('h-2 w-2 rounded-full', SESSION_CONFIG[session].dotClass)} />
                                  <span>{isEn ? SESSION_CONFIG[session].labelEn : SESSION_CONFIG[session].labelVi}</span>
                                </div>
                                <div className="mt-1 text-[11px] opacity-80">
                                  {schedule.startTime && schedule.endTime
                                    ? `${schedule.startTime} - ${schedule.endTime}`
                                    : isEn
                                      ? 'Working session'
                                      : 'Ca làm việc'}
                                </div>
                              </div>
                            );
                          })}
                        </div>
                      </button>
                    );
                  })}
                </div>
              )}
            </CardContent>
          </Card>
        </div>

        <Card className="h-fit border-border/70 shadow-sm xl:sticky xl:top-6">
          <CardHeader className="border-b bg-muted/30 pb-4">
            <div className="flex items-center justify-between gap-3">
              <div>
                <p className="text-xs font-medium uppercase tracking-[0.2em] text-muted-foreground">{isEn ? 'Selected date' : 'Ngày được chọn'}</p>
                <CardTitle className="mt-1 text-xl">
                  {format(selectedDate, isEn ? 'EEEE, dd MMM yyyy' : 'EEEE, dd/MM/yyyy', { locale })}
                </CardTitle>
              </div>
              <div className="rounded-2xl border bg-background p-3 shadow-sm">
                <Stethoscope className="h-5 w-5 text-primary" />
              </div>
            </div>
          </CardHeader>
          <CardContent className="space-y-4 p-5">
            {(['MORNING', 'AFTERNOON'] as SessionKey[]).map((session) => {
              const schedule = selectedDaySchedules[session];
              return (
                <div key={session} className="rounded-2xl border bg-background p-4 shadow-sm">
                  <div className="mb-3 flex items-center gap-2">
                    <span className={cn('h-2.5 w-2.5 rounded-full', SESSION_CONFIG[session].dotClass)} />
                    <h3 className="text-sm font-semibold text-foreground">
                      {isEn ? SESSION_CONFIG[session].labelEn : SESSION_CONFIG[session].labelVi}
                    </h3>
                    <Badge className={cn('rounded-full', schedule ? SESSION_CONFIG[session].badgeClass : 'border-border bg-muted text-muted-foreground')}>
                      {schedule ? (isEn ? 'Scheduled' : 'Có lịch') : (isEn ? 'Empty' : 'Không có lịch')}
                    </Badge>
                  </div>

                  {schedule ? (
                    <div className="space-y-2 text-sm text-muted-foreground">
                      <div className="flex items-center justify-between gap-3">
                        <span>{isEn ? 'Time' : 'Khung giờ'}</span>
                        <span className="font-medium text-foreground">
                          {schedule.startTime && schedule.endTime
                            ? `${schedule.startTime} - ${schedule.endTime}`
                            : isEn
                              ? 'Configured by admin'
                              : 'Được cấu hình bởi quản trị'}
                        </span>
                      </div>
                      {schedule.note && (
                        <div className="rounded-xl border bg-muted/25 px-3 py-2 text-sm text-foreground">
                          {schedule.note}
                        </div>
                      )}
                    </div>
                  ) : (
                    <p className="text-sm text-muted-foreground">
                      {isEn ? 'No shift planned for this session.' : 'Không có ca làm việc cho buổi này.'}
                    </p>
                  )}
                </div>
              );
            })}
          </CardContent>
        </Card>
      </div>
    </div>
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
