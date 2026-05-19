import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Eye,
  MoreHorizontal,
  Plus,
  RotateCcw,
  Search,
  ShieldOff,
  Unlock,
} from 'lucide-react';
import { DataTable, type Column } from '@/components/DataTable';
import { EmptyState } from '@/components/EmptyState';
import { ErrorState } from '@/components/ErrorState';
import { LoadingSkeleton } from '@/components/LoadingSkeleton';
import { PageHeader } from '@/components/PageHeader';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
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
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { Input } from '@/components/ui/input';
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Textarea } from '@/components/ui/textarea';
import {
  useBookingRestrictionDetail,
  useBookingRestrictions,
  useCreateBookingOverrideOnce,
  useCreateBookingStaffPardon,
  useCreateManualBookingViolation,
  useLiftBookingRestriction,
  useVoidBookingViolationEvent,
} from '@/hooks/use-admin-data';
import { useDebouncedValue } from '@/hooks/use-debounced-value';
import { statusToneClasses } from '@/lib/status-style-classes';
import { cn } from '@/lib/utils';
import type {
  BookingRestriction,
  BookingRestrictionOverrideResponse,
  BookingRestrictionListQueryParams,
  BookingViolationEvent,
} from '@/types/api';

type RestrictionTab = 'active' | 'warning' | 'unlocked';

type RestrictionAction =
  | { type: 'lift'; restriction: BookingRestriction }
  | { type: 'override'; restriction: BookingRestriction }
  | { type: 'pardon'; restriction: BookingRestriction }
  | { type: 'manual'; restriction: BookingRestriction }
  | { type: 'void'; event: BookingViolationEvent; restrictionId?: string };

const tabLabels: Record<RestrictionTab, string> = {
  active: 'Đang hạn chế',
  warning: 'Cảnh báo',
  unlocked: 'Đã mở khóa',
};

const restrictionTabs = Object.keys(tabLabels) as RestrictionTab[];

function formatDateTime(value?: string) {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('vi-VN');
}

function formatNumber(value?: number) {
  return Number(value ?? 0).toLocaleString('vi-VN');
}

function getHttpStatus(error: unknown) {
  if (typeof error !== 'object' || error === null) return undefined;
  const response = (error as { response?: { status?: unknown } }).response;
  return typeof response?.status === 'number' ? response.status : undefined;
}

function getRestrictionEmptyMessage(tab: RestrictionTab) {
  switch (tab) {
    case 'warning':
      return 'Hiện không có bệnh nhân trong mức cảnh báo.';
    case 'unlocked':
      return 'Chưa có hồ sơ đã được mở khóa.';
    case 'active':
    default:
      return 'Hiện không có bệnh nhân đang bị hạn chế đặt lịch.';
  }
}

function getRestrictionRequestIdentity(restriction: BookingRestriction) {
  return {
    ...(restriction.id ? { restrictionId: restriction.id } : {}),
    ...(restriction.patientId ? { patientId: restriction.patientId } : {}),
    ...(restriction.appointmentId ? { appointmentId: restriction.appointmentId } : {}),
    ...(restriction.patientPhone ? { phone: restriction.patientPhone } : {}),
    ...(restriction.patientFullName ? { fullName: restriction.patientFullName } : {}),
    ...(restriction.patientDob ? { dob: restriction.patientDob } : {}),
    ...(restriction.patientEmail ? { email: restriction.patientEmail } : {}),
  };
}

function normalizeActionName(value: string) {
  return value.replace(/[\s-]+/g, '_').toUpperCase();
}

function supportsAction(restriction: BookingRestriction, action: string) {
  return Array.isArray(restriction.supportedActions)
    && restriction.supportedActions.map(normalizeActionName).includes(normalizeActionName(action));
}

function isTerminalRestriction(restriction: BookingRestriction) {
  const status = String(restriction.status || '').toUpperCase();
  const level = String(restriction.level || '').toUpperCase();
  return status === 'LIFTED' || status === 'EXPIRED' || level === 'LIFTED' || level === 'EXPIRED';
}

function isActiveRestriction(restriction: BookingRestriction) {
  const status = String(restriction.status || '').toUpperCase();
  const level = String(restriction.level || '').toUpperCase();
  const activeValues = ['ACTIVE', 'WARNING', 'VERIFY_REQUIRED', 'STAFF_ONLY'];

  return !isTerminalRestriction(restriction) && (
    activeValues.includes(status) ||
    (!status && activeValues.includes(level))
  );
}

function hasRestrictionIdentityInfo(restriction: BookingRestriction) {
  return Boolean(
    restriction.patientId ||
      restriction.appointmentId ||
      restriction.patientPhone ||
      restriction.patientEmail ||
      restriction.patientFullName ||
      restriction.patientDob,
  );
}

function canLift(restriction: BookingRestriction) {
  return !isTerminalRestriction(restriction) && (
    restriction.canLift === true ||
    supportsAction(restriction, 'LIFT') ||
    (restriction.canLift !== false && isActiveRestriction(restriction))
  );
}

function canOverrideOnce(restriction: BookingRestriction) {
  return !isTerminalRestriction(restriction) && (
    restriction.canOverrideOnce === true ||
    supportsAction(restriction, 'OVERRIDE_ONCE') ||
    (restriction.canOverrideOnce !== false && isActiveRestriction(restriction))
  );
}

function canStaffPardon(restriction: BookingRestriction) {
  return (
    restriction.canStaffPardon === true ||
    supportsAction(restriction, 'STAFF_PARDON') ||
    (restriction.canStaffPardon !== false &&
      hasRestrictionIdentityInfo(restriction) &&
      !isTerminalRestriction(restriction))
  );
}

function canCreateManualViolation(restriction: BookingRestriction) {
  return (
    restriction.canCreateManualViolation === true ||
    supportsAction(restriction, 'CREATE_MANUAL_VIOLATION') ||
    (restriction.canCreateManualViolation !== false && hasRestrictionIdentityInfo(restriction))
  );
}

function getRestrictionDisplayLevel(restriction: BookingRestriction) {
  const status = String(restriction.status || '').toUpperCase();
  if (status === 'LIFTED' || status === 'EXPIRED') return status;
  return restriction.level;
}

function getRestrictionLabel(level?: string) {
  switch (String(level || '').toUpperCase()) {
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
    default:
      return level || '—';
  }
}

function getRestrictionStatusLabel(status?: string) {
  switch (String(status || '').toUpperCase()) {
    case 'ACTIVE':
      return 'Đang áp dụng';
    case 'LIFTED':
      return 'Đã gỡ';
    case 'EXPIRED':
      return 'Đã hết hạn';
    default:
      return status || '—';
  }
}

function getRestrictionClass(level?: string) {
  switch (String(level || '').toUpperCase()) {
    case 'WARNING':
      return statusToneClasses.warning;
    case 'VERIFY_REQUIRED':
      return statusToneClasses.primary;
    case 'STAFF_ONLY':
      return statusToneClasses.champagne;
    case 'LIFTED':
      return statusToneClasses.success;
    case 'EXPIRED':
      return statusToneClasses.neutral;
    default:
      return statusToneClasses.neutral;
  }
}

function RestrictionBadge({ level }: { level?: string }) {
  return (
    <span
      className={cn(
        'inline-flex items-center whitespace-nowrap rounded-full border px-2.5 py-1 text-xs font-medium leading-none',
        getRestrictionClass(level),
      )}
    >
      {getRestrictionLabel(level)}
    </span>
  );
}

function getOverrideStatusLabel(status?: string) {
  switch (String(status || '').toUpperCase()) {
    case 'ACTIVE':
      return 'Hiệu lực';
    case 'USED':
      return 'Đã dùng';
    case 'EXPIRED':
      return 'Đã hết hạn';
    case 'CANCELLED':
      return 'Đã hủy';
    default:
      return status || '—';
  }
}

function getEventTypeLabel(type?: string) {
  switch (String(type || '').toUpperCase()) {
    case 'MANUAL':
      return 'Ghi nhận thủ công';
    case 'NO_SHOW':
      return 'No-show';
    case 'LATE_CANCEL':
      return 'Hủy sát giờ';
    case 'WRONG_CONTACT':
      return 'Sai thông tin liên hệ';
    case 'DUPLICATE_ABUSE':
      return 'Giữ slot bất thường';
    case 'STAFF_PARDON':
      return 'Ân xá / giảm điểm';
    case 'SUCCESSFUL_VISIT_CREDIT':
      return 'Hoàn thành khám / trừ điểm';
    default:
      return type || '—';
  }
}

function getEventStatusLabel(status?: string) {
  switch (String(status || '').toUpperCase()) {
    case 'ACTIVE':
      return 'Hiệu lực';
    case 'VOID':
    case 'VOIDED':
      return 'Đã void';
    default:
      return status || '—';
  }
}

function getEventStatusClass(status?: string) {
  const normalized = String(status || '').toUpperCase();
  if (normalized === 'VOID' || normalized === 'VOIDED') return statusToneClasses.neutral;
  return statusToneClasses.primary;
}

function EventStatusBadge({ status }: { status?: string }) {
  return (
    <span
      className={cn(
        'inline-flex items-center whitespace-nowrap rounded-full border px-2.5 py-1 text-xs font-medium leading-none',
        getEventStatusClass(status),
      )}
    >
      {getEventStatusLabel(status)}
    </span>
  );
}

function InfoTile({ label, value }: { label: string; value?: string | number | null }) {
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

function buildRestrictionParams(
  tab: RestrictionTab,
  page: number,
  q?: string,
): BookingRestrictionListQueryParams {
  const params: BookingRestrictionListQueryParams = {
    page: String(page - 1),
    size: '20',
    tab: tab.toUpperCase(),
  };

  if (q) params.q = q;

  if (tab === 'active') params.status = 'ACTIVE';
  if (tab === 'warning') params.level = 'WARNING';
  if (tab === 'unlocked') params.status = 'LIFTED';

  return params;
}

function BookingRestrictionActions({
  restriction,
  onOpenDetail,
  onAction,
}: {
  restriction: BookingRestriction;
  onOpenDetail: () => void;
  onAction: (action: RestrictionAction) => void;
}) {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="sm" aria-label="Mở hành động">
          <MoreHorizontal className="h-4 w-4" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-56">
        <DropdownMenuItem onClick={onOpenDetail}>
          <Eye className="mr-2 h-4 w-4" />
          Xem chi tiết
        </DropdownMenuItem>
        {canLift(restriction) ? (
          <DropdownMenuItem onClick={() => onAction({ type: 'lift', restriction })}>
            <ShieldOff className="mr-2 h-4 w-4" />
            Gỡ hạn chế
          </DropdownMenuItem>
        ) : null}
        {canOverrideOnce(restriction) ? (
          <DropdownMenuItem onClick={() => onAction({ type: 'override', restriction })}>
            <Unlock className="mr-2 h-4 w-4" />
            Mở cho một lần đặt
          </DropdownMenuItem>
        ) : null}
        {(canStaffPardon(restriction) || canCreateManualViolation(restriction)) && (
          <DropdownMenuSeparator />
        )}
        {canStaffPardon(restriction) ? (
          <DropdownMenuItem onClick={() => onAction({ type: 'pardon', restriction })}>
            <RotateCcw className="mr-2 h-4 w-4" />
            Ân xá / Giảm điểm
          </DropdownMenuItem>
        ) : null}
        {canCreateManualViolation(restriction) ? (
          <DropdownMenuItem onClick={() => onAction({ type: 'manual', restriction })}>
            <Plus className="mr-2 h-4 w-4" />
            Tạo manual violation
          </DropdownMenuItem>
        ) : null}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

function RestrictionDetailDrawer({
  open,
  restriction,
  events,
  overrides,
  isLoading,
  isError,
  onOpenChange,
  onAction,
}: {
  open: boolean;
  restriction?: BookingRestriction;
  events: BookingViolationEvent[];
  overrides: BookingRestrictionOverrideResponse[];
  isLoading: boolean;
  isError: boolean;
  onOpenChange: (open: boolean) => void;
  onAction: (action: RestrictionAction) => void;
}) {
  const displayLevel = restriction ? getRestrictionDisplayLevel(restriction) : undefined;

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="w-full p-0 sm:max-w-4xl">
        <div className="flex h-full flex-col">
          <SheetHeader className="shrink-0 border-b border-border bg-card px-5 py-4">
            <div className="flex flex-col gap-3 pr-8 sm:flex-row sm:items-start sm:justify-between">
              <div className="min-w-0">
                <SheetTitle className="text-lg">
                  {restriction?.patientFullName || 'Chi tiết hạn chế đặt lịch'}
                </SheetTitle>
                <SheetDescription>
                  {restriction?.patientPhone || '—'}
                  {restriction?.patientEmail ? ` · ${restriction.patientEmail}` : ''}
                </SheetDescription>
              </div>
              {displayLevel ? <RestrictionBadge level={displayLevel} /> : null}
            </div>
          </SheetHeader>

          <div className="flex-1 space-y-4 overflow-y-auto px-5 py-4">
            {!restriction ? (
              isError ? (
                <EmptyState
                  title="Không tải được chi tiết hạn chế"
                  description="Vui lòng thử lại sau hoặc kiểm tra kết nối backend."
                  className="min-h-40"
                />
              ) : (
                <LoadingSkeleton variant="detail" />
              )
            ) : (
              <>
                <section className="space-y-3">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <h3 className="text-base font-semibold text-foreground">Thông tin bệnh nhân</h3>
                    <div className="flex flex-wrap gap-2">
                      {canLift(restriction) ? (
                        <Button size="sm" variant="outline" onClick={() => onAction({ type: 'lift', restriction })}>
                          <ShieldOff className="mr-2 h-4 w-4" />
                          Gỡ hạn chế
                        </Button>
                      ) : null}
                      {canOverrideOnce(restriction) ? (
                        <Button size="sm" onClick={() => onAction({ type: 'override', restriction })}>
                          <Unlock className="mr-2 h-4 w-4" />
                          Mở cho một lần đặt
                        </Button>
                      ) : null}
                    </div>
                  </div>
                  <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
                    <InfoTile label="Bệnh nhân" value={restriction.patientFullName} />
                    <InfoTile label="SĐT" value={restriction.patientPhone} />
                    <InfoTile label="Email" value={restriction.patientEmail} />
                    <InfoTile label="Mã bệnh nhân" value={restriction.patientCode || restriction.patientId} />
                  </div>
                </section>

                <section className="space-y-3">
                  <h3 className="text-base font-semibold text-foreground">Trạng thái hiện tại</h3>
                  <div className="grid gap-3 md:grid-cols-4">
                    <InfoTile label="Điểm tháng" value={formatNumber(restriction.monthlyScore)} />
                    <div className="rounded-lg border border-border/70 bg-muted/20 px-3 py-2.5">
                      <p className="text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
                        Mức hiện tại
                      </p>
                      <div className="mt-2">
                        <RestrictionBadge level={displayLevel} />
                      </div>
                    </div>
                    <InfoTile label="Trạng thái" value={getRestrictionStatusLabel(restriction.status)} />
                    <InfoTile label="Số lần no-show" value={formatNumber(restriction.noShowCount)} />
                  </div>
                </section>

                <section className="space-y-3">
                  <h3 className="text-base font-semibold text-foreground">Thông tin hạn chế đang áp dụng</h3>
                  <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
                    <InfoTile label="Bắt đầu" value={formatDateTime(restriction.startsAt)} />
                    <InfoTile label="Hết hạn" value={formatDateTime(restriction.expiresAt)} />
                    <InfoTile label="Người tạo" value={restriction.createdByName} />
                    <InfoTile label="Ngày tạo" value={formatDateTime(restriction.createdAt)} />
                  </div>
                  <div className="rounded-lg border border-border/70 bg-muted/20 px-3 py-2.5">
                    <p className="text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
                      Lý do
                    </p>
                    <p className="mt-1 text-sm text-foreground">{restriction.reason || '—'}</p>
                  </div>
                </section>

                <section className="space-y-3">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <h3 className="text-base font-semibold text-foreground">Lịch sử vi phạm</h3>
                    <div className="flex flex-wrap gap-2">
                      {canStaffPardon(restriction) ? (
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => onAction({ type: 'pardon', restriction })}
                        >
                          <RotateCcw className="mr-2 h-4 w-4" />
                          Ân xá / Giảm điểm
                        </Button>
                      ) : null}
                      {canCreateManualViolation(restriction) ? (
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => onAction({ type: 'manual', restriction })}
                        >
                          <Plus className="mr-2 h-4 w-4" />
                          Tạo manual violation
                        </Button>
                      ) : null}
                    </div>
                  </div>

                  {isLoading ? (
                    <LoadingSkeleton variant="table" count={4} />
                  ) : isError && events.length === 0 ? (
                    <EmptyState
                      title="Không tải được lịch sử vi phạm"
                      description="Vui lòng thử lại sau hoặc kiểm tra kết nối backend."
                      className="min-h-40"
                    />
                  ) : events.length === 0 ? (
                    <EmptyState
                      title="Chưa có sự kiện vi phạm"
                      description="Hồ sơ này chưa có vi phạm hoặc điều chỉnh điểm nào."
                      className="min-h-40"
                    />
                  ) : (
                    <div className="overflow-hidden rounded-lg border border-border bg-card">
                      <div className="overflow-x-auto">
                        <Table>
                          <TableHeader>
                            <TableRow className="bg-muted/45 hover:bg-muted/45">
                              <TableHead>Loại</TableHead>
                              <TableHead>Điểm</TableHead>
                              <TableHead>Trạng thái</TableHead>
                              <TableHead>Ghi chú</TableHead>
                              <TableHead>Lịch hẹn</TableHead>
                              <TableHead>Ngày tạo</TableHead>
                              <TableHead>Người tạo</TableHead>
                              <TableHead>Void</TableHead>
                              <TableHead className="w-[76px]">Hành động</TableHead>
                            </TableRow>
                          </TableHeader>
                          <TableBody>
                            {events.map((event) => (
                              <TableRow key={event.id}>
                                <TableCell className="font-medium">{getEventTypeLabel(event.type)}</TableCell>
                                <TableCell className="tabular-nums">{formatNumber(event.points)}</TableCell>
                                <TableCell><EventStatusBadge status={event.status} /></TableCell>
                                <TableCell className="min-w-48 max-w-72">{event.note || '—'}</TableCell>
                                <TableCell>
                                  {event.appointmentId ? (
                                    <Link
                                      to={`/app/appointments/${event.appointmentId}/process`}
                                      className="font-medium text-primary hover:underline"
                                    >
                                      {event.appointmentCode || event.appointmentId}
                                    </Link>
                                  ) : (
                                    event.appointmentCode || '—'
                                  )}
                                </TableCell>
                                <TableCell>{formatDateTime(event.createdAt)}</TableCell>
                                <TableCell>{event.createdByName || '—'}</TableCell>
                                <TableCell>
                                  {event.voidedAt || event.voidReason ? (
                                    <div className="text-xs text-muted-foreground">
                                      <p>{formatDateTime(event.voidedAt)}</p>
                                      <p>{event.voidedByName || '—'}</p>
                                      <p>{event.voidReason || '—'}</p>
                                    </div>
                                  ) : (
                                    '—'
                                  )}
                                </TableCell>
                                <TableCell>
                                  {event.canVoid !== false &&
                                  !['VOID', 'VOIDED'].includes(String(event.status || '').toUpperCase()) ? (
                                    <Button
                                      size="sm"
                                      variant="outline"
                                      onClick={() => onAction({ type: 'void', event, restrictionId: restriction.id })}
                                    >
                                      Void
                                    </Button>
                                  ) : null}
                                </TableCell>
                              </TableRow>
                            ))}
                          </TableBody>
                        </Table>
                      </div>
                    </div>
                  )}
                </section>

                <section className="space-y-3">
                  <h3 className="text-base font-semibold text-foreground">Lượt mở đặt lịch</h3>
                  {isLoading ? (
                    <LoadingSkeleton variant="table" count={3} />
                  ) : overrides.length === 0 ? (
                    <EmptyState
                      title="Chưa có lượt mở đặt lịch"
                      description="Hồ sơ này chưa có ngoại lệ mở đặt lịch một lần."
                      className="min-h-32"
                    />
                  ) : (
                    <div className="overflow-hidden rounded-lg border border-border bg-card">
                      <div className="overflow-x-auto">
                        <Table>
                          <TableHeader>
                            <TableRow className="bg-muted/45 hover:bg-muted/45">
                              <TableHead>Trạng thái</TableHead>
                              <TableHead>Hiệu lực đến</TableHead>
                              <TableHead>Lịch hẹn sử dụng</TableHead>
                              <TableHead>Lý do</TableHead>
                              <TableHead>Người tạo</TableHead>
                              <TableHead>Ngày tạo</TableHead>
                            </TableRow>
                          </TableHeader>
                          <TableBody>
                            {overrides.map((override) => {
                              const usedAppointmentId =
                                override.usedByAppointmentId || override.appointmentId;
                              const usedAppointmentCode =
                                override.usedByAppointmentCode || override.appointmentCode || usedAppointmentId;

                              return (
                                <TableRow key={override.id}>
                                  <TableCell>
                                    <span
                                      className={cn(
                                        'inline-flex items-center whitespace-nowrap rounded-full border px-2.5 py-1 text-xs font-medium leading-none',
                                        getEventStatusClass(override.status),
                                      )}
                                    >
                                      {getOverrideStatusLabel(override.status)}
                                    </span>
                                  </TableCell>
                                  <TableCell>
                                    {formatDateTime(override.validUntil || override.expiresAt)}
                                  </TableCell>
                                  <TableCell>
                                    {usedAppointmentId ? (
                                      <Link
                                        to={`/app/appointments/${usedAppointmentId}/process`}
                                        className="font-medium text-primary hover:underline"
                                      >
                                        {usedAppointmentCode}
                                      </Link>
                                    ) : (
                                      '—'
                                    )}
                                  </TableCell>
                                  <TableCell className="min-w-48 max-w-72">{override.reason || '—'}</TableCell>
                                  <TableCell>{override.createdByName || '—'}</TableCell>
                                  <TableCell>{formatDateTime(override.createdAt)}</TableCell>
                                </TableRow>
                              );
                            })}
                          </TableBody>
                        </Table>
                      </div>
                    </div>
                  )}
                </section>

                {restriction.liftedAt || restriction.liftReason ? (
                  <section className="space-y-3">
                    <h3 className="text-base font-semibold text-foreground">Thông tin gỡ hạn chế</h3>
                    <div className="grid gap-3 md:grid-cols-3">
                      <InfoTile label="Thời điểm gỡ" value={formatDateTime(restriction.liftedAt)} />
                      <InfoTile label="Người gỡ" value={restriction.liftedByName} />
                      <InfoTile label="Lý do gỡ" value={restriction.liftReason} />
                    </div>
                  </section>
                ) : null}
              </>
            )}
          </div>
        </div>
      </SheetContent>
    </Sheet>
  );
}

function RestrictionActionDialog({
  action,
  open,
  isPending,
  onOpenChange,
  onSubmit,
}: {
  action: RestrictionAction | null;
  open: boolean;
  isPending: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmit: (payload: {
    reason: string;
    validHours: number;
    amount: number;
    points: number;
    type: string;
    note?: string;
  }) => void;
}) {
  const [reason, setReason] = useState('');
  const [validHours, setValidHours] = useState(24);
  const [amount, setAmount] = useState(1);
  const [points, setPoints] = useState(1);
  const [note, setNote] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    if (!open) return;
    setReason('');
    setValidHours(24);
    setAmount(1);
    setPoints(1);
    setNote('');
    setError('');
  }, [open, action?.type]);

  if (!action) return null;

  const copy = {
    lift: {
      title: 'Gỡ hạn chế',
      description: 'Nhập lý do nghiệp vụ trước khi gỡ hạn chế đặt lịch.',
      submit: 'Gỡ hạn chế',
    },
    override: {
      title: 'Mở cho một lần đặt',
      description: 'Tạo quyền đặt lịch tạm thời cho bệnh nhân này.',
      submit: 'Tạo quyền đặt tạm thời',
    },
    pardon: {
      title: 'Ân xá / Giảm điểm',
      description: 'Ghi nhận điều chỉnh điểm có lý do rõ ràng.',
      submit: 'Ghi nhận giảm điểm',
    },
    manual: {
      title: 'Tạo manual violation',
      description: 'Chỉ tạo khi cần ghi nhận sự kiện vi phạm thủ công.',
      submit: 'Tạo vi phạm',
    },
    void: {
      title: 'Void vi phạm',
      description: 'Nhập lý do trước khi void sự kiện vi phạm.',
      submit: 'Void vi phạm',
    },
  }[action.type];

  const handleSubmit = () => {
    const trimmedReason = reason.trim();
    if (!trimmedReason) {
      setError('Vui lòng nhập lý do.');
      return;
    }
    if (action.type === 'override' && validHours <= 0) {
      setError('Thời hạn phải lớn hơn 0 giờ.');
      return;
    }
    if (action.type === 'pardon' && amount <= 0) {
      setError('Số điểm giảm phải lớn hơn 0.');
      return;
    }
    if (action.type === 'manual' && points <= 0) {
      setError('Điểm vi phạm phải lớn hơn 0.');
      return;
    }

    onSubmit({
      reason: trimmedReason,
      validHours,
      amount,
      points,
      type: 'MANUAL',
      note: note.trim() || undefined,
    });
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{copy.title}</DialogTitle>
          <DialogDescription>{copy.description}</DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          {action.type === 'override' ? (
            <div>
              <label className="mb-1.5 block text-sm font-medium">Hiệu lực trong</label>
              <div className="flex items-center gap-2">
                <Input
                  type="number"
                  min={1}
                  value={validHours}
                  onChange={(event) => setValidHours(Number(event.target.value))}
                  className="w-32"
                />
                <span className="text-sm text-muted-foreground">giờ</span>
              </div>
            </div>
          ) : null}

          {action.type === 'pardon' ? (
            <div>
              <label className="mb-1.5 block text-sm font-medium">Số điểm giảm</label>
              <Input
                type="number"
                min={1}
                value={amount}
                onChange={(event) => setAmount(Number(event.target.value))}
                className="w-32"
              />
            </div>
          ) : null}

          {action.type === 'manual' ? (
            <div>
              <label className="mb-1.5 block text-sm font-medium">Điểm</label>
              <Input
                type="number"
                min={1}
                value={points}
                onChange={(event) => setPoints(Number(event.target.value))}
                className="w-32"
              />
            </div>
          ) : null}

          <div>
            <label className="mb-1.5 block text-sm font-medium">Lý do</label>
            <Textarea
              rows={4}
              value={reason}
              onChange={(event) => {
                setReason(event.target.value);
                setError('');
              }}
              placeholder="Nhập lý do thao tác"
            />
            {error ? <p className="mt-1 text-xs text-destructive">{error}</p> : null}
          </div>

          {action.type === 'manual' ? (
            <div>
              <label className="mb-1.5 block text-sm font-medium">Ghi chú thêm</label>
              <Textarea
                rows={2}
                value={note}
                onChange={(event) => setNote(event.target.value)}
                placeholder="Thông tin bổ sung nếu có"
              />
            </div>
          ) : null}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={isPending}>
            Hủy
          </Button>
          <Button onClick={handleSubmit} disabled={isPending}>
            {isPending ? 'Đang xử lý...' : copy.submit}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export default function BookingRestrictionsPage() {
  const [activeTab, setActiveTab] = useState<RestrictionTab>('active');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(1);
  const [selected, setSelected] = useState<BookingRestriction | null>(null);
  const [action, setAction] = useState<RestrictionAction | null>(null);
  const debouncedSearch = useDebouncedValue(search.trim(), 350);

  useEffect(() => {
    setPage(1);
  }, [activeTab, debouncedSearch]);

  const restrictionParams = useMemo(
    () => buildRestrictionParams(activeTab, page, debouncedSearch),
    [activeTab, debouncedSearch, page],
  );

  const restrictionsQuery = useBookingRestrictions(restrictionParams);
  const detailQuery = useBookingRestrictionDetail(selected?.id, {
    enabled: Boolean(selected?.id),
  });

  const liftMutation = useLiftBookingRestriction();
  const overrideMutation = useCreateBookingOverrideOnce();
  const voidMutation = useVoidBookingViolationEvent();
  const pardonMutation = useCreateBookingStaffPardon();
  const manualMutation = useCreateManualBookingViolation();

  const currentRestriction = detailQuery.data?.restriction || selected || undefined;
  const detailEvents = detailQuery.data?.events ?? currentRestriction?.events ?? [];
  const detailOverrides = detailQuery.data?.overrides ?? currentRestriction?.overrides ?? [];
  const isActionPending =
    liftMutation.isPending ||
    overrideMutation.isPending ||
    voidMutation.isPending ||
    pardonMutation.isPending ||
    manualMutation.isPending;

  const openDetail = (restriction: BookingRestriction) => {
    setSelected(restriction);
  };

  const handleActionSubmit = async (payload: {
    reason: string;
    validHours: number;
    amount: number;
    points: number;
    type: string;
    note?: string;
  }) => {
    if (!action) return;

    try {
      if (action.type === 'lift') {
        await liftMutation.mutateAsync({
          id: action.restriction.id,
          body: { reason: payload.reason },
        });
      } else if (action.type === 'override') {
        await overrideMutation.mutateAsync({
          id: action.restriction.id,
          body: { reason: payload.reason, validHours: payload.validHours },
        });
      } else if (action.type === 'void') {
        await voidMutation.mutateAsync({
          eventId: action.event.id,
          restrictionId: action.restrictionId,
          body: { reason: payload.reason },
        });
      } else if (action.type === 'pardon') {
        await pardonMutation.mutateAsync({
          restrictionId: action.restriction.id,
          body: {
            ...getRestrictionRequestIdentity(action.restriction),
            reason: payload.reason,
            pointsToReduce: payload.amount,
          },
        });
      } else if (action.type === 'manual') {
        await manualMutation.mutateAsync({
          restrictionId: action.restriction.id,
          body: {
            ...getRestrictionRequestIdentity(action.restriction),
            reason: payload.reason,
            type: payload.type,
            points: payload.points,
            note: payload.note || payload.reason,
          },
        });
      }
      setAction(null);
      void restrictionsQuery.refetch();
      if (selected?.id) void detailQuery.refetch();
    } catch {
      // Mutation hooks show backend errors.
    }
  };

  const restrictionColumns: Column<BookingRestriction>[] = [
    {
      key: 'patientFullName',
      header: 'Bệnh nhân',
      cell: (row) => (
        <div>
          <p className="font-medium">{row.patientFullName || '—'}</p>
          <p className="text-xs text-muted-foreground">{row.patientCode || row.patientId || '—'}</p>
        </div>
      ),
    },
    { key: 'patientPhone', header: 'SĐT', cell: (row) => row.patientPhone || '—' },
    { key: 'patientEmail', header: 'Email', cell: (row) => row.patientEmail || '—' },
    {
      key: 'monthlyScore',
      header: 'Điểm tháng',
      cell: (row) => <span className="tabular-nums">{formatNumber(row.monthlyScore)}</span>,
    },
    {
      key: 'level',
      header: 'Mức hạn chế',
      cell: (row) => <RestrictionBadge level={getRestrictionDisplayLevel(row)} />,
    },
    {
      key: 'noShowCount',
      header: 'Số lần no-show',
      cell: (row) => <span className="tabular-nums">{formatNumber(row.noShowCount)}</span>,
    },
    {
      key: 'latestViolationAt',
      header: 'Lần vi phạm gần nhất',
      cell: (row) => formatDateTime(row.latestViolationAt),
    },
    {
      key: 'expiresAt',
      header: 'Ngày hết hạn',
      cell: (row) => formatDateTime(row.expiresAt),
    },
    { key: 'createdByName', header: 'Người tạo', cell: (row) => row.createdByName || '—' },
  ];

  const restrictionErrorStatus = getHttpStatus(restrictionsQuery.error);
  const restrictionErrorTitle =
    restrictionErrorStatus === 403 ? 'Bạn không có quyền xem màn này' : 'Không tải được dữ liệu';
  const restrictionErrorDescription =
    restrictionErrorStatus === 403
      ? 'Chức năng hạn chế đặt lịch dành cho nhân viên tiếp nhận hoặc quản trị vận hành.'
      : 'Vui lòng thử lại hoặc kiểm tra kết nối.';

  return (
    <div className="space-y-4">
      <PageHeader
        title="Hạn chế đặt lịch"
        description="Theo dõi điểm vi phạm đặt lịch và xử lý các ngoại lệ nội bộ cho bệnh nhân."
      />

      <Tabs value={activeTab} onValueChange={(value) => setActiveTab(value as RestrictionTab)}>
        <Card className="p-3 shadow-sm">
          <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
            <div className="min-w-0">
              <TabsList className="flex h-auto w-full flex-wrap justify-start gap-1 rounded-lg border border-border bg-muted/50 p-1 sm:w-auto">
                {restrictionTabs.map((tab) => (
                  <TabsTrigger
                    key={tab}
                    value={tab}
                    className="border border-transparent px-3 py-1.5 text-sm text-muted-foreground shadow-none hover:bg-muted data-[state=active]:border-primary/20 data-[state=active]:bg-primary/10 data-[state=active]:text-primary data-[state=active]:shadow-none"
                  >
                    {tabLabels[tab]}
                  </TabsTrigger>
                ))}
              </TabsList>
              <p className="mt-2 text-xs text-muted-foreground">
                Lịch sử vi phạm nằm trong chi tiết từng hồ sơ.
              </p>
            </div>

            <div className="relative w-full lg:max-w-sm">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Tìm bệnh nhân, SĐT hoặc email..."
                className="pl-9"
              />
            </div>
          </div>
        </Card>
      </Tabs>

      {restrictionsQuery.isError ? (
        <ErrorState
          title={restrictionErrorTitle}
          description={restrictionErrorDescription}
          onRetry={() => void restrictionsQuery.refetch()}
          className="min-h-40 py-8"
        />
      ) : (
        <DataTable
          columns={restrictionColumns}
          data={restrictionsQuery.data?.items ?? []}
          page={page}
          totalPages={Math.max(restrictionsQuery.data?.meta.totalPages ?? 1, 1)}
          onPageChange={setPage}
          isLoading={restrictionsQuery.isLoading}
          emptyMessage={getRestrictionEmptyMessage(activeTab)}
          keyExtractor={(row) => row.id}
          onRowClick={openDetail}
          actions={(row) => (
            <BookingRestrictionActions
              restriction={row}
              onOpenDetail={() => openDetail(row)}
              onAction={setAction}
            />
          )}
        />
      )}

      <RestrictionDetailDrawer
        open={Boolean(selected)}
        restriction={currentRestriction}
        events={detailEvents}
        overrides={detailOverrides}
        isLoading={detailQuery.isLoading}
        isError={detailQuery.isError}
        onOpenChange={(open) => {
          if (!open) setSelected(null);
        }}
        onAction={setAction}
      />

      <RestrictionActionDialog
        action={action}
        open={Boolean(action)}
        isPending={isActionPending}
        onOpenChange={(open) => {
          if (!open && !isActionPending) setAction(null);
        }}
        onSubmit={(payload) => void handleActionSubmit(payload)}
      />
    </div>
  );
}
