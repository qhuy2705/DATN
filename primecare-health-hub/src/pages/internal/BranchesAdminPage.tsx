import { useMemo, useState } from 'react';
import {
  Building2,
  MapPin,
  MoreHorizontal,
  PauseCircle,
  Phone,
  Plus,
  RotateCcw,
  SquarePen,
} from 'lucide-react';
import { DataTable, type Column } from '@/components/DataTable';
import { PageHeader } from '@/components/PageHeader';
import { StatusBadge } from '@/components/StatusBadge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
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
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import {
  useAdminBranches,
  useAdminBranchesSummary,
  useSaveBranch,
  useUpdateBranchStatus,
} from '@/hooks/use-admin-data';
import { getToggleStatusActionClass, statusToneClasses } from '@/lib/status-style-classes';
import type { Branch } from '@/types/api';
import type { LucideIcon } from 'lucide-react';

type BranchForm = {
  code: string;
  nameVn: string;
  nameEn: string;
  addressVn: string;
  addressEn: string;
  phone: string;
  email: string;
  descriptionVn: string;
  descriptionEn: string;
  imageUrl: string;
  status: string;
};

const defaultForm: BranchForm = {
  code: '',
  nameVn: '',
  nameEn: '',
  addressVn: '',
  addressEn: '',
  phone: '',
  email: '',
  descriptionVn: '',
  descriptionEn: '',
  imageUrl: '',
  status: 'ACTIVE',
};

function SummaryCard({
  label,
  value,
  hint,
  icon: Icon,
  toneClass,
}: {
  label: string;
  value: string;
  hint: string;
  icon: LucideIcon;
  toneClass: string;
}) {
  return (
    <Card className="overflow-hidden rounded-2xl border border-border/60 bg-card shadow-sm">
      <CardContent className="p-4">
        <div className="flex items-start justify-between gap-3">
          <div className="space-y-2">
            <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-muted-foreground/90">
              {label}
            </p>
            <p className="text-3xl font-semibold leading-none tracking-tight text-foreground">{value}</p>
          </div>
          <div className={`flex h-10 w-10 items-center justify-center rounded-2xl border ${toneClass}`}>
            <Icon className="h-4 w-4" />
          </div>
        </div>
        <p className="mt-3 line-clamp-2 text-sm leading-5 text-muted-foreground">{hint}</p>
      </CardContent>
    </Card>
  );
}

export default function BranchesAdminPage() {
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('__all__');
  const [page, setPage] = useState(1);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<Branch | null>(null);
  const [form, setForm] = useState<BranchForm>(defaultForm);
  const [statusTarget, setStatusTarget] = useState<Branch | null>(null);

  const filterParams = useMemo(
    () => ({
      ...(search.trim() ? { q: search.trim() } : {}),
      ...(statusFilter !== '__all__' ? { status: statusFilter } : {}),
    }),
    [search, statusFilter],
  );

  const { data, isLoading } = useAdminBranches({
    page: String(page - 1),
    size: '10',
    ...filterParams,
  });
  const { data: branchSummary } = useAdminBranchesSummary(filterParams);

  const saveBranch = useSaveBranch();
  const updateStatus = useUpdateBranchStatus();

  const rows = useMemo(() => data?.items ?? [], [data?.items]);

  const loadedPageSummary = useMemo(() => {
    const activeBranches = rows.filter((branch) => branch.status === 'ACTIVE').length;
    const inactiveBranches = rows.filter((branch) => branch.status === 'INACTIVE').length;

    return {
      total: data?.meta.totalItems ?? rows.length,
      activeBranches,
      inactiveBranches,
    };
  }, [data?.meta.totalItems, rows]);
  const summary = branchSummary ?? loadedPageSummary;

  const openCreate = () => {
    setEditing(null);
    setForm(defaultForm);
    setOpen(true);
  };

  const openEdit = (row: Branch) => {
    setEditing(row);
    setForm({
      code: row.code ?? '',
      nameVn: row.nameVn ?? '',
      nameEn: row.nameEn ?? '',
      addressVn: row.addressVn ?? '',
      addressEn: row.addressEn ?? '',
      phone: row.phone ?? '',
      email: row.email ?? '',
      descriptionVn: row.descriptionVn ?? '',
      descriptionEn: row.descriptionEn ?? '',
      imageUrl: row.imageUrl ?? '',
      status: row.status ?? 'ACTIVE',
    });
    setOpen(true);
  };

  const submit = async () => {
    const body = editing
      ? {
          code: form.code,
          nameVn: form.nameVn,
          nameEn: form.nameEn,
          addressVn: form.addressVn,
          addressEn: form.addressEn,
          phone: form.phone,
          email: form.email,
          descriptionVn: form.descriptionVn,
          descriptionEn: form.descriptionEn,
          imageUrl: form.imageUrl,
        }
      : {
          code: form.code,
          nameVn: form.nameVn,
          nameEn: form.nameEn,
          addressVn: form.addressVn,
          addressEn: form.addressEn,
          phone: form.phone,
          email: form.email,
          descriptionVn: form.descriptionVn,
          descriptionEn: form.descriptionEn,
          imageUrl: form.imageUrl,
          status: 'ACTIVE',
        };

    await saveBranch.mutateAsync({
      id: editing?.id,
      body,
    });
    setOpen(false);
  };

  const confirmToggleStatus = async () => {
    if (!statusTarget) return;

    await updateStatus.mutateAsync({
      id: statusTarget.id,
      status: statusTarget.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE',
    });

    setStatusTarget(null);
  };

  const isDeactivating = statusTarget?.status === 'ACTIVE';

  const columns: Column<Branch>[] = [
    {
      key: 'branch',
      header: 'Chi nhánh',
      className: 'min-w-[340px]',
      cell: (row) => (
        <div className="space-y-2">
          <div className="space-y-0.5">
            <p className="text-sm font-semibold text-foreground">{row.name}</p>
            <p className="text-xs text-muted-foreground">Mã: {row.code || 'Chưa thiết lập mã'}</p>
          </div>

          <div className="flex items-start gap-2 text-sm text-muted-foreground">
            <MapPin className="mt-0.5 h-4 w-4 shrink-0" />
            <span className="line-clamp-2">{row.address || 'Chưa cập nhật địa chỉ chi nhánh'}</span>
          </div>
        </div>
      ),
    },
    {
      key: 'overview',
      header: 'Tổng quan vận hành',
      className: 'min-w-[320px]',
      cell: (row) => (
        <div className="space-y-2.5 rounded-2xl border border-border/70 bg-muted/20 p-3">
          <div className="flex flex-wrap items-center gap-2">
            <StatusBadge status={row.status || 'ACTIVE'} />
          </div>

          <div className="space-y-1 text-sm text-muted-foreground">
            <div className="flex items-center gap-2">
              <Phone className="h-4 w-4" />
              <span>{row.phone || 'Chưa cập nhật số điện thoại'}</span>
            </div>
            <div className="flex items-center gap-2">
              <Building2 className="h-4 w-4" />
              <span className="truncate">{row.email || 'Chưa cập nhật email chi nhánh'}</span>
            </div>
            <p className="pt-1 text-xs leading-5 text-muted-foreground">
              {row.description || 'Chi nhánh chưa có mô tả vận hành.'}
            </p>
          </div>
        </div>
      ),
    },
    {
      key: 'actions',
      header: 'Thao tác',
      className: 'w-[110px] text-right',
      cell: (row) => (
        <div className="ml-auto flex items-center justify-end">
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="outline" size="icon" className="h-9 w-9 rounded-xl border-border/70">
                <MoreHorizontal className="h-4 w-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-52 rounded-xl">
              <DropdownMenuItem onClick={() => openEdit(row)}>
                <SquarePen className="mr-2 h-4 w-4" />
                Sửa chi nhánh
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem
                onClick={() => setStatusTarget(row)}
                className={getToggleStatusActionClass(row.status === 'ACTIVE')}
              >
                {row.status === 'ACTIVE' ? (
                  <PauseCircle className="mr-2 h-4 w-4" />
                ) : (
                  <RotateCcw className="mr-2 h-4 w-4" />
                )}
                {row.status === 'ACTIVE' ? 'Ngưng hoạt động' : 'Kích hoạt lại'}
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <PageHeader
        title="Quản lý chi nhánh"
        description="Tổ chức mạng lưới cơ sở rõ ràng hơn, dễ quét nhanh và đồng bộ với giao diện quản trị hiện tại"
        actions={
          <Button onClick={openCreate} className="rounded-xl">
            <Plus className="mr-2 h-4 w-4" />
            Thêm chi nhánh
          </Button>
        }
      />

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        <SummaryCard
          label="Tổng chi nhánh"
          value={summary.total.toString()}
          hint="Theo bộ lọc hiện tại"
          icon={Building2}
          toneClass={statusToneClasses.neutral}
        />
        <SummaryCard
          label="Đang hoạt động"
          value={summary.activeBranches.toString()}
          hint="Theo bộ lọc hiện tại"
          icon={RotateCcw}
          toneClass={statusToneClasses.success}
        />
        <SummaryCard
          label="Tạm ngưng"
          value={summary.inactiveBranches.toString()}
          hint="Theo bộ lọc hiện tại"
          icon={PauseCircle}
          toneClass={statusToneClasses.warning}
        />
      </div>

      <DataTable
        columns={columns}
        data={rows}
        searchValue={search}
        onSearchChange={setSearch}
        searchPlaceholder="Tìm theo mã, tên, địa chỉ, email..."
        toolbar={
          <Select value={statusFilter} onValueChange={setStatusFilter}>
            <SelectTrigger className="w-[180px] rounded-xl">
              <SelectValue placeholder="Lọc trạng thái" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="__all__">Tất cả trạng thái</SelectItem>
              <SelectItem value="ACTIVE">Đang hoạt động</SelectItem>
              <SelectItem value="INACTIVE">Tạm ngưng</SelectItem>
            </SelectContent>
          </Select>
        }
        page={page}
        totalPages={Math.max(data?.meta.totalPages ?? 1, 1)}
        onPageChange={setPage}
        emptyMessage={isLoading ? 'Đang tải...' : 'Không có dữ liệu'}
        keyExtractor={(row) => row.id}
      />

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="flex h-[90vh] flex-col overflow-hidden p-0 sm:max-w-2xl">
          <DialogHeader className="shrink-0 border-b bg-background px-6 pb-4 pt-6">
            <DialogTitle>{editing ? 'Cập nhật chi nhánh' : 'Tạo chi nhánh'}</DialogTitle>
            <DialogDescription>
              Cập nhật thông tin nhận diện, liên hệ và mô tả để chi nhánh hiển thị nhất quán trong toàn hệ thống.
            </DialogDescription>
          </DialogHeader>

          <div className="min-h-0 flex-1 overflow-y-auto px-6 py-5">
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div>
                <label className="mb-1.5 block text-sm font-medium">Mã chi nhánh</label>
                <Input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} />
              </div>

              <div>
                <label className="mb-1.5 block text-sm font-medium">Tên VN</label>
                <Input value={form.nameVn} onChange={(e) => setForm({ ...form, nameVn: e.target.value })} />
              </div>

              <div>
                <label className="mb-1.5 block text-sm font-medium">Tên EN</label>
                <Input value={form.nameEn} onChange={(e) => setForm({ ...form, nameEn: e.target.value })} />
              </div>

              <div>
                <label className="mb-1.5 block text-sm font-medium">Điện thoại</label>
                <Input value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
              </div>

              <div className="sm:col-span-2">
                <label className="mb-1.5 block text-sm font-medium">Email</label>
                <Input value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
              </div>

              <div className="sm:col-span-2">
                <label className="mb-1.5 block text-sm font-medium">Địa chỉ VN</label>
                <Input value={form.addressVn} onChange={(e) => setForm({ ...form, addressVn: e.target.value })} />
              </div>

              <div className="sm:col-span-2">
                <label className="mb-1.5 block text-sm font-medium">Địa chỉ EN</label>
                <Input value={form.addressEn} onChange={(e) => setForm({ ...form, addressEn: e.target.value })} />
              </div>

              <div className="sm:col-span-2">
                <label className="mb-1.5 block text-sm font-medium">Image URL</label>
                <Input value={form.imageUrl} onChange={(e) => setForm({ ...form, imageUrl: e.target.value })} />
              </div>

              <div className="sm:col-span-2">
                <label className="mb-1.5 block text-sm font-medium">Mô tả VN</label>
                <Textarea
                  rows={4}
                  value={form.descriptionVn}
                  onChange={(e) => setForm({ ...form, descriptionVn: e.target.value })}
                />
              </div>

              <div className="sm:col-span-2">
                <label className="mb-1.5 block text-sm font-medium">Mô tả EN</label>
                <Textarea
                  rows={4}
                  value={form.descriptionEn}
                  onChange={(e) => setForm({ ...form, descriptionEn: e.target.value })}
                />
              </div>
            </div>
          </div>

          <DialogFooter className="shrink-0 border-t bg-background px-6 py-4">
            <Button variant="outline" onClick={() => setOpen(false)}>
              Hủy
            </Button>
            <Button onClick={() => void submit()} disabled={saveBranch.isPending}>
              Lưu
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        open={Boolean(statusTarget)}
        onOpenChange={(value) => {
          if (!value) setStatusTarget(null);
        }}
      >
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>
              {isDeactivating ? 'Tạm ngưng hoạt động chi nhánh' : 'Kích hoạt chi nhánh'}
            </DialogTitle>
            <DialogDescription>
              {isDeactivating
                ? 'Chi nhánh sẽ không còn được dùng trong các luồng vận hành mới cho đến khi được kích hoạt lại.'
                : 'Chi nhánh sẽ hoạt động trở lại và có thể được dùng trong các luồng vận hành tiếp theo.'}
            </DialogDescription>
          </DialogHeader>

          <p className="text-sm text-muted-foreground">
            Bạn có chắc muốn {isDeactivating ? 'tạm ngưng' : 'kích hoạt lại'} chi nhánh
            <span className="font-medium text-foreground"> {statusTarget?.name}</span> không?
          </p>

          <DialogFooter>
            <Button variant="outline" onClick={() => setStatusTarget(null)}>
              Hủy
            </Button>
            <Button
              variant={isDeactivating ? 'destructive' : 'default'}
              onClick={() => void confirmToggleStatus()}
              disabled={updateStatus.isPending}
            >
              Xác nhận
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
