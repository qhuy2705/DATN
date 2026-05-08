import { useEffect, useState } from 'react';
import axios from 'axios';
import { useAuthStore } from '@/stores/auth-store';

export function useSessionBootstrap() {
  const [ready, setReady] = useState(false);
  const { accessToken, user, authResolved, setAccessToken, setUser, logout, markAuthResolved } = useAuthStore();

  useEffect(() => {
    let mounted = true;

    const bootstrap = async () => {
      if (authResolved) {
        if (mounted) setReady(true);
        return;
      }

      try {
        if (accessToken && user) {
          markAuthResolved();
          if (mounted) setReady(true);
          return;
        }

        const res = await axios.post(
          `${import.meta.env.VITE_API_BASE_URL || '/api'}/auth/refresh`,
          {},
          { withCredentials: true }
        );

        const payload = res.data?.data;
        if (payload?.accessToken) {
          setAccessToken(payload.accessToken);
        }
        if (payload?.user) {
          setUser(payload.user);
        }
      } catch {
        logout();
      } finally {
        markAuthResolved();
        if (mounted) setReady(true);
      }
    };

    void bootstrap();

    return () => {
      mounted = false;
    };
  }, [accessToken, authResolved, logout, markAuthResolved, setAccessToken, setUser, user]);

  return { ready: ready || authResolved };
}
