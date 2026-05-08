import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { CheckCircle2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { PageHeader } from '@/components/PageHeader';
import { StatusBadge } from '@/components/StatusBadge';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { useCreateWalkIn, useReceptionPatientSearch } from '@/hooks/use-reception-data';
import {
  useAvailability,
  useBranchSpecialties,
  useBranches,
  useDoctors,
} from '@/hooks/use-public-data';
import { buildArrivedOptions, buildSessionOptions } from '@/lib/filter-options';
import { toLocalDateInputValue } from '@/lib/date';
import { useDebouncedValue } from '@/hooks/use-debounced-value';
import type { Appointment, BranchSessionType, Patient } from '@/types/api';

function getPatientCode(patient: Patient) {
  return patient.patientCode || patient.code || patient.id;
}

export default function WalkInPage() {
  const { t } = useTranslation();
  const today = toLocalDateInputValue();

  const [form, setForm] = useState({
    branchId: '',
    specialtyId: '',
    doctorId: '',
    visitDate: today,
    session: '',
    slotStart: '',
    patientFullName: '',
    patientPhone: '',
    patientEmail: '',
    patientDob: '',
    patientGender: '',
    patientAddress: '',
    patientNote: '',
    arrived: 'true',
  });
  const [createdAppointment, setCreatedAppointment] = useState<Appointment | null>(null);
  const [patientSearch, setPatientSearch] = useState('');
  const [selectedPatient, setSelectedPatient] = useState<Patient | null>(null);
  const debouncedPatientSearch = useDebouncedValue(patientSearch.trim(), 400);

  const { data: branches = [] } = useBranches();
  const { data: specialties = [] } = useBranchSpecialties(form.branchId);

  const shouldLoadDoctors = !!form.branchId && !!form.specialtyId;
  const doctorParams = useMemo(
    () =>
      shouldLoadDoctors
        ? {
            size: '100',
            ...(form.branchId ? { branchId: form.branchId } : {}),
            ...(form.specialtyId ? { specialtyId: form.specialtyId } : {}),
          }
        : undefined,
    [form.branchId, form.specialtyId, shouldLoadDoctors],
  );
  const { data: doctorsPage } = useDoctors(doctorParams);
  const doctors = useMemo(() => doctorsPage?.items ?? [], [doctorsPage?.items]);

  useEffect(() => {
    if (!form.specialtyId) return;
    if (specialties.some((item) => item.id === form.specialtyId)) return;
    setForm((current) => ({
      ...current,
      specialtyId: '',
      doctorId: '',
      slotStart: '',
    }));
  }, [form.specialtyId, specialties]);

  useEffect(() => {
    if (!form.doctorId) return;
    if (doctors.some((item) => item.id === form.doctorId)) return;
    setForm((current) => ({
      ...current,
      doctorId: '',
      slotStart: '',
    }));
  }, [doctors, form.doctorId]);

  const sessionOptions = useMemo(() => buildSessionOptions(t), [t]);
  const arrivedOptions = useMemo(() => buildArrivedOptions(t), [t]);

  const availabilityParams = useMemo(
    () =>
      form.branchId && form.specialtyId && form.doctorId && form.visitDate && form.session
        ? {
            branchId: form.branchId,
            specialtyId: form.specialtyId,
            doctorId: form.doctorId,
            visitDate: form.visitDate,
            session: form.session,
            onlyAvailable: 'true',
          }
        : undefined,
    [form.branchId, form.doctorId, form.session, form.specialtyId, form.visitDate],
  );
  const { data: slots = [], isFetching: isLoadingSlots } = useAvailability(availabilityParams);

  const createWalkIn = useCreateWalkIn();
  const {
    data: patientSearchResults = [],
    isFetching: isSearchingPatients,
  } = useReceptionPatientSearch(debouncedPatientSearch);

  const set = (key: string, value: string) => setForm((current) => ({ ...current, [key]: value }));

  const selectPatient = (patient: Patient) => {
    setSelectedPatient(patient);
    setForm((current) => ({
      ...current,
      patientFullName: patient.fullName || '',
      patientPhone: patient.phone || '',
      patientEmail: patient.email || '',
      patientDob: patient.dob || '',
      patientGender: patient.gender || '',
      patientAddress: patient.address || '',
    }));
  };

  const clearSelectedPatient = () => {
    setSelectedPatient(null);
    setPatientSearch('');
    setForm((current) => ({
      ...current,
      patientFullName: '',
      patientPhone: '',
      patientEmail: '',
      patientDob: '',
      patientGender: '',
      patientAddress: '',
    }));
  };

  const selectedBranch = useMemo(
    () => branches.find((item) => item.id === form.branchId),
    [branches, form.branchId],
  );
  const selectedSpecialty = useMemo(
    () => specialties.find((item) => item.id === form.specialtyId),
    [specialties, form.specialtyId],
  );
  const selectedDoctor = useMemo(
    () => doctors.find((item) => item.id === form.doctorId),
    [doctors, form.doctorId],
  );
  const selectedSessionLabel = useMemo(
    () => sessionOptions.find((item) => item.value === form.session)?.label || 'Chưa chọn',
    [form.session, sessionOptions],
  );
  const selectedArrivedLabel = useMemo(
    () => arrivedOptions.find((item) => item.value === form.arrived)?.label || 'Chưa chọn',
    [arrivedOptions, form.arrived],
  );
  const availableSlots = useMemo(
    () => slots.filter((slot) => slot.remainingSlots > 0),
    [slots],
  );
  const canSubmit = Boolean(
    form.branchId &&
      form.specialtyId &&
      form.doctorId &&
      form.visitDate &&
      form.session &&
      form.slotStart &&
      form.patientFullName &&
      form.patientPhone,
  );

  const submit = async () => {
    const created = await createWalkIn.mutateAsync({
      patientId: selectedPatient?.id,
      branchId: form.branchId,
      specialtyId: form.specialtyId,
      doctorId: form.doctorId,
      visitDate: form.visitDate,
      session: form.session as BranchSessionType,
      slotStart: form.slotStart,
      patientFullName: form.patientFullName,
      patientPhone: form.patientPhone,
      patientEmail: form.patientEmail || undefined,
      patientDob: form.patientDob || undefined,
      patientGender: form.patientGender || undefined,
      patientNote: form.patientNote || undefined,
      arrived: form.arrived === 'true',
    });

    setCreatedAppointment(created);
    setSelectedPatient(null);
    setPatientSearch('');
    setForm({
      branchId: '',
      specialtyId: '',
      doctorId: '',
      visitDate: today,
      session: '',
      slotStart: '',
      patientFullName: '',
      patientPhone: '',
      patientEmail: '',
      patientDob: '',
      patientGender: '',
      patientAddress: '',
      patientNote: '',
      arrived: 'true',
    });
  };

  return (
    <div className="space-y-4">
      <PageHeader
        title={t('modules.walkIn.title')}
        description="Tiếp nhận bệnh nhân walk-in nhanh, rõ ràng và hạn chế sai sót khi điều phối lịch khám."
      />

      {createdAppointment ? (
        <Card className="border-success/30 bg-success/5 shadow-sm">
          <CardContent className="flex flex-col gap-4 pt-6 lg:flex-row lg:items-center lg:justify-between">
            <div className="flex items-start gap-3">
              <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-success/10 text-success">
                <CheckCircle2 className="h-5 w-5" />
              </span>
              <div>
                <p className="font-semibold text-foreground">Đã tạo lượt walk-in</p>
                <div className="mt-3 grid gap-2 text-sm text-muted-foreground sm:grid-cols-2 lg:grid-cols-3">
                  <span>Mã lịch hẹn: {createdAppointment.code}</span>
                  <span>Số tiếp nhận: {createdAppointment.receptionQueueNo ?? createdAppointment.queueNo ?? '—'}</span>
                  <span>Bác sĩ: {createdAppointment.doctorName || '—'}</span>
                  <span>Chuyên khoa: {createdAppointment.specialtyName || '—'}</span>
                  <span>
                    Giờ khám: {createdAppointment.visitDate} {createdAppointment.slotStart || ''}
                  </span>
                  <span className="flex items-center gap-2">
                    Trạng thái: <StatusBadge status={createdAppointment.status} />
                  </span>
                </div>
              </div>
            </div>
            <div className="flex flex-wrap gap-2">
              <Button asChild>
                <Link to="/app/reception/queue">Mở hàng đợi</Link>
              </Button>
              <Button variant="outline" onClick={() => setCreatedAppointment(null)}>
                Tạo lượt mới
              </Button>
            </div>
          </CardContent>
        </Card>
      ) : null}

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,1.6fr)_360px]">
        <div className="space-y-4">
          <Card className="border-border/70 shadow-sm">
            <CardHeader>
              <CardTitle className="text-lg">Thông tin lượt khám</CardTitle>
              <CardDescription>
                Chọn cơ sở, chuyên khoa, bác sĩ và ca khám trước khi tiếp nhận bệnh nhân.
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="grid grid-cols-1 gap-4 md:grid-cols-4">
                <div>
                  <label className="mb-1.5 block text-sm font-medium">{t('common.branch')} *</label>
                  <Select
                    value={form.branchId}
                    onValueChange={(value) => {
                      set('branchId', value);
                      set('specialtyId', '');
                      set('doctorId', '');
                      set('slotStart', '');
                    }}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder={t('common.branch')} />
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
                  <label className="mb-1.5 block text-sm font-medium">{t('common.specialty')} *</label>
                  <Select
                    value={form.specialtyId}
                    onValueChange={(value) => {
                      set('specialtyId', value);
                      set('doctorId', '');
                      set('slotStart', '');
                    }}
                    disabled={!form.branchId}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder={t('common.specialty')} />
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

                <div>
                  <label className="mb-1.5 block text-sm font-medium">{t('common.doctor')} *</label>
                  <Select
                    value={form.doctorId}
                    onValueChange={(value) => {
                      set('doctorId', value);
                      set('slotStart', '');
                    }}
                    disabled={!shouldLoadDoctors}
                  >
                    <SelectTrigger>
                      <SelectValue
                        placeholder={
                          shouldLoadDoctors ? t('common.doctor') : t('filters.doctorHint')
                        }
                      />
                    </SelectTrigger>
                    <SelectContent>
                      {doctors.map((doctor) => (
                        <SelectItem key={doctor.id} value={doctor.id}>
                          {doctor.fullName}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                <div>
                  <label className="mb-1.5 block text-sm font-medium">Ngày khám *</label>
                  <Input
                    type="date"
                    value={form.visitDate}
                    min={today}
                    onChange={(e) => {
                      set('visitDate', e.target.value);
                      set('slotStart', '');
                    }}
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
                <div>
                  <label className="mb-1.5 block text-sm font-medium">
                    {t('modules.doctorSchedules.session')} *
                  </label>
                  <Select
                    value={form.session}
                    onValueChange={(value) => {
                      set('session', value);
                      set('slotStart', '');
                    }}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder={t('modules.doctorSchedules.session')} />
                    </SelectTrigger>
                    <SelectContent>
                      {sessionOptions.map((option) => (
                        <SelectItem key={option.value} value={option.value}>
                          {option.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                <div>
                  <label className="mb-1.5 block text-sm font-medium">Khung giờ *</label>
                  <Select
                    value={form.slotStart}
                    onValueChange={(value) => set('slotStart', value)}
                    disabled={!availabilityParams || isLoadingSlots || !availableSlots.length}
                  >
                    <SelectTrigger>
                      <SelectValue
                        placeholder={
                          !availabilityParams
                            ? 'Chọn đủ thông tin để xem khung giờ'
                            : isLoadingSlots
                              ? 'Đang tải khung giờ...'
                              : availableSlots.length
                                ? 'Chọn khung giờ'
                                : 'Không còn khung giờ trống'
                        }
                      />
                    </SelectTrigger>
                    <SelectContent>
                      {availableSlots.map((slot) => (
                        <SelectItem key={slot.id} value={slot.slotStart}>
                          {slot.slotStart} - {slot.slotEnd}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                <div>
                  <label className="mb-1.5 block text-sm font-medium">
                    {t('filters.walkInArrived.label')}
                  </label>
                  <Select value={form.arrived} onValueChange={(value) => set('arrived', value)}>
                    <SelectTrigger>
                      <SelectValue placeholder={t('filters.walkInArrived.label')} />
                    </SelectTrigger>
                    <SelectContent>
                      {arrivedOptions.map((option) => (
                        <SelectItem key={option.value} value={option.value}>
                          {option.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              </div>
            </CardContent>
          </Card>

          <Card className="border-border/70 shadow-sm">
            <CardHeader>
              <CardTitle className="text-lg">Thông tin bệnh nhân</CardTitle>
              <CardDescription>
                Nhập tối thiểu họ tên và số điện thoại để tạo lượt tiếp nhận thủ công.
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="rounded-lg border border-border/70 bg-muted/20 p-4">
                <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                  <div>
                    <p className="font-semibold text-foreground">Tìm bệnh nhân cũ</p>
                    <p className="mt-1 text-sm text-muted-foreground">
                      Tìm theo số điện thoại, tên hoặc mã bệnh nhân.
                    </p>
                  </div>
                  {selectedPatient ? (
                    <Badge className="w-fit rounded-full px-3 py-1">
                      Đã chọn hồ sơ bệnh nhân {getPatientCode(selectedPatient)}
                    </Badge>
                  ) : null}
                </div>

                <div className="mt-3 grid gap-3 md:grid-cols-[minmax(0,1fr)_auto]">
                  <Input
                    value={patientSearch}
                    onChange={(event) => setPatientSearch(event.target.value)}
                    placeholder="Nhập SĐT, tên hoặc mã bệnh nhân"
                  />
                  <Button type="button" variant="outline" onClick={clearSelectedPatient}>
                    Nhập bệnh nhân mới
                  </Button>
                </div>

                <div className="mt-3">
                  {debouncedPatientSearch.length < 2 ? (
                    <p className="text-sm text-muted-foreground">
                      Nhập thêm thông tin để tìm kiếm
                    </p>
                  ) : isSearchingPatients ? (
                    <p className="text-sm text-muted-foreground">Đang tìm bệnh nhân...</p>
                  ) : patientSearchResults.length === 0 ? (
                    <p className="text-sm text-muted-foreground">
                      Không tìm thấy bệnh nhân phù hợp
                    </p>
                  ) : (
                    <div className="grid gap-2">
                      {patientSearchResults.map((patient) => (
                        <button
                          key={patient.id}
                          type="button"
                          className="rounded-lg border border-border bg-card px-3 py-2 text-left transition-colors hover:border-primary/40 hover:bg-primary/5"
                          onClick={() => selectPatient(patient)}
                        >
                          <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
                            <p className="font-medium text-foreground">{patient.fullName}</p>
                            <span className="text-xs font-medium text-muted-foreground">
                              {getPatientCode(patient)}
                            </span>
                          </div>
                          <p className="mt-1 text-sm text-muted-foreground">
                            {patient.phone || 'Chưa có SĐT'}
                            {patient.dob ? ` · ${patient.dob}` : ''}
                            {patient.gender ? ` · ${patient.gender}` : ''}
                          </p>
                        </button>
                      ))}
                    </div>
                  )}
                </div>
              </div>

              <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                <div>
                  <label className="mb-1.5 block text-sm font-medium">{t('common.name')} *</label>
                  <Input
                    value={form.patientFullName}
                    onChange={(e) => set('patientFullName', e.target.value)}
                  />
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-medium">{t('common.phone')} *</label>
                  <Input
                    value={form.patientPhone}
                    onChange={(e) => set('patientPhone', e.target.value)}
                  />
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-medium">{t('common.email')}</label>
                  <Input
                    value={form.patientEmail}
                    onChange={(e) => set('patientEmail', e.target.value)}
                  />
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-medium">{t('modules.patients.dob')}</label>
                  <Input
                    type="date"
                    value={form.patientDob}
                    onChange={(e) => set('patientDob', e.target.value)}
                  />
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-medium">{t('common.gender')}</label>
                  <Select
                    value={form.patientGender}
                    onValueChange={(value) => set('patientGender', value)}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder={t('common.gender')} />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="MALE">{t('common.male')}</SelectItem>
                      <SelectItem value="FEMALE">{t('common.female')}</SelectItem>
                      <SelectItem value="OTHER">{t('common.other')}</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div className="md:col-span-2">
                  <label className="mb-1.5 block text-sm font-medium">Địa chỉ</label>
                  <Input
                    value={form.patientAddress}
                    onChange={(e) => set('patientAddress', e.target.value)}
                  />
                </div>
              </div>

              <div>
                <label className="mb-1.5 block text-sm font-medium">{t('common.note')}</label>
                <Textarea
                  value={form.patientNote}
                  onChange={(e) => set('patientNote', e.target.value)}
                  rows={4}
                  placeholder="Lý do khám, giấy tờ/BHYT hoặc ghi chú tiếp nhận"
                />
              </div>
            </CardContent>
          </Card>

          <div className="flex justify-end">
            <Button onClick={submit} disabled={createWalkIn.isPending || !canSubmit}>
              {createWalkIn.isPending ? 'Đang tạo lượt khám...' : 'Tạo lượt walk-in'}
            </Button>
          </div>
        </div>

        <div className="space-y-4">
          <Card className="border-border/70 shadow-sm">
            <CardHeader>
              <CardTitle className="text-lg">Tóm tắt tiếp nhận</CardTitle>
              <CardDescription>
                Kiểm tra nhanh thông tin trước khi tạo lượt khám cho bệnh nhân.
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-3 text-sm">
              <SummaryRow label="Cơ sở" value={selectedBranch?.name || 'Chưa chọn'} />
              <SummaryRow label="Chuyên khoa" value={selectedSpecialty?.name || 'Chưa chọn'} />
              <SummaryRow label="Bác sĩ" value={selectedDoctor?.fullName || 'Chưa chọn'} />
              <SummaryRow label="Ngày khám" value={form.visitDate || 'Chưa chọn'} />
              <SummaryRow label="Buổi" value={selectedSessionLabel} />
              <SummaryRow label="Khung giờ" value={form.slotStart || 'Chưa chọn'} />
              <div className="rounded-2xl border border-border/70 bg-muted/30 p-3">
                <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  Trạng thái đến khám
                </p>
                <Badge className="mt-2 w-fit rounded-full px-3 py-1">{selectedArrivedLabel}</Badge>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}

function SummaryRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-2xl border border-border/70 bg-muted/25 p-3">
      <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</p>
      <p className="mt-1 font-medium text-foreground">{value}</p>
    </div>
  );
}
