import { describe, expect, it } from 'vitest';
import type { Patient } from '@/types/api';
import {
  getCurrentPasswordFieldError,
  hasContactChanged,
  toPatientProfilePayload,
  type PatientProfileFormValues,
} from './patient-profile-utils';

const patient = {
  id: 'patient-1',
  fullName: 'Nguyen Van A',
  phone: '0901234567',
  email: 'patient@example.com',
} satisfies Patient;

describe('patient profile payload helpers', () => {
  it('omits strict parsed fields when they are blank', () => {
    const values: PatientProfileFormValues = {
      fullName: ' Nguyen Van A ',
      phone: '   ',
      email: '',
      dob: ' ',
      gender: '',
      address: '',
      allergyNote: '   ',
      chronicDiseaseNote: '',
      note: ' Can go home after appointment. ',
      currentPassword: '',
    };

    const payload = toPatientProfilePayload(values, false);

    expect(payload).toEqual({
      fullName: 'Nguyen Van A',
      note: 'Can go home after appointment.',
    });
    expect(payload).not.toHaveProperty('phone');
    expect(payload).not.toHaveProperty('email');
    expect(payload).not.toHaveProperty('dob');
    expect(payload).not.toHaveProperty('gender');
    expect(payload).not.toHaveProperty('currentPassword');
  });

  it('does not require or send currentPassword when email and phone are unchanged after trim', () => {
    const values: PatientProfileFormValues = {
      email: ' patient@example.com ',
      phone: ' 0901234567 ',
      currentPassword: '   ',
    };

    expect(hasContactChanged(values, patient)).toBe(false);
    expect(toPatientProfilePayload(values, false)).toEqual({
      email: 'patient@example.com',
      phone: '0901234567',
    });
  });

  it('detects changed email and includes trimmed currentPassword when requested', () => {
    const values: PatientProfileFormValues = {
      email: 'new@example.com',
      phone: '0901234567',
      currentPassword: ' old-password ',
    };

    expect(hasContactChanged(values, patient)).toBe(true);
    expect(toPatientProfilePayload(values, true)).toMatchObject({
      email: 'new@example.com',
      phone: '0901234567',
      currentPassword: 'old-password',
    });
  });

  it('detects changed phone and omits blank currentPassword from payload', () => {
    const values: PatientProfileFormValues = {
      email: 'patient@example.com',
      phone: '0907654321',
      currentPassword: '',
    };

    expect(hasContactChanged(values, patient)).toBe(true);
    expect(toPatientProfilePayload(values, true)).toEqual({
      email: 'patient@example.com',
      phone: '0907654321',
    });
  });

  it('maps backend currentPassword messages to the currentPassword form field', () => {
    expect(
      getCurrentPasswordFieldError({
        response: {
          data: {
            details: {
              fields: {
                currentPassword: ['Mật khẩu hiện tại không đúng'],
              },
            },
          },
        },
      }),
    ).toBe('Mật khẩu hiện tại không đúng');

    expect(
      getCurrentPasswordFieldError({
        response: {
          data: {
            message: 'Current password is invalid',
          },
        },
      }),
    ).toBe('Current password is invalid');
  });
});
