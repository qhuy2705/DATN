import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
  CalendarDays,
  Check,
  ChevronLeft,
  ChevronRight,
  CircleHelp,
  Hospital,
  MapPin,
  ShieldCheck,
  Stethoscope,
  User,
} from 'lucide-react';
import { format, parseISO } from 'date-fns';
import { enUS, vi } from 'date-fns/locale';
import { toast } from 'sonner';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { Calendar } from '@/components/ui/calendar';
import { ScrollReveal } from '@/components/ScrollReveal';
import {
  usePublicAvailabilityRealtime,
  useBranchSpecialties,
  useBranches,
  useCreatePublicAppointment,
  useDoctor,
  useDoctors,
} from '@/hooks/use-public-data';
import { useCurrentUser } from '@/hooks/use-auth';
import { usePatientProfile } from '@/hooks/use-patient-portal';
import { getApiErrorMessage } from '@/lib/error-utils';
import { toLocalDateInputValue } from '@/lib/date';
import { cn } from '@/lib/utils';

const steps = [
  { labelVi: 'Chọn dịch vụ', labelEn: 'Care team', icon: Stethoscope },
  { labelVi: 'Chọn ngày & giờ', labelEn: 'Time', icon: CalendarDays },
  { labelVi: 'Thông tin bệnh nhân', labelEn: 'Details', icon: User },
  { labelVi: 'Kiểm tra lại', labelEn: 'Review', icon: Check },
];

type PatientForm = {
  patientFullName: string;
  patientPhone: string;
  patientEmail?: string;
  patientDob: string;
  patientGender: string;
  visitType: string;
  reasonForVisit?: string;
  patientNote?: string;
};

type PrefillPatientField =
  | 'patientFullName'
  | 'patientPhone'
  | 'patientEmail'
  | 'patientDob'
  | 'patientGender';

function isPatientAccount(user: { role?: string; roles?: string[] } | null | undefined) {
  return user?.role === 'PATIENT' || user?.roles?.includes('PATIENT') === true;
}

function normalizeProfileText(value?: string | null) {
  const trimmed = value?.trim();
  return trimmed || undefined;
}

function normalizeProfileDate(value?: string | null) {
  return normalizeProfileText(value)?.slice(0, 10);
}

function getPatientProfileDob(profile?: { dob?: string; dateOfBirth?: string } | null) {
  return normalizeProfileDate(profile?.dob ?? profile?.dateOfBirth);
}

function visitTypeLabel(value: string, isEn: boolean) {
  switch (value) {
    case 'FOLLOW_UP':
      return isEn ? 'Follow-up' : 'Tái khám';
    case 'CONSULTATION':
      return isEn ? 'Consultation' : 'Tư vấn';
    case 'NEW_PATIENT':
    default:
      return isEn ? 'New patient' : 'Khám mới';
  }
}

function formatDisplayDate(value?: string, isEn?: boolean) {
  if (!value) return isEn ? 'Not selected yet' : 'Chưa chọn';
  try {
    return format(parseISO(value), isEn ? 'EEEE, dd/MM/yyyy' : 'EEEE, dd/MM/yyyy', {
      locale: isEn ? enUS : vi,
    });
  } catch {
    return value;
  }
}

function formatCurrencyVnd(value?: number) {
  if (typeof value !== 'number' || Number.isNaN(value)) return null;
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(value);
}

function StepCard({
  index,
  step,
  activeStep,
  isEn,
}: {
  index: number;
  step: (typeof steps)[number];
  activeStep: number;
  isEn: boolean;
}) {
  const Icon = step.icon;
  const isCompleted = index < activeStep;
  const isActive = index === activeStep;

  return (
    <div
      className={cn(
        'rounded-2xl border bg-card px-4 py-4 text-card-foreground transition-all',
        isActive && 'border-primary shadow-[0_0_0_1px_hsl(var(--primary))]',
        isCompleted && 'border-primary/20 bg-primary/5',
        !isActive && !isCompleted && 'border-border/70 bg-muted/20 text-muted-foreground',
      )}
    >
      <div className="flex items-center gap-3">
        <div
          className={cn(
            'flex h-11 w-11 items-center justify-center rounded-full border',
            isActive && 'border-primary bg-primary text-primary-foreground',
            isCompleted && 'border-primary/20 bg-primary/10 text-primary',
            !isActive && !isCompleted && 'border-border bg-muted/40 text-muted-foreground',
          )}
        >
          {isCompleted ? <Check className="h-5 w-5" /> : <Icon className="h-5 w-5" />}
        </div>
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">
            {isEn ? `Step ${index + 1}` : `Bước ${index + 1}`}
          </p>
          <p className={cn('text-base font-semibold', isActive || isCompleted ? 'text-foreground' : 'text-muted-foreground')}>
            {isEn ? step.labelEn : step.labelVi}
          </p>
        </div>
      </div>
    </div>
  );
}

function SummaryRow({ label, value, accent = false }: { label: string; value?: string; accent?: boolean }) {
  return (
    <div className="flex flex-col gap-1 rounded-xl border border-border/60 bg-muted/20 px-4 py-3 sm:flex-row sm:items-start sm:justify-between sm:gap-3">
      <span className="text-sm text-muted-foreground">{label}</span>
      <span className={cn('break-words text-sm font-medium text-foreground sm:max-w-[60%] sm:text-right', accent && 'text-primary')}>
        {value || '—'}
      </span>
    </div>
  );
}

function getContinueLabel(step: number, isEn: boolean) {
  switch (step) {
    case 0:
      return isEn ? 'Choose time' : 'Chọn giờ khám';
    case 1:
      return isEn ? 'Add details' : 'Nhập thông tin';
    case 2:
      return isEn ? 'Review request' : 'Xem lại yêu cầu';
    default:
      return isEn ? 'Continue' : 'Tiếp tục';
  }
}

export default function BookingPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { i18n } = useTranslation();
  const isEn = i18n.language.startsWith('en');
  const calendarLocale = isEn ? enUS : vi;
  const currentUser = useCurrentUser();
  const isPatientUser = isPatientAccount(currentUser);
  const didPrefillPatientRef = useRef<string | null>(null);

  const patientSchema = useMemo(
    () =>
      z.object({
        patientFullName: z
          .string()
          .min(2, isEn ? 'Please enter at least 2 characters.' : 'Họ tên tối thiểu 2 ký tự')
          .max(100),
        patientPhone: z.string().regex(/^(\+84|84|0)(3|5|7|8|9)\d{8}$/, isEn ? 'Invalid Vietnam phone number.' : 'Số điện thoại không hợp lệ'),
        patientEmail: z
          .string()
          .email(isEn ? 'Invalid email address.' : 'Email không hợp lệ')
          .or(z.literal(''))
          .optional(),
        patientDob: z.string().min(1, isEn ? 'Please select date of birth.' : 'Vui lòng chọn ngày sinh'),
        patientGender: z.string().min(1, isEn ? 'Please select gender.' : 'Vui lòng chọn giới tính'),
        visitType: z.string().min(1, isEn ? 'Please select visit type.' : 'Vui lòng chọn loại lượt khám'),
        reasonForVisit: z.string().max(1000).optional(),
        patientNote: z.string().max(500).optional(),
      }),
    [isEn],
  );

  const [step, setStep] = useState(0);
  const [branchId, setBranchId] = useState(searchParams.get('branchId') ?? '');
  const [specialtyId, setSpecialtyId] = useState(searchParams.get('specialtyId') ?? '');
  const [doctorId, setDoctorId] = useState(searchParams.get('doctorId') ?? '');
  const [visitDate, setVisitDate] = useState(searchParams.get('date') ?? '');
  const [session, setSession] = useState(searchParams.get('session') ?? 'AM');
  const [slotStart, setSlotStart] = useState(searchParams.get('slot') ?? '');
  const today = toLocalDateInputValue();

  const form = useForm<PatientForm>({
    resolver: zodResolver(patientSchema),
    mode: 'onChange',
    defaultValues: {
      patientFullName: '',
      patientPhone: '',
      patientEmail: '',
      patientDob: '',
      patientGender: '',
      visitType: '',
      reasonForVisit: '',
      patientNote: '',
    },
  });

  const {
    data: patientProfile,
    isLoading: isPatientProfileLoading,
    isError: isPatientProfileError,
  } = usePatientProfile(isPatientUser);
  const isPatientProfileForCurrentUser =
    Boolean(patientProfile) &&
    (!currentUser?.patientId || patientProfile?.id === currentUser.patientId);
  const usablePatientProfile = isPatientProfileForCurrentUser ? patientProfile : undefined;
  const patientProfileDob = getPatientProfileDob(usablePatientProfile);
  const patientProfileFieldValues = {
    fullName: normalizeProfileText(usablePatientProfile?.fullName),
    phone: normalizeProfileText(usablePatientProfile?.phone),
    email: normalizeProfileText(usablePatientProfile?.email),
    dob: patientProfileDob,
    gender: normalizeProfileText(usablePatientProfile?.gender),
  };
  const watchedPatientFullName = form.watch('patientFullName');
  const watchedPatientPhone = form.watch('patientPhone');
  const watchedPatientEmail = form.watch('patientEmail');
  const watchedPatientDob = form.watch('patientDob');
  const watchedPatientGender = form.watch('patientGender');
  const patientProfileFields = {
    fullName: Boolean(patientProfileFieldValues.fullName && watchedPatientFullName === patientProfileFieldValues.fullName),
    phone: Boolean(patientProfileFieldValues.phone && watchedPatientPhone === patientProfileFieldValues.phone),
    email: Boolean(patientProfileFieldValues.email && watchedPatientEmail === patientProfileFieldValues.email),
    dob: Boolean(patientProfileFieldValues.dob && watchedPatientDob === patientProfileFieldValues.dob),
    gender: Boolean(patientProfileFieldValues.gender && watchedPatientGender === patientProfileFieldValues.gender),
  };
  const isPatientProfilePending =
    isPatientUser && (isPatientProfileLoading || (Boolean(patientProfile) && !isPatientProfileForCurrentUser));

  const { data: branches = [] } = useBranches();
  const {
    data: branchSpecialties = [],
    isLoading: branchSpecialtiesLoading,
    isFetching: branchSpecialtiesFetching,
  } = useBranchSpecialties(branchId);
  const { data: doctorDetail } = useDoctor(doctorId);
  const doctorListParams = useMemo(
    () => ({
      page: '0',
      size: '1000',
      ...(branchId ? { branchId } : {}),
      ...(specialtyId ? { specialtyId } : {}),
    }),
    [branchId, specialtyId],
  );
  const { data: doctorsPage } = useDoctors(doctorListParams);
  const doctors = useMemo(() => doctorsPage?.items ?? [], [doctorsPage?.items]);
  const availabilityParams = useMemo(() => {
    if (!branchId || !specialtyId || !doctorId || !visitDate || !session) return undefined;
    return { branchId, specialtyId, doctorId, visitDate, session, onlyAvailable: 'true' };
  }, [branchId, specialtyId, doctorId, visitDate, session]);
  const {
    data: slots = [],
    isLoading: availabilityLoading,
    isError: availabilityError,
    isPlaceholderData: availabilityPlaceholder,
    isLiveConnected,
    lastSyncAt,
  } = usePublicAvailabilityRealtime(availabilityParams);
  const createAppointment = useCreatePublicAppointment();

  useEffect(() => {
    if (!branchId || branchSpecialtiesLoading || branchSpecialtiesFetching) return;
    if (!specialtyId) return;
    if (branchSpecialties.some((item) => item.id === specialtyId)) return;
    setSpecialtyId('');
    setDoctorId('');
    setVisitDate('');
    setSlotStart('');
  }, [branchId, branchSpecialties, branchSpecialtiesFetching, branchSpecialtiesLoading, specialtyId]);

  useEffect(() => {
    if (!doctorId) return;
    if (doctors.some((item) => item.id === doctorId) || doctorDetail?.id === doctorId) return;
    setDoctorId('');
    setVisitDate('');
    setSlotStart('');
  }, [doctorDetail?.id, doctorId, doctors]);

  useEffect(() => {
    if (!doctorDetail) return;
    if (!branchId && doctorDetail.branchId) setBranchId(doctorDetail.branchId);
    const preferredSpecialty = doctorDetail.specialtyId || doctorDetail.specialtyIds?.[0];
    if (!specialtyId && preferredSpecialty) setSpecialtyId(preferredSpecialty);
  }, [doctorDetail, branchId, specialtyId]);

  useEffect(() => {
    if (!slotStart) return;
    if (slots.some((slot) => slot.slotStart === slotStart)) return;
    if (slots.length > 0) setSlotStart(slots[0].slotStart);
  }, [slotStart, slots]);

  useEffect(() => {
    if (!isPatientUser) {
      didPrefillPatientRef.current = null;
      return;
    }

    if (!usablePatientProfile) return;

    const prefillKey = currentUser?.id ?? usablePatientProfile.id;
    if (didPrefillPatientRef.current === prefillKey) return;

    const current = form.getValues();
    const pickProfileValue = (field: PrefillPatientField, value?: string) => {
      const normalized =
        field === 'patientDob' ? normalizeProfileDate(value) : normalizeProfileText(value);

      if (!normalized) return current[field] ?? '';
      return form.getFieldState(field).isDirty ? current[field] ?? '' : normalized;
    };

    form.reset({
      ...current,
      patientFullName: pickProfileValue('patientFullName', usablePatientProfile.fullName),
      patientPhone: pickProfileValue('patientPhone', usablePatientProfile.phone),
      patientEmail: pickProfileValue('patientEmail', usablePatientProfile.email),
      patientDob: pickProfileValue('patientDob', patientProfileDob),
      patientGender: pickProfileValue('patientGender', usablePatientProfile.gender),
    });

    didPrefillPatientRef.current = prefillKey;
  }, [currentUser?.id, form, isPatientUser, patientProfileDob, usablePatientProfile]);

  const selectedBranch = branches.find((branch) => branch.id === branchId);
  const selectedSpecialty = branchSpecialties.find((item) => item.id === specialtyId);
  const selectedDoctor = doctors.find((item) => item.id === doctorId) ?? doctorDetail;
  const selectedSlot = slots.find((item) => item.slotStart === slotStart);
  const consultationFee = selectedSpecialty?.consultationFee;
  const formattedConsultationFee = formatCurrencyVnd(consultationFee);
  const consultationFeeValue = formattedConsultationFee
    ?? (selectedSpecialty
      ? isEn
        ? 'Updating'
        : 'Đang cập nhật'
      : isEn
        ? 'Choose specialty'
        : 'Chọn chuyên khoa');

  const canNext = () => {
    if (step === 0) return Boolean(branchId && specialtyId && doctorId);
    if (step === 1) return Boolean(visitDate && session && slotStart);
    if (step === 2) return form.formState.isValid;
    return true;
  };

  const handleSubmit = async () => {
    const values = form.getValues();
    try {
      const appointment = await createAppointment.mutateAsync({
        branchId,
        specialtyId,
        doctorId,
        visitDate,
        session,
        slotStart,
        patientFullName: values.patientFullName,
        patientPhone: values.patientPhone,
        patientEmail: values.patientEmail ?? '',
        patientDob: values.patientDob,
        patientGender: values.patientGender,
        visitType: values.visitType,
        reasonForVisit: values.reasonForVisit,
        patientNote: values.patientNote,
      });

      toast.success(isEn ? 'Appointment request submitted successfully.' : 'Gửi yêu cầu đặt lịch thành công!');
      navigate('/booking/success', {
        state: {
          appointment,
          branchName: selectedBranch?.name,
          specialtyName: selectedSpecialty?.name,
          doctorName: selectedDoctor?.fullName,
          slotEnd: selectedSlot?.slotEnd,
        },
      });
    } catch (error: unknown) {
      toast.error(
        getApiErrorMessage(
          error,
          isEn
            ? 'Unable to create appointment. Please review the selected slot.'
            : 'Không thể đặt lịch. Vui lòng kiểm tra lại khung giờ đã chọn.',
        ),
      );
    }
  };

  const selectionStep = (
    <div className="space-y-6">
      <div className="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-3">
        <div className="xl:col-span-1">
          <label className="mb-2 block text-sm font-medium text-foreground">
            {isEn ? 'Branch' : 'Cơ sở'} <span className="text-destructive">*</span>
          </label>
          <Select
            value={branchId}
            onValueChange={(value) => {
              setBranchId(value);
              setSpecialtyId('');
              setDoctorId('');
              setVisitDate('');
              setSlotStart('');
            }}
          >
            <SelectTrigger className="h-12 rounded-2xl border-border/70 bg-background">
              <SelectValue placeholder={isEn ? 'Select branch' : 'Chọn cơ sở'} />
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

        <div className="xl:col-span-1">
          <label className="mb-2 block text-sm font-medium text-foreground">
            {isEn ? 'Specialty' : 'Chuyên khoa'} <span className="text-destructive">*</span>
          </label>
          <Select
            value={specialtyId}
            onValueChange={(value) => {
              setSpecialtyId(value);
              setDoctorId('');
              setVisitDate('');
              setSlotStart('');
            }}
            disabled={!branchId}
          >
            <SelectTrigger className="h-12 rounded-2xl border-border/70 bg-background">
              <SelectValue placeholder={isEn ? 'Select specialty' : 'Chọn chuyên khoa'} />
            </SelectTrigger>
            <SelectContent>
              {branchSpecialties.map((specialty) => (
                <SelectItem key={specialty.id} value={specialty.id}>
                  {specialty.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="xl:col-span-1">
          <label className="mb-2 block text-sm font-medium text-foreground">
            {isEn ? 'Doctor' : 'Bác sĩ'} <span className="text-destructive">*</span>
          </label>
          <Select
            value={doctorId}
            onValueChange={(value) => {
              setDoctorId(value);
              setVisitDate('');
              setSlotStart('');
            }}
            disabled={!specialtyId}
          >
            <SelectTrigger className="h-12 rounded-2xl border-border/70 bg-background">
              <SelectValue placeholder={isEn ? 'Select doctor' : 'Chọn bác sĩ'} />
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
      </div>

      <div className="grid gap-5 lg:grid-cols-[1.25fr_0.75fr]">
        <div className="rounded-[28px] border border-border/70 bg-card p-6 text-card-foreground">
          <div className="flex items-start justify-between gap-4 border-b border-border/60 pb-5">
            <div>
              <p className="text-xs font-semibold uppercase tracking-[0.2em] text-primary">
                {isEn ? 'Your care path' : 'Lộ trình đang chọn'}
              </p>
              <h2 className="mt-2 text-2xl font-semibold text-foreground">
                {selectedSpecialty?.name || (isEn ? 'Choose a specialty' : 'Chọn chuyên khoa phù hợp')}
              </h2>
              <p className="mt-2 max-w-2xl text-sm leading-6 text-muted-foreground">
                {isEn
                  ? 'Start with the clinic, specialty, and doctor you want to see. The next screen will show only live slots that are still available.'
                  : 'Bắt đầu bằng cách chọn cơ sở, chuyên khoa và bác sĩ phù hợp. Màn hình tiếp theo chỉ hiển thị các khung giờ còn trống.'}
              </p>
            </div>
            <div className="hidden rounded-2xl bg-primary/5 p-3 text-primary md:block">
              <Hospital className="h-6 w-6" />
            </div>
          </div>

          {selectedDoctor ? (
            <div className="grid gap-4 pt-5 md:grid-cols-3">
              <div className="rounded-2xl border border-border/60 bg-muted/15 p-4">
                <p className="text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">
                  {isEn ? 'Doctor' : 'Bác sĩ'}
                </p>
                <p className="mt-2 text-lg font-semibold text-foreground">{selectedDoctor.fullName}</p>
                <p className="mt-1 text-sm text-muted-foreground">{selectedDoctor.title || '—'}</p>
              </div>
              <div className="rounded-2xl border border-border/60 bg-muted/15 p-4">
                <p className="text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">
                  {isEn ? 'Specialty' : 'Chuyên khoa'}
                </p>
                <p className="mt-2 text-lg font-semibold text-foreground">{selectedDoctor.specialtyName || selectedSpecialty?.name || '—'}</p>
                <p className="mt-1 text-sm text-muted-foreground">{selectedDoctor.branchName || selectedBranch?.name || '—'}</p>
              </div>
              <div className="rounded-2xl border border-border/60 bg-muted/15 p-4">
                <p className="text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">
                  {isEn ? 'Initial consultation fee' : 'Phí khám ban đầu'}
                </p>
                <p className="mt-2 text-lg font-semibold text-foreground">{consultationFeeValue}</p>
                <p className="mt-1 text-sm text-muted-foreground">
                  {selectedSpecialty
                    ? isEn
                      ? 'Applies to the selected specialty at this branch. Additional services are billed separately if needed.'
                      : 'Áp dụng cho chuyên khoa đã chọn tại cơ sở này. Dịch vụ phát sinh sẽ được tính riêng nếu có.'
                    : isEn
                      ? 'Displayed after you choose a branch and specialty.'
                      : 'Hiển thị sau khi bạn chọn cơ sở và chuyên khoa.'}
                </p>
              </div>
            </div>
          ) : (
            <div className="rounded-2xl border border-dashed border-border/70 bg-muted/10 px-6 py-10 text-center text-sm text-muted-foreground">
              {isEn
                ? 'Select enough information to preview the doctor and booking details.'
                : 'Hãy chọn đủ thông tin để xem trước bác sĩ và chi tiết lịch hẹn.'}
            </div>
          )}
        </div>

        <div className="rounded-[28px] border border-border/70 bg-card p-6 text-card-foreground">
          <div className="flex items-center gap-2 text-sm font-semibold text-primary">
            <ShieldCheck className="h-4 w-4" />
            {isEn ? 'What happens next' : 'Tiếp theo là gì'}
          </div>
          <ul className="mt-4 space-y-3 text-sm leading-6 text-muted-foreground">
            <li>{isEn ? 'Choose the branch that is most convenient for your visit.' : 'Chọn cơ sở thuận tiện nhất để đến khám.'}</li>
            <li>{isEn ? 'The doctor list is filtered by your selected specialty.' : 'Danh sách bác sĩ được lọc theo chuyên khoa đã chọn.'}</li>
            <li>{isEn ? 'After you submit, the clinic reviews the request and confirms by SMS.' : 'Sau khi gửi yêu cầu, phòng khám sẽ duyệt và xác nhận qua SMS.'}</li>
          </ul>
        </div>
      </div>
    </div>
  );

  const scheduleStep = (
    <div className="grid gap-6 xl:grid-cols-[1.6fr_0.95fr]">
      <div className="space-y-6">
        <div className="rounded-[28px] border border-border/70 bg-card p-6 text-card-foreground shadow-sm">
          <div className="flex flex-col gap-4 border-b border-border/60 pb-5 md:flex-row md:items-center md:justify-between">
            <div>
              <p className="text-xs font-semibold uppercase tracking-[0.2em] text-primary">
                {isEn ? 'Choose a date' : 'Chọn ngày khám'}
              </p>
              <h2 className="mt-2 text-2xl font-semibold text-foreground">
                {visitDate ? formatDisplayDate(visitDate, isEn) : isEn ? 'Select a suitable date' : 'Chọn ngày phù hợp'}
              </h2>
            </div>
            <div className="inline-flex rounded-2xl border border-border/70 bg-muted/15 p-1">
              {[
                { value: 'AM', label: isEn ? 'Morning' : 'Buổi sáng' },
                { value: 'PM', label: isEn ? 'Afternoon' : 'Buổi chiều' },
              ].map((item) => (
                <button
                  type="button"
                  key={item.value}
                  onClick={() => {
                    setSession(item.value);
                    setSlotStart('');
                  }}
                  className={cn(
                    'rounded-xl px-4 py-2 text-sm font-medium transition-colors',
                    session === item.value ? 'bg-primary text-primary-foreground shadow-sm' : 'text-muted-foreground hover:text-foreground',
                  )}
                >
                  {item.label}
                </button>
              ))}
            </div>
          </div>

          <div className="mt-5 overflow-hidden rounded-[24px] border border-border/60 bg-muted/10 p-3 sm:p-5">
            <Calendar
              mode="single"
              selected={visitDate ? parseISO(visitDate) : undefined}
              onSelect={(value) => {
                if (!value) return;
                setVisitDate(toLocalDateInputValue(value));
                setSlotStart('');
              }}
              locale={calendarLocale}
              disabled={(date) => toLocalDateInputValue(date) < today}
              className="w-full"
              classNames={{
                months: 'w-full',
                month: 'w-full space-y-5',
                table: 'w-full border-collapse',
                head_row: 'grid grid-cols-7 gap-2',
                row: 'mt-2 grid grid-cols-7 gap-2',
                head_cell: 'h-10 w-full rounded-xl text-xs font-semibold uppercase tracking-[0.12em] text-muted-foreground',
                cell: 'h-auto w-full p-0 text-center',
                day: 'h-14 w-full rounded-2xl text-base font-medium hover:bg-primary/8',
                day_selected: 'bg-primary text-primary-foreground hover:bg-primary hover:text-primary-foreground shadow-[0_10px_24px_hsl(var(--primary)_/_0.22)]',
                day_today: 'bg-primary/10 text-primary',
                day_disabled: 'cursor-not-allowed opacity-35 line-through',
              }}
            />
          </div>
        </div>

        <div className="rounded-[28px] border border-border/70 bg-card p-6 text-card-foreground shadow-sm">
          <div className="flex items-center justify-between gap-3">
            <div>
              <p className="text-xs font-semibold uppercase tracking-[0.2em] text-primary">
                {isEn ? 'Available times' : 'Giờ khám khả dụng'}
              </p>
              <h3 className="mt-2 text-2xl font-semibold text-foreground">
                {visitDate
                  ? formatDisplayDate(visitDate, isEn)
                  : isEn
                    ? 'Choose a date first'
                    : 'Hãy chọn ngày trước'}
              </h3>
            </div>
            {lastSyncAt ? (
              <div className="rounded-full bg-muted/20 px-3 py-1 text-xs text-muted-foreground">
                {isEn ? 'Last sync' : 'Cập nhật'}: {new Date(lastSyncAt).toLocaleTimeString()}
              </div>
            ) : null}
          </div>

          {availabilityParams ? (
            <div className="mt-4 rounded-2xl border border-border/60 bg-muted/15 px-4 py-3 text-sm text-muted-foreground">
              <span className="font-medium text-foreground">
                {isLiveConnected
                  ? isEn
                    ? 'Live availability is active.'
                    : 'Đang theo dõi lịch trống theo thời gian thực.'
                  : isEn
                    ? 'Realtime is reconnecting.'
                    : 'Kết nối realtime đang khôi phục.'}
              </span>{' '}
              {isEn
                ? 'Selected slots can change if another patient books first.'
                : 'Khung giờ có thể thay đổi tự động nếu có bệnh nhân khác đặt trước.'}
            </div>
          ) : null}

          <div className="mt-5">
            {!availabilityParams ? (
              <div className="rounded-2xl border border-dashed border-border/70 px-6 py-10 text-center text-sm text-muted-foreground">
                {isEn
                  ? 'Select a branch, specialty, doctor, date and session to load available slots.'
                  : 'Hãy chọn cơ sở, chuyên khoa, bác sĩ, ngày và buổi khám để tải lịch trống.'}
              </div>
            ) : availabilityLoading || availabilityPlaceholder ? (
              <div className="rounded-2xl border border-dashed border-border/70 px-6 py-10 text-center text-sm text-muted-foreground">
                {isEn ? 'Loading available time slots...' : 'Đang tải khung giờ trống...'}
              </div>
            ) : availabilityError ? (
              <div className="rounded-2xl border border-dashed border-destructive/30 px-6 py-10 text-center text-sm text-muted-foreground">
                {isEn ? 'Unable to load time slots. Please try again.' : 'Không thể tải khung giờ. Vui lòng thử lại'}
              </div>
            ) : slots.length === 0 ? (
              <div className="rounded-2xl border border-dashed border-border/70 px-6 py-10 text-center text-sm text-muted-foreground">
                {isEn ? 'No available slots in this session.' : 'Không còn khung giờ trống trong buổi này'}
              </div>
            ) : (
              <div className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-4">
                {slots.map((slot) => {
                  const selected = slot.slotStart === slotStart;
                  return (
                    <button
                      type="button"
                      key={slot.slotStart}
                      onClick={() => setSlotStart(slot.slotStart)}
                      className={cn(
                        'rounded-2xl border px-4 py-4 text-center text-sm font-semibold transition-all',
                        selected
                          ? 'border-primary bg-primary text-primary-foreground shadow-[0_10px_24px_hsl(var(--primary)_/_0.22)]'
                          : 'border-border/70 bg-muted/15 text-foreground hover:border-primary/40 hover:bg-primary/5',
                      )}
                    >
                      <div className="flex items-center justify-center gap-2">
                        <span>{slot.slotStart}</span>
                        {selected ? <Check className="h-4 w-4" /> : null}
                      </div>
                    </button>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      </div>

      <div className="space-y-6">
        <div className="rounded-[28px] border border-border/70 bg-card p-6 text-card-foreground shadow-sm xl:sticky xl:top-28">
          <div className="flex items-center gap-2 text-lg font-semibold text-foreground">
            <CalendarDays className="h-5 w-5 text-primary" />
            {isEn ? 'Visit summary' : 'Tóm tắt lượt khám'}
          </div>

          <div className="mt-5 flex items-start gap-4 border-b border-border/60 pb-5">
            <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-primary/10 text-primary">
              <Stethoscope className="h-7 w-7" />
            </div>
            <div>
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">
                {selectedSpecialty?.name || (isEn ? 'Selected specialty' : 'Chuyên khoa đang chọn')}
              </p>
              <p className="mt-1 text-2xl font-semibold text-foreground">
                {selectedDoctor?.fullName || (isEn ? 'Select doctor' : 'Chọn bác sĩ')}
              </p>
              <p className="mt-1 text-sm text-muted-foreground">{selectedDoctor?.title || selectedBranch?.name || '—'}</p>
            </div>
          </div>

          <div className="mt-5 space-y-3">
            <SummaryRow label={isEn ? 'Branch' : 'Cơ sở'} value={selectedBranch?.name || (isEn ? 'Choose branch' : 'Chọn cơ sở')} />
            <SummaryRow label={isEn ? 'Specialty' : 'Chuyên khoa'} value={selectedSpecialty?.name || (isEn ? 'Choose specialty' : 'Chọn chuyên khoa')} />
            <SummaryRow label={isEn ? 'Doctor' : 'Bác sĩ'} value={selectedDoctor?.fullName || (isEn ? 'Choose doctor' : 'Chọn bác sĩ')} />
            <SummaryRow label={isEn ? 'Date' : 'Ngày khám'} value={formatDisplayDate(visitDate, isEn)} />
            <SummaryRow
              label={isEn ? 'Estimated time' : 'Thời gian dự kiến'}
              value={slotStart ? `${slotStart}${selectedSlot?.slotEnd ? ` - ${selectedSlot.slotEnd}` : ''}` : isEn ? 'Choose time slot' : 'Chọn giờ khám'}
              accent={Boolean(slotStart)}
            />
          </div>

          <div className="mt-5 rounded-2xl bg-muted/20 p-4">
            <div className="flex items-center justify-between gap-4">
              <div>
                <p className="text-sm text-muted-foreground">{isEn ? 'Initial consultation fee' : 'Phí khám ban đầu'}</p>
                <p className="mt-1 text-2xl font-semibold text-foreground">{consultationFeeValue}</p>
              </div>
              <CircleHelp className="h-5 w-5 text-muted-foreground" />
            </div>
            <p className="mt-2 text-xs leading-5 text-muted-foreground">
              {isEn
                ? 'Applies to the selected specialty at this branch. Additional services or tests are billed separately if required.'
                : 'Áp dụng cho chuyên khoa đã chọn tại cơ sở này. Dịch vụ hoặc xét nghiệm phát sinh sẽ được tính riêng nếu cần.'}
            </p>
          </div>
        </div>
      </div>
    </div>
  );

  const identityInputClassName = (fromProfile: boolean) =>
    cn(
      'h-12 rounded-2xl border-border/70 bg-background',
      fromProfile && 'bg-muted/30 text-muted-foreground',
    );

  const renderProfileFieldHint = (fromProfile: boolean, profileHasValue: boolean, optional = false) => {
    if (!isPatientUser || !usablePatientProfile) return null;

    return (
      <p className={cn('mt-1 text-xs', fromProfile ? 'text-primary' : 'text-muted-foreground')}>
        {fromProfile
          ? isEn
            ? 'Filled from your profile.'
            : 'Đã lấy từ hồ sơ.'
          : profileHasValue
            ? isEn
              ? 'Using edited details for this booking.'
              : 'Đang dùng thông tin đã chỉnh sửa cho lịch hẹn này.'
          : optional
            ? isEn
              ? 'You can add this if you want clinic updates by email.'
              : 'Bạn có thể bổ sung nếu muốn nhận thông báo qua email.'
            : isEn
              ? 'Missing in your profile; please enter it here.'
              : 'Hồ sơ chưa có thông tin này, vui lòng nhập bổ sung.'}
      </p>
    );
  };

  const patientStep = (
    <div className="grid gap-6 xl:grid-cols-[1.45fr_0.95fr]">
      <div className="rounded-[28px] border border-border/70 bg-card p-6 text-card-foreground shadow-sm md:p-8">
        <div className="border-b border-border/60 pb-5">
          <p className="text-xs font-semibold uppercase tracking-[0.2em] text-primary">
            {isEn ? 'Booking details' : 'Chi tiết đặt lịch'}
          </p>
          <h2 className="mt-2 text-2xl font-semibold text-foreground">
            {isEn ? 'Patient details' : 'Thông tin bệnh nhân'}
          </h2>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-muted-foreground">
            {isPatientUser
              ? isEn
                ? 'This information is taken from your account profile.'
                : 'Thông tin được lấy từ hồ sơ tài khoản của bạn.'
              : isEn
                ? 'Please enter your details so the clinic can contact you to confirm the appointment.'
                : 'Vui lòng nhập thông tin để phòng khám liên hệ xác nhận lịch hẹn.'}
          </p>
          {isPatientUser ? (
            <div
              className={cn(
                'mt-4 flex gap-3 rounded-2xl border px-4 py-3 text-sm leading-6',
                usablePatientProfile &&
                  'border-primary/20 bg-primary/5 text-primary',
                isPatientProfilePending &&
                  'border-border/70 bg-muted/20 text-muted-foreground',
                isPatientProfileError &&
                  !usablePatientProfile &&
                  'border-destructive/20 bg-destructive/5 text-destructive',
              )}
            >
              {isPatientProfileError && !usablePatientProfile ? (
                <CircleHelp className="mt-0.5 h-4 w-4 shrink-0" />
              ) : (
                <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0" />
              )}
              <div>
                <p className="font-medium">
                  {isPatientProfilePending
                    ? isEn
                      ? 'Loading patient details...'
                      : 'Đang tải thông tin bệnh nhân...'
                    : isPatientProfileError && !usablePatientProfile
                      ? isEn
                        ? 'Unable to load your profile details automatically.'
                        : 'Không thể tự động lấy thông tin hồ sơ. Vui lòng nhập thông tin bệnh nhân.'
                      : isEn
                        ? 'Patient details are automatically filled from your account profile.'
                        : 'Thông tin bệnh nhân đã được tự động lấy từ hồ sơ tài khoản của bạn.'}
                </p>
                {usablePatientProfile ? (
                  <p className="mt-1 text-xs leading-5 text-muted-foreground">
                    {isEn
                      ? 'If anything is incorrect, please update it in your personal profile.'
                      : 'Nếu thông tin chưa đúng, vui lòng cập nhật trong hồ sơ cá nhân.'}
                  </p>
                ) : null}
              </div>
            </div>
          ) : null}
        </div>

        <div className="mt-6 grid grid-cols-1 gap-5 md:grid-cols-2">
          <div>
            <label className="mb-2 block text-sm font-medium text-foreground">
              {isEn ? 'Full name' : 'Họ và tên'} <span className="text-destructive">*</span>
            </label>
            <Input
              className={identityInputClassName(patientProfileFields.fullName)}
              readOnly={patientProfileFields.fullName}
              aria-readonly={patientProfileFields.fullName}
              {...form.register('patientFullName')}
            />
            {form.formState.errors.patientFullName ? (
              <p className="mt-1 text-xs text-destructive">{form.formState.errors.patientFullName.message}</p>
            ) : (
              renderProfileFieldHint(patientProfileFields.fullName, Boolean(patientProfileFieldValues.fullName))
            )}
          </div>
          <div>
            <label className="mb-2 block text-sm font-medium text-foreground">
              {isEn ? 'Phone number' : 'Số điện thoại'} <span className="text-destructive">*</span>
            </label>
            <Input
              className={identityInputClassName(patientProfileFields.phone)}
              readOnly={patientProfileFields.phone}
              aria-readonly={patientProfileFields.phone}
              {...form.register('patientPhone')}
            />
            {form.formState.errors.patientPhone ? (
              <p className="mt-1 text-xs text-destructive">{form.formState.errors.patientPhone.message}</p>
            ) : (
              renderProfileFieldHint(patientProfileFields.phone, Boolean(patientProfileFieldValues.phone))
            )}
          </div>
          <div>
            <label className="mb-2 block text-sm font-medium text-foreground">Email</label>
            <Input
              className={identityInputClassName(patientProfileFields.email)}
              readOnly={patientProfileFields.email}
              aria-readonly={patientProfileFields.email}
              {...form.register('patientEmail')}
            />
            {form.formState.errors.patientEmail ? (
              <p className="mt-1 text-xs text-destructive">{form.formState.errors.patientEmail.message}</p>
            ) : (
              renderProfileFieldHint(patientProfileFields.email, Boolean(patientProfileFieldValues.email), true)
            )}
          </div>
          <div>
            <label className="mb-2 block text-sm font-medium text-foreground">
              {isEn ? 'Date of birth' : 'Ngày sinh'} <span className="text-destructive">*</span>
            </label>
            <Input
              className={identityInputClassName(patientProfileFields.dob)}
              type="date"
              readOnly={patientProfileFields.dob}
              aria-readonly={patientProfileFields.dob}
              {...form.register('patientDob')}
              max={today}
            />
            {form.formState.errors.patientDob ? (
              <p className="mt-1 text-xs text-destructive">{form.formState.errors.patientDob.message}</p>
            ) : (
              renderProfileFieldHint(patientProfileFields.dob, Boolean(patientProfileFieldValues.dob))
            )}
          </div>
          <div>
            <label className="mb-2 block text-sm font-medium text-foreground">
              {isEn ? 'Gender' : 'Giới tính'} <span className="text-destructive">*</span>
            </label>
            <Select
              value={form.watch('patientGender')}
              onValueChange={(value) => form.setValue('patientGender', value, { shouldValidate: true })}
              disabled={patientProfileFields.gender}
            >
              <SelectTrigger className={identityInputClassName(patientProfileFields.gender)}>
                <SelectValue placeholder={isEn ? 'Select gender' : 'Chọn giới tính'} />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="MALE">{isEn ? 'Male' : 'Nam'}</SelectItem>
                <SelectItem value="FEMALE">{isEn ? 'Female' : 'Nữ'}</SelectItem>
                <SelectItem value="OTHER">{isEn ? 'Other' : 'Khác'}</SelectItem>
              </SelectContent>
            </Select>
            {form.formState.errors.patientGender ? (
              <p className="mt-1 text-xs text-destructive">{form.formState.errors.patientGender.message}</p>
            ) : (
              renderProfileFieldHint(patientProfileFields.gender, Boolean(patientProfileFieldValues.gender))
            )}
          </div>
          <div>
            <label className="mb-2 block text-sm font-medium text-foreground">
              {isEn ? 'Visit type' : 'Loại lượt khám'} <span className="text-destructive">*</span>
            </label>
            <Select value={form.watch('visitType')} onValueChange={(value) => form.setValue('visitType', value, { shouldValidate: true })}>
              <SelectTrigger className="h-12 rounded-2xl border-border/70 bg-background">
                <SelectValue placeholder={isEn ? 'Select visit type' : 'Chọn loại lượt khám'} />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="NEW_PATIENT">{isEn ? 'New patient' : 'Khám mới'}</SelectItem>
                <SelectItem value="FOLLOW_UP">{isEn ? 'Follow-up' : 'Tái khám'}</SelectItem>
                <SelectItem value="CONSULTATION">{isEn ? 'Consultation' : 'Tư vấn'}</SelectItem>
              </SelectContent>
            </Select>
            {form.formState.errors.visitType ? <p className="mt-1 text-xs text-destructive">{form.formState.errors.visitType.message}</p> : null}
          </div>
          <div className="md:col-span-2">
            <label className="mb-2 block text-sm font-medium text-foreground">{isEn ? 'Reason for visit' : 'Lý do đến khám'}</label>
            <Textarea className="min-h-28 rounded-2xl border-border/70 bg-background" rows={4} {...form.register('reasonForVisit')} />
          </div>
          <div className="md:col-span-2">
            <label className="mb-2 block text-sm font-medium text-foreground">{isEn ? 'Note for clinic (optional)' : 'Ghi chú cho phòng khám'}</label>
            <Textarea className="min-h-28 rounded-2xl border-border/70 bg-background" rows={4} {...form.register('patientNote')} />
          </div>
        </div>
      </div>

      <div className="space-y-6">
        <div className="rounded-[28px] border border-border/70 bg-card p-6 text-card-foreground shadow-sm xl:sticky xl:top-28">
          <div className="flex items-center gap-2 text-lg font-semibold text-foreground">
            <User className="h-5 w-5 text-primary" />
            {isEn ? 'Review before continuing' : 'Kiểm tra trước khi tiếp tục'}
          </div>
          <div className="mt-5 space-y-3">
            <SummaryRow label={isEn ? 'Doctor' : 'Bác sĩ'} value={selectedDoctor?.fullName} />
            <SummaryRow label={isEn ? 'Date & time' : 'Ngày & giờ'} value={visitDate && slotStart ? `${formatDisplayDate(visitDate, isEn)} • ${slotStart}` : undefined} accent={Boolean(visitDate && slotStart)} />
            <SummaryRow label={isEn ? 'Clinic branch' : 'Cơ sở'} value={selectedBranch?.name} />
            <SummaryRow label={isEn ? 'Initial consultation fee' : 'Phí khám ban đầu'} value={consultationFeeValue} accent={Boolean(formattedConsultationFee)} />
            <SummaryRow label={isEn ? 'Patient' : 'Bệnh nhân'} value={form.watch('patientFullName') || (isEn ? 'Not filled yet' : 'Chưa nhập')} />
            <SummaryRow label={isEn ? 'Phone' : 'Điện thoại'} value={form.watch('patientPhone') || (isEn ? 'Not filled yet' : 'Chưa nhập')} />
            <SummaryRow label={isEn ? 'Visit type' : 'Loại lượt khám'} value={form.watch('visitType') ? visitTypeLabel(form.watch('visitType'), isEn) : isEn ? 'Not selected yet' : 'Chưa chọn'} />
          </div>
        </div>
      </div>
    </div>
  );

  const reviewStep = (
    <div className="grid gap-6 xl:grid-cols-[1.45fr_0.95fr]">
      <div className="rounded-[28px] border border-border/70 bg-card p-6 text-card-foreground shadow-sm md:p-8">
        <div className="border-b border-border/60 pb-5">
          <p className="text-xs font-semibold uppercase tracking-[0.2em] text-primary">
            {isEn ? 'Final review' : 'Kiểm tra lần cuối'}
          </p>
          <h2 className="mt-2 text-2xl font-semibold text-foreground">
            {isEn ? 'Send your appointment request' : 'Gửi yêu cầu đặt lịch'}
          </h2>
        </div>

        <div className="mt-6 grid grid-cols-1 gap-4 md:grid-cols-2">
          <SummaryRow label={isEn ? 'Branch' : 'Cơ sở'} value={selectedBranch?.name} />
          <SummaryRow label={isEn ? 'Specialty' : 'Chuyên khoa'} value={selectedSpecialty?.name} />
          <SummaryRow label={isEn ? 'Doctor' : 'Bác sĩ'} value={selectedDoctor?.fullName} />
          <SummaryRow label={isEn ? 'Time' : 'Thời gian'} value={`${formatDisplayDate(visitDate, isEn)} • ${slotStart}${selectedSlot?.slotEnd ? ` - ${selectedSlot.slotEnd}` : ''}`} accent />
          <SummaryRow label={isEn ? 'Initial consultation fee' : 'Phí khám ban đầu'} value={consultationFeeValue} accent={Boolean(formattedConsultationFee)} />
          <SummaryRow label={isEn ? 'Patient' : 'Bệnh nhân'} value={form.getValues('patientFullName')} />
          <SummaryRow label={isEn ? 'Phone' : 'Điện thoại'} value={form.getValues('patientPhone')} />
          <SummaryRow label={isEn ? 'Visit type' : 'Loại lượt khám'} value={visitTypeLabel(form.getValues('visitType'), isEn)} />
          <SummaryRow label={isEn ? 'Email' : 'Email'} value={form.getValues('patientEmail') || '—'} />
        </div>

        <div className="mt-5 rounded-2xl bg-muted/20 p-4 text-sm leading-6 text-muted-foreground">
          {isEn
            ? 'After you send this request, the clinic reviews the live slot you selected and confirms it by SMS to your registered phone number. If the slot changes before submission, the page will refresh the availability and you can choose again.'
            : 'Sau khi bạn gửi yêu cầu, phòng khám sẽ xem lại khung giờ bạn đã chọn và xác nhận qua SMS đến số điện thoại đã đăng ký. Nếu khung giờ thay đổi trước khi gửi, trang sẽ tự cập nhật và bạn có thể chọn lại.'}
        </div>
      </div>

      <div className="space-y-6">
        <div className="rounded-[28px] border border-border/70 bg-card p-6 text-card-foreground shadow-sm xl:sticky xl:top-28">
          <div className="flex items-center gap-2 text-lg font-semibold text-foreground">
            <MapPin className="h-5 w-5 text-primary" />
            {isEn ? 'Final check' : 'Kiểm tra cuối'}
          </div>
          <div className="mt-5 space-y-3">
            <SummaryRow label={isEn ? 'Doctor' : 'Bác sĩ'} value={selectedDoctor?.fullName} />
            <SummaryRow label={isEn ? 'Clinic branch' : 'Cơ sở'} value={selectedBranch?.name} />
            <SummaryRow label={isEn ? 'Initial consultation fee' : 'Phí khám ban đầu'} value={consultationFeeValue} accent={Boolean(formattedConsultationFee)} />
            <SummaryRow label={isEn ? 'Reason for visit' : 'Lý do khám'} value={form.getValues('reasonForVisit') || '—'} />
          </div>
        </div>
      </div>
    </div>
  );

  return (
    <div className="section-padding bg-muted/20">
      <div className="container-wide max-w-[1240px]">
        <ScrollReveal>
          <div className="max-w-3xl">
            <h1 className="text-4xl font-semibold tracking-tight text-foreground md:text-5xl">
              {isEn ? 'Book an appointment' : 'Đặt lịch khám bệnh'}
            </h1>
            <p className="mt-4 text-lg leading-8 text-muted-foreground">
              {isEn
                ? 'Choose a branch, specialty, and doctor, then send a request from the live slot you select. The clinic will confirm the appointment by SMS.'
                : 'Chọn cơ sở, chuyên khoa và bác sĩ, rồi gửi yêu cầu từ khung giờ còn trống bạn chọn. Phòng khám sẽ xác nhận lịch hẹn qua SMS.'}
            </p>
            <div className="mt-6 grid gap-3 rounded-[24px] border border-primary/10 bg-primary/[0.04] p-4 sm:grid-cols-3 sm:p-5">
              <div className="rounded-2xl bg-card px-4 py-3 text-card-foreground shadow-sm">
                <p className="text-xs font-semibold uppercase tracking-[0.16em] text-primary">{isEn ? 'Live slots' : 'Khung giờ thực'}</p>
                <p className="mt-1 text-sm leading-6 text-muted-foreground">
                  {isEn ? 'You only see slots that are still available.' : 'Bạn chỉ thấy các khung giờ còn trống.'}
                </p>
              </div>
              <div className="rounded-2xl bg-card px-4 py-3 text-card-foreground shadow-sm">
                <p className="text-xs font-semibold uppercase tracking-[0.16em] text-primary">{isEn ? 'Clear fee' : 'Rõ phí khám'}</p>
                <p className="mt-1 text-sm leading-6 text-muted-foreground">
                  {isEn ? 'The consultation fee is shown before you submit.' : 'Phí khám được hiển thị trước khi gửi yêu cầu.'}
                </p>
              </div>
              <div className="rounded-2xl bg-card px-4 py-3 text-card-foreground shadow-sm">
                <p className="text-xs font-semibold uppercase tracking-[0.16em] text-primary">{isEn ? 'SMS confirmation' : 'Xác nhận SMS'}</p>
                <p className="mt-1 text-sm leading-6 text-muted-foreground">
                  {isEn ? 'The clinic confirms the request to your phone number.' : 'Phòng khám xác nhận qua số điện thoại đã đăng ký.'}
                </p>
              </div>
            </div>
          </div>
        </ScrollReveal>

        <ScrollReveal className="mt-10">
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            {steps.map((item, index) => (
              <StepCard key={item.labelVi} index={index} step={item} activeStep={step} isEn={isEn} />
            ))}
          </div>
        </ScrollReveal>

        <ScrollReveal className="mt-10">
          {step === 0 ? selectionStep : null}
          {step === 1 ? scheduleStep : null}
          {step === 2 ? patientStep : null}
          {step === 3 ? reviewStep : null}
        </ScrollReveal>

        <div className="mt-10 flex flex-col-reverse items-stretch justify-end gap-3 border-t border-border/50 pt-8 sm:flex-row sm:items-center">
          <Button variant="outline" className="h-12 w-full rounded-2xl px-6 sm:w-auto" onClick={() => setStep((prev) => Math.max(0, prev - 1))} disabled={step === 0}>
            <ChevronLeft className="mr-2 h-4 w-4" />
            {isEn ? 'Back' : 'Quay lại'}
          </Button>
          {step < steps.length - 1 ? (
            <Button className="h-12 w-full rounded-2xl px-7 sm:w-auto" onClick={() => setStep((prev) => prev + 1)} disabled={!canNext()}>
              {getContinueLabel(step, isEn)}
              <ChevronRight className="ml-2 h-4 w-4" />
            </Button>
          ) : (
            <Button className="h-12 w-full rounded-2xl px-7 sm:w-auto" onClick={handleSubmit} disabled={createAppointment.isPending}>
              {createAppointment.isPending
                ? isEn
                  ? 'Submitting...'
                  : 'Đang gửi...'
                : isEn
                  ? 'Send request'
                  : 'Gửi yêu cầu'}
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}
