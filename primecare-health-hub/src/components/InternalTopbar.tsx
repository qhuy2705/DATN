import { useState } from 'react';
import {
  AlertTriangle,
  Bell,
  CheckCheck,
  CheckCircle2,
  Info,
  LogOut,
  Moon,
  Settings,
  Sun,
  XCircle,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import { SidebarTrigger } from '@/components/ui/sidebar';
import { UserAvatar } from '@/components/UserAvatar';
import { useAuthStore } from '@/stores/auth-store';
import { useThemeStore } from '@/stores/theme-store';
import { useLogout } from '@/hooks/use-auth';
import { LanguageSwitcher } from '@/components/LanguageSwitcher';
import { useTranslation } from 'react-i18next';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { ScrollArea } from '@/components/ui/scroll-area';
import {
  useMarkAllNotificationsRead,
  useMarkNotificationRead,
  useNotifications,
  useUnreadNotificationCount,
} from '@/hooks/use-notification-data';
import type { InternalNotification } from '@/types/api';

function formatNotificationTime(value?: string) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';

  const diffMs = Date.now() - date.getTime();
  const diffMinutes = Math.max(0, Math.floor(diffMs / 60_000));
  if (diffMinutes < 1) return 'Vừa xong';
  if (diffMinutes < 60) return `${diffMinutes} phút trước`;

  const diffHours = Math.floor(diffMinutes / 60);
  if (diffHours < 24) return `${diffHours} giờ trước`;

  return new Intl.DateTimeFormat('vi-VN', {
    hour: '2-digit',
    minute: '2-digit',
    day: '2-digit',
    month: '2-digit',
  }).format(date);
}

function notificationTone(notification: InternalNotification) {
  const severity = notification.severity?.toUpperCase();
  if (severity === 'ERROR' || severity === 'CRITICAL') {
    return {
      icon: XCircle,
      className: 'bg-destructive/10 text-destructive',
    };
  }
  if (severity === 'WARNING' || severity === 'WARN') {
    return {
      icon: AlertTriangle,
      className: 'bg-warning/10 text-warning',
    };
  }
  if (severity === 'SUCCESS') {
    return {
      icon: CheckCircle2,
      className: 'bg-success/10 text-success',
    };
  }
  return {
    icon: Info,
    className: 'bg-primary/10 text-primary',
  };
}

function getNotificationRoute(item: InternalNotification) {
  const entityType = item.entityType?.toUpperCase();
  if (
    entityType === 'INVENTORY' ||
    entityType === 'PHARMACY_INVENTORY' ||
    entityType === 'BATCH' ||
    entityType === 'MEDICATION_BATCH'
  ) {
    return item.route || '/app/pharmacy/inventory';
  }

  return item.route;
}

function NotificationItem({
  item,
  onRead,
}: {
  item: InternalNotification;
  onRead: (id: string) => void;
}) {
  const tone = notificationTone(item);
  const Icon = tone.icon;
  const content = (
    <div className="flex w-full items-start gap-3 py-1">
      <span className={`mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-full ${tone.className}`}>
        <Icon className="h-4 w-4" />
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex items-start justify-between gap-3">
          <p className="text-sm font-medium leading-5 text-foreground">{item.title}</p>
          <span className="shrink-0 text-[11px] text-muted-foreground">
            {formatNotificationTime(item.createdAt)}
          </span>
        </div>
        <p className="mt-1 text-xs leading-5 text-muted-foreground">{item.message}</p>
      </div>
      {!item.read ? <span className="mt-3 h-2 w-2 shrink-0 rounded-full bg-primary" /> : null}
    </div>
  );
  const route = getNotificationRoute(item);

  if (route) {
    return (
      <DropdownMenuItem asChild className="cursor-pointer items-start px-3 py-2">
        <Link to={route} onClick={() => onRead(item.id)}>
          {content}
        </Link>
      </DropdownMenuItem>
    );
  }

  return (
    <DropdownMenuItem
      className="cursor-pointer items-start px-3 py-2"
      onSelect={() => onRead(item.id)}
    >
      {content}
    </DropdownMenuItem>
  );
}

export function InternalTopbar() {
  const user = useAuthStore((s) => s.user);
  const { theme, toggleTheme } = useThemeStore();
  const logoutMutation = useLogout();
  const { t } = useTranslation();
  const [notificationsOpen, setNotificationsOpen] = useState(false);

  const unreadCountQuery = useUnreadNotificationCount();
  const notificationsQuery = useNotifications(
    { page: '0', size: '20' },
    notificationsOpen,
  );
  const markRead = useMarkNotificationRead();
  const markAllRead = useMarkAllNotificationsRead();

  const unreadCount = unreadCountQuery.data ?? 0;
  const notifications = notificationsQuery.data?.items ?? [];

  return (
    <header className="h-14 border-b bg-background flex items-center justify-between px-4 gap-4 shrink-0">
      <div className="flex items-center gap-3">
        <SidebarTrigger />
      </div>
      <div className="flex items-center gap-1.5">
        <LanguageSwitcher />
        <button onClick={toggleTheme} className="p-2 rounded-md hover:bg-muted transition-colors">
          {theme === 'light' ? <Moon className="h-4 w-4" /> : <Sun className="h-4 w-4" />}
        </button>

        <DropdownMenu open={notificationsOpen} onOpenChange={setNotificationsOpen}>
          <DropdownMenuTrigger asChild>
            <button className="p-2 rounded-md hover:bg-muted transition-colors relative">
              <Bell className="h-4 w-4" />
              {unreadCount > 0 && (
                <span className="absolute -top-0.5 -right-0.5 min-w-4 h-4 px-1 rounded-full bg-destructive text-[10px] text-destructive-foreground flex items-center justify-center">
                  {unreadCount > 9 ? '9+' : unreadCount}
                </span>
              )}
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-[23rem] p-0">
            <div className="flex items-center justify-between border-b px-3 py-2">
              <div>
                <p className="text-sm font-semibold">Thông báo</p>
                <p className="text-xs text-muted-foreground">
                  {unreadCountQuery.isLoading
                    ? 'Đang tải số chưa đọc'
                    : unreadCount > 0
                      ? `${unreadCount} chưa đọc`
                      : 'Bạn đã xem hết thông báo'}
                </p>
              </div>
              {notifications.length > 0 && (
                <button
                  type="button"
                  onClick={() => markAllRead.mutate()}
                  disabled={markAllRead.isPending}
                  className="inline-flex h-8 w-8 items-center justify-center rounded-md hover:bg-muted disabled:opacity-60"
                  title="Đánh dấu tất cả đã đọc"
                >
                  <CheckCheck className="h-4 w-4" />
                </button>
              )}
            </div>

            <ScrollArea className="max-h-96">
              {notificationsQuery.isLoading ? (
                <div className="px-4 py-8 text-center text-sm text-muted-foreground">
                  Đang tải thông báo...
                </div>
              ) : notifications.length === 0 ? (
                <div className="px-4 py-8 text-center text-sm text-muted-foreground">
                  Chưa có thông báo nào.
                </div>
              ) : (
                notifications.map((item) => (
                  <NotificationItem
                    key={`${item.id}-${item.createdAt}`}
                    item={item}
                    onRead={(id) => {
                      if (!item.read) markRead.mutate(id);
                    }}
                  />
                ))
              )}
            </ScrollArea>
          </DropdownMenuContent>
        </DropdownMenu>

        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button className="flex items-center gap-2 p-1.5 rounded-md hover:bg-muted transition-colors">
              <UserAvatar name={user?.fullName || 'User'} avatarUrl={user?.avatarUrl} size="sm" />
              <span className="hidden md:block text-sm font-medium text-foreground">{user?.fullName}</span>
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-56">
            <div className="px-2 py-1.5">
              <p className="text-sm font-medium">{user?.fullName}</p>
              <p className="text-xs text-muted-foreground">{user?.role}</p>
            </div>
            <DropdownMenuSeparator />
            <DropdownMenuItem asChild>
              <Link to="/app/account">
                <Settings className="h-4 w-4 mr-2" /> Tài khoản & bảo mật
              </Link>
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={() => logoutMutation.mutate()}>
              <LogOut className="h-4 w-4 mr-2" /> {t('nav.logout')}
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  );
}
