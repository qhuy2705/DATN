import { useMemo, useState } from 'react';
import {
  AlertTriangle,
  BriefcaseMedical,
  Building2,
  KeyRound,
  MoreHorizontal,
  PauseCircle,
  Plus,
  RefreshCcw,
  RotateCcw,
  SquarePen,
  Stethoscope,
  UserRound,
} from 'lucide-react';
import { DataTable, type Column } from '@/components/DataTable';
import { PageHeader } from '@/components/PageHeader';
import { StatusBadge } from '@/components/StatusBadge';
import { UploadField } from '@/components/UploadField';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
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
import { Textarea } from '@/components/ui/textarea';
import {
  useAdminDoctors,
  useAdminDoctorsSummary,
  useProvisionDoctorAccount,
  useResetDoctorAccountPassword,
  useSaveDoctor,
  useUpdateDoctorStatus,
} from '@/hooks/use-admin-data';
import { useBranches, useSpecialties } from '@/hooks/use-public-data';
import { resolveImagePreviewUrl } from '@/lib/api-url';
import type { Doctor } from '@/types/api';
import type { LucideIcon } from 'lucide-react';

type DoctorForm = {
  fullName: string;
  displayTitleVn: string;
  displayTitleEn: string;
  bioVn: string;
  bioEn: string;
  expertiseVn: string;
  expertiseEn: string;
  yearsExp: number;
  specialtyIds: string[];
  branchId: string;
  educationVn: string;
  educationEn: string;
  achievementsVn: string;
  achievementsEn: string;
  avatarUrl: string;
};

const defaultForm: DoctorForm = {
  fullName: '',
  displayTitleVn: '',
  displayTitleEn: '',
  bioVn: '',
  bioEn: '',
  expertiseVn: '',
  expertiseEn: '',
  yearsExp: 0,
  specialtyIds: [],
  branchId: '',
  educationVn: '',
  educationEn: '',
  achievementsVn: '',
  achievementsEn: '',
  avatarUrl: '',
};

const inactiveReasonLabel = (reason?: string | null) => {
  switch (reason) {
    case 'BRANCH_INACTIVE':
      return 'Chi nhánh đang tạm ngưng hoạt động';
    case 'SPECIALTY_INACTIVE':
      return 'Chuyên khoa đang tạm ngưng hoạt động';
    case 'SELF_INACTIVE':
      return 'Bác sĩ đang ngưng hoạt động';
    default:
      return 'Đang vận hành bình thường';
  }
};

const accountBadgeClass = (hasAccount?: boolean) =>
  hasAccount
    ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
    : 'border-slate-200 bg-slate-100 text-slate-700';

const getInitials = (name?: string) => {
  if (!name) return 'BS';
  return (
    name
      .trim()
      .split(/\s+/)
      .slice(-2)
      .map((part) => part[0]?.toUpperCase() ?? '')
      .join('') || 'BS'
  );
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
    <Card className="overflow-hidden rounded-2xl border border-border/60 bg-white shadow-sm">
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

export default function DoctorsAdminPage() {
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('__all__');
  const [branchFilter, setBranchFilter] = useState('__all__');
  const [specialtyFilter, setSpecialtyFilter] = useState('__all__');
  const [page, setPage] = useState(1);
  const [open, setOpen] = useState(false);
  const [accountOpen, setAccountOpen] = useState(false);
  const [editing, setEditing] = useState<Doctor | null>(null);
  const [accountDoctor, setAccountDoctor] = useState<Doctor | null>(null);
  const [statusTarget, setStatusTarget] = useState<Doctor | null>(null);
  const [resetTarget, setResetTarget] = useState<Doctor | null>(null);
  const [resetResult, setResetResult] = useState<{ mode: 'ACCOUNT_SETUP' | 'PASSWORD_RESET'; doctorName: string; deliveryChannel?: string; deliveryTarget?: string; setupUrl?: string; expiresAt?: string } | null>(null);
  const [form, setForm] = useState<DoctorForm>(defaultForm);
  const [accountForm, setAccountForm] = useState({
    email: '',
    phone: '',
  });

  const filterParams = useMemo(
    () => ({
      ...(search.trim() ? { q: search.trim() } : {}),
      ...(statusFilter !== '__all__' ? { status: statusFilter } : {}),
      ...(branchFilter !== '__all__' ? { branchId: branchFilter } : {}),
      ...(specialtyFilter !== '__all__' ? { specialtyId: specialtyFilter } : {}),
    }),
    [branchFilter, search, specialtyFilter, statusFilter],
  );

  const { data, isLoading } = useAdminDoctors({
    page: String(page - 1),
    size: '20',
    ...filterParams,
  });
  const { data: doctorSummary } = useAdminDoctorsSummary(filterParams);

  const { data: branches = [] } = useBranches();
  const { data: specialties = [] } = useSpecialties();

  const saveDoctor = useSaveDoctor();
  const provisionAccount = useProvisionDoctorAccount();
  const resetDoctorPassword = useResetDoctorAccountPassword();
  const updateDoctorStatus = useUpdateDoctorStatus();

  const branchMap = useMemo(
    () => new Map(branches.map((branch) => [branch.id, branch.name])),
    [branches],
  );
  const specialtyMap = useMemo(
    () => new Map(specialties.map((specialty) => [specialty.id, specialty.name])),
    [specialties],
  );

  const rows = useMemo(
    () =>
      (data?.items ?? []).map((doctor) => ({
        ...doctor,
        branchName: doctor.branchName || branchMap.get(doctor.branchId || '') || '',
        specialtyName:
          doctor.specialtyName ||
          specialtyMap.get(doctor.specialtyId || doctor.specialtyIds?.[0] || '') ||
          '',
      })),
    [data?.items, branchMap, specialtyMap],
  );

  const loadedPageSummary = useMemo(() => {
    const activeDoctors = rows.filter((doctor) => doctor.status === 'ACTIVE').length;
    const noAccountDoctors = rows.filter((doctor) => !doctor.hasAccount).length;
    const blockedDoctors = rows.filter(
      (doctor) => Boolean(doctor.inactiveReason && doctor.inactiveReason !== 'SELF_INACTIVE'),
    ).length;

    return {
      total: data?.meta.totalItems ?? rows.length,
      activeDoctors,
      noAccountDoctors,
      blockedDoctors,
    };
  }, [data?.meta.totalItems, rows]);
  const summary = doctorSummary ?? loadedPageSummary;

  const openCreate = () => {
    setEditing(null);
    setForm(defaultForm);
    setOpen(true);
  };

  const openEdit = (row: Doctor) => {
    setEditing(row);
    setForm({
      fullName: row.fullName ?? '',
      displayTitleVn: row.title ?? '',
      displayTitleEn: row.title ?? '',
      bioVn: row.bio ?? '',
      bioEn: row.bio ?? '',
      expertiseVn: row.expertise ?? '',
      expertiseEn: row.expertise ?? '',
      yearsExp: Number(row.yearsOfExperience ?? 0),
      specialtyIds: row.specialtyIds?.length ? row.specialtyIds : row.specialtyId ? [row.specialtyId] : [],
      branchId: row.branchId ?? '',
      educationVn: row.education ?? '',
      educationEn: row.education ?? '',
      achievementsVn: row.achievements ?? '',
      achievementsEn: row.achievements ?? '',
      avatarUrl: row.avatarUrl ?? '',
    });
    setOpen(true);
  };

  const submit = async () => {
    await saveDoctor.mutateAsync({
      id: editing?.id,
      body: {
        ...form,
        specialtyIds: form.specialtyIds,
        ...(editing ? {} : { status: 'ACTIVE' }),
      },
    });
    setOpen(false);
  };

  const openProvision = (row: Doctor) => {
    setAccountDoctor(row);
    setAccountForm({
      email: row.accountEmail || '',
      phone: row.accountPhone || '',
    });
    setAccountOpen(true);
  };

  const submitProvision = async () => {
    if (!accountDoctor) return;
    const result = await provisionAccount.mutateAsync({
      doctorId: accountDoctor.id,
      body: accountForm,
    });
    setResetResult({
      mode: 'ACCOUNT_SETUP',
      doctorName: accountDoctor.fullName,
      deliveryChannel: result.deliveryChannel,
      deliveryTarget: result.deliveryTarget,
      setupUrl: result.setupUrl,
      expiresAt: result.setupExpiresAt ?? result.expiresAt,
    });
    setAccountOpen(false);
  };

  const confirmToggleStatus = async () => {
    if (!statusTarget) return;

    await updateDoctorStatus.mutateAsync({
      id: statusTarget.id,
      status: statusTarget.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE',
    });

    setStatusTarget(null);
  };

  const handleResetPassword = async () => {
    if (!resetTarget) return;

    const result = await resetDoctorPassword.mutateAsync({ doctorId: resetTarget.id });
    setResetResult({
      mode: 'PASSWORD_RESET',
      doctorName: resetTarget.fullName,
      deliveryChannel: result.deliveryChannel,
      deliveryTarget: result.deliveryTarget,
      setupUrl: result.setupUrl,
      expiresAt: result.expiresAt,
    });
    setResetTarget(null);
  };

  const columns: Column<Doctor>[] = [
    {
      key: 'doctor',
      header: 'Bác sĩ',
      className: 'min-w-[320px]',
      cell: (row) => {
        const avatarSrc = resolveImagePreviewUrl(row.avatarUrl, 'image/*');

        return (
          <div className="flex items-start gap-3">
            <Avatar className="h-11 w-11 rounded-2xl border border-border/70">
              {avatarSrc && <AvatarImage src={avatarSrc} alt={row.fullName} />}
              <AvatarFallback className="rounded-2xl bg-primary/10 text-primary">
                {getInitials(row.fullName)}
              </AvatarFallback>
            </Avatar>

            <div className="min-w-0 space-y-2">
              <div className="space-y-0.5">
                <p className="truncate text-sm font-semibold text-foreground">{row.fullName}</p>
                <p className="truncate text-xs text-muted-foreground">
                  {row.title || 'Chưa cập nhật chức danh'}
                </p>
              </div>

              <div className="flex flex-wrap gap-1.5">
                <Badge variant="outline" className="gap-1 rounded-full border-border/70 px-2.5 py-1 text-xs">
                  <Stethoscope className="h-3.5 w-3.5" />
                  {row.specialtyName || 'Chưa gán chuyên khoa'}
                </Badge>
                <Badge variant="outline" className="gap-1 rounded-full border-border/70 px-2.5 py-1 text-xs">
                  <Building2 className="h-3.5 w-3.5" />
                  {row.branchName || 'Chưa gán chi nhánh'}
                </Badge>
                <Badge variant="outline" className="gap-1 rounded-full border-border/70 px-2.5 py-1 text-xs">
                  <BriefcaseMedical className="h-3.5 w-3.5" />
                  {row.yearsOfExperience || 0} năm KN
                </Badge>
              </div>
            </div>
          </div>
        );
      },
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
              className={`inline-flex items-center rounded-full border px-2.5 py-1 text-xs font-medium ${accountBadgeClass(
                row.hasAccount,
              )}`}
            >
              {row.hasAccount ? 'Đã cấp tài khoản' : 'Chưa cấp tài khoản'}
            </span>
          </div>

          <div className="space-y-1 text-sm">
            <div className="flex items-center gap-2 text-muted-foreground">
              <UserRound className="h-4 w-4" />
              <span className="font-medium text-foreground">Vận hành:</span>
              <span>{inactiveReasonLabel(row.inactiveReason || row.effectiveStatus)}</span>
            </div>

            {row.inactiveReason && row.inactiveReason !== 'SELF_INACTIVE' && (
              <div className="flex items-start gap-2 rounded-xl border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-700">
                <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0" />
                <span>{inactiveReasonLabel(row.inactiveReason)}</span>
              </div>
            )}
          </div>
        </div>
      ),
    },
    {
      key: 'actions',
      header: 'Thao tác',
      className: 'w-[120px] text-right',
      cell: (row) => (
        <div className="ml-auto flex items-center justify-end">
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
                className={row.status === 'ACTIVE' ? 'text-amber-700' : 'text-emerald-700'}
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
        title="Quản lý bác sĩ"
        description="Tập trung vào trạng thái thật sự quan trọng, giảm nhiễu nhưng vẫn đủ thông tin để vận hành"
        actions={
          <Button onClick={openCreate} className="rounded-xl">
            <Plus className="mr-2 h-4 w-4" />
            Thêm bác sĩ
          </Button>
        }
      />

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <SummaryCard
          label="Tổng bác sĩ"
          value={summary.total.toString()}
          hint="Theo bộ lọc hiện tại"
          icon={UserRound}
          toneClass="border-slate-200 bg-slate-50 text-slate-600"
        />
        <SummaryCard
          label="Đang hoạt động"
          value={summary.activeDoctors.toString()}
          hint="Theo bộ lọc hiện tại"
          icon={Stethoscope}
          toneClass="border-emerald-200 bg-emerald-50 text-emerald-700"
        />
        <SummaryCard
          label="Chưa có tài khoản"
          value={summary.noAccountDoctors.toString()}
          hint="Theo bộ lọc hiện tại"
          icon={KeyRound}
          toneClass="border-amber-200 bg-amber-50 text-amber-700"
        />
        <SummaryCard
          label="Đang bị chặn vận hành"
          value={summary.blockedDoctors.toString()}
          hint="Theo bộ lọc hiện tại"
          icon={AlertTriangle}
          toneClass="border-rose-200 bg-rose-50 text-rose-700"
        />
      </div>

      <DataTable
        columns={columns}
        data={rows}
        searchValue={search}
        onSearchChange={setSearch}
        searchPlaceholder="Tìm theo bác sĩ, chức danh, chuyên khoa..."
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

            <Select value={specialtyFilter} onValueChange={setSpecialtyFilter}>
              <SelectTrigger className="w-[180px] rounded-xl">
                <SelectValue placeholder="Chuyên khoa" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="__all__">Tất cả chuyên khoa</SelectItem>
                {specialties.map((specialty) => (
                  <SelectItem key={specialty.id} value={specialty.id}>
                    {specialty.name}
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
        <DialogContent className="flex h-[90vh] flex-col overflow-hidden p-0 sm:max-w-4xl">
          <DialogHeader className="shrink-0 border-b bg-background px-6 pb-4 pt-6">
              <DialogTitle>{editing ? 'Cập nhật bác sĩ' : 'Tạo bác sĩ'}</DialogTitle>
              <DialogDescription>
                Admin quản lý hồ sơ, trạng thái hoạt động và avatar bác sĩ. Với bác sĩ mới, nên tạo hồ sơ trước rồi mới cấp tài khoản.
              </DialogDescription>
            </DialogHeader>

            <div className="min-h-0 flex-1 overflow-y-auto px-6 py-5 space-y-5">
              <div className="grid gap-4 sm:grid-cols-2">
                <div>
                  <label className="mb-1.5 block text-sm font-medium">Họ tên</label>
                  <Input value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} />
                </div>

                <div>
                  <label className="mb-1.5 block text-sm font-medium">Số năm kinh nghiệm</label>
                  <Input
                    type="number"
                    value={form.yearsExp}
                    onChange={(e) => setForm({ ...form, yearsExp: Number(e.target.value) })}
                  />
                </div>
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
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

                <div>
                  <label className="mb-1.5 block text-sm font-medium">Chuyên khoa chính</label>
                  <Select
                    value={form.specialtyIds[0] || ''}
                    onValueChange={(value) => setForm({ ...form, specialtyIds: value ? [value] : [] })}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder="Chọn chuyên khoa" />
                    </SelectTrigger>
                    <SelectContent>
                      {specialties.map((specialty) => (
                        <SelectItem key={specialty.id} value={specialty.id}>
                          {specialty.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <div>
                  <label className="mb-1.5 block text-sm font-medium">Chức danh VN</label>
                  <Input
                    value={form.displayTitleVn}
                    onChange={(e) => setForm({ ...form, displayTitleVn: e.target.value })}
                  />
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-medium">Chức danh EN</label>
                  <Input
                    value={form.displayTitleEn}
                    onChange={(e) => setForm({ ...form, displayTitleEn: e.target.value })}
                  />
                </div>
              </div>

              <UploadField
                label="Avatar bác sĩ"
                value={form.avatarUrl}
                ownerType="DOCTOR"
                ownerId={editing?.id}
                onChange={(url) => setForm({ ...form, avatarUrl: url })}
                helperText={
                  editing
                    ? 'Ảnh sẽ được lưu lên hệ thống file và cập nhật vào hồ sơ bác sĩ.'
                    : 'Tạo bác sĩ trước rồi quay lại chỉnh sửa để upload avatar trực tiếp.'
                }
              />

              <div className="grid gap-4 sm:grid-cols-2">
                <div>
                  <label className="mb-1.5 block text-sm font-medium">Bio VN</label>
                  <Textarea
                    rows={4}
                    value={form.bioVn}
                    onChange={(e) => setForm({ ...form, bioVn: e.target.value })}
                  />
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-medium">Bio EN</label>
                  <Textarea
                    rows={4}
                    value={form.bioEn}
                    onChange={(e) => setForm({ ...form, bioEn: e.target.value })}
                  />
                </div>
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <div>
                  <label className="mb-1.5 block text-sm font-medium">Chuyên môn VN</label>
                  <Textarea
                    rows={4}
                    value={form.expertiseVn}
                    onChange={(e) => setForm({ ...form, expertiseVn: e.target.value })}
                  />
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-medium">Chuyên môn EN</label>
                  <Textarea
                    rows={4}
                    value={form.expertiseEn}
                    onChange={(e) => setForm({ ...form, expertiseEn: e.target.value })}
                  />
                </div>
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <div>
                  <label className="mb-1.5 block text-sm font-medium">Học vấn VN</label>
                  <Textarea
                    rows={4}
                    value={form.educationVn}
                    onChange={(e) => setForm({ ...form, educationVn: e.target.value })}
                  />
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-medium">Học vấn EN</label>
                  <Textarea
                    rows={4}
                    value={form.educationEn}
                    onChange={(e) => setForm({ ...form, educationEn: e.target.value })}
                  />
                </div>
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <div>
                  <label className="mb-1.5 block text-sm font-medium">Thành tựu VN</label>
                  <Textarea
                    rows={4}
                    value={form.achievementsVn}
                    onChange={(e) => setForm({ ...form, achievementsVn: e.target.value })}
                  />
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-medium">Thành tựu EN</label>
                  <Textarea
                    rows={4}
                    value={form.achievementsEn}
                    onChange={(e) => setForm({ ...form, achievementsEn: e.target.value })}
                  />
                </div>
              </div>
            </div>

          <DialogFooter className="shrink-0 border-t bg-background px-6 py-4">
            <Button variant="outline" onClick={() => setOpen(false)}>
              Hủy
            </Button>
            <Button onClick={() => void submit()} disabled={saveDoctor.isPending}>
              Lưu hồ sơ
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={accountOpen} onOpenChange={setAccountOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Tạo tài khoản bác sĩ</DialogTitle>
            <DialogDescription>
              PrimeCare sẽ gửi liên kết thiết lập mật khẩu qua email hoặc SMS. Nếu không gửi tự động được, admin có thể copy link thủ công để chuyển cho bác sĩ qua kênh nội bộ an toàn.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <div>
              <label className="mb-1.5 block text-sm font-medium">Email</label>
              <Input
                value={accountForm.email}
                onChange={(e) => setAccountForm({ ...accountForm, email: e.target.value })}
                placeholder="doctor@primecare.vn"
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
            <div className="rounded-xl border bg-muted/30 p-3 text-xs leading-5 text-muted-foreground">
              Hệ thống sẽ tạo tài khoản ở trạng thái chờ kích hoạt và gửi liên kết thiết lập mật khẩu một lần. Bác sĩ chỉ có thể đăng nhập sau khi hoàn tất bước thiết lập mật khẩu.
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setAccountOpen(false)}>
              Hủy
            </Button>
            <Button onClick={() => void submitProvision()} disabled={provisionAccount.isPending}>
              Tạo tài khoản
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={Boolean(statusTarget)} onOpenChange={(value) => !value && setStatusTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {statusTarget?.status === 'ACTIVE' ? 'Ngưng hoạt động bác sĩ' : 'Kích hoạt lại bác sĩ'}
            </DialogTitle>
            <DialogDescription>
              {statusTarget?.status === 'ACTIVE'
                ? 'Khi bác sĩ ngưng hoạt động, tài khoản đăng nhập của bác sĩ cũng sẽ tự chuyển sang trạng thái ngưng hoạt động để tránh đăng nhập sai quyền.'
                : 'Khi kích hoạt lại bác sĩ, tài khoản bác sĩ cũng sẽ được mở lại nếu đã tồn tại.'}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setStatusTarget(null)}>
              Hủy
            </Button>
            <Button onClick={() => void confirmToggleStatus()} disabled={updateDoctorStatus.isPending}>
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
              PrimeCare sẽ phát hành một liên kết đặt lại mật khẩu mới cho bác sĩ {resetTarget?.fullName}. Liên kết cũ sẽ bị thu hồi ngay sau khi tạo mới.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setResetTarget(null)}>
              Hủy
            </Button>
            <Button onClick={() => void handleResetPassword()} disabled={resetDoctorPassword.isPending}>
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
                ? 'PrimeCare đã tạo liên kết kích hoạt tài khoản cho bác sĩ ' + (resetResult?.doctorName || '') + '. Bạn có thể gửi đường dẫn này qua kênh nội bộ an toàn nếu hệ thống không giao tự động được.'
                : 'PrimeCare đã tạo liên kết đặt lại mật khẩu cho bác sĩ ' + (resetResult?.doctorName || '') + '. Bạn có thể gửi đường dẫn này qua kênh nội bộ an toàn nếu hệ thống không giao tự động được.'}
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
