import { describe, expect, it } from 'vitest';
import {
  getAppointmentEndDateTime,
  getAppointmentStartDateTime,
  isAppointmentOverdue,
  isLateForCheckIn,
  isNoShowEligible,
} from '@/lib/appointment-utils';
import type { Appointment } from '@/types/api';

function appointment(overrides: Partial<Appointment> = {}): Appointment {
  return {
    id: 'apt-1',
    code: 'APT-1',
    patientFullName: 'Nguyen Van A',
    patientPhone: '0909000000',
    doctorId: 'doctor-1',
    doctorName: 'BS. An',
    branchId: 'branch-1',
    branchName: 'PrimeCare',
    specialtyId: 'specialty-1',
    specialtyName: 'Tim mach',
    visitDate: '2026-05-15',
    session: 'MORNING',
    slotStart: '09:00',
    etaStart: '09:00',
    etaEnd: '09:30',
    status: 'CONFIRMED',
    ...overrides,
  };
}

describe('appointment timing helpers', () => {
  it('parses etaStart and etaEnd against the appointment visit date', () => {
    const start = getAppointmentStartDateTime(appointment());
    const end = getAppointmentEndDateTime(appointment());

    expect(start?.getFullYear()).toBe(2026);
    expect(start?.getMonth()).toBe(4);
    expect(start?.getDate()).toBe(15);
    expect(start?.getHours()).toBe(9);
    expect(start?.getMinutes()).toBe(0);
    expect(end?.getHours()).toBe(9);
    expect(end?.getMinutes()).toBe(30);
  });

  it('does not mark no-show eligible before etaEnd', () => {
    expect(
      isNoShowEligible(appointment(), new Date('2026-05-15T09:10:00')),
    ).toBe(false);
  });

  it('shows late check-in only from etaStart plus 15 minutes through etaEnd', () => {
    expect(isLateForCheckIn(appointment(), new Date('2026-05-15T09:14:59'))).toBe(false);
    expect(isLateForCheckIn(appointment(), new Date('2026-05-15T09:15:00'))).toBe(true);
    expect(isLateForCheckIn(appointment(), new Date('2026-05-15T09:30:00'))).toBe(true);
    expect(isLateForCheckIn(appointment(), new Date('2026-05-15T09:30:01'))).toBe(false);
  });

  it('marks no-show eligible only after etaEnd when the patient has not checked in', () => {
    expect(isNoShowEligible(appointment(), new Date('2026-05-15T09:30:00'))).toBe(false);
    expect(isNoShowEligible(appointment(), new Date('2026-05-15T09:30:01'))).toBe(true);
    expect(
      isNoShowEligible(
        appointment({ checkedInAt: '2026-05-15T09:20:00' }),
        new Date('2026-05-15T09:45:00'),
      ),
    ).toBe(false);
  });

  it('does not infer no-show eligibility when etaEnd is missing', () => {
    expect(
      isNoShowEligible(appointment({ etaEnd: undefined }), new Date('2026-05-15T11:00:00')),
    ).toBe(false);
    expect(
      isAppointmentOverdue(
        appointment({ etaEnd: undefined }),
        new Date('2026-05-15T11:00:00'),
      ),
    ).toBe(false);
  });
});
