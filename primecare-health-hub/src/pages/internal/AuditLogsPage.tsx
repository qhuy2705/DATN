import { type FormEvent, type ReactNode, useMemo, useState } from 'react';
import { Eye } from 'lucide-react';
import { DataTable, type Column } from '@/components/DataTable';
import { JsonViewer } from '@/components/JsonViewer';
import { PageHeader } from '@/components/PageHeader';
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
import { Label } from '@/components/ui/label';
import { useAuditLogs } from '@/hooks/use-admin-data';
import type { AuditLog, AuditLogQueryParams } from '@/types/api';

const AUDIT_LOG_PAGE_SIZE = '20';

const AUDIT_ACTION_SUGGESTIONS = [
  'CREATE_DOCTOR',
  'UPDATE_DOCTOR',
  'UPDATE_DOCTOR_STATUS',
  'CREATE_STAFF',
  'UPDATE_STAFF',
  'UPDATE_STAFF_STATUS',
  'UPSERT_DOCTOR_SCHEDULE',
  'DELETE_DOCTOR_SCHEDULE',
  'IMPORT_DOCTOR_SCHEDULE',
  'CREATE_DOCTOR_LEAVE_REQUEST',
  'CANCEL_DOCTOR_LEAVE_REQUEST',
  'APPROVE_DOCTOR_LEAVE_REQUEST',
  'REJECT_DOCTOR_LEAVE_REQUEST',
  'CONFIRM_APPOINTMENT',
  'CANCEL_APPOINTMENT',
  'RESCHEDULE_APPOINTMENT',
  'MANUAL_CHECK_IN',
  'QR_CHECK_IN',
  'MARK_NO_SHOW',
  'RESOLVE_NO_SHOW',
  'RESOLVE_FOLLOW_UP',
  'CREATE_WALK_IN',
  'CREATE_INVOICE',
  'MARK_INVOICE_PAID',
  'CONFIRM_BANK_TRANSFER',
  'REFUND_INVOICE',
  'CREATE_PRESCRIPTION',
  'UPDATE_PRESCRIPTION',
  'CANCEL_PRESCRIPTION',
  'DISPENSE_PRESCRIPTION',
  'SUBMIT_SERVICE_RESULT',
  'VERIFY_SERVICE_RESULT',
  'IMPORT_INVENTORY',
  'CREATE_MEDICATION_BATCH',
  'UPDATE_MEDICATION_BATCH',
  'ADJUST_STOCK',
  'LOGIN_SUCCESS',
  'LOGIN_FAILED',
  'LOGOUT',
  'CHANGE_PASSWORD',
  'PUBLIC_ACCEPT_RESCHEDULE',
  'PUBLIC_CANCEL_RESCHEDULE',
  'PUBLIC_REQUEST_CONTACT',
  'PUBLIC_CANCEL_APPOINTMENT',
  'CREATE',
  'UPDATE',
  'DELETE',
  'LOGIN',
];

const AUDIT_ENTITY_SUGGESTIONS = [
  'APPOINTMENT',
  'PATIENT',
  'DOCTOR',
  'STAFF',
  'BRANCH',
  'SPECIALTY',
  'BRANCH_SPECIALTY',
  'MEDICAL_SERVICE',
  'MEDICATION',
  'DOCTOR_SCHEDULE',
  'DOCTOR_LEAVE_REQUEST',
  'ENCOUNTER',
  'PRESCRIPTION',
  'SERVICE_ORDER',
  'SERVICE_RESULT',
  'INVOICE',
  'PAYMENT',
  'PHARMACY',
  'INVENTORY',
  'MEDICATION_BATCH',
  'FILE',
  'AUTH',
  'USER_ACCOUNT',
  'SLOT_HOLD',
  'BOOKING_RESTRICTION',
  'USER',
];

type AuditLogFilters = {
  date: string;
  fromDate: string;
  toDate: string;
  action: string;
  entity: string;
  entityId: string;
  actorRole: string;
  q: string;
};

function createEmptyFilters(): AuditLogFilters {
  return {
    date: '',
    fromDate: '',
    toDate: '',
    action: '',
    entity: '',
    entityId: '',
    actorRole: '',
    q: '',
  };
}

function trimFilterValue(value: string) {
  const trimmed = value.trim();
  return trimmed || undefined;
}

function buildAuditLogParams(page: number, filters: AuditLogFilters): AuditLogQueryParams {
  const date = trimFilterValue(filters.date);
  const fromDate = date ? undefined : trimFilterValue(filters.fromDate);
  const toDate = date ? undefined : trimFilterValue(filters.toDate);
  const action = trimFilterValue(filters.action);
  const entity = trimFilterValue(filters.entity);
  const entityId = trimFilterValue(filters.entityId);
  const actorRole = trimFilterValue(filters.actorRole);
  const q = trimFilterValue(filters.q);

  return {
    page: String(page - 1),
    size: AUDIT_LOG_PAGE_SIZE,
    ...(date ? { date } : {}),
    ...(fromDate ? { fromDate } : {}),
    ...(toDate ? { toDate } : {}),
    ...(action ? { action } : {}),
    ...(entity ? { entity } : {}),
    ...(entityId ? { entityId } : {}),
    ...(actorRole ? { actorRole } : {}),
    ...(q ? { q } : {}),
  };
}

function displayValue(value?: string | null) {
  return value?.trim() ? value : '-';
}

function formatDateTime(value?: string | null) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('vi-VN');
}

function getActorDisplay(log: AuditLog) {
  return displayValue(log.actorName || log.actorEmail || log.actorId || log.actor);
}

function getEntityDisplay(log: AuditLog) {
  return displayValue(log.entity || log.entityType || log.targetType);
}

function getEntityIdDisplay(log: AuditLog) {
  return displayValue(log.entityId || log.targetId);
}

function getActiveDateFilterLabel(filters: AuditLogFilters) {
  const date = filters.date.trim();
  const fromDate = filters.fromDate.trim();
  const toDate = filters.toDate.trim();

  if (date) return `Đang xem log trong ngày: ${date}`;
  if (fromDate && toDate) return `Đang xem log từ ${fromDate} đến ${toDate}`;
  if (fromDate) return `Đang xem log từ ${fromDate}`;
  if (toDate) return `Đang xem log đến ${toDate}`;
  return undefined;
}

function SuggestionDatalist({ id, options }: { id: string; options: string[] }) {
  return (
    <datalist id={id}>
      {options.map((option) => (
        <option key={option} value={option} />
      ))}
    </datalist>
  );
}

function AuditFilterField({
  id,
  label,
  children,
}: {
  id: string;
  label: string;
  children: ReactNode;
}) {
  return (
    <div className="space-y-1.5">
      <Label htmlFor={id} className="text-xs font-medium text-muted-foreground">
        {label}
      </Label>
      {children}
    </div>
  );
}

function DetailItem({
  label,
  value,
  className,
}: {
  label: string;
  value?: string | number | null;
  className?: string;
}) {
  return (
    <div className={className}>
      <p className="text-xs font-medium text-muted-foreground">{label}</p>
      <p className="mt-1 break-all text-sm text-foreground">{displayValue(String(value ?? ''))}</p>
    </div>
  );
}

function AuditLogDetailDialog({
  log,
  open,
  onOpenChange,
}: {
  log: AuditLog | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  if (!log) return null;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="flex max-h-[90vh] flex-col overflow-hidden p-0 sm:max-w-4xl">
        <DialogHeader className="shrink-0 border-b bg-background px-6 pb-4 pt-6">
          <DialogTitle>Audit log detail</DialogTitle>
          <DialogDescription>Thông tin chỉ đọc về thao tác đã ghi nhận.</DialogDescription>
        </DialogHeader>

        <div className="space-y-ui-md overflow-y-auto px-6 py-5">
          <div className="grid gap-ui-sm sm:grid-cols-2">
            <DetailItem label="ID" value={log.id} />
            {log.eventId ? <DetailItem label="Event ID" value={log.eventId} /> : null}
            <DetailItem label="Created at" value={formatDateTime(log.createdAt)} />
            <DetailItem label="Actor name" value={log.actorName} />
            <DetailItem label="Actor email" value={log.actorEmail} />
            <DetailItem label="Actor ID" value={log.actorId} />
            <DetailItem label="Actor role" value={log.actorRole} />
            <DetailItem label="Action" value={log.action} />
            <DetailItem label="Entity" value={getEntityDisplay(log)} />
            <DetailItem label="Entity ID" value={getEntityIdDisplay(log)} />
            <DetailItem label="IP address" value={log.ipAddress} />
            <DetailItem label="User agent" value={log.userAgent} className="sm:col-span-2" />
          </div>

          <div className="grid gap-ui-md lg:grid-cols-2">
            <JsonViewer title="Before JSON" data={log.beforeJson} />
            <JsonViewer title="After JSON" data={log.afterJson} />
          </div>
        </div>

        <DialogFooter className="shrink-0 border-t bg-background px-6 py-4">
          <Button type="button" variant="outline" size="sm" onClick={() => onOpenChange(false)}>
            Đóng
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export default function AuditLogsPage() {
  const [page, setPage] = useState(1);
  const [draftFilters, setDraftFilters] = useState<AuditLogFilters>(() => createEmptyFilters());
  const [appliedFilters, setAppliedFilters] = useState<AuditLogFilters>(() => createEmptyFilters());
  const [selectedLog, setSelectedLog] = useState<AuditLog | null>(null);

  const queryParams = useMemo(
    () => buildAuditLogParams(page, appliedFilters),
    [appliedFilters, page],
  );

  const { data, isLoading, isError, error, refetch } = useAuditLogs(queryParams);
  const dateSelected = Boolean(draftFilters.date.trim());
  const activeDateFilterLabel = getActiveDateFilterLabel(appliedFilters);
  const hasDraftFilters = Object.values(draftFilters).some((value) => Boolean(value.trim()));
  const hasAppliedFilters = Object.values(appliedFilters).some((value) => Boolean(value.trim()));

  const updateFilter = (key: keyof AuditLogFilters, value: string) => {
    setDraftFilters((current) => {
      const next = { ...current, [key]: value };

      if (key === 'date' && value) {
        next.fromDate = '';
        next.toDate = '';
      }

      if ((key === 'fromDate' || key === 'toDate') && value) {
        next.date = '';
      }

      return next;
    });
  };

  const handleApply = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setAppliedFilters(draftFilters);
    setPage(1);
  };

  const handleClearFilters = () => {
    const emptyFilters = createEmptyFilters();
    setDraftFilters(emptyFilters);
    setAppliedFilters(emptyFilters);
    setPage(1);
  };

  const columns: Column<AuditLog>[] = [
    {
      key: 'createdAt',
      header: 'Thời gian',
      className: 'whitespace-nowrap',
      cell: (row) => <span className="text-sm font-medium">{formatDateTime(row.createdAt)}</span>,
    },
    {
      key: 'actor',
      header: 'Người thực hiện',
      cell: (row) => (
        <div>
          <p className="text-sm font-medium">{getActorDisplay(row)}</p>
          {row.actorName && row.actorEmail ? (
            <p className="text-xs text-muted-foreground">{row.actorEmail}</p>
          ) : null}
        </div>
      ),
    },
    {
      key: 'actorRole',
      header: 'Vai trò',
      className: 'whitespace-nowrap',
      cell: (row) => <span className="text-sm">{displayValue(row.actorRole)}</span>,
    },
    {
      key: 'action',
      header: 'Hành động',
      className: 'whitespace-nowrap',
      cell: (row) => (
        <Badge variant="secondary">
          <span className="max-w-[160px] truncate">{displayValue(row.action)}</span>
        </Badge>
      ),
    },
    {
      key: 'entity',
      header: 'Đối tượng',
      cell: (row) => <span className="text-sm">{getEntityDisplay(row)}</span>,
    },
    {
      key: 'entityId',
      header: 'Entity ID',
      cell: (row) => (
        <span className="block max-w-[180px] truncate text-sm">{getEntityIdDisplay(row)}</span>
      ),
    },
    {
      key: 'ipAddress',
      header: 'IP',
      className: 'whitespace-nowrap',
      cell: (row) => <span className="text-sm">{displayValue(row.ipAddress)}</span>,
    },
    {
      key: 'detail',
      header: 'Chi tiết',
      className: 'whitespace-nowrap',
      cell: (row) => (
        <Button type="button" variant="outline" size="sm" onClick={() => setSelectedLog(row)}>
          <Eye className="h-4 w-4" />
          Xem
        </Button>
      ),
    },
  ];

  return (
    <div>
      <PageHeader
        title="Audit Logs"
        description="Theo dõi thao tác quan trọng trong hệ thống"
      />

      <form
        onSubmit={handleApply}
        className="mb-ui-md rounded-lg border border-border bg-card p-ui-md shadow-sm"
      >
        <div className="mb-ui-sm flex flex-wrap items-center gap-ui-xs">
          <p className="text-xs text-muted-foreground">
            Mặc định hiển thị audit logs gần đây nhất nếu không chọn ngày.
          </p>
          {activeDateFilterLabel ? (
            <Badge variant="outline" className="rounded-md">
              {activeDateFilterLabel}
            </Badge>
          ) : null}
        </div>

        <div className="grid gap-ui-sm md:grid-cols-2 xl:grid-cols-4">
          <AuditFilterField id="audit-date" label="Ngày">
            <Input
              id="audit-date"
              type="date"
              value={draftFilters.date}
              onChange={(event) => updateFilter('date', event.target.value)}
            />
          </AuditFilterField>

          <AuditFilterField id="audit-from-date" label="Từ ngày">
            <Input
              id="audit-from-date"
              type="date"
              value={draftFilters.fromDate}
              onChange={(event) => updateFilter('fromDate', event.target.value)}
              disabled={dateSelected}
            />
          </AuditFilterField>

          <AuditFilterField id="audit-to-date" label="Đến ngày">
            <Input
              id="audit-to-date"
              type="date"
              value={draftFilters.toDate}
              onChange={(event) => updateFilter('toDate', event.target.value)}
              disabled={dateSelected}
            />
          </AuditFilterField>

          <AuditFilterField id="audit-action" label="Hành động">
            <Input
              id="audit-action"
              list="audit-action-suggestions"
              value={draftFilters.action}
              onChange={(event) => updateFilter('action', event.target.value)}
              placeholder="Chọn hoặc nhập action"
            />
            <SuggestionDatalist id="audit-action-suggestions" options={AUDIT_ACTION_SUGGESTIONS} />
          </AuditFilterField>

          <AuditFilterField id="audit-entity" label="Đối tượng">
            <Input
              id="audit-entity"
              list="audit-entity-suggestions"
              value={draftFilters.entity}
              onChange={(event) => updateFilter('entity', event.target.value)}
              placeholder="Chọn hoặc nhập entity"
            />
            <SuggestionDatalist id="audit-entity-suggestions" options={AUDIT_ENTITY_SUGGESTIONS} />
          </AuditFilterField>

          <AuditFilterField id="audit-entity-id" label="Entity ID">
            <Input
              id="audit-entity-id"
              value={draftFilters.entityId}
              onChange={(event) => updateFilter('entityId', event.target.value)}
              placeholder="ID đối tượng"
            />
          </AuditFilterField>

          <AuditFilterField id="audit-actor-role" label="Vai trò">
            <Input
              id="audit-actor-role"
              value={draftFilters.actorRole}
              onChange={(event) => updateFilter('actorRole', event.target.value)}
              placeholder="SYSTEM_ADMIN"
            />
          </AuditFilterField>

          <AuditFilterField id="audit-keyword" label="Từ khóa">
            <Input
              id="audit-keyword"
              value={draftFilters.q}
              onChange={(event) => updateFilter('q', event.target.value)}
              placeholder="Tìm audit log"
            />
          </AuditFilterField>
        </div>

        <div className="mt-ui-md flex flex-wrap justify-end gap-ui-xs">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={handleClearFilters}
            disabled={!hasDraftFilters && !hasAppliedFilters}
          >
            Xóa bộ lọc
          </Button>
          <Button type="submit" size="sm">
            Áp dụng
          </Button>
        </div>
      </form>

      <DataTable
        columns={columns}
        data={data?.items ?? []}
        page={page}
        totalPages={Math.max(data?.meta.totalPages ?? 1, 1)}
        onPageChange={setPage}
        emptyMessage="Không có audit log phù hợp với bộ lọc"
        isLoading={isLoading}
        isError={isError}
        errorMessage={error instanceof Error ? error.message : undefined}
        onRetry={() => void refetch()}
        keyExtractor={(row) =>
          row.id || row.eventId || `${row.createdAt ?? ''}-${row.action}-${getEntityIdDisplay(row)}`
        }
      />

      <AuditLogDetailDialog
        log={selectedLog}
        open={Boolean(selectedLog)}
        onOpenChange={(open) => {
          if (!open) setSelectedLog(null);
        }}
      />
    </div>
  );
}
