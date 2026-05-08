import { type QueryClient, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import apiClient from '@/lib/api-client';
import { unwrapApiData, unwrapPage } from '@/lib/api-adapters';
import { getApiErrorMessage } from '@/lib/error-utils';
import type { InternalNotification, PageResponse } from '@/types/api';

export const notificationQueryKeys = {
  list: (params?: Record<string, string>) => ['internal', 'notifications', params] as const,
  unreadCount: ['internal', 'notifications', 'unread-count'] as const,
};

export function normalizeInternalNotification(raw: unknown): InternalNotification {
  const item = (raw ?? {}) as Record<string, unknown>;
  const readAt = typeof item.readAt === 'string' ? item.readAt : undefined;
  const read =
    typeof item.read === 'boolean'
      ? item.read
      : typeof item.unread === 'boolean'
        ? !item.unread
        : Boolean(readAt);

  return {
    id: String(item.id ?? item.notificationId ?? ''),
    title:
      typeof item.title === 'string' && item.title.trim() ? item.title : 'Thông báo mới',
    message:
      typeof item.message === 'string' && item.message.trim()
        ? item.message
        : typeof item.body === 'string' && item.body.trim()
          ? item.body
          : 'Có cập nhật mới trong hệ thống.',
    route: typeof item.route === 'string' && item.route.trim() ? item.route : undefined,
    entityType:
      typeof item.entityType === 'string' && item.entityType.trim()
        ? item.entityType
        : undefined,
    entityId:
      item.entityId !== null && typeof item.entityId !== 'undefined'
        ? String(item.entityId)
        : undefined,
    type: typeof item.type === 'string' && item.type.trim() ? item.type : undefined,
    severity:
      typeof item.severity === 'string' && item.severity.trim()
        ? item.severity
        : typeof item.level === 'string' && item.level.trim()
          ? item.level
          : 'INFO',
    createdAt:
      typeof item.createdAt === 'string'
        ? item.createdAt
        : typeof item.occurredAt === 'string'
          ? item.occurredAt
          : new Date().toISOString(),
    read,
    readAt,
  };
}

function normalizeUnreadCount(raw: unknown) {
  const data = unwrapApiData(raw);
  if (typeof data === 'number') return data;
  if (typeof data === 'string') {
    const parsed = Number(data);
    return Number.isFinite(parsed) ? parsed : 0;
  }
  if (typeof data === 'object' && data !== null) {
    const record = data as Record<string, unknown>;
    const value = record.count ?? record.unreadCount ?? record.total;
    if (typeof value === 'number') return value;
    if (typeof value === 'string') {
      const parsed = Number(value);
      return Number.isFinite(parsed) ? parsed : 0;
    }
  }
  return 0;
}

function patchNotificationLists(
  qc: QueryClient,
  updater: (items: InternalNotification[]) => InternalNotification[],
) {
  const listQueries = qc.getQueriesData<PageResponse<InternalNotification>>({
    queryKey: ['internal', 'notifications'],
  });

  listQueries.forEach(([queryKey, cacheValue]) => {
    if (!cacheValue?.items) return;
    qc.setQueryData(queryKey, {
      ...cacheValue,
      items: updater(cacheValue.items),
    });
  });
}

export function appendNotificationToCache(qc: QueryClient, notification: InternalNotification) {
  if (!notification.id) {
    qc.invalidateQueries({ queryKey: ['internal', 'notifications'] });
    qc.invalidateQueries({ queryKey: notificationQueryKeys.unreadCount });
    return;
  }

  let appended = false;
  const listQueries = qc.getQueriesData<PageResponse<InternalNotification>>({
    queryKey: ['internal', 'notifications'],
  });

  listQueries.forEach(([queryKey, cacheValue]) => {
    if (!cacheValue?.items) return;
    if (cacheValue.items.some((item) => String(item.id) === String(notification.id))) return;
    appended = true;
    qc.setQueryData(queryKey, {
      ...cacheValue,
      items: [notification, ...cacheValue.items].slice(0, 20),
    });
  });

  if ((appended || listQueries.length === 0) && !notification.read) {
    qc.setQueryData<number>(notificationQueryKeys.unreadCount, (current) => (current ?? 0) + 1);
    return;
  }

  if (!appended) {
    qc.invalidateQueries({ queryKey: ['internal', 'notifications'], refetchType: 'inactive' });
  }
}

export function useNotifications(params?: Record<string, string>, enabled = true) {
  return useQuery({
    queryKey: notificationQueryKeys.list(params),
    enabled,
    queryFn: async () => {
      const { data } = await apiClient.get('/internal/notifications', { params });
      const page = unwrapPage<unknown>(data);
      return {
        ...page,
        items: page.items.map(normalizeInternalNotification),
      };
    },
    staleTime: 30_000,
    placeholderData: (previousData) => previousData,
  });
}

export function useUnreadNotificationCount() {
  return useQuery({
    queryKey: notificationQueryKeys.unreadCount,
    queryFn: async () => {
      const { data } = await apiClient.get('/internal/notifications/unread-count');
      return normalizeUnreadCount(data);
    },
    staleTime: 30_000,
    refetchOnWindowFocus: true,
  });
}

export function useMarkNotificationRead() {
  const qc = useQueryClient();

  return useMutation({
    mutationFn: async (id: string) => {
      await apiClient.patch(`/internal/notifications/${id}/read`);
      return id;
    },
    onMutate: async (id) => {
      patchNotificationLists(qc, (items) =>
        items.map((item) => {
          if (String(item.id) !== String(id)) return item;
          return { ...item, read: true, readAt: new Date().toISOString() };
        }),
      );
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['internal', 'notifications'] });
      qc.invalidateQueries({ queryKey: notificationQueryKeys.unreadCount });
    },
    onError: (error) => {
      qc.invalidateQueries({ queryKey: ['internal', 'notifications'] });
      qc.invalidateQueries({ queryKey: notificationQueryKeys.unreadCount });
      toast.error(getApiErrorMessage(error, 'Không thể đánh dấu thông báo đã đọc'));
    },
  });
}

export function useMarkAllNotificationsRead() {
  const qc = useQueryClient();

  return useMutation({
    mutationFn: async () => {
      await apiClient.patch('/internal/notifications/read-all');
    },
    onMutate: () => {
      patchNotificationLists(qc, (items) =>
        items.map((item) => ({ ...item, read: true, readAt: new Date().toISOString() })),
      );
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['internal', 'notifications'] });
      qc.invalidateQueries({ queryKey: notificationQueryKeys.unreadCount });
    },
    onError: (error) => {
      qc.invalidateQueries({ queryKey: ['internal', 'notifications'] });
      qc.invalidateQueries({ queryKey: notificationQueryKeys.unreadCount });
      toast.error(getApiErrorMessage(error, 'Không thể đánh dấu tất cả đã đọc'));
    },
  });
}
