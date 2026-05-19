import { Link } from 'react-router-dom';
import { Bell, CheckCheck } from 'lucide-react';
import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { PageHeader } from '@/components/PageHeader';
import {
  useMarkAllNotificationsRead,
  useMarkNotificationRead,
  useNotifications,
} from '@/hooks/use-notification-data';
import { canAccessInternalRoute } from '@/lib/route-access';
import { useAuthStore } from '@/stores/auth-store';
import type { AppRole, InternalNotification } from '@/types/api';

function formatNotificationTime(value?: string) {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('vi-VN');
}

function getAllowedNotificationRoute(item: InternalNotification, role?: AppRole) {
  if (!item.route || !role) return undefined;
  return canAccessInternalRoute(item.route, role) ? item.route : undefined;
}

export default function NotificationsAdminPage() {
  const [page, setPage] = useState(1);
  const userRole = useAuthStore((state) => state.user?.role);
  const notificationsQuery = useNotifications({ page: String(page - 1), size: '20' });
  const markRead = useMarkNotificationRead();
  const markAllRead = useMarkAllNotificationsRead();

  const notifications = notificationsQuery.data?.items ?? [];
  const totalPages = Math.max(notificationsQuery.data?.meta.totalPages ?? 1, 1);

  const handleOpen = (item: InternalNotification) => {
    if (!item.read) markRead.mutate(item.id);
  };

  return (
    <div className="space-y-4">
      <PageHeader
        title="Trung tâm thông báo"
        description="Theo dõi thông báo hệ thống và mở các tác vụ hợp lệ theo quyền hiện tại."
        actions={
          <Button
            variant="outline"
            onClick={() => markAllRead.mutate()}
            disabled={notifications.length === 0 || markAllRead.isPending}
          >
            <CheckCheck className="mr-2 h-4 w-4" />
            Đánh dấu đã đọc
          </Button>
        }
      />

      <Card>
        <CardContent className="space-y-2 p-4">
          {notificationsQuery.isLoading ? (
            <div className="py-10 text-center text-sm text-muted-foreground">Đang tải thông báo...</div>
          ) : notifications.length === 0 ? (
            <div className="py-10 text-center text-sm text-muted-foreground">Chưa có thông báo nào.</div>
          ) : (
            notifications.map((item) => {
              const route = getAllowedNotificationRoute(item, userRole);

              return (
                <div
                  key={`${item.id}-${item.createdAt}`}
                  className="flex flex-col gap-3 rounded-lg border border-border/70 bg-background px-4 py-3 md:flex-row md:items-center md:justify-between"
                >
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <Bell className="h-4 w-4 text-primary" />
                      <p className="font-medium text-foreground">{item.title}</p>
                      {!item.read ? (
                        <span className="rounded-full bg-primary px-2 py-0.5 text-[11px] font-medium text-primary-foreground">
                          Mới
                        </span>
                      ) : null}
                    </div>
                    <p className="mt-1 text-sm text-muted-foreground">{item.message}</p>
                    <p className="mt-1 text-xs text-muted-foreground">
                      {formatNotificationTime(item.createdAt)}
                    </p>
                  </div>
                  {route ? (
                    <Button asChild size="sm" variant="outline" onClick={() => handleOpen(item)}>
                      <Link to={route}>Mở</Link>
                    </Button>
                  ) : (
                    <Button size="sm" variant="outline" disabled>
                      Không có đích hợp lệ
                    </Button>
                  )}
                </div>
              );
            })
          )}
        </CardContent>
      </Card>

      <div className="flex items-center justify-end gap-2">
        <Button
          variant="outline"
          size="sm"
          onClick={() => setPage((current) => Math.max(1, current - 1))}
          disabled={page <= 1}
        >
          Trước
        </Button>
        <span className="text-sm text-muted-foreground">
          Trang {page}/{totalPages}
        </span>
        <Button
          variant="outline"
          size="sm"
          onClick={() => setPage((current) => Math.min(totalPages, current + 1))}
          disabled={page >= totalPages}
        >
          Sau
        </Button>
      </div>
    </div>
  );
}
