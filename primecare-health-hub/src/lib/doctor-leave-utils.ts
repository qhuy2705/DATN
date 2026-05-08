import type { LeaveRequest } from '@/types/api';

export type SessionKey = 'MORNING' | 'AFTERNOON';

export const CALENDAR_SESSIONS: SessionKey[] = ['MORNING', 'AFTERNOON'];

export function normalizeSessionKey(session: string | undefined): SessionKey | undefined {
  if (session === 'MORNING' || session === 'AM') return 'MORNING';
  if (session === 'AFTERNOON' || session === 'PM') return 'AFTERNOON';
  return undefined;
}

export function toApiSessionValue(session: SessionKey | string | undefined): 'AM' | 'PM' | undefined {
  const normalized = normalizeSessionKey(session);
  if (normalized === 'MORNING') return 'AM';
  if (normalized === 'AFTERNOON') return 'PM';
  return undefined;
}

export const LEAVE_STATUS_STYLES: Record<
  LeaveRequest['status'],
  {
    badgeClass: string;
    dotClass: string;
    labelVi: string;
    labelEn: string;
    shortVi: string;
    shortEn: string;
  }
> = {
  PENDING: {
    badgeClass: 'border-violet-200 bg-violet-50 text-violet-700',
    dotClass: 'bg-violet-500',
    labelVi: 'Chờ duyệt',
    labelEn: 'Pending',
    shortVi: 'Chờ duyệt',
    shortEn: 'Pending',
  },
  APPROVED: {
    badgeClass: 'border-rose-200 bg-rose-50 text-rose-700',
    dotClass: 'bg-rose-500',
    labelVi: 'Đã duyệt',
    labelEn: 'Approved',
    shortVi: 'Đã duyệt',
    shortEn: 'Approved',
  },
  REJECTED: {
    badgeClass: 'border-slate-200 bg-slate-100 text-slate-700',
    dotClass: 'bg-slate-500',
    labelVi: 'Từ chối',
    labelEn: 'Rejected',
    shortVi: 'Từ chối',
    shortEn: 'Rejected',
  },
  CANCELLED: {
    badgeClass: 'border-zinc-200 bg-zinc-100 text-zinc-600',
    dotClass: 'bg-zinc-500',
    labelVi: 'Đã hủy',
    labelEn: 'Cancelled',
    shortVi: 'Đã hủy',
    shortEn: 'Cancelled',
  },
};

export function getSessionLabel(session: string | undefined, isEn: boolean) {
  const normalized = normalizeSessionKey(session);
  if (normalized === 'MORNING') return isEn ? 'Morning' : 'Sáng';
  if (normalized === 'AFTERNOON') return isEn ? 'Afternoon' : 'Chiều';
  return session || '';
}

export function getLeaveStatusLabel(status: LeaveRequest['status'], isEn: boolean) {
  const config = LEAVE_STATUS_STYLES[status] ?? LEAVE_STATUS_STYLES.PENDING;
  return isEn ? config.labelEn : config.labelVi;
}

export function getLeaveSessionSummary(leave: LeaveRequest, isEn: boolean) {
  const start = getSessionLabel(leave.startSession, isEn);
  const end = getSessionLabel(leave.endSession, isEn);
  if (!start && !end) return isEn ? 'Full day' : 'Cả ngày';
  if (leave.startDate === leave.endDate && start === end) return start;
  if (leave.startDate === leave.endDate) {
    return isEn ? `${start} - ${end}` : `${start} - ${end}`;
  }
  return isEn ? `${start} → ${end}` : `${start} → ${end}`;
}

export function isSessionCoveredByLeave(
  leave: LeaveRequest,
  dateKey: string,
  session: SessionKey,
) {
  if (dateKey < leave.startDate || dateKey > leave.endDate) return false;

  const targetOrder = session === 'MORNING' ? 0 : 1;
  const startOrder = normalizeSessionKey(leave.startSession) === 'AFTERNOON' ? 1 : 0;
  const endOrder = normalizeSessionKey(leave.endSession) === 'MORNING' ? 0 : 1;

  if (leave.startDate === leave.endDate) {
    return targetOrder >= startOrder && targetOrder <= endOrder;
  }

  if (dateKey === leave.startDate) {
    return targetOrder >= startOrder;
  }

  if (dateKey === leave.endDate) {
    return targetOrder <= endOrder;
  }

  return true;
}

export function getLeavesForDate(leaves: LeaveRequest[], dateKey: string) {
  return leaves.filter((leave) => dateKey >= leave.startDate && dateKey <= leave.endDate);
}

export function getLeaveForSession(
  leaves: LeaveRequest[],
  dateKey: string,
  session: SessionKey,
  statuses?: LeaveRequest['status'][],
) {
  return leaves.find((leave) => {
    if (statuses && !statuses.includes(leave.status)) return false;
    return isSessionCoveredByLeave(leave, dateKey, session);
  });
}

function addOneDayKey(dateKey: string) {
  const [year, month, day] = dateKey.split('-').map(Number);
  if (!year || !month || !day) return dateKey;

  const utcDate = new Date(Date.UTC(year, month - 1, day));
  utcDate.setUTCDate(utcDate.getUTCDate() + 1);

  const nextYear = utcDate.getUTCFullYear();
  const nextMonth = String(utcDate.getUTCMonth() + 1).padStart(2, '0');
  const nextDay = String(utcDate.getUTCDate()).padStart(2, '0');

  return `${nextYear}-${nextMonth}-${nextDay}`;
}

export function countUniqueLeaveDays(
  leaves: LeaveRequest[],
  statuses?: LeaveRequest['status'][],
) {
  const days = new Set<string>();

  leaves.forEach((leave) => {
    if (statuses && !statuses.includes(leave.status)) return;
    if (!leave.startDate || !leave.endDate) return;

    let current = leave.startDate;
    let guard = 0;

    while (current <= leave.endDate && guard < 1000) {
      days.add(current);

      const next = addOneDayKey(current);
      if (next === current) break;

      current = next;
      guard += 1;
    }
  });

  return days.size;
}