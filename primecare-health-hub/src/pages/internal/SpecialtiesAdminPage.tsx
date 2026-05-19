import { useMemo, useState } from 'react';
import { Plus, SquarePen, ToggleLeft, ToggleRight } from 'lucide-react';
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { useTranslation } from 'react-i18next';
import { DataTable, type Column } from '@/components/DataTable';
import { PageHeader } from '@/components/PageHeader';
import { StatusBadge } from '@/components/StatusBadge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { useAdminSpecialties, useSaveSpecialty, useUpdateSpecialtyStatus } from '@/hooks/use-admin-data';
import type { AdminSpecialty } from '@/types/api';

export default function SpecialtiesAdminPage() {
  const { t } = useTranslation();
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(1);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editItem, setEditItem] = useState<AdminSpecialty | null>(null);
  const [form, setForm] = useState({
    code: '',
    nameVn: '',
    nameEn: '',
    descriptionVn: '',
    descriptionEn: '',
    iconUrl: '',
    defaultSlotMinutes: '15',
    maxPerSession: '0',
    status: 'ACTIVE',
  });

  const { data, isLoading } = useAdminSpecialties({
    page: String(page - 1),
    size: '20',
  });
  const saveMutation = useSaveSpecialty();
  const updateStatusMutation = useUpdateSpecialtyStatus();

  const rows = useMemo(() => {
    const items = data?.items ?? [];
    if (!search.trim()) return items;
    const q = search.toLowerCase();
    return items.filter((item) =>
      [item.name, item.code, item.description].join(' ').toLowerCase().includes(q),
    );
  }, [data?.items, search]);

  const openCreate = () => {
    setEditItem(null);
    setForm({
      code: '',
      nameVn: '',
      nameEn: '',
      descriptionVn: '',
      descriptionEn: '',
      iconUrl: '',
      defaultSlotMinutes: '15',
      maxPerSession: '0',
      status: 'ACTIVE',
    });
    setDialogOpen(true);
  };

  const openEdit = (item: AdminSpecialty) => {
    setEditItem(item);
    setForm({
      code: item.code ?? '',
      nameVn: item.nameVn ?? '',
      nameEn: item.nameEn ?? '',
      descriptionVn: item.descriptionVn ?? '',
      descriptionEn: item.descriptionEn ?? '',
      iconUrl: item.iconUrl ?? '',
      defaultSlotMinutes: String(item.defaultSlotMinutes ?? 15),
      maxPerSession: String(item.maxPerSession ?? 0),
      status: item.status ?? 'ACTIVE',
    });
    setDialogOpen(true);
  };

  const submit = async () => {
    const body = editItem
      ? {
          nameVn: form.nameVn,
          nameEn: form.nameEn,
          descriptionVn: form.descriptionVn,
          descriptionEn: form.descriptionEn,
          iconUrl: form.iconUrl,
          defaultSlotMinutes: Number(form.defaultSlotMinutes),
          maxPerSession: Number(form.maxPerSession),
          status: form.status,
        }
      : {
          code: form.code,
          nameVn: form.nameVn,
          nameEn: form.nameEn,
          descriptionVn: form.descriptionVn,
          descriptionEn: form.descriptionEn,
          iconUrl: form.iconUrl,
          defaultSlotMinutes: Number(form.defaultSlotMinutes),
          maxPerSession: Number(form.maxPerSession),
        };

    await saveMutation.mutateAsync({
      id: editItem?.id,
      body,
    });
    setDialogOpen(false);
  };

  const columns: Column<AdminSpecialty>[] = [
    {
      key: 'name',
      header: t('common.name'),
      cell: (r) => (
        <div>
          <p className="font-medium">{r.name}</p>
          <p className="text-xs text-muted-foreground">{r.code || '-'}</p>
        </div>
      ),
    },
    {
      key: 'defaultSlotMinutes',
      header: 'Slot mặc định',
      cell: (r) => <span>{r.defaultSlotMinutes ?? '-'}</span>,
    },
    {
      key: 'maxPerSession',
      header: 'Max / session',
      cell: (r) => <span>{r.maxPerSession ?? '-'}</span>,
    },
    {
      key: 'status',
      header: t('common.status'),
      cell: (r) => <StatusBadge status={r.status} />,
    },
  ];

  return (
    <div>
      <PageHeader
        title={t('modules.specialties.title')}
        description={t('modules.specialties.desc')}
        actions={
          <Button onClick={openCreate}>
            <Plus className="h-4 w-4 mr-2" />
            Thêm chuyên khoa
          </Button>
        }
      />
      <DataTable
        columns={columns}
        data={rows}
        searchValue={search}
        onSearchChange={setSearch}
        page={page}
        totalPages={Math.max(data?.meta.totalPages ?? 1, 1)}
        onPageChange={setPage}
        emptyMessage={isLoading ? 'Đang tải...' : 'Không có dữ liệu'}
        keyExtractor={(r) => r.id}
        actions={(r) => (
          <TooltipProvider>
            <div className="ml-auto flex items-center gap-2">
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button
                    variant="outline"
                    size="icon"
                    onClick={() => openEdit(r as AdminSpecialty)}
                    className="h-9 w-9 rounded-xl border-border/70 bg-background hover:bg-muted"
                  >
                    <SquarePen className="h-4 w-4" />
                  </Button>
                </TooltipTrigger>
                <TooltipContent>Sửa chuyên khoa</TooltipContent>
              </Tooltip>
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button
                    variant="outline"
                    size="icon"
                    disabled={updateStatusMutation.isPending}
                    onClick={() => updateStatusMutation.mutate({ id: (r as AdminSpecialty).id, status: (r as AdminSpecialty).status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE' })}
                    className="h-9 w-9 rounded-xl border-border/70 bg-background hover:bg-muted"
                  >
                    {(r as AdminSpecialty).status === 'ACTIVE' ? <ToggleRight className="h-4 w-4" /> : <ToggleLeft className="h-4 w-4" />}
                  </Button>
                </TooltipTrigger>
                <TooltipContent>{(r as AdminSpecialty).status === 'ACTIVE' ? 'Ngưng hoạt động' : 'Kích hoạt lại'}</TooltipContent>
              </Tooltip>
            </div>
          </TooltipProvider>
        )}
      />
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>
              {editItem ? 'Cập nhật chuyên khoa' : 'Tạo chuyên khoa'}
            </DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            {!editItem && (
              <div>
                <label className="text-sm font-medium mb-1.5 block">Code *</label>
                <Input
                  value={form.code}
                  onChange={(e) => setForm({ ...form, code: e.target.value })}
                />
              </div>
            )}
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="text-sm font-medium mb-1.5 block">Tên VN *</label>
                <Input
                  value={form.nameVn}
                  onChange={(e) => setForm({ ...form, nameVn: e.target.value })}
                />
              </div>
              <div>
                <label className="text-sm font-medium mb-1.5 block">Tên EN</label>
                <Input
                  value={form.nameEn}
                  onChange={(e) => setForm({ ...form, nameEn: e.target.value })}
                />
              </div>
            </div>
            <div>
              <label className="text-sm font-medium mb-1.5 block">Mô tả VN</label>
              <Textarea
                value={form.descriptionVn}
                onChange={(e) => setForm({ ...form, descriptionVn: e.target.value })}
                rows={3}
              />
            </div>
            <div>
              <label className="text-sm font-medium mb-1.5 block">Mô tả EN</label>
              <Textarea
                value={form.descriptionEn}
                onChange={(e) => setForm({ ...form, descriptionEn: e.target.value })}
                rows={3}
              />
            </div>
            <div>
              <label className="text-sm font-medium mb-1.5 block">Icon URL</label>
              <Input
                value={form.iconUrl}
                onChange={(e) => setForm({ ...form, iconUrl: e.target.value })}
              />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="text-sm font-medium mb-1.5 block">Slot mặc định</label>
                <Input
                  type="number"
                  value={form.defaultSlotMinutes}
                  onChange={(e) =>
                    setForm({ ...form, defaultSlotMinutes: e.target.value })
                  }
                />
              </div>
              <div>
                <label className="text-sm font-medium mb-1.5 block">Max / session</label>
                <Input
                  type="number"
                  value={form.maxPerSession}
                  onChange={(e) =>
                    setForm({ ...form, maxPerSession: e.target.value })
                  }
                />
              </div>
            </div>
            {editItem && (
              <div>
                <label className="text-sm font-medium mb-1.5 block">{t('common.status')}</label>
                <Select
                  value={form.status}
                  onValueChange={(v) => setForm({ ...form, status: v })}
                >
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ACTIVE">ACTIVE</SelectItem>
                    <SelectItem value="INACTIVE">INACTIVE</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            )}
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
    </div>
  );
}
