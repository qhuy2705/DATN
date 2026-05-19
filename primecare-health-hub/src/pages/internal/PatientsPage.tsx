import { useMemo, useState } from 'react';
import { Plus, SquarePen } from 'lucide-react';
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { useTranslation } from 'react-i18next';
import { DataTable, type Column } from '@/components/DataTable';
import { PageHeader } from '@/components/PageHeader';
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
  useAdminPatients,
  useSavePatient,
} from '@/hooks/use-admin-data';
import type { Patient } from '@/types/api';
import { PatientAllergyModal } from '@/components/patient/PatientAllergyModal';
import { PatientTimelineModal } from '@/components/patient/PatientTimelineModal';

export default function PatientsPage() {
  const { t } = useTranslation();
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('__all__');
  const [page, setPage] = useState(1);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<Patient | null>(null);
  const [form, setForm] = useState({
    fullName: '',
    phone: '',
    email: '',
    dob: '',
    gender: 'MALE',
    bloodType: '',
    ethnicity: '',
    nationality: '',
    occupation: '',
    province: '',
    district: '',
    ward: '',
    address: '',
    insuranceExpiryDate: '',
    insuranceRegisteredHospital: '',
  });
  const [allergyPatientId, setAllergyPatientId] = useState<string | null>(null);
  const [timelinePatient, setTimelinePatient] = useState<Patient | null>(null);

  const { data, isLoading } = useAdminPatients({
    page: String(page - 1),
    size: '20',
    ...(search.trim() ? { q: search.trim() } : {}),
    ...(statusFilter !== '__all__' ? { status: statusFilter } : {}),
  });
  const savePatient = useSavePatient();

  const rows = useMemo(() => data?.items ?? [], [data?.items]);

  const openCreate = () => {
    setEditing(null);
    setForm({
      fullName: '',
      phone: '',
      email: '',
      dob: '',
      gender: 'MALE',
      bloodType: '',
      ethnicity: '',
      nationality: '',
      occupation: '',
      province: '',
      district: '',
      ward: '',
      address: '',
      insuranceExpiryDate: '',
      insuranceRegisteredHospital: '',
    });
    setOpen(true);
  };

  const openEdit = (row: Patient) => {
    setEditing(row);
    setForm({
      fullName: row.fullName ?? '',
      phone: row.phone ?? '',
      email: row.email ?? '',
      dob: row.dob ?? '',
      gender: row.gender ?? 'MALE',
      bloodType: row.bloodType ?? '',
      ethnicity: row.ethnicity ?? '',
      nationality: row.nationality ?? '',
      occupation: row.occupation ?? '',
      province: row.province ?? '',
      district: row.district ?? '',
      ward: row.ward ?? '',
      address: row.address ?? '',
      insuranceExpiryDate: row.insuranceExpiryDate ?? '',
      insuranceRegisteredHospital: row.insuranceRegisteredHospital ?? '',
    });
    setOpen(true);
  };

  const submit = async () => {
    const preservedFields = editing
      ? {
          avatarUrl: editing.avatarUrl,
          identityNumber: editing.identityNumber,
          insuranceNumber: editing.insuranceNumber,
          emergencyContactName: editing.emergencyContactName,
          emergencyContactPhone: editing.emergencyContactPhone,
          allergyNote: editing.allergyNote,
          chronicDiseaseNote: editing.chronicDiseaseNote,
          note: editing.note,
        }
      : {};

    await savePatient.mutateAsync({
      id: editing?.id,
      body: {
        ...preservedFields,
        ...form,
      },
    });
    setOpen(false);
  };

  const columns: Column<Patient>[] = [
    {
      key: 'fullName',
      header: t('common.name'),
      cell: (r) => (
        <div>
          <p className="font-medium">{r.fullName}</p>
          <p className="text-xs text-muted-foreground">{r.code || '-'}</p>
        </div>
      ),
    },
    { key: 'phone', header: t('common.phone') },
    {
      key: 'email',
      header: t('common.email'),
      cell: (r) => <span>{r.email || '—'}</span>,
    },
    { key: 'dob', header: t('modules.patients.dob') },
    {
      key: 'gender',
      header: t('common.gender'),
      cell: (r) => <span>{r.gender || '—'}</span>,
    },
    {
      key: 'createdAt',
      header: t('common.createdAt'),
      cell: (r) => <span>{r.createdAt ? new Date(r.createdAt).toLocaleString('vi-VN') : '-'}</span>,
    },
  ];

  return (
    <div>
      <PageHeader
        title={t('modules.patients.title')}
        description={t('modules.patients.desc')}
        actions={
          <Button onClick={openCreate}>
            <Plus className="h-4 w-4 mr-2" />
            Thêm bệnh nhân
          </Button>
        }
      />
      <DataTable
        columns={columns}
        data={rows}
        searchValue={search}
        onSearchChange={setSearch}
        searchPlaceholder="Tìm theo mã BN, tên, SĐT, email..."
        toolbar={
          <Select value={statusFilter} onValueChange={setStatusFilter}>
            <SelectTrigger className="w-[180px]">
              <SelectValue placeholder="Trạng thái" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="__all__">Tất cả trạng thái</SelectItem>
              <SelectItem value="ACTIVE">ACTIVE</SelectItem>
              <SelectItem value="INACTIVE">INACTIVE</SelectItem>
            </SelectContent>
          </Select>
        }
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
                    onClick={() => setAllergyPatientId(r.id)}
                    className="h-9 w-9 rounded-xl border-warning/20 bg-warning/10 text-warning hover:bg-warning/20 hover:text-warning"
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m10.5 20.5 10-10a4.95 4.95 0 1 0-7-7l-10 10a4.95 4.95 0 1 0 7 7Z"/><path d="m8.5 8.5 7 7"/></svg>
                  </Button>
                </TooltipTrigger>
                <TooltipContent>Dị ứng</TooltipContent>
              </Tooltip>
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button
                    variant="outline"
                    size="icon"
                    onClick={() => setTimelinePatient(r as Patient)}
                    className="h-9 w-9 rounded-xl border-primary/20 bg-primary/10 text-primary hover:bg-primary/20 hover:text-primary"
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M12 2v20"/><path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/></svg>
                  </Button>
                </TooltipTrigger>
                <TooltipContent>Lịch sử khám bệnh</TooltipContent>
              </Tooltip>
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button
                    variant="outline"
                    size="icon"
                    onClick={() => openEdit(r as Patient)}
                    className="h-9 w-9 rounded-xl border-border/70 bg-background hover:bg-muted"
                  >
                    <SquarePen className="h-4 w-4" />
                  </Button>
                </TooltipTrigger>
                <TooltipContent>Sửa bệnh nhân</TooltipContent>
              </Tooltip>
            </div>
          </TooltipProvider>
        )}
      />

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="max-w-2xl max-h-[90vh] flex flex-col">
          <DialogHeader>
            <DialogTitle>{editing ? 'Cập nhật bệnh nhân' : 'Tạo bệnh nhân'}</DialogTitle>
          </DialogHeader>
          <div className="overflow-y-auto pr-4 -mr-4 flex-1">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="text-sm font-medium mb-1.5 block">Họ tên</label>
                <Input value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} />
              </div>
              <div>
                <label className="text-sm font-medium mb-1.5 block">Số điện thoại</label>
                <Input value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
              </div>
              <div>
                <label className="text-sm font-medium mb-1.5 block">Email</label>
                <Input value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
              </div>
              <div>
                <label className="text-sm font-medium mb-1.5 block">Ngày sinh</label>
                <Input type="date" value={form.dob} onChange={(e) => setForm({ ...form, dob: e.target.value })} />
              </div>
              <div>
                <label className="text-sm font-medium mb-1.5 block">Giới tính</label>
                <Select value={form.gender} onValueChange={(val) => setForm({ ...form, gender: val })}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="MALE">Nam</SelectItem>
                    <SelectItem value="FEMALE">Nữ</SelectItem>
                    <SelectItem value="OTHER">Khác</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div>
                <label className="text-sm font-medium mb-1.5 block">Nhóm máu</label>
                <Select value={form.bloodType} onValueChange={(val) => setForm({ ...form, bloodType: val })}>
                  <SelectTrigger><SelectValue placeholder="Chọn nhóm máu" /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="A_PLUS">A+</SelectItem>
                    <SelectItem value="A_MINUS">A-</SelectItem>
                    <SelectItem value="B_PLUS">B+</SelectItem>
                    <SelectItem value="B_MINUS">B-</SelectItem>
                    <SelectItem value="AB_PLUS">AB+</SelectItem>
                    <SelectItem value="AB_MINUS">AB-</SelectItem>
                    <SelectItem value="O_PLUS">O+</SelectItem>
                    <SelectItem value="O_MINUS">O-</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div>
                <label className="text-sm font-medium mb-1.5 block">Dân tộc</label>
                <Input value={form.ethnicity} onChange={(e) => setForm({ ...form, ethnicity: e.target.value })} />
              </div>
              <div>
                <label className="text-sm font-medium mb-1.5 block">Quốc tịch</label>
                <Input value={form.nationality} onChange={(e) => setForm({ ...form, nationality: e.target.value })} />
              </div>
              <div>
                <label className="text-sm font-medium mb-1.5 block">Nghề nghiệp</label>
                <Input value={form.occupation} onChange={(e) => setForm({ ...form, occupation: e.target.value })} />
              </div>
              <div>
                <label className="text-sm font-medium mb-1.5 block">Tỉnh/Thành phố</label>
                <Input value={form.province} onChange={(e) => setForm({ ...form, province: e.target.value })} />
              </div>
              <div>
                <label className="text-sm font-medium mb-1.5 block">Quận/Huyện</label>
                <Input value={form.district} onChange={(e) => setForm({ ...form, district: e.target.value })} />
              </div>
              <div>
                <label className="text-sm font-medium mb-1.5 block">Phường/Xã</label>
                <Input value={form.ward} onChange={(e) => setForm({ ...form, ward: e.target.value })} />
              </div>
              <div className="md:col-span-2">
                <label className="text-sm font-medium mb-1.5 block">Số nhà / Đường</label>
                <Input value={form.address} onChange={(e) => setForm({ ...form, address: e.target.value })} />
              </div>
              <div>
                <label className="text-sm font-medium mb-1.5 block">Nơi ĐK KCB BHYT ban đầu</label>
                <Input value={form.insuranceRegisteredHospital} onChange={(e) => setForm({ ...form, insuranceRegisteredHospital: e.target.value })} />
              </div>
              <div>
                <label className="text-sm font-medium mb-1.5 block">Hạn BHYT</label>
                <Input type="date" value={form.insuranceExpiryDate} onChange={(e) => setForm({ ...form, insuranceExpiryDate: e.target.value })} />
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)}>
              Hủy
            </Button>
            <Button onClick={submit} disabled={savePatient.isPending}>
              Lưu
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <PatientAllergyModal
        patientId={allergyPatientId}
        open={!!allergyPatientId}
        onOpenChange={(isOpen) => !isOpen && setAllergyPatientId(null)}
      />

      {timelinePatient && (
        <PatientTimelineModal
          patientId={timelinePatient.id}
          patientName={timelinePatient.fullName}
          open={!!timelinePatient}
          onOpenChange={(isOpen) => !isOpen && setTimelinePatient(null)}
        />
      )}
    </div>
  );
}
