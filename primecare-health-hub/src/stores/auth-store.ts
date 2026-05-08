import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { CurrentUser, AppRole } from '@/types/api';

interface AuthState {
  accessToken: string | null;
  user: CurrentUser | null;
  isAuthenticated: boolean;
  authResolved: boolean;
  setAccessToken: (token: string | null) => void;
  setUser: (user: CurrentUser | null) => void;
  markAuthResolved: () => void;
  login: (token: string, user: CurrentUser) => void;
  logout: () => void;
  hasRole: (...roles: AppRole[]) => boolean;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      accessToken: null,
      user: null,
      isAuthenticated: false,
      authResolved: false,
      setAccessToken: (token) =>
        set((state) => ({
          accessToken: token,
          isAuthenticated: Boolean(token && state.user),
        })),
      setUser: (user) =>
        set((state) => ({
          user,
          isAuthenticated: Boolean(state.accessToken && user),
        })),
      markAuthResolved: () => set({ authResolved: true }),
      login: (token, user) =>
        set({ accessToken: token, user, isAuthenticated: true, authResolved: true }),
      logout: () =>
        set({ accessToken: null, user: null, isAuthenticated: false, authResolved: true }),
      hasRole: (...roles) => {
        const user = get().user;
        return user ? roles.includes(user.role) : false;
      },
    }),
    {
      name: 'primecare-auth',
      partialize: (state) => ({ user: state.user }),
    }
  )
);
