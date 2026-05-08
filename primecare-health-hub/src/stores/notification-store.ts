import { create } from 'zustand';

export interface InternalNotificationItem {
  id: string;
  title: string;
  message: string;
  route?: string;
  entityType?: string;
  entityId?: string;
  createdAt: string;
  read: boolean;
}

interface NotificationState {
  items: InternalNotificationItem[];
  addNotification: (notification: Omit<InternalNotificationItem, 'id' | 'read'>) => void;
  markRead: (id: string) => void;
  markAllRead: () => void;
  clearAll: () => void;
}

const MAX_NOTIFICATIONS = 30;

function buildNotificationId(notification: {
  title: string;
  message: string;
  route?: string;
  entityId?: string;
  createdAt: string;
}) {
  return `${notification.title}::${notification.message}::${notification.route ?? ''}::${notification.entityId ?? ''}::${notification.createdAt}`;
}

export const useNotificationStore = create<NotificationState>((set) => ({
  items: [],
  addNotification: (notification) =>
    set((state) => {
      const item: InternalNotificationItem = {
        ...notification,
        id: buildNotificationId(notification),
        read: false,
      };

      if (state.items.some((existing) => existing.id === item.id)) {
        return state;
      }

      return {
        items: [item, ...state.items].slice(0, MAX_NOTIFICATIONS),
      };
    }),
  markRead: (id) =>
    set((state) => ({
      items: state.items.map((item) => (item.id === id ? { ...item, read: true } : item)),
    })),
  markAllRead: () =>
    set((state) => ({
      items: state.items.map((item) => ({ ...item, read: true })),
    })),
  clearAll: () => set({ items: [] }),
}));
