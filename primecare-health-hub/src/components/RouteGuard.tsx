import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/stores/auth-store';
import type { AppRole } from '@/types/api';
import { DEFAULT_ROLE_HOME } from '@/lib/route-access';

interface RouteGuardProps {
  roles?: AppRole[];
}

export function RouteGuard({ roles }: RouteGuardProps) {
  const { isAuthenticated, user, authResolved } = useAuthStore();
  const location = useLocation();

  if (!authResolved) {
    return (
      <div className="flex min-h-screen items-center justify-center text-sm text-muted-foreground">
        Đang khôi phục phiên làm việc...
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (roles && user && !roles.includes(user.role)) {
    return <Navigate to={DEFAULT_ROLE_HOME[user.role] || '/app/dashboard'} replace />;
  }

  return <Outlet />;
}
