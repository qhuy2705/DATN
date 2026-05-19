import { useEffect, useMemo, useState } from 'react';
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
import {
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  Clock3,
  FileSpreadsheet,
  Lock,
  Plus,
  Save,
  ShieldCheck,
  Stethoscope,
  Trash2,
  Upload,
} from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { PageHeader } from '@/components/PageHeader';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Switch } from '@/components/ui/switch';
import { cn } from '@/lib/utils';
import { statusDotClasses, statusTextClasses, statusToneClasses } from '@/lib/status-style-classes';
import { getDoctorReadinessState, isDoctorBookable } from '@/lib/doctor-readiness';
import {
  useAdminDoctorLeaves,
  useAdminDoctorSchedules,
  useDeleteDoctorSchedule,
  useImportDoctorSchedules,
  useOperationalDoctorOptions,
  useUpsertDoctorSchedule,
} from '@/hooks/use-admin-data';
import {
  CALENDAR_SESSIONS,
  countUniqueLeaveDays,
  getLeaveForSession,
  getSessionLabel,
  LEAVE_STATUS_STYLES,
  toApiSessionValue,
  type SessionKey,
} from '@/lib/doctor-leave-utils';
import type { DoctorSchedule, DoctorScheduleImportResult } from '@/types/api';

const SESSION_CONFIG: Record<
  SessionKey,
  { tone: string; dotClass: string; labelVi: string; labelEn: string }
> = {
  MORNING: {
    tone: statusToneClasses.info,
    dotClass: statusDotClasses.info,
    labelVi: 'Sáng',
    labelEn: 'Morning',
  },
  AFTERNOON: {
    tone: statusToneClasses.warning,
    dotClass: statusDotClasses.warning,
    labelVi: 'Chiều',
    labelEn: 'Afternoon',
  },
};

const weekdayLabelsVi = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'];
const weekdayLabelsEn = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

type SessionFormState = {
  note: string;
};

function emptyForm(schedule?: DoctorSchedule): SessionFormState {
  return {
    note: schedule?.note ?? '',
  };
}

export default function DoctorSchedulesAdminPage() {
  const { t, i18n } = useTranslation();
  const isEn = i18n.language?.startsWith('en');
  const locale = isEn ? enUS : vi;

  const [doctorFilter, setDoctorFilter] = useState('__all__');
  const [currentMonth, setCurrentMonth] = useState(() => startOfMonth(new Date()));
  const [selectedDate, setSelectedDate] = useState(() => new Date());
  const [importFile, setImportFile] = useState<File | null>(null);
  const [clearMonthFirst, setClearMonthFirst] = useState(false);
  const [lastImport, setLastImport] = useState<DoctorScheduleImportResult | null>(null);
  const [importDialogOpen, setImportDialogOpen] = useState(false);
  const [forms, setForms] = useState<Record<SessionKey, SessionFormState>>({
    MORNING: emptyForm(),
    AFTERNOON: emptyForm(),
  });

  const monthStart = format(startOfMonth(currentMonth), 'yyyy-MM-dd');
  const monthEnd = format(endOfMonth(currentMonth), 'yyyy-MM-dd');
  const selectedDateKey = format(selectedDate, 'yyyy-MM-dd');

  const {
    data: doctorOptions = [],
    isError: isDoctorOptionsError,
    isLoading: isDoctorOptionsLoading,
  } = useOperationalDoctorOptions();
  const doctors = useMemo(
    () => doctorOptions.filter(isDoctorBookable),
    [doctorOptions],
  );

  useEffect(() => {
    if (
      doctorFilter !== '__all__' &&
      !doctors.some((doctor) => doctor.id === doctorFilter)
    ) {
      setDoctorFilter('__all__');
    }
  }, [doctorFilter, doctors]);

  useEffect(() => {
    if (!isSameMonth(selectedDate, currentMonth)) {
      setSelectedDate(startOfMonth(currentMonth));
    }
  }, [currentMonth, selectedDate]);

  const selectedDoctor = doctors.find((doctor) => doctor.id === doctorFilter);
  const selectedDoctorSchedulable = Boolean(selectedDoctor && isDoctorBookable(selectedDoctor));
  const selectedDoctorReadiness = getDoctorReadinessState(selectedDoctor, isEn);

  const { data: schedulePage, isLoading } = useAdminDoctorSchedules(
    doctorFilter !== '__all__'
      ? {
          doctorId: doctorFilter,
          from: monthStart,
          to: monthEnd,
          page: '0',
          size: '100',
        }
      : undefined,
  );

  const { data: leavePage } = useAdminDoctorLeaves(
    doctorFilter !== '__all__'
      ? {
          doctorId: doctorFilter,
          status: 'APPROVED',
          from: monthStart,
          to: monthEnd,
          page: '0',
          size: '100',
        }
      : undefined,
  );

  const upsertMutation = useUpsertDoctorSchedule();
  const deleteMutation = useDeleteDoctorSchedule();
  const importMutation = useImportDoctorSchedules();

  const schedules = useMemo(() => schedulePage?.items ?? [], [schedulePage?.items]);
  const approvedLeaves = useMemo(() => leavePage?.items ?? [], [leavePage?.items]);

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

  useEffect(() => {
    const daySchedules = scheduleMap.get(selectedDateKey) ?? {};
    setForms({
      MORNING: emptyForm(daySchedules.MORNING),
      AFTERNOON: emptyForm(daySchedules.AFTERNOON),
    });
  }, [scheduleMap, selectedDateKey]);

  const calendarDays = useMemo(() => {
    const start = startOfWeek(startOfMonth(currentMonth), { weekStartsOn: 1 });
    const end = endOfWeek(endOfMonth(currentMonth), { weekStartsOn: 1 });
    return eachDayOfInterval({ start, end });
  }, [currentMonth]);

  const monthStats = useMemo(() => {
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
      leaveDays: countUniqueLeaveDays(approvedLeaves, ['APPROVED']),
    };
  }, [approvedLeaves, schedules]);

  const selectedDaySchedules = scheduleMap.get(selectedDateKey) ?? {};

  const saveSession = async (session: SessionKey) => {
    if (doctorFilter === '__all__' || !selectedDoctorSchedulable) return;

    const blockedByLeave = getLeaveForSession(
      approvedLeaves,
      selectedDateKey,
      session,
      ['APPROVED'],
    );
    if (blockedByLeave) return;

    const form = forms[session];

    await upsertMutation.mutateAsync({
      doctorId: Number(doctorFilter),
      workDate: selectedDateKey,
      session: toApiSessionValue(session),
      note: form.note.trim() || undefined,
    });
  };

  const removeSession = async (session: SessionKey) => {
    if (doctorFilter === '__all__') return;

    await deleteMutation.mutateAsync({
      doctorId: doctorFilter,
      workDate: selectedDateKey,
      session: toApiSessionValue(session),
    });
  };

  const handleImportMonth = async () => {
    if (doctorFilter === '__all__' || !selectedDoctorSchedulable || !importFile) return;

    const result = await importMutation.mutateAsync({
      doctorId: doctorFilter,
      month: monthStart,
      clearMonthFirst,
      file: importFile,
    });

    setLastImport(result);
    setImportFile(null);
    setImportDialogOpen(false);
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title={t('modules.doctorSchedules.title')}
        description={
          isEn
            ? 'Monthly doctor work calendar with low-query loading: the screen only fetches one doctor and one month at a time to stay fast.'
            : 'Lịch làm việc bác sĩ theo tháng với cách tải nhẹ: chỉ truy vấn đúng 1 bác sĩ và 1 tháng để màn vận hành luôn mượt.'
        }
      />

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
        <div className="space-y-6">
          <Card className="overflow-hidden border-border/70 shadow-sm">
            <CardContent className="p-4 md:p-5">
              <div className="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
                <div className="flex flex-wrap items-center gap-2">
                  <Button
                    variant="outline"
                    size="icon"
                    onClick={() => setCurrentMonth((prev) => subMonths(prev, 1))}
                  >
                    <ChevronLeft className="h-4 w-4" />
                  </Button>

                  <Button
                    variant="outline"
                    onClick={() => setCurrentMonth(startOfMonth(new Date()))}
                  >
                    {isEn ? 'Today' : 'Hôm nay'}
                  </Button>

                  <Button
                    variant="outline"
                    size="icon"
                    onClick={() => setCurrentMonth((prev) => addMonths(prev, 1))}
                  >
                    <ChevronRight className="h-4 w-4" />
                  </Button>
                </div>

                <div className="text-left xl:text-right">
                  <p className="text-xs font-medium uppercase tracking-[0.22em] text-muted-foreground">
                    {isEn ? 'Loaded window' : 'Khung dữ liệu đang tải'}
                  </p>
                  <p className="text-2xl font-semibold tracking-tight text-foreground">
                    {format(
                      currentMonth,
                      isEn ? 'MMMM yyyy' : "'Tháng' M 'năm' yyyy",
                      { locale },
                    )}
                  </p>
                  <p className="mt-1 text-sm text-muted-foreground">
                    {isEn
                      ? 'Fast mode: one doctor × one month only.'
                      : 'Chế độ tải nhanh: chỉ 1 bác sĩ × 1 tháng.'}
                  </p>
                </div>
              </div>
            </CardContent>
          </Card>

          <div className="grid gap-4 md:grid-cols-4">
            <MonthStatCard
              icon={CalendarDays}
              label={isEn ? 'Working days' : 'Ngày có lịch'}
              value={monthStats.workingDays}
              tone="text-primary"
            />
            <MonthStatCard
              icon={Clock3}
              label={isEn ? 'Morning sessions' : 'Ca sáng'}
              value={monthStats.morningCount}
              tone={statusTextClasses.info}
            />
            <MonthStatCard
              icon={Clock3}
              label={isEn ? 'Afternoon sessions' : 'Ca chiều'}
              value={monthStats.afternoonCount}
              tone={statusTextClasses.warning}
            />
            <MonthStatCard
              icon={ShieldCheck}
              label={isEn ? 'Leave days' : 'Ngày nghỉ duyệt'}
              value={monthStats.leaveDays}
              tone={statusTextClasses.success}
            />
          </div>

          <Card className="border-border/70 shadow-sm">
            <CardHeader className="space-y-4 border-b bg-muted/25 pb-4">
              <div className="flex flex-col gap-4">
                <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                  <div>
                    <CardTitle className="text-lg">
                      {isEn ? 'Monthly work calendar' : 'Lịch làm việc theo tháng'}
                    </CardTitle>
                    <p className="mt-1 text-sm text-muted-foreground">
                      {doctorFilter === '__all__'
                        ? isEn
                          ? 'Select a doctor to load the monthly calendar.'
                          : 'Chọn bác sĩ để tải lịch làm việc theo tháng.'
                        : isEn
                          ? 'Click a date to review and edit morning/afternoon sessions.'
                          : 'Chọn một ngày để xem và chỉnh ca sáng/chiều.'}
                    </p>
                  </div>

                  <Dialog open={importDialogOpen} onOpenChange={setImportDialogOpen}>
                    <DialogTrigger asChild>
                      <Button
                        variant="default"
                        disabled={doctorFilter === '__all__' || !selectedDoctorSchedulable}
                        className="self-start lg:self-auto"
                      >
                        <FileSpreadsheet className="mr-2 h-4 w-4" />
                        {isEn ? 'Import Excel' : 'Nhập Excel'}
                      </Button>
                    </DialogTrigger>

                    <DialogContent className="sm:max-w-[620px]">
                      <DialogHeader>
                        <DialogTitle>
                          {isEn
                            ? 'Monthly Excel import'
                            : 'Nhập lịch tháng bằng Excel'}
                        </DialogTitle>
                        <DialogDescription>
                          {isEn
                            ? 'Upload one .xlsx file for the selected doctor and currently displayed month.'
                            : 'Tải lên 1 file .xlsx cho bác sĩ đang chọn và đúng tháng đang hiển thị.'}
                        </DialogDescription>
                      </DialogHeader>

                      <div className="space-y-4">
                        <div className="rounded-2xl border border-border/70 bg-muted/15 p-4 text-sm text-muted-foreground">
                          <p className="font-medium text-foreground">
                            {isEn ? 'Expected columns' : 'Định dạng cột mong muốn'}
                          </p>
                          <div className="mt-2 space-y-1 font-mono text-[12px] leading-5">
                            <p>
                              A: {isEn ? 'Date' : 'Ngày'} (2026-04-01 / 01/04/2026)
                            </p>
                            <p>
                              B: {isEn ? 'Session' : 'Ca'} (AM / PM / MORNING /
                              AFTERNOON / SANG / CHIEU)
                            </p>
                            <p>
                              C: {isEn ? 'Note (optional)' : 'Ghi chú (tùy chọn)'}
                            </p>
                          </div>
                        </div>

                        <div className="grid gap-4 md:grid-cols-2">
                          <div>
                            <label className="mb-1.5 block text-sm font-medium text-foreground">
                              {isEn ? 'Selected doctor' : 'Bác sĩ đang chọn'}
                            </label>
                            <div className="rounded-2xl border border-border/70 bg-background px-4 py-3 text-sm font-medium text-foreground">
                              {selectedDoctor?.fullName ||
                                (isEn ? 'No doctor selected' : 'Chưa chọn bác sĩ')}
                            </div>
                          </div>

                          <div>
                            <label className="mb-1.5 block text-sm font-medium text-foreground">
                              {isEn ? 'Selected month' : 'Tháng đang nhập'}
                            </label>
                            <div className="rounded-2xl border border-border/70 bg-background px-4 py-3 text-sm font-medium text-foreground">
                              {format(
                                currentMonth,
                                isEn ? 'MMMM yyyy' : "'Tháng' M 'năm' yyyy",
                                { locale },
                              )}
                            </div>
                          </div>
                        </div>

                        <div>
                          <label className="mb-1.5 block text-sm font-medium text-foreground">
                            {isEn ? 'Excel file' : 'File Excel'}
                          </label>
                          <Input
                            type="file"
                            accept=".xlsx"
                            onChange={(e) => setImportFile(e.target.files?.[0] ?? null)}
                          />
                          {importFile && (
                            <p className="mt-2 text-xs text-muted-foreground">
                              {importFile.name}
                            </p>
                          )}
                        </div>

                        <div className="flex items-center justify-between gap-3 rounded-2xl border border-border/70 bg-background px-4 py-3">
                          <div>
                            <p className="text-sm font-medium text-foreground">
                              {isEn
                                ? 'Replace existing month schedule'
                                : 'Xóa lịch cũ trong tháng trước khi nhập'}
                            </p>
                            <p className="text-xs text-muted-foreground">
                              {isEn
                                ? 'Turn on to clear the selected month before importing new rows.'
                                : 'Bật để dọn sạch lịch của tháng đang chọn trước khi ghi dữ liệu mới.'}
                            </p>
                          </div>
                          <Switch
                            checked={clearMonthFirst}
                            onCheckedChange={setClearMonthFirst}
                          />
                        </div>

                        {lastImport && (
                          <div className="rounded-2xl border border-success/20 bg-success/10 p-4 text-sm text-success">
                            <p className="font-medium">
                              {isEn
                                ? 'Latest import result'
                                : 'Kết quả nhập gần nhất'}
                            </p>
                            <p className="mt-1">
                              {isEn
                                ? `${lastImport.importedRows}/${lastImport.totalRows} rows imported successfully.`
                                : `Đã nhập thành công ${lastImport.importedRows}/${lastImport.totalRows} dòng.`}
                            </p>
                            {lastImport.skippedRows > 0 && (
                              <p className="mt-1 text-warning">
                                {isEn
                                  ? `${lastImport.skippedRows} rows were skipped.`
                                  : `${lastImport.skippedRows} dòng đã bị bỏ qua.`}
                              </p>
                            )}
                          </div>
                        )}
                      </div>

                      <DialogFooter>
                        <Button
                          variant="outline"
                          onClick={() => setImportDialogOpen(false)}
                        >
                          {isEn ? 'Close' : 'Đóng'}
                        </Button>

                        <Button
                          onClick={handleImportMonth}
                          disabled={
                            doctorFilter === '__all__' ||
                            !selectedDoctorSchedulable ||
                            !importFile ||
                            importMutation.isPending
                          }
                        >
                          <Upload className="mr-2 h-4 w-4" />
                          {importMutation.isPending
                            ? isEn
                              ? 'Importing...'
                              : 'Đang nhập...'
                            : isEn
                              ? 'Import monthly schedule'
                              : 'Nhập lịch làm việc theo tháng'}
                        </Button>
                      </DialogFooter>
                    </DialogContent>
                  </Dialog>
                </div>

                <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
                  <div className="w-full max-w-[360px] space-y-1.5">
                    <p className="text-xs font-medium uppercase tracking-[0.22em] text-muted-foreground">
                      {isEn ? 'Doctor' : 'Bác sĩ'}
                    </p>
                    <Select value={doctorFilter} onValueChange={setDoctorFilter}>
                      <SelectTrigger className="h-11 rounded-xl bg-background">
                        <SelectValue
                          placeholder={isEn ? 'Select doctor' : 'Chọn bác sĩ'}
                        />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="__all__">
                          {isEn ? 'Choose doctor...' : 'Chọn bác sĩ...'}
                        </SelectItem>
                        {doctors.map((doctor) => (
                          <SelectItem key={doctor.id} value={doctor.id}>
                            {doctor.fullName}
                            {doctor.branchName ? ` · ${doctor.branchName}` : ''}
                          </SelectItem>
                        ))}
                        {isDoctorOptionsError ? (
                          <SelectItem value="__doctor_options_error__" disabled>
                            Không tải được danh sách bác sĩ
                          </SelectItem>
                        ) : !isDoctorOptionsLoading && doctors.length === 0 ? (
                          <SelectItem value="__doctor_options_empty__" disabled>
                            Không có bác sĩ sẵn sàng vận hành phù hợp.
                          </SelectItem>
                        ) : null}
                      </SelectContent>
                    </Select>
                    {isDoctorOptionsError ? (
                      <p className="text-xs text-destructive">
                        Không tải được danh sách bác sĩ. Vui lòng thử lại.
                      </p>
                    ) : !isDoctorOptionsLoading && doctors.length === 0 ? (
                      <p className="text-xs text-muted-foreground">
                        Không có bác sĩ sẵn sàng vận hành phù hợp.
                      </p>
                    ) : null}
                  </div>

                  <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                    {CALENDAR_SESSIONS.map((session) => (
                      <div
                        key={session}
                        className="inline-flex items-center gap-2 rounded-full border bg-background px-3 py-1.5"
                      >
                        <span
                          className={cn(
                            'h-2.5 w-2.5 rounded-full',
                            SESSION_CONFIG[session].dotClass,
                          )}
                        />
                        <span>
                          {isEn
                            ? SESSION_CONFIG[session].labelEn
                            : SESSION_CONFIG[session].labelVi}
                        </span>
                      </div>
                    ))}

                    <div className="inline-flex items-center gap-2 rounded-full border bg-background px-3 py-1.5">
                      <span
                        className={cn(
                          'h-2.5 w-2.5 rounded-full',
                          LEAVE_STATUS_STYLES.APPROVED.dotClass,
                        )}
                      />
                      <span>{isEn ? 'Approved leave' : 'Nghỉ đã duyệt'}</span>
                    </div>
                  </div>
                </div>
              </div>

            </CardHeader>

            <CardContent className="p-0">
              {doctorFilter === '__all__' ? (
                <div className="flex min-h-[520px] items-center justify-center p-8 text-center text-muted-foreground">
                  {isEn
                    ? 'Choose a doctor from the selector above to load the calendar.'
                    : 'Hãy chọn bác sĩ ở bộ chọn phía trên để tải lịch tháng.'}
                </div>
              ) : isLoading ? (
                <div className="flex min-h-[520px] items-center justify-center p-8 text-center text-muted-foreground">
                  {isEn
                    ? 'Loading monthly schedule...'
                    : 'Đang tải lịch làm việc theo tháng...'}
                </div>
              ) : (
                <>
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

                  <div className="grid grid-cols-7">
                    {calendarDays.map((day) => {
                      const dateKey = format(day, 'yyyy-MM-dd');
                      const daySchedules = scheduleMap.get(dateKey) ?? {};
                      const morningLeave = getLeaveForSession(
                        approvedLeaves,
                        dateKey,
                        'MORNING',
                        ['APPROVED'],
                      );
                      const afternoonLeave = getLeaveForSession(
                        approvedLeaves,
                        dateKey,
                        'AFTERNOON',
                        ['APPROVED'],
                      );
                      const sessionCount =
                        Number(!!daySchedules.MORNING) + Number(!!daySchedules.AFTERNOON);
                      const blockedCount =
                        Number(!!morningLeave) + Number(!!afternoonLeave);
                      const isSelected = isSameDay(day, selectedDate);
                      const inCurrentMonth = isSameMonth(day, currentMonth);

                      return (
                        <button
                          type="button"
                          key={dateKey}
                          onClick={() => setSelectedDate(day)}
                          className={cn(
                            'group min-h-[142px] border-r border-b p-3 text-left transition-all last:border-r-0',
                            inCurrentMonth
                              ? 'bg-background hover:bg-muted/40'
                              : 'bg-muted/15 text-muted-foreground/70',
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

                            {(sessionCount > 0 || blockedCount > 0) && (
                              <Badge
                                variant="secondary"
                                className="rounded-full bg-foreground/[0.06] text-[11px] font-medium text-foreground/80"
                              >
                                {sessionCount > 0
                                  ? `${sessionCount} ${isEn ? 'shift' : 'ca'}`
                                  : `${blockedCount} ${isEn ? 'leave' : 'nghỉ'}`}
                              </Badge>
                            )}
                          </div>

                          <div className="mt-3 space-y-2">
                            {CALENDAR_SESSIONS.map((session) => {
                              const schedule = daySchedules[session];
                              const blockedLeave =
                                session === 'MORNING' ? morningLeave : afternoonLeave;

                              if (!schedule && !blockedLeave) return null;

                              return (
                                <div
                                  key={session}
                                  className={cn(
                                    'rounded-xl border px-2.5 py-2 text-xs shadow-sm',
                                    blockedLeave
                                      ? LEAVE_STATUS_STYLES.APPROVED.badgeClass
                                      : SESSION_CONFIG[session].tone,
                                    !inCurrentMonth && 'opacity-75',
                                  )}
                                >
                                  <div className="flex items-center gap-2 font-medium">
                                    <span
                                      className={cn(
                                        'h-2 w-2 rounded-full',
                                        blockedLeave
                                          ? LEAVE_STATUS_STYLES.APPROVED.dotClass
                                          : SESSION_CONFIG[session].dotClass,
                                      )}
                                    />
                                    <span>
                                      {blockedLeave
                                        ? isEn
                                          ? `${SESSION_CONFIG[session].labelEn} leave`
                                          : `Nghỉ ${SESSION_CONFIG[session].labelVi.toLowerCase()}`
                                        : isEn
                                          ? SESSION_CONFIG[session].labelEn
                                          : SESSION_CONFIG[session].labelVi}
                                    </span>
                                  </div>

                                  <div className="mt-1 text-[11px] opacity-80">
                                    {blockedLeave
                                      ? blockedLeave.reason ||
                                        (isEn
                                          ? 'Approved leave'
                                          : 'Nghỉ đã duyệt')
                                      : schedule?.startTime && schedule?.endTime
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
                </>
              )}
            </CardContent>
          </Card>
        </div>

        <div className="space-y-6 xl:sticky xl:top-6 xl:h-fit">
          <Card className="border-border/70 shadow-sm">
            <CardHeader className="border-b bg-muted/25 pb-4">
              <div className="flex items-center justify-between gap-3">
                <div>
                  <p className="text-xs font-medium uppercase tracking-[0.2em] text-muted-foreground">
                    {isEn ? 'Selected date' : 'Ngày được chọn'}
                  </p>
                  <CardTitle className="mt-1 text-xl">
                    {format(
                      selectedDate,
                      isEn ? 'EEEE, dd MMM yyyy' : 'EEEE, dd/MM/yyyy',
                      { locale },
                    )}
                  </CardTitle>
                </div>
                <div className="rounded-2xl border bg-background p-3 shadow-sm">
                  <Stethoscope className="h-5 w-5 text-primary" />
                </div>
              </div>

              <div className="rounded-2xl border border-primary/10 bg-primary/[0.05] px-4 py-3 text-sm text-muted-foreground">
                {selectedDoctor ? (
                  <>
                    <p className="font-medium text-foreground">
                      {selectedDoctor.fullName}
                    </p>
                    <p className="mt-1">
                      {[
                        selectedDoctor.specialtyNames?.join(', '),
                        selectedDoctor.branchName,
                      ]
                        .filter(Boolean)
                        .join(' · ') ||
                        (isEn
                          ? 'Included in schedulable doctor options'
                          : 'Có trong danh sách bác sĩ được xếp lịch')}
                    </p>
                    {selectedDoctorReadiness.ready === true ? (
                      <Badge className="mt-2 rounded-full bg-success/10 text-success">
                        {isEn ? 'Schedulable' : 'Có thể xếp lịch'}
                      </Badge>
                    ) : selectedDoctorReadiness.ready === false ? (
                      <Badge className="mt-2 rounded-full bg-warning/10 text-warning">
                        {selectedDoctorReadiness.label}
                      </Badge>
                    ) : null}
                  </>
                ) : (
                  <p>
                    {isEn
                      ? 'Please choose a doctor to start.'
                      : 'Vui lòng chọn bác sĩ để bắt đầu.'}
                  </p>
                )}
              </div>
            </CardHeader>

            <CardContent className="space-y-4 p-5">
              {CALENDAR_SESSIONS.map((session) => {
                const existing = selectedDaySchedules[session];
                const blockedLeave = getLeaveForSession(
                  approvedLeaves,
                  selectedDateKey,
                  session,
                  ['APPROVED'],
                );

                return (
                  <div
                    key={session}
                    className="rounded-2xl border bg-background p-4 shadow-sm"
                  >
                    <div className="mb-4 flex items-center justify-between gap-3">
                      <div className="flex items-center gap-2">
                        <span
                          className={cn(
                            'h-2.5 w-2.5 rounded-full',
                            blockedLeave
                              ? LEAVE_STATUS_STYLES.APPROVED.dotClass
                              : SESSION_CONFIG[session].dotClass,
                          )}
                        />
                        <h3 className="text-sm font-semibold text-foreground">
                          {getSessionLabel(session, isEn)}
                        </h3>
                      </div>

                      {blockedLeave ? (
                        <Badge
                          className={cn(
                            'rounded-full',
                            LEAVE_STATUS_STYLES.APPROVED.badgeClass,
                          )}
                        >
                          {isEn ? 'Blocked by leave' : 'Bị chặn do nghỉ phép'}
                        </Badge>
                      ) : existing ? (
                        <Badge
                          className={cn(
                            'rounded-full',
                            SESSION_CONFIG[session].tone,
                          )}
                        >
                          {isEn ? 'Scheduled' : 'Đã tạo lịch'}
                        </Badge>
                      ) : (
                        <Badge variant="outline" className="rounded-full">
                          {isEn ? 'Empty' : 'Chưa có lịch'}
                        </Badge>
                      )}
                    </div>

                    {blockedLeave ? (
                      <div className="rounded-2xl border border-destructive/20 bg-destructive/10 p-4 text-sm text-destructive">
                        <div className="flex items-start gap-3">
                          <Lock className="mt-0.5 h-4 w-4" />
                          <div>
                            <p className="font-medium text-destructive">
                              {isEn
                                ? 'This session is locked because approved leave already exists.'
                                : 'Buổi này đang bị khóa vì đã có đơn nghỉ được duyệt.'}
                            </p>
                            <p className="mt-1">
                              {blockedLeave.reason ||
                                (isEn
                                  ? 'Adjust the leave request first before assigning work.'
                                  : 'Hãy điều chỉnh đơn nghỉ trước khi xếp lịch làm việc.')}
                            </p>
                          </div>
                        </div>
                      </div>
                    ) : (
                      <div className="space-y-3">
                        <div className="rounded-2xl border border-primary/10 bg-primary/[0.04] px-4 py-3 text-sm text-muted-foreground">
                          {isEn
                            ? 'Capacity per session and slot duration are inherited from branch-specialty configuration. This screen keeps scheduling standardized and only stores the working session plus optional note.'
                            : 'Số bệnh nhân mỗi ca và phút mỗi slot sẽ kế thừa từ cấu hình khoa/chuyên khoa. Màn này chỉ dùng để xếp ca làm việc và ghi chú tùy chọn để tránh nhập tay thiếu chuyên nghiệp.'}
                        </div>

                        <div>
                          <label className="mb-1.5 block text-xs font-medium uppercase tracking-wide text-muted-foreground">
                            {isEn ? 'Note' : 'Ghi chú'}
                          </label>
                          <Input
                            value={forms[session].note}
                            onChange={(e) =>
                              setForms((prev) => ({
                                ...prev,
                                [session]: {
                                  ...prev[session],
                                  note: e.target.value,
                                },
                              }))
                            }
                            placeholder={
                              isEn
                                ? 'Optional operational note for this session'
                                : 'Ghi chú vận hành thêm cho ca làm việc'
                            }
                          />
                        </div>

                        {existing && (
                          <div className="rounded-xl border bg-muted/20 px-3 py-2 text-xs text-muted-foreground">
                            <div className="flex items-center justify-between gap-3">
                              <span>
                                {isEn ? 'Current session info' : 'Thông tin ca hiện tại'}
                              </span>
                              <span className="font-medium text-foreground">
                                {existing.startTime && existing.endTime
                                  ? `${existing.startTime} - ${existing.endTime}`
                                  : isEn
                                    ? 'Inherited working session'
                                    : 'Ca làm việc theo cấu hình'}
                              </span>
                            </div>
                          </div>
                        )}

                        <div className="flex flex-wrap items-center gap-2 pt-1">
                          <Button
                            onClick={() => saveSession(session)}
                            disabled={
                              doctorFilter === '__all__' ||
                              !selectedDoctorSchedulable ||
                              upsertMutation.isPending
                            }
                            className="min-w-[132px]"
                          >
                            {existing ? (
                              <Save className="mr-2 h-4 w-4" />
                            ) : (
                              <Plus className="mr-2 h-4 w-4" />
                            )}
                            {existing
                              ? isEn
                                ? 'Update'
                                : 'Cập nhật'
                              : isEn
                                ? 'Create'
                                : 'Tạo lịch'}
                          </Button>

                          {existing && (
                            <Button
                              variant="outline"
                              onClick={() => removeSession(session)}
                              disabled={deleteMutation.isPending}
                              className="border-destructive/20 text-destructive hover:bg-destructive/5"
                            >
                              <Trash2 className="mr-2 h-4 w-4" />
                              {isEn ? 'Delete' : 'Xóa ca'}
                            </Button>
                          )}
                        </div>
                      </div>
                    )}
                  </div>
                );
              })}
            </CardContent>
          </Card>

          {lastImport?.warnings?.length ? (
            <Card className="border-border/70 shadow-sm">
              <CardHeader>
                <CardTitle className="text-lg">
                  {isEn ? 'Import warnings' : 'Cảnh báo khi nhập lịch'}
                </CardTitle>
                <CardDescription>
                  {isEn
                    ? 'Review skipped rows and fix the Excel file if needed.'
                    : 'Xem các dòng bị bỏ qua để chỉnh lại file Excel khi cần.'}
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-2 text-sm text-muted-foreground">
                {lastImport.warnings.slice(0, 8).map((warning, index) => (
                  <div
                    key={`${warning}-${index}`}
                    className="rounded-xl border border-border/70 bg-muted/15 px-3 py-2"
                  >
                    {warning}
                  </div>
                ))}

                {lastImport.warnings.length > 8 && (
                  <div className="text-xs text-muted-foreground">
                    {isEn
                      ? `And ${lastImport.warnings.length - 8} more warnings...`
                      : `Còn ${lastImport.warnings.length - 8} cảnh báo khác...`}
                  </div>
                )}
              </CardContent>
            </Card>
          ) : null}
        </div>
      </div>
    </div>
  );
}

function MonthStatCard({
  icon: Icon,
  label,
  value,
  tone,
}: {
  icon: typeof CalendarDays;
  label: string;
  value: string | number;
  tone?: string;
}) {
  return (
    <Card className="border-border/70 shadow-sm">
      <CardContent className="flex items-center justify-between p-5">
        <div>
          <p className="text-sm text-muted-foreground">{label}</p>
          <p className="mt-2 text-2xl font-semibold tracking-tight text-foreground">
            {value}
          </p>
        </div>
        <div className={cn('rounded-2xl bg-muted/50 p-3', tone)}>
          <Icon className="h-5 w-5" />
        </div>
      </CardContent>
    </Card>
  );
}
