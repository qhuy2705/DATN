import { useEffect, useMemo, useRef, useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
  AlertTriangle,
  Bot,
  CalendarDays,
  Check,
  ChevronLeft,
  ChevronRight,
  CircleHelp,
  Hospital,
  Loader2,
  Mail,
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
import {
  Dialog,
  DialogContent,
  DialogDescription,
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
import { Calendar } from '@/components/ui/calendar';
import { ScrollReveal } from '@/components/ScrollReveal';
import {
  usePublicAvailabilityRealtime,
  useBranchSpecialties,
  useBranches,
  useCreatePublicAppointment,
  useDoctor,
  useDoctors,
  useRequestBookingEmailOtp,
  useVerifyBookingEmailOtp,
} from '@/hooks/use-public-data';
import { useCurrentUser } from '@/hooks/use-auth';
import { usePatientProfile } from '@/hooks/use-patient-portal';
import {
  clearPersistedAiBookingDraft,
  getAiBookingDraftFromRouteState,
  getSessionFromAiBookingDraft,
  readPersistedAiBookingDraft,
} from '@/lib/ai-booking-draft';
import { getApiErrorMessage } from '@/lib/error-utils';
import { toLocalDateInputValue } from '@/lib/date';
import {
  getPublicDoctorBookingSpecialty,
  isPublicDoctorBookableForSpecialty,
} from '@/lib/doctor-readiness';
import { cn } from '@/lib/utils';
import {
  CHRONIC_CONDITION_LABELS,
  CHRONIC_CONDITION_OPTIONS,
  FUNCTIONAL_IMPACT_LABELS,
  FUNCTIONAL_IMPACT_OPTIONS,
  RED_FLAG_LABELS,
  RED_FLAG_OPTIONS,
  SYMPTOM_ONSET_LABELS,
  SYMPTOM_ONSET_OPTIONS,
  formatTriageSelection,
  formatTriageSelections,
} from '@/lib/triage';
import type { ChronicCondition, FunctionalImpact, RedFlag, SymptomOnset } from '@/types/api';

const steps = [
  { labelVi: 'Chọn dịch vụ', labelEn: 'Care team', icon: Stethoscope },
  { labelVi: 'Chọn ngày & giờ', labelEn: 'Time', icon: CalendarDays },
  { labelVi: 'Thông tin bệnh nhân', labelEn: 'Details', icon: User },
  { labelVi: 'Kiểm tra lại', labelEn: 'Review', icon: Check },
];

const BOOKING_REQUIRES_STAFF_ASSISTANCE_CODE = 'BOOKING_REQUIRES_STAFF_ASSISTANCE';
const DOCTOR_NOT_READY_ERROR_CODES = new Set([
  'DOCTOR_NOT_READY',
  'DOCTOR_NOT_BOOKABLE',
  'DOCTOR_UNAVAILABLE',
  'DOCTOR_NOT_AVAILABLE',
  'DOCTOR_NOT_READY_FOR_BOOKING',
  'DOCTOR_NOT_OPERATIONAL_READY',
  'DOCTOR_NOT_OPERATIONALLY_READY',
  'DOCTOR_ACCOUNT_MISSING',
  'DOCTOR_ACCOUNT_INACTIVE',
  'DOCTOR_ACCOUNT_BLOCKED',
]);
const DEFAULT_HOTLINE_PHONE = '1900 1234';
const DEFAULT_EMAIL_OTP_RESEND_SECONDS = 30;

type PatientForm = {
  patientFullName: string;
  patientPhone: string;
  patientEmail: string;
  patientDob: string;
  patientGender: string;
  visitType: string;
  reasonForVisit?: string;
  symptomOnset: SymptomOnset;
  chronicConditions: ChronicCondition[];
  chronicConditionOthers: string[];
  functionalImpact: FunctionalImpact;
  redFlags: RedFlag[];
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

function normalizeEmail(value?: string | null) {
  return value?.trim().toLowerCase() ?? '';
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

function getApiErrorCode(error: unknown) {
  const maybeAxios = error as {
    response?: {
      data?: {
        code?: unknown;
        errorCode?: unknown;
      };
    };
  };
  const code = maybeAxios.response?.data?.code ?? maybeAxios.response?.data?.errorCode;
  return typeof code === 'string' ? code : undefined;
}

function isDoctorNotReadyError(error: unknown) {
  const code = getApiErrorCode(error);
  if (code && DOCTOR_NOT_READY_ERROR_CODES.has(code)) return true;

  const message = getApiErrorMessage(error, '').toLowerCase();
  return (
    (message.includes('doctor') || message.includes('bác sĩ') || message.includes('bac si')) &&
    (message.includes('ready') ||
      message.includes('sẵn sàng') ||
      message.includes('san sang') ||
      message.includes('bookable') ||
      message.includes('operational') ||
      message.includes('account') ||
      message.includes('tài khoản') ||
      message.includes('tai khoan'))
  );
}

function getNestedRecord(value: unknown) {
  return value && typeof value === 'object' ? value as Record<string, unknown> : undefined;
}

function readSeconds(value: unknown) {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return Math.max(0, Math.ceil(value));
  }

  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) return Math.max(0, Math.ceil(parsed));
  }

  return undefined;
}

function secondsOrDefault(value: unknown, fallback: number) {
  return readSeconds(value) ?? fallback;
}

function getErrorStatus(error: unknown) {
  return (error as { response?: { status?: number } }).response?.status;
}

function getCooldownSeconds(error: unknown) {
  const responseData = (error as { response?: { data?: unknown } }).response?.data;
  const responseRecord = getNestedRecord(responseData);
  const detailsRecord = getNestedRecord(responseRecord?.details);
  const dataRecord = getNestedRecord(responseRecord?.data);

  const candidates = [
    responseRecord?.resendAvailableInSeconds,
    responseRecord?.retryAfterSeconds,
    responseRecord?.cooldownSeconds,
    dataRecord?.resendAvailableInSeconds,
    dataRecord?.retryAfterSeconds,
    dataRecord?.cooldownSeconds,
    detailsRecord?.resendAvailableInSeconds,
    detailsRecord?.retryAfterSeconds,
    detailsRecord?.cooldownSeconds,
  ];

  for (const candidate of candidates) {
    const seconds = readSeconds(candidate);
    if (typeof seconds === 'number') return seconds;
  }

  return undefined;
}

function hasVerifiedEmail(value: unknown) {
  const record = getNestedRecord(value);
  if (!record) return false;
  if (record.emailVerified === true || record.verifiedEmail === true) return true;
  if (typeof record.emailVerifiedAt === 'string' && record.emailVerifiedAt.trim()) return true;
  if (typeof record.emailVerificationStatus === 'string') {
    return record.emailVerificationStatus.trim().toUpperCase() === 'VERIFIED';
  }
  return false;
}

function toTelHref(phone: string) {
  return `tel:${phone.replace(/[^\d+]/g, '')}`;
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

function QuickCardGroup<T extends string>({
  title,
  options,
  value,
  values,
  onSelect,
  onToggle,
  gridClassName,
  buttonClassName,
}: {
  title: string;
  options: Array<{ value: T; label: string }>;
  value?: T;
  values?: T[];
  onSelect?: (value: T) => void;
  onToggle?: (value: T) => void;
  gridClassName?: string;
  buttonClassName?: string;
}) {
  return (
    <div>
      <p className="mb-2 text-sm font-medium text-foreground">{title}</p>
      <div className={cn('grid gap-2 sm:grid-cols-2', gridClassName)}>
        {options.map((option) => {
          const selected = value === option.value || values?.includes(option.value) === true;
          return (
            <button
              key={option.value}
              type="button"
              onClick={() => (onSelect ? onSelect(option.value) : onToggle?.(option.value))}
              className={cn(
                'min-h-9 rounded-xl border px-3 py-2 text-left text-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                selected
                  ? 'border-primary bg-primary/10 text-primary'
                  : 'border-border/70 bg-background text-foreground hover:border-primary/40 hover:bg-primary/5',
                buttonClassName,
              )}
            >
              {option.label}
            </button>
          );
        })}
      </div>
    </div>
  );
}

export default function BookingPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const { i18n } = useTranslation();
  const isEn = i18n.language.startsWith('en');
  const calendarLocale = isEn ? enUS : vi;
  const currentUser = useCurrentUser();
  const isPatientUser = isPatientAccount(currentUser);
  const didPrefillPatientRef = useRef<string | null>(null);
  const hasAiDraftRouteFlag =
    searchParams.get('source') === 'AI_ASSISTANT' || searchParams.get('aiDraft') === '1';
  const [aiBookingDraft] = useState(() => {
    const routeDraft = getAiBookingDraftFromRouteState(location.state);
    return routeDraft ?? (hasAiDraftRouteFlag ? readPersistedAiBookingDraft() : null);
  });
  const isAiBookingFlow = Boolean(aiBookingDraft);
  const aiDraftSession = getSessionFromAiBookingDraft(aiBookingDraft);

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
          .trim()
          .min(1, isEn ? 'Please enter an email to receive lookup/cancellation OTP.' : 'Vui lòng nhập email để nhận mã OTP tra cứu/hủy lịch.')
          .email(isEn ? 'Invalid email address.' : 'Email không hợp lệ'),
        patientDob: z.string().min(1, isEn ? 'Please select date of birth.' : 'Vui lòng chọn ngày sinh'),
        patientGender: z.string().min(1, isEn ? 'Please select gender.' : 'Vui lòng chọn giới tính'),
        visitType: z.string().min(1, isEn ? 'Please select visit type.' : 'Vui lòng chọn loại lượt khám'),
        reasonForVisit: z.string().max(1000).optional(),
        symptomOnset: z
          .enum(['TODAY', 'DAYS_2_3', 'WEEK_1', 'OVER_MONTH', 'UNKNOWN'])
          .default('UNKNOWN'),
        chronicConditions: z
          .array(
            z.enum([
              'CARDIOVASCULAR',
              'DIABETES',
              'RESPIRATORY',
              'CANCER',
              'IMMUNODEFICIENCY',
              'PREGNANCY',
              'ELDERLY',
              'NONE',
            ]),
          )
          .default(['NONE']),
        chronicConditionOthers: z.array(z.string().trim().max(80)).max(5).default([]),
        functionalImpact: z
          .enum(['NORMAL', 'MILD_DIFFICULTY', 'SEVERE_DIFFICULTY', 'UNABLE_SELF_CARE', 'UNKNOWN'])
          .default('UNKNOWN'),
        redFlags: z
          .array(
            z.enum([
              'CHEST_PAIN',
              'DYSPNEA',
              'FAINTING',
              'SEIZURE',
              'STROKE_SIGNS',
              'HEAVY_BLEEDING',
              'SEVERE_PAIN',
              'HIGH_FEVER',
              'ALLERGIC_REACTION',
              'NONE',
            ]),
          )
          .default(['NONE']),
        patientNote: z.string().max(500).optional(),
      }),
    [isEn],
  );

  const [step, setStep] = useState(0);
  const [branchId, setBranchId] = useState(aiBookingDraft?.facilityId ?? searchParams.get('branchId') ?? '');
  const [specialtyId, setSpecialtyId] = useState(aiBookingDraft?.specialtyId ?? searchParams.get('specialtyId') ?? '');
  const [doctorId, setDoctorId] = useState(aiBookingDraft?.doctorId ?? searchParams.get('doctorId') ?? '');
  const [visitDate, setVisitDate] = useState(aiBookingDraft?.appointmentDate ?? searchParams.get('date') ?? '');
  const [session, setSession] = useState(aiDraftSession ?? searchParams.get('session') ?? 'AM');
  const [slotStart, setSlotStart] = useState(
    aiBookingDraft?.startTime ?? searchParams.get('slot') ?? searchParams.get('shiftId') ?? '',
  );
  const today = toLocalDateInputValue();

  /* ── AI pre-fill tracking ──────────────────────────────────── */
  const isPrefill = searchParams.get('prefill') === '1' || isAiBookingFlow;
  const didAutoAdvanceRef = useRef(false);
  const didValidateSlotRef = useRef(false);

  const form = useForm<PatientForm>({
    resolver: zodResolver(patientSchema),
    mode: 'onChange',
    defaultValues: {
      patientFullName: '',
      patientPhone: '',
      patientEmail: '',
      patientDob: '',
      patientGender: '',
      visitType: isAiBookingFlow ? 'NEW_PATIENT' : '',
      reasonForVisit: '',
      symptomOnset: 'UNKNOWN',
      chronicConditions: ['NONE'],
      chronicConditionOthers: [],
      functionalImpact: 'UNKNOWN',
      redFlags: ['NONE'],
      patientNote: '',
    },
  });
  const [showOtherConditionInput, setShowOtherConditionInput] = useState(false);
  const [otherConditionInput, setOtherConditionInput] = useState('');
  const [staffAssistanceOpen, setStaffAssistanceOpen] = useState(false);
  const [bookingEmailVerificationToken, setBookingEmailVerificationToken] = useState<string | null>(null);
  const [bookingEmailVerificationId, setBookingEmailVerificationId] = useState<string | null>(null);
  const [verifiedBookingEmail, setVerifiedBookingEmail] = useState('');
  const [bookingEmailOtpRequestedEmail, setBookingEmailOtpRequestedEmail] = useState('');
  const [bookingEmailOtp, setBookingEmailOtp] = useState('');
  const [bookingEmailOtpError, setBookingEmailOtpError] = useState<string | null>(null);
  const [bookingEmailOtpNotice, setBookingEmailOtpNotice] = useState<string | null>(null);
  const [bookingEmailOtpResendSeconds, setBookingEmailOtpResendSeconds] = useState(0);

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
  const normalizedPatientEmail = normalizeEmail(watchedPatientEmail);
  const watchedPatientDob = form.watch('patientDob');
  const watchedPatientGender = form.watch('patientGender');
  const patientProfileFields = {
    fullName: Boolean(patientProfileFieldValues.fullName && watchedPatientFullName === patientProfileFieldValues.fullName),
    phone: Boolean(patientProfileFieldValues.phone && watchedPatientPhone === patientProfileFieldValues.phone),
    email: Boolean(patientProfileFieldValues.email && watchedPatientEmail === patientProfileFieldValues.email),
    dob: Boolean(patientProfileFieldValues.dob && watchedPatientDob === patientProfileFieldValues.dob),
    gender: Boolean(patientProfileFieldValues.gender && watchedPatientGender === patientProfileFieldValues.gender),
  };
  const watchedSymptomOnset = form.watch('symptomOnset');
  const watchedChronicConditions = form.watch('chronicConditions') ?? ['NONE'];
  const watchedChronicConditionOthers = form.watch('chronicConditionOthers') ?? [];
  const watchedFunctionalImpact = form.watch('functionalImpact');
  const watchedRedFlags = form.watch('redFlags') ?? ['NONE'];
  const isPatientProfilePending =
    isPatientUser && (isPatientProfileLoading || (Boolean(patientProfile) && !isPatientProfileForCurrentUser));

  const toggleChronicCondition = (value: ChronicCondition) => {
    const current = form.getValues('chronicConditions') ?? [];
    const others = form.getValues('chronicConditionOthers') ?? [];
    if (value === 'NONE' && others.length > 0) {
      form.setValue('chronicConditions', current.filter((item) => item !== 'NONE'), {
        shouldDirty: true,
        shouldValidate: true,
      });
      toast.info('Đã giữ bệnh nền khác, nên không chọn "Không có".');
      return;
    }

    const next =
      value === 'NONE'
        ? ['NONE']
        : current.includes(value)
          ? current.filter((item) => item !== value)
          : [...current.filter((item) => item !== 'NONE'), value];

    form.setValue('chronicConditions', next.length ? next : ['NONE'], {
      shouldDirty: true,
      shouldValidate: true,
    });
  };

  const addOtherChronicCondition = () => {
    const value = otherConditionInput.trim().replace(/\s+/g, ' ');
    const current = form.getValues('chronicConditionOthers') ?? [];

    if (!value) {
      toast.error('Vui lòng nhập bệnh nền khác trước khi thêm.');
      return;
    }

    if (value.length > 80) {
      toast.error('Mỗi bệnh nền khác tối đa 80 ký tự.');
      return;
    }

    if (current.length >= 5) {
      toast.error('Chỉ có thể thêm tối đa 5 bệnh nền khác.');
      return;
    }

    if (current.some((item) => item.trim().toLowerCase() === value.toLowerCase())) {
      toast.error('Bệnh nền này đã được thêm.');
      return;
    }

    form.setValue('chronicConditionOthers', [...current, value], {
      shouldDirty: true,
      shouldValidate: true,
    });
    form.setValue(
      'chronicConditions',
      (form.getValues('chronicConditions') ?? []).filter((item) => item !== 'NONE'),
      {
        shouldDirty: true,
        shouldValidate: true,
      },
    );
    setOtherConditionInput('');
  };

  const removeOtherChronicCondition = (value: string) => {
    const next = (form.getValues('chronicConditionOthers') ?? []).filter((item) => item !== value);
    form.setValue('chronicConditionOthers', next, {
      shouldDirty: true,
      shouldValidate: true,
    });
    const currentPresets = form.getValues('chronicConditions') ?? [];
    if (!next.length && !currentPresets.length) {
      form.setValue('chronicConditions', ['NONE'], {
        shouldDirty: true,
        shouldValidate: true,
      });
    }
  };

  const toggleRedFlag = (value: RedFlag) => {
    const current = form.getValues('redFlags') ?? [];
    const next =
      value === 'NONE'
        ? ['NONE']
        : current.includes(value)
          ? current.filter((item) => item !== value)
          : [...current.filter((item) => item !== 'NONE'), value];

    form.setValue('redFlags', next.length ? next : ['NONE'], {
      shouldDirty: true,
      shouldValidate: true,
    });
  };

  const { data: branches = [] } = useBranches();
  const {
    data: branchSpecialties = [],
    isLoading: branchSpecialtiesLoading,
    isFetching: branchSpecialtiesFetching,
  } = useBranchSpecialties(branchId);
  const { data: doctorDetail, isLoading: doctorDetailLoading } = useDoctor(doctorId);
  const doctorListParams = useMemo(
    () => ({
      page: '0',
      size: '1000',
      ...(branchId ? { branchId } : {}),
      ...(specialtyId ? { specialtyId } : {}),
    }),
    [branchId, specialtyId],
  );
  const { data: doctorsPage, isLoading: doctorsLoading } = useDoctors(doctorListParams);
  const doctors = useMemo(
    () => (doctorsPage?.items ?? []).filter((doctor) => isPublicDoctorBookableForSpecialty(doctor, specialtyId)),
    [doctorsPage?.items, specialtyId],
  );
  const selectedDoctor =
    doctors.find((item) => item.id === doctorId) ??
    (doctorDetail && isPublicDoctorBookableForSpecialty(doctorDetail, specialtyId) ? doctorDetail : undefined);
  const nonBookableSelectedDoctor =
    doctorDetail?.id === doctorId && !isPublicDoctorBookableForSpecialty(doctorDetail, specialtyId) ? doctorDetail : undefined;
  const selectedDoctorForDisplay = selectedDoctor ?? nonBookableSelectedDoctor;
  const selectedDoctorCanBook = Boolean(selectedDoctor && specialtyId && isPublicDoctorBookableForSpecialty(selectedDoctor, specialtyId));
  const doctorNotReadySelectionMessage = isEn
    ? 'This doctor is not ready to receive appointments. Please choose another doctor.'
    : 'Bác sĩ hiện chưa sẵn sàng nhận lịch. Vui lòng chọn bác sĩ khác.';
  const availabilityParams = useMemo(() => {
    if (!branchId || !specialtyId || !doctorId || !visitDate || !session || !selectedDoctorCanBook) return undefined;
    return { branchId, specialtyId, doctorId, visitDate, session, onlyAvailable: 'true' };
  }, [branchId, specialtyId, doctorId, visitDate, session, selectedDoctorCanBook]);
  const {
    data: slots = [],
    isLoading: availabilityLoading,
    isError: availabilityError,
    isPlaceholderData: availabilityPlaceholder,
    isLiveConnected,
    lastSyncAt,
  } = usePublicAvailabilityRealtime(availabilityParams);
  const createAppointment = useCreatePublicAppointment();
  const requestBookingEmailOtp = useRequestBookingEmailOtp();
  const verifyBookingEmailOtp = useVerifyBookingEmailOtp();

  useEffect(() => {
    if (isAiBookingFlow) return;
    if (!branchId || branchSpecialtiesLoading || branchSpecialtiesFetching) return;
    if (!specialtyId) return;
    if (branchSpecialties.some((item) => item.id === specialtyId)) return;
    setSpecialtyId('');
    setDoctorId('');
    setVisitDate('');
    setSlotStart('');
  }, [branchId, branchSpecialties, branchSpecialtiesFetching, branchSpecialtiesLoading, isAiBookingFlow, specialtyId]);

  useEffect(() => {
    if (isAiBookingFlow) return;
    if (!doctorId) return;
    if (doctorsLoading || doctorDetailLoading) return;
    if (doctorDetail?.id === doctorId && !isPublicDoctorBookableForSpecialty(doctorDetail, specialtyId)) {
      toast.error(doctorNotReadySelectionMessage);
      setVisitDate('');
      setSlotStart('');
      return;
    }
    if (doctors.some((item) => item.id === doctorId) || doctorDetail?.id === doctorId) return;
    setDoctorId('');
    setVisitDate('');
    setSlotStart('');
  }, [doctorDetail, doctorDetail?.id, doctorDetailLoading, doctorId, doctorNotReadySelectionMessage, doctors, doctorsLoading, isAiBookingFlow, specialtyId]);

  useEffect(() => {
    if (!doctorDetail) return;
    if (!branchId && doctorDetail.branchId) setBranchId(doctorDetail.branchId);
    const preferredSpecialty = getPublicDoctorBookingSpecialty(doctorDetail)?.id;
    if (!specialtyId && preferredSpecialty) setSpecialtyId(preferredSpecialty);
  }, [doctorDetail, branchId, specialtyId]);

  useEffect(() => {
    if (isAiBookingFlow) return;
    if (!slotStart) return;
    if (slots.some((slot) => slot.slotStart === slotStart)) return;
    // Slots loaded but the selected one is gone
    if (slots.length > 0) {
      if (isPrefill && !didValidateSlotRef.current) {
        // AI-suggested slot was taken — show a graceful alert
        didValidateSlotRef.current = true;
        setSlotStart('');
        toast.error(
          isEn
            ? 'The selected time slot is no longer available. Please choose another time.'
            : 'Khung giờ đã chọn không còn trống. Vui lòng chọn giờ khác.',
        );
      } else {
        setSlotStart(slots[0].slotStart);
      }
    }
  }, [isAiBookingFlow, isPrefill, isEn, slotStart, slots]);

  /* ── AI pre-fill: auto-advance steps ───────────────────────── */
  useEffect(() => {
    if (!isPrefill || didAutoAdvanceRef.current) return;

    // Step 0 → Step 1: need branchId, specialtyId, doctorId
    if (step === 0 && branchId && specialtyId && doctorId && selectedDoctorCanBook) {
      setStep(1);
      return;
    }

    // Step 1 → Step 2: need visitDate, session, slotStart
    if (step === 1 && visitDate && session && slotStart) {
      didAutoAdvanceRef.current = true;
      setStep(2);
      return;
    }
  }, [isPrefill, step, branchId, specialtyId, doctorId, selectedDoctorCanBook, visitDate, session, slotStart]);

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
    void form.trigger();

    didPrefillPatientRef.current = prefillKey;
  }, [currentUser?.id, form, isPatientUser, patientProfileDob, usablePatientProfile]);

  useEffect(() => {
    if (bookingEmailOtpResendSeconds <= 0) return;

    const timer = window.setTimeout(() => {
      setBookingEmailOtpResendSeconds((seconds) => Math.max(0, seconds - 1));
    }, 1000);

    return () => window.clearTimeout(timer);
  }, [bookingEmailOtpResendSeconds]);

  useEffect(() => {
    if (
      bookingEmailVerificationToken &&
      verifiedBookingEmail &&
      verifiedBookingEmail !== normalizedPatientEmail
    ) {
      setBookingEmailVerificationToken(null);
      setBookingEmailVerificationId(null);
      setVerifiedBookingEmail('');
      setBookingEmailOtpNotice(null);
    }

    if (
      bookingEmailOtpRequestedEmail &&
      bookingEmailOtpRequestedEmail !== normalizedPatientEmail
    ) {
      setBookingEmailOtpRequestedEmail('');
      setBookingEmailVerificationId(null);
      setBookingEmailVerificationToken(null);
      setVerifiedBookingEmail('');
      setBookingEmailOtp('');
      setBookingEmailOtpError(null);
      setBookingEmailOtpNotice(null);
      setBookingEmailOtpResendSeconds(0);
    }
  }, [
    bookingEmailOtpRequestedEmail,
    bookingEmailVerificationToken,
    normalizedPatientEmail,
    verifiedBookingEmail,
  ]);

  const selectedBranch = branches.find((branch) => branch.id === branchId);
  const selectedSpecialty = branchSpecialties.find((item) => item.id === specialtyId);
  const selectedSlot = slots.find((item) => item.slotStart === slotStart);
  const selectedBranchName = selectedBranch?.name ?? aiBookingDraft?.facilityName;
  const selectedBranchAddress = selectedBranch?.address ?? aiBookingDraft?.facilityAddress;
  const selectedSpecialtyName = selectedSpecialty?.name ?? aiBookingDraft?.specialtyName;
  const selectedDoctorName = selectedDoctorForDisplay?.fullName ?? aiBookingDraft?.doctorName;
  const selectedDoctorSpecialtyName = selectedDoctorForDisplay?.specialtyName ?? selectedSpecialtyName;
  const selectedDoctorBranchName = selectedDoctorForDisplay?.branchName ?? selectedBranchName;
  const selectedSlotEnd = selectedSlot?.slotEnd ?? aiBookingDraft?.endTime;
  const consultationFee = selectedSpecialty?.consultationFee;
  const formattedConsultationFee = formatCurrencyVnd(consultationFee);
  const consultationFeeValue = formattedConsultationFee
    ?? (selectedSpecialtyName
      ? isEn
        ? 'Updating'
        : 'Đang cập nhật'
      : isEn
        ? 'Choose specialty'
        : 'Chọn chuyên khoa');
  const hotlinePhone = selectedBranch?.phone || DEFAULT_HOTLINE_PHONE;
  const normalizedAccountEmail = normalizeEmail(currentUser?.email);
  const normalizedProfileEmail = normalizeEmail(usablePatientProfile?.email);
  const emailMatchesVerifiedAccount =
    Boolean(normalizedPatientEmail) &&
    (
      (normalizedAccountEmail === normalizedPatientEmail && hasVerifiedEmail(currentUser)) ||
      (normalizedProfileEmail === normalizedPatientEmail && hasVerifiedEmail(usablePatientProfile))
    );
  const emailVerifiedByOtp =
    Boolean(bookingEmailVerificationToken) && verifiedBookingEmail === normalizedPatientEmail;
  const isBookingContactEmailVerified = emailMatchesVerifiedAccount || emailVerifiedByOtp;
  const requiresBookingEmailVerification =
    Boolean(normalizedPatientEmail) && !isBookingContactEmailVerified;
  const canSubmitBooking =
    !createAppointment.isPending &&
    selectedDoctorCanBook &&
    (!requiresBookingEmailVerification || isBookingContactEmailVerified);

  const canNext = () => {
    if (step === 0) return Boolean(branchId && specialtyId && doctorId && selectedDoctorCanBook);
    if (step === 1) return Boolean(visitDate && session && slotStart);
    if (step === 2) return form.formState.isValid;
    return true;
  };

  const handleRequestBookingEmailOtp = async () => {
    const isEmailValid = await form.trigger('patientEmail');
    const email = normalizeEmail(form.getValues('patientEmail'));

    if (!isEmailValid || !email) {
      setBookingEmailOtpError(isEn ? 'Please enter a valid email first.' : 'Vui lòng nhập email hợp lệ trước khi gửi mã.');
      return;
    }

    try {
      const result = await requestBookingEmailOtp.mutateAsync({ email });
      const maskedDestination = result.maskedDestination ?? result.maskedEmail;
      setBookingEmailOtpRequestedEmail(email);
      setBookingEmailVerificationId(result.verificationId ?? null);
      setBookingEmailOtp('');
      setBookingEmailOtpError(null);
      setBookingEmailVerificationToken(null);
      setVerifiedBookingEmail('');
      setBookingEmailOtpNotice(
        isEn
          ? `Verification code sent to your email${maskedDestination ? ` (${maskedDestination})` : ''}.`
          : `Mã xác thực đã được gửi tới email của bạn${maskedDestination ? ` (${maskedDestination})` : ''}.`,
      );
      setBookingEmailOtpResendSeconds(
        secondsOrDefault(
          result.resendAvailableInSeconds ?? result.retryAfterSeconds ?? result.cooldownSeconds,
          DEFAULT_EMAIL_OTP_RESEND_SECONDS,
        ),
      );
    } catch (error: unknown) {
      const cooldownSeconds = getCooldownSeconds(error);
      if (typeof cooldownSeconds === 'number' || getErrorStatus(error) === 429) {
        const nextCooldown = cooldownSeconds ?? Math.max(bookingEmailOtpResendSeconds, DEFAULT_EMAIL_OTP_RESEND_SECONDS);
        setBookingEmailOtpResendSeconds(nextCooldown);
        setBookingEmailOtpError(
          isEn
            ? `Please wait ${nextCooldown} seconds before requesting another verification code.`
            : `Vui lòng chờ ${nextCooldown} giây trước khi gửi lại mã xác thực.`,
        );
        return;
      }

      setBookingEmailOtpError(
        getApiErrorMessage(
          error,
          isEn ? 'Unable to send verification code right now.' : 'Chưa thể gửi mã xác thực lúc này.',
        ),
      );
    }
  };

  const handleVerifyBookingEmailOtp = async () => {
    const email = normalizeEmail(form.getValues('patientEmail'));
    const otp = bookingEmailOtp.trim();

    if (!email || bookingEmailOtpRequestedEmail !== email) {
      setBookingEmailOtpError(isEn ? 'Please send a verification code first.' : 'Vui lòng gửi mã xác thực trước.');
      return;
    }

    if (!bookingEmailVerificationId) {
      setBookingEmailOtpError(isEn ? 'Please send the verification code again.' : 'Vui lòng gửi lại mã xác thực.');
      return;
    }

    if (otp.length < 6) {
      setBookingEmailOtpError(isEn ? 'Please enter the 6-digit verification code.' : 'Vui lòng nhập đủ 6 chữ số xác thực.');
      return;
    }

    try {
      const result = await verifyBookingEmailOtp.mutateAsync({ verificationId: bookingEmailVerificationId, otp });
      if (!result.bookingEmailVerificationToken) {
        setBookingEmailOtpError(isEn ? 'Unable to verify this email right now.' : 'Chưa thể xác thực email lúc này.');
        return;
      }

      setBookingEmailVerificationToken(result.bookingEmailVerificationToken);
      setBookingEmailVerificationId(null);
      setVerifiedBookingEmail(normalizeEmail(result.normalizedEmail ?? result.email ?? email) || email);
      setBookingEmailOtpError(null);
      setBookingEmailOtpNotice(isEn ? 'Email has been verified.' : 'Email đã được xác thực.');
      setBookingEmailOtpRequestedEmail('');
      setBookingEmailOtp('');
      setBookingEmailOtpResendSeconds(0);
    } catch {
      setBookingEmailVerificationToken(null);
      setVerifiedBookingEmail('');
      setBookingEmailOtpError(
        isEn
          ? 'The verification code is invalid or has expired.'
          : 'Mã xác thực không hợp lệ hoặc đã hết hạn.',
      );
    }
  };

  const handleSubmit = async () => {
    if (!selectedDoctorCanBook) {
      toast.error(doctorNotReadySelectionMessage);
      setStep(0);
      return;
    }

    if (requiresBookingEmailVerification && !isBookingContactEmailVerified) {
      setBookingEmailOtpError(
        isEn
          ? 'Please verify your email before sending the appointment request.'
          : 'Vui lòng xác thực email trước khi gửi yêu cầu đặt lịch.',
      );
      return;
    }

    const values = form.getValues();
    const chronicConditionOthers = values.chronicConditionOthers?.map((item) => item.trim()).filter(Boolean) ?? [];
    const selectedChronicConditions = values.chronicConditions?.filter((item) => item !== 'NONE') ?? [];
    const normalizedChronicConditions =
      selectedChronicConditions.length > 0
        ? selectedChronicConditions
        : chronicConditionOthers.length > 0
          ? []
          : ['NONE'];
    const normalizedRedFlags = values.redFlags?.length ? values.redFlags : ['NONE'];

    try {
      const appointment = await createAppointment.mutateAsync({
        source: isAiBookingFlow ? 'AI_ASSISTANT' : undefined,
        branchId,
        specialtyId,
        doctorId,
        visitDate,
        session,
        slotStart,
        slotId: aiBookingDraft?.slotId,
        patientFullName: values.patientFullName,
        patientPhone: values.patientPhone,
        patientEmail: values.patientEmail.trim(),
        bookingEmailVerificationToken: emailVerifiedByOtp
          ? bookingEmailVerificationToken ?? undefined
          : undefined,
        patientDob: values.patientDob,
        patientGender: values.patientGender,
        visitType: values.visitType,
        reasonForVisit: values.reasonForVisit,
        preTriage: {
          symptomOnset: values.symptomOnset || 'UNKNOWN',
          chronicConditions: normalizedChronicConditions,
          chronicConditionOthers,
          functionalImpact: values.functionalImpact || 'UNKNOWN',
          redFlags: normalizedRedFlags,
        },
        patientNote: values.patientNote,
      });

      if (isAiBookingFlow) {
        clearPersistedAiBookingDraft();
      }
      toast.success(isEn ? 'Appointment request submitted successfully.' : 'Gửi yêu cầu đặt lịch thành công!');
      navigate('/booking/success', {
        state: {
          appointment,
          branchName: selectedBranchName,
          specialtyName: selectedSpecialtyName,
          doctorName: selectedDoctorName,
          slotEnd: selectedSlotEnd,
        },
      });
    } catch (error: unknown) {
      if (getApiErrorCode(error) === BOOKING_REQUIRES_STAFF_ASSISTANCE_CODE) {
        setStaffAssistanceOpen(true);
        return;
      }

      if (isDoctorNotReadyError(error)) {
        toast.error(getApiErrorMessage(error, doctorNotReadySelectionMessage));
        setStep(0);
        return;
      }

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
            disabled={isAiBookingFlow}
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
            disabled={isAiBookingFlow || !branchId}
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
            disabled={isAiBookingFlow || !specialtyId}
          >
            <SelectTrigger className="h-12 rounded-2xl border-border/70 bg-background">
              <SelectValue placeholder={isEn ? 'Select doctor' : 'Chọn bác sĩ'} />
            </SelectTrigger>
            <SelectContent>
              {nonBookableSelectedDoctor ? (
                <SelectItem value={nonBookableSelectedDoctor.id} disabled>
                  {nonBookableSelectedDoctor.fullName} - {isEn ? 'Not ready for booking' : 'Chưa sẵn sàng nhận lịch'}
                </SelectItem>
              ) : null}
              {doctors.map((doctor) => (
                <SelectItem key={doctor.id} value={doctor.id}>
                  {doctor.fullName}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      {doctorId && !selectedDoctorCanBook && !doctorsLoading && !doctorDetailLoading ? (
        <div className="rounded-2xl border border-warning/30 bg-warning/10 px-4 py-3 text-sm leading-6 text-warning">
          {doctorNotReadySelectionMessage}
        </div>
      ) : null}

      {isAiBookingFlow ? (
        <div className="rounded-2xl border border-primary/20 bg-primary/5 px-4 py-3 text-sm leading-6 text-primary">
          <div className="flex items-start gap-3">
            <Bot className="mt-0.5 h-4 w-4 shrink-0" />
            <div>
              <p className="font-semibold">
                {isEn ? 'This schedule was selected from PrimeCare AI.' : 'Lịch này được chọn từ PrimeCare AI.'}
              </p>
              <p className="text-xs leading-5 text-muted-foreground">
                {isEn
                  ? 'Doctor, branch, date, and time are locked for review. Confirming here is the only step that creates an appointment.'
                  : 'Bác sĩ, cơ sở, ngày và giờ được khóa để bạn kiểm tra. Chỉ khi xác nhận tại đây hệ thống mới tạo lịch hẹn thật.'}
              </p>
            </div>
          </div>
        </div>
      ) : null}

      <div className="grid gap-5 lg:grid-cols-[1.25fr_0.75fr]">
        <div className="rounded-[28px] border border-border/70 bg-card p-6 text-card-foreground">
          <div className="flex items-start justify-between gap-4 border-b border-border/60 pb-5">
            <div>
              <p className="text-xs font-semibold uppercase tracking-[0.2em] text-primary">
                {isEn ? 'Your care path' : 'Lộ trình đang chọn'}
              </p>
              <h2 className="mt-2 text-2xl font-semibold text-foreground">
                {selectedSpecialtyName || (isEn ? 'Choose a specialty' : 'Chọn chuyên khoa phù hợp')}
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

          {selectedDoctorName ? (
            <div className="grid gap-4 pt-5 md:grid-cols-3">
              <div className="rounded-2xl border border-border/60 bg-muted/15 p-4">
                <p className="text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">
                  {isEn ? 'Doctor' : 'Bác sĩ'}
                </p>
                <p className="mt-2 text-lg font-semibold text-foreground">{selectedDoctorName}</p>
                <p className="mt-1 text-sm text-muted-foreground">{selectedDoctor?.title || '—'}</p>
              </div>
              <div className="rounded-2xl border border-border/60 bg-muted/15 p-4">
                <p className="text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">
                  {isEn ? 'Specialty' : 'Chuyên khoa'}
                </p>
                <p className="mt-2 text-lg font-semibold text-foreground">{selectedDoctorSpecialtyName || '—'}</p>
                <p className="mt-1 text-sm text-muted-foreground">{selectedDoctorBranchName || '—'}</p>
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
            <li>{isEn ? 'After you submit, the clinic reviews the request and sends lookup/cancellation OTP by email.' : 'Sau khi gửi yêu cầu, phòng khám sẽ duyệt và gửi mã OTP tra cứu/hủy lịch qua email.'}</li>
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
                    if (isAiBookingFlow) return;
                    setSession(item.value);
                    setSlotStart('');
                  }}
                  disabled={isAiBookingFlow}
                  className={cn(
                    'rounded-xl px-4 py-2 text-sm font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-70',
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
                if (isAiBookingFlow) return;
                if (!value) return;
                setVisitDate(toLocalDateInputValue(value));
                setSlotStart('');
              }}
              locale={calendarLocale}
              disabled={(date) =>
                isAiBookingFlow
                  ? toLocalDateInputValue(date) !== visitDate
                  : toLocalDateInputValue(date) < today
              }
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

          {availabilityParams && !isAiBookingFlow ? (
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
            {isAiBookingFlow ? (
              <div className="rounded-2xl border border-primary/20 bg-primary/5 p-4 text-sm">
                <div className="flex items-start gap-3">
                  <Check className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
                  <div className="min-w-0 flex-1">
                    <p className="font-semibold text-foreground">
                      {slotStart
                        ? `${slotStart}${selectedSlotEnd ? ` - ${selectedSlotEnd}` : ''}`
                        : isEn
                          ? 'AI selected time'
                          : 'Giờ AI đã chọn'}
                    </p>
                    <p className="mt-1 text-xs leading-5 text-muted-foreground">
                      {isEn
                        ? 'This slot is carried from the AI booking draft. You can review patient details before submitting the appointment request.'
                        : 'Ca khám này được chuyển từ bản nháp AI. Bạn có thể kiểm tra thông tin bệnh nhân trước khi gửi yêu cầu đặt lịch.'}
                    </p>
                    {aiBookingDraft?.slotId ? (
                      <p className="mt-2 break-all text-xs text-muted-foreground">
                        Slot ID: {aiBookingDraft.slotId}
                      </p>
                    ) : null}
                  </div>
                </div>
              </div>
            ) : !availabilityParams ? (
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
                {selectedSpecialtyName || (isEn ? 'Selected specialty' : 'Chuyên khoa đang chọn')}
              </p>
              <p className="mt-1 text-2xl font-semibold text-foreground">
                {selectedDoctorName || (isEn ? 'Select doctor' : 'Chọn bác sĩ')}
              </p>
              <p className="mt-1 text-sm text-muted-foreground">{selectedDoctor?.title || selectedBranchName || '—'}</p>
            </div>
          </div>

          <div className="mt-5 space-y-3">
            <SummaryRow label={isEn ? 'Branch' : 'Cơ sở'} value={selectedBranchName || (isEn ? 'Choose branch' : 'Chọn cơ sở')} />
            <SummaryRow label={isEn ? 'Specialty' : 'Chuyên khoa'} value={selectedSpecialtyName || (isEn ? 'Choose specialty' : 'Chọn chuyên khoa')} />
            <SummaryRow label={isEn ? 'Doctor' : 'Bác sĩ'} value={selectedDoctorName || (isEn ? 'Choose doctor' : 'Chọn bác sĩ')} />
            <SummaryRow label={isEn ? 'Date' : 'Ngày khám'} value={formatDisplayDate(visitDate, isEn)} />
            <SummaryRow
              label={isEn ? 'Estimated time' : 'Thời gian dự kiến'}
              value={slotStart ? `${slotStart}${selectedSlotEnd ? ` - ${selectedSlotEnd}` : ''}` : isEn ? 'Choose time slot' : 'Chọn giờ khám'}
              accent={Boolean(slotStart)}
            />
            {selectedBranchAddress ? (
              <SummaryRow label={isEn ? 'Address' : 'Địa chỉ'} value={selectedBranchAddress} />
            ) : null}
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
              ? 'Email is required for appointment lookup and cancellation OTP.'
              : 'Email là bắt buộc để nhận mã OTP tra cứu/hủy lịch.'
            : isEn
              ? 'Missing in your profile; please enter it here.'
              : 'Hồ sơ chưa có thông tin này, vui lòng nhập bổ sung.'}
      </p>
    );
  };

  const compactTriageGridClassName = 'sm:grid-cols-2 xl:grid-cols-3';

  const preliminaryScreeningSection = (
    <div className="rounded-[28px] border border-border/70 bg-card p-5 text-card-foreground shadow-sm md:p-6">
      <div className="border-b border-border/60 pb-4">
        <h3 className="text-base font-semibold text-foreground">Sàng lọc sơ bộ</h3>
        <p className="mt-1 text-sm leading-6 text-muted-foreground">
          Thông tin sàng lọc sơ bộ giúp phòng khám nhận biết các trường hợp cần ưu tiên. Đây không phải chẩn đoán y tế.
        </p>
      </div>

      <div className="mt-5 grid grid-cols-1 gap-6 lg:grid-cols-2 lg:items-start">
        <div className="space-y-4">
          <div>
            <label className="mb-2 block text-sm font-medium text-foreground">{isEn ? 'Reason for visit' : 'Lý do đến khám'}</label>
            <Textarea className="min-h-24 rounded-2xl border-border/70 bg-background" rows={4} {...form.register('reasonForVisit')} />
          </div>

          <QuickCardGroup
            title="Triệu chứng bắt đầu từ khi nào?"
            options={SYMPTOM_ONSET_OPTIONS}
            value={watchedSymptomOnset}
            gridClassName={compactTriageGridClassName}
            onSelect={(value) =>
              form.setValue('symptomOnset', value, {
                shouldDirty: true,
                shouldValidate: true,
              })
            }
          />

          <QuickCardGroup
            title="Mức độ ảnh hưởng sinh hoạt"
            options={FUNCTIONAL_IMPACT_OPTIONS}
            value={watchedFunctionalImpact}
            gridClassName={compactTriageGridClassName}
            onSelect={(value) =>
              form.setValue('functionalImpact', value, {
                shouldDirty: true,
                shouldValidate: true,
              })
            }
          />
        </div>

        <div className="space-y-4">
          <QuickCardGroup
            title="Bệnh nền / yếu tố nguy cơ"
            options={CHRONIC_CONDITION_OPTIONS}
            values={watchedChronicConditions}
            gridClassName={compactTriageGridClassName}
            onToggle={toggleChronicCondition}
          />

          <div className="space-y-3 rounded-xl border border-border/70 bg-background px-3 py-3">
            <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <p className="text-sm font-medium text-foreground">Bệnh nền khác</p>
                <p className="text-xs leading-5 text-muted-foreground">
                  Bạn có thể nhập thêm bệnh nền hoặc thông tin y tế khác nếu muốn staff lưu ý.
                </p>
              </div>
              <Button
                type="button"
                variant="outline"
                size="sm"
                className="self-start"
                onClick={() => setShowOtherConditionInput((current) => !current)}
              >
                + Thêm bệnh nền khác
              </Button>
            </div>

            {showOtherConditionInput ? (
              <div className="grid gap-2 sm:grid-cols-[minmax(0,1fr)_auto]">
                <Input
                  value={otherConditionInput}
                  onChange={(event) => setOtherConditionInput(event.target.value.slice(0, 80))}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter') {
                      event.preventDefault();
                      addOtherChronicCondition();
                    }
                  }}
                  placeholder="Ví dụ: suy thận mạn, đang chạy thận, đang dùng thuốc chống đông..."
                  maxLength={80}
                />
                <Button type="button" size="sm" onClick={addOtherChronicCondition}>
                  Thêm
                </Button>
              </div>
            ) : null}

            {watchedChronicConditionOthers.length > 0 ? (
              <div>
                <p className="mb-2 text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  Đã thêm
                </p>
                <div className="flex flex-wrap gap-2">
                  {watchedChronicConditionOthers.map((item) => (
                    <span
                      key={item}
                      className="inline-flex items-center gap-2 rounded-full border border-primary/20 bg-primary/10 px-3 py-1 text-xs font-medium text-primary"
                    >
                      {item}
                      <button
                        type="button"
                        className="text-primary/70 hover:text-primary"
                        aria-label={`Xóa ${item}`}
                        onClick={() => removeOtherChronicCondition(item)}
                      >
                        x
                      </button>
                    </span>
                  ))}
                </div>
              </div>
            ) : null}
          </div>

          <QuickCardGroup
            title="Dấu hiệu cần chú ý"
            options={RED_FLAG_OPTIONS}
            values={watchedRedFlags}
            gridClassName={compactTriageGridClassName}
            onToggle={toggleRedFlag}
          />

          <div className="flex gap-3 rounded-xl border border-destructive/20 bg-destructive/5 px-3 py-2.5 text-sm leading-6 text-destructive">
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
            <p>
              Nếu bạn đang đau ngực dữ dội, khó thở nặng, ngất, co giật, yếu liệt nửa người hoặc chảy máu nhiều, vui lòng liên hệ cấp cứu hoặc đến cơ sở y tế gần nhất.
            </p>
          </div>
        </div>
      </div>
    </div>
  );

  const patientStep = (
    <div className="space-y-6">
      <div className="rounded-[28px] border border-border/70 bg-card p-5 text-card-foreground shadow-sm md:p-6">
        <div className="border-b border-border/60 pb-4">
          <p className="text-xs font-semibold uppercase tracking-[0.2em] text-primary">
            {isEn ? 'Booking details' : 'Chi tiết đặt lịch'}
          </p>
          <h2 className="mt-2 text-xl font-semibold text-foreground">
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

        <div className="mt-5 grid grid-cols-1 gap-4 md:grid-cols-2">
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
            <label className="mb-2 block text-sm font-medium text-foreground">
              Email <span className="text-destructive">*</span>
            </label>
            <Input
              className={identityInputClassName(false)}
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
            <label className="mb-2 block text-sm font-medium text-foreground">{isEn ? 'Note for clinic (optional)' : 'Ghi chú cho phòng khám'}</label>
            <Textarea className="min-h-24 rounded-2xl border-border/70 bg-background" rows={4} {...form.register('patientNote')} />
          </div>
        </div>
      </div>

      {preliminaryScreeningSection}
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
          {isAiBookingFlow ? <SummaryRow label={isEn ? 'Source' : 'Nguồn'} value="PrimeCare AI" accent /> : null}
          <SummaryRow label={isEn ? 'Branch' : 'Cơ sở'} value={selectedBranchName} />
          <SummaryRow label={isEn ? 'Specialty' : 'Chuyên khoa'} value={selectedSpecialtyName} />
          <SummaryRow label={isEn ? 'Doctor' : 'Bác sĩ'} value={selectedDoctorName} />
          <SummaryRow label={isEn ? 'Time' : 'Thời gian'} value={`${formatDisplayDate(visitDate, isEn)} • ${slotStart}${selectedSlotEnd ? ` - ${selectedSlotEnd}` : ''}`} accent />
          <SummaryRow label={isEn ? 'Initial consultation fee' : 'Phí khám ban đầu'} value={consultationFeeValue} accent={Boolean(formattedConsultationFee)} />
          <SummaryRow label={isEn ? 'Patient' : 'Bệnh nhân'} value={form.getValues('patientFullName')} />
          <SummaryRow label={isEn ? 'Phone' : 'Điện thoại'} value={form.getValues('patientPhone')} />
          <SummaryRow label={isEn ? 'Visit type' : 'Loại lượt khám'} value={visitTypeLabel(form.getValues('visitType'), isEn)} />
          <SummaryRow label={isEn ? 'Email' : 'Email'} value={form.getValues('patientEmail') || '—'} />
          {aiBookingDraft?.slotId ? <SummaryRow label="Slot ID" value={aiBookingDraft.slotId} /> : null}
        </div>

        <div className="mt-5 rounded-2xl border border-border/70 bg-muted/10 p-4">
          <div className="flex items-start gap-3">
            <div className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
              <Mail className="h-4 w-4" />
            </div>
            <div className="min-w-0 flex-1">
              <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                <div>
                  <h3 className="text-base font-semibold text-foreground">
                    {isEn ? 'Verify contact email' : 'Xác thực email liên hệ'}
                  </h3>
                  <p className="mt-1 text-sm leading-6 text-muted-foreground">
                    {isEn
                      ? 'Verify this email before sending the booking request so the clinic can send appointment updates.'
                      : 'Xác thực email này trước khi gửi yêu cầu đặt lịch để phòng khám gửi thông tin cập nhật lịch hẹn.'}
                  </p>
                </div>
                <div className="rounded-lg bg-background px-3 py-2 text-sm font-medium text-foreground">
                  {form.getValues('patientEmail') || '—'}
                </div>
              </div>

              {isBookingContactEmailVerified ? (
                <div className="mt-3 flex items-center gap-2 rounded-xl border border-primary/20 bg-primary/5 px-3 py-2 text-sm font-medium text-primary">
                  <Check className="h-4 w-4" />
                  {isEn ? 'Email has been verified.' : 'Email đã được xác thực.'}
                </div>
              ) : (
                <div className="mt-4 space-y-3">
                  <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
                    <div className="min-w-0 flex-1">
                      <label htmlFor="booking-email-otp" className="mb-2 block text-sm font-medium text-foreground">
                        {isEn ? 'Email verification code' : 'Mã xác thực email'}
                      </label>
                      <Input
                        id="booking-email-otp"
                        inputMode="numeric"
                        autoComplete="one-time-code"
                        maxLength={6}
                        value={bookingEmailOtp}
                        onChange={(event) => {
                          setBookingEmailOtp(event.target.value.replace(/\D/g, '').slice(0, 6));
                          setBookingEmailOtpError(null);
                        }}
                        disabled={bookingEmailOtpRequestedEmail !== normalizedPatientEmail}
                        placeholder="000000"
                        aria-invalid={Boolean(bookingEmailOtpError)}
                      />
                    </div>
                    <Button
                      type="button"
                      variant="outline"
                      onClick={handleRequestBookingEmailOtp}
                      disabled={requestBookingEmailOtp.isPending || bookingEmailOtpResendSeconds > 0}
                    >
                      {requestBookingEmailOtp.isPending ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
                      {bookingEmailOtpResendSeconds > 0
                        ? isEn
                          ? `Send again in ${bookingEmailOtpResendSeconds}s`
                          : `Gửi lại sau ${bookingEmailOtpResendSeconds}s`
                        : isEn
                          ? 'Send verification code'
                          : 'Gửi mã xác thực'}
                    </Button>
                    <Button
                      type="button"
                      onClick={handleVerifyBookingEmailOtp}
                      disabled={
                        verifyBookingEmailOtp.isPending ||
                        bookingEmailOtpRequestedEmail !== normalizedPatientEmail ||
                        bookingEmailOtp.trim().length < 6
                      }
                    >
                      {verifyBookingEmailOtp.isPending ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
                      {isEn ? 'Verify email' : 'Xác thực email'}
                    </Button>
                  </div>

                  {bookingEmailOtpNotice ? (
                    <p className="text-sm font-medium text-primary">{bookingEmailOtpNotice}</p>
                  ) : null}
                  {bookingEmailOtpError ? (
                    <p role="alert" className="text-sm font-medium text-destructive">{bookingEmailOtpError}</p>
                  ) : null}
                </div>
              )}
            </div>
          </div>
        </div>

        <div className="mt-5 rounded-2xl bg-muted/20 p-4 text-sm leading-6 text-muted-foreground">
          {isAiBookingFlow
            ? isEn
              ? 'AI only prepared this draft. The appointment is created only after you send this request.'
              : 'AI chỉ chuẩn bị bản nháp này. Lịch hẹn thật chỉ được tạo sau khi bạn gửi yêu cầu.'
            : isEn
              ? 'After you send this request, the clinic reviews the live slot you selected and sends lookup/cancellation OTP to your email. If the slot changes before submission, the page will refresh the availability and you can choose again.'
              : 'Sau khi bạn gửi yêu cầu, phòng khám sẽ xem lại khung giờ bạn đã chọn và gửi mã OTP tra cứu/hủy lịch tới email của bạn. Nếu khung giờ thay đổi trước khi gửi, trang sẽ tự cập nhật và bạn có thể chọn lại.'}
        </div>
      </div>

      <div className="space-y-6">
        <div className="rounded-[28px] border border-border/70 bg-card p-6 text-card-foreground shadow-sm xl:sticky xl:top-28">
          <div className="flex items-center gap-2 text-lg font-semibold text-foreground">
            <MapPin className="h-5 w-5 text-primary" />
            {isEn ? 'Final check' : 'Kiểm tra cuối'}
          </div>
          <div className="mt-5 space-y-3">
            <SummaryRow label={isEn ? 'Doctor' : 'Bác sĩ'} value={selectedDoctorName} />
            <SummaryRow label={isEn ? 'Clinic branch' : 'Cơ sở'} value={selectedBranchName} />
            <SummaryRow label={isEn ? 'Initial consultation fee' : 'Phí khám ban đầu'} value={consultationFeeValue} accent={Boolean(formattedConsultationFee)} />
            <SummaryRow label={isEn ? 'Reason for visit' : 'Lý do khám'} value={form.getValues('reasonForVisit') || '—'} />
            <SummaryRow
              label="Sàng lọc sơ bộ"
              value={`${formatTriageSelection(form.getValues('symptomOnset'), SYMPTOM_ONSET_LABELS)} · ${formatTriageSelection(form.getValues('functionalImpact'), FUNCTIONAL_IMPACT_LABELS)}`}
            />
            <SummaryRow
              label="Yếu tố nguy cơ"
              value={formatTriageSelections(form.getValues('chronicConditions'), CHRONIC_CONDITION_LABELS)}
            />
            <SummaryRow
              label="Bệnh nền khác"
              value={(form.getValues('chronicConditionOthers') ?? []).join(', ') || 'Không có'}
            />
            <SummaryRow
              label="Dấu hiệu cần chú ý"
              value={formatTriageSelections(form.getValues('redFlags'), RED_FLAG_LABELS)}
            />
            {selectedBranchAddress ? <SummaryRow label={isEn ? 'Address' : 'Địa chỉ'} value={selectedBranchAddress} /> : null}
          </div>
        </div>
      </div>
    </div>
  );

  return (
    <>
    <div className="section-padding bg-muted/20">
      <div className="container-wide max-w-[1240px]">
        <ScrollReveal>
          <div className="max-w-3xl">
            <h1 className="text-4xl font-semibold tracking-tight text-foreground md:text-5xl">
              {isEn ? 'Book an appointment' : 'Đặt lịch khám bệnh'}
            </h1>
            <p className="mt-4 text-lg leading-8 text-muted-foreground">
              {isEn
                ? 'Choose a branch, specialty, and doctor, then send a request from the live slot you select. Email is required for lookup and cancellation OTP.'
                : 'Chọn cơ sở, chuyên khoa và bác sĩ, rồi gửi yêu cầu từ khung giờ còn trống bạn chọn. Email là bắt buộc để nhận OTP tra cứu/hủy lịch.'}
            </p>
            {isAiBookingFlow ? (
              <div className="mt-5 rounded-2xl border border-primary/20 bg-primary/5 px-4 py-3 text-sm leading-6 text-primary">
                <div className="flex items-start gap-3">
                  <Bot className="mt-0.5 h-4 w-4 shrink-0" />
                  <div>
                    <p className="font-semibold">
                      {isEn ? 'This schedule was selected from PrimeCare AI.' : 'Lịch này được chọn từ PrimeCare AI.'}
                    </p>
                    <p className="text-xs leading-5 text-muted-foreground">
                      {selectedDoctorName || aiBookingDraft?.doctorName || '—'} • {formatDisplayDate(visitDate, isEn)} • {slotStart}
                    </p>
                  </div>
                </div>
              </div>
            ) : null}
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
                <p className="text-xs font-semibold uppercase tracking-[0.16em] text-primary">{isEn ? 'Email verification' : 'Xác thực email'}</p>
                <p className="mt-1 text-sm leading-6 text-muted-foreground">
                  {isEn
                    ? 'Verify email before booking; it is also used later for lookup and cancellation OTP.'
                    : 'Xác thực email trước khi đặt; email này cũng dùng để nhận OTP tra cứu và hủy lịch sau đó.'}
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
            <Button className="h-12 w-full rounded-2xl px-7 sm:w-auto" onClick={handleSubmit} disabled={!canSubmitBooking}>
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
    <Dialog open={staffAssistanceOpen} onOpenChange={setStaffAssistanceOpen}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <div className="mb-1 flex h-10 w-10 items-center justify-center rounded-full bg-primary/10 text-primary">
            <ShieldCheck className="h-5 w-5" />
          </div>
          <DialogTitle>Cần nhân viên hỗ trợ xác nhận</DialogTitle>
          <DialogDescription>
            Yêu cầu đặt lịch này cần được phòng khám hỗ trợ xác nhận trực tiếp để đảm bảo thông tin liên hệ và thời gian khám phù hợp. Vui lòng liên hệ hotline hoặc để lại yêu cầu để nhân viên hỗ trợ.
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            onClick={() => {
              setStaffAssistanceOpen(false);
              setStep(1);
            }}
          >
            Quay lại chọn lịch
          </Button>
          <Button asChild>
            <a href={toTelHref(hotlinePhone)}>Liên hệ hotline</a>
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
    </>
  );
}
