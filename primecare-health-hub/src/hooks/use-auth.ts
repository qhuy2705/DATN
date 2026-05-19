import { useMutation, useQuery } from '@tanstack/react-query';
import { useLocation, useNavigate } from 'react-router-dom';
import apiClient from '@/lib/api-client';
import { useAuthStore } from '@/stores/auth-store';
import type {
  ApiResponse,
  LoginRequest,
  LoginResponse,
  AppRole,
  CredentialSetupTokenInfo,
  ForgotPasswordRequest,
  CompleteCredentialSetupRequest,
} from '@/types/api';
import { DEFAULT_ROLE_HOME } from '@/lib/route-access';
import { toast } from 'sonner';

function getErrorMessage(error: unknown) {
  const maybeAxios = error as {
    response?: {
      data?: {
        message?: string;
        details?: {
          fields?: Record<string, string>;
        };
      };
    };
  };

  const fieldErrors = maybeAxios.response?.data?.details?.fields;
  if (fieldErrors) {
    const firstFieldError = Object.values(fieldErrors)[0];
    if (firstFieldError) return firstFieldError;
  }

  return maybeAxios.response?.data?.message || 'Đăng nhập thất bại. Vui lòng kiểm tra lại thông tin.';
}

function resolvePostLoginTarget(role: AppRole, fallbackPath?: string) {
  if (role === 'PATIENT') {
    if (fallbackPath && fallbackPath !== '/login' && fallbackPath !== '/register') {
      return fallbackPath;
    }
    return DEFAULT_ROLE_HOME.PATIENT;
  }
  return DEFAULT_ROLE_HOME[role] || '/app/dashboard';
}

export function useLogin() {
  const login = useAuthStore((s) => s.login);
  const navigate = useNavigate();
  const location = useLocation();

  return useMutation({
    mutationFn: async (data: LoginRequest) => {
      const res = await apiClient.post<ApiResponse<LoginResponse>>('/auth/login', data);
      return res.data.data;
    },
    onSuccess: (data) => {
      login(data.accessToken, data.user);
      const from = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname;
      navigate(resolvePostLoginTarget(data.user.role, from), { replace: true });
    },
    onError: (error) => {
      toast.error(getErrorMessage(error));
    },
  });
}

export function useLogout() {
  const logout = useAuthStore((s) => s.logout);
  const navigate = useNavigate();

  return useMutation({
    mutationFn: () => apiClient.post('/auth/logout'),
    onSettled: () => {
      const currentUser = useAuthStore.getState().user;
      logout();
      navigate(currentUser?.role === 'PATIENT' ? '/' : '/login', { replace: true });
    },
  });
}

export function useChangePassword() {
  const logout = useAuthStore((s) => s.logout);
  const navigate = useNavigate();

  return useMutation({
    mutationFn: async (body: { currentPassword: string; newPassword: string }) => {
      const { data } = await apiClient.post('/auth/change-password', body);
      return data;
    },
    onSuccess: () => {
      toast.success('Đổi mật khẩu thành công. Vui lòng đăng nhập lại.');
      logout();
      navigate('/login');
    },
    onError: (error) => {
      toast.error(getErrorMessage(error));
    },
  });
}

export function useRequestPasswordReset() {
  return useMutation({
    mutationFn: async (body: ForgotPasswordRequest) => {
      const { data } = await apiClient.post('/auth/password/forgot', body);
      return data;
    },
    onSuccess: () => {
      toast.success('Nếu tài khoản tồn tại, PrimeCare đã gửi hướng dẫn thiết lập mật khẩu.');
    },
    onError: (error) => {
      toast.error(getErrorMessage(error));
    },
  });
}

export function useInspectCredentialToken(token?: string) {
  return useQuery({
    queryKey: ['auth', 'credential-token', token],
    queryFn: async () => {
      const { data } = await apiClient.post<ApiResponse<CredentialSetupTokenInfo>>('/auth/password/inspect', { token });
      return data.data;
    },
    enabled: Boolean(token),
    retry: false,
  });
}

export function useCompleteCredentialSetup() {
  const navigate = useNavigate();
  return useMutation({
    mutationFn: async (body: CompleteCredentialSetupRequest) => {
      const { data } = await apiClient.post('/auth/password/complete', body);
      return data;
    },
    onSuccess: () => {
      toast.success('Thiết lập mật khẩu thành công. Bạn có thể đăng nhập ngay bây giờ.');
      navigate('/login');
    },
    onError: (error) => {
      toast.error(getErrorMessage(error));
    },
  });
}

export function useCurrentUser() {
  return useAuthStore((s) => s.user);
}

export function usePermission(...roles: AppRole[]) {
  return useAuthStore((s) => s.hasRole(...roles));
}

export { DEFAULT_ROLE_HOME as ROLE_HOME };
