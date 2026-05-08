import { useEffect, useMemo, useState } from 'react';
import {
  AlertTriangle,
  Clock3,
  FileImage,
  FileText,
  FlaskConical,
  Loader2,
  Microscope,
  Plus,
  Search,
  ShieldCheck,
  TestTube2,
  Trash2,
  UploadCloud,
} from 'lucide-react';
import { toast } from 'sonner';
import apiClient from '@/lib/api-client';
import { openProtectedFile } from '@/lib/download-file';
import { AppPagination } from '@/components/AppPagination';
import { PageHeader } from '@/components/PageHeader';
import { QueueBoard } from '@/components/QueueBoard';
import { LivePulseBadge } from '@/components/LivePulseBadge';
import { StatCard } from '@/components/StatCard';
import { StatusBadge } from '@/components/StatusBadge';
import { EmptyState } from '@/components/EmptyState';
import { ErrorState } from '@/components/ErrorState';
import { LoadingSkeleton } from '@/components/LoadingSkeleton';
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
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { getApiErrorMessage } from '@/lib/error-utils';
import {
  useServiceDeskQueue,
  useServiceDeskSummary,
  useServiceResultHistory,
  useSubmitServiceResult,
} from '@/hooks/use-service-desk-data';
import { useDebouncedValue } from '@/hooks/use-debounced-value';
import type {
  AuditLog,
  ServiceDeskQueueItem,
  ServiceResultAttachment,
  ServiceResultTemplateCode,
} from '@/types/api';

const EMPTY_LAB_ROW = {
  parameter: '',
  result: '',
  unit: '',
  referenceRange: '',
  flag: '',
  note: '',
};

type LabRow = typeof EMPTY_LAB_ROW;

type ResultFormState = {
  templateCode: ServiceResultTemplateCode;
  templateSchemaJson: string;
  reportTitle: string;
  resultTextVn: string;
  resultTextEn: string;
  conclusionText: string;
  impressionText: string;
  attachments: ServiceResultAttachment[];
  genericSummary: string;
  genericRecommendation: string;
  labSpecimen: string;
  labClinicalInfo: string;
  labDeviceName: string;
  labRows: LabRow[];
  imagingClinicalInfo: string;
  imagingTechnique: string;
  imagingFindings: string;
  imagingRecommendation: string;
  procedureName: string;
  procedureSpecimen: string;
  procedureMacroscopy: string;
  procedureMicroscopy: string;
};

function formatDateTime(value?: string) {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('vi-VN');
}

function getServiceLabel(item: ServiceDeskQueueItem) {
  return item.serviceNameVn || item.serviceNameEn || item.serviceCode || 'Dịch vụ';
}

function safeParseJson<T>(value?: string, fallback?: T): T | undefined {
  if (!value?.trim()) return fallback;
  try {
    return JSON.parse(value) as T;
  } catch {
    return fallback;
  }
}

function createDefaultForm(item?: ServiceDeskQueueItem): ResultFormState {
  const templateCode = item?.templateCode ?? 'GENERIC_NARRATIVE';
  const fieldValues = safeParseJson<Record<string, unknown>>(
    item?.fieldValuesJson || item?.resultDataJson,
    {},
  ) ?? {};
  const rows = Array.isArray(fieldValues.rows)
    ? fieldValues.rows
        .map((row) => {
          if (!row || typeof row !== 'object') return null;
          const data = row as Record<string, unknown>;
          return {
            parameter: typeof data.parameter === 'string' ? data.parameter : '',
            result: typeof data.result === 'string' ? data.result : '',
            unit: typeof data.unit === 'string' ? data.unit : '',
            referenceRange:
              typeof data.referenceRange === 'string' ? data.referenceRange : '',
            flag: typeof data.flag === 'string' ? data.flag : '',
            note: typeof data.note === 'string' ? data.note : '',
          };
        })
        .filter((row): row is LabRow => Boolean(row))
    : [];

  return {
    templateCode,
    templateSchemaJson: item?.templateSchemaJson || '',
    reportTitle: item?.reportTitle || getServiceLabel(item ?? ({ } as ServiceDeskQueueItem)),
    resultTextVn: item?.resultTextVn || '',
    resultTextEn: item?.resultTextEn || '',
    conclusionText:
      item?.conclusionText ||
      (typeof fieldValues.conclusion === 'string' ? fieldValues.conclusion : '') ||
      '',
    impressionText:
      item?.impressionText ||
      (typeof fieldValues.impression === 'string' ? fieldValues.impression : '') ||
      '',
    attachments: item?.attachments ?? [],
    genericSummary: typeof fieldValues.summary === 'string' ? fieldValues.summary : '',
    genericRecommendation:
      typeof fieldValues.recommendation === 'string' ? fieldValues.recommendation : '',
    labSpecimen: typeof fieldValues.specimen === 'string' ? fieldValues.specimen : '',
    labClinicalInfo:
      typeof fieldValues.clinicalInfo === 'string' ? fieldValues.clinicalInfo : '',
    labDeviceName: typeof fieldValues.deviceName === 'string' ? fieldValues.deviceName : '',
    labRows: rows.length > 0 ? rows : [{ ...EMPTY_LAB_ROW }],
    imagingClinicalInfo:
      typeof fieldValues.clinicalInfo === 'string' ? fieldValues.clinicalInfo : '',
    imagingTechnique: typeof fieldValues.technique === 'string' ? fieldValues.technique : '',
    imagingFindings: typeof fieldValues.findings === 'string' ? fieldValues.findings : '',
    imagingRecommendation:
      typeof fieldValues.recommendation === 'string' ? fieldValues.recommendation : '',
    procedureName:
      typeof fieldValues.procedureName === 'string' ? fieldValues.procedureName : '',
    procedureSpecimen: typeof fieldValues.specimen === 'string' ? fieldValues.specimen : '',
    procedureMacroscopy:
      typeof fieldValues.macroscopy === 'string' ? fieldValues.macroscopy : '',
    procedureMicroscopy:
      typeof fieldValues.microscopy === 'string' ? fieldValues.microscopy : '',
  };
}

function buildFieldValues(form: ResultFormState) {
  switch (form.templateCode) {
    case 'LAB_TABLE':
      return {
        specimen: form.labSpecimen.trim() || undefined,
        clinicalInfo: form.labClinicalInfo.trim() || undefined,
        deviceName: form.labDeviceName.trim() || undefined,
        rows: form.labRows
          .map((row) => ({
            parameter: row.parameter.trim(),
            result: row.result.trim(),
            unit: row.unit.trim() || undefined,
            referenceRange: row.referenceRange.trim() || undefined,
            flag: row.flag.trim() || undefined,
            note: row.note.trim() || undefined,
          }))
          .filter((row) => row.parameter || row.result || row.unit || row.referenceRange || row.flag || row.note),
      };
    case 'IMAGING_REPORT':
      return {
        clinicalInfo: form.imagingClinicalInfo.trim() || undefined,
        technique: form.imagingTechnique.trim() || undefined,
        findings: form.imagingFindings.trim() || undefined,
        impression: form.impressionText.trim() || undefined,
        recommendation: form.imagingRecommendation.trim() || undefined,
      };
    case 'PROCEDURE_REPORT':
      return {
        procedureName: form.procedureName.trim() || undefined,
        specimen: form.procedureSpecimen.trim() || undefined,
        macroscopy: form.procedureMacroscopy.trim() || undefined,
        microscopy: form.procedureMicroscopy.trim() || undefined,
        conclusion: form.conclusionText.trim() || undefined,
      };
    case 'GENERIC_NARRATIVE':
    default:
      return {
        summary: form.genericSummary.trim() || undefined,
        conclusion: form.conclusionText.trim() || undefined,
        recommendation: form.genericRecommendation.trim() || undefined,
      };
  }
}

function buildQuickPreview(form: ResultFormState) {
  if (form.resultTextVn.trim()) return form.resultTextVn.trim();

  switch (form.templateCode) {
    case 'LAB_TABLE': {
      const firstRow = form.labRows.find((row) => row.parameter.trim() || row.result.trim());
      const pieces = [
        form.labSpecimen.trim(),
        firstRow ? `${firstRow.parameter}: ${firstRow.result}` : '',
        form.conclusionText.trim(),
      ].filter(Boolean);
      return pieces.join(' · ');
    }
    case 'IMAGING_REPORT':
      return [form.imagingFindings.trim(), form.impressionText.trim()]
        .filter(Boolean)
        .join('\n');
    case 'PROCEDURE_REPORT':
      return [form.procedureName.trim(), form.conclusionText.trim()]
        .filter(Boolean)
        .join(' · ');
    case 'GENERIC_NARRATIVE':
    default:
      return [form.genericSummary.trim(), form.conclusionText.trim()].filter(Boolean).join('\n');
  }
}

function formatPdfStatus(status?: string) {
  switch (status) {
    case 'PENDING':
      return 'Đang chờ sinh PDF';
    case 'PROCESSING':
      return 'Đang sinh PDF';
    case 'COMPLETED':
      return 'PDF đã sẵn sàng';
    case 'FAILED':
      return 'Sinh PDF lỗi';
    default:
      return 'Chưa có PDF';
  }
}


function formatMinutesLabel(value?: number) {
  if (typeof value !== 'number' || Number.isNaN(value)) return '—';
  if (value < 60) return `${Math.round(value)} phút`;
  const hours = value / 60;
  return hours >= 10 ? `${hours.toFixed(0)} giờ` : `${hours.toFixed(1)} giờ`;
}

function formatAuditAction(action?: string) {
  switch (action) {
    case 'CREATE':
      return 'Tạo kết quả';
    case 'UPDATE':
      return 'Cập nhật kết quả';
    case 'VERIFY':
      return 'Xác thực nội bộ';
    default:
      return action || 'Thao tác';
  }
}

function formatAuditActor(log: AuditLog) {
  return log.actorName || log.actor || 'Hệ thống';
}

function attachmentTitle(attachment: ServiceResultAttachment, index: number) {
  return attachment.label || attachment.fileName || `Tệp đính kèm ${index + 1}`;
}

function isImageAttachment(attachment: ServiceResultAttachment) {
  if (attachment.mimeType?.startsWith('image/')) return true;
  return /\.(png|jpe?g|gif|webp|bmp|svg)(\?.*)?$/i.test(attachment.url);
}

const itemStatusOptions = [
  { value: '__all__', label: 'Tất cả trạng thái hàng đợi' },
  { value: 'WAITING_EXECUTION', label: 'Chờ thực hiện' },
  { value: 'IN_PROGRESS', label: 'Đang thực hiện' },
  { value: 'DONE', label: 'Đã hoàn tất' },
];

const resultStatusOptions = [
  { value: '__all__', label: 'Tất cả trạng thái kết quả' },
  { value: 'COMPLETED', label: 'Đã lưu kết quả' },
  { value: 'VERIFIED', label: 'Đã xác thực nội bộ (nếu có)' },
];

export default function ServiceDeskPage() {
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [departmentCode, setDepartmentCode] = useState('');
  const [itemStatus, setItemStatus] = useState('__all__');
  const [resultStatus, setResultStatus] = useState('__all__');
  const [dialogOpen, setDialogOpen] = useState(false);
  const [selectedItem, setSelectedItem] = useState<ServiceDeskQueueItem | null>(null);
  const [form, setForm] = useState<ResultFormState>(createDefaultForm());
  const [uploadingFiles, setUploadingFiles] = useState(false);
  const debouncedSearch = useDebouncedValue(search.trim(), 400);

  useEffect(() => {
    setPage(0);
  }, [debouncedSearch, departmentCode, itemStatus, resultStatus]);

  const params = useMemo(
    () => ({
      page: String(page),
      size: '12',
      ...(debouncedSearch ? { q: debouncedSearch } : {}),
      ...(departmentCode.trim() ? { departmentCode: departmentCode.trim().toUpperCase() } : {}),
      ...(itemStatus !== '__all__' ? { itemStatus } : {}),
      ...(resultStatus !== '__all__' ? { resultStatus } : {}),
    }),
    [debouncedSearch, departmentCode, itemStatus, page, resultStatus],
  );

  const normalizedDepartmentCode = departmentCode.trim().toUpperCase() || undefined;
  const { data, isLoading, isError, refetch, dataUpdatedAt } = useServiceDeskQueue(params);
  const { data: summary } = useServiceDeskSummary(normalizedDepartmentCode);
  const { data: history = [], isLoading: historyLoading } = useServiceResultHistory(
    selectedItem?.itemId,
  );
  const submitResult = useSubmitServiceResult();

  const items = useMemo(() => data?.items ?? [], [data?.items]);
  const totalItems = data?.meta.totalItems ?? 0;


  const boardColumns = useMemo(
    () => [
      {
        id: 'waiting',
        title: 'Chờ thực hiện',
        description: 'Các mẫu mới vào queue, đang chờ kỹ thuật viên nhận xử lý.',
        accentClassName: 'bg-slate-100 text-slate-700',
        items: items.filter((item) => item.itemStatus === 'WAITING_EXECUTION' && !item.overdue),
      },
      {
        id: 'in-progress',
        title: 'Đang thực hiện',
        description: 'Đang thao tác trên máy, đang ghi kết quả hoặc đang hoàn thiện report.',
        accentClassName: 'bg-sky-100 text-sky-700',
        items: items.filter((item) => item.itemStatus === 'IN_PROGRESS'),
      },
      {
        id: 'ready',
        title: 'Sẵn sàng trả bác sĩ',
        description: 'Đã lưu kết quả và có thể bác sĩ đọc để chốt ca khám.',
        accentClassName: 'bg-emerald-100 text-emerald-700',
        items: items.filter((item) => item.resultStatus === 'COMPLETED' || item.resultStatus === 'VERIFIED'),
      },
      {
        id: 'overdue',
        title: 'Quá SLA',
        description: 'Các mẫu đang trễ hạn, nên ưu tiên xử lý hoặc escalte nội bộ.',
        accentClassName: 'bg-amber-100 text-amber-700',
        items: items.filter((item) => item.overdue),
      },
    ],
    [items],
  );

  const lastUpdated = dataUpdatedAt ? new Date(dataUpdatedAt).toLocaleTimeString('vi-VN') : '—';

  const stats = useMemo(
    () => ({
      waitingCount: summary?.waitingCount ?? 0,
      inProgressCount: summary?.inProgressCount ?? 0,
      completedTodayCount: summary?.completedTodayCount ?? 0,
      overdueCount: summary?.overdueCount ?? 0,
      readyForDoctorCount: summary?.readyForDoctorCount ?? 0,
      pdfPendingCount: summary?.pdfPendingCount ?? 0,
      averageTurnaroundMinutes: summary?.averageTurnaroundMinutes ?? 0,
      turnaroundBreachRate: summary?.turnaroundBreachRate ?? 0,
    }),
    [summary],
  );

  const openDialog = (item: ServiceDeskQueueItem) => {
    setSelectedItem(item);
    setForm(createDefaultForm(item));
    setDialogOpen(true);
  };

  const closeDialog = () => {
    setDialogOpen(false);
    setSelectedItem(null);
    setForm(createDefaultForm());
  };

  const updateLabRow = (index: number, key: keyof LabRow, value: string) => {
    setForm((prev) => ({
      ...prev,
      labRows: prev.labRows.map((row, rowIndex) =>
        rowIndex === index ? { ...row, [key]: value } : row,
      ),
    }));
  };

  const addLabRow = () => {
    setForm((prev) => ({
      ...prev,
      labRows: [...prev.labRows, { ...EMPTY_LAB_ROW }],
    }));
  };

  const removeLabRow = (index: number) => {
    setForm((prev) => ({
      ...prev,
      labRows:
        prev.labRows.length > 1
          ? prev.labRows.filter((_, rowIndex) => rowIndex !== index)
          : [{ ...EMPTY_LAB_ROW }],
    }));
  };

  const handleOpenResultPdf = async (item?: Pick<ServiceDeskQueueItem, 'reportPdfUrl' | 'itemId'> | null) => {
    if (!item?.reportPdfUrl) return;

    try {
      await openProtectedFile(item.reportPdfUrl, `ket-qua-${item.itemId || 'service'}.pdf`);
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'Không thể mở PDF kết quả'));
    }
  };

  const handleUploadFiles = async (files: FileList | null) => {
    if (!files || !selectedItem) return;

    if (!selectedItem.resultId) {
      toast.error('Vui lòng lưu kết quả trước để hệ thống tạo resultId rồi mới tải tệp đính kèm.');
      return;
    }

    const ownerId = selectedItem.resultId;
    setUploadingFiles(true);
    try {
      const nextAttachments: ServiceResultAttachment[] = [];
      for (const file of Array.from(files)) {
        const formData = new FormData();
        formData.append('file', file);
        const { data } = await apiClient.post('/files/attachments', formData, {
          params: {
            ownerType: 'SERVICE_RESULT',
            ownerId,
          },
          headers: {
            'Content-Type': 'multipart/form-data',
          },
        });
        const payload = data?.data || data;
        if (!payload?.url) continue;
        nextAttachments.push({
          url: payload.url,
          mimeType: payload.mimeType,
          fileName: payload.fileName,
        });
      }

      if (nextAttachments.length === 0) {
        toast.error('Không upload được tệp nào.');
        return;
      }

      setForm((prev) => ({
        ...prev,
        attachments: [...prev.attachments, ...nextAttachments],
      }));
      toast.success(`Đã thêm ${nextAttachments.length} tệp đính kèm`);
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'Upload tệp đính kèm thất bại'));
    } finally {
      setUploadingFiles(false);
    }
  };

  const handleSubmit = async () => {
    if (!selectedItem) return;

    const fieldValues = buildFieldValues(form);
    const firstAttachment = form.attachments[0];

    const savedResult = await submitResult.mutateAsync({
      itemId: selectedItem.itemId,
      body: {
        resultTextVn: buildQuickPreview(form) || undefined,
        resultTextEn: form.resultTextEn.trim() || undefined,
        resultDataJson: JSON.stringify(fieldValues),
        fieldValuesJson: JSON.stringify(fieldValues),
        attachmentUrl: firstAttachment?.url,
        attachmentMimeType: firstAttachment?.mimeType,
        attachmentUrlsJson: JSON.stringify(form.attachments),
        conclusionText: form.conclusionText.trim() || undefined,
        impressionText: form.impressionText.trim() || undefined,
        templateCode: form.templateCode,
        templateSchemaJson: form.templateSchemaJson || undefined,
        reportTitle: form.reportTitle.trim() || getServiceLabel(selectedItem),
      },
    });

    setSelectedItem((prev) =>
      prev
        ? {
            ...prev,
            resultId: savedResult?.id ? String(savedResult.id) : prev.resultId,
            resultStatus: savedResult?.status || 'COMPLETED',
            reportPdfStatus: savedResult?.reportPdfStatus || prev.reportPdfStatus,
            reportPdfUrl: savedResult?.reportPdfUrl || prev.reportPdfUrl,
            reportPdfErrorMessage: savedResult?.reportPdfErrorMessage || prev.reportPdfErrorMessage,
            reportPdfGeneratedAt: savedResult?.reportPdfGeneratedAt || prev.reportPdfGeneratedAt,
          }
        : prev,
    );

    closeDialog();
  };

  const renderTemplateEditor = () => {
    switch (form.templateCode) {
      case 'LAB_TABLE':
        return (
          <div className="space-y-4">
            <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
              <div>
                <label className="mb-1.5 block text-sm font-medium">Mẫu bệnh phẩm</label>
                <Input
                  value={form.labSpecimen}
                  onChange={(event) => setForm({ ...form, labSpecimen: event.target.value })}
                  placeholder="Máu toàn phần / Huyết thanh..."
                />
              </div>
              <div>
                <label className="mb-1.5 block text-sm font-medium">Chỉ định lâm sàng</label>
                <Input
                  value={form.labClinicalInfo}
                  onChange={(event) =>
                    setForm({ ...form, labClinicalInfo: event.target.value })
                  }
                  placeholder="Theo dõi viêm / kiểm tra định kỳ..."
                />
              </div>
              <div>
                <label className="mb-1.5 block text-sm font-medium">Thiết bị / máy</label>
                <Input
                  value={form.labDeviceName}
                  onChange={(event) => setForm({ ...form, labDeviceName: event.target.value })}
                  placeholder="Mindray BC-6800"
                />
              </div>
            </div>

            <div className="overflow-x-auto rounded-xl border">
              <table className="min-w-full text-sm">
                <thead className="bg-muted/40 text-left text-muted-foreground">
                  <tr>
                    <th className="px-3 py-2 font-medium">Thông số</th>
                    <th className="px-3 py-2 font-medium">Kết quả</th>
                    <th className="px-3 py-2 font-medium">Đơn vị</th>
                    <th className="px-3 py-2 font-medium">Khoảng tham chiếu</th>
                    <th className="px-3 py-2 font-medium">Cờ</th>
                    <th className="px-3 py-2 font-medium">Ghi chú</th>
                    <th className="w-12 px-3 py-2" />
                  </tr>
                </thead>
                <tbody>
                  {form.labRows.map((row, index) => (
                    <tr key={`${index}-${row.parameter}`} className="border-t align-top">
                      <td className="px-3 py-2">
                        <Input
                          value={row.parameter}
                          onChange={(event) => updateLabRow(index, 'parameter', event.target.value)}
                          placeholder="WBC"
                        />
                      </td>
                      <td className="px-3 py-2">
                        <Input
                          value={row.result}
                          onChange={(event) => updateLabRow(index, 'result', event.target.value)}
                          placeholder="7.2"
                        />
                      </td>
                      <td className="px-3 py-2">
                        <Input
                          value={row.unit}
                          onChange={(event) => updateLabRow(index, 'unit', event.target.value)}
                          placeholder="G/L"
                        />
                      </td>
                      <td className="px-3 py-2">
                        <Input
                          value={row.referenceRange}
                          onChange={(event) =>
                            updateLabRow(index, 'referenceRange', event.target.value)
                          }
                          placeholder="4.0 - 10.0"
                        />
                      </td>
                      <td className="px-3 py-2">
                        <Input
                          value={row.flag}
                          onChange={(event) => updateLabRow(index, 'flag', event.target.value)}
                          placeholder="H / L"
                        />
                      </td>
                      <td className="px-3 py-2">
                        <Input
                          value={row.note}
                          onChange={(event) => updateLabRow(index, 'note', event.target.value)}
                          placeholder="Tăng nhẹ"
                        />
                      </td>
                      <td className="px-3 py-2">
                        <Button
                          type="button"
                          variant="ghost"
                          size="icon"
                          onClick={() => removeLabRow(index)}
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <Button type="button" variant="outline" onClick={addLabRow}>
              <Plus className="mr-2 h-4 w-4" />
              Thêm dòng chỉ số
            </Button>

            <div>
              <label className="mb-1.5 block text-sm font-medium">Kết luận kỹ thuật viên</label>
              <Textarea
                value={form.conclusionText}
                onChange={(event) => setForm({ ...form, conclusionText: event.target.value })}
                rows={3}
                placeholder="Nhận xét chung về bộ xét nghiệm"
              />
            </div>
          </div>
        );
      case 'IMAGING_REPORT':
        return (
          <div className="space-y-4">
            <div>
              <label className="mb-1.5 block text-sm font-medium">Thông tin lâm sàng</label>
              <Textarea
                value={form.imagingClinicalInfo}
                onChange={(event) =>
                  setForm({ ...form, imagingClinicalInfo: event.target.value })
                }
                rows={2}
              />
            </div>
            <div>
              <label className="mb-1.5 block text-sm font-medium">Kỹ thuật thực hiện</label>
              <Textarea
                value={form.imagingTechnique}
                onChange={(event) => setForm({ ...form, imagingTechnique: event.target.value })}
                rows={2}
                placeholder="CT ngực không cản quang..."
              />
            </div>
            <div>
              <label className="mb-1.5 block text-sm font-medium">Findings / Mô tả</label>
              <Textarea
                value={form.imagingFindings}
                onChange={(event) => setForm({ ...form, imagingFindings: event.target.value })}
                rows={6}
                placeholder="Mô tả chi tiết các cấu trúc quan sát được"
              />
            </div>
            <div>
              <label className="mb-1.5 block text-sm font-medium">Impression / Kết luận</label>
              <Textarea
                value={form.impressionText}
                onChange={(event) => setForm({ ...form, impressionText: event.target.value })}
                rows={3}
                placeholder="Không ghi nhận tổn thương khu trú..."
              />
            </div>
            <div>
              <label className="mb-1.5 block text-sm font-medium">Khuyến nghị</label>
              <Textarea
                value={form.imagingRecommendation}
                onChange={(event) =>
                  setForm({ ...form, imagingRecommendation: event.target.value })
                }
                rows={2}
                placeholder="Theo dõi thêm / đối chiếu lâm sàng"
              />
            </div>
          </div>
        );
      case 'PROCEDURE_REPORT':
        return (
          <div className="space-y-4">
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <div>
                <label className="mb-1.5 block text-sm font-medium">Tên thủ thuật</label>
                <Input
                  value={form.procedureName}
                  onChange={(event) => setForm({ ...form, procedureName: event.target.value })}
                />
              </div>
              <div>
                <label className="mb-1.5 block text-sm font-medium">Bệnh phẩm</label>
                <Input
                  value={form.procedureSpecimen}
                  onChange={(event) =>
                    setForm({ ...form, procedureSpecimen: event.target.value })
                  }
                />
              </div>
            </div>
            <div>
              <label className="mb-1.5 block text-sm font-medium">Đại thể</label>
              <Textarea
                value={form.procedureMacroscopy}
                onChange={(event) =>
                  setForm({ ...form, procedureMacroscopy: event.target.value })
                }
                rows={4}
              />
            </div>
            <div>
              <label className="mb-1.5 block text-sm font-medium">Vi thể</label>
              <Textarea
                value={form.procedureMicroscopy}
                onChange={(event) =>
                  setForm({ ...form, procedureMicroscopy: event.target.value })
                }
                rows={4}
              />
            </div>
            <div>
              <label className="mb-1.5 block text-sm font-medium">Kết luận</label>
              <Textarea
                value={form.conclusionText}
                onChange={(event) => setForm({ ...form, conclusionText: event.target.value })}
                rows={3}
              />
            </div>
          </div>
        );
      case 'GENERIC_NARRATIVE':
      default:
        return (
          <div className="space-y-4">
            <div>
              <label className="mb-1.5 block text-sm font-medium">Tóm tắt kết quả</label>
              <Textarea
                value={form.genericSummary}
                onChange={(event) => setForm({ ...form, genericSummary: event.target.value })}
                rows={4}
                placeholder="Tóm tắt ngắn gọn kết quả thực hiện"
              />
            </div>
            <div>
              <label className="mb-1.5 block text-sm font-medium">Kết luận</label>
              <Textarea
                value={form.conclusionText}
                onChange={(event) => setForm({ ...form, conclusionText: event.target.value })}
                rows={3}
              />
            </div>
            <div>
              <label className="mb-1.5 block text-sm font-medium">Khuyến nghị</label>
              <Textarea
                value={form.genericRecommendation}
                onChange={(event) =>
                  setForm({ ...form, genericRecommendation: event.target.value })
                }
                rows={2}
              />
            </div>
          </div>
        );
    }
  };

  return (
    <div className="space-y-4">
      <PageHeader
        title="Kỹ thuật viên - kết quả cận lâm sàng"
        description="Nhập kết quả theo mẫu chuẩn bệnh viện, theo dõi SLA/TAT, lưu audit thao tác, sinh PDF qua RabbitMQ và trả kết quả về bác sĩ để chốt ca khám."
        actions={<LivePulseBadge label={`Realtime · cập nhật ${lastUpdated}`} />}
      />

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-6">
        <StatCard title="Tổng phiếu trong bộ lọc" value={totalItems} icon={FlaskConical} />
        <StatCard title="Chờ thực hiện" value={stats.waitingCount} icon={TestTube2} />
        <StatCard title="Đang thực hiện" value={stats.inProgressCount} icon={Clock3} />
        <StatCard title="Sẵn sàng trả bác sĩ" value={stats.readyForDoctorCount} icon={ShieldCheck} />
        <StatCard title="Quá SLA" value={stats.overdueCount} icon={AlertTriangle} />
        <StatCard
          title="TAT trung bình"
          value={formatMinutesLabel(stats.averageTurnaroundMinutes)}
          icon={Microscope}
          trend={`${stats.turnaroundBreachRate.toFixed(1)}% vượt SLA`}
          trendUp={false}
        />
      </div>

      <Card className="border-border/70 shadow-sm">
        <CardHeader>
          <CardTitle className="text-lg">Bộ lọc hàng đợi</CardTitle>
          <CardDescription>
            Có thể lọc theo khoa thực hiện, trạng thái hàng đợi hoặc trạng thái lưu kết quả để theo dõi backlog và SLA.
          </CardDescription>
        </CardHeader>
        <CardContent className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-4">
          <div className="relative">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              className="pl-9"
              placeholder="Tìm theo mã phiếu, mã hồ sơ, bệnh nhân, bác sĩ..."
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
          </div>
          <Input
            placeholder="Mã khoa thực hiện (VD: LAB, IMG...)"
            value={departmentCode}
            onChange={(event) => setDepartmentCode(event.target.value)}
          />
          <Select value={itemStatus} onValueChange={setItemStatus}>
            <SelectTrigger>
              <SelectValue placeholder="Trạng thái hàng đợi" />
            </SelectTrigger>
            <SelectContent>
              {itemStatusOptions.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Select value={resultStatus} onValueChange={setResultStatus}>
            <SelectTrigger>
              <SelectValue placeholder="Trạng thái kết quả" />
            </SelectTrigger>
            <SelectContent>
              {resultStatusOptions.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </CardContent>
      </Card>
      <QueueBoard
        columns={boardColumns}
        renderCard={(item) => {
          const previewText = item.resultTextVn || item.impressionText || item.conclusionText;
          return (
            <div key={item.itemId} className="rounded-2xl border bg-background p-3">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="font-medium text-foreground">{item.patientName || 'Chưa rõ bệnh nhân'}</p>
                  <p className="mt-1 text-xs text-muted-foreground">{getServiceLabel(item)} · {item.departmentCode || '—'}</p>
                  <p className="mt-1 text-xs text-muted-foreground">{item.serviceOrderCode || '—'} · {item.encounterCode || item.appointmentCode || '—'}</p>
                </div>
                <div className="flex h-10 w-10 items-center justify-center rounded-full bg-primary/10 text-sm font-semibold text-primary">
                  {item.queueNo ?? '-'}
                </div>
              </div>
              <div className="mt-3 flex flex-wrap items-center gap-2 text-xs">
                <StatusBadge status={item.itemStatus || 'WAITING_EXECUTION'} />
                {item.resultStatus ? <StatusBadge status={item.resultStatus} /> : null}
                {item.overdue ? (
                  <span className="rounded-full border border-destructive/30 bg-destructive/10 px-2.5 py-1 text-destructive">Quá SLA</span>
                ) : null}
              </div>
              <p className="mt-3 line-clamp-3 text-sm text-muted-foreground">
                {previewText || 'Chưa nhập kết quả. Mở phiếu để nhập đúng form dịch vụ.'}
              </p>
              <div className="mt-3 flex flex-wrap gap-2">
                <Button size="sm" onClick={() => openDialog(item)}>
                  {item.resultStatus ? 'Cập nhật kết quả' : 'Nhập kết quả'}
                </Button>
                {item.reportPdfUrl ? (
                  <Button size="sm" variant="outline" onClick={() => { void handleOpenResultPdf(item); }}>
                    PDF
                  </Button>
                ) : null}
              </div>
            </div>
          );
        }}
      />

      <div className="grid gap-3">
        {isError ? (
          <ErrorState
            title="Không tải được hàng đợi cận lâm sàng"
            description="Vui lòng thử lại hoặc kiểm tra quyền truy cập khu vực nhập kết quả."
            onRetry={() => void refetch()}
          />
        ) : isLoading ? (
          <LoadingSkeleton variant="list" count={5} />
        ) : items.length > 0 ? (
          items.map((item) => {
            const canEdit = true;
            const previewText = item.resultTextVn || item.impressionText || item.conclusionText;
            const timingText =
              typeof item.turnaroundMinutes === 'number'
                ? `TAT ${formatMinutesLabel(item.turnaroundMinutes)}`
                : `Đã trôi qua ${formatMinutesLabel(item.elapsedMinutes)}`;

            return (
              <Card key={item.itemId} className="border-border/70 shadow-sm">
              <CardContent className="flex flex-col gap-4 p-4 lg:flex-row lg:items-start lg:justify-between">
                <div className="flex items-start gap-4">
                  <div className="flex h-12 w-12 items-center justify-center rounded-full bg-primary/10 text-lg font-bold text-primary">
                    {item.queueNo ?? '-'}
                  </div>
                  <div className="space-y-1.5">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="font-semibold text-foreground">
                        {item.patientName || 'Chưa rõ bệnh nhân'}
                      </p>
                      {item.departmentCode ? (
                        <span className="text-xs text-muted-foreground">· {item.departmentCode}</span>
                      ) : null}
                    </div>
                    <p className="text-sm text-muted-foreground">
                      {getServiceLabel(item)} · {item.doctorName || 'Chưa rõ bác sĩ'}
                    </p>
                    <p className="text-xs text-muted-foreground">
                      {item.serviceOrderCode || '—'} · {item.encounterCode || item.appointmentCode || '—'} · {item.branchName || '—'}
                    </p>
                    <p className="text-xs text-muted-foreground">
                      Xếp hàng: {formatDateTime(item.queuedAt)} · Hoàn tất: {formatDateTime(item.completedAt)}
                    </p>
                    {previewText ? (
                      <p className="line-clamp-3 max-w-3xl text-sm text-foreground">{previewText}</p>
                    ) : (
                      <p className="text-sm text-muted-foreground">
                        Chưa nhập kết quả. Mở phiếu để nhập đúng form dịch vụ.
                      </p>
                    )}
                    <div className="flex flex-wrap items-center gap-3 text-xs text-muted-foreground">
                      <span>Mẫu: {item.templateCode || 'GENERIC_NARRATIVE'}</span>
                      <span>{item.attachments?.length || 0} tệp đính kèm</span>
                      <span>{formatPdfStatus(item.reportPdfStatus)}</span>
                    </div>
                    <div className="flex flex-wrap items-center gap-2 text-xs">
                      <span className="rounded-full bg-muted px-2.5 py-1 text-muted-foreground">
                        Hạn SLA: {formatDateTime(item.dueAt)}
                      </span>
                      <span className="rounded-full bg-muted px-2.5 py-1 text-muted-foreground">
                        {timingText}
                      </span>
                      {typeof item.turnaroundTargetMinutes === 'number' ? (
                        <span className="rounded-full bg-muted px-2.5 py-1 text-muted-foreground">
                          Mục tiêu {formatMinutesLabel(item.turnaroundTargetMinutes)}
                        </span>
                      ) : null}
                      {item.overdue ? (
                        <span className="rounded-full border border-destructive/30 bg-destructive/10 px-2.5 py-1 text-destructive">
                          Quá SLA
                        </span>
                      ) : null}
                    </div>
                    {item.reportPdfUrl ? (
                      <button
                        type="button"
                        onClick={() => {
                          void handleOpenResultPdf(item);
                        }}
                        className="inline-flex text-xs text-primary underline-offset-4 hover:underline"
                      >
                        Mở PDF kết quả
                      </button>
                    ) : null}
                  </div>
                </div>

                <div className="flex flex-col items-start gap-3 lg:items-end">
                  <div className="flex flex-wrap items-center gap-2">
                    <StatusBadge status={item.itemStatus || 'WAITING_EXECUTION'} />
                    {item.resultStatus ? <StatusBadge status={item.resultStatus} /> : null}
                  </div>
                  {item.reportPdfStatus === 'FAILED' && item.reportPdfErrorMessage ? (
                    <div className="max-w-xs rounded-lg border border-destructive/20 bg-destructive/5 px-3 py-2 text-xs text-destructive">
                      {item.reportPdfErrorMessage}
                    </div>
                  ) : null}
                  <div className="flex flex-wrap gap-2">
                    {canEdit ? (
                      <Button onClick={() => openDialog(item)}>
                        {item.resultStatus ? 'Cập nhật kết quả' : 'Nhập kết quả'}
                      </Button>
                    ) : (
                      <Button variant="outline" onClick={() => openDialog(item)}>
                        Xem kết quả
                      </Button>
                    )}
                  </div>
                </div>
              </CardContent>
              </Card>
            );
          })
        ) : (
          <EmptyState
            title="Không có phiếu phù hợp"
            description="Không có phiếu nào trong bộ lọc hiện tại."
            className="min-h-48"
          />
        )}
      </div>

      <AppPagination
        page={page}
        totalPages={Math.max(data?.meta.totalPages ?? 1, 1)}
        onPageChange={setPage}
      />

      {dialogOpen && selectedItem ? (
      <Dialog open={dialogOpen} onOpenChange={(open) => {
        if (!open) closeDialog();
      }}>
        <DialogContent className="max-h-[92vh] max-w-6xl overflow-hidden p-0">
          <DialogHeader className="border-b bg-background px-6 pb-4 pt-6">
            <DialogTitle>
              {selectedItem ? `${getServiceLabel(selectedItem)} - ${selectedItem.patientName || ''}` : 'Phiếu kết quả'}
            </DialogTitle>
          </DialogHeader>

          <div className="grid max-h-[calc(92vh-140px)] grid-cols-1 overflow-hidden lg:grid-cols-[minmax(0,1.25fr)_360px]">
            <div className="overflow-y-auto px-6 py-5">
              <div className="space-y-5">
                <div className="grid grid-cols-1 gap-4 rounded-2xl border bg-muted/15 p-4 md:grid-cols-3">
                  <div>
                    <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Bệnh nhân</p>
                    <p className="mt-1 font-medium text-foreground">{selectedItem?.patientName || '—'}</p>
                  </div>
                  <div>
                    <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Bác sĩ chỉ định</p>
                    <p className="mt-1 font-medium text-foreground">{selectedItem?.doctorName || '—'}</p>
                  </div>
                  <div>
                    <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Mẫu biểu</p>
                    <p className="mt-1 font-medium text-foreground">{form.templateCode}</p>
                  </div>
                </div>

                <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                  <div>
                    <label className="mb-1.5 block text-sm font-medium">Tiêu đề phiếu PDF</label>
                    <Input
                      value={form.reportTitle}
                      onChange={(event) => setForm({ ...form, reportTitle: event.target.value })}
                    />
                  </div>
                  <div>
                    <label className="mb-1.5 block text-sm font-medium">Tóm tắt hiển thị nhanh</label>
                    <Input
                      value={form.resultTextVn}
                      onChange={(event) => setForm({ ...form, resultTextVn: event.target.value })}
                      placeholder="Dùng cho summary ngắn ở danh sách và màn bác sĩ"
                    />
                  </div>
                </div>

                <div className="rounded-2xl border bg-background p-4">
                  <div className="mb-4 flex items-center justify-between gap-3">
                    <div>
                      <h3 className="text-base font-semibold text-foreground">Form nhập kết quả chuẩn hoá</h3>
                      <p className="text-sm text-muted-foreground">
                        Dữ liệu sẽ được lưu có cấu trúc, đẩy RabbitMQ để sinh PDF và trả về màn bác sĩ.
                      </p>
                    </div>
                  </div>
                  {renderTemplateEditor()}
                </div>

                <div className="rounded-2xl border bg-background p-4">
                  <div className="mb-3 flex items-center justify-between gap-3">
                    <div>
                      <h3 className="text-base font-semibold text-foreground">Ảnh / tệp đính kèm</h3>
                      <p className="text-sm text-muted-foreground">
                        Hỗ trợ ảnh siêu âm, phim chụp, PDF, tài liệu nội bộ hoặc file tham chiếu.
                        {!selectedItem?.resultId ? ' Lưu kết quả trước để hệ thống tạo resultId rồi mới cho phép tải tệp.' : ''}
                      </p>
                    </div>
                    <label className={`inline-flex items-center gap-2 rounded-md border px-3 py-2 text-sm font-medium ${selectedItem?.resultId ? 'cursor-pointer hover:bg-muted/40' : 'cursor-not-allowed opacity-60'}`}>
                      {uploadingFiles ? <Loader2 className="h-4 w-4 animate-spin" /> : <UploadCloud className="h-4 w-4" />}
                      Tải tệp lên
                      <input
                        type="file"
                        multiple
                        className="hidden"
                        disabled={!selectedItem?.resultId}
                        onChange={(event) => void handleUploadFiles(event.target.files)}
                      />
                    </label>
                  </div>

                  <div className="grid gap-3 md:grid-cols-2">
                    {form.attachments.length === 0 ? (
                      <div className="rounded-xl border border-dashed px-4 py-6 text-sm text-muted-foreground md:col-span-2">
                        Chưa có tệp đính kèm.
                      </div>
                    ) : (
                      form.attachments.map((attachment, index) => (
                        <div key={`${attachment.url}-${index}`} className="flex items-start gap-3 rounded-xl border p-3">
                          <div className="mt-0.5 text-muted-foreground">
                            {isImageAttachment(attachment) ? (
                              <FileImage className="h-5 w-5" />
                            ) : (
                              <FileText className="h-5 w-5" />
                            )}
                          </div>
                          <div className="min-w-0 flex-1">
                            <button
                              type="button"
                              onClick={() => {
                                void handleOpenAttachment(attachment, index);
                              }}
                              className="block max-w-full truncate text-left text-sm font-medium text-primary underline-offset-4 hover:underline"
                            >
                              {attachmentTitle(attachment, index)}
                            </button>
                            <p className="mt-1 text-xs text-muted-foreground">{attachment.mimeType || 'Tệp đính kèm'}</p>
                          </div>
                          <Button
                            type="button"
                            variant="ghost"
                            size="icon"
                            onClick={() =>
                              setForm((prev) => ({
                                ...prev,
                                attachments: prev.attachments.filter((_, fileIndex) => fileIndex !== index),
                              }))
                            }
                          >
                            <Trash2 className="h-4 w-4" />
                          </Button>
                        </div>
                      ))
                    )}
                  </div>
                </div>
              </div>
            </div>

            <div className="overflow-y-auto border-l bg-muted/10 px-6 py-5">
              <div className="space-y-4">
                <div className="rounded-2xl border bg-background p-4">
                  <h3 className="text-sm font-semibold text-foreground">Trạng thái PDF</h3>
                  <p className="mt-2 text-sm text-muted-foreground">
                    {formatPdfStatus(selectedItem?.reportPdfStatus)}
                  </p>
                  {selectedItem?.reportPdfGeneratedAt ? (
                    <p className="mt-2 text-xs text-muted-foreground">
                      Sinh lúc: {formatDateTime(selectedItem.reportPdfGeneratedAt)}
                    </p>
                  ) : null}
                  {selectedItem?.reportPdfErrorMessage ? (
                    <div className="mt-3 rounded-lg border border-destructive/20 bg-destructive/5 px-3 py-2 text-xs text-destructive">
                      {selectedItem.reportPdfErrorMessage}
                    </div>
                  ) : null}
                  {selectedItem?.reportPdfUrl ? (
                    <button
                      type="button"
                      onClick={() => {
                        void handleOpenResultPdf(selectedItem);
                      }}
                      className="mt-3 inline-flex text-sm text-primary underline-offset-4 hover:underline"
                    >
                      Mở PDF hiện tại
                    </button>
                  ) : null}
                </div>

                <div className="rounded-2xl border bg-background p-4">
                  <h3 className="text-sm font-semibold text-foreground">Luồng nghiệp vụ sau khi lưu</h3>
                  <ol className="mt-3 space-y-2 text-sm text-muted-foreground">
                    <li>1. Kỹ thuật viên lưu kết quả theo đúng template dịch vụ.</li>
                    <li>2. Backend đẩy message sang RabbitMQ để sinh PDF kết quả.</li>
                    <li>3. Websocket cập nhật lại màn kỹ thuật viên và màn bác sĩ.</li>
                    <li>4. Bác sĩ xem kết quả/PDF rồi kết luận và hoàn tất lần khám.</li>
                    <li>5. Public tra cứu kết quả bằng OTP sau khi bác sĩ đã chốt ca.</li>
                  </ol>
                </div>

                <div className="rounded-2xl border bg-background p-4">
                  <h3 className="text-sm font-semibold text-foreground">SLA / thời gian xử lý</h3>
                  <div className="mt-3 space-y-2 text-sm text-muted-foreground">
                    <p>Hạn SLA: {formatDateTime(selectedItem?.dueAt)}</p>
                    <p>Đã trôi qua: {formatMinutesLabel(selectedItem?.elapsedMinutes)}</p>
                    <p>TAT hoàn tất: {formatMinutesLabel(selectedItem?.turnaroundMinutes)}</p>
                    <p>Mục tiêu: {formatMinutesLabel(selectedItem?.turnaroundTargetMinutes)}</p>
                    <p>Trạng thái: {selectedItem?.overdue ? 'Quá SLA' : 'Trong SLA / chưa đủ dữ liệu'}</p>
                  </div>
                </div>

                <div className="rounded-2xl border bg-background p-4">
                  <h3 className="text-sm font-semibold text-foreground">Audit thao tác kết quả</h3>
                  <div className="mt-3 space-y-3 text-sm text-muted-foreground">
                    {historyLoading ? (
                      <p>Đang tải lịch sử thao tác...</p>
                    ) : history.length === 0 ? (
                      <p>Chưa có bản ghi audit cho phiếu này.</p>
                    ) : (
                      history.map((entry) => (
                        <div key={entry.id} className="rounded-xl border px-3 py-3">
                          <div className="flex flex-wrap items-center justify-between gap-2">
                            <p className="font-medium text-foreground">{formatAuditAction(entry.action)}</p>
                            <span className="text-xs">{formatDateTime(entry.createdAt)}</span>
                          </div>
                          <p className="mt-1 text-xs">{formatAuditActor(entry)} · {entry.actorRole || 'Hệ thống'}</p>
                          {entry.description ? <p className="mt-2 text-xs">{entry.description}</p> : null}
                        </div>
                      ))
                    )}
                  </div>
                </div>
              </div>
            </div>
          </div>

          <DialogFooter className="border-t bg-background px-6 py-4">
            <Button variant="outline" onClick={closeDialog}>
              Đóng
            </Button>
            <Button onClick={handleSubmit} disabled={submitResult.isPending || uploadingFiles}>
              {submitResult.isPending ? 'Đang lưu...' : 'Lưu kết quả & sinh PDF'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
      ) : null}
    </div>
  );
}
