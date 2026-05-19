import { useEffect, useMemo, useRef, useState, type Dispatch, type ReactNode, type SetStateAction } from 'react';
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
  Mail,
  MapPin,
  Phone,
  PhoneCall,
  RotateCw,
  ShieldAlert,
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
import { Checkbox } from '@/components/ui/checkbox';
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
  useAppointmentBookingRestrictionSummary,
  useAppointmentAction,
  useAppointmentSummaryRealtime,
  useMarkWrongAppointmentContact,
  useRecordAppointmentCallOutcome,
  useRescheduleAvailability,
  useUpdateAppointmentIntake,
} from '@/hooks/use-admin-data';
import { toLocalDateInputValue } from '@/lib/date';
import {
  canMarkAppointmentNoShow,
  getAppointmentEndDateTime,
  getCheckedInLateLabel,
  isAppointmentOverdue,
  isLateForCheckIn,
} from '@/lib/appointment-utils';
import {
  CheckedInLateBadge,
  LateCheckInBadge,
  NoShowEligibleBadge,
} from '@/components/OverdueBadge';
import { useAuthStore } from '@/stores/auth-store';
import { cn } from '@/lib/utils';
import {
  CHRONIC_CONDITION_LABELS,
  FUNCTIONAL_IMPACT_LABELS,
  RED_FLAG_LABELS,
  SYMPTOM_ONSET_LABELS,
  TRIAGE_PRIORITY_OPTIONS,
  formatTriagePriority,
  formatTriageSelection,
  formatTriageSelections,
  getConfidenceClass,
  getConfidenceDisplay,
  getPreTriageLevelClass,
  getPreTriageLevelDisplay,
  getPriorityClass,
  getSourceDisplay,
} from '@/lib/triage';
import type {
  Appointment,
  AppointmentCallOutcome,
  BookingRestrictionSummary,
  BranchSessionType,
  TriagePriority,
  TriageReviewStatus,
} from '@/types/api';

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

type ConfirmTriagePayload = {
  note: string | null;
  triagePriority: TriagePriority;
  triageNote: string | null;
  triageReviewStatus: TriageReviewStatus;
  triageOverrideReason: string | null;
};

type AppointmentCancelPayload = {
  reason: string;
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
    case 'PUBLIC_BOOKING':
      return 'Đặt online';
    case 'STAFF_BOOKING':
      return 'Nhân viên đặt';
    case 'WALK_IN':
      return 'Khám trực tiếp';
    default:
      return value || '—';
  }
}

function renderArrivalStatus(value?: string) {
  if (value === 'ARRIVED') return 'Đã đến';
  if (value === 'NOT_ARRIVED') return 'Chưa đến';
  return value || '—';
}

function renderBookingRestrictionLevel(value?: string) {
  switch (value) {
    case 'WARNING':
      return 'Cảnh báo';
    case 'VERIFY_REQUIRED':
      return 'Cần xác minh';
    case 'STAFF_ONLY':
      return 'Staff-only';
    case 'LIFTED':
      return 'Đã gỡ';
    case 'EXPIRED':
      return 'Đã hết hạn';
    case 'NONE':
    case 'CLEAR':
      return 'Không hạn chế';
    default:
      return value || '—';
  }
}

function renderContactStatus(value?: string) {
  switch (value) {
    case 'PHONE_PROVIDED':
      return 'Đã cung cấp SĐT';
    case 'PHONE_STAFF_VERIFIED':
      return 'Đã xác minh qua điện thoại';
    case 'PHONE_UNREACHABLE':
      return 'Chưa liên hệ được';
    case 'PHONE_CONFIRMED_BY_EMAIL':
      return 'Bệnh nhân xác nhận SĐT qua email';
    case 'PHONE_UPDATED_BY_PATIENT':
      return 'Bệnh nhân đã cập nhật SĐT';
    default:
      return value || 'Chưa có trạng thái';
  }
}

function getContactStatusClass(value?: string) {
  switch (value) {
    case 'PHONE_STAFF_VERIFIED':
      return 'border-success/20 bg-success/10 text-success';
    case 'PHONE_CONFIRMED_BY_EMAIL':
    case 'PHONE_UPDATED_BY_PATIENT':
      return 'border-primary/20 bg-primary/10 text-primary';
    case 'PHONE_UNREACHABLE':
      return 'border-warning/20 bg-warning/10 text-warning';
    default:
      return 'border-border bg-muted text-muted-foreground';
  }
}

function renderPatientResponseStatus(value?: string) {
  switch (value) {
    case 'NEED_PATIENT_RESPONSE':
      return 'Cần bệnh nhân phản hồi';
    case 'PATIENT_EMAIL_CONFIRMED':
      return 'Bệnh nhân đã xác nhận qua email';
    case 'WAITING_STAFF_RECALL':
      return 'Đang chờ staff gọi lại';
    default:
      return value || 'Chưa có phản hồi';
  }
}

function getPatientResponseClass(value?: string) {
  switch (value) {
    case 'PATIENT_EMAIL_CONFIRMED':
      return 'border-primary/20 bg-primary/10 text-primary';
    case 'WAITING_STAFF_RECALL':
      return 'border-warning/20 bg-warning/10 text-warning';
    case 'NEED_PATIENT_RESPONSE':
      return 'border-champagne/20 bg-champagne/10 text-champagne';
    default:
      return 'border-border bg-muted text-muted-foreground';
  }
}

function renderCallOutcome(value?: string) {
  switch (value) {
    case 'CONFIRMED':
      return 'Confirmed';
    case 'NO_ANSWER':
      return 'No answer';
    case 'BUSY':
      return 'Busy';
    case 'WRONG_NUMBER':
      return 'Wrong number';
    case 'OTHER':
      return 'Other';
    default:
      return value || 'Chưa gọi';
  }
}

function isFailedCallOutcome(value?: string) {
  return value === 'NO_ANSWER' || value === 'BUSY' || value === 'WRONG_NUMBER';
}

function normalizeAction(value: string) {
  return value.replace(/[\s-]+/g, '_').toUpperCase();
}

function appointmentSupportsContactAction(appointment: Appointment, candidates: string[]) {
  const actions = appointment.contactSupportedActions?.map(normalizeAction) ?? [];
  return candidates.some((candidate) => actions.includes(normalizeAction(candidate)));
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
      <p className="mt-1 break-words text-sm font-semibold text-foreground" title={String(value || '—')}>
        {value || '—'}
      </p>
    </div>
  );
}

function DetailBlock({ label, value }: { label: string; value?: string | number | null }) {
  return (
    <div className="min-w-0 rounded-lg border border-border/70 bg-muted/20 px-3 py-2.5">
      <p className="text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
        {label}
      </p>
      <p className="mt-1 break-words text-sm font-semibold text-foreground">
        {value || '—'}
      </p>
    </div>
  );
}

function InlineBadge({
  children,
  className,
}: {
  children: ReactNode;
  className?: string;
}) {
  return (
    <span
      className={cn(
        'inline-flex items-center whitespace-nowrap rounded-full border px-2.5 py-1 text-xs font-medium leading-none',
        className,
      )}
    >
      {children}
    </span>
  );
}

function formatAuditActor(actorType?: string, actorName?: string | null) {
  if (actorName) return actorName;
  return actorType === 'SYSTEM' ? 'Hệ thống' : 'Staff';
}

function formatAuditLine(log: NonNullable<Appointment['triageAuditLogs']>[number]) {
  const actor = formatAuditActor(log.actorType, log.actorName);
  const fromPriority = log.fromPriority ? formatTriagePriority(log.fromPriority) : '';
  const toPriority = log.toPriority ? formatTriagePriority(log.toPriority) : '';

  if (log.actorType === 'SYSTEM') {
    return toPriority ? `${actor} gợi ý ${toPriority}` : `${actor} ghi nhận sàng lọc`;
  }

  if (fromPriority && toPriority && fromPriority !== toPriority) {
    return `${actor} điều chỉnh ${fromPriority} -> ${toPriority}`;
  }

  return toPriority ? `${actor} chấp nhận ${toPriority}` : `${actor} xác nhận sàng lọc`;
}

function AppointmentProcessingHeader({
  appointment,
  canClaim,
  canMarkNoShow,
  noShowBlockedReason,
  canReschedule,
  isNoShowEligible,
  isLateForCheckIn: lateForCheckIn,
  isOwnedByCurrentUser,
  isBusy,
  onBackToList,
  onCancel,
  onClaim,
  onConfirm,
  onMarkNoShow,
  onOpenQueue,
  onOpenReschedule,
}: {
  appointment: Appointment;
  canClaim: boolean;
  canMarkNoShow: boolean;
  noShowBlockedReason?: string;
  canReschedule: boolean;
  isNoShowEligible: boolean;
  isLateForCheckIn: boolean;
  isOwnedByCurrentUser: boolean;
  isBusy: boolean;
  onBackToList: () => void;
  onCancel: () => void;
  onClaim: () => void;
  onConfirm: () => void;
  onMarkNoShow: () => void;
  onOpenQueue: () => void;
  onOpenReschedule: () => void;
}) {
  const isPending = appointment.status === 'REQUESTED';
  const isConfirmed = appointment.status === 'CONFIRMED';
  const isCheckedIn = appointment.status === 'CHECKED_IN';
  const noShowFollowUp = isNoShowFollowUp(appointment);
  const claimHint = renderClaimHint(appointment, canClaim, isOwnedByCurrentUser);
  const checkedInLateLabel = getCheckedInLateLabel(appointment);

  return (
    <section className="sticky top-4 z-20 rounded-lg border border-border bg-card/95 p-4 shadow-sm backdrop-blur">
      <div className="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <span className="inline-flex items-center rounded-full border border-primary/15 bg-primary/5 px-3 py-1 text-[11px] font-semibold uppercase tracking-[0.18em] text-primary">
              Tiếp nhận
            </span>
            <StatusBadge status={appointment.status} />
            {isNoShowEligible ? <NoShowEligibleBadge /> : null}
            {lateForCheckIn ? <LateCheckInBadge /> : null}
            {checkedInLateLabel ? <CheckedInLateBadge label={checkedInLateLabel} /> : null}
            {noShowFollowUp ? (
              <span className="inline-flex items-center whitespace-nowrap rounded-full border border-warning/25 bg-warning/10 px-2.5 py-1 text-xs font-medium leading-none text-warning">
                Cần xử lý tiếp
              </span>
            ) : null}
            {appointment.receptionQueueNo ? (
              <span className="inline-flex items-center rounded-full border border-border bg-muted/30 px-2.5 py-1 text-xs font-medium text-foreground">
                Số tiếp nhận {appointment.receptionQueueNo}
              </span>
            ) : null}
            {appointment.triagePriority ? (
              <span
                className={cn(
                  'inline-flex items-center whitespace-nowrap rounded-full border px-2.5 py-1 text-xs font-medium leading-none',
                  getPriorityClass(appointment.triagePriority),
                )}
              >
                {formatTriagePriority(appointment.triagePriority)}
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
          {isOwnedByCurrentUser && isConfirmed && !canMarkNoShow && noShowBlockedReason ? (
            <p className="mt-1 text-xs text-muted-foreground">
              {noShowBlockedReason}
            </p>
          ) : null}
        </div>

        <div className="flex flex-wrap items-center gap-2 xl:justify-end">
          {canClaim ? (
            <Button onClick={onClaim} disabled={isBusy}>
              <ShieldCheck className="mr-2 h-4 w-4" />
              Nhận xử lý
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
  const checkedInLateLabel = getCheckedInLateLabel(appointment);

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
        {checkedInLateLabel ? (
          <InfoBlock label="Ghi nhận tiếp nhận" value={checkedInLateLabel} icon={Clock3} />
        ) : null}
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

function AppointmentBookingRestrictionCard({
  summary,
  isLoading,
}: {
  summary?: BookingRestrictionSummary | null;
  isLoading?: boolean;
}) {
  if (isLoading && !summary) {
    return (
      <Card className="border-border/70 shadow-sm">
        <CardHeader className="pb-3">
          <CardTitle className="text-base">Trạng thái đặt lịch của bệnh nhân</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-2">
            <div className="h-4 w-1/2 rounded bg-muted" />
            <div className="h-4 w-2/3 rounded bg-muted" />
          </div>
        </CardContent>
      </Card>
    );
  }

  if (!summary) return null;

  if (summary.clear) {
    return (
      <Card className="border-border/70 shadow-sm">
        <CardHeader className="pb-3">
          <CardTitle className="text-base">Trạng thái đặt lịch của bệnh nhân</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="rounded-lg border border-border/70 bg-muted/20 px-3 py-2.5 text-sm text-muted-foreground">
            Không có hạn chế đặt lịch trong tháng này.
          </p>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card className="border-border/70 shadow-sm">
      <CardHeader className="pb-3">
        <CardTitle className="text-base">Trạng thái đặt lịch của bệnh nhân</CardTitle>
      </CardHeader>
      <CardContent className="grid gap-3 sm:grid-cols-2 xl:grid-cols-1 2xl:grid-cols-2">
        <InfoBlock label="Điểm tháng này" value={summary.monthlyScore.toLocaleString('vi-VN')} />
        <InfoBlock label="Mức hiện tại" value={renderBookingRestrictionLevel(summary.level)} />
        <InfoBlock label="Lý do gần nhất" value={summary.reason} />
        <InfoBlock label="Ngày hết hạn" value={formatDateTime(summary.expiresAt)} />
      </CardContent>
    </Card>
  );
}

function AppointmentContactConfirmationPanel({
  appointment,
  canEdit,
  isPending,
  onOpenCallDialog,
  onOpenWrongContactDialog,
}: {
  appointment: Appointment;
  canEdit: boolean;
  isPending: boolean;
  onOpenCallDialog: () => void;
  onOpenWrongContactDialog: () => void;
}) {
  const canRecordCall = appointment.canRecordCallOutcome !== false;
  const canMarkWrongContact =
    appointment.canMarkWrongContact === true ||
    appointmentSupportsContactAction(appointment, [
      'MARK_WRONG_CONTACT',
      'WRONG_CONTACT',
      'CREATE_WRONG_CONTACT_VIOLATION',
    ]);
  const fallbackEmailLabel = appointment.fallbackEmailSentAt
    ? `Email xác nhận lại đã gửi: ${formatDateTime(appointment.fallbackEmailSentAt)}`
    : null;

  return (
    <Card className="h-fit border-border/70 shadow-sm">
      <CardHeader className="pb-3">
        <div className="flex items-start justify-between gap-3">
          <div>
            <CardTitle className="text-base">Xác nhận liên hệ</CardTitle>
            <CardDescription>
              Theo dõi trạng thái liên hệ qua điện thoại và phản hồi qua email của bệnh nhân.
            </CardDescription>
          </div>
          {appointment.contactStatus ? (
            <InlineBadge className={getContactStatusClass(appointment.contactStatus)}>
              {renderContactStatus(appointment.contactStatus)}
            </InlineBadge>
          ) : null}
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="grid gap-3 sm:grid-cols-2">
          <InfoBlock label="SĐT hiện tại" value={appointment.patientPhone} icon={Phone} />
          <div className="rounded-lg border border-border/70 bg-muted/20 px-3 py-2.5">
            <p className="text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
              Trạng thái liên hệ
            </p>
            <InlineBadge className={cn('mt-2', getContactStatusClass(appointment.contactStatus))}>
              {renderContactStatus(appointment.contactStatus)}
            </InlineBadge>
          </div>
          <InfoBlock
            label="Kết quả cuộc gọi gần nhất"
            value={`${renderCallOutcome(appointment.lastCallOutcome)}${
              appointment.lastCallAt ? ` · ${formatDateTime(appointment.lastCallAt)}` : ''
            }`}
            icon={PhoneCall}
          />
          <div className="rounded-lg border border-border/70 bg-muted/20 px-3 py-2.5">
            <p className="text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
              Phản hồi bệnh nhân
            </p>
            <InlineBadge className={cn('mt-2', getPatientResponseClass(appointment.patientResponseStatus))}>
              {renderPatientResponseStatus(appointment.patientResponseStatus)}
            </InlineBadge>
          </div>
        </div>

        {appointment.lastCallNote || appointment.lastCallByName || fallbackEmailLabel ? (
          <div className="rounded-lg border border-border/70 bg-muted/20 px-3 py-2.5 text-sm leading-6">
            {appointment.lastCallByName ? (
              <p>
                Người ghi nhận gần nhất: <span className="font-medium">{appointment.lastCallByName}</span>
              </p>
            ) : null}
            {appointment.lastCallNote ? <p>Ghi chú: {appointment.lastCallNote}</p> : null}
            {fallbackEmailLabel ? (
              <p className="flex items-center gap-2 text-muted-foreground">
                <Mail className="h-4 w-4" />
                {fallbackEmailLabel}
              </p>
            ) : null}
          </div>
        ) : null}

        <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-border/70 bg-muted/20 px-3 py-2.5">
          <p className="text-sm text-muted-foreground">
            {canEdit
              ? 'Ghi nhận kết quả liên hệ sau mỗi lần gọi để đồng bộ trạng thái chăm sóc.'
              : 'Nhận xử lý lịch hẹn để cập nhật trạng thái liên hệ.'}
          </p>
          <div className="flex flex-wrap gap-2">
            {canRecordCall ? (
              <Button size="sm" onClick={onOpenCallDialog} disabled={!canEdit || isPending}>
                <PhoneCall className="mr-2 h-4 w-4" />
                Ghi nhận cuộc gọi
              </Button>
            ) : null}
            {canMarkWrongContact ? (
              <Button
                size="sm"
                variant="outline"
                onClick={onOpenWrongContactDialog}
                disabled={!canEdit || isPending}
              >
                <ShieldAlert className="mr-2 h-4 w-4" />
                Đánh dấu sai thông tin liên hệ
              </Button>
            ) : null}
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

function AppointmentPreTriageCard({ appointment }: { appointment: Appointment }) {
  const matchedTerms = appointment.preTriageMatchedTerms ?? [];
  const matchedRules = appointment.preTriageMatchedRules ?? [];
  const auditLogs = appointment.triageAuditLogs ?? [];
  const sourceDisplay = getSourceDisplay(appointment.preTriageSource);
  const versions = [
    appointment.preTriageKnowledgeBaseVersion
      ? `Knowledge base: ${appointment.preTriageKnowledgeBaseVersion}`
      : '',
    appointment.preTriageRulesetVersion ? `Ruleset: ${appointment.preTriageRulesetVersion}` : '',
    appointment.preTriageAiModelVersion ? `AI model: ${appointment.preTriageAiModelVersion}` : '',
  ].filter(Boolean);
  const hasPreTriage =
    Boolean(appointment.preTriagePriority || appointment.preTriageLevel) ||
    Boolean(appointment.preTriageSummary) ||
    Boolean(appointment.preTriageReasons?.length) ||
    Boolean(appointment.symptomOnset || appointment.functionalImpact) ||
    Boolean(appointment.chronicConditionOthers?.length) ||
    Boolean(matchedTerms.length || matchedRules.length) ||
    Boolean(appointment.preTriageSource || appointment.preTriageConfidenceLevel) ||
    Boolean(auditLogs.length || appointment.triageReviewStatus);

  if (!hasPreTriage) return null;

  return (
    <Card className="border-border/70 shadow-sm">
      <CardHeader className="pb-3">
        <CardTitle className="text-base">Sàng lọc sơ bộ</CardTitle>
        <CardDescription>
          AI chỉ hỗ trợ phân tích mô tả và lựa chọn nhanh của bệnh nhân. Staff cần xác nhận trước khi mức ưu tiên được dùng trong hàng đợi.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
          <DetailBlock label="Gợi ý ưu tiên" value={formatTriagePriority(appointment.preTriagePriority)} />
          <DetailBlock label="Mức cảnh báo" value={getPreTriageLevelDisplay(appointment.preTriageLevel)} />
          <DetailBlock label="Độ tin cậy" value={getConfidenceDisplay(appointment.preTriageConfidenceLevel)} />
          <DetailBlock label="Nguồn đánh giá" value={sourceDisplay.label} />
        </div>

        <div className="flex flex-wrap gap-2">
          <InlineBadge className={getPriorityClass(appointment.preTriagePriority)}>
            Gợi ý: {formatTriagePriority(appointment.preTriagePriority)}
          </InlineBadge>
          <InlineBadge className={getPreTriageLevelClass(appointment.preTriageLevel)}>
            {getPreTriageLevelDisplay(appointment.preTriageLevel)}
          </InlineBadge>
          <InlineBadge className={getConfidenceClass(appointment.preTriageConfidenceLevel)}>
            Độ tin cậy: {getConfidenceDisplay(appointment.preTriageConfidenceLevel)}
          </InlineBadge>
        </div>

        <div className="rounded-lg border border-border/70 bg-muted/20 px-3 py-2.5 text-sm leading-6">
          {appointment.preTriageSummary || 'Chưa có tóm tắt sàng lọc.'}
        </div>

        <div>
          <p className="text-sm font-medium text-foreground">Lý do gợi ý</p>
          {appointment.preTriageReasons?.length ? (
            <ul className="mt-2 space-y-1 text-sm leading-6 text-muted-foreground">
              {appointment.preTriageReasons.map((reason) => (
                <li key={reason}>- {reason}</li>
              ))}
            </ul>
          ) : (
            <p className="mt-1 text-sm text-muted-foreground">Không có lý do chi tiết</p>
          )}
        </div>

        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
          <DetailBlock
            label="Khởi phát"
            value={formatTriageSelection(appointment.symptomOnset, SYMPTOM_ONSET_LABELS)}
          />
          <DetailBlock
            label="Bệnh nền / nguy cơ"
            value={formatTriageSelections(appointment.chronicConditions, CHRONIC_CONDITION_LABELS)}
          />
          <DetailBlock
            label="Bệnh nền khác"
            value={
              appointment.chronicConditionOthers?.length
                ? appointment.chronicConditionOthers.join(', ')
                : 'Không có bệnh nền khác được nhập.'
            }
          />
          <DetailBlock
            label="Ảnh hưởng sinh hoạt"
            value={formatTriageSelection(appointment.functionalImpact, FUNCTIONAL_IMPACT_LABELS)}
          />
          <DetailBlock
            label="Dấu hiệu cần chú ý"
            value={formatTriageSelections(appointment.redFlagSelections, RED_FLAG_LABELS)}
          />
        </div>

        {appointment.preTriageLevel === 'RED_FLAG' ? (
          <div className="flex gap-3 rounded-lg border border-destructive/20 bg-destructive/5 px-3 py-2.5 text-sm leading-6 text-destructive">
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
            <p>
              Trường hợp này có dấu hiệu cần xác minh trước khi xác nhận lịch. Nếu triệu chứng đang diễn tiến nặng, staff nên liên hệ bệnh nhân và hướng dẫn đến cơ sở cấp cứu phù hợp.
            </p>
          </div>
        ) : null}

        <Accordion type="single" collapsible>
          <AccordionItem value="triage-detail" className="rounded-lg border border-border/70 px-3">
            <AccordionTrigger className="py-3 text-left text-sm font-medium hover:no-underline">
              Xem chi tiết đánh giá
            </AccordionTrigger>
            <AccordionContent className="space-y-4 pb-4">
              <p className="text-sm leading-6 text-muted-foreground">
                Các từ khóa và rule bên dưới giúp staff kiểm chứng vì sao hệ thống gợi ý mức ưu tiên này.
              </p>

              <div>
                <p className="text-sm font-medium text-foreground">Từ khóa nhận diện</p>
                {matchedTerms.length ? (
                  <div className="mt-2 space-y-2">
                    {matchedTerms.map((term, index) => (
                      <div
                        key={`${term.term}-${term.code}-${index}`}
                        className="rounded-lg border border-border/70 bg-muted/20 px-3 py-2 text-sm"
                      >
                        <p className="font-medium text-foreground">
                          "{term.term}" - {term.label || term.code || 'Không rõ'}
                        </p>
                        <p className="mt-1 text-xs text-muted-foreground">
                          {term.code || 'Không có code'} · {term.category || 'Không rõ nhóm'} · {term.source || 'Không rõ nguồn'}
                        </p>
                        {term.evidenceText ? (
                          <p className="mt-1 text-xs text-muted-foreground">{term.evidenceText}</p>
                        ) : null}
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="mt-1 text-sm text-muted-foreground">
                    Không có từ khóa khớp từ knowledge base.
                  </p>
                )}
              </div>

              <div>
                <p className="text-sm font-medium text-foreground">Rule kích hoạt</p>
                {matchedRules.length ? (
                  <div className="mt-2 space-y-2">
                    {matchedRules.map((rule, index) => (
                      <div
                        key={`${rule.id}-${index}`}
                        className="rounded-lg border border-border/70 bg-muted/20 px-3 py-2 text-sm"
                      >
                        <p className="font-medium text-foreground">{rule.id || 'Rule không có mã'}</p>
                        <p className="mt-1 text-sm text-muted-foreground">
                          Lý do: {rule.reason || 'Không có lý do chi tiết'}
                        </p>
                        <p className="mt-1 text-xs text-muted-foreground">
                          Kết quả: {formatTriagePriority(rule.priority)} / {getPreTriageLevelDisplay(rule.level)}
                        </p>
                        {rule.matchedCodes?.length ? (
                          <p className="mt-1 text-xs text-muted-foreground">
                            Mã khớp: {rule.matchedCodes.join(', ')}
                          </p>
                        ) : null}
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="mt-1 text-sm text-muted-foreground">
                    Không có rule chi tiết được kích hoạt.
                  </p>
                )}
              </div>

              <div className="grid gap-3 md:grid-cols-2">
                <DetailBlock
                  label="Nguồn đánh giá"
                  value={`${appointment.preTriageSource || 'Không rõ'}${sourceDisplay.description ? ` - ${sourceDisplay.description}` : ''}`}
                />
                <DetailBlock
                  label="Độ tin cậy"
                  value={getConfidenceDisplay(appointment.preTriageConfidenceLevel)}
                />
              </div>
              <p className="text-xs leading-5 text-muted-foreground">
                Độ tin cậy phản ánh mức độ rõ ràng của thông tin đầu vào, không phải xác suất chẩn đoán.
              </p>

              <div>
                <p className="text-sm font-medium text-foreground">Phiên bản đánh giá</p>
                {versions.length ? (
                  <div className="mt-2 flex flex-wrap gap-2">
                    {versions.map((version) => (
                      <span
                        key={version}
                        className="rounded-full border border-border bg-muted px-2.5 py-1 text-xs text-muted-foreground"
                      >
                        {version}
                      </span>
                    ))}
                  </div>
                ) : (
                  <p className="mt-1 text-sm text-muted-foreground">Chưa có thông tin phiên bản.</p>
                )}
              </div>
            </AccordionContent>
          </AccordionItem>
        </Accordion>

        <Accordion type="single" collapsible>
          <AccordionItem value="triage-audit" className="rounded-lg border border-border/70 px-3">
            <AccordionTrigger className="py-3 text-left text-sm font-medium hover:no-underline">
              Lịch sử sàng lọc
            </AccordionTrigger>
            <AccordionContent className="space-y-3 pb-4">
              {auditLogs.length ? (
                auditLogs.map((log) => (
                  <div key={String(log.id ?? `${log.createdAt}-${log.action}`)} className="rounded-lg border border-border/70 bg-muted/20 px-3 py-2 text-sm">
                    <p className="font-medium text-foreground">
                      {log.createdAt ? `${formatDateTime(log.createdAt)} - ` : ''}
                      {formatAuditLine(log)}
                    </p>
                    {log.reason ? (
                      <p className="mt-1 text-sm text-muted-foreground">Lý do: {log.reason}</p>
                    ) : null}
                  </div>
                ))
              ) : appointment.triageReviewStatus ? (
                <div className="rounded-lg border border-border/70 bg-muted/20 px-3 py-2 text-sm">
                  <p className="font-medium text-foreground">
                    {appointment.triageReviewStatus === 'OVERRIDDEN'
                      ? `Staff đã điều chỉnh: ${formatTriagePriority(appointment.triagePriority)}`
                      : `Staff đã xác nhận: ${formatTriagePriority(appointment.triagePriority)}`}
                  </p>
                  <p className="mt-1 text-sm text-muted-foreground">
                    {appointment.triageReviewedAt ? `${formatDateTime(appointment.triageReviewedAt)} · ` : ''}
                    {appointment.triageReviewedByName || 'Không rõ staff'}
                  </p>
                  {appointment.triageOverrideReason ? (
                    <p className="mt-1 text-sm text-muted-foreground">
                      Lý do: {appointment.triageOverrideReason}
                    </p>
                  ) : null}
                </div>
              ) : (
                <p className="text-sm text-muted-foreground">Chưa có lịch sử xác nhận.</p>
              )}
            </AccordionContent>
          </AccordionItem>
        </Accordion>
      </CardContent>
    </Card>
  );
}

function ConfirmTriageDialog({
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
  onSubmit: (payload: ConfirmTriagePayload) => void;
}) {
  const [selectedPriority, setSelectedPriority] = useState<TriagePriority>('ROUTINE');
  const [triageNote, setTriageNote] = useState('');
  const [confirmNote, setConfirmNote] = useState('');
  const [overrideReason, setOverrideReason] = useState('');
  const [overrideError, setOverrideError] = useState('');
  const suggestedPriority = appointment.preTriagePriority ?? null;
  const isOverride = Boolean(suggestedPriority && selectedPriority !== suggestedPriority);

  useEffect(() => {
    if (!open) return;
    setSelectedPriority(appointment.preTriagePriority ?? 'ROUTINE');
    setTriageNote(appointment.preTriageSummary || appointment.triageNote || '');
    setConfirmNote('');
    setOverrideReason('');
    setOverrideError('');
  }, [
    appointment.id,
    appointment.preTriagePriority,
    appointment.preTriageSummary,
    appointment.triageNote,
    open,
  ]);

  const submit = () => {
    const trimmedOverrideReason = overrideReason.trim();
    if (isOverride && !trimmedOverrideReason) {
      setOverrideError('Vui lòng nhập lý do khi điều chỉnh mức ưu tiên khác với gợi ý.');
      return;
    }

    onSubmit({
      note: confirmNote.trim() || null,
      triagePriority: selectedPriority,
      triageNote: triageNote.trim() || appointment.preTriageSummary || null,
      triageReviewStatus: isOverride ? 'OVERRIDDEN' : suggestedPriority ? 'ACCEPTED' : 'MANUAL',
      triageOverrideReason: isOverride ? trimmedOverrideReason : null,
    });
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Xác nhận lịch hẹn</DialogTitle>
          <DialogDescription>
            AI hỗ trợ sàng lọc sơ bộ từ mô tả và lựa chọn nhanh của bệnh nhân. Staff cần xác nhận hoặc điều chỉnh mức ưu tiên trước khi lịch hẹn được đưa vào hàng đợi.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="rounded-lg border border-border/70 bg-muted/20 px-3 py-2.5">
              <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                Gợi ý pre-triage
              </p>
              <span
                className={cn(
                  'mt-2 inline-flex items-center whitespace-nowrap rounded-full border px-2.5 py-1 text-xs font-medium leading-none',
                  getPriorityClass(suggestedPriority ?? 'ROUTINE'),
                )}
              >
                {formatTriagePriority(suggestedPriority ?? 'ROUTINE')}
              </span>
            </div>

            <div>
              <label className="mb-1.5 block text-sm font-medium">Mức ưu tiên staff xác nhận</label>
              <Select
                value={selectedPriority}
                onValueChange={(value) => {
                  setSelectedPriority(value as TriagePriority);
                  setOverrideError('');
                }}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Chọn mức ưu tiên" />
                </SelectTrigger>
                <SelectContent>
                  {TRIAGE_PRIORITY_OPTIONS.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label} - {option.description}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          <div>
            <label className="mb-1.5 block text-sm font-medium">Ghi chú sàng lọc</label>
            <Textarea
              rows={3}
              value={triageNote}
              onChange={(event) => setTriageNote(event.target.value)}
              placeholder="Ghi chú mức ưu tiên"
            />
          </div>

          <div>
            <label className="mb-1.5 block text-sm font-medium">Ghi chú xác nhận</label>
            <Textarea
              rows={2}
              value={confirmNote}
              onChange={(event) => setConfirmNote(event.target.value)}
              placeholder="Ghi chú nội bộ khi xác nhận lịch, nếu có"
            />
          </div>

          {isOverride ? (
            <div>
              <label className="mb-1.5 block text-sm font-medium">
                Lý do điều chỉnh <span className="text-destructive">*</span>
              </label>
              <Textarea
                rows={2}
                value={overrideReason}
                onChange={(event) => {
                  setOverrideReason(event.target.value);
                  setOverrideError('');
                }}
                placeholder="Ví dụ: đã gọi xác minh, triệu chứng nhẹ hơn mô tả ban đầu"
              />
              {overrideError ? <p className="mt-1 text-xs text-destructive">{overrideError}</p> : null}
            </div>
          ) : null}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={isPending}>
            Hủy
          </Button>
          <Button onClick={submit} disabled={isPending}>
            {isPending ? 'Đang xác nhận...' : 'Xác nhận lịch hẹn'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function CancelAppointmentDialog({
  open,
  isPending,
  onOpenChange,
  onSubmit,
}: {
  open: boolean;
  isPending: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmit: (payload: AppointmentCancelPayload) => void;
}) {
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    if (!open) return;
    setReason('');
    setError('');
  }, [open]);

  const submit = () => {
    const trimmedReason = reason.trim();
    if (!trimmedReason) {
      setError('Vui lòng nhập lý do hủy lịch.');
      return;
    }

    onSubmit({ reason: trimmedReason });
  };

  return (
    <Dialog
      open={open}
      onOpenChange={(nextOpen) => {
        if (!isPending) onOpenChange(nextOpen);
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Hủy lịch hẹn</DialogTitle>
          <DialogDescription>
            Thao tác này sẽ hủy lịch hẹn. Vui lòng nhập lý do để phục vụ truy vết và chăm sóc bệnh nhân.
          </DialogDescription>
        </DialogHeader>

        <div>
          <label className="mb-1.5 block text-sm font-medium" htmlFor="appointment-cancel-reason">
            Lý do hủy lịch <span className="text-destructive">*</span>
          </label>
          <Textarea
            id="appointment-cancel-reason"
            rows={4}
            value={reason}
            onChange={(event) => {
              setReason(event.target.value);
              if (error) setError('');
            }}
            onBlur={() => {
              if (!reason.trim()) setError('Vui lòng nhập lý do hủy lịch.');
            }}
            disabled={isPending}
            aria-invalid={Boolean(error)}
            aria-describedby={error ? 'appointment-cancel-reason-error' : undefined}
            placeholder="Nhập lý do hủy lịch hẹn"
          />
          {error ? (
            <p id="appointment-cancel-reason-error" className="mt-1.5 text-sm text-destructive">
              {error}
            </p>
          ) : null}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={isPending}>
            Đóng
          </Button>
          <Button variant="destructive" onClick={submit} disabled={isPending}>
            {isPending ? 'Đang hủy lịch...' : 'Xác nhận hủy lịch'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
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
    <Card className="h-fit border-border/70 shadow-sm">
      <CardHeader className="pb-3">
        <CardTitle className="text-base">Thông tin tiếp nhận</CardTitle>
        <CardDescription>
          Kiểm tra thông tin bệnh nhân, lý do khám và ghi chú tiếp nhận trước khi xử lý lịch.
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
                  <label className="mb-1.5 block text-sm font-medium">Mức ưu tiên đã xác nhận</label>
                  <div className="rounded-md border border-border bg-muted/20 px-3 py-2">
                    <span
                      className={cn(
                        'inline-flex items-center whitespace-nowrap rounded-full border px-2.5 py-1 text-xs font-medium leading-none',
                        getPriorityClass(appointment.triagePriority),
                      )}
                    >
                      {formatTriagePriority(appointment.triagePriority)}
                    </span>
                    <p className="mt-2 text-xs leading-5 text-muted-foreground">
                      Mức ưu tiên chính thức được xác nhận trong dialog xác nhận lịch.
                    </p>
                  </div>
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
              Mở khi cần đối chiếu các mốc xác nhận, đã đến và tiếp nhận tại quầy.
            </p>
          </div>
        </AccordionTrigger>
        <AccordionContent className="pb-4">
          <div className="space-y-3">
            {timeline.map((item) => (
              <div
                key={item.label}
                className="flex gap-3 rounded-xl border border-border/70 bg-muted/20 p-3"
              >
                <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary">
                  <item.icon className="h-4 w-4" />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-semibold text-foreground">{item.label}</p>
                  <p className="mt-1 break-words text-sm leading-6 text-muted-foreground">
                    {item.value || '—'}
                  </p>
                </div>
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
            <CardTitle className="text-base">Xử lý tiếp lịch không đến</CardTitle>
            <CardDescription>
              Kiểm tra thông tin không đến trước khi dời lịch hoặc đánh dấu đã xử lý.
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
              {pending ? 'Cần xử lý tiếp' : 'Đã xử lý'}
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
              Đánh dấu đã xử lý
            </Button>
          </div>
        ) : null}

        {pending && !canAct ? (
          <p className="rounded-lg border border-border/70 bg-card/70 px-3 py-2.5 text-sm text-muted-foreground">
            Bạn cần nhận xử lý trước khi dời lịch hoặc đánh dấu đã xử lý.
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
            Chỉ dùng khi lịch đã qua etaEnd và bệnh nhân chưa được tiếp nhận tại quầy.
          </DialogDescription>
        </DialogHeader>
        <div>
          <label className="mb-1.5 block text-sm font-medium">Ghi chú xử lý</label>
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
          <DialogTitle>Đánh dấu đã xử lý</DialogTitle>
          <DialogDescription>
            Xác nhận đánh dấu lịch hẹn này đã được xử lý?
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={isPending}>
            Hủy
          </Button>
          <Button onClick={onConfirm} disabled={isPending}>
            {isPending ? 'Đang xử lý...' : 'Đánh dấu đã xử lý'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

const CALL_OUTCOME_OPTIONS: Array<{ value: AppointmentCallOutcome; label: string }> = [
  { value: 'CONFIRMED', label: 'Confirmed' },
  { value: 'NO_ANSWER', label: 'No answer' },
  { value: 'BUSY', label: 'Busy' },
  { value: 'WRONG_NUMBER', label: 'Wrong number' },
  { value: 'OTHER', label: 'Other' },
];

function RecordCallOutcomeDialog({
  open,
  isPending,
  onOpenChange,
  onSubmit,
}: {
  open: boolean;
  isPending: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmit: (payload: {
    outcome: AppointmentCallOutcome;
    note?: string;
    sendFallbackEmail?: boolean;
  }) => void;
}) {
  const [outcome, setOutcome] = useState<AppointmentCallOutcome>('CONFIRMED');
  const [note, setNote] = useState('');
  const [sendFallbackEmail, setSendFallbackEmail] = useState(false);

  useEffect(() => {
    if (!open) return;
    setOutcome('CONFIRMED');
    setNote('');
    setSendFallbackEmail(false);
  }, [open]);

  const shouldOfferFallback = isFailedCallOutcome(outcome);

  useEffect(() => {
    if (!shouldOfferFallback) setSendFallbackEmail(false);
  }, [shouldOfferFallback]);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Ghi nhận kết quả cuộc gọi</DialogTitle>
          <DialogDescription>
            Kết quả này chỉ cập nhật trạng thái liên hệ nội bộ. Email xác nhận lại chỉ gửi khi staff chọn tùy chọn bên dưới.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <div>
            <label className="mb-1.5 block text-sm font-medium">Kết quả cuộc gọi</label>
            <Select value={outcome} onValueChange={(value) => setOutcome(value as AppointmentCallOutcome)}>
              <SelectTrigger>
                <SelectValue placeholder="Chọn kết quả" />
              </SelectTrigger>
              <SelectContent>
                {CALL_OUTCOME_OPTIONS.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {shouldOfferFallback ? (
            <label className="flex items-start gap-3 rounded-lg border border-border/70 bg-muted/20 px-3 py-2.5 text-sm">
              <Checkbox
                checked={sendFallbackEmail}
                onCheckedChange={(checked) => setSendFallbackEmail(checked === true)}
              />
              <span>
                <span className="block font-medium text-foreground">
                  Gửi email xác nhận lại cho bệnh nhân
                </span>
                <span className="block text-muted-foreground">
                  Bệnh nhân có thể giữ lịch, yêu cầu gọi lại, cập nhật số điện thoại hoặc hủy lịch qua liên kết trong email.
                </span>
              </span>
            </label>
          ) : null}

          <div>
            <label className="mb-1.5 block text-sm font-medium">Ghi chú nội bộ</label>
            <Textarea
              rows={3}
              value={note}
              onChange={(event) => setNote(event.target.value)}
              placeholder="Ví dụ: gọi lúc 10:30, thuê bao báo bận"
            />
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={isPending}>
            Hủy
          </Button>
          <Button
            onClick={() =>
              onSubmit({
                outcome,
                note: note.trim() || undefined,
                sendFallbackEmail: shouldOfferFallback ? sendFallbackEmail : false,
              })
            }
            disabled={isPending}
          >
            {isPending ? 'Đang ghi nhận...' : 'Ghi nhận'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function WrongContactDialog({
  open,
  isPending,
  onOpenChange,
  onSubmit,
}: {
  open: boolean;
  isPending: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmit: (reason: string) => void;
}) {
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    if (!open) return;
    setReason('');
    setError('');
  }, [open]);

  const submit = () => {
    const trimmedReason = reason.trim();
    if (!trimmedReason) {
      setError('Vui lòng nhập lý do.');
      return;
    }
    onSubmit(trimmedReason);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Đánh dấu sai thông tin liên hệ</DialogTitle>
          <DialogDescription>
            Thao tác này dành cho staff khi xác định số điện thoại không phù hợp với bệnh nhân đặt lịch.
          </DialogDescription>
        </DialogHeader>
        <div>
          <label className="mb-1.5 block text-sm font-medium">Lý do</label>
          <Textarea
            rows={4}
            value={reason}
            onChange={(event) => {
              setReason(event.target.value);
              setError('');
            }}
            placeholder="Ví dụ: người nghe xác nhận không quen bệnh nhân này"
          />
          {error ? <p className="mt-1 text-xs text-destructive">{error}</p> : null}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={isPending}>
            Hủy
          </Button>
          <Button onClick={submit} disabled={isPending}>
            {isPending ? 'Đang ghi nhận...' : 'Ghi nhận'}
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
  const bookingRestrictionSummaryQuery = useAppointmentBookingRestrictionSummary(id, {
    enabled: Boolean(id),
  });

  useAppointmentSummaryRealtime(undefined, { appointmentId: id });

  const appointmentAction = useAppointmentAction();
  const updateIntake = useUpdateAppointmentIntake();
  const recordCallOutcome = useRecordAppointmentCallOutcome();
  const markWrongContact = useMarkWrongAppointmentContact();
  const [now, setNow] = useState(() => new Date());
  const [markNoShowOpen, setMarkNoShowOpen] = useState(false);
  const [resolveNoShowOpen, setResolveNoShowOpen] = useState(false);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [rescheduleOpen, setRescheduleOpen] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [recordCallOpen, setRecordCallOpen] = useState(false);
  const [wrongContactOpen, setWrongContactOpen] = useState(false);
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

  const appointmentNoShowEligible = useMemo(
    () => (appointment ? isAppointmentOverdue(appointment, now) : false),
    [appointment, now],
  );

  const appointmentLateForCheckIn = useMemo(
    () => (appointment && !appointmentNoShowEligible ? isLateForCheckIn(appointment, now) : false),
    [appointment, appointmentNoShowEligible, now],
  );

  const canMarkNoShow = Boolean(
    isOwnedByCurrentUser &&
      appointment &&
      canMarkAppointmentNoShow(appointment, now),
  );

  const cannotDetermineNoShowEligibility = Boolean(
    appointment &&
      appointment.status === 'CONFIRMED' &&
      !appointment.checkedInAt &&
      !getAppointmentEndDateTime(appointment),
  );
  const noShowBlockedReason =
    appointment?.noShowBlockedReason ||
    (cannotDetermineNoShowEligibility
      ? 'Không xác định được điều kiện no-show vì chưa có etaEnd.'
      : undefined);

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

  const handleConfirm = async (body: ConfirmTriagePayload) => {
    try {
      await appointmentAction.mutateAsync({ id, action: 'confirm', body });
      setConfirmOpen(false);
    } catch {
      // useAppointmentAction already shows the backend message.
    }
  };

  const handleCancel = async (body: AppointmentCancelPayload) => {
    releaseOnLeaveRef.current = true;
    try {
      await appointmentAction.mutateAsync({ id, action: 'cancel', body });
      setCancelOpen(false);
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

  const handleSaveIntake = async () => {
    await updateIntake.mutateAsync({
      id,
      body: {
        reasonForVisit: intakeForm.reasonForVisit || undefined,
        visitType: intakeForm.visitType || undefined,
        triageNote: intakeForm.triageNote || undefined,
        insuranceNote: intakeForm.insuranceNote || undefined,
        emergencyContactName: intakeForm.emergencyContactName || undefined,
        emergencyContactPhone: intakeForm.emergencyContactPhone || undefined,
      },
    });
  };

  const handleRecordCallOutcome = async (body: {
    outcome: AppointmentCallOutcome;
    note?: string;
    sendFallbackEmail?: boolean;
  }) => {
    try {
      await recordCallOutcome.mutateAsync({ id, body });
      setRecordCallOpen(false);
    } catch {
      // Mutation hook shows the backend message.
    }
  };

  const handleWrongContact = async (reason: string) => {
    try {
      await markWrongContact.mutateAsync({ id, body: { reason } });
      setWrongContactOpen(false);
    } catch {
      // Mutation hook shows the backend message.
    }
  };

  const timeline = useMemo(
    () => {
      const checkedInLateLabel = appointment ? getCheckedInLateLabel(appointment) : null;

      return [
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
          label: 'Tiếp nhận tại quầy',
          value: appointment?.checkedInAt
            ? `${formatDateTime(appointment.checkedInAt)}${appointment.checkedInByName ? ` · ${appointment.checkedInByName}` : ''}${checkedInLateLabel ? ` · ${checkedInLateLabel}` : ''}`
            : 'Chưa tiếp nhận',
          icon: CheckCircle2,
        },
      ];
    },
    [appointment],
  );

  if (isLoading) {
    return <div className="py-12 text-center text-muted-foreground">Đang tải...</div>;
  }

  if (!appointment) {
    return <div className="py-12 text-center text-muted-foreground">Không tìm thấy lịch hẹn</div>;
  }

  const bookingRestrictionSummary =
    bookingRestrictionSummaryQuery.data ?? appointment.bookingRestrictionSummary ?? null;
  const intakeLocked =
    appointment.status === 'COMPLETED' ||
    appointment.status === 'CANCELLED' ||
    appointment.status === 'NO_SHOW';
  const isBusy =
    appointmentAction.isPending ||
    updateIntake.isPending ||
    recordCallOutcome.isPending ||
    markWrongContact.isPending;

  return (
    <div className="space-y-4">
      <AppointmentProcessingHeader
        appointment={appointment}
        canClaim={canClaimAppointment}
        canMarkNoShow={canMarkNoShow}
        noShowBlockedReason={noShowBlockedReason}
        canReschedule={canReschedule}
        isNoShowEligible={appointmentNoShowEligible}
        isLateForCheckIn={appointmentLateForCheckIn}
        isOwnedByCurrentUser={isOwnedByCurrentUser}
        isBusy={isBusy}
        onBackToList={() => void handleBackToList()}
        onCancel={() => setCancelOpen(true)}
        onClaim={() => void handleClaim()}
        onConfirm={() => setConfirmOpen(true)}
        onMarkNoShow={() => setMarkNoShowOpen(true)}
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

      <div className="space-y-4">
        <div className="grid items-start gap-4 xl:grid-cols-3">
          <AppointmentScheduleCard appointment={appointment} />
          <AppointmentPatientCard appointment={appointment} />
          <AppointmentBookingRestrictionCard
            summary={bookingRestrictionSummary}
            isLoading={bookingRestrictionSummaryQuery.isLoading}
          />
        </div>

        <div className="grid items-start gap-4 xl:grid-cols-[minmax(0,0.95fr)_minmax(460px,1.05fr)]">
          <div className="space-y-4 self-start">
            <AppointmentContactConfirmationPanel
              appointment={appointment}
              canEdit={isOwnedByCurrentUser}
              isPending={isBusy}
              onOpenCallDialog={() => setRecordCallOpen(true)}
              onOpenWrongContactDialog={() => setWrongContactOpen(true)}
            />

            <AppointmentHistoryAccordion timeline={timeline} />
          </div>

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
      </div>

      <AppointmentPreTriageCard appointment={appointment} />

      <ConfirmTriageDialog
        appointment={appointment}
        open={confirmOpen}
        isPending={appointmentAction.isPending}
        onOpenChange={setConfirmOpen}
        onSubmit={(body) => void handleConfirm(body)}
      />

      <CancelAppointmentDialog
        open={cancelOpen}
        isPending={appointmentAction.isPending}
        onOpenChange={setCancelOpen}
        onSubmit={(body) => void handleCancel(body)}
      />

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

      <RecordCallOutcomeDialog
        open={recordCallOpen}
        isPending={recordCallOutcome.isPending}
        onOpenChange={setRecordCallOpen}
        onSubmit={(body) => void handleRecordCallOutcome(body)}
      />

      <WrongContactDialog
        open={wrongContactOpen}
        isPending={markWrongContact.isPending}
        onOpenChange={setWrongContactOpen}
        onSubmit={(reason) => void handleWrongContact(reason)}
      />
    </div>
  );
}
