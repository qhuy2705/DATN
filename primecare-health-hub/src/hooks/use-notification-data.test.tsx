import type { ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import apiClient from '@/lib/api-client';
import {
  appendNotificationToCache,
  normalizeInternalNotification,
  notificationQueryKeys,
  useMarkNotificationRead,
} from '@/hooks/use-notification-data';
import type { InternalNotification, PageResponse } from '@/types/api';

function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
}

function createNotification(overrides: Partial<InternalNotification> = {}): InternalNotification {
  return {
    id: 'n-1',
    title: 'Thông báo',
    message: 'Có cập nhật mới',
    createdAt: '2026-05-05T00:00:00.000Z',
    read: false,
    ...overrides,
  };
}

function page(items: InternalNotification[]): PageResponse<InternalNotification> {
  return {
    items,
    meta: {
      page: 0,
      size: 20,
      totalItems: items.length,
      totalPages: 1,
      hasNext: false,
      hasPrev: false,
    },
  };
}

describe('notification cache behavior', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('appends realtime notifications once per id and increments unread count once', () => {
    const qc = createQueryClient();
    const listKey = notificationQueryKeys.list({ page: '0', size: '20' });
    const existing = createNotification({ id: 'n-0', read: false });
    const incoming = createNotification({ id: 'n-1', read: false });

    qc.setQueryData(listKey, page([existing]));
    qc.setQueryData(notificationQueryKeys.unreadCount, 1);

    appendNotificationToCache(qc, incoming);
    appendNotificationToCache(qc, incoming);

    const cached = qc.getQueryData<PageResponse<InternalNotification>>(listKey);
    expect(cached?.items.map((item) => item.id)).toEqual(['n-1', 'n-0']);
    expect(qc.getQueryData(notificationQueryKeys.unreadCount)).toBe(2);
  });

  it('mark read mutation patches list cache and invalidates list plus unread count', async () => {
    const qc = createQueryClient();
    const listKey = notificationQueryKeys.list({ page: '0', size: '20' });
    qc.setQueryData(listKey, page([createNotification({ id: 'n-1', read: false })]));
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');
    vi.spyOn(apiClient, 'patch').mockResolvedValue({ data: { success: true } });

    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={qc}>{children}</QueryClientProvider>
    );
    const { result } = renderHook(() => useMarkNotificationRead(), { wrapper });

    await act(async () => {
      await result.current.mutateAsync('n-1');
    });

    expect(apiClient.patch).toHaveBeenCalledWith('/internal/notifications/n-1/read');
    expect(qc.getQueryData<PageResponse<InternalNotification>>(listKey)?.items[0].read).toBe(true);
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['internal', 'notifications'] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: notificationQueryKeys.unreadCount });
  });

  it('preserves backend route used by notification click navigation', () => {
    const notification = normalizeInternalNotification({
      id: 'n-route',
      title: 'Kho dược',
      message: 'Có lô thuốc sắp hết hạn',
      route: '/app/pharmacy/inventory',
      entityType: 'BATCH',
    });

    expect(notification.route).toBe('/app/pharmacy/inventory');
    expect(notification.entityType).toBe('BATCH');
  });
});
