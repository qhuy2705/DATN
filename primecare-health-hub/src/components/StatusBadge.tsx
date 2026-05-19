import { useTranslation } from 'react-i18next';
import { cn } from '@/lib/utils';
import { getAppointmentStatusLabel, getPaymentStatusLabel } from '@/lib/filter-options';
import { statusToneClasses } from '@/lib/status-style-classes';

const STATUS_CLASSES: Record<string, string> = {
  REQUESTED: statusToneClasses.warning,
  PENDING: statusToneClasses.warning,
  CONFIRMED: statusToneClasses.primary,
  CHECKED_IN: statusToneClasses.accent,
  IN_PROGRESS: statusToneClasses.primary,
  REOPENED: statusToneClasses.primary,
  WAITING_PAYMENT: statusToneClasses.warning,
  WAITING_RESULTS: statusToneClasses.primary,
  READY_FOR_CONCLUSION: statusToneClasses.success,
  COMPLETED: statusToneClasses.success,
  CANCELLED: statusToneClasses.destructive,
  NO_SHOW: statusToneClasses.neutral,
  RESCHEDULED: statusToneClasses.champagne,

  ACTIVE: statusToneClasses.success,
  INACTIVE: statusToneClasses.neutral,

  SELF_INACTIVE: statusToneClasses.neutral,
  BRANCH_INACTIVE: statusToneClasses.warning,
  SPECIALTY_INACTIVE: statusToneClasses.champagne,

  DRAFT: statusToneClasses.neutral,
  VERIFIED: statusToneClasses.success,
  PENDING_PAYMENT: statusToneClasses.warning,
  WAITING_EXECUTION: statusToneClasses.primary,
  DONE: statusToneClasses.success,
  ISSUED: statusToneClasses.warning,
  DISPENSED: statusToneClasses.success,
  UNPAID: statusToneClasses.warning,
  PENDING_CONFIRMATION: statusToneClasses.primary,
  PAYMENT_REVIEW: statusToneClasses.warning,
  PARTIALLY_REFUNDED: statusToneClasses.champagne,
  REFUNDED: statusToneClasses.neutral,
  PAID: statusToneClasses.success,
  VOID: statusToneClasses.neutral,
  APPROVED: statusToneClasses.success,
  REJECTED: statusToneClasses.destructive,
  PROCESSING: statusToneClasses.primary,
  FAILED: statusToneClasses.destructive,
  AVAILABLE: statusToneClasses.success,
  ALMOST_FULL: statusToneClasses.warning,
  FULL: statusToneClasses.destructive,
};

const STATUS_LABELS: Record<string, string> = {
  REQUESTED: getAppointmentStatusLabel('REQUESTED'),
  CONFIRMED: getAppointmentStatusLabel('CONFIRMED'),
  CHECKED_IN: getAppointmentStatusLabel('CHECKED_IN'),
  NO_SHOW: getAppointmentStatusLabel('NO_SHOW'),
  ACTIVE: 'Đang hoạt động',
  REOPENED: 'Đang khám lại',
  DRAFT: 'Nháp',
  ISSUED: 'Chờ thanh toán',
  DISPENSED: 'Đã phát thuốc',
  WAITING_PAYMENT: 'Chờ thanh toán',
  WAITING_RESULTS: 'Chờ kết quả',
  READY_FOR_CONCLUSION: 'Sẵn sàng kết luận',
  COMPLETED: getAppointmentStatusLabel('COMPLETED'),
  CANCELLED: getAppointmentStatusLabel('CANCELLED'),
  UNPAID: getPaymentStatusLabel('UNPAID'),
  PENDING_CONFIRMATION: getPaymentStatusLabel('PENDING_CONFIRMATION'),
  PAYMENT_REVIEW: getPaymentStatusLabel('PAYMENT_REVIEW'),
  PAID: getPaymentStatusLabel('PAID'),
  PARTIALLY_REFUNDED: getPaymentStatusLabel('PARTIALLY_REFUNDED'),
  REFUNDED: getPaymentStatusLabel('REFUNDED'),
  VOID: getPaymentStatusLabel('VOID'),
  INACTIVE: 'Ngưng hoạt động',
  SELF_INACTIVE: 'Bác sĩ ngưng hoạt động',
  BRANCH_INACTIVE: 'Chi nhánh tạm ngưng hoạt động',
  SPECIALTY_INACTIVE: 'Chuyên khoa tạm ngưng hoạt động',
};

interface StatusBadgeProps {
  status: string;
  label?: string;
  className?: string;
}

export function StatusBadge({ status, label: labelOverride, className = '' }: StatusBadgeProps) {
  const { t } = useTranslation();
  const cls = STATUS_CLASSES[status] || statusToneClasses.neutral;
  const label = labelOverride || STATUS_LABELS[status] || t(`status.${status}`, status);

  return (
    <span
      className={cn(
        'inline-flex items-center whitespace-nowrap rounded-full border px-2.5 py-1 text-xs font-medium leading-none',
        cls,
        className,
      )}
    >
      {label}
    </span>
  );
}
