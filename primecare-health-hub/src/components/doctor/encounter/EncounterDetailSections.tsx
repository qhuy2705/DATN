import type { Dispatch, SetStateAction } from 'react';
import {
  ArrowLeft,
  CheckCircle2,
  ChevronDown,
  CircleAlert,
  ClipboardList,
  FileImage,
  FileText,
  Pill,
  Plus,
  Save,
  Trash2,
} from 'lucide-react';
import { toast } from 'sonner';
import { Icd10Select } from '@/components/Icd10Select';
import { PatientJourneyTimeline } from '@/components/PatientJourneyTimeline';
import { StatusBadge } from '@/components/StatusBadge';
import { EncounterPrescriptionsPanel } from '@/components/doctor/EncounterPrescriptionsPanel';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion';
import { Input } from '@/components/ui/input';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { openProtectedFile } from '@/lib/download-file';
import { getApiErrorMessage } from '@/lib/error-utils';
import { useDoctorEncounterTimeline } from '@/hooks/use-doctor-data';
import type {
  Encounter,
  EncounterDiagnosisResponse,
  ServiceOrder,
  ServiceOrderItem,
  ServiceResultAttachment,
} from '@/types/api';

const currency = new Intl.NumberFormat('vi-VN');

export type ServiceOrderDraftItem = {
  medicalServiceId: string;
  quantity: string;
  note: string;
};

export interface EncounterFormState {
  intakeReasonForVisit: string;
  visitType: string;
  triagePriority: string;
  triageNote: string;
  insuranceNote: string;
  emergencyContactName: string;
  emergencyContactPhone: string;
  chiefComplaint: string;
  clinicalNote: string;
  diagnoses: EncounterDiagnosisResponse[];
  conclusion: string;
  heightCm: string;
  weightKg: string;
  temperatureC: string;
  pulse: string;
  systolicBp: string;
  diastolicBp: string;
  respiratoryRate: string;
  spo2: string;
  allergySnapshot: string;
  chronicDiseaseSnapshot: string;
  pastMedicalHistory: string;
  physicalExamination: string;
  treatmentPlan: string;
  followUpDate: string;
  followUpNote: string;
}

type SetEncounterForm = Dispatch<SetStateAction<EncounterFormState>>;

interface EncounterActionProps {
  encounter: Encounter;
  canEdit: boolean;
  canOrderServices: boolean;
  canCreatePrescription: boolean;
  canComplete: boolean;
  isSavingWorkspace: boolean;
  completePending: boolean;
  formDirty: boolean;
  onSave: () => void;
  onOrderServices: () => void;
  onCreatePrescription: () => void;
  onLeave: () => void;
  onReopen: () => void;
  onComplete: () => void;
}

function getDiagnosisTypeLabel(type: EncounterDiagnosisResponse['diagnosisType']) {
  switch (type) {
    case 'PRELIMINARY':
      return 'Chẩn đoán sơ bộ';
    case 'FINAL':
      return 'Chẩn đoán chính ICD-10';
    case 'SECONDARY':
      return 'Chẩn đoán kèm theo';
    default:
      return type;
  }
}

function renderVisitType(value?: string) {
  switch (value) {
    case 'NEW_PATIENT':
      return 'Khám mới';
    case 'FOLLOW_UP':
      return 'Tái khám';
    case 'CONSULTATION':
      return 'Tư vấn';
    default:
      return value || '—';
  }
}

function renderPriority(value?: string) {
  switch (value) {
    case 'ROUTINE':
      return 'Thông thường';
    case 'PRIORITY':
      return 'Ưu tiên';
    case 'URGENT':
      return 'Khẩn';
    default:
      return value || '—';
  }
}

function safeParseJson<T>(value?: string, fallback?: T): T | undefined {
  if (!value?.trim()) return fallback;
  try {
    return JSON.parse(value) as T;
  } catch {
    return fallback;
  }
}

function attachmentTitle(attachment: ServiceResultAttachment, index: number) {
  return attachment.label || attachment.fileName || `Tệp đính kèm ${index + 1}`;
}

function isImageAttachment(attachment: ServiceResultAttachment) {
  if (attachment.mimeType?.startsWith('image/')) return true;
  return /\.(png|jpe?g|gif|webp|bmp|svg)(\?.*)?$/i.test(attachment.url);
}

function getServiceDisplayName(item: ServiceOrderItem, language?: string) {
  if (language?.toLowerCase().startsWith('en') && item.serviceNameEn) {
    return item.serviceNameEn;
  }

  return item.serviceNameVn || item.serviceNameEn || item.serviceCode || item.id;
}

function isServiceItemDone(item: ServiceOrderItem) {
  return item.status === 'DONE' || item.resultStatus === 'COMPLETED' || item.resultStatus === 'VERIFIED';
}

function hasServiceResult(item: ServiceOrderItem) {
  return Boolean(
    item.resultTextVn ||
      item.resultTextEn ||
      item.fieldValuesJson ||
      item.resultDataJson ||
      item.reportPdfUrl ||
      item.reportPdfStatus ||
      (item.attachments?.length ?? 0) > 0,
  );
}

function FieldBlock({
  label,
  value,
}: {
  label: string;
  value?: string | null;
}) {
  if (!value) return null;
  return (
    <div className="space-y-1">
      <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</p>
      <div className="whitespace-pre-wrap rounded-md bg-muted/30 px-3 py-2 text-sm text-foreground">
        {value}
      </div>
    </div>
  );
}

function StructuredServiceResult({ item }: { item: ServiceOrderItem }) {
  const fieldValues =
    safeParseJson<Record<string, unknown>>(item.fieldValuesJson || item.resultDataJson, {}) || {};
  const templateCode = item.templateCode || 'GENERIC_NARRATIVE';
  const summaryText = item.resultTextVn || item.resultTextEn;
  const rows = Array.isArray(fieldValues.rows)
    ? fieldValues.rows.filter(
        (row): row is Record<string, unknown> => typeof row === 'object' && row !== null,
      )
    : [];
  const attachments = item.attachments || [];

  const handleOpenResultPdf = async () => {
    if (!item.reportPdfUrl) return;

    try {
      await openProtectedFile(item.reportPdfUrl, `ket-qua-${item.id || 'service'}.pdf`);
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'Không thể mở phiếu PDF'));
    }
  };

  const handleOpenAttachment = async (attachment: ServiceResultAttachment, index: number) => {
    try {
      await openProtectedFile(attachment.url, attachment.fileName || `tep-dinh-kem-${index + 1}`);
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'Không thể mở tệp đính kèm'));
    }
  };

  return (
    <div className="mt-3 space-y-3 rounded-lg border bg-muted/15 px-3 py-3">
      {summaryText ? (
        <div className="whitespace-pre-wrap rounded-md bg-muted/30 px-3 py-2 text-sm text-foreground">
          {summaryText}
        </div>
      ) : null}

      {templateCode === 'LAB_TABLE' ? (
        <div className="space-y-3">
          <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
            <FieldBlock
              label="Mẫu bệnh phẩm"
              value={typeof fieldValues.specimen === 'string' ? fieldValues.specimen : undefined}
            />
            <FieldBlock
              label="Chỉ định lâm sàng"
              value={typeof fieldValues.clinicalInfo === 'string' ? fieldValues.clinicalInfo : undefined}
            />
            <FieldBlock
              label="Thiết bị / máy"
              value={typeof fieldValues.deviceName === 'string' ? fieldValues.deviceName : undefined}
            />
          </div>
          {rows.length > 0 ? (
            <div className="overflow-x-auto rounded-md border">
              <table className="min-w-full text-sm">
                <thead className="bg-muted/40 text-left text-muted-foreground">
                  <tr>
                    <th className="px-3 py-2 font-medium">Thông số</th>
                    <th className="px-3 py-2 font-medium">Kết quả</th>
                    <th className="px-3 py-2 font-medium">Đơn vị</th>
                    <th className="px-3 py-2 font-medium">Khoảng tham chiếu</th>
                    <th className="px-3 py-2 font-medium">Cờ</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row, index) => {
                    const flag = String(row.flag ?? '').trim();
                    const abnormal = ['H', 'L', 'HIGH', 'LOW', 'CRITICAL', 'A', '*'].includes(
                      flag.toUpperCase(),
                    );
                    return (
                      <tr
                        key={`${item.id}-row-${index}`}
                        className={abnormal ? 'border-t bg-amber-50/80' : 'border-t'}
                      >
                        <td className="px-3 py-2">{String(row.parameter ?? '—')}</td>
                        <td className="px-3 py-2 font-medium text-foreground">{String(row.result ?? '—')}</td>
                        <td className="px-3 py-2">{String(row.unit ?? '—')}</td>
                        <td className="px-3 py-2">{String(row.referenceRange ?? '—')}</td>
                        <td className="px-3 py-2">
                          <span
                            className={
                              abnormal
                                ? 'rounded-full border border-amber-300 bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-900'
                                : ''
                            }
                          >
                            {String(row.flag ?? '—')}
                          </span>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          ) : null}
          <FieldBlock label="Kết luận" value={item.conclusionText} />
        </div>
      ) : null}

      {templateCode === 'IMAGING_REPORT' ? (
        <div className="space-y-3">
          <FieldBlock
            label="Thông tin lâm sàng"
            value={typeof fieldValues.clinicalInfo === 'string' ? fieldValues.clinicalInfo : undefined}
          />
          <FieldBlock
            label="Kỹ thuật thực hiện"
            value={typeof fieldValues.technique === 'string' ? fieldValues.technique : undefined}
          />
          <FieldBlock
            label="Findings / Mô tả"
            value={typeof fieldValues.findings === 'string' ? fieldValues.findings : undefined}
          />
          <FieldBlock
            label="Impression"
            value={item.impressionText || (typeof fieldValues.impression === 'string' ? fieldValues.impression : undefined)}
          />
          <FieldBlock
            label="Khuyến nghị"
            value={typeof fieldValues.recommendation === 'string' ? fieldValues.recommendation : undefined}
          />
        </div>
      ) : null}

      {templateCode === 'PROCEDURE_REPORT' ? (
        <div className="space-y-3">
          <FieldBlock
            label="Thủ thuật"
            value={typeof fieldValues.procedureName === 'string' ? fieldValues.procedureName : undefined}
          />
          <FieldBlock
            label="Bệnh phẩm"
            value={typeof fieldValues.specimen === 'string' ? fieldValues.specimen : undefined}
          />
          <FieldBlock
            label="Đại thể"
            value={typeof fieldValues.macroscopy === 'string' ? fieldValues.macroscopy : undefined}
          />
          <FieldBlock
            label="Vi thể"
            value={typeof fieldValues.microscopy === 'string' ? fieldValues.microscopy : undefined}
          />
          <FieldBlock
            label="Kết luận"
            value={item.conclusionText || (typeof fieldValues.conclusion === 'string' ? fieldValues.conclusion : undefined)}
          />
        </div>
      ) : null}

      {templateCode === 'GENERIC_NARRATIVE' ? (
        <div className="space-y-3">
          <FieldBlock
            label="Tóm tắt"
            value={typeof fieldValues.summary === 'string' ? fieldValues.summary : undefined}
          />
          <FieldBlock
            label="Kết luận"
            value={item.conclusionText || (typeof fieldValues.conclusion === 'string' ? fieldValues.conclusion : undefined)}
          />
          <FieldBlock
            label="Khuyến nghị"
            value={typeof fieldValues.recommendation === 'string' ? fieldValues.recommendation : undefined}
          />
        </div>
      ) : null}

      {item.dueAt || item.turnaroundMinutes || item.elapsedMinutes || item.overdue ? (
        <div className="flex flex-wrap items-center gap-2 rounded-md border bg-background px-3 py-2 text-xs text-muted-foreground">
          {item.dueAt ? <span>Hạn SLA: {new Date(item.dueAt).toLocaleString('vi-VN')}</span> : null}
          {typeof item.turnaroundMinutes === 'number' ? (
            <span>TAT: {item.turnaroundMinutes.toFixed(0)} phút</span>
          ) : typeof item.elapsedMinutes === 'number' ? (
            <span>Đã trôi qua: {item.elapsedMinutes.toFixed(0)} phút</span>
          ) : null}
          {typeof item.turnaroundTargetMinutes === 'number' ? (
            <span>Mục tiêu: {item.turnaroundTargetMinutes.toFixed(0)} phút</span>
          ) : null}
          {item.overdue ? (
            <span className="rounded-full border border-destructive/25 bg-destructive/10 px-2 py-0.5 text-destructive">
              Quá SLA
            </span>
          ) : null}
        </div>
      ) : null}

      {attachments.length > 0 ? (
        <div className="space-y-2">
          <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
            Ảnh / tệp đính kèm
          </p>
          <div className="space-y-2">
            {attachments.map((attachment, index) => (
              <button
                key={`${attachment.url}-${index}`}
                type="button"
                onClick={() => {
                  void handleOpenAttachment(attachment, index);
                }}
                className="flex w-full items-center gap-2 rounded-md border bg-background px-3 py-2 text-left text-sm text-primary hover:bg-muted/40"
              >
                {isImageAttachment(attachment) ? (
                  <FileImage className="h-4 w-4" />
                ) : (
                  <FileText className="h-4 w-4" />
                )}
                <span className="truncate">{attachmentTitle(attachment, index)}</span>
              </button>
            ))}
          </div>
        </div>
      ) : null}

      {item.reportPdfStatus || item.reportPdfUrl ? (
        <div className="flex flex-wrap items-center gap-3 rounded-md border bg-background px-3 py-2 text-xs text-muted-foreground">
          <span>PDF: {item.reportPdfStatus || 'N/A'}</span>
          {item.reportPdfUrl ? (
            <button
              type="button"
              onClick={() => {
                void handleOpenResultPdf();
              }}
              className="text-primary underline-offset-4 hover:underline"
            >
              Mở phiếu PDF
            </button>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}

function getEncounterStep(encounter: Encounter, canComplete?: boolean) {
  if (encounter.status === 'COMPLETED') {
    return {
      title: 'Hoàn tất',
      description: 'Lượt khám đã hoàn tất. Chỉ mở lại khi cần bổ sung hoặc điều chỉnh hồ sơ.',
      badge: 'Đã hoàn tất',
      variant: 'success' as const,
    };
  }

  if (encounter.status === 'REOPENED') {
    return {
      title: 'Đã mở lại',
      description: 'Hồ sơ đang được bổ sung sau khi hoàn tất trước đó.',
      badge: 'Đang cập nhật',
      variant: 'warning' as const,
    };
  }

  if (encounter.hasPendingPayment || encounter.status === 'WAITING_PAYMENT') {
    return {
      title: 'Chờ thanh toán dịch vụ',
      description: 'Bác sĩ có thể xem hồ sơ, nhưng nên chờ thanh toán trước khi tiếp tục dịch vụ cận lâm sàng.',
      badge: 'Chờ thanh toán',
      variant: 'warning' as const,
    };
  }

  if (encounter.hasWaitingResults || encounter.status === 'WAITING_RESULTS') {
    return {
      title: 'Chờ kết quả',
      description: 'Theo dõi kết quả cận lâm sàng trước khi kết luận hoặc kê đơn.',
      badge: 'Chờ kết quả',
      variant: 'default' as const,
    };
  }

  if (canComplete || encounter.readyForConclusion || encounter.status === 'READY_FOR_CONCLUSION') {
    return {
      title: 'Sẵn sàng kết luận',
      description: 'Hồ sơ đã đủ điều kiện chính để bác sĩ kết luận và hoàn tất lượt khám.',
      badge: 'Có thể hoàn tất',
      variant: 'success' as const,
    };
  }

  return {
    title: 'Đang khám',
    description: 'Tập trung ghi nhận triệu chứng, khám lâm sàng, chẩn đoán và kế hoạch điều trị.',
    badge: 'Đang xử lý',
    variant: 'default' as const,
  };
}

export function EncounterActionsBar({
  encounter,
  canEdit,
  canOrderServices,
  canCreatePrescription,
  canComplete,
  isSavingWorkspace,
  completePending,
  formDirty,
  onSave,
  onOrderServices,
  onCreatePrescription,
  onLeave,
  onReopen,
  onComplete,
}: EncounterActionProps) {
  const actions = [
    {
      key: 'save',
      label: 'Lưu thông tin khám',
      icon: Save,
      disabled: !canEdit || isSavingWorkspace,
      onClick: onSave,
      priority:
        encounter.status !== 'COMPLETED' &&
        (formDirty || (!canOrderServices && !canCreatePrescription && !canComplete)),
    },
    {
      key: 'order',
      label: 'Chỉ định dịch vụ',
      icon: Plus,
      disabled: !canOrderServices,
      onClick: onOrderServices,
      priority: !formDirty && canOrderServices && !canComplete,
    },
    {
      key: 'prescription',
      label: 'Kê đơn',
      icon: Pill,
      disabled: !canCreatePrescription,
      onClick: onCreatePrescription,
      priority: !formDirty && !canOrderServices && canCreatePrescription && !canComplete,
    },
    {
      key: 'complete',
      label: 'Hoàn tất lượt khám',
      icon: CheckCircle2,
      disabled: !canComplete || completePending || isSavingWorkspace,
      onClick: onComplete,
      priority: canComplete && encounter.status !== 'COMPLETED',
      className: 'bg-success text-success-foreground hover:bg-success/90',
    },
    {
      key: 'reopen',
      label: 'Mở lại hồ sơ',
      icon: ClipboardList,
      disabled: encounter.status !== 'COMPLETED',
      onClick: onReopen,
      priority: encounter.status === 'COMPLETED',
    },
  ];
  const primary = actions.find((action) => action.priority) ?? actions[0];
  const secondaryActions = actions.filter((action) => action.key !== primary.key);
  const PrimaryIcon = primary.icon;

  return (
    <div className="flex flex-wrap items-center gap-2">
      <Button
        type="button"
        onClick={() => primary.onClick()}
        disabled={primary.disabled}
        className={primary.className}
      >
        <PrimaryIcon className="h-4 w-4" />
        {primary.label}
      </Button>

      <details className="group relative">
        <summary className="flex h-10 cursor-pointer list-none items-center gap-2 rounded-lg border border-input bg-background px-3 text-sm font-medium shadow-sm transition-colors hover:border-primary/30 hover:bg-primary/5 hover:text-primary">
          Thao tác khác
          <ChevronDown className="h-4 w-4 transition-transform group-open:rotate-180" />
        </summary>
        <div className="absolute right-0 z-40 mt-2 w-56 rounded-lg border bg-card p-2 shadow-lg">
          <div className="space-y-1">
            {secondaryActions.map((action) => {
              const Icon = action.icon;
              return (
                <Button
                  key={action.key}
                  type="button"
                  variant="ghost"
                  className="w-full justify-start"
                  onClick={() => action.onClick()}
                  disabled={action.disabled}
                >
                  <Icon className="h-4 w-4" />
                  {action.label}
                </Button>
              );
            })}
            <Button
              type="button"
              variant="ghost"
              className="w-full justify-start"
              onClick={onLeave}
              disabled={isSavingWorkspace}
            >
              <ArrowLeft className="h-4 w-4" />
              Tạm rời hồ sơ
            </Button>
          </div>
        </div>
      </details>
    </div>
  );
}

export function EncounterHeader({
  encounter,
  encounterTitle,
  patientMeta,
  encounterTimeLabel,
  actionProps,
}: {
  encounter: Encounter;
  encounterTitle: string;
  patientMeta: string;
  encounterTimeLabel: string;
  actionProps: EncounterActionProps;
}) {
  return (
    <div className="sticky top-0 z-30 -mx-4 -mt-4 mb-4 border-b bg-background/95 px-4 py-3 backdrop-blur md:-mx-6 md:-mt-6 md:px-6 lg:-mx-8 lg:-mt-8 lg:px-8">
      <div className="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
        <div className="min-w-0 space-y-2">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="truncate text-xl font-semibold tracking-tight text-foreground">
              {encounter.patientName}
            </h1>
            <StatusBadge status={encounter.status} />
          </div>
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-muted-foreground">
            <span>{encounterTitle}</span>
            <span className="max-w-[18rem] truncate">{encounter.code || encounter.id}</span>
            <span>{encounter.doctorName}</span>
            {encounterTimeLabel ? <span>{encounterTimeLabel}</span> : null}
            {patientMeta ? <span>{patientMeta}</span> : null}
          </div>
        </div>

        <EncounterActionsBar {...actionProps} />
      </div>
    </div>
  );
}

export function EncounterStatusBanner({
  encounter,
  canComplete,
  completionReasons,
}: {
  encounter: Encounter;
  canComplete: boolean;
  completionReasons: string[];
}) {
  const step = getEncounterStep(encounter, canComplete);
  const visibleReasons = completionReasons.slice(0, 3);

  return (
    <section className="rounded-xl border bg-card p-4">
      <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant={step.variant}>{step.badge}</Badge>
            {encounter.hasPendingPayment ? <Badge variant="warning">Chờ thanh toán</Badge> : null}
            {encounter.hasWaitingResults ? <Badge>Chờ kết quả</Badge> : null}
            {encounter.readyForConclusion ? <Badge variant="success">Sẵn sàng kết luận</Badge> : null}
          </div>
          <h2 className="mt-3 text-lg font-semibold tracking-tight text-foreground">{step.title}</h2>
          <p className="mt-1 max-w-3xl text-sm leading-6 text-muted-foreground">{step.description}</p>
        </div>
        <div className="flex items-center gap-2 rounded-lg bg-muted/25 px-3 py-2 text-sm text-muted-foreground">
          {canComplete ? (
            <CheckCircle2 className="h-4 w-4 text-success" />
          ) : (
            <CircleAlert className="h-4 w-4 text-warning" />
          )}
          <span>{canComplete ? 'Không còn chặn hoàn tất' : 'Còn điều kiện cần xử lý'}</span>
        </div>
      </div>

      {!canComplete ? (
        <div className="mt-4 flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
          <span className="font-medium text-foreground">Cần xử lý:</span>
          {visibleReasons.length > 0 ? (
            visibleReasons.map((reason) => (
              <span key={reason} className="rounded-full bg-muted px-2.5 py-1 text-xs">
                {reason}
              </span>
            ))
          ) : (
            <span>Hệ thống cần kiểm tra lại điều kiện sau khi lưu hồ sơ.</span>
          )}
          {completionReasons.length > visibleReasons.length ? (
            <span className="text-xs">+{completionReasons.length - visibleReasons.length} lý do khác</span>
          ) : null}
        </div>
      ) : null}
    </section>
  );
}

export function PatientSummaryCard({ encounter }: { encounter: Encounter }) {
  const dob = encounter.patientDob ? new Date(encounter.patientDob).toLocaleDateString('vi-VN') : '—';

  return (
    <section className="rounded-xl border bg-card p-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h3 className="text-base font-semibold text-foreground">Bệnh nhân</h3>
          <p className="mt-1 text-sm text-muted-foreground">{encounter.patientName}</p>
        </div>
        <StatusBadge status={encounter.status} />
      </div>
      <dl className="mt-4 grid grid-cols-1 gap-3 text-sm">
        <div>
          <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Liên hệ</dt>
          <dd className="mt-1 text-foreground">{encounter.patientPhone || '—'}</dd>
          {encounter.patientEmail ? <dd className="text-muted-foreground">{encounter.patientEmail}</dd> : null}
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Ngày sinh</dt>
            <dd className="mt-1 text-foreground">{dob}</dd>
          </div>
          <div>
            <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Giới tính</dt>
            <dd className="mt-1 text-foreground">{encounter.patientGender || '—'}</dd>
          </div>
        </div>
        {encounter.emergencyContactName || encounter.emergencyContactPhone ? (
          <div>
            <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Liên hệ khẩn cấp</dt>
            <dd className="mt-1 text-foreground">
              {[encounter.emergencyContactName, encounter.emergencyContactPhone].filter(Boolean).join(' · ')}
            </dd>
          </div>
        ) : null}
      </dl>
    </section>
  );
}

export function QuickEncounterSection({
  form,
  setForm,
  encounter,
  canEdit,
}: {
  form: EncounterFormState;
  setForm: SetEncounterForm;
  encounter: Encounter;
  canEdit: boolean;
}) {
  return (
    <AccordionItem value="quick" className="rounded-xl border bg-card px-4 shadow-sm">
      <AccordionTrigger className="py-3 text-left hover:no-underline">
        <div>
          <p className="font-semibold text-foreground">Thông tin khám nhanh</p>
          <p className="mt-1 text-xs font-normal text-muted-foreground">
            {renderVisitType(form.visitType || encounter.visitType)} · {renderPriority(form.triagePriority || encounter.triagePriority)}
          </p>
        </div>
      </AccordionTrigger>
      <AccordionContent className="space-y-4 pb-4">
        <div className="grid gap-4 md:grid-cols-3">
          <div>
            <label className="mb-1.5 block text-sm font-medium">Loại lượt khám</label>
            <Select
              value={form.visitType || undefined}
              onValueChange={(value) => setForm((current) => ({ ...current, visitType: value }))}
              disabled={!canEdit}
            >
              <SelectTrigger><SelectValue placeholder="Chọn loại lượt khám" /></SelectTrigger>
              <SelectContent>
                <SelectItem value="NEW_PATIENT">Khám mới</SelectItem>
                <SelectItem value="FOLLOW_UP">Tái khám</SelectItem>
                <SelectItem value="CONSULTATION">Tư vấn</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div>
            <label className="mb-1.5 block text-sm font-medium">Mức ưu tiên</label>
            <Select
              value={form.triagePriority || undefined}
              onValueChange={(value) => setForm((current) => ({ ...current, triagePriority: value }))}
              disabled={!canEdit}
            >
              <SelectTrigger><SelectValue placeholder="Chọn mức ưu tiên" /></SelectTrigger>
              <SelectContent>
                <SelectItem value="ROUTINE">Thông thường</SelectItem>
                <SelectItem value="PRIORITY">Ưu tiên</SelectItem>
                <SelectItem value="URGENT">Khẩn</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div>
            <label className="mb-1.5 block text-sm font-medium">Ghi chú bảo hiểm</label>
            <Input
              value={form.insuranceNote}
              onChange={(event) => setForm((current) => ({ ...current, insuranceNote: event.target.value }))}
              disabled={!canEdit}
            />
          </div>
        </div>

        <div className="grid gap-4 md:grid-cols-2">
          <div>
            <label className="mb-1.5 block text-sm font-medium">Lý do đến khám</label>
            <Textarea
              value={form.intakeReasonForVisit}
              onChange={(event) => setForm((current) => ({ ...current, intakeReasonForVisit: event.target.value }))}
              rows={2}
              disabled={!canEdit}
              placeholder="Thông tin từ tiếp nhận..."
            />
          </div>
          <div>
            <label className="mb-1.5 block text-sm font-medium">Triệu chứng chính</label>
            <Textarea
              value={form.chiefComplaint}
              onChange={(event) => setForm((current) => ({ ...current, chiefComplaint: event.target.value }))}
              rows={2}
              disabled={!canEdit}
              placeholder="Triệu chứng chính khiến bệnh nhân đến khám..."
            />
          </div>
        </div>

        <div>
          <label className="mb-1.5 block text-sm font-medium">Ghi chú sàng lọc / điều dưỡng</label>
          <Textarea
            value={form.triageNote}
            onChange={(event) => setForm((current) => ({ ...current, triageNote: event.target.value }))}
            rows={2}
            disabled={!canEdit}
          />
        </div>
      </AccordionContent>
    </AccordionItem>
  );
}

export function VitalsSection({
  form,
  setForm,
  canEdit,
}: {
  form: EncounterFormState;
  setForm: SetEncounterForm;
  canEdit: boolean;
}) {
  return (
    <AccordionItem value="vitals" className="rounded-xl border bg-card px-4 shadow-sm">
      <AccordionTrigger className="py-3 text-left hover:no-underline">
        <div>
          <p className="font-semibold text-foreground">Sinh hiệu / tiền sử</p>
          <p className="mt-1 text-xs font-normal text-muted-foreground">
            Mặc định thu gọn để dành chỗ cho chẩn đoán và kết luận
          </p>
        </div>
      </AccordionTrigger>
      <AccordionContent className="space-y-4 pb-4">
        <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
          {([
            ['heightCm', 'Chiều cao', 'cm'],
            ['weightKg', 'Cân nặng', 'kg'],
            ['temperatureC', 'Nhiệt độ', '°C'],
            ['pulse', 'Nhịp tim', 'lần/ph'],
            ['systolicBp', 'HA tâm thu', 'mmHg'],
            ['diastolicBp', 'HA tâm trương', 'mmHg'],
            ['respiratoryRate', 'Nhịp thở', 'lần/ph'],
            ['spo2', 'SpO2', '%'],
          ] as const).map(([key, label, unit]) => (
            <div key={key}>
              <label className="mb-1.5 block text-xs font-medium text-muted-foreground">
                {label}
              </label>
              <div className="relative">
                <Input
                  type="number"
                  value={form[key]}
                  onChange={(event) =>
                    setForm((current) => ({ ...current, [key]: event.target.value }))
                  }
                  disabled={!canEdit}
                  className="pr-14 font-semibold"
                />
                <span className="absolute right-3 top-2.5 text-xs text-muted-foreground">
                  {unit}
                </span>
              </div>
            </div>
          ))}
        </div>

        <div className="grid gap-4 md:grid-cols-3">
          <div>
            <label className="mb-1.5 block text-sm font-medium">Dị ứng</label>
            <Textarea
              value={form.allergySnapshot}
              onChange={(event) => setForm((current) => ({ ...current, allergySnapshot: event.target.value }))}
              rows={2}
              disabled={!canEdit}
            />
          </div>
          <div>
            <label className="mb-1.5 block text-sm font-medium">Bệnh mạn tính</label>
            <Textarea
              value={form.chronicDiseaseSnapshot}
              onChange={(event) => setForm((current) => ({ ...current, chronicDiseaseSnapshot: event.target.value }))}
              rows={2}
              disabled={!canEdit}
            />
          </div>
          <div>
            <label className="mb-1.5 block text-sm font-medium">Tiền sử</label>
            <Textarea
              value={form.pastMedicalHistory}
              onChange={(event) => setForm((current) => ({ ...current, pastMedicalHistory: event.target.value }))}
              rows={2}
              disabled={!canEdit}
            />
          </div>
        </div>
      </AccordionContent>
    </AccordionItem>
  );
}

export function ClinicalNoteSection({
  form,
  setForm,
  canEdit,
}: {
  form: EncounterFormState;
  setForm: SetEncounterForm;
  canEdit: boolean;
}) {
  return (
    <AccordionItem value="exam" className="rounded-xl border bg-card px-4 shadow-sm">
      <AccordionTrigger className="py-3 text-left hover:no-underline">
        <div>
          <p className="font-semibold text-foreground">Khám lâm sàng</p>
          <p className="mt-1 text-xs font-normal text-muted-foreground">
            Khám thực thể và ghi chú lâm sàng
          </p>
        </div>
      </AccordionTrigger>
      <AccordionContent className="grid gap-4 pb-4 md:grid-cols-2">
        <div>
          <label className="mb-1.5 block text-sm font-medium">Khám thực thể</label>
          <Textarea
            value={form.physicalExamination}
            onChange={(event) => setForm((current) => ({ ...current, physicalExamination: event.target.value }))}
            rows={4}
            disabled={!canEdit}
            placeholder="Ghi nhận qua thăm khám lâm sàng..."
          />
        </div>
        <div>
          <label className="mb-1.5 block text-sm font-medium">Ghi chú lâm sàng</label>
          <Textarea
            value={form.clinicalNote}
            onChange={(event) => setForm((current) => ({ ...current, clinicalNote: event.target.value }))}
            rows={4}
            disabled={!canEdit}
            placeholder="Diễn biến, phân tích, hướng xử trí..."
          />
        </div>
      </AccordionContent>
    </AccordionItem>
  );
}

export function DiagnosisSection({
  form,
  setForm,
  canEdit,
}: {
  form: EncounterFormState;
  setForm: SetEncounterForm;
  canEdit: boolean;
}) {
  return (
    <AccordionItem value="diagnosis" className="rounded-xl border bg-card px-4 shadow-sm">
      <AccordionTrigger className="py-3 text-left hover:no-underline">
        <div>
          <p className="font-semibold text-foreground">Chẩn đoán</p>
          <p className="mt-1 text-xs font-normal text-muted-foreground">
            {form.diagnoses.length > 0
              ? `${form.diagnoses.length} chẩn đoán ICD-10`
              : 'Cần có chẩn đoán chính ICD-10 để hoàn tất'}
          </p>
        </div>
      </AccordionTrigger>
      <AccordionContent className="space-y-3 pb-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="text-sm text-muted-foreground">ICD-10 diagnosis</p>
          {canEdit ? (
            <Icd10Select
              onSelect={(code) => {
                setForm((current) => ({
                  ...current,
                  diagnoses: [
                    ...current.diagnoses,
                    {
                      icd10CodeId: code.id,
                      icd10Code: code.code,
                      icd10NameVn: code.nameVn,
                      diagnosisType: current.diagnoses.length === 0 ? 'FINAL' : 'SECONDARY',
                    },
                  ],
                }));
              }}
            />
          ) : null}
        </div>

        {form.diagnoses.length === 0 ? (
          <div className="rounded-lg border border-dashed bg-muted/10 px-4 py-6 text-center text-sm text-muted-foreground">
            Chưa có chẩn đoán nào được ghi nhận.
          </div>
        ) : (
          <div className="space-y-2">
            {form.diagnoses.map((diagnosis, index) => (
              <div key={`${diagnosis.icd10CodeId}-${index}`} className="rounded-lg border bg-muted/10 p-3">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="rounded-full bg-primary/10 px-2 py-0.5 text-xs font-semibold text-primary">
                        {getDiagnosisTypeLabel(diagnosis.diagnosisType)}
                      </span>
                      <span className="font-semibold text-foreground">{diagnosis.icd10Code}</span>
                      <span className="text-sm text-foreground">{diagnosis.icd10NameVn}</span>
                    </div>
                  </div>
                  {canEdit ? (
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      aria-label="Xóa chẩn đoán"
                      onClick={() => {
                        setForm((current) => {
                          const nextDiagnoses = [...current.diagnoses];
                          nextDiagnoses.splice(index, 1);
                          return { ...current, diagnoses: nextDiagnoses };
                        });
                      }}
                      className="text-muted-foreground hover:bg-destructive/10 hover:text-destructive"
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  ) : null}
                </div>
                <div className="mt-3 grid gap-3 md:grid-cols-[220px_minmax(0,1fr)]">
                  <div>
                    <label className="mb-1.5 block text-xs font-medium text-muted-foreground">
                      Loại chẩn đoán
                    </label>
                    <Select
                      value={diagnosis.diagnosisType}
                      onValueChange={(value) => {
                        setForm((current) => {
                          const nextDiagnoses = [...current.diagnoses];
                          nextDiagnoses[index] = {
                            ...nextDiagnoses[index],
                            diagnosisType: value as EncounterDiagnosisResponse['diagnosisType'],
                          };
                          return { ...current, diagnoses: nextDiagnoses };
                        });
                      }}
                      disabled={!canEdit}
                    >
                      <SelectTrigger className="h-9 bg-background"><SelectValue /></SelectTrigger>
                      <SelectContent>
                        <SelectItem value="PRELIMINARY">Chẩn đoán sơ bộ</SelectItem>
                        <SelectItem value="FINAL">Chẩn đoán chính ICD-10</SelectItem>
                        <SelectItem value="SECONDARY">Chẩn đoán kèm theo</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                  <div>
                    <label className="mb-1.5 block text-xs font-medium text-muted-foreground">
                      Ghi chú
                    </label>
                    <Input
                      className="h-9 bg-background"
                      value={diagnosis.note || ''}
                      onChange={(event) => {
                        setForm((current) => {
                          const nextDiagnoses = [...current.diagnoses];
                          nextDiagnoses[index] = {
                            ...nextDiagnoses[index],
                            note: event.target.value,
                          };
                          return { ...current, diagnoses: nextDiagnoses };
                        });
                      }}
                      disabled={!canEdit}
                      placeholder="Chi tiết thêm về chẩn đoán..."
                    />
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </AccordionContent>
    </AccordionItem>
  );
}

export function ConclusionSection({
  form,
  setForm,
  canEdit,
}: {
  form: EncounterFormState;
  setForm: SetEncounterForm;
  canEdit: boolean;
}) {
  return (
    <AccordionItem value="plan" className="rounded-xl border bg-card px-4 shadow-sm">
      <AccordionTrigger className="py-3 text-left hover:no-underline">
        <div>
          <p className="font-semibold text-foreground">Kết luận & kế hoạch</p>
          <p className="mt-1 text-xs font-normal text-muted-foreground">
            Kết luận, điều trị và tái khám
          </p>
        </div>
      </AccordionTrigger>
      <AccordionContent className="space-y-4 pb-4">
        <div className="grid gap-4 md:grid-cols-2">
          <div>
            <label className="mb-1.5 block text-sm font-medium">Kết luận</label>
            <Textarea
              value={form.conclusion}
              onChange={(event) => setForm((current) => ({ ...current, conclusion: event.target.value }))}
              rows={4}
              disabled={!canEdit}
              placeholder="Tổng kết chung về tình trạng và bệnh lý..."
            />
          </div>
          <div>
            <label className="mb-1.5 block text-sm font-medium">Kế hoạch điều trị</label>
            <Textarea
              value={form.treatmentPlan}
              onChange={(event) => setForm((current) => ({ ...current, treatmentPlan: event.target.value }))}
              rows={4}
              disabled={!canEdit}
              placeholder="Phương pháp điều trị, chỉ định, dặn dò..."
            />
          </div>
        </div>
        <div className="grid gap-4 md:grid-cols-[220px_minmax(0,1fr)]">
          <div>
            <label className="mb-1.5 block text-sm font-medium">Ngày tái khám</label>
            <Input
              type="date"
              value={form.followUpDate}
              onChange={(event) => setForm((current) => ({ ...current, followUpDate: event.target.value }))}
              disabled={!canEdit}
            />
          </div>
          <div>
            <label className="mb-1.5 block text-sm font-medium">Ghi chú tái khám</label>
            <Input
              value={form.followUpNote}
              onChange={(event) => setForm((current) => ({ ...current, followUpNote: event.target.value }))}
              disabled={!canEdit}
              placeholder="Mục đích tái khám..."
            />
          </div>
        </div>
      </AccordionContent>
    </AccordionItem>
  );
}

export function ServiceOrdersSection({
  orders,
  canCreate,
  onCreate,
  language,
}: {
  orders: ServiceOrder[];
  canCreate: boolean;
  onCreate: () => void;
  language?: string;
}) {
  const totalItems = orders.reduce((acc, order) => acc + order.items.length, 0);
  const doneItems = orders.reduce(
    (acc, order) => acc + order.items.filter(isServiceItemDone).length,
    0,
  );

  return (
    <section className="rounded-xl border bg-card p-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h3 className="text-base font-semibold text-foreground">Chỉ định dịch vụ</h3>
          <p className="mt-1 text-sm text-muted-foreground">
            {orders.length > 0
              ? `${orders.length} phiếu, ${doneItems}/${totalItems} dịch vụ có kết quả`
              : 'Chưa có chỉ định'}
          </p>
        </div>
        <Button type="button" size="sm" variant="outline" onClick={onCreate} disabled={!canCreate}>
          <Plus className="h-4 w-4" />
          Tạo
        </Button>
      </div>

      {orders.length === 0 ? (
        <div className="mt-4 rounded-lg border border-dashed bg-muted/10 px-3 py-5 text-center text-sm text-muted-foreground">
          Chưa có dịch vụ cận lâm sàng trong lượt khám.
        </div>
      ) : (
        <div className="mt-4 space-y-2">
          {orders.map((order) => {
            const firstItem = order.items[0];
            const hiddenItemCount = Math.max(order.items.length - 1, 0);
            const orderDoneItems = order.items.filter(isServiceItemDone).length;

            return (
              <details key={order.id} className="group rounded-lg border bg-background/60 px-3 py-2.5">
                <summary className="flex cursor-pointer list-none items-start justify-between gap-3">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="font-medium text-foreground">{order.code}</p>
                      {order.status ? <StatusBadge status={order.status} /> : null}
                    </div>
                    <p className="mt-1 truncate text-sm text-muted-foreground">
                      {firstItem ? getServiceDisplayName(firstItem, language) : 'Chưa có dịch vụ'}
                      {hiddenItemCount > 0 ? ` +${hiddenItemCount} dịch vụ khác` : ''}
                    </p>
                    <p className="mt-1 text-xs text-muted-foreground">
                      {orderDoneItems}/{order.items.length} có kết quả
                      {order.paymentStatus ? ` · Thanh toán: ${order.paymentStatus}` : ''}
                    </p>
                  </div>
                  <ChevronDown className="mt-1 h-4 w-4 shrink-0 text-muted-foreground transition-transform group-open:rotate-180" />
                </summary>

                <div className="mt-3 space-y-3 border-t pt-3">
                  <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-muted-foreground">
                    <span>{order.orderedAt ? new Date(order.orderedAt).toLocaleString('vi-VN') : 'Chưa có thời gian chỉ định'}</span>
                    <span className="font-medium text-foreground">
                      {currency.format(order.estimatedTotalAmount || 0)} đ
                    </span>
                  </div>

                  <div className="space-y-2">
                    {order.items.map((item) => (
                      <div key={item.id} className="rounded-md border bg-muted/10 px-3 py-2">
                        <div className="flex flex-wrap items-start justify-between gap-2">
                          <div className="min-w-0">
                            <p className="font-medium text-foreground">
                              {getServiceDisplayName(item, language)}
                            </p>
                            <p className="mt-0.5 text-xs text-muted-foreground">
                              SL {item.quantity} · {currency.format(item.lineTotalAmount || 0)} đ
                            </p>
                          </div>
                          <div className="flex flex-wrap items-center gap-1.5">
                            {item.status ? <StatusBadge status={item.status} /> : null}
                            {item.resultStatus ? (
                              <span className="rounded-full bg-muted px-2 py-1 text-xs text-muted-foreground">
                                KQ: {item.resultStatus}
                              </span>
                            ) : null}
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              </details>
            );
          })}
        </div>
      )}
    </section>
  );
}

export function ResultsSection({
  orders,
  language,
}: {
  orders: ServiceOrder[];
  language?: string;
}) {
  const resultItems = orders.flatMap((order) =>
    order.items
      .filter(hasServiceResult)
      .map((item) => ({ orderCode: order.code, item })),
  );

  return (
    <section className="rounded-xl border bg-card p-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h3 className="text-base font-semibold text-foreground">Kết quả cận lâm sàng</h3>
          <p className="mt-1 text-sm text-muted-foreground">
            {resultItems.length > 0 ? `${resultItems.length} kết quả đã ghi nhận` : 'Chưa có kết quả'}
          </p>
        </div>
        <FileText className="mt-1 h-4 w-4 text-primary" />
      </div>

      {resultItems.length === 0 ? (
        <div className="mt-4 rounded-lg border border-dashed bg-muted/10 px-3 py-5 text-center text-sm text-muted-foreground">
          Kết quả sẽ xuất hiện tại đây sau khi bộ phận cận lâm sàng hoàn tất.
        </div>
      ) : (
        <div className="mt-4 space-y-2">
          {resultItems.map(({ orderCode, item }) => (
            <details key={`${orderCode}-${item.id}`} className="group rounded-lg border bg-background/60 px-3 py-2.5">
              <summary className="flex cursor-pointer list-none items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="font-medium text-foreground">{getServiceDisplayName(item, language)}</p>
                  <p className="mt-1 text-xs text-muted-foreground">
                    {orderCode} · {item.resultStatus || item.status || 'Có kết quả'}
                  </p>
                </div>
                <ChevronDown className="mt-1 h-4 w-4 shrink-0 text-muted-foreground transition-transform group-open:rotate-180" />
              </summary>
              <StructuredServiceResult item={item} />
            </details>
          ))}
        </div>
      )}
    </section>
  );
}

export function PrescriptionSection({
  encounterId,
  canCreatePrescription,
  canEdit,
}: {
  encounterId: string;
  canCreatePrescription: boolean;
  canEdit: boolean;
}) {
  return (
    <EncounterPrescriptionsPanel
      encounterId={encounterId}
      canCreatePrescription={canCreatePrescription}
      canEdit={canEdit}
    />
  );
}

export function EncounterTimelineSection({ encounterId }: { encounterId: string }) {
  const { data: timeline = [], isLoading } = useDoctorEncounterTimeline(encounterId);

  return (
    <details className="group rounded-xl border bg-card p-4">
      <summary className="flex cursor-pointer list-none items-start justify-between gap-3">
        <div>
          <h3 className="text-base font-semibold text-foreground">Lịch sử hồ sơ</h3>
          <p className="mt-1 text-sm text-muted-foreground">
            {isLoading ? 'Đang tải timeline...' : `${timeline.length} sự kiện`}
          </p>
        </div>
        <ChevronDown className="mt-1 h-4 w-4 text-muted-foreground transition-transform group-open:rotate-180" />
      </summary>
      <div className="mt-4 border-t pt-4">
        <PatientJourneyTimeline
          items={timeline}
          compact
          emptyText={isLoading ? 'Đang tải timeline...' : 'Chưa có lịch sử cho hồ sơ này.'}
        />
      </div>
    </details>
  );
}
