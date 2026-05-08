import { describe, expect, it } from 'vitest';
import {
  buildAppointmentStatusOptions,
  getAppointmentStatusLabel,
  getPaymentStatusLabel,
} from '@/lib/filter-options';
import { normalizeAppointment } from '@/lib/api-adapters';
import type { AppointmentStatus, PaymentStatus } from '@/types/api';

const appointmentStatuses = [
  'REQUESTED',
  'CONFIRMED',
  'CHECKED_IN',
  'COMPLETED',
  'NO_SHOW',
  'CANCELLED',
] satisfies AppointmentStatus[];

const paymentStatuses = [
  'UNPAID',
  'PENDING_CONFIRMATION',
  'PAYMENT_REVIEW',
  'PAID',
  'REFUNDED',
  'VOID',
] satisfies PaymentStatus[];

describe('status contract helpers', () => {
  it('labels backend appointment statuses', () => {
    expect(getAppointmentStatusLabel('REQUESTED')).toBe('Chờ xác nhận');
    expect(getAppointmentStatusLabel('CONFIRMED')).toBe('Đã xác nhận');
    expect(getAppointmentStatusLabel('CHECKED_IN')).toBe('Đã check-in');
    expect(getAppointmentStatusLabel('COMPLETED')).toBe('Đã hoàn tất');
    expect(getAppointmentStatusLabel('NO_SHOW')).toBe('Không đến');
    expect(getAppointmentStatusLabel('CANCELLED')).toBe('Đã hủy');
  });

  it('does not expose legacy appointment statuses in option contracts', () => {
    expect(appointmentStatuses).not.toContain('PENDING');
    expect(appointmentStatuses).not.toContain('IN_PROGRESS');

    const options = buildAppointmentStatusOptions((key: string) => key);
    const optionValues = options.map((option) => option.value);
    expect(optionValues).toHaveLength(appointmentStatuses.length);
    expect(optionValues).toEqual(expect.arrayContaining(appointmentStatuses));
  });

  it('keeps REQUESTED unchanged when normalizing appointments', () => {
    expect(normalizeAppointment({ id: 1, status: 'REQUESTED' }).status).toBe('REQUESTED');
  });

  it('falls back to REQUESTED for removed appointment statuses', () => {
    expect(normalizeAppointment({ id: 1, status: 'PENDING' }).status).toBe('REQUESTED');
    expect(normalizeAppointment({ id: 1, status: 'IN_PROGRESS' }).status).toBe('REQUESTED');
  });

  it('labels backend payment statuses', () => {
    expect(getPaymentStatusLabel('UNPAID')).toBe('Chưa thanh toán');
    expect(getPaymentStatusLabel('PENDING_CONFIRMATION')).toBe('Chờ xác nhận');
    expect(getPaymentStatusLabel('PAYMENT_REVIEW')).toBe('Cần kiểm tra');
    expect(getPaymentStatusLabel('PAID')).toBe('Đã thanh toán');
    expect(getPaymentStatusLabel('REFUNDED')).toBe('Đã hoàn tiền');
    expect(getPaymentStatusLabel('VOID')).toBe('Đã hủy');
  });

  it('does not include removed payment statuses in frontend contract', () => {
    expect(paymentStatuses).not.toContain('FAILED');
    expect(paymentStatuses).not.toContain('PENDING');
  });
});
