import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import { toast } from 'sonner';
import { useAuthStore } from '@/stores/auth-store';
import i18n from '@/i18n';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true,
});

apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = useAuthStore.getState().accessToken;
  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  const language = i18n.language?.startsWith('en') ? 'en' : 'vi';
  if (config.headers) {
    config.headers['Accept-Language'] = language;
    config.headers['X-Language'] = language;
  }

  return config;
});

let isRefreshing = false;
let failedQueue: Array<{
  resolve: (value: unknown) => void;
  reject: (error: unknown) => void;
}> = [];

const processQueue = (error: unknown) => {
  failedQueue.forEach(({ resolve, reject }) => {
    if (error) reject(error);
    else resolve(undefined);
  });
  failedQueue = [];
};

function isAuthEndpoint(url?: string) {
  if (!url) return false;
  return (
    url.includes('/auth/login') ||
    url.includes('/auth/refresh') ||
    url.includes('/auth/logout') ||
    url.includes('/auth/change-password') ||
    url.includes('/auth/password/') ||
    url.includes('/auth/patient/register')
  );
}

function shouldSkipAuthRefreshForUrl(url?: string) {
  if (!url) return false;
  return url.includes('/public/') || url.includes('/lookup/');
}

function extractResponseField(data: unknown, field: string) {
  if (!data || typeof data !== 'object') return '';
  const record = data as Record<string, unknown>;
  const value = record[field];
  if (typeof value === 'string' && value.trim()) return value.trim();
  return '';
}

function extractResponseMessage(data: unknown) {
  for (const key of ['message', 'error', 'detail', 'title']) {
    const value = extractResponseField(data, key);
    if (value) return value;
  }
  return '';
}

const BUSINESS_AUTH_ERROR_CODES = new Set([
  'APPOINTMENT_CLAIM_REQUIRED',
  'APPOINTMENT_CLAIM_NOT_OWNED',
  'APPOINTMENT_CHECKIN_TOKEN_INVALID',
  'APPOINTMENT_CHECKIN_TOKEN_EXPIRED',
  'PUBLIC_LOOKUP_TOKEN_INVALID',
  'PUBLIC_LOOKUP_TOKEN_EXPIRED',
  'PUBLIC_LOOKUP_OTP_INVALID',
  'PUBLIC_LOOKUP_OTP_EXPIRED',
  'PUBLIC_LOOKUP_OTP_LOCKED',
]);

function isBusinessAuthErrorCode(code?: string) {
  return Boolean(code && BUSINESS_AUTH_ERROR_CODES.has(code));
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & {
      _retry?: boolean;
    };

    const status = error.response?.status;
    const requestUrl = originalRequest?.url;
    const responseCode = extractResponseField(error.response?.data, 'code');
    const responseMessage = extractResponseMessage(error.response?.data);

    if (status === 409 && !responseMessage) {
      toast.error('Dữ liệu đã bị thay đổi bởi người khác, vui lòng tải lại trang');
    }

    const shouldSkipRefresh =
      !originalRequest ||
      originalRequest._retry ||
      status !== 401 ||
      isAuthEndpoint(requestUrl) ||
      shouldSkipAuthRefreshForUrl(requestUrl) ||
      isBusinessAuthErrorCode(responseCode);

    if (shouldSkipRefresh) {
      return Promise.reject(error);
    }

    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        failedQueue.push({ resolve, reject });
      }).then(() => apiClient(originalRequest));
    }

    originalRequest._retry = true;
    isRefreshing = true;

    try {
      const { data } = await axios.post(
        `${API_BASE_URL}/auth/refresh`,
        {},
        { withCredentials: true }
      );

      const payload = data?.data;
      if (payload?.accessToken) {
        useAuthStore.getState().setAccessToken(payload.accessToken);
      }
      if (payload?.user) {
        useAuthStore.getState().setUser(payload.user);
      }

      processQueue(null);
      return apiClient(originalRequest);
    } catch (refreshError) {
      processQueue(refreshError);
      useAuthStore.getState().logout();
      window.location.href = '/login';
      return Promise.reject(refreshError);
    } finally {
      isRefreshing = false;
    }
  }
);

export default apiClient;
