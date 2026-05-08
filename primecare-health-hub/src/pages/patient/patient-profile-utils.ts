import type { Patient, UpdatePatientSelfProfileRequest } from '@/types/api';

export type PatientProfileFormValues = UpdatePatientSelfProfileRequest;

export const CONTACT_PASSWORD_REQUIRED_MESSAGE = 'Vui lòng nhập mật khẩu hiện tại để thay đổi email hoặc số điện thoại';

export function trimString(value?: string | null) {
  return value == null ? undefined : value.trim();
}

export function blankToUndefined(value?: string | null) {
  const trimmed = trimString(value);
  return trimmed ? trimmed : undefined;
}

export function toPatientProfileFormValues(patient: Patient): PatientProfileFormValues {
  return {
    avatarUrl: patient.avatarUrl || '',
    fullName: patient.fullName || '',
    phone: patient.phone || '',
    email: patient.email || '',
    dob: patient.dob || '',
    gender: patient.gender || '',
    insuranceNumber: patient.insuranceNumber || '',
    address: patient.address || '',
    emergencyContactName: patient.emergencyContactName || '',
    emergencyContactPhone: patient.emergencyContactPhone || '',
    allergyNote: patient.allergyNote || '',
    chronicDiseaseNote: patient.chronicDiseaseNote || '',
    note: patient.note || '',
    currentPassword: '',
  };
}

export function hasContactChanged(values: Pick<PatientProfileFormValues, 'email' | 'phone'>, patient?: Patient) {
  if (!patient) return false;
  return trimString(values.email) !== trimString(patient.email) || trimString(values.phone) !== trimString(patient.phone);
}

function assignStringField(
  payload: UpdatePatientSelfProfileRequest,
  key: keyof UpdatePatientSelfProfileRequest,
  value: string | undefined,
) {
  if (value !== undefined) {
    Object.assign(payload, { [key]: value });
  }
}

export function toPatientProfilePayload(values: PatientProfileFormValues, includeCurrentPassword: boolean): UpdatePatientSelfProfileRequest {
  const payload: UpdatePatientSelfProfileRequest = {};

  assignStringField(payload, 'avatarUrl', blankToUndefined(values.avatarUrl));
  assignStringField(payload, 'fullName', blankToUndefined(values.fullName));
  assignStringField(payload, 'phone', blankToUndefined(values.phone));
  assignStringField(payload, 'email', blankToUndefined(values.email));
  assignStringField(payload, 'dob', blankToUndefined(values.dob));
  assignStringField(payload, 'gender', blankToUndefined(values.gender));
  assignStringField(payload, 'insuranceNumber', blankToUndefined(values.insuranceNumber));
  assignStringField(payload, 'address', blankToUndefined(values.address));
  assignStringField(payload, 'emergencyContactName', blankToUndefined(values.emergencyContactName));
  assignStringField(payload, 'emergencyContactPhone', blankToUndefined(values.emergencyContactPhone));
  assignStringField(payload, 'allergyNote', blankToUndefined(values.allergyNote));
  assignStringField(payload, 'chronicDiseaseNote', blankToUndefined(values.chronicDiseaseNote));
  assignStringField(payload, 'note', blankToUndefined(values.note));

  if (includeCurrentPassword) {
    assignStringField(payload, 'currentPassword', blankToUndefined(values.currentPassword));
  }

  return payload;
}

function firstErrorMessage(value: unknown) {
  if (typeof value === 'string' && value.trim()) {
    return value;
  }
  if (Array.isArray(value)) {
    return value.find((item) => typeof item === 'string' && item.trim());
  }
  return undefined;
}

function isCurrentPasswordMessage(message: string) {
  const normalized = message.toLowerCase();
  return (
    normalized.includes('currentpassword') ||
    normalized.includes('current password') ||
    normalized.includes('mật khẩu hiện tại') ||
    normalized.includes('mat khau hien tai')
  );
}

export function getCurrentPasswordFieldError(error: unknown) {
  const maybeAxios = error as {
    response?: {
      data?: {
        message?: string;
        details?: {
          fields?: Record<string, string | string[]>;
        };
      };
    };
  };

  const fieldMessage = firstErrorMessage(maybeAxios.response?.data?.details?.fields?.currentPassword);
  if (fieldMessage) return fieldMessage;

  const message = maybeAxios.response?.data?.message;
  if (typeof message === 'string' && message.trim() && isCurrentPasswordMessage(message)) {
    return message;
  }

  return undefined;
}
