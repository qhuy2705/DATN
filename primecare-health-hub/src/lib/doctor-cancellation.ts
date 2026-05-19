import type {
  DoctorCancellationFollowUpType,
  DoctorCancellationHoldStatus,
  RescheduleOfferAppointmentInfo,
} from '@/types/api';
import { cn } from '@/lib/utils';

export function formatAppointmentDateTime(appointment?: RescheduleOfferAppointmentInfo | null) {
  if (!appointment) return 'Chưa có thông tin';
  const date = appointment.visitDate || '';
  const time = [appointment.slotStart, appointment.slotEnd].filter(Boolean).join(' - ');
  return [date, time].filter(Boolean).join(' · ') || 'Chưa có thông tin';
}

export function formatHoldDeadline(value?: string | null) {
  if (!value) return 'Chưa có thời hạn';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;

  const today = new Date();
  const isToday =
    date.getFullYear() === today.getFullYear() &&
    date.getMonth() === today.getMonth() &&
    date.getDate() === today.getDate();
  const time = date.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
  if (isToday) return `${time} hôm nay`;

  return date.toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function getFollowUpTypeDisplay(type?: DoctorCancellationFollowUpType | string | null) {
  switch (type) {
    case 'DOCTOR_CANCELLATION_NO_RESPONSE':
      return 'Bác sĩ hủy · Chờ phản hồi';
    case 'DOCTOR_CANCELLATION_CONTACT_REQUESTED':
      return 'Bác sĩ hủy · Cần liên hệ';
    case 'DOCTOR_CANCELLATION':
      return 'Bác sĩ hủy';
    case 'PATIENT_CONTACT_REQUESTED':
    case 'CONTACT_REQUEST':
    case 'PATIENT_PHONE_UPDATED':
    case 'PATIENT_UPDATED_PHONE':
      return 'Cần liên hệ';
    case 'NO_SHOW':
      return 'Không đến';
    default:
      return 'Cần xử lý sau lịch';
  }
}

export function getFollowUpTypeClass(type?: DoctorCancellationFollowUpType | string | null) {
  switch (type) {
    case 'DOCTOR_CANCELLATION_CONTACT_REQUESTED':
    case 'PATIENT_CONTACT_REQUESTED':
    case 'CONTACT_REQUEST':
    case 'PATIENT_PHONE_UPDATED':
    case 'PATIENT_UPDATED_PHONE':
      return 'border-primary/25 bg-primary/10 text-primary';
    case 'DOCTOR_CANCELLATION_NO_RESPONSE':
    case 'DOCTOR_CANCELLATION':
      return 'border-warning/30 bg-warning/10 text-warning';
    case 'NO_SHOW':
      return 'border-destructive/25 bg-destructive/10 text-destructive';
    default:
      return 'border-border bg-muted text-muted-foreground';
  }
}

export function getHoldStatusDisplay(status?: DoctorCancellationHoldStatus | string | null) {
  switch (status) {
    case 'HELD':
      return 'Đang giữ chỗ';
    case 'EXPIRED':
      return 'Hết hạn giữ chỗ';
    case 'CONTACT_REQUESTED':
      return 'Đã yêu cầu liên hệ';
    case 'CANCELLED':
      return 'Đã hủy theo yêu cầu';
    case 'ACCEPTED':
      return 'Đã xác nhận lịch mới';
    default:
      return 'Chưa có trạng thái';
  }
}

export function getHoldStatusClass(status?: DoctorCancellationHoldStatus | string | null) {
  switch (status) {
    case 'HELD':
      return 'border-primary/25 bg-primary/10 text-primary';
    case 'CONTACT_REQUESTED':
      return 'border-warning/30 bg-warning/10 text-warning';
    case 'ACCEPTED':
      return 'border-success/25 bg-success/10 text-success';
    case 'EXPIRED':
    case 'CANCELLED':
      return 'border-destructive/25 bg-destructive/10 text-destructive';
    default:
      return 'border-border bg-muted text-muted-foreground';
  }
}

export function badgeClass(base: string, extra?: string) {
  return cn('inline-flex items-center rounded-full border px-2.5 py-1 text-xs font-medium', base, extra);
}
