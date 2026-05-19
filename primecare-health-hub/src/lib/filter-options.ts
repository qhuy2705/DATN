import type { TFunction } from 'i18next';
import type { AppointmentStatus, PaymentStatus } from '@/types/api';

export type SelectOption = { label: string; value: string };

const APPOINTMENT_STATUS_LABELS: Record<AppointmentStatus, string> = {
  REQUESTED: 'Chờ xác nhận',
  CONFIRMED: 'Đã xác nhận',
  CHECKED_IN: 'Đã check-in',
  COMPLETED: 'Đã hoàn tất',
  NO_SHOW: 'Không đến',
  CANCELLED: 'Đã hủy',
};

const PAYMENT_STATUS_LABELS: Record<PaymentStatus, string> = {
  UNPAID: 'Chưa thanh toán',
  PENDING_CONFIRMATION: 'Chờ xác nhận',
  PAYMENT_REVIEW: 'Cần kiểm tra',
  PAID: 'Đã thanh toán',
  PARTIALLY_REFUNDED: 'Hoàn một phần',
  REFUNDED: 'Đã hoàn tiền',
  VOID: 'Đã hủy',
};

export function getAppointmentStatusLabel(status?: string) {
  return APPOINTMENT_STATUS_LABELS[status as AppointmentStatus] ?? status ?? '-';
}

export function getPaymentStatusLabel(status?: string) {
  return PAYMENT_STATUS_LABELS[status as PaymentStatus] ?? status ?? '-';
}

export function buildAppointmentStatusOptions(t: TFunction): SelectOption[] {
  return [
    { value: 'REQUESTED', label: t('status.REQUESTED') },
    { value: 'CONFIRMED', label: getAppointmentStatusLabel('CONFIRMED') },
    { value: 'CHECKED_IN', label: getAppointmentStatusLabel('CHECKED_IN') },
    { value: 'COMPLETED', label: getAppointmentStatusLabel('COMPLETED') },
    { value: 'CANCELLED', label: getAppointmentStatusLabel('CANCELLED') },
    { value: 'NO_SHOW', label: getAppointmentStatusLabel('NO_SHOW') },
  ];
}

export function buildArrivalStatusOptions(t: TFunction): SelectOption[] {
  return [
    { value: 'ARRIVED', label: t('filters.arrivalStatus.arrived') },
    { value: 'NOT_ARRIVED', label: t('filters.arrivalStatus.notArrived') },
  ];
}

export function buildSourceTypeOptions(t: TFunction): SelectOption[] {
  void t;
  return [
    { value: 'PUBLIC_BOOKING', label: 'Đặt online' },
    { value: 'STAFF_BOOKING', label: 'Nhân viên đặt' },
    { value: 'WALK_IN', label: 'Khám trực tiếp' },
  ];
}

export function buildArrivedOptions(t: TFunction): SelectOption[] {
  return [
    { value: 'true', label: t('filters.arrivalStatus.arrived') },
    { value: 'false', label: t('filters.arrivalStatus.notArrived') },
  ];
}

export function buildSessionOptions(t: TFunction): SelectOption[] {
  return [
    { value: 'AM', label: t('modules.doctorSchedules.AM') },
    { value: 'PM', label: t('modules.doctorSchedules.PM') },
  ];
}

export function getArrivalStatusLabel(value: string | undefined, t: TFunction): string {
  if (value === 'ARRIVED') return t('filters.arrivalStatus.arrived');
  if (value === 'NOT_ARRIVED') return t('filters.arrivalStatus.notArrived');
  return value || '-';
}

export function getSourceTypeLabel(value: string | undefined, t: TFunction): string {
  void t;
  if (value === 'PUBLIC_BOOKING') return 'Đặt online';
  if (value === 'STAFF_BOOKING') return 'Nhân viên đặt';
  if (value === 'WALK_IN') return 'Khám trực tiếp';
  return value || '-';
}
