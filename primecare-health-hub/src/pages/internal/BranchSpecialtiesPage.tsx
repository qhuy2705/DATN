import { useMemo, useState } from 'react';
import { AlertTriangle, Plus, SquarePen } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { DataTable, type Column } from '@/components/DataTable';
import { FilterBar } from '@/components/FilterBar';
import { PageHeader } from '@/components/PageHeader';
import { StatusBadge } from '@/components/StatusBadge';
import { Button } from '@/components/ui/button';
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
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import {
  useAdminBranchSpecialties,
  useSaveBranchSpecialty,
} from '@/hooks/use-admin-data';
import { useBranches, useSpecialties } from '@/hooks/use-public-data';
import type { BranchSpecialty } from '@/types/api';

const inactiveReasonLabel = (reason?: string | null) => {
  switch (reason) {
    case 'BRANCH_INACTIVE':
      return 'Chi nhánh tạm ngưng hoạt động';
    case 'SPECIALTY_INACTIVE':
      return 'Chuyên khoa tạm ngưng hoạt động';
    case 'SELF_INACTIVE':
      return 'Cấu hình chuyên khoa đang ngưng hoạt động';
    default:
      return '';
  }
};

export default function BranchSpecialtiesPage() {
  const { t } = useTranslation();
  const [branchFilter, setBranchFilter] = useState('__all__');
  const [page, setPage] = useState(1);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<BranchSpecialty | null>(null);
  const [form, setForm] = useState({
    branchId: '',
    specialtyId: '',
    status: 'ACTIVE',
    displayOrder: 0,
    consultationFee: 0,
    slotMinutesOverride: 15,
    note: '',
  });

  const { data: branches = [] } = useBranches();
  const { data: specialties = [] } = useSpecialties();
  const { data, isLoading } = useAdminBranchSpecialties(
    branchFilter !== '__all__'
      ? {
          branchId: branchFilter,
          page: String(page - 1),
          size: '20',
        }
      : undefined,
  );
  const saveMutation = useSaveBranchSpecialty();

  const rows = useMemo(() => data?.items ?? [], [data?.items]);

  const openCreate = () => {
    setEditing(null);
    setForm({
      branchId: branchFilter !== '__all__' ? branchFilter : '',
      specialtyId: '',
      status: 'ACTIVE',
      displayOrder: 0,
      consultationFee: 0,
      slotMinutesOverride: 15,
      note: '',
    });
    setOpen(true);
  };

  const openEdit = (row: BranchSpecialty) => {
    setEditing(row);
    setForm({
      branchId: row.branchId,
      specialtyId: row.specialtyId,
      status: row.status,
      displayOrder: row.displayOrder ?? 0,
      consultationFee: row.consultationFee ?? 0,
      slotMinutesOverride: row.slotMinutesOverride ?? 15,
      note: row.note ?? '',
    });
    setOpen(true);
  };

  const submit = async () => {
    const body = editing
      ? {
          status: form.status,
          displayOrder: Number(form.displayOrder),
          consultationFee: Number(form.consultationFee),
          slotMinutesOverride: Number(form.slotMinutesOverride),
          note: form.note,
        }
      : {
          branchId: Number(form.branchId),
          specialtyId: Number(form.specialtyId),
          displayOrder: Number(form.displayOrder),
          consultationFee: Number(form.consultationFee),
          slotMinutesOverride: Number(form.slotMinutesOverride),
          note: form.note,
        };

    await saveMutation.mutateAsync({
      id: editing?.id,
      body,
    });
    setOpen(false);
  };

  const columns: Column<BranchSpecialty>[] = [
    {
      key: 'branchName',
      header: t('common.branch'),
      cell: (r) => <span className="font-medium">{r.branchName}</span>,
    },
    {
      key: 'specialtyName',
      header: t('common.specialty'),
      cell: (r) => <span>{r.specialtyName}</span>,
    },
    {
      key: 'consultationFee',
      header: 'Phí khám',
      cell: (r) => <span>{(r.consultationFee ?? 0).toLocaleString('vi-VN')}</span>,
    },
    {
      key: 'status',
      header: 'Trạng thái cấu hình',
      cell: (r) => <StatusBadge status={r.status} />,
    },
    {
      key: 'effectiveStatus',
      header: 'Vận hành',
      cell: (r) => (
        <div className="space-y-1">
          <StatusBadge status={r.inactiveReason || r.effectiveStatus || 'ACTIVE'} />
          {r.inactiveReason && (
            <div className="flex items-start gap-1 text-xs text-muted-foreground">
              <AlertTriangle className="mt-0.5 h-3.5 w-3.5 text-amber-500" />
              <span>{inactiveReasonLabel(r.inactiveReason)}</span>
            </div>
          )}
        </div>
      ),
    },
  ];

  return (
    <div>
      <PageHeader
        title={t('modules.branchSpecialties.title')}
        description="Quản lý chuyên khoa theo từng chi nhánh và hiển thị rõ trạng thái vận hành thực tế"
        actions={
          <Button onClick={openCreate} disabled={branchFilter === '__all__'}>
            <Plus className="mr-2 h-4 w-4" />
            Thêm cấu hình
          </Button>
        }
      />

      <FilterBar
        filters={[
          {
            key: 'branch',
            label: t('common.branch'),
            options: branches.map((b) => ({ label: b.name, value: b.id })),
            value: branchFilter,
            onChange: setBranchFilter,
          },
        ]}
      />

      <DataTable
        columns={columns}
        data={rows}
        page={page}
        totalPages={Math.max(data?.meta.totalPages ?? 1, 1)}
        onPageChange={setPage}
        emptyMessage={
          branchFilter === '__all__'
            ? 'Chọn chi nhánh để xem cấu hình'
            : isLoading
              ? 'Đang tải...'
              : 'Không có dữ liệu'
        }
        keyExtractor={(r) => r.id}
        actions={(r) => (
          <TooltipProvider>
            <div className="ml-auto flex items-center gap-2">
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button
                    variant="outline"
                    size="icon"
                    onClick={() => openEdit(r as BranchSpecialty)}
                    className="h-9 w-9 rounded-xl border-border/70 bg-background hover:bg-muted"
                  >
                    <SquarePen className="h-4 w-4" />
                  </Button>
                </TooltipTrigger>
                <TooltipContent>Sửa cấu hình</TooltipContent>
              </Tooltip>
            </div>
          </TooltipProvider>
        )}
      />

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {editing ? 'Cập nhật cấu hình chuyên khoa' : 'Thêm cấu hình chuyên khoa'}
            </DialogTitle>
          </DialogHeader>

          <div className="space-y-4">
            {!editing && (
              <>
                <div>
                  <label className="mb-1.5 block text-sm font-medium">Chi nhánh</label>
                  <Select
                    value={form.branchId}
                    onValueChange={(value) => setForm({ ...form, branchId: value })}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder="Chọn chi nhánh" />
                    </SelectTrigger>
                    <SelectContent>
                      {branches.map((b) => (
                        <SelectItem key={b.id} value={b.id}>
                          {b.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                <div>
                  <label className="mb-1.5 block text-sm font-medium">Chuyên khoa</label>
                  <Select
                    value={form.specialtyId}
                    onValueChange={(value) => setForm({ ...form, specialtyId: value })}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder="Chọn chuyên khoa" />
                    </SelectTrigger>
                    <SelectContent>
                      {specialties.map((s) => (
                        <SelectItem key={s.id} value={s.id}>
                          {s.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              </>
            )}

            {editing && (
              <div>
                <label className="mb-1.5 block text-sm font-medium">Trạng thái cấu hình</label>
                <Select
                  value={form.status}
                  onValueChange={(value) => setForm({ ...form, status: value })}
                >
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

            <div>
              <label className="mb-1.5 block text-sm font-medium">Display order</label>
              <Input
                type="number"
                value={form.displayOrder}
                onChange={(e) => setForm({ ...form, displayOrder: Number(e.target.value) })}
              />
            </div>

            <div>
              <label className="mb-1.5 block text-sm font-medium">Phí khám</label>
              <Input
                type="number"
                value={form.consultationFee}
                onChange={(e) => setForm({ ...form, consultationFee: Number(e.target.value) })}
              />
            </div>

            <div>
              <label className="mb-1.5 block text-sm font-medium">Slot phút</label>
              <Input
                type="number"
                value={form.slotMinutesOverride}
                onChange={(e) =>
                  setForm({ ...form, slotMinutesOverride: Number(e.target.value) })
                }
              />
            </div>

            <div>
              <label className="mb-1.5 block text-sm font-medium">Ghi chú</label>
              <Input
                value={form.note}
                onChange={(e) => setForm({ ...form, note: e.target.value })}
              />
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)}>
              Hủy
            </Button>
            <Button onClick={submit} disabled={saveMutation.isPending}>
              Lưu
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}