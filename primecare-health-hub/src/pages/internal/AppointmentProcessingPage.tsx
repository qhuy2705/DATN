import { useEffect, useMemo, useRef, useState, type Dispatch, type SetStateAction } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import type { LucideIcon } from 'lucide-react';
import {
  ArrowLeft,
  AlertTriangle,
  CalendarDays,
  CheckCircle2,
  ClipboardList,
  Clock3,
  DoorOpen,
  FileText,
  Hash,
  IdCard,
  LogOut,
  MapPin,
  Phone,
  RotateCw,
  ShieldCheck,
  UserRound,
  XCircle,
} from 'lucide-react';
import { StatusBadge } from '@/components/StatusBadge';
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import {
  useAdminAppointmentDetail,
  useAppointmentAction,
  useAppointmentSummaryRealtime,
  useRescheduleAvailability,
  useUpdateAppointmentIntake,
} from '@/hooks/use-admin-data';
import { useManualCheckIn, useMarkArrived } from '@/hooks/use-reception-data';
import { toLocalDateInputValue } from '@/lib/date';
import { isAppointmentOverdue } from '@/lib/appointment-utils';
import { OverdueBadge } from '@/components/OverdueBadge';
import { useAuthStore } from '@/stores/auth-store';
import type { Appointment, BranchSessionType } from '@/types/api';

const HOLD_MINUTES = 5;
const HEARTBEAT_INTERVAL_MS = 30000;

type IntakeFormState = {
  reasonForVisit: string;
  visitType: string;
  triagePriority: string;
  triageNote: string;
  insuranceNote: string;
  emergencyContactName: string;
  emergencyContactPhone: string;
};

type TimelineItem = {
  label: string;
  value: string;
  icon: LucideIcon;
};

function buildAbsoluteApiUrl(path: string) {
  if (typeof window === 'undefined') return path;

  const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '');
  const absoluteApiBase = apiBaseUrl.startsWith('http')
    ? apiBaseUrl
    : `${window.location.origin}${apiBaseUrl.startsWith('/') ? '' : '/'}${apiBaseUrl}`;

  return `${absoluteApiBase}${path.startsWith('/') ? path : `/${path}`}`;
}

function formatDateTime(value?: string) {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('vi-VN');
}

function formatTime(value?: string) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
}

function renderGender(value?: string) {
  switch (value) {
    case 'MALE':
      return 'Nam';
    case 'FEMALE':
      return 'Nữ';
    case 'OTHER':
      return 'Khác';
    default:
      return value || '—';
  }
}

function renderSourceType(value?: string) {
  switch (value) {
    case 'ONLINE':
      return 'Đặt online';
    case 'WALK_IN':
      return 'Walk-in';
    default:
      return value || '—';
  }
}

function renderArrivalStatus(value?: string) {
  if (value === 'ARRIVED') return 'Đã đến';
  if (value === 'NOT_ARRIVED') return 'Chưa đến';
  return value || '—';
}

function isClaimExpired(appointment: Appointment) {
  if (!appointment.processingExpiresAt) return false;
  const expiresAt = new Date(appointment.processingExpiresAt);
  return !Number.isNaN(expiresAt.getTime()) && expiresAt.getTime() <= Date.now();
}

function isNoShowFollowUp(appointment: Appointment) {
  return appointment.status === 'NO_SHOW' && appointment.followUpPending === true;
}

function hasActiveClaim(appointment: Appointment) {
  return Boolean(appointment.processingById && !isClaimExpired(appointment));
}

function renderClaimStatus(appointment: Appointment, isOwnedByCurrentUser: boolean) {
  const expiresLabel = formatTime(appointment.processingExpiresAt);

  if (appointment.processingById && isClaimExpired(appointment)) {
    return 'Claim đã hết hạn';
  }

  if (isOwnedByCurrentUser) {
    return `Quyền xử lý được giữ đến ${expiresLabel || 'khi hệ thống tự gia hạn'}`;
  }

  if (hasActiveClaim(appointment)) {
    return `Đang được giữ bởi ${appointment.claimedBy || 'nhân viên khác'}${expiresLabel ? ` đến ${expiresLabel}` : ''}`;
  }

  return 'Lịch hẹn chưa có nhân viên giữ quyền xử lý';
}

function renderClaimHint(
  appointment: Appointment,
  canClaim: boolean,
  isOwnedByCurrentUser: boolean,
) {
  if (isOwnedByCurrentUser) {
    return 'Hệ thống tự giữ quyền xử lý trong 5 phút và tự gia hạn khi bạn còn thao tác trên màn này.';
  }

  if (hasActiveClaim(appointment)) {
    return null;
  }

  if (canClaim) {
    return 'Nhấn Nhận xử lý để cập nhật lịch hẹn này.';
  }

  return 'Chế độ xem chi tiết. Lịch hẹn này không còn thao tác xử lý.';
}

function InfoBlock({
  label,
  value,
  icon: Icon,
}: {
  label: string;
  value?: string | number | null;
  icon?: LucideIcon;
}) {
  return (
    <div className="min-w-0 rounded-lg border border-border/70 bg-muted/20 px-3 py-2.5">
      <p className="flex items-center gap-1.5 text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
        {Icon ? <Icon className="h-3.5 w-3.5" /> : null}
        {label}
      </p>
      <p className="mt-1 truncate text-sm font-semibold text-foreground" title={String(value || '—')}>
        {value || '—'}
      </p>
    </div>
  );
}

function AppointmentProcessingHeader({
  appointment,
  canClaim,
  canMarkNoShow,
  canReschedule,
  isOverdue,
  isOwnedByCurrentUser,
  isBusy,
  onBackToList,
  onCancel,
  onCheckIn,
  onClaim,
  onConfirm,
  onMarkNoShow,
  onMarkArrived,
  onOpenQueue,
  onOpenReschedule,
}: {
  appointment: Appointment;
  canClaim: boolean;
  canMarkNoShow: boolean;
  canReschedule: boolean;
  isOverdue: boolean;
  isOwnedByCurrentUser: boolean;
  isBusy: boolean;
  onBackToList: () => void;
  onCancel: () => void;
  onCheckIn: () => void;
  onClaim: () => void;
  onConfirm: () => void;
  onMarkNoShow: () => void;
  onMarkArrived: () => void;
  onOpenQueue: () => void;
  onOpenReschedule: () => void;
}) {
  const isPending = appointment.status === 'REQUESTED';
  const isConfirmed = appointment.status === 'CONFIRMED';
  const isCheckedIn = appointment.status === 'CHECKED_IN';
  const noShowFollowUp = isNoShowFollowUp(appointment);
  const claimHint = renderClaimHint(appointment, canClaim, isOwnedByCurrentUser);

  return (
    <section className="sticky top-4 z-20 rounded-lg border border-border bg-card/95 p-4 shadow-sm backdrop-blur">
      <div className="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <span className="inline-flex items-center rounded-full border border-primary/15 bg-primary/5 px-3 py-1 text-[11px] font-semibold uppercase tracking-[0.18em] text-primary">
              Tiếp nhận
            </span>
            <StatusBadge status={appointment.status} />
            {isOverdue ? <OverdueBadge /> : null}
            {noShowFollowUp ? (
              <span className="inline-flex items-center whitespace-nowrap rounded-full border border-warning/25 bg-warning/10 px-2.5 py-1 text-xs font-medium leading-none text-warning">
                Cần follow-up
              </span>
            ) : null}
            {appointment.receptionQueueNo ? (
              <span className="inline-flex items-center rounded-full border border-border bg-muted/30 px-2.5 py-1 text-xs font-medium text-foreground">
                Số tiếp nhận {appointment.receptionQueueNo}
              </span>
            ) : null}
          </div>
          <div className="mt-3 flex flex-wrap items-end gap-x-4 gap-y-1">
            <h1 className="text-xl font-semibold tracking-tight text-foreground">
              #{appointment.code}
            </h1>
            <p className="text-lg font-medium text-foreground">{appointment.patientFullName}</p>
            <p className="text-sm text-muted-foreground">{appointment.patientPhone}</p>
          </div>
          <p className="mt-2 text-sm text-muted-foreground">
            {renderClaimStatus(appointment, isOwnedByCurrentUser)}
          </p>
          {claimHint ? (
            <p className="mt-1 text-xs text-muted-foreground">
              {claimHint}
            </p>
          ) : null}
        </div>

        <div className="flex flex-wrap items-center gap-2 xl:justify-end">
          {canClaim ? (
            <Button onClick={onClaim} disabled={isBusy}>
              <ShieldCheck className="mr-2 h-4 w-4" />
              {noShowFollowUp ? 'Nhận xử lý follow-up' : 'Nhận xử lý'}
            </Button>
          ) : null}

          {isOwnedByCurrentUser && isPending ? (
            <>
              <Button onClick={onConfirm} disabled={isBusy}>
                <CheckCircle2 className="mr-2 h-4 w-4" />
                Xác nhận lịch hẹn
              </Button>
              <Button variant="destructive" onClick={onCancel} disabled={isBusy}>
                <XCircle className="mr-2 h-4 w-4" />
                Hủy lịch
              </Button>
              {canReschedule ? (
                <Button variant="outline" onClick={onOpenReschedule} disabled={isBusy}>
                  <RotateCw className="mr-2 h-4 w-4" />
                  Dời lịch
                </Button>
              ) : null}
            </>
          ) : null}

          {isOwnedByCurrentUser && isConfirmed ? (
            <>
              {appointment.arrivalStatus !== 'ARRIVED' ? (
                <Button variant="outline" onClick={onMarkArrived} disabled={isBusy}>
                  <DoorOpen className="mr-2 h-4 w-4" />
                  Đánh dấu đã đến
                </Button>
              ) : null}
              <Button onClick={onCheckIn} disabled={isBusy}>
                <UserRound className="mr-2 h-4 w-4" />
                Check-in
              </Button>
              <Button variant="destructive" onClick={onCancel} disabled={isBusy}>
                <XCircle className="mr-2 h-4 w-4" />
                Hủy lịch
              </Button>
              {canMarkNoShow ? (
                <Button variant="outline" onClick={onMarkNoShow} disabled={isBusy}>
                  <AlertTriangle className="mr-2 h-4 w-4" />
                  Đánh dấu không đến
                </Button>
              ) : null}
              {canReschedule ? (
                <Button variant="outline" onClick={onOpenReschedule} disabled={isBusy}>
                  <RotateCw className="mr-2 h-4 w-4" />
                  Dời lịch
                </Button>
              ) : null}
            </>
          ) : null}

          {isOwnedByCurrentUser && appointment.status === 'NO_SHOW' && canReschedule ? (
            <Button variant="outline" onClick={onOpenReschedule} disabled={isBusy}>
              <RotateCw className="mr-2 h-4 w-4" />
              Dời lịch
            </Button>
          ) : null}

          {isCheckedIn ? (
            <Button variant="outline" onClick={onOpenQueue}>
              Mở hàng đợi
            </Button>
          ) : null}

          <Button variant="outline" onClick={onBackToList} disabled={isBusy}>
            {isOwnedByCurrentUser ? (
              <>
                <LogOut className="mr-2 h-4 w-4" />
                Nhả xử lý và quay lại
              </>
            ) : (
              <>
                <ArrowLeft className="mr-2 h-4 w-4" />
                Quay lại danh sách
              </>
            )}
          </Button>
        </div>
      </div>
    </section>
  );
}

function AppointmentScheduleCard({ appointment }: { appointment: Appointment }) {
  return (
    <Card className="border-border/70 shadow-sm">
      <CardHeader className="pb-3">
        <CardTitle className="text-base">Thông tin lịch hẹn</CardTitle>
      </CardHeader>
      <CardContent className="grid gap-3 sm:grid-cols-2 xl:grid-cols-1 2xl:grid-cols-2">
        <InfoBlock label="Ngày khám" value={appointment.visitDate} icon={CalendarDays} />
        <InfoBlock
          label="Giờ dự kiến"
          value={`${appointment.slotStart || '—'}${appointment.slotEnd ? ` - ${appointment.slotEnd}` : ''}`}
          icon={Clock3}
        />
        <InfoBlock label="Cơ sở" value={appointment.branchName} icon={MapPin} />
        <InfoBlock label="Chuyên khoa" value={appointment.specialtyName} icon={ClipboardList} />
        <InfoBlock label="Bác sĩ" value={appointment.doctorName} icon={UserRound} />
        <InfoBlock label="Nguồn đặt lịch" value={renderSourceType(appointment.sourceType)} icon={FileText} />
        <InfoBlock label="Trạng thái đến" value={renderArrivalStatus(appointment.arrivalStatus)} icon={DoorOpen} />
        <div className="rounded-lg border border-border/70 bg-muted/20 px-3 py-2.5">
          <p className="text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
            Trạng thái lịch
          </p>
          <StatusBadge status={appointment.status} className="mt-2" />
        </div>
      </CardContent>
    </Card>
  );
}

function AppointmentPatientCard({ appointment }: { appointment: Appointment }) {
  return (
    <Card className="border-border/70 shadow-sm">
      <CardHeader className="pb-3">
        <CardTitle className="text-base">Thông tin bệnh nhân</CardTitle>
      </CardHeader>
      <CardContent className="grid gap-3 sm:grid-cols-2 xl:grid-cols-1 2xl:grid-cols-2">
        <InfoBlock label="Họ tên" value={appointment.patientFullName} icon={UserRound} />
        <InfoBlock label="Số điện thoại" value={appointment.patientPhone} icon={Phone} />
        <InfoBlock label="Email" value={appointment.patientEmail} />
        <InfoBlock label="Ngày sinh" value={appointment.patientDob} />
        <InfoBlock label="Giới tính" value={renderGender(appointment.patientGender)} />
        <InfoBlock label="Mã bệnh nhân" value={appointment.patientId} icon={IdCard} />
        <InfoBlock label="Mã lịch hẹn" value={appointment.code} icon={Hash} />
        <InfoBlock label="Ghi chú bệnh nhân" value={appointment.patientNote} icon={FileText} />
      </CardContent>
    </Card>
  );
}

function AppointmentReceptionInfoCard({
  appointment,
  canEdit,
  intakeForm,
  intakeLocked,
  isSaving,
  setIntakeForm,
  onSave,
}: {
  appointment: Appointment;
  canEdit: boolean;
  intakeForm: IntakeFormState;
  intakeLocked: boolean;
  isSaving: boolean;
  setIntakeForm: Dispatch<SetStateAction<IntakeFormState>>;
  onSave: () => void;
}) {
  const disabled = !canEdit || intakeLocked;

  return (
    <Card className="border-border/70 shadow-sm">
      <CardHeader className="pb-3">
        <CardTitle className="text-base">Thông tin tiếp nhận</CardTitle>
        <CardDescription>
          Kiểm tra thông tin bệnh nhân, lý do khám và ghi chú tiếp nhận trước khi xác nhận/check-in.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div>
          <label className="mb-1.5 block text-sm font-medium">Lý do khám</label>
          <Textarea
            rows={2}
            value={intakeForm.reasonForVisit}
            onChange={(event) =>
              setIntakeForm((prev) => ({ ...prev, reasonForVisit: event.target.value }))
            }
            disabled={disabled}
            placeholder="Lý do khám bệnh nhân đã cung cấp"
          />
        </div>

        <div>
          <label className="mb-1.5 block text-sm font-medium">Ghi chú tiếp nhận</label>
          <Textarea
            rows={2}
            value={intakeForm.triageNote}
            onChange={(event) =>
              setIntakeForm((prev) => ({ ...prev, triageNote: event.target.value }))
            }
            disabled={disabled}
            placeholder="Ghi chú hành chính tại quầy tiếp nhận"
          />
        </div>

        <div>
          <label className="mb-1.5 block text-sm font-medium">Giấy tờ / BHYT nếu có</label>
          <Input
            value={intakeForm.insuranceNote}
            onChange={(event) =>
              setIntakeForm((prev) => ({ ...prev, insuranceNote: event.target.value }))
            }
            disabled={disabled}
            placeholder="Thông tin giấy tờ, BHYT hoặc lưu ý thanh toán"
          />
        </div>

        <Accordion type="single" collapsible>
          <AccordionItem value="more" className="rounded-lg border border-border/70 px-3">
            <AccordionTrigger className="py-3 text-left text-sm font-medium hover:no-underline">
              Tùy chọn tiếp nhận
            </AccordionTrigger>
            <AccordionContent className="space-y-4 pb-4">
              <div className="grid gap-4 sm:grid-cols-2">
                <div>
                  <label className="mb-1.5 block text-sm font-medium">Loại lượt khám</label>
                  <Select
                    value={intakeForm.visitType || undefined}
                    onValueChange={(value) =>
                      setIntakeForm((prev) => ({ ...prev, visitType: value }))
                    }
                    disabled={disabled}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder="Chọn loại lượt khám" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="NEW_PATIENT">Khám mới</SelectItem>
                      <SelectItem value="FOLLOW_UP">Tái khám</SelectItem>
                      <SelectItem value="CONSULTATION">Tư vấn</SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                <div>
                  <label className="mb-1.5 block text-sm font-medium">Mức ưu tiên tiếp nhận</label>
                  <Select
                    value={intakeForm.triagePriority || undefined}
                    onValueChange={(value) =>
                      setIntakeForm((prev) => ({ ...prev, triagePriority: value }))
                    }
                    disabled={disabled}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder="Chọn mức ưu tiên" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="ROUTINE">Thông thường</SelectItem>
                      <SelectItem value="PRIORITY">Ưu tiên</SelectItem>
                      <SelectItem value="URGENT">Khẩn</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <div>
                  <label className="mb-1.5 block text-sm font-medium">Người liên hệ khẩn</label>
                  <Input
                    value={intakeForm.emergencyContactName}
                    onChange={(event) =>
                      setIntakeForm((prev) => ({
                        ...prev,
                        emergencyContactName: event.target.value,
                      }))
                    }
                    disabled={disabled}
                  />
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-medium">SĐT liên hệ khẩn</label>
                  <Input
                    value={intakeForm.emergencyContactPhone}
                    onChange={(event) =>
                      setIntakeForm((prev) => ({
                        ...prev,
                        emergencyContactPhone: event.target.value,
                      }))
                    }
                    disabled={disabled}
                  />
                </div>
              </div>
            </AccordionContent>
          </AccordionItem>
        </Accordion>

        <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-border/70 bg-muted/20 px-3 py-2.5 text-sm">
          <span className="text-muted-foreground">
            {appointment.intakeCompletedAt
              ? `Cập nhật gần nhất: ${formatDateTime(appointment.intakeCompletedAt)}${appointment.intakeCompletedByName ? ` · ${appointment.intakeCompletedByName}` : ''}`
              : canEdit
                ? 'Chưa có ghi chú tiếp nhận'
                : 'Nhận xử lý để cập nhật thông tin tiếp nhận'}
          </span>
          <Button size="sm" onClick={onSave} disabled={isSaving || disabled}>
            Lưu thông tin tiếp nhận
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

function AppointmentHistoryAccordion({ timeline }: { timeline: TimelineItem[] }) {
  return (
    <Accordion type="single" collapsible className="rounded-lg border border-border bg-card px-4 shadow-sm">
      <AccordionItem value="history" className="border-0">
        <AccordionTrigger className="py-4 text-left hover:no-underline">
          <div>
            <p className="text-base font-semibold text-foreground">Lịch sử xử lý</p>
            <p className="text-sm font-normal text-muted-foreground">
              Mở khi cần đối chiếu các mốc xác nhận, đã đến và check-in.
            </p>
          </div>
        </AccordionTrigger>
        <AccordionContent className="pb-4">
          <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-5">
            {timeline.map((item) => (
              <div key={item.label} className="rounded-lg border border-border/70 bg-muted/20 p-3">
                <div className="flex items-center gap-2">
                  <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary">
                    <item.icon className="h-4 w-4" />
                  </span>
                  <p className="text-sm font-medium text-foreground">{item.label}</p>
                </div>
                <p className="mt-2 text-sm text-muted-foreground">{item.value}</p>
              </div>
            ))}
          </div>
        </AccordionContent>
      </AccordionItem>
    </Accordion>
  );
}

function NoShowFollowUpPanel({
  appointment,
  canAct,
  isBusy,
  onOpenReschedule,
  onResolve,
}: {
  appointment: Appointment;
  canAct: boolean;
  isBusy: boolean;
  onOpenReschedule: () => void;
  onResolve: () => void;
}) {
  if (appointment.status !== 'NO_SHOW') return null;

  const pending = appointment.followUpPending === true;

  return (
    <Card className="border-warning/25 bg-warning/5 shadow-sm">
      <CardHeader className="pb-3">
        <div className="flex items-start gap-3">
          <span className="mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-warning/10 text-warning">
            <AlertTriangle className="h-5 w-5" />
          </span>
          <div className="min-w-0">
            <CardTitle className="text-base">Follow-up lịch không đến</CardTitle>
            <CardDescription>
              Kiểm tra thông tin no-show trước khi dời lịch hoặc đóng follow-up.
            </CardDescription>
          </div>
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
          <InfoBlock
            label="Người đánh dấu không đến"
            value={appointment.noShowMarkedByName || 'Chưa ghi nhận'}
          />
          <InfoBlock label="Thời điểm" value={formatDateTime(appointment.noShowMarkedAt)} />
          <InfoBlock label="Ghi chú" value={appointment.noShowNote || '—'} />
          <div className="rounded-lg border border-border/70 bg-card/70 px-3 py-2.5">
            <p className="text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
              Trạng thái
            </p>
            <span className="mt-2 inline-flex items-center whitespace-nowrap rounded-full border border-warning/25 bg-warning/10 px-2.5 py-1 text-xs font-medium leading-none text-warning">
              {pending ? 'Cần follow-up' : 'Đã xử lý follow-up'}
            </span>
          </div>
        </div>

        {pending && canAct ? (
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" onClick={onOpenReschedule} disabled={isBusy}>
              <RotateCw className="mr-2 h-4 w-4" />
              Dời lịch
            </Button>
            <Button onClick={onResolve} disabled={isBusy}>
              Đóng follow-up
            </Button>
          </div>
        ) : null}

        {pending && !canAct ? (
          <p className="rounded-lg border border-border/70 bg-card/70 px-3 py-2.5 text-sm text-muted-foreground">
            Bạn cần nhận xử lý follow-up trước khi dời lịch hoặc đóng follow-up.
          </p>
        ) : null}
      </CardContent>
    </Card>
  );
}

function AutoCancelWarning({ appointment }: { appointment: Appointment }) {
  if (appointment.status !== 'CANCELLED' || !appointment.autoCancelled) return null;

  return (
    <div className="rounded-lg border border-warning/25 bg-warning/5 px-4 py-3 text-sm text-foreground">
      <div className="flex items-start gap-3">
        <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-warning" />
        <div>
          <p className="font-medium">Lịch hẹn đã tự động hủy do quá hạn xác nhận.</p>
          {appointment.cancellationReason ? (
            <p className="mt-1 text-muted-foreground">{appointment.cancellationReason}</p>
          ) : null}
        </div>
      </div>
    </div>
  );
}

function MarkNoShowDialog({
  open,
  isPending,
  onOpenChange,
  onSubmit,
}: {
  open: boolean;
  isPending: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmit: (note: string) => void;
}) {
  const [note, setNote] = useState('');

  useEffect(() => {
    if (open) setNote('');
  }, [open]);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Đánh dấu không đến</DialogTitle>
          <DialogDescription>
            Chỉ dùng khi lịch đã quá giờ và bệnh nhân chưa đến/check-in.
          </DialogDescription>
        </DialogHeader>
        <div>
          <label className="mb-1.5 block text-sm font-medium">Ghi chú follow-up</label>
          <Textarea
            rows={3}
            value={note}
            onChange={(event) => setNote(event.target.value)}
            placeholder="Ví dụ: đã gọi nhưng chưa liên hệ được"
          />
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={isPending}>
            Hủy
          </Button>
          <Button onClick={() => onSubmit(note)} disabled={isPending}>
            {isPending ? 'Đang cập nhật...' : 'Đánh dấu không đến'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function RescheduleDialog({
  appointment,
  open,
  isPending,
  onOpenChange,
  onSubmit,
}: {
  appointment: Appointment;
  open: boolean;
  isPending: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmit: (body: {
    visitDate: string;
    session: BranchSessionType;
    slotStart: string;
    note: string;
  }) => void | Promise<void>;
}) {
  const today = toLocalDateInputValue();
  const [form, setForm] = useState({
    visitDate: today,
    session: 'AM',
    slotStart: '',
    reason: '',
  });
  const [reasonTouched, setReasonTouched] = useState(false);

  useEffect(() => {
    if (!open) return;
    setForm({
      visitDate: today,
      session: appointment.session === 'AFTERNOON' ? 'PM' : 'AM',
      slotStart: '',
      reason: '',
    });
    setReasonTouched(false);
  }, [appointment.session, open, today]);

  const availabilityParams = useMemo(
    () =>
      form.visitDate && form.session
        ? {
            branchId: appointment.branchId,
            specialtyId: appointment.specialtyId,
            doctorId: appointment.doctorId,
            visitDate: form.visitDate,
            session: form.session,
          }
        : undefined,
    [
      appointment.branchId,
      appointment.doctorId,
      appointment.specialtyId,
      form.session,
      form.visitDate,
    ],
  );
  const { data: slots = [], isFetching } = useRescheduleAvailability(
    appointment.id,
    availabilityParams,
    { enabled: open },
  );
  const availableSlots = useMemo(
    () => slots.filter((slot) => slot.remainingSlots > 0 && slot.status !== 'FULL'),
    [slots],
  );
  const trimmedReason = form.reason.trim();
  const hasRequiredSchedule = Boolean(form.visitDate && form.session && form.slotStart);
  const reasonError =
    (reasonTouched || hasRequiredSchedule) && !trimmedReason
      ? 'Vui lòng nhập lý do dời lịch.'
      : '';
  const canSubmit = Boolean(
    hasRequiredSchedule &&
      trimmedReason &&
      !isPending,
  );

  const submit = () => {
    setReasonTouched(true);
    if (!trimmedReason) return;

    void onSubmit({
      visitDate: form.visitDate,
      session: form.session as BranchSessionType,
      slotStart: form.slotStart,
      note: trimmedReason,
    });
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Dời lịch hẹn</DialogTitle>
          <DialogDescription>
            Chọn thời gian mới còn trống cho cùng bác sĩ và chuyên khoa.
          </DialogDescription>
        </DialogHeader>
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label className="mb-1.5 block text-sm font-medium">Ngày mới</label>
            <Input
              type="date"
              min={today}
              value={form.visitDate}
              onChange={(event) =>
                setForm((current) => ({ ...current, visitDate: event.target.value, slotStart: '' }))
              }
            />
          </div>
          <div>
            <label className="mb-1.5 block text-sm font-medium">Buổi</label>
            <Select
              value={form.session}
              onValueChange={(value) =>
                setForm((current) => ({ ...current, session: value, slotStart: '' }))
              }
            >
              <SelectTrigger>
                <SelectValue placeholder="Chọn buổi" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="AM">Sáng</SelectItem>
                <SelectItem value="PM">Chiều</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>
        <div>
          <label className="mb-1.5 block text-sm font-medium">Khung giờ mới</label>
          <Select
            value={form.slotStart}
            onValueChange={(value) => setForm((current) => ({ ...current, slotStart: value }))}
            disabled={isFetching || availableSlots.length === 0}
          >
            <SelectTrigger>
              <SelectValue
                placeholder={
                  isFetching
                    ? 'Đang tải khung giờ...'
                    : availableSlots.length
                      ? 'Chọn khung giờ'
                      : 'Không còn khung giờ trống'
                }
              />
            </SelectTrigger>
            <SelectContent>
              {availableSlots.map((slot) => (
                <SelectItem key={slot.id} value={slot.slotStart}>
                  {slot.slotStart} - {slot.slotEnd}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div>
          <label className="mb-1.5 block text-sm font-medium" htmlFor="reschedule-note">
            Lý do dời lịch *
          </label>
          <Textarea
            id="reschedule-note"
            rows={3}
            value={form.reason}
            onChange={(event) =>
              setForm((current) => ({ ...current, reason: event.target.value }))
            }
            onBlur={() => setReasonTouched(true)}
            aria-invalid={Boolean(reasonError)}
            aria-describedby={reasonError ? 'reschedule-note-error' : undefined}
            placeholder="Ví dụ: Bệnh nhân bận việc cá nhân, xin chuyển sang khung giờ khác"
          />
          {reasonError ? (
            <p id="reschedule-note-error" className="mt-1.5 text-sm text-destructive">
              {reasonError}
            </p>
          ) : null}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={isPending}>
            Hủy
          </Button>
          <Button
            onClick={submit}
            disabled={!canSubmit}
          >
            {isPending ? 'Đang dời lịch...' : 'Dời lịch'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function ResolveNoShowDialog({
  open,
  isPending,
  onOpenChange,
  onConfirm,
}: {
  open: boolean;
  isPending: boolean;
  onOpenChange: (open: boolean) => void;
  onConfirm: () => void;
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Đóng follow-up</DialogTitle>
          <DialogDescription>
            Xác nhận đóng follow-up cho lịch hẹn này?
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={isPending}>
            Hủy
          </Button>
          <Button onClick={onConfirm} disabled={isPending}>
            {isPending ? 'Đang đóng...' : 'Đóng follow-up'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export default function AppointmentProcessingPage() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const currentUser = useAuthStore((s) => s.user);
  const accessToken = useAuthStore((s) => s.accessToken);
  const releaseOnLeaveRef = useRef(false);
  const { data: appointment, isLoading } = useAdminAppointmentDetail(id, {
    enabled: Boolean(id),
    refetchOnWindowFocus: false,
  });

  useAppointmentSummaryRealtime(undefined, { appointmentId: id });

  const appointmentAction = useAppointmentAction();
  const markArrived = useMarkArrived();
  const manualCheckIn = useManualCheckIn();
  const updateIntake = useUpdateAppointmentIntake();
  const [now, setNow] = useState(() => new Date());
  const [markNoShowOpen, setMarkNoShowOpen] = useState(false);
  const [resolveNoShowOpen, setResolveNoShowOpen] = useState(false);
  const [rescheduleOpen, setRescheduleOpen] = useState(false);
  const [intakeForm, setIntakeForm] = useState<IntakeFormState>({
    reasonForVisit: '',
    visitType: '',
    triagePriority: '',
    triageNote: '',
    insuranceNote: '',
    emergencyContactName: '',
    emergencyContactPhone: '',
  });

  const isOwnedByCurrentUser = useMemo(() => {
    if (!appointment || isClaimExpired(appointment)) return false;
    if (!appointment?.processingById || !currentUser?.id) return false;
    return String(appointment.processingById) === String(currentUser.id);
  }, [appointment, currentUser?.id]);

  const canClaimAppointment = useMemo(() => {
    if (!appointment) return false;
    const claimableStatus =
      appointment.status === 'REQUESTED' ||
      appointment.status === 'CONFIRMED' ||
      isNoShowFollowUp(appointment);
    return claimableStatus && (!appointment.processingById || isClaimExpired(appointment));
  }, [appointment]);

  const appointmentOverdue = useMemo(
    () => (appointment ? isAppointmentOverdue(appointment, now) : false),
    [appointment, now],
  );

  const canMarkNoShow = Boolean(
    isOwnedByCurrentUser &&
      appointment &&
      appointment.status === 'CONFIRMED' &&
      appointmentOverdue &&
      appointment.arrivalStatus !== 'ARRIVED' &&
      !appointment.checkedInAt,
  );

  const canReschedule = Boolean(
    isOwnedByCurrentUser &&
      appointment &&
      (
        appointment.status === 'REQUESTED' ||
        appointment.status === 'CONFIRMED' ||
        (appointment.status === 'NO_SHOW' && appointment.followUpPending)
      ),
  );

  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 60_000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    if (!appointment) return;
    setIntakeForm({
      reasonForVisit: appointment.reasonForVisit || '',
      visitType: appointment.visitType || '',
      triagePriority: appointment.triagePriority || '',
      triageNote: appointment.triageNote || '',
      insuranceNote: appointment.insuranceNote || '',
      emergencyContactName: appointment.emergencyContactName || '',
      emergencyContactPhone: appointment.emergencyContactPhone || '',
    });
  }, [appointment]);

  useEffect(() => {
    if (!id || !isOwnedByCurrentUser) return;

    const timer = window.setInterval(() => {
      appointmentAction.mutate({
        id,
        action: 'heartbeat',
        body: { holdMinutes: HOLD_MINUTES },
      });
    }, HEARTBEAT_INTERVAL_MS);

    return () => window.clearInterval(timer);
  }, [appointmentAction, id, isOwnedByCurrentUser]);

  useEffect(() => {
    if (!id || !isOwnedByCurrentUser || !accessToken) return;

    const releaseUrl = buildAbsoluteApiUrl(`/admin/appointments/${id}/release-claim`);

    const releaseBestEffort = () => {
      if (releaseOnLeaveRef.current) return;
      releaseOnLeaveRef.current = true;

      void fetch(releaseUrl, {
        method: 'POST',
        keepalive: true,
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${accessToken}`,
        },
        body: '{}',
      }).catch(() => {
        releaseOnLeaveRef.current = false;
      });
    };

    const handleBeforeUnload = () => {
      releaseBestEffort();
    };

    const handlePageHide = () => {
      releaseBestEffort();
    };

    window.addEventListener('beforeunload', handleBeforeUnload);
    window.addEventListener('pagehide', handlePageHide);

    return () => {
      window.removeEventListener('beforeunload', handleBeforeUnload);
      window.removeEventListener('pagehide', handlePageHide);
      releaseBestEffort();
    };
  }, [accessToken, id, isOwnedByCurrentUser]);

  const handleBackToList = async () => {
    if (!isOwnedByCurrentUser) {
      navigate('/app/appointments');
      return;
    }

    releaseOnLeaveRef.current = true;
    try {
      await appointmentAction.mutateAsync({ id, action: 'release-claim' });
      navigate('/app/appointments');
    } catch {
      releaseOnLeaveRef.current = false;
      // useAppointmentAction already shows the backend message.
    }
  };

  const handleClaim = async () => {
    try {
      await appointmentAction.mutateAsync({ id, action: 'claim' });
    } catch {
      // useAppointmentAction already shows the backend message.
    }
  };

  const handleConfirm = async () => {
    try {
      await appointmentAction.mutateAsync({ id, action: 'confirm' });
    } catch {
      // useAppointmentAction already shows the backend message.
    }
  };

  const handleCancel = async () => {
    releaseOnLeaveRef.current = true;
    try {
      await appointmentAction.mutateAsync({ id, action: 'cancel' });
      navigate('/app/appointments');
    } catch {
      releaseOnLeaveRef.current = false;
      // useAppointmentAction already shows the backend message.
    }
  };

  const handleMarkNoShow = async (noShowNote: string) => {
    try {
      await appointmentAction.mutateAsync({
        id,
        action: 'no-show',
        body: { noShowNote: noShowNote.trim() || undefined, note: noShowNote.trim() || undefined },
      });
      setMarkNoShowOpen(false);
    } catch {
      // useAppointmentAction already shows the backend message.
    }
  };

  const handleResolveNoShow = async () => {
    try {
      await appointmentAction.mutateAsync({ id, action: 'resolve-no-show' });
      setResolveNoShowOpen(false);
    } catch {
      // useAppointmentAction already shows the backend message.
    }
  };

  const handleReschedule = async (body: {
    visitDate: string;
    session: BranchSessionType;
    slotStart: string;
    note: string;
  }) => {
    try {
      await appointmentAction.mutateAsync({
        id,
        action: 'reschedule',
        body,
      });
      setRescheduleOpen(false);
    } catch {
      // useAppointmentAction already shows the backend message.
    }
  };

  const handleMarkArrived = async () => {
    await markArrived.mutateAsync(id);
  };

  const handleManualCheckIn = async () => {
    await manualCheckIn.mutateAsync(id);
  };

  const handleSaveIntake = async () => {
    await updateIntake.mutateAsync({
      id,
      body: {
        reasonForVisit: intakeForm.reasonForVisit || undefined,
        visitType: intakeForm.visitType || undefined,
        triagePriority: intakeForm.triagePriority || undefined,
        triageNote: intakeForm.triageNote || undefined,
        insuranceNote: intakeForm.insuranceNote || undefined,
        emergencyContactName: intakeForm.emergencyContactName || undefined,
        emergencyContactPhone: intakeForm.emergencyContactPhone || undefined,
      },
    });
  };

  const timeline = useMemo(
    () => [
      {
        label: 'Lịch được tạo',
        value: formatDateTime(appointment?.createdAt),
        icon: Clock3,
      },
      {
        label: 'Thông tin tiếp nhận',
        value: appointment?.intakeCompletedAt
          ? `${formatDateTime(appointment.intakeCompletedAt)}${appointment.intakeCompletedByName ? ` · ${appointment.intakeCompletedByName}` : ''}`
          : 'Chưa cập nhật',
        icon: ClipboardList,
      },
      {
        label: 'Xác nhận lịch',
        value: appointment?.confirmedAt
          ? `${formatDateTime(appointment.confirmedAt)}${appointment.confirmedByName ? ` · ${appointment.confirmedByName}` : ''}`
          : 'Chưa xác nhận',
        icon: ShieldCheck,
      },
      {
        label: 'Bệnh nhân đã đến',
        value: appointment?.arrivedAt
          ? `${formatDateTime(appointment.arrivedAt)}${appointment.arrivedByName ? ` · ${appointment.arrivedByName}` : ''}`
          : 'Chưa ghi nhận',
        icon: DoorOpen,
      },
      {
        label: 'Check-in',
        value: appointment?.checkedInAt
          ? `${formatDateTime(appointment.checkedInAt)}${appointment.checkedInByName ? ` · ${appointment.checkedInByName}` : ''}`
          : 'Chưa check-in',
        icon: CheckCircle2,
      },
    ],
    [appointment],
  );

  if (isLoading) {
    return <div className="py-12 text-center text-muted-foreground">Đang tải...</div>;
  }

  if (!appointment) {
    return <div className="py-12 text-center text-muted-foreground">Không tìm thấy lịch hẹn</div>;
  }

  const intakeLocked =
    appointment.status === 'COMPLETED' ||
    appointment.status === 'CANCELLED' ||
    appointment.status === 'NO_SHOW';
  const isBusy =
    appointmentAction.isPending ||
    markArrived.isPending ||
    manualCheckIn.isPending ||
    updateIntake.isPending;

  return (
    <div className="space-y-4">
      <AppointmentProcessingHeader
        appointment={appointment}
        canClaim={canClaimAppointment}
        canMarkNoShow={canMarkNoShow}
        canReschedule={canReschedule}
        isOverdue={appointmentOverdue}
        isOwnedByCurrentUser={isOwnedByCurrentUser}
        isBusy={isBusy}
        onBackToList={() => void handleBackToList()}
        onCancel={() => void handleCancel()}
        onCheckIn={() => void handleManualCheckIn()}
        onClaim={() => void handleClaim()}
        onConfirm={() => void handleConfirm()}
        onMarkNoShow={() => setMarkNoShowOpen(true)}
        onMarkArrived={() => void handleMarkArrived()}
        onOpenQueue={() => navigate('/app/reception/queue')}
        onOpenReschedule={() => setRescheduleOpen(true)}
      />

      <AutoCancelWarning appointment={appointment} />

      <NoShowFollowUpPanel
        appointment={appointment}
        canAct={isOwnedByCurrentUser}
        isBusy={isBusy}
        onOpenReschedule={() => setRescheduleOpen(true)}
        onResolve={() => setResolveNoShowOpen(true)}
      />

      <div className="grid gap-4 xl:grid-cols-3">
        <AppointmentScheduleCard appointment={appointment} />
        <AppointmentPatientCard appointment={appointment} />
        <AppointmentReceptionInfoCard
          appointment={appointment}
          canEdit={isOwnedByCurrentUser}
          intakeForm={intakeForm}
          intakeLocked={intakeLocked}
          isSaving={updateIntake.isPending}
          setIntakeForm={setIntakeForm}
          onSave={() => void handleSaveIntake()}
        />
      </div>

      <AppointmentHistoryAccordion timeline={timeline} />

      <MarkNoShowDialog
        open={markNoShowOpen}
        isPending={appointmentAction.isPending}
        onOpenChange={setMarkNoShowOpen}
        onSubmit={(noShowNote) => void handleMarkNoShow(noShowNote)}
      />

      <ResolveNoShowDialog
        open={resolveNoShowOpen}
        isPending={appointmentAction.isPending}
        onOpenChange={setResolveNoShowOpen}
        onConfirm={() => void handleResolveNoShow()}
      />

      <RescheduleDialog
        appointment={appointment}
        open={rescheduleOpen}
        isPending={appointmentAction.isPending}
        onOpenChange={setRescheduleOpen}
        onSubmit={(body) => void handleReschedule(body)}
      />
    </div>
  );
}
