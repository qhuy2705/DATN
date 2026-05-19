type RefundStatusRecord = {
  status?: string;
  itemStatus?: string;
  refundStatus?: string;
  refunded?: boolean;
  cancelled?: boolean;
  canceled?: boolean;
  refundedAt?: string;
  cancelledAt?: string;
  canceledAt?: string;
  refundReason?: string;
  cancellationReason?: string;
  cancelReason?: string;
  notRefundableReason?: string;
};

export const SERVICE_REFUNDED_LABEL = 'Đã hủy / đã hoàn tiền';
export const MEDICATION_REFUNDED_LABEL = 'Đã hủy / đã hoàn tiền trước khi phát';

function normalizedStatusValues(item?: RefundStatusRecord | null) {
  return [item?.status, item?.itemStatus, item?.refundStatus]
    .filter(Boolean)
    .map((value) => String(value).toUpperCase());
}

export function isRefundedOrCancelledItem(item?: RefundStatusRecord | null) {
  if (!item) return false;
  if (item.refunded || item.cancelled || item.canceled || item.refundedAt || item.cancelledAt || item.canceledAt) return true;

  return normalizedStatusValues(item).some(
    (status) =>
      status === 'REFUNDED' ||
      status === 'PARTIALLY_REFUNDED' ||
      status === 'CANCELLED' ||
      status === 'CANCELED' ||
      status === 'VOID' ||
      status.includes('REFUND'),
  );
}

export function getRefundReason(item?: RefundStatusRecord | null) {
  return item?.refundReason || item?.cancellationReason || item?.cancelReason || item?.notRefundableReason || '';
}

export function getRefundedAt(item?: RefundStatusRecord | null) {
  return item?.refundedAt || item?.cancelledAt || item?.canceledAt || '';
}
