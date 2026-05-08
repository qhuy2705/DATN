import type { Appointment } from '@/types/api';
import { toLocalDateInputValue } from '@/lib/date';

const DEFAULT_GRACE_MINUTES = 15;

export function getAppointmentStartDateTime(appointment: Pick<Appointment, 'visitDate' | 'slotStart'>) {
  if (!appointment.visitDate || !appointment.slotStart) return null;

  const time = appointment.slotStart.match(/^(\d{1,2}):(\d{2})/);
  if (!time) return null;

  const date = new Date(`${appointment.visitDate}T${time[1].padStart(2, '0')}:${time[2]}:00`);
  return Number.isNaN(date.getTime()) ? null : date;
}

export function isAppointmentOverdue(
  appointment: Pick<
    Appointment,
    'arrivalStatus' | 'checkedInAt' | 'overdue' | 'slotStart' | 'status' | 'visitDate'
  >,
  now = new Date(),
  graceMinutes = DEFAULT_GRACE_MINUTES,
) {
  if (typeof appointment.overdue === 'boolean') return appointment.overdue;
  if (appointment.status !== 'CONFIRMED') return false;
  if (appointment.arrivalStatus === 'ARRIVED' || appointment.checkedInAt) return false;

  const start = getAppointmentStartDateTime(appointment);
  if (!start) return false;

  const todayKey = toLocalDateInputValue(now);
  if (appointment.visitDate !== todayKey) return false;

  return now.getTime() > start.getTime() + graceMinutes * 60_000;
}
