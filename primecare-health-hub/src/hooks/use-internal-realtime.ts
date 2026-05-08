import { useEffect } from 'react';
import { Client, type StompSubscription } from '@stomp/stompjs';
import { type QueryKey, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { useAuthStore } from '@/stores/auth-store';
import {
  appendNotificationToCache,
  normalizeInternalNotification,
  notificationQueryKeys,
} from '@/hooks/use-notification-data';
import type { AppRole, CurrentUser, InternalNotification } from '@/types/api';

function buildWebSocketUrl() {
  if (typeof window === 'undefined') return null;

  const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || '/api';
  const normalizedApiBaseUrl = apiBaseUrl.replace(/\/$/, '');

  const absoluteHttpBase = normalizedApiBaseUrl.startsWith('http')
    ? normalizedApiBaseUrl
    : `${window.location.origin}${normalizedApiBaseUrl.startsWith('/') ? '' : '/'}${normalizedApiBaseUrl}`;

  const appBaseUrl = absoluteHttpBase.replace(/\/api$/, '');
  return `${appBaseUrl.replace(/^http/, 'ws')}/ws`;
}

type NotificationPayload = {
  event?: string;
  id?: string | number;
  notificationId?: string | number;
  title?: string;
  message?: string;
  body?: string;
  route?: string;
  entityType?: string;
  entityId?: string | number | null;
  type?: string;
  severity?: string;
  level?: string;
  silent?: boolean | string;
  targetUserId?: string | number | null;
  recipientUserId?: string | number | null;
  userId?: string | number | null;
  targetRole?: string;
  role?: string;
  branchId?: string | number | null;
  createdAt?: string;
  read?: boolean;
};

type RealtimeNotificationSource = 'role' | 'user';

const INVALIDATE_DEBOUNCE_MS = 500;
const TOAST_DEDUPE_MS = 15_000;
const BACKGROUND_EVENTS = new Set([
  'SUMMARY_UPDATED',
  'APPOINTMENT_SUMMARY_CHANGED',
  'QUEUE_REFRESH',
  'CASHIER_SUMMARY_CHANGED',
  'SERVICE_DESK_SUMMARY_CHANGED',
  'PHARMACY_SUMMARY_CHANGED',
  'HEARTBEAT',
]);
const HIGH_PRIORITY_SEVERITIES = new Set(['WARNING', 'ERROR', 'URGENT', 'CRITICAL']);

function canSubscribe(role: AppRole, allowedRoles: AppRole[]) {
  return allowedRoles.includes(role);
}

function normalizeBranchId(branchId?: string | number | null) {
  if (branchId == null) return null;
  const normalized = String(branchId).trim();
  return normalized || null;
}

function getPayloadText(value: unknown) {
  return typeof value === 'string' && value.trim() ? value.trim() : null;
}

function normalizeToken(value: unknown) {
  return typeof value === 'string' ? value.trim().toUpperCase() : '';
}

function containsSummary(value: unknown) {
  return typeof value === 'string' && value.toLowerCase().includes('summary');
}

function readPayloadUserId(payload: NotificationPayload) {
  const candidate = payload.targetUserId ?? payload.recipientUserId ?? payload.userId;
  return candidate != null ? String(candidate) : null;
}

function readPayloadRole(payload: NotificationPayload) {
  const role = payload.targetRole ?? payload.role;
  return typeof role === 'string' && role.trim() ? role.trim().toUpperCase() : null;
}

function isBackgroundNotification(
  payload: NotificationPayload,
  notification: InternalNotification,
) {
  const event = normalizeToken(payload.event);
  const type = normalizeToken(payload.type ?? notification.type);

  if (payload.silent === true || String(payload.silent).toLowerCase() === 'true') return true;
  if (BACKGROUND_EVENTS.has(event) || BACKGROUND_EVENTS.has(type)) return true;
  if (containsSummary(payload.route) || containsSummary(notification.route)) return true;
  if (containsSummary(payload.type) || containsSummary(notification.type)) return true;

  return false;
}

function notificationMatchesActionableRole(
  payload: NotificationPayload,
  notification: InternalNotification,
  role: AppRole,
) {
  const haystack = [
    payload.type,
    notification.type,
    payload.entityType,
    notification.entityType,
    payload.route,
    notification.route,
  ]
    .filter(Boolean)
    .join(' ')
    .toUpperCase();

  const hasAny = (tokens: string[]) => tokens.some((token) => haystack.includes(token));

  switch (role) {
    case 'STAFF':
      return hasAny(['APPOINTMENT', 'CHECKED_IN', 'PATIENT_CHECKED_IN', 'WAITING_EXAM']);
    case 'CASHIER':
      return hasAny(['CASHIER', 'INVOICE', 'PAYMENT', 'BANK_TRANSFER', 'PAYMENT_REVIEW']);
    case 'SERVICE_TECHNICIAN':
      return hasAny(['SERVICE_DESK', 'SERVICE_RESULT', 'RESULT_VERIFY', 'RESULT_READY']);
    case 'PHARMACIST':
      return hasAny(['PHARMACY', 'PRESCRIPTION', 'DISPENSE']);
    case 'DOCTOR':
      return hasAny(['DOCTOR', 'ENCOUNTER', 'PATIENT_CHECKED_IN', 'WAITING_EXAM']);
    default:
      return false;
  }
}

function shouldShowRealtimeToast(
  payload: NotificationPayload,
  notification: InternalNotification,
  user: CurrentUser,
  source: RealtimeNotificationSource,
) {
  const title = getPayloadText(payload.title);
  const message = getPayloadText(payload.message) ?? getPayloadText(payload.body);
  if (!title || !message) return false;
  if (isBackgroundNotification(payload, notification)) return false;

  const targetUserId = readPayloadUserId(payload);
  if (targetUserId && targetUserId !== String(user.id)) return false;

  const targetRole = readPayloadRole(payload);
  if (targetRole && targetRole !== user.role) return false;

  if (targetUserId === String(user.id) || source === 'user') return true;

  const severity = normalizeToken(notification.severity ?? payload.severity ?? payload.level);
  if (HIGH_PRIORITY_SEVERITIES.has(severity)) return true;

  return notificationMatchesActionableRole(payload, notification, user.role);
}

function buildToastKey(
  payload: NotificationPayload,
  notification: InternalNotification,
  title: string,
  message: string,
) {
  return [
    notification.id || payload.notificationId || payload.id || payload.type || 'notification',
    notification.route || payload.route || '',
    notification.entityId || payload.entityId || '',
    title,
    message,
  ].join('|');
}

function getNotificationDomain(
  payload: NotificationPayload,
  notification: InternalNotification,
) {
  const text = [
    payload.type,
    notification.type,
    payload.entityType,
    notification.entityType,
    payload.route,
    notification.route,
  ]
    .filter(Boolean)
    .join(' ')
    .toUpperCase();

  if (text.includes('/CASHIER') || text.includes('CASHIER') || text.includes('INVOICE') || text.includes('PAYMENT')) {
    return 'cashier';
  }
  if (text.includes('/SERVICE-DESK') || text.includes('SERVICE_DESK') || text.includes('SERVICE_RESULT')) {
    return 'service-desk';
  }
  if (
    text.includes('/PHARMACY') ||
    text.includes('PHARMACY') ||
    text.includes('PRESCRIPTION') ||
    text.includes('MEDICATION') ||
    text.includes('BATCH') ||
    text.includes('INVENTORY')
  ) {
    return 'pharmacy';
  }
  if (text.includes('/APPOINTMENTS') || text.includes('/RECEPTION') || text.includes('APPOINTMENT') || text.includes('QUEUE')) {
    return 'appointment';
  }
  if (text.includes('/DOCTOR') || text.includes('DOCTOR') || text.includes('ENCOUNTER')) {
    return 'doctor';
  }

  return null;
}

function appointmentInvalidationKeys(role: AppRole, appointmentId?: string): QueryKey[] {
  if (canSubscribe(role, ['STAFF', 'OPERATIONS_ADMIN', 'SYSTEM_ADMIN'])) {
    return [
      ['reception', 'queue'],
      ['reception', 'queue', 'summary'],
      ['admin', 'appointments'],
      ['admin', 'appointments', 'summary'],
      ...(appointmentId ? ([['admin', 'appointments', 'detail', appointmentId]] as QueryKey[]) : []),
    ];
  }

  if (role === 'DOCTOR') {
    return [
      ['doctor', 'appointments'],
      ['doctor', 'appointments', 'summary'],
    ];
  }

  return [];
}

function receptionInvalidationKeys(): QueryKey[] {
  return [
    ['reception', 'queue'],
    ['reception', 'queue', 'summary'],
  ];
}

function cashierInvalidationKeys(role: AppRole): QueryKey[] {
  if (!canSubscribe(role, ['CASHIER', 'OPERATIONS_ADMIN', 'SYSTEM_ADMIN'])) return [];
  return [
    ['cashier', 'summary'],
    ['cashier', 'service-orders'],
    ['cashier', 'invoices'],
    ['cashier', 'invoice'],
  ];
}

function serviceDeskInvalidationKeys(role: AppRole): QueryKey[] {
  if (!canSubscribe(role, ['SERVICE_TECHNICIAN', 'OPERATIONS_ADMIN', 'SYSTEM_ADMIN'])) return [];
  return [
    ['service-desk', 'queue'],
    ['service-desk', 'summary'],
  ];
}

function pharmacyInvalidationKeys(role: AppRole): QueryKey[] {
  if (!canSubscribe(role, ['PHARMACIST', 'OPERATIONS_ADMIN'])) return [];
  return [
    ['pharmacist-prescriptions'],
    ['pharmacy-inventory'],
    ['pharmacy-batches'],
    ['pharmacy-expiring-batches'],
  ];
}

function doctorInvalidationKeys(role: AppRole): QueryKey[] {
  if (role !== 'DOCTOR') return [];
  return [
    ['doctor', 'appointments'],
    ['doctor', 'appointments', 'summary'],
  ];
}

export function useInternalRealtime() {
  const accessToken = useAuthStore((state) => state.accessToken);
  const user = useAuthStore((state) => state.user);
  const qc = useQueryClient();

  useEffect(() => {
    const wsUrl = buildWebSocketUrl();
    if (!accessToken || !user || !wsUrl || user.role === 'PATIENT') {
      return;
    }

    const branchId = normalizeBranchId(user.branchId);
    const seenNotificationIds = new Set<string>();
    const recentToastKeys = new Map<string, number>();
    const scheduledInvalidations = new Map<string, QueryKey>();
    let invalidateTimer: number | undefined;
    let subscriptions: StompSubscription[] = [];

    const scheduleInvalidate = (queryKey: QueryKey) => {
      scheduledInvalidations.set(JSON.stringify(queryKey), queryKey);

      if (invalidateTimer) {
        window.clearTimeout(invalidateTimer);
      }

      invalidateTimer = window.setTimeout(() => {
        const keys = Array.from(scheduledInvalidations.values());
        scheduledInvalidations.clear();
        invalidateTimer = undefined;
        keys.forEach((key) => {
          qc.invalidateQueries({ queryKey: key, refetchType: 'active' });
        });
      }, INVALIDATE_DEBOUNCE_MS);
    };

    const scheduleMany = (queryKeys: QueryKey[]) => {
      queryKeys.forEach(scheduleInvalidate);
    };

    const clearSubscriptions = () => {
      subscriptions.forEach((subscription) => subscription.unsubscribe());
      subscriptions = [];
    };

    const debugMissingBranch = () => {
      if (import.meta.env.DEV) {
        console.debug(`[internal-realtime] Skip branch subscriptions for ${user.role}: missing branchId`);
      }
    };

    const handleDomainNotification = (
      payload: NotificationPayload,
      notification: InternalNotification,
    ) => {
      const domain = getNotificationDomain(payload, notification);
      switch (domain) {
        case 'appointment':
          scheduleMany(appointmentInvalidationKeys(user.role, notification.entityId));
          break;
        case 'cashier':
          scheduleMany(cashierInvalidationKeys(user.role));
          break;
        case 'service-desk':
          scheduleMany(serviceDeskInvalidationKeys(user.role));
          break;
        case 'pharmacy':
          scheduleMany(pharmacyInvalidationKeys(user.role));
          break;
        case 'doctor':
          scheduleMany(doctorInvalidationKeys(user.role));
          break;
        default:
          break;
      }
    };

    const maybeToastNotification = (
      payload: NotificationPayload,
      notification: InternalNotification,
      source: RealtimeNotificationSource,
    ) => {
      if (!shouldShowRealtimeToast(payload, notification, user, source)) return;

      const title = getPayloadText(payload.title);
      const message = getPayloadText(payload.message) ?? getPayloadText(payload.body);
      if (!title || !message) return;

      const toastKey = buildToastKey(payload, notification, title, message);
      const now = Date.now();
      recentToastKeys.forEach((shownAt, key) => {
        if (now - shownAt > TOAST_DEDUPE_MS) recentToastKeys.delete(key);
      });
      const lastShownAt = recentToastKeys.get(toastKey);
      if (lastShownAt && now - lastShownAt < TOAST_DEDUPE_MS) return;

      recentToastKeys.set(toastKey, now);
      toast(title, { description: message, id: toastKey });
    };

    const client = new Client({
      brokerURL: wsUrl,
      connectHeaders: {
        Authorization: `Bearer ${accessToken}`,
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: () => {},
    });

    client.onConnect = () => {
      clearSubscriptions();

      const subscribe = (destination: string, callback: (messageBody: string) => void) => {
        subscriptions.push(client.subscribe(destination, (message) => callback(message.body)));
      };

      const handleInternalNotification = (
        messageBody: string,
        source: RealtimeNotificationSource,
      ) => {
        try {
          const payload = JSON.parse(messageBody) as NotificationPayload;
          if (payload.event !== 'INTERNAL_NOTIFICATION') return;

          const notification = normalizeInternalNotification(payload);
          if (notification.id) {
            if (seenNotificationIds.has(notification.id)) return;
            seenNotificationIds.add(notification.id);
          }
          if (notification.id) {
            appendNotificationToCache(qc, notification);
          } else {
            scheduleMany([
              ['internal', 'notifications'],
              notificationQueryKeys.unreadCount,
            ]);
          }

          handleDomainNotification(payload, notification);
          maybeToastNotification(payload, notification, source);
        } catch {
          // ignore malformed notification payloads
        }
      };

      if (canSubscribe(user.role, ['SYSTEM_ADMIN', 'OPERATIONS_ADMIN'])) {
        subscribe(`/topic/notifications/role/${user.role}`, (messageBody) => {
          handleInternalNotification(messageBody, 'role');
        });
      }

      subscribe(`/topic/notifications/user/${user.id}`, (messageBody) => {
        handleInternalNotification(messageBody, 'user');
      });

      if (user.role === 'STAFF') {
        if (!branchId) {
          debugMissingBranch();
        } else {
          subscribe(`/topic/branches/${branchId}/appointments/summary`, () => {
            scheduleMany(appointmentInvalidationKeys(user.role));
          });
          subscribe(`/topic/branches/${branchId}/reception/queue`, () => {
            scheduleMany(receptionInvalidationKeys());
          });
        }
      }

      if (user.role === 'CASHIER') {
        if (!branchId) {
          debugMissingBranch();
        } else {
          subscribe(`/topic/branches/${branchId}/cashier/orders`, () => {
            scheduleMany(cashierInvalidationKeys(user.role));
          });
        }
      }

      if (user.role === 'SERVICE_TECHNICIAN') {
        if (!branchId) {
          debugMissingBranch();
        } else {
          subscribe(`/topic/branches/${branchId}/service-desk/updates`, () => {
            scheduleMany(serviceDeskInvalidationKeys(user.role));
          });
        }
      }

      if (user.role === 'PHARMACIST') {
        if (!branchId) {
          debugMissingBranch();
        } else {
          subscribe(`/topic/branches/${branchId}/pharmacy/updates`, () => {
            scheduleMany(pharmacyInvalidationKeys(user.role));
          });
        }
      }

      if (canSubscribe(user.role, ['OPERATIONS_ADMIN', 'SYSTEM_ADMIN'])) {
        subscribe('/topic/appointments/summary', () => {
          scheduleMany(appointmentInvalidationKeys(user.role));
        });
        subscribe('/topic/service-desk/updates', () => {
          scheduleMany(serviceDeskInvalidationKeys(user.role));
        });
        subscribe('/topic/cashier/orders', () => {
          scheduleMany(cashierInvalidationKeys(user.role));
        });
      }
    };

    client.activate();
    return () => {
      if (invalidateTimer) {
        window.clearTimeout(invalidateTimer);
      }
      clearSubscriptions();
      void client.deactivate();
    };
  }, [accessToken, qc, user]);
}
