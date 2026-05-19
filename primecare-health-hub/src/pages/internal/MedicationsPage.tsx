import { useMemo, useState } from 'react';
import {
  BadgeCheck,
  CircleOff,
  PauseCircle,
  Pill,
  Plus,
  RotateCcw,
  SquarePen,
} from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { DataTable, type Column } from '@/components/DataTable';
import { PageHeader } from '@/components/PageHeader';
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
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import {
  useAdminMedications,
  useSaveMedication,
  useUpdateMedicationStatus,
} from '@/hooks/use-admin-data';
import type { Medication } from '@/types/api';

export default function MedicationsPage() {
  const { t } = useTranslation();
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('__all__');
  const [page, setPage] = useState(1);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editItem, setEditItem] = useState<Medication | null>(null);
  const [statusTarget, setStatusTarget] = useState<Medication | null>(null);

  const [form, setForm] = useState({
    code: '',
    name: '',
    genericName: '',
    strength: '',
    dosageForm: '',
    unit: '',
    manufacturer: '',
    indicationNote: '',
    contraindicationNote: '',
  });

  const { data, isLoading } = useAdminMedications({
    page: String(page - 1),
    size: '20',
    ...(search.trim() ? { q: search.trim() } : {}),
    ...(statusFilter !== '__all__' ? { status: statusFilter } : {}),
  });

  const saveMutation = useSaveMedication();
  const updateStatusMutation = useUpdateMedicationStatus();

  const rows = useMemo(() => data?.items ?? [], [data?.items]);
  const activeCount = useMemo(
    () => rows.filter((item) => item.status === 'ACTIVE').length,
    [rows],
  );
  const inactiveCount = useMemo(
    () => rows.filter((item) => item.status === 'INACTIVE').length,
    [rows],
  );

  const openCreate = () => {
    setEditItem(null);
    setForm({
      code: '',
      name: '',
      genericName: '',
      strength: '',
      dosageForm: '',
      unit: '',
      manufacturer: '',
      indicationNote: '',
      contraindicationNote: '',
    });
    setDialogOpen(true);
  };

  const openEdit = (m: Medication) => {
    setEditItem(m);
    setForm({
      code: m.code ?? '',
      name: m.name,
      genericName: m.genericName || '',
      strength: m.strength || '',
      dosageForm: m.dosageForm,
      unit: m.unit,
      manufacturer: m.manufacturer || '',
      indicationNote: m.indicationNote || '',
      contraindicationNote: m.contraindicationNote || '',
    });
    setDialogOpen(true);
  };

  const submit = async () => {
    const body = editItem
      ? {
          name: form.name,
          genericName: form.genericName,
          strength: form.strength,
          dosageForm: form.dosageForm,
          unit: form.unit,
          manufacturer: form.manufacturer,
          indicationNote: form.indicationNote,
          contraindicationNote: form.contraindicationNote,
        }
      : {
          code: form.code,
          name: form.name,
          genericName: form.genericName,
          strength: form.strength,
          dosageForm: form.dosageForm,
          unit: form.unit,
          manufacturer: form.manufacturer,
          indicationNote: form.indicationNote,
          contraindicationNote: form.contraindicationNote,
        };

    await saveMutation.mutateAsync({
      id: editItem?.id,
      body,
    });
    setDialogOpen(false);
  };

  const confirmToggleStatus = async () => {
    if (!statusTarget) return;

    await updateStatusMutation.mutateAsync({
      id: statusTarget.id,
      status: statusTarget.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE',
    });

    setStatusTarget(null);
  };

  const columns: Column<Medication>[] = [
    {
      key: 'name',
      header: t('common.name'),
      cell: (r) => (
        <div>
          <p className="font-medium text-foreground">{r.name}</p>
          <p className="text-xs text-muted-foreground">{r.code || '—'}</p>
        </div>
      ),
    },
    {
      key: 'genericName',
      header: 'Hoạt chất',
      cell: (r) => <span className="text-muted-foreground">{r.genericName || '—'}</span>,
    },
    {
      key: 'dosageForm',
      header: 'Dạng bào chế',
      cell: (r) => <span>{r.dosageForm || '—'}</span>,
    },
    {
      key: 'unit',
      header: 'Đơn vị',
      cell: (r) => <span>{r.unit || '—'}</span>,
    },
    {
      key: 'status',
      header: t('common.status'),
      cell: (r) => <StatusBadge status={r.status || 'INACTIVE'} />,
    },
    {
      key: 'actions',
      header: 'Thao tác',
      cell: (r) => (
        <TooltipProvider>
          <div className="ml-auto flex items-center gap-2">
            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  variant="outline"
                  size="icon"
                  onClick={() => openEdit(r)}
                  className="h-9 w-9 rounded-xl border-border/70 bg-background hover:bg-muted"
                >
                  <SquarePen className="h-4 w-4" />
                </Button>
              </TooltipTrigger>
              <TooltipContent>Chỉnh sửa thuốc</TooltipContent>
            </Tooltip>

            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  variant="outline"
                  size="icon"
                  onClick={() => setStatusTarget(r)}
                  className={`h-9 w-9 rounded-xl border-border/70 bg-background hover:bg-muted ${
                    r.status === 'ACTIVE'
                      ? 'text-warning hover:text-warning'
                      : 'text-success hover:text-success'
                  }`}
                >
                  {r.status === 'ACTIVE' ? (
                    <PauseCircle className="h-4 w-4" />
                  ) : (
                    <RotateCcw className="h-4 w-4" />
                  )}
                </Button>
              </TooltipTrigger>
              <TooltipContent>
                {r.status === 'ACTIVE' ? 'Ngưng hoạt động' : 'Kích hoạt lại'}
              </TooltipContent>
            </Tooltip>
          </div>
        </TooltipProvider>
      ),
    },
  ];

  return (
    <div className="space-y-4">
      <PageHeader
        title={t('modules.medications.title')}
        description={t('modules.medications.desc')}
        actions={
          <Button onClick={openCreate}>
            <Plus className="mr-2 h-4 w-4" />
            {t('modules.medications.create')}
          </Button>
        }
      />

      <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
        <StatCard title="Thuốc hiển thị" value={rows.length} icon={Pill} />
        <StatCard title="Đang hoạt động" value={activeCount} icon={BadgeCheck} />
        <StatCard title="Tạm ngưng" value={inactiveCount} icon={CircleOff} />
      </div>

      <Card className="border-border/70 shadow-sm">
        <CardContent className="grid grid-cols-1 gap-3 pt-6 sm:grid-cols-2">
          <Input
            placeholder="Tìm theo tên thuốc / mã thuốc"
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
      />

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="max-w-4xl">
          <DialogHeader>
            <DialogTitle>
              {editItem ? 'Chỉnh sửa thuốc' : 'Thêm thuốc mới'}
            </DialogTitle>
          </DialogHeader>

          <div className="space-y-5">
            {!editItem && (
              <div>
                <label className="mb-1.5 block text-sm font-medium">Mã thuốc *</label>
                <Input
                  value={form.code}
                  onChange={(e) => setForm({ ...form, code: e.target.value })}
                />
              </div>
            )}

            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <div>
                <label className="mb-1.5 block text-sm font-medium">Tên thuốc *</label>
                <Input
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                />
              </div>
              <div>
                <label className="mb-1.5 block text-sm font-medium">Hoạt chất</label>
                <Input
                  value={form.genericName}
                  onChange={(e) => setForm({ ...form, genericName: e.target.value })}
                />
              </div>
            </div>

            <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
              <div>
                <label className="mb-1.5 block text-sm font-medium">Hàm lượng / nồng độ</label>
                <Input
                  value={form.strength}
                  onChange={(e) => setForm({ ...form, strength: e.target.value })}
                />
              </div>
              <div>
                <label className="mb-1.5 block text-sm font-medium">Dạng bào chế</label>
                <Input
                  value={form.dosageForm}
                  onChange={(e) => setForm({ ...form, dosageForm: e.target.value })}
                />
              </div>
              <div>
                <label className="mb-1.5 block text-sm font-medium">Đơn vị tính</label>
                <Input
                  value={form.unit}
                  onChange={(e) => setForm({ ...form, unit: e.target.value })}
                />
              </div>
            </div>

            <div>
              <label className="mb-1.5 block text-sm font-medium">Nhà sản xuất</label>
              <Input
                value={form.manufacturer}
                onChange={(e) => setForm({ ...form, manufacturer: e.target.value })}
              />
            </div>

            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <div>
                <label className="mb-1.5 block text-sm font-medium">Chỉ định</label>
                <Textarea
                  value={form.indicationNote}
                  onChange={(e) => setForm({ ...form, indicationNote: e.target.value })}
                  rows={4}
                />
              </div>
              <div>
                <label className="mb-1.5 block text-sm font-medium">Chống chỉ định</label>
                <Textarea
                  value={form.contraindicationNote}
                  onChange={(e) =>
                    setForm({ ...form, contraindicationNote: e.target.value })
                  }
                  rows={4}
                />
              </div>
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button onClick={submit} disabled={saveMutation.isPending}>
              {t('common.save')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={!!statusTarget} onOpenChange={(open) => !open && setStatusTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {statusTarget?.status === 'ACTIVE'
                ? 'Ngưng hoạt động thuốc'
                : 'Kích hoạt lại thuốc'}
            </DialogTitle>
          </DialogHeader>

          <div className="text-sm text-muted-foreground">
            {statusTarget?.status === 'ACTIVE'
              ? 'Thuốc sẽ không còn xuất hiện trong danh sách đang hoạt động cho đến khi được kích hoạt lại.'
              : 'Thuốc sẽ được khôi phục trạng thái hoạt động và hiển thị lại cho người dùng liên quan.'}
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setStatusTarget(null)}>
              Hủy
            </Button>
            <Button onClick={confirmToggleStatus} disabled={updateStatusMutation.isPending}>
              Xác nhận
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
