import { useMemo, useState } from 'react';
import { BadgeCheck, CircleOff, Pencil, Plus, Stethoscope, ToggleLeft, ToggleRight, Wallet } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { PageHeader } from '@/components/PageHeader';
import { DataTable, type Column } from '@/components/DataTable';
import { StatCard } from '@/components/StatCard';
import { StatusBadge } from '@/components/StatusBadge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
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
import { useAdminMedicalServices, useSaveMedicalService, useUpdateMedicalServiceStatus } from '@/hooks/use-admin-data';
import type { MedicalService, ServiceResultTemplateCode } from '@/types/api';

const SERVICE_TYPE_OPTIONS = [
  { value: 'LAB', label: 'Xét nghiệm' },
  { value: 'IMAGING', label: 'Chẩn đoán hình ảnh' },
  { value: 'PROCEDURE', label: 'Thủ thuật / giải phẫu bệnh' },
  { value: 'FUNCTIONAL_TEST', label: 'Thăm dò chức năng' },
  { value: 'OTHER', label: 'Khác' },
] as const;

const TEMPLATE_OPTIONS: Array<{ value: ServiceResultTemplateCode; label: string; hint: string }> = [
  {
    value: 'LAB_TABLE',
    label: 'Bảng chỉ số xét nghiệm',
    hint: 'Dùng cho kết quả có chỉ số, đơn vị, khoảng tham chiếu, cờ bất thường.',
  },
  {
    value: 'IMAGING_REPORT',
    label: 'Báo cáo chẩn đoán hình ảnh',
    hint: 'Tách rõ thông tin lâm sàng, kỹ thuật, findings, impression và khuyến nghị.',
  },
  {
    value: 'PROCEDURE_REPORT',
    label: 'Biên bản thủ thuật / GPB',
    hint: 'Phù hợp nội soi, thủ thuật, mô tả đại thể/vi thể hoặc bệnh phẩm.',
  },
  {
    value: 'GENERIC_NARRATIVE',
    label: 'Tường thuật chung',
    hint: 'Dùng cho dịch vụ chỉ cần mô tả, kết luận và khuyến nghị cơ bản.',
  },
];

const DEFAULT_SCHEMA_BY_TEMPLATE: Record<ServiceResultTemplateCode, string> = {
  LAB_TABLE: JSON.stringify(
    {
      template: 'LAB_TABLE',
      rowsKey: 'rows',
      meta: [
        { key: 'specimen', label: 'Mẫu bệnh phẩm' },
        { key: 'clinicalInfo', label: 'Chỉ định lâm sàng' },
        { key: 'deviceName', label: 'Thiết bị / máy' },
      ],
      columns: [
        { key: 'parameter', label: 'Thông số' },
        { key: 'result', label: 'Kết quả' },
        { key: 'unit', label: 'Đơn vị' },
        { key: 'referenceRange', label: 'Khoảng tham chiếu' },
        { key: 'flag', label: 'Đánh dấu' },
        { key: 'note', label: 'Ghi chú' },
      ],
    },
    null,
    2,
  ),
  IMAGING_REPORT: JSON.stringify(
    {
      template: 'IMAGING_REPORT',
      sections: [
        { key: 'clinicalInfo', label: 'Thông tin lâm sàng' },
        { key: 'technique', label: 'Kỹ thuật thực hiện' },
        { key: 'findings', label: 'Mô tả / Findings' },
        { key: 'impression', label: 'Kết luận / Impression' },
        { key: 'recommendation', label: 'Khuyến nghị' },
      ],
    },
    null,
    2,
  ),
  PROCEDURE_REPORT: JSON.stringify(
    {
      template: 'PROCEDURE_REPORT',
      sections: [
        { key: 'procedureName', label: 'Thủ thuật' },
        { key: 'specimen', label: 'Bệnh phẩm' },
        { key: 'macroscopy', label: 'Đại thể' },
        { key: 'microscopy', label: 'Vi thể' },
        { key: 'conclusion', label: 'Kết luận' },
      ],
    },
    null,
    2,
  ),
  GENERIC_NARRATIVE: JSON.stringify(
    {
      template: 'GENERIC_NARRATIVE',
      sections: [
        { key: 'summary', label: 'Tóm tắt kết quả' },
        { key: 'conclusion', label: 'Kết luận' },
        { key: 'recommendation', label: 'Khuyến nghị' },
      ],
    },
    null,
    2,
  ),
};

type MedicalServiceFormState = {
  code: string;
  nameVn: string;
  nameEn: string;
  descriptionVn: string;
  descriptionEn: string;
  serviceType: string;
  departmentCode: string;
  basePrice: string;
  publicVisible: string;
  displayOrder: string;
  thumbnailUrl: string;
  defaultTurnaroundMinutes: string;
  requiresFileResult: string;
  requiresNumericResult: string;
  resultTemplateCode: ServiceResultTemplateCode;
  resultTemplateSchemaJson: string;
  resultReportTitle: string;
  status: string;
};

const createDefaultForm = (): MedicalServiceFormState => ({
  code: '',
  nameVn: '',
  nameEn: '',
  descriptionVn: '',
  descriptionEn: '',
  serviceType: 'LAB',
  departmentCode: '',
  basePrice: '0',
  publicVisible: 'true',
  displayOrder: '0',
  thumbnailUrl: '',
  defaultTurnaroundMinutes: '0',
  requiresFileResult: 'false',
  requiresNumericResult: 'true',
  resultTemplateCode: 'LAB_TABLE',
  resultTemplateSchemaJson: DEFAULT_SCHEMA_BY_TEMPLATE.LAB_TABLE,
  resultReportTitle: '',
  status: 'ACTIVE',
});

function prettifySchema(value?: string, template?: ServiceResultTemplateCode) {
  const fallback = DEFAULT_SCHEMA_BY_TEMPLATE[template ?? 'GENERIC_NARRATIVE'];
  if (!value?.trim()) return fallback;
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

export default function MedicalServicesAdminPage() {
  const { t } = useTranslation();
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('__all__');
  const [page, setPage] = useState(1);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editItem, setEditItem] = useState<MedicalService | null>(null);
  const [form, setForm] = useState<MedicalServiceFormState>(createDefaultForm);

  const { data, isLoading } = useAdminMedicalServices({
    page: String(page - 1),
    size: '20',
    ...(search.trim() ? { q: search.trim() } : {}),
    ...(statusFilter !== '__all__' ? { status: statusFilter } : {}),
  });
  const saveMutation = useSaveMedicalService();
  const updateStatusMutation = useUpdateMedicalServiceStatus();

  const rows = useMemo(() => data?.items ?? [], [data?.items]);
  const activeCount = useMemo(
    () => rows.filter((item) => item.status === 'ACTIVE').length,
    [rows],
  );
  const inactiveCount = useMemo(
    () => rows.filter((item) => item.status === 'INACTIVE').length,
    [rows],
  );
  const visibleRevenue = useMemo(
    () => rows.reduce((total, item) => total + (item.price ?? 0), 0),
    [rows],
  );

  const selectedTemplateMeta = TEMPLATE_OPTIONS.find(
    (option) => option.value === form.resultTemplateCode,
  );

  const openCreate = () => {
    setEditItem(null);
    setForm(createDefaultForm());
    setDialogOpen(true);
  };

  const openEdit = (service: MedicalService) => {
    const nextTemplate = service.resultTemplateCode ?? 'GENERIC_NARRATIVE';
    setEditItem(service);
    setForm({
      code: service.code ?? '',
      nameVn: service.nameVn ?? service.name ?? '',
      nameEn: service.nameEn ?? '',
      descriptionVn: service.descriptionVn ?? service.description ?? '',
      descriptionEn: service.descriptionEn ?? '',
      serviceType: service.serviceType || service.groupName || 'OTHER',
      departmentCode: service.departmentCode ?? '',
      basePrice: String(service.price ?? 0),
      publicVisible: service.publicVisible === false ? 'false' : 'true',
      displayOrder: String(service.displayOrder ?? 0),
      thumbnailUrl: service.thumbnailUrl ?? '',
      defaultTurnaroundMinutes: String(service.defaultTurnaroundMinutes ?? 0),
      requiresFileResult: service.requiresFileResult ? 'true' : 'false',
      requiresNumericResult: service.requiresNumericResult ? 'true' : 'false',
      resultTemplateCode: nextTemplate,
      resultTemplateSchemaJson: prettifySchema(service.resultTemplateSchemaJson, nextTemplate),
      resultReportTitle: service.resultReportTitle ?? service.nameVn ?? service.name ?? '',
      status: service.status ?? 'ACTIVE',
    });
    setDialogOpen(true);
  };

  const updateTemplate = (value: ServiceResultTemplateCode) => {
    setForm((prev) => {
      const next = {
        ...prev,
        resultTemplateCode: value,
        resultTemplateSchemaJson:
          prev.resultTemplateSchemaJson === DEFAULT_SCHEMA_BY_TEMPLATE[prev.resultTemplateCode] ||
          !prev.resultTemplateSchemaJson.trim()
            ? DEFAULT_SCHEMA_BY_TEMPLATE[value]
            : prev.resultTemplateSchemaJson,
      };

      if (value === 'LAB_TABLE') {
        next.requiresNumericResult = 'true';
      }
      if (value === 'IMAGING_REPORT' || value === 'PROCEDURE_REPORT') {
        next.requiresFileResult = 'true';
      }
      return next;
    });
  };

  const submit = async () => {
    const body = {
      ...(editItem ? {} : { code: form.code.trim() }),
      nameVn: form.nameVn.trim(),
      nameEn: form.nameEn.trim() || undefined,
      descriptionVn: form.descriptionVn.trim() || undefined,
      descriptionEn: form.descriptionEn.trim() || undefined,
      serviceType: form.serviceType,
      departmentCode: form.departmentCode.trim() || undefined,
      basePrice: Number(form.basePrice || '0'),
      publicVisible: form.publicVisible === 'true',
      displayOrder: Number(form.displayOrder || '0'),
      thumbnailUrl: form.thumbnailUrl.trim() || undefined,
      defaultTurnaroundMinutes: Number(form.defaultTurnaroundMinutes || '0'),
      requiresFileResult: form.requiresFileResult === 'true',
      requiresNumericResult: form.requiresNumericResult === 'true',
      resultTemplateCode: form.resultTemplateCode,
      resultTemplateSchemaJson: prettifySchema(form.resultTemplateSchemaJson, form.resultTemplateCode),
      resultReportTitle: form.resultReportTitle.trim() || form.nameVn.trim() || undefined,
      ...(editItem ? { status: form.status } : {}),
    };

    await saveMutation.mutateAsync({
      id: editItem?.id,
      body,
    });
    setDialogOpen(false);
  };

  const columns: Column<MedicalService>[] = [
    {
      key: 'name',
      header: 'Dịch vụ',
      cell: (r) => (
        <div>
          <p className="font-medium text-foreground">{r.name}</p>
          <p className="text-xs text-muted-foreground">{r.code || '—'}</p>
        </div>
      ),
    },
    {
      key: 'groupName',
      header: 'Loại dịch vụ',
      cell: (r) => (
        <span>
          {SERVICE_TYPE_OPTIONS.find((option) => option.value === (r.serviceType || r.groupName))
            ?.label || r.serviceType || r.groupName || '—'}
        </span>
      ),
    },
    {
      key: 'resultTemplateCode',
      header: 'Mẫu kết quả',
      cell: (r) => (
        <span>
          {TEMPLATE_OPTIONS.find((option) => option.value === r.resultTemplateCode)?.label ||
            r.resultTemplateCode ||
            '—'}
        </span>
      ),
    },
    {
      key: 'departmentCode',
      header: 'Khoa/phòng',
      cell: (r) => <span>{r.departmentCode || '—'}</span>,
    },
    {
      key: 'price',
      header: 'Giá cơ bản',
      cell: (r) => <span>{(r.price ?? 0).toLocaleString('vi-VN')}</span>,
    },
    {
      key: 'status',
      header: t('common.status'),
      cell: (r) => <StatusBadge status={r.status || 'INACTIVE'} />,
    },
  ];

  return (
    <div className="space-y-4">
      <PageHeader
        title={t('modules.medicalServices.title')}
        description="Quản lý cấu hình dịch vụ theo đúng loại nghiệp vụ và mẫu kết quả/PDF trả bác sĩ."
        actions={
          <Button onClick={openCreate}>
            <Plus className="mr-2 h-4 w-4" />
            {t('modules.medicalServices.create')}
          </Button>
        }
      />

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
        <StatCard title="Dịch vụ hiển thị" value={rows.length} icon={Stethoscope} />
        <StatCard title="Đang hoạt động" value={activeCount} icon={BadgeCheck} />
        <StatCard title="Tạm ngưng" value={inactiveCount} icon={CircleOff} />
        <StatCard
          title="Tổng giá trị hiển thị"
          value={visibleRevenue.toLocaleString('vi-VN')}
          icon={Wallet}
        />
      </div>

      <Card className="border-border/70 shadow-sm">
        <CardContent className="grid grid-cols-1 gap-3 pt-6 sm:grid-cols-2">
          <Input
            placeholder="Tìm theo tên dịch vụ / mã dịch vụ"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          <Select value={statusFilter} onValueChange={setStatusFilter}>
            <SelectTrigger>
              <SelectValue placeholder="Lọc trạng thái" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="__all__">Tất cả trạng thái</SelectItem>
              <SelectItem value="ACTIVE">Đang hoạt động</SelectItem>
              <SelectItem value="INACTIVE">Ngưng hoạt động</SelectItem>
            </SelectContent>
          </Select>
        </CardContent>
      </Card>

      <DataTable
        columns={columns}
        data={rows}
        page={page}
        totalPages={Math.max(data?.meta.totalPages ?? 1, 1)}
        onPageChange={setPage}
        emptyMessage={isLoading ? 'Đang tải...' : 'Không có dữ liệu'}
        keyExtractor={(r) => r.id}
        actions={(row) => (
          <div className="flex items-center gap-1">
            <Button variant="ghost" size="sm" onClick={() => openEdit(row as MedicalService)}>
              <Pencil className="h-3.5 w-3.5" />
            </Button>
            <Button
              variant="ghost"
              size="sm"
              title={(row as MedicalService).status === 'ACTIVE' ? 'Ngưng hoạt động' : 'Kích hoạt lại'}
              disabled={updateStatusMutation.isPending}
              onClick={() => updateStatusMutation.mutate({ id: (row as MedicalService).id, status: (row as MedicalService).status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE' })}
            >
              {(row as MedicalService).status === 'ACTIVE' ? <ToggleRight className="h-3.5 w-3.5" /> : <ToggleLeft className="h-3.5 w-3.5" />}
            </Button>
          </div>
        )}
      />

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="flex h-[90vh] max-w-5xl flex-col overflow-hidden p-0">
          <DialogHeader className="shrink-0 border-b bg-background px-6 pb-4 pt-6">
            <DialogTitle>
              {editItem ? 'Chỉnh sửa dịch vụ y tế' : 'Thêm dịch vụ y tế'}
            </DialogTitle>
          </DialogHeader>

          <div className="flex-1 overflow-y-auto px-6 py-5">
            <div className="space-y-5">
              {!editItem && (
                <div>
                  <label className="mb-1.5 block text-sm font-medium">Mã dịch vụ *</label>
                  <Input
                    value={form.code}
                    onChange={(e) => setForm({ ...form, code: e.target.value })}
                  />
                </div>
              )}

              <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                <div>
                  <label className="mb-1.5 block text-sm font-medium">Tên dịch vụ (tiếng Việt) *</label>
                  <Input
                    value={form.nameVn}
                    onChange={(e) => setForm({ ...form, nameVn: e.target.value })}
                  />
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-medium">Tên dịch vụ (tiếng Anh)</label>
                  <Input
                    value={form.nameEn}
                    onChange={(e) => setForm({ ...form, nameEn: e.target.value })}
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                <div>
                  <label className="mb-1.5 block text-sm font-medium">Mô tả tiếng Việt</label>
                  <Textarea
                    value={form.descriptionVn}
                    onChange={(e) => setForm({ ...form, descriptionVn: e.target.value })}
                    rows={4}
                  />
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-medium">Mô tả tiếng Anh</label>
                  <Textarea
                    value={form.descriptionEn}
                    onChange={(e) => setForm({ ...form, descriptionEn: e.target.value })}
                    rows={4}
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
                <div>
                  <label className="mb-1.5 block text-sm font-medium">Loại dịch vụ</label>
                  <Select
                    value={form.serviceType}
                    onValueChange={(value) => setForm({ ...form, serviceType: value })}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder="Chọn loại dịch vụ" />
                    </SelectTrigger>
                    <SelectContent>
                      {SERVICE_TYPE_OPTIONS.map((option) => (
                        <SelectItem key={option.value} value={option.value}>
                          {option.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-medium">Mã khoa/phòng</label>
                  <Input
                    value={form.departmentCode}
                    onChange={(e) => setForm({ ...form, departmentCode: e.target.value })}
                    placeholder="LAB, IMG, ENDO..."
                  />
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-medium">Tiêu đề PDF / phiếu trả kết quả</label>
                  <Input
                    value={form.resultReportTitle}
                    onChange={(e) => setForm({ ...form, resultReportTitle: e.target.value })}
                    placeholder="Phiếu kết quả huyết học"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
                <div>
                  <label className="mb-1.5 block text-sm font-medium">Giá cơ bản</label>
                  <Input
                    type="number"
                    value={form.basePrice}
                    onChange={(e) => setForm({ ...form, basePrice: e.target.value })}
                  />
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-medium">Thứ tự hiển thị</label>
                  <Input
                    type="number"
                    value={form.displayOrder}
                    onChange={(e) => setForm({ ...form, displayOrder: e.target.value })}
                  />
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-medium">Thời gian trả kết quả (phút)</label>
                  <Input
                    type="number"
                    value={form.defaultTurnaroundMinutes}
                    onChange={(e) => setForm({ ...form, defaultTurnaroundMinutes: e.target.value })}
                  />
                </div>
              </div>

              <div>
                <label className="mb-1.5 block text-sm font-medium">URL ảnh đại diện</label>
                <Input
                  value={form.thumbnailUrl}
                  onChange={(e) => setForm({ ...form, thumbnailUrl: e.target.value })}
                />
              </div>

              <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                <div className="space-y-2 rounded-2xl border bg-muted/15 p-4">
                  <div>
                    <label className="mb-1.5 block text-sm font-medium">Mẫu kết quả / PDF</label>
                    <Select value={form.resultTemplateCode} onValueChange={updateTemplate}>
                      <SelectTrigger>
                        <SelectValue placeholder="Chọn mẫu kết quả" />
                      </SelectTrigger>
                      <SelectContent>
                        {TEMPLATE_OPTIONS.map((option) => (
                          <SelectItem key={option.value} value={option.value}>
                            {option.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                  <p className="text-xs text-muted-foreground">{selectedTemplateMeta?.hint}</p>
                </div>

                <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
                  <div>
                    <label className="mb-1.5 block text-sm font-medium">Hiển thị công khai</label>
                    <Select
                      value={form.publicVisible}
                      onValueChange={(value) => setForm({ ...form, publicVisible: value })}
                    >
                      <SelectTrigger>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="true">Có</SelectItem>
                        <SelectItem value="false">Không</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>

                  <div>
                    <label className="mb-1.5 block text-sm font-medium">Yêu cầu file kết quả</label>
                    <Select
                      value={form.requiresFileResult}
                      onValueChange={(value) => setForm({ ...form, requiresFileResult: value })}
                    >
                      <SelectTrigger>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="true">Có</SelectItem>
                        <SelectItem value="false">Không</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>

                  <div>
                    <label className="mb-1.5 block text-sm font-medium">Yêu cầu kết quả số</label>
                    <Select
                      value={form.requiresNumericResult}
                      onValueChange={(value) => setForm({ ...form, requiresNumericResult: value })}
                    >
                      <SelectTrigger>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="true">Có</SelectItem>
                        <SelectItem value="false">Không</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                </div>
              </div>

              <div>
                <div className="mb-1.5 flex items-center justify-between gap-3">
                  <label className="block text-sm font-medium">Schema field của mẫu kết quả</label>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() =>
                      setForm((prev) => ({
                        ...prev,
                        resultTemplateSchemaJson:
                          DEFAULT_SCHEMA_BY_TEMPLATE[prev.resultTemplateCode],
                      }))
                    }
                  >
                    Khôi phục preset
                  </Button>
                </div>
                <Textarea
                  value={form.resultTemplateSchemaJson}
                  onChange={(e) =>
                    setForm({ ...form, resultTemplateSchemaJson: e.target.value })
                  }
                  rows={16}
                  className="font-mono text-xs"
                />
                <p className="mt-2 text-xs text-muted-foreground">
                  Đây là cấu hình dùng để dựng form nhập kết quả và render PDF theo từng dịch vụ. Với đồ án thực tế, đây là nơi bạn khóa chuẩn field theo chuyên khoa thay vì cho kỹ thuật viên nhập tự do.
                </p>
              </div>

              {editItem && (
                <div>
                  <label className="mb-1.5 block text-sm font-medium">Trạng thái</label>
                  <Select value={form.status} onValueChange={(value) => setForm({ ...form, status: value })}>
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="ACTIVE">Đang hoạt động</SelectItem>
                      <SelectItem value="INACTIVE">Ngưng hoạt động</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              )}
            </div>
          </div>

          <DialogFooter className="shrink-0 border-t bg-background px-6 py-4">
            <Button variant="outline" onClick={() => setDialogOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button onClick={submit} disabled={saveMutation.isPending}>
              {t('common.save')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
