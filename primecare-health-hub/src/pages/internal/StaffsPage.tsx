import { useMemo, useState } from 'react';
import {
  Building2,
  KeyRound,
  MoreHorizontal,
  PauseCircle,
  Plus,
  RefreshCcw,
  RotateCcw,
  SquarePen,
  UserRound,
  Users,
} from 'lucide-react';
import { DataTable, type Column } from '@/components/DataTable';
import { PageHeader } from '@/components/PageHeader';
import { StatusBadge } from '@/components/StatusBadge';
import { Badge } from '@/components/ui/badge';
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
import {
  useAdminStaffs,
  useAdminStaffsSummary,
  useProvisionStaffAccount,
  useResetStaffAccountPassword,
  useSaveStaff,
  useUpdateStaffStatus,
} from '@/hooks/use-admin-data';
import { useBranches } from '@/hooks/use-public-data';
import { getAccountBadgeClass, getToggleStatusActionClass, statusToneClasses } from '@/lib/status-style-classes';
import type { AppRole, Staff } from '@/types/api';
import type { LucideIcon } from 'lucide-react';

type StaffForm = {
  fullName: string;
  branchId: string;
};

type StaffOperationalRole = Extract<
  AppRole,
  'STAFF' | 'CASHIER' | 'SERVICE_TECHNICIAN' | 'PHARMACIST'
>;

type StaffAccountForm = {
  email: string;
  phone: string;
  role: StaffOperationalRole;
};

const defaultForm: StaffForm = {
  fullName: '',
  branchId: '',
};

const STAFF_ACCOUNT_ROLE_OPTIONS: Array<{ value: StaffOperationalRole; label: string }> = [
  { value: 'STAFF', label: 'Tiếp đón / vận hành chung' },
  { value: 'CASHIER', label: 'Thu ngân' },
  { value: 'SERVICE_TECHNICIAN', label: 'Kỹ thuật viên cận lâm sàng' },
  { value: 'PHARMACIST', label: 'Dược sĩ' },
];

const STAFF_ACCOUNT_ROLE_VALUES = new Set<string>(
  STAFF_ACCOUNT_ROLE_OPTIONS.map((option) => option.value),
);

function normalizeStaffAccountRole(value?: string | null): StaffOperationalRole {
  return value && STAFF_ACCOUNT_ROLE_VALUES.has(value) ? (value as StaffOperationalRole) : 'STAFF';
}

function getStaffAccountRoleLabel(value?: string | null) {
  return (
    STAFF_ACCOUNT_ROLE_OPTIONS.find((option) => option.value === value)?.label ??
    'Chưa xác định vai trò nghiệp vụ'
  );
}

const getInitials = (name?: string) => {
  if (!name) return 'NS';
  return (
    name
      .trim()
      .split(/\s+/)
      .slice(-2)
      .map((part) => part[0]?.toUpperCase() ?? '')
      .join('') || 'NS'
  );
};

function formatSummaryValue(value: number | undefined, isLoading: boolean, isError: boolean) {
  if (isLoading) return '...';
  if (isError) return '-';
  return typeof value === 'number' ? value.toLocaleString('vi-VN') : '-';
}

function getSummaryHint(
  value: number | undefined,
  isLoading: boolean,
  isError: boolean,
  hasBackendSummary: boolean,
) {
  if (isLoading) return 'Đang tải tổng hợp từ backend';
  if (isError) return 'Không tải được tổng hợp từ backend';
  if (!hasBackendSummary) return 'Chưa có dữ liệu tổng hợp từ backend';
  if (typeof value !== 'number') return 'Backend chưa trả số liệu này';
  return 'Theo bộ lọc hiện tại';
}

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

export default function StaffsPage() {
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('__all__');
  const [branchFilter, setBranchFilter] = useState('__all__');
  const [page, setPage] = useState(1);
  const [open, setOpen] = useState(false);
  const [accountOpen, setAccountOpen] = useState(false);
  const [editing, setEditing] = useState<Staff | null>(null);
  const [accountStaff, setAccountStaff] = useState<Staff | null>(null);
  const [statusTarget, setStatusTarget] = useState<Staff | null>(null);
  const [resetTarget, setResetTarget] = useState<Staff | null>(null);
  const [resetResult, setResetResult] = useState<{ mode: 'ACCOUNT_SETUP' | 'PASSWORD_RESET'; staffName: string; deliveryChannel?: string; deliveryTarget?: string; setupUrl?: string; expiresAt?: string } | null>(null);
  const [form, setForm] = useState<StaffForm>(defaultForm);
  const [accountForm, setAccountForm] = useState<StaffAccountForm>({
    email: '',
    phone: '',
    role: 'STAFF',
  });

  const filterParams = useMemo(
    () => ({
      ...(search.trim() ? { q: search.trim() } : {}),
      ...(statusFilter !== '__all__' ? { status: statusFilter } : {}),
      ...(branchFilter !== '__all__' ? { branchId: branchFilter } : {}),
    }),
    [branchFilter, search, statusFilter],
  );

  const { data, isLoading } = useAdminStaffs({
    page: String(page - 1),
    size: '20',
    ...filterParams,
  });
  const {
    data: staffSummary,
    isLoading: staffSummaryLoading,
    isError: staffSummaryLoadError,
    isRefetchError: staffSummaryRefetchError,
    isPlaceholderData: staffSummaryPlaceholder,
  } = useAdminStaffsSummary(filterParams);

  const { data: branches = [] } = useBranches();
  const saveStaff = useSaveStaff();
  const provisionAccount = useProvisionStaffAccount();
  const resetStaffPassword = useResetStaffAccountPassword();
  const updateStaffStatus = useUpdateStaffStatus();

  const branchMap = useMemo(() => new Map(branches.map((branch) => [branch.id, branch.name])), [branches]);

  const rows = useMemo(
    () =>
      (data?.items ?? []).map((staff) => ({
        ...staff,
        branchName: staff.branchName || branchMap.get(staff.branchId || '') || '',
      })),
    [data?.items, branchMap],
  );

  const staffSummaryPending = staffSummaryLoading || staffSummaryPlaceholder;
  const staffSummaryError = staffSummaryLoadError || staffSummaryRefetchError;
  const hasStaffSummary = Boolean(staffSummary) && !staffSummaryPending && !staffSummaryError;
  const staffSummaryHint = (value: number | undefined) =>
    getSummaryHint(value, staffSummaryPending, staffSummaryError, hasStaffSummary);
  const inactiveAccountStaffs =
    staffSummary?.inactiveAccountStaffs ?? staffSummary?.blockedAccountStaffs;

  const openCreate = () => {
    setEditing(null);
    setForm(defaultForm);
    setOpen(true);
  };

  const openEdit = (row: Staff) => {
    setEditing(row);
    setForm({
      fullName: row.fullName ?? '',
      branchId: row.branchId ?? '',
    });
    setOpen(true);
  };

  const submit = async () => {
    await saveStaff.mutateAsync({
      id: editing?.id,
      body: form,
    });
    setOpen(false);
  };

  const openProvision = (row: Staff) => {
    setAccountStaff(row);
    setAccountForm({
      email: row.accountEmail || row.email || '',
      phone: row.accountPhone || row.phone || '',
      role: normalizeStaffAccountRole(row.accountRole || row.role),
    });
    setAccountOpen(true);
  };

  const submitProvision = async () => {
    if (!accountStaff) return;
    const result = await provisionAccount.mutateAsync({
      staffId: accountStaff.id,
      body: accountForm,
    });
    setResetResult({
      mode: 'ACCOUNT_SETUP',
      staffName: accountStaff.fullName,
      deliveryChannel: result.deliveryChannel,
      deliveryTarget: result.deliveryTarget,
      setupUrl: result.setupUrl,
      expiresAt: result.setupExpiresAt ?? result.expiresAt,
    });
    setAccountOpen(false);
  };

  const confirmToggleStatus = async () => {
    if (!statusTarget) return;

    await updateStaffStatus.mutateAsync({
      id: statusTarget.id,
      status: statusTarget.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE',
    });

    setStatusTarget(null);
  };

  const confirmResetPassword = async () => {
    if (!resetTarget) return;
    const target = resetTarget;
    const result = await resetStaffPassword.mutateAsync({ staffId: target.id });
    setResetTarget(null);
    setResetResult({
      mode: 'PASSWORD_RESET',
      staffName: target.fullName,
      deliveryChannel: result.deliveryChannel,
      deliveryTarget: result.deliveryTarget,
      setupUrl: result.setupUrl,
      expiresAt: result.expiresAt,
    });
  };

  const columns: Column<Staff>[] = [
    {
      key: 'staff',
      header: 'Nhân sự',
      className: 'min-w-[320px]',
      cell: (row) => (
        <div className="flex items-start gap-3">
          <div className="flex h-11 w-11 items-center justify-center rounded-2xl border border-border/70 bg-primary/10 text-primary">
            <span className="text-sm font-semibold">{getInitials(row.fullName)}</span>
          </div>

          <div className="min-w-0 space-y-2">
            <div className="space-y-0.5">
              <p className="truncate text-sm font-semibold text-foreground">{row.fullName}</p>
              <p className="truncate text-xs text-muted-foreground">
                {row.accountEmail || row.email || row.accountPhone || row.phone || 'Chưa có thông tin liên hệ'}
              </p>
            </div>

            <div className="flex flex-wrap gap-1.5">
              <Badge variant="outline" className="gap-1 rounded-full border-border/70 px-2.5 py-1 text-xs">
                <Building2 className="h-3.5 w-3.5" />
                {row.branchName || 'Chưa gán chi nhánh'}
              </Badge>
              {row.hasAccount ? (
                <Badge variant="outline" className="gap-1 rounded-full border-border/70 px-2.5 py-1 text-xs">
                  <UserRound className="h-3.5 w-3.5" />
                  {getStaffAccountRoleLabel(row.accountRole || row.role)}
                </Badge>
              ) : null}
            </div>
          </div>
        </div>
      ),
    },
    {
      key: 'overview',
      header: 'Tổng quan trạng thái',
      className: 'min-w-[300px]',
      cell: (row) => (
        <div className="space-y-2.5 rounded-2xl border border-border/70 bg-muted/20 p-3">
          <div className="flex flex-wrap items-center gap-2">
            <StatusBadge status={row.status || 'ACTIVE'} />
            <span
              className={`inline-flex items-center rounded-full border px-2.5 py-1 text-xs font-medium ${getAccountBadgeClass(
                row.hasAccount,
              )}`}
            >
              {row.hasAccount ? 'Đã cấp tài khoản' : 'Chưa cấp tài khoản'}
            </span>
          </div>

          <div className="space-y-1 text-sm text-muted-foreground">
            <div className="flex items-center gap-2">
              <KeyRound className="h-4 w-4" />
              <span className="font-medium text-foreground">Tài khoản:</span>
              <span>
                {row.hasAccount
                  ? row.accountStatus === 'INACTIVE'
                    ? 'Đang khóa'
                    : 'Đang hoạt động'
                  : 'Chưa tạo'}
              </span>
            </div>
            <div className="flex items-center gap-2">
              <Users className="h-4 w-4" />
              <span className="font-medium text-foreground">Vai trò:</span>
              <span>
                {row.hasAccount
                  ? getStaffAccountRoleLabel(row.accountRole || row.role)
                  : 'Chưa cấp tài khoản'}
              </span>
            </div>
            <div className="flex items-center gap-2">
              <UserRound className="h-4 w-4" />
              <span className="font-medium text-foreground">Liên hệ:</span>
              <span>{row.accountPhone || row.phone || row.accountEmail || row.email || 'Chưa cập nhật'}</span>
            </div>
          </div>
        </div>
      ),
    },
    {
      key: 'actions',
      header: 'Thao tác',
      className: 'w-[90px] text-right',
      cell: (row) => (
        <div className="ml-auto flex items-center justify-end gap-2">
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="outline" size="icon" className="h-9 w-9 rounded-xl border-border/70">
                <MoreHorizontal className="h-4 w-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-56 rounded-xl">
              <DropdownMenuItem onClick={() => openEdit(row)}>
                <SquarePen className="mr-2 h-4 w-4" />
                Sửa hồ sơ
              </DropdownMenuItem>

              {!row.hasAccount ? (
                <DropdownMenuItem onClick={() => openProvision(row)}>
                  <KeyRound className="mr-2 h-4 w-4" />
                  Tạo tài khoản
                </DropdownMenuItem>
              ) : (
                <DropdownMenuItem onClick={() => setResetTarget(row)}>
                  <RefreshCcw className="mr-2 h-4 w-4" />
                  Reset mật khẩu mặc định
                </DropdownMenuItem>
              )}

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
        title="Quản lý nhân sự"
        description="Tổ chức hồ sơ nhân sự vận hành gọn gàng, rõ trạng thái và quản lý tài khoản tập trung"
        actions={
          <Button onClick={openCreate} className="rounded-xl">
            <Plus className="mr-2 h-4 w-4" />
            Thêm nhân sự
          </Button>
        }
      />

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <SummaryCard
          label="Tổng nhân sự"
          value={formatSummaryValue(staffSummary?.total, staffSummaryPending, staffSummaryError)}
          hint={staffSummaryHint(staffSummary?.total)}
          icon={Users}
          toneClass={statusToneClasses.neutral}
        />
        <SummaryCard
          label="Đang hoạt động"
          value={formatSummaryValue(staffSummary?.activeStaffs, staffSummaryPending, staffSummaryError)}
          hint={staffSummaryHint(staffSummary?.activeStaffs)}
          icon={UserRound}
          toneClass={statusToneClasses.success}
        />
        <SummaryCard
          label="Ngừng hoạt động"
          value={formatSummaryValue(staffSummary?.inactiveStaffs, staffSummaryPending, staffSummaryError)}
          hint={staffSummaryHint(staffSummary?.inactiveStaffs)}
          icon={PauseCircle}
          toneClass={statusToneClasses.neutral}
        />
        <SummaryCard
          label="Chưa cấp tài khoản"
          value={formatSummaryValue(staffSummary?.noAccountStaffs, staffSummaryPending, staffSummaryError)}
          hint={staffSummaryHint(staffSummary?.noAccountStaffs)}
          icon={KeyRound}
          toneClass={statusToneClasses.warning}
        />
        <SummaryCard
          label="Tài khoản không hoạt động"
          value={formatSummaryValue(inactiveAccountStaffs, staffSummaryPending, staffSummaryError)}
          hint={staffSummaryHint(inactiveAccountStaffs)}
          icon={KeyRound}
          toneClass={statusToneClasses.destructive}
        />
      </div>

      <DataTable
        columns={columns}
        data={rows}
        searchValue={search}
        onSearchChange={setSearch}
        searchPlaceholder="Tìm theo nhân sự, email, số điện thoại, chi nhánh..."
        toolbar={
          <>
            <Select value={branchFilter} onValueChange={setBranchFilter}>
              <SelectTrigger className="w-[180px] rounded-xl">
                <SelectValue placeholder="Chi nhánh" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="__all__">Tất cả chi nhánh</SelectItem>
                {branches.map((branch) => (
                  <SelectItem key={branch.id} value={branch.id}>
                    {branch.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>

            <Select value={statusFilter} onValueChange={setStatusFilter}>
              <SelectTrigger className="w-[180px] rounded-xl">
                <SelectValue placeholder="Trạng thái" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="__all__">Tất cả trạng thái</SelectItem>
                <SelectItem value="ACTIVE">Đang hoạt động</SelectItem>
                <SelectItem value="INACTIVE">Ngưng hoạt động</SelectItem>
              </SelectContent>
            </Select>
          </>
        }
        page={page}
        totalPages={Math.max(data?.meta.totalPages ?? 1, 1)}
        onPageChange={setPage}
        emptyMessage={isLoading ? 'Đang tải...' : 'Không có dữ liệu'}
        keyExtractor={(row) => row.id}
      />

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="sm:max-w-xl">
          <DialogHeader>
            <DialogTitle>{editing ? 'Cập nhật nhân sự' : 'Tạo nhân sự'}</DialogTitle>
            <DialogDescription>
              Sysadmin quản lý hồ sơ nhân sự và đồng bộ trạng thái tài khoản theo hồ sơ nhân sự.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <div>
              <label className="mb-1.5 block text-sm font-medium">Họ tên</label>
              <Input value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} />
            </div>

            <div>
              <label className="mb-1.5 block text-sm font-medium">Chi nhánh</label>
              <Select value={form.branchId} onValueChange={(value) => setForm({ ...form, branchId: value })}>
                <SelectTrigger>
                  <SelectValue placeholder="Chọn chi nhánh" />
                </SelectTrigger>
                <SelectContent>
                  {branches.map((branch) => (
                    <SelectItem key={branch.id} value={branch.id}>
                      {branch.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)}>
              Hủy
            </Button>
            <Button onClick={() => void submit()} disabled={saveStaff.isPending}>
              Lưu hồ sơ
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={accountOpen} onOpenChange={setAccountOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Cấp tài khoản nghiệp vụ</DialogTitle>
            <DialogDescription>
              Chọn đúng vai trò nghiệp vụ để nhân sự nhìn thấy đúng menu và luồng làm việc. PrimeCare sẽ gửi liên kết thiết lập mật khẩu qua email hoặc SMS.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <div>
              <label className="mb-1.5 block text-sm font-medium">Email</label>
              <Input
                value={accountForm.email}
                onChange={(e) => setAccountForm({ ...accountForm, email: e.target.value })}
                placeholder="staff@primecare.vn"
              />
            </div>
            <div>
              <label className="mb-1.5 block text-sm font-medium">Số điện thoại</label>
              <Input
                value={accountForm.phone}
                onChange={(e) => setAccountForm({ ...accountForm, phone: e.target.value })}
                placeholder="09xxxxxxxx"
              />
            </div>

            <div>
              <label className="mb-1.5 block text-sm font-medium">Vai trò tài khoản</label>
              <Select
                value={accountForm.role}
                onValueChange={(value) =>
                  setAccountForm({ ...accountForm, role: normalizeStaffAccountRole(value) })
                }
              >
                <SelectTrigger>
                  <SelectValue placeholder="Chọn vai trò" />
                </SelectTrigger>
                <SelectContent>
                  {STAFF_ACCOUNT_ROLE_OPTIONS.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <p className="mt-1 text-xs text-muted-foreground">
                Chỉ cấp các vai trò nghiệp vụ nội bộ: tiếp đón, thu ngân, kỹ thuật viên cận lâm sàng hoặc dược sĩ.
              </p>
            </div>
            <div className="rounded-xl border bg-muted/30 p-3 text-xs leading-5 text-muted-foreground">
              Tài khoản nhân sự sẽ được tạo ở trạng thái chờ kích hoạt. Người dùng cần mở liên kết thiết lập mật khẩu trước khi có thể đăng nhập vào PrimeCare.
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setAccountOpen(false)}>
              Hủy
            </Button>
            <Button onClick={() => void submitProvision()} disabled={provisionAccount.isPending}>
              Cấp tài khoản
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={Boolean(statusTarget)} onOpenChange={(value) => !value && setStatusTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {statusTarget?.status === 'ACTIVE' ? 'Ngưng hoạt động nhân sự' : 'Kích hoạt lại nhân sự'}
            </DialogTitle>
            <DialogDescription>
              {statusTarget?.status === 'ACTIVE'
                ? 'Khi nhân sự ngưng hoạt động, tài khoản đăng nhập cũng sẽ tự chuyển sang trạng thái ngưng hoạt động để tránh đăng nhập sai quyền.'
                : 'Khi kích hoạt lại nhân sự, tài khoản của nhân sự cũng sẽ được mở lại nếu đã tồn tại.'}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setStatusTarget(null)}>
              Hủy
            </Button>
            <Button onClick={() => void confirmToggleStatus()} disabled={updateStaffStatus.isPending}>
              Xác nhận
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={Boolean(resetTarget)} onOpenChange={(value) => !value && setResetTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Xác nhận reset mật khẩu</DialogTitle>
            <DialogDescription>
              PrimeCare sẽ phát hành một liên kết đặt lại mật khẩu mới cho nhân sự {resetTarget?.fullName}. Liên kết cũ sẽ bị thu hồi ngay sau khi tạo mới.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setResetTarget(null)}>
              Hủy
            </Button>
            <Button onClick={() => void confirmResetPassword()} disabled={resetStaffPassword.isPending}>
              Xác nhận reset
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={Boolean(resetResult)} onOpenChange={(value) => !value && setResetResult(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{resetResult?.mode === 'ACCOUNT_SETUP' ? 'Đã tạo liên kết kích hoạt tài khoản' : 'Đã phát hành liên kết đặt lại mật khẩu'}</DialogTitle>
            <DialogDescription>
              {resetResult?.mode === 'ACCOUNT_SETUP'
                ? 'PrimeCare đã tạo liên kết kích hoạt tài khoản cho nhân sự ' + (resetResult?.staffName || '') + '. Bạn có thể gửi đường dẫn này qua kênh nội bộ an toàn nếu hệ thống không giao tự động được.'
                : 'PrimeCare đã tạo liên kết đặt lại mật khẩu cho nhân sự ' + (resetResult?.staffName || '') + '. Bạn có thể gửi đường dẫn này qua kênh nội bộ an toàn nếu hệ thống không giao tự động được.'}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-3 rounded-lg border bg-muted/40 p-4 text-sm">
            <div>
              <p className="text-xs uppercase tracking-wide text-muted-foreground">Kênh giao</p>
              <p className="mt-1 font-medium">{resetResult?.deliveryChannel || 'MANUAL'} {resetResult?.deliveryTarget ? `• ${resetResult.deliveryTarget}` : ''}</p>
            </div>
            <div>
              <p className="text-xs uppercase tracking-wide text-muted-foreground">Liên kết thiết lập mật khẩu</p>
              <p className="mt-1 break-all font-mono text-xs">{resetResult?.setupUrl || 'Không có'}</p>
            </div>
          </div>

          <DialogFooter>
            <Button onClick={() => setResetResult(null)}>Đã hiểu</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
