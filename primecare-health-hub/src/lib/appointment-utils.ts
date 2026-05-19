import type { Appointment } from '@/types/api';

const LATE_CHECK_IN_GRACE_MINUTES = 15;

type AppointmentStartFields = Pick<Appointment, 'visitDate'> &
  Partial<Pick<Appointment, 'etaStart' | 'slotStart'>>;

type AppointmentEndFields = Pick<Appointment, 'visitDate'> &
  Partial<Pick<Appointment, 'etaEnd'>>;

type AppointmentCheckInTimingFields = AppointmentStartFields &
  AppointmentEndFields &
  Pick<Appointment, 'status'> &
  Partial<Pick<Appointment, 'checkedInAt'>>;

type AppointmentNoShowFields = AppointmentEndFields &
  Pick<Appointment, 'status'> &
  Partial<Pick<Appointment, 'checkedInAt'>>;

function parseAppointmentDateTime(visitDate: string | undefined, value: string | undefined) {
  const trimmed = value?.trim();
  if (!trimmed) return null;

  const normalized = trimmed.replace(' ', 'T');
  if (/^\d{4}-\d{2}-\d{2}/.test(normalized)) {
    const directDate = new Date(normalized);
    return Number.isNaN(directDate.getTime()) ? null : directDate;
  }

  if (!visitDate) return null;

  const time = trimmed.match(/^(\d{1,2}):(\d{2})(?::(\d{2}))?/);
  if (!time) return null;

  const date = new Date(
    `${visitDate}T${time[1].padStart(2, '0')}:${time[2]}:${time[3] ?? '00'}`,
  );
  return Number.isNaN(date.getTime()) ? null : date;
}

export function getAppointmentStartDateTime(appointment: AppointmentStartFields) {
  return parseAppointmentDateTime(appointment.visitDate, appointment.etaStart ?? appointment.slotStart);
}

export function getAppointmentEndDateTime(appointment: AppointmentEndFields) {
  return parseAppointmentDateTime(appointment.visitDate, appointment.etaEnd);
}

export function isLateForCheckIn(appointment: AppointmentCheckInTimingFields, now = new Date()) {
  if (appointment.status !== 'CONFIRMED') return false;
  if (appointment.checkedInAt) return false;

  const start = getAppointmentStartDateTime(appointment);
  const end = getAppointmentEndDateTime(appointment);
  if (!start || !end) return false;

  const lateStartsAt = start.getTime() + LATE_CHECK_IN_GRACE_MINUTES * 60_000;
  const nowTime = now.getTime();

  return nowTime >= lateStartsAt && nowTime <= end.getTime();
}

export function isNoShowEligible(appointment: AppointmentNoShowFields, now = new Date()) {
  if (appointment.status !== 'CONFIRMED') return false;
  if (appointment.checkedInAt) return false;

  const end = getAppointmentEndDateTime(appointment);
  if (!end) return false;

  return now.getTime() > end.getTime();
}

export function isAppointmentOverdue(
  appointment: Pick<
    Appointment,
    | 'canMarkNoShow'
    | 'checkedInAt'
    | 'etaEnd'
    | 'overdue'
    | 'status'
    | 'visitDate'
  >,
  now = new Date(),
) {
  if (appointment.status !== 'CONFIRMED') return false;
  if (appointment.checkedInAt) return false;
  if (!getAppointmentEndDateTime(appointment)) return false;
  if (typeof appointment.overdue === 'boolean') return appointment.overdue;
  if (typeof appointment.canMarkNoShow === 'boolean') return appointment.canMarkNoShow;

  return isNoShowEligible(appointment, now);
}

export function canMarkAppointmentNoShow(
  appointment: Pick<
    Appointment,
    | 'canMarkNoShow'
    | 'checkedInAt'
    | 'etaEnd'
    | 'overdue'
    | 'status'
    | 'visitDate'
  >,
  now = new Date(),
) {
  if (appointment.status !== 'CONFIRMED') return false;
  if (appointment.checkedInAt) return false;
  if (!getAppointmentEndDateTime(appointment)) return false;

  if (typeof appointment.canMarkNoShow === 'boolean') {
    return appointment.canMarkNoShow;
  }

  return isAppointmentOverdue(appointment, now);
}

export function getCheckedInLateLabel(
  appointment: Partial<Pick<Appointment, 'checkedInLate' | 'lateMinutes'>>,
) {
  if (typeof appointment.lateMinutes === 'number' && appointment.lateMinutes > 0) {
    return `Check-in trễ ${Math.round(appointment.lateMinutes)} phút`;
  }

  return appointment.checkedInLate ? 'Check-in trễ' : null;
}
