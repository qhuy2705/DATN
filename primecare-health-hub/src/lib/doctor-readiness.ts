import type { Doctor, DoctorOption, DoctorPublicSpecialty, DoctorScheduleDoctorOption } from '@/types/api';

type DoctorReadinessInput = Pick<
  Doctor & DoctorOption & DoctorScheduleDoctorOption,
  | 'status'
  | 'profileStatus'
  | 'effectiveStatus'
  | 'inactiveReason'
  | 'hasAccount'
  | 'accountStatus'
  | 'operationalReady'
  | 'bookable'
  | 'notReadyReason'
>;

export type DoctorReadinessReason =
  | 'NO_ACCOUNT'
  | 'ACCOUNT_INACTIVE'
  | 'ACCOUNT_BLOCKED'
  | 'PROFILE_INACTIVE'
  | 'BRANCH_INACTIVE'
  | 'SPECIALTY_INACTIVE'
  | 'UNKNOWN';

export type DoctorReadinessTone = 'success' | 'warning' | 'destructive' | 'neutral';

export interface DoctorReadinessState {
  ready?: boolean;
  reason?: DoctorReadinessReason;
  label: string;
  tone: DoctorReadinessTone;
}

function normalizeStatus(value?: string | null) {
  return value?.trim().toUpperCase() || '';
}

function normalizeReadinessReason(value?: string | null): DoctorReadinessReason | undefined {
  const normalized = normalizeStatus(value).replace(/[\s-]+/g, '_');
  switch (normalized) {
    case 'NO_ACCOUNT':
    case 'MISSING_ACCOUNT':
    case 'DOCTOR_ACCOUNT_MISSING':
    case 'ACCOUNT_MISSING':
      return 'NO_ACCOUNT';
    case 'ACCOUNT_INACTIVE':
    case 'DOCTOR_ACCOUNT_INACTIVE':
    case 'INACTIVE_ACCOUNT':
      return 'ACCOUNT_INACTIVE';
    case 'ACCOUNT_BLOCKED':
    case 'ACCOUNT_LOCKED':
    case 'DOCTOR_ACCOUNT_BLOCKED':
    case 'DOCTOR_ACCOUNT_LOCKED':
    case 'BLOCKED_ACCOUNT':
    case 'LOCKED_ACCOUNT':
      return 'ACCOUNT_BLOCKED';
    case 'PROFILE_INACTIVE':
    case 'DOCTOR_INACTIVE':
    case 'DOCTOR_PROFILE_INACTIVE':
    case 'SELF_INACTIVE':
      return 'PROFILE_INACTIVE';
    case 'BRANCH_INACTIVE':
      return 'BRANCH_INACTIVE';
    case 'SPECIALTY_INACTIVE':
      return 'SPECIALTY_INACTIVE';
    case 'NOT_READY':
    case 'UNKNOWN':
      return 'UNKNOWN';
    default:
      return undefined;
  }
}

function isBlockedAccountStatus(status?: string) {
  return ['BLOCKED', 'LOCKED', 'SUSPENDED', 'DISABLED'].includes(normalizeStatus(status));
}

function isInactiveAccountStatus(status?: string) {
  const normalized = normalizeStatus(status);
  return Boolean(normalized && normalized !== 'ACTIVE');
}

function getKnownNotReadyReason(
  doctor?: DoctorReadinessInput | null,
): DoctorReadinessReason | undefined {
  if (!doctor) return undefined;

  if (doctor.hasAccount === false) return 'NO_ACCOUNT';

  if (doctor.hasAccount === true && isInactiveAccountStatus(doctor.accountStatus)) {
    return isBlockedAccountStatus(doctor.accountStatus)
      ? 'ACCOUNT_BLOCKED'
      : 'ACCOUNT_INACTIVE';
  }

  const status = normalizeStatus(doctor.status ?? doctor.profileStatus);
  const effectiveStatus = normalizeStatus(doctor.effectiveStatus);
  const inactiveReason = normalizeStatus(doctor.inactiveReason);

  if (status && status !== 'ACTIVE') return 'PROFILE_INACTIVE';
  if (inactiveReason === 'SELF_INACTIVE') return 'PROFILE_INACTIVE';
  if (effectiveStatus === 'BRANCH_INACTIVE' || inactiveReason === 'BRANCH_INACTIVE') {
    return 'BRANCH_INACTIVE';
  }
  if (effectiveStatus === 'SPECIALTY_INACTIVE' || inactiveReason === 'SPECIALTY_INACTIVE') {
    return 'SPECIALTY_INACTIVE';
  }

  return undefined;
}

function getReadinessLabel(reason: DoctorReadinessReason | undefined, isEn: boolean) {
  if (!reason) return isEn ? 'Ready for operation' : 'Sẵn sàng vận hành';

  if (isEn) {
    switch (reason) {
      case 'NO_ACCOUNT':
        return 'Not ready: no account';
      case 'ACCOUNT_INACTIVE':
        return 'Not ready: account inactive';
      case 'ACCOUNT_BLOCKED':
        return 'Not ready: account blocked';
      case 'PROFILE_INACTIVE':
        return 'Not ready: profile inactive';
      case 'BRANCH_INACTIVE':
        return 'Not ready: branch inactive';
      case 'SPECIALTY_INACTIVE':
        return 'Not ready: specialty inactive';
      case 'UNKNOWN':
      default:
        return 'Not ready for operation';
    }
  }

  switch (reason) {
    case 'NO_ACCOUNT':
      return 'Chưa sẵn sàng: chưa cấp tài khoản';
    case 'ACCOUNT_INACTIVE':
      return 'Chưa sẵn sàng: tài khoản chưa hoạt động';
    case 'ACCOUNT_BLOCKED':
      return 'Chưa sẵn sàng: tài khoản bị khóa';
    case 'PROFILE_INACTIVE':
      return 'Chưa sẵn sàng: hồ sơ ngưng hoạt động';
    case 'BRANCH_INACTIVE':
      return 'Chưa sẵn sàng: chi nhánh tạm ngưng';
    case 'SPECIALTY_INACTIVE':
      return 'Chưa sẵn sàng: chuyên khoa tạm ngưng';
    case 'UNKNOWN':
    default:
      return 'Chưa sẵn sàng vận hành';
  }
}

function getReadinessTone(reason: DoctorReadinessReason | undefined): DoctorReadinessTone {
  if (!reason) return 'success';
  if (reason === 'ACCOUNT_BLOCKED' || reason === 'PROFILE_INACTIVE') return 'destructive';
  if (reason === 'UNKNOWN') return 'neutral';
  return 'warning';
}

export function getDoctorReadinessState(
  doctor?: DoctorReadinessInput | null,
  isEn = false,
): DoctorReadinessState {
  const knownReason = getKnownNotReadyReason(doctor);
  const backendReason = normalizeReadinessReason(doctor?.notReadyReason);
  const explicitReady =
    typeof doctor?.operationalReady === 'boolean'
      ? doctor.operationalReady
      : typeof doctor?.bookable === 'boolean'
        ? doctor.bookable
        : undefined;
  const reason = backendReason ?? knownReason ?? (explicitReady === false ? 'UNKNOWN' : undefined);

  if (reason) {
    return {
      ready: false,
      reason,
      label: getReadinessLabel(reason, isEn),
      tone: getReadinessTone(reason),
    };
  }

  if (explicitReady === true) {
    return {
      ready: true,
      label: getReadinessLabel(undefined, isEn),
      tone: 'success',
    };
  }

  return {
    ready: undefined,
    label: isEn ? 'Readiness not provided' : 'Chưa có dữ liệu sẵn sàng',
    tone: 'neutral',
  };
}

export function isDoctorBookable(doctor?: DoctorReadinessInput | null) {
  return getDoctorReadinessState(doctor).ready !== false;
}

export function isPublicDoctorBookable(doctor?: DoctorReadinessInput | null) {
  if (!doctor) return false;

  if (doctor.bookable === false || doctor.operationalReady === false) return false;
  if (doctor.bookable === true || doctor.operationalReady === true) return true;

  const status = normalizeStatus(doctor.status ?? doctor.profileStatus);
  const effectiveStatus = normalizeStatus(doctor.effectiveStatus);
  const inactiveReason = normalizeStatus(doctor.inactiveReason);

  if (status && status !== 'ACTIVE') return false;
  if (inactiveReason === 'SELF_INACTIVE') return false;
  if (effectiveStatus === 'BRANCH_INACTIVE' || inactiveReason === 'BRANCH_INACTIVE') return false;
  if (effectiveStatus === 'SPECIALTY_INACTIVE' || inactiveReason === 'SPECIALTY_INACTIVE') return false;

  return true;
}

function isInactivePublicSpecialty(specialty?: Pick<DoctorPublicSpecialty, 'status' | 'effectiveStatus' | 'inactiveReason'> | null) {
  const status = normalizeStatus(specialty?.status);
  const effectiveStatus = normalizeStatus(specialty?.effectiveStatus);
  const inactiveReason = normalizeStatus(specialty?.inactiveReason);

  if (status && status !== 'ACTIVE') return true;
  if (inactiveReason === 'SELF_INACTIVE') return true;
  if (effectiveStatus === 'BRANCH_INACTIVE' || inactiveReason === 'BRANCH_INACTIVE') return true;
  if (effectiveStatus === 'SPECIALTY_INACTIVE' || inactiveReason === 'SPECIALTY_INACTIVE') return true;
  return false;
}

export function isPublicDoctorSpecialtyBookable(specialty?: DoctorPublicSpecialty | null) {
  if (!specialty?.id) return false;
  if (specialty.bookable === false) return false;
  return !isInactivePublicSpecialty(specialty);
}

export function getPublicDoctorBookingSpecialty(doctor?: Pick<Doctor, 'specialtyId' | 'specialtyIds' | 'specialtyName' | 'publicSpecialties'> | null) {
  if (!doctor) return undefined;

  if (doctor.publicSpecialties !== undefined) {
    return doctor.publicSpecialties.find(isPublicDoctorSpecialtyBookable);
  }

  const legacySpecialtyId = doctor.specialtyId || doctor.specialtyIds?.find(Boolean);
  if (!legacySpecialtyId) return undefined;

  return {
    id: legacySpecialtyId,
    name: doctor.specialtyName || '',
  } satisfies DoctorPublicSpecialty;
}

export function isPublicDoctorBookableForSpecialty(
  doctor?: (DoctorReadinessInput & Pick<Doctor, 'specialtyId' | 'specialtyIds' | 'specialtyName' | 'publicSpecialties'>) | null,
  specialtyId?: string,
) {
  if (!isPublicDoctorBookable(doctor)) return false;

  if (!specialtyId) return Boolean(getPublicDoctorBookingSpecialty(doctor));

  if (doctor?.publicSpecialties !== undefined) {
    return doctor.publicSpecialties.some(
      (specialty) =>
        specialty.id === specialtyId &&
        isPublicDoctorSpecialtyBookable(specialty),
    );
  }

  const legacySpecialtyIds = [doctor?.specialtyId, ...(doctor?.specialtyIds ?? [])]
    .filter((value): value is string => Boolean(value));
  return legacySpecialtyIds.length === 0 || legacySpecialtyIds.includes(specialtyId);
}

export function getDoctorPublicBookingLabel(
  doctor?: DoctorReadinessInput | null,
  isEn = false,
) {
  return isPublicDoctorBookable(doctor)
    ? isEn
      ? 'Ready for booking'
      : 'Sẵn sàng nhận lịch'
    : isEn
      ? 'Not ready for booking'
      : 'Chưa sẵn sàng nhận lịch';
}

export function getDoctorUnavailableBookingMessage(isEn = false) {
  return isEn
    ? 'This doctor is not ready to receive appointments right now.'
    : 'Bác sĩ hiện chưa sẵn sàng nhận lịch khám.';
}

export function getInternalDoctorOptionNote(
  doctor?: Pick<
    DoctorReadinessInput,
    'hasAccount' | 'accountStatus' | 'operationalReady' | 'bookable'
  > | null,
  isEn = false,
) {
  if (doctor?.hasAccount === false) {
    return isEn ? 'No account' : 'Chưa cấp tài khoản';
  }

  if (doctor?.hasAccount === true && isInactiveAccountStatus(doctor.accountStatus)) {
    return isEn ? 'Account inactive' : 'Tài khoản không hoạt động';
  }

  if (doctor?.operationalReady === false || doctor?.bookable === false) {
    return isEn ? 'Not operationally ready' : 'Chưa sẵn sàng vận hành';
  }

  return undefined;
}

export function getDoctorAccountStatusState(
  doctor?: Pick<DoctorReadinessInput, 'hasAccount' | 'accountStatus'> | null,
  isEn = false,
) {
  if (doctor?.hasAccount === false) {
    return {
      label: isEn ? 'No account' : 'Chưa cấp tài khoản',
      tone: 'warning' as DoctorReadinessTone,
    };
  }

  if (doctor?.hasAccount === true) {
    if (isInactiveAccountStatus(doctor.accountStatus)) {
      const blocked = isBlockedAccountStatus(doctor.accountStatus);
      return {
        label: blocked
          ? isEn
            ? 'Account blocked'
            : 'Tài khoản bị khóa'
          : isEn
            ? 'Account inactive'
            : 'Tài khoản chưa hoạt động',
        tone: blocked
          ? ('destructive' as DoctorReadinessTone)
          : ('warning' as DoctorReadinessTone),
      };
    }

    return {
      label: isEn ? 'Has account' : 'Đã cấp tài khoản',
      tone: 'success' as DoctorReadinessTone,
    };
  }

  return {
    label: isEn ? 'Account unknown' : 'Chưa rõ tài khoản',
    tone: 'neutral' as DoctorReadinessTone,
  };
}
