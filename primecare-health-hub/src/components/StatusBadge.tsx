import { useTranslation } from 'react-i18next';
import { cn } from '@/lib/utils';
import { getAppointmentStatusLabel, getPaymentStatusLabel } from '@/lib/filter-options';

const STATUS_CLASSES: Record<string, string> = {
  REQUESTED: 'bg-warning/10 text-warning border-warning/20',
  PENDING: 'bg-warning/10 text-warning border-warning/20',
  CONFIRMED: 'bg-primary/10 text-primary border-primary/20',
  CHECKED_IN: 'bg-accent/10 text-accent border-accent/20',
  IN_PROGRESS: 'bg-primary/10 text-primary border-primary/20',
  REOPENED: 'bg-primary/10 text-primary border-primary/20',
  WAITING_PAYMENT: 'bg-warning/10 text-warning border-warning/20',
  WAITING_RESULTS: 'bg-primary/10 text-primary border-primary/20',
  READY_FOR_CONCLUSION: 'bg-success/10 text-success border-success/20',
  COMPLETED: 'bg-success/10 text-success border-success/20',
  CANCELLED: 'bg-destructive/10 text-destructive border-destructive/20',
  NO_SHOW: 'bg-muted text-muted-foreground border-border',
  RESCHEDULED: 'bg-champagne/10 text-champagne border-champagne/20',

  ACTIVE: 'bg-success/10 text-success border-success/20',
  INACTIVE: 'bg-muted text-muted-foreground border-border',

  SELF_INACTIVE: 'bg-muted text-muted-foreground border-border',
  BRANCH_INACTIVE: 'bg-warning/10 text-warning border-warning/20',
  SPECIALTY_INACTIVE: 'bg-champagne/10 text-champagne border-champagne/20',

  DRAFT: 'bg-muted text-muted-foreground border-border',
  VERIFIED: 'bg-success/10 text-success border-success/20',
  PENDING_PAYMENT: 'bg-warning/10 text-warning border-warning/20',
  WAITING_EXECUTION: 'bg-primary/10 text-primary border-primary/20',
  DONE: 'bg-success/10 text-success border-success/20',
  ISSUED: 'bg-success/10 text-success border-success/20',
  DISPENSED: 'bg-success/10 text-success border-success/20',
  UNPAID: 'bg-warning/10 text-warning border-warning/20',
  PENDING_CONFIRMATION: 'bg-primary/10 text-primary border-primary/20',
  PAYMENT_REVIEW: 'bg-warning/10 text-warning border-warning/20',
  REFUNDED: 'bg-muted text-muted-foreground border-border',
  PAID: 'bg-success/10 text-success border-success/20',
  VOID: 'bg-muted text-muted-foreground border-border',
  APPROVED: 'bg-success/10 text-success border-success/20',
  REJECTED: 'bg-destructive/10 text-destructive border-destructive/20',
  PROCESSING: 'bg-primary/10 text-primary border-primary/20',
  FAILED: 'bg-destructive/10 text-destructive border-destructive/20',
  AVAILABLE: 'bg-success/10 text-success border-success/20',
  ALMOST_FULL: 'bg-warning/10 text-warning border-warning/20',
  FULL: 'bg-destructive/10 text-destructive border-destructive/20',
};

const STATUS_LABELS: Record<string, string> = {
  REQUESTED: getAppointmentStatusLabel('REQUESTED'),
  CONFIRMED: getAppointmentStatusLabel('CONFIRMED'),
  CHECKED_IN: getAppointmentStatusLabel('CHECKED_IN'),
  NO_SHOW: getAppointmentStatusLabel('NO_SHOW'),
  ACTIVE: 'Đang hoạt động',
  REOPENED: 'Đang khám lại',
  WAITING_PAYMENT: 'Chờ thanh toán',
  WAITING_RESULTS: 'Chờ kết quả',
  READY_FOR_CONCLUSION: 'Sẵn sàng kết luận',
  COMPLETED: getAppointmentStatusLabel('COMPLETED'),
  CANCELLED: getAppointmentStatusLabel('CANCELLED'),
  UNPAID: getPaymentStatusLabel('UNPAID'),
  PENDING_CONFIRMATION: getPaymentStatusLabel('PENDING_CONFIRMATION'),
  PAYMENT_REVIEW: getPaymentStatusLabel('PAYMENT_REVIEW'),
  PAID: getPaymentStatusLabel('PAID'),
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
  const cls = STATUS_CLASSES[status] || 'bg-muted text-muted-foreground border-border';
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
