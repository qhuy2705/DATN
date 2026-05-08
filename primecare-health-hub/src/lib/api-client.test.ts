import axios, { AxiosError, type AxiosAdapter, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios';
import { afterEach, describe, expect, it, vi } from 'vitest';
import apiClient from '@/lib/api-client';
import { useAuthStore } from '@/stores/auth-store';
import type { CurrentUser } from '@/types/api';

const originalAdapter = apiClient.defaults.adapter;
const originalBaseUrl = apiClient.defaults.baseURL;

const user = {
  id: 'user-1',
  email: 'doctor@example.com',
  fullName: 'Doctor Prime',
  role: 'DOCTOR',
} satisfies CurrentUser;

function setAuthenticated(token = 'old-token') {
  useAuthStore.setState({
    accessToken: token,
    user,
    isAuthenticated: true,
    authResolved: true,
  });
}

function axiosResponse<T>(config: InternalAxiosRequestConfig, data: T, status = 200): AxiosResponse<T> {
  return {
    data,
    status,
    statusText: String(status),
    headers: {},
    config,
  };
}

function axiosError(config: InternalAxiosRequestConfig, status: number, data: unknown) {
  return new AxiosError(
    `Request failed with status code ${status}`,
    undefined,
    config,
    undefined,
    axiosResponse(config, data, status),
  );
}

describe('apiClient auth refresh interceptor', () => {
  afterEach(() => {
    apiClient.defaults.adapter = originalAdapter;
    apiClient.defaults.baseURL = originalBaseUrl;
    useAuthStore.setState({
      accessToken: null,
      user: null,
      isAuthenticated: false,
      authResolved: true,
    });
    vi.restoreAllMocks();
  });

  it('does not refresh, retry, or logout when change-password returns 401', async () => {
    setAuthenticated();
    const refreshSpy = vi.spyOn(axios, 'post');
    let requestCount = 0;

    apiClient.defaults.adapter = (async (config) => {
      requestCount += 1;
      return Promise.reject(
        axiosError(config, 401, {
          message: 'Mật khẩu hiện tại không đúng',
        }),
      );
    }) as AxiosAdapter;

    await expect(apiClient.post('/auth/change-password', {
      currentPassword: 'wrong-password',
      newPassword: 'new-password',
    })).rejects.toMatchObject({
      response: {
        status: 401,
        data: {
          message: 'Mật khẩu hiện tại không đúng',
        },
      },
    });

    expect(refreshSpy).not.toHaveBeenCalled();
    expect(requestCount).toBe(1);
    expect(useAuthStore.getState().accessToken).toBe('old-token');
    expect(useAuthStore.getState().user).toEqual(user);
  });

  it('does not refresh, retry, logout, or redirect when public lookup verify returns 400', async () => {
    setAuthenticated();
    window.history.replaceState(null, '', '/appointments/lookup');
    const refreshSpy = vi.spyOn(axios, 'post');
    let requestCount = 0;

    apiClient.defaults.adapter = (async (config) => {
      requestCount += 1;
      return Promise.reject(
        axiosError(config, 400, {
          code: 'PUBLIC_LOOKUP_OTP_INVALID',
          message: 'Mã OTP không đúng',
        }),
      );
    }) as AxiosAdapter;

    await expect(apiClient.post('/public/lookup/appointments/verify-otp', {
      code: 'PC-001',
      otp: '123456',
    })).rejects.toMatchObject({
      response: {
        status: 400,
        data: {
          code: 'PUBLIC_LOOKUP_OTP_INVALID',
        },
      },
    });

    expect(refreshSpy).not.toHaveBeenCalled();
    expect(requestCount).toBe(1);
    expect(useAuthStore.getState().accessToken).toBe('old-token');
    expect(useAuthStore.getState().user).toEqual(user);
    expect(window.location.pathname).toBe('/appointments/lookup');
  });

  it('does not refresh, retry, logout, or redirect when public lookup verify returns 401', async () => {
    setAuthenticated();
    window.history.replaceState(null, '', '/appointments/lookup');
    const refreshSpy = vi.spyOn(axios, 'post');
    let requestCount = 0;

    apiClient.defaults.adapter = (async (config) => {
      requestCount += 1;
      return Promise.reject(
        axiosError(config, 401, {
          code: 'PUBLIC_LOOKUP_OTP_INVALID',
          message: 'Mã OTP không đúng',
        }),
      );
    }) as AxiosAdapter;

    await expect(apiClient.post('/public/lookup/appointments/verify-otp', {
      code: 'PC-001',
      otp: '123456',
    })).rejects.toMatchObject({
      response: {
        status: 401,
        data: {
          code: 'PUBLIC_LOOKUP_OTP_INVALID',
        },
      },
    });

    expect(refreshSpy).not.toHaveBeenCalled();
    expect(requestCount).toBe(1);
    expect(useAuthStore.getState().accessToken).toBe('old-token');
    expect(useAuthStore.getState().user).toEqual(user);
    expect(window.location.pathname).toBe('/appointments/lookup');
  });

  it('does not refresh appointment business auth errors when backend sends a business code', async () => {
    setAuthenticated();
    const refreshSpy = vi.spyOn(axios, 'post');
    let requestCount = 0;

    apiClient.defaults.adapter = (async (config) => {
      requestCount += 1;
      return Promise.reject(
        axiosError(config, 401, {
          code: 'APPOINTMENT_CLAIM_NOT_OWNED',
          message: 'Backend wording can change without affecting refresh behavior',
        }),
      );
    }) as AxiosAdapter;

    await expect(apiClient.post('/admin/appointments/123/release-claim')).rejects.toMatchObject({
      response: {
        status: 401,
        data: {
          code: 'APPOINTMENT_CLAIM_NOT_OWNED',
        },
      },
    });

    expect(refreshSpy).not.toHaveBeenCalled();
    expect(requestCount).toBe(1);
    expect(useAuthStore.getState().accessToken).toBe('old-token');
    expect(useAuthStore.getState().user).toEqual(user);
  });

  it.each([
    ['APPOINTMENT_CLAIM_REQUIRED', '/admin/appointments/123/process'],
    ['PUBLIC_LOOKUP_TOKEN_INVALID', '/public/lookup/appointments/PC-001'],
    ['APPOINTMENT_CHECKIN_TOKEN_INVALID', '/reception/appointments/check-in/qr'],
  ])('does not refresh or logout for business auth code %s', async (code, url) => {
    setAuthenticated();
    const refreshSpy = vi.spyOn(axios, 'post');
    let requestCount = 0;

    apiClient.defaults.adapter = (async (config) => {
      requestCount += 1;
      return Promise.reject(
        axiosError(config, 401, {
          code,
          message: 'Backend message can be changed safely',
        }),
      );
    }) as AxiosAdapter;

    await expect(apiClient.post(url, { token: 'bad-token' })).rejects.toMatchObject({
      response: {
        status: 401,
        data: { code },
      },
    });

    expect(refreshSpy).not.toHaveBeenCalled();
    expect(requestCount).toBe(1);
    expect(useAuthStore.getState().accessToken).toBe('old-token');
    expect(useAuthStore.getState().user).toEqual(user);
  });

  it('does not classify business auth by Vietnamese message text', async () => {
    setAuthenticated();
    const refreshSpy = vi.spyOn(axios, 'post').mockResolvedValue({
      data: {
        data: {
          accessToken: 'new-token',
          user,
        },
      },
    });
    let requestCount = 0;

    apiClient.defaults.adapter = (async (config) => {
      requestCount += 1;
      if (!(config as InternalAxiosRequestConfig & { _retry?: boolean })._retry) {
        return Promise.reject(
          axiosError(config, 401, {
            message: 'Bạn không giữ quyền xử lý lịch hẹn này.',
          }),
        );
      }

      return axiosResponse(config, { ok: true });
    }) as AxiosAdapter;

    const response = await apiClient.post('/admin/appointments/123/release-claim');

    expect(response.data).toEqual({ ok: true });
    expect(refreshSpy).toHaveBeenCalledTimes(1);
    expect(requestCount).toBe(2);
    expect(useAuthStore.getState().accessToken).toBe('new-token');
  });

  it('refreshes and retries a normal API endpoint after a 401', async () => {
    setAuthenticated();
    const refreshedUser = { ...user, fullName: 'Doctor Refreshed' };
    const refreshSpy = vi.spyOn(axios, 'post').mockResolvedValue({
      data: {
        data: {
          accessToken: 'new-token',
          user: refreshedUser,
        },
      },
    });
    const seenUrls: string[] = [];

    apiClient.defaults.adapter = (async (config) => {
      seenUrls.push(String(config.url));
      if (config.url === '/patient/me' && !(config as InternalAxiosRequestConfig & { _retry?: boolean })._retry) {
        return Promise.reject(axiosError(config, 401, { message: 'Expired token' }));
      }

      return axiosResponse(config, { ok: true });
    }) as AxiosAdapter;

    const response = await apiClient.get('/patient/me');

    expect(response.data).toEqual({ ok: true });
    expect(refreshSpy).toHaveBeenCalledTimes(1);
    expect(refreshSpy).toHaveBeenCalledWith(expect.stringMatching(/\/api\/auth\/refresh$/), {}, { withCredentials: true });
    expect(seenUrls).toEqual(['/patient/me', '/patient/me']);
    expect(useAuthStore.getState().accessToken).toBe('new-token');
    expect(useAuthStore.getState().user).toEqual(refreshedUser);
  });

  it('refreshes protected appointment endpoints when 401 has no business code', async () => {
    setAuthenticated();
    const refreshSpy = vi.spyOn(axios, 'post').mockResolvedValue({
      data: {
        data: {
          accessToken: 'new-token',
          user,
        },
      },
    });
    let requestCount = 0;

    apiClient.defaults.adapter = (async (config) => {
      requestCount += 1;
      if (!(config as InternalAxiosRequestConfig & { _retry?: boolean })._retry) {
        return Promise.reject(axiosError(config, 401, { message: 'Session expired' }));
      }

      return axiosResponse(config, { ok: true });
    }) as AxiosAdapter;

    const response = await apiClient.post('/admin/appointments/123/release-claim');

    expect(response.data).toEqual({ ok: true });
    expect(refreshSpy).toHaveBeenCalledTimes(1);
    expect(requestCount).toBe(2);
    expect(useAuthStore.getState().accessToken).toBe('new-token');
  });

  it('logs out locally when refresh fails for a normal API endpoint', async () => {
    setAuthenticated();
    vi.spyOn(axios, 'post').mockRejectedValue(new Error('refresh failed'));
    vi.spyOn(console, 'error').mockImplementation(() => undefined);

    apiClient.defaults.adapter = (async (config) => {
      return Promise.reject(axiosError(config, 401, { message: 'Expired token' }));
    }) as AxiosAdapter;

    await expect(apiClient.get('/patient/me')).rejects.toThrow('refresh failed');

    expect(useAuthStore.getState().accessToken).toBeNull();
    expect(useAuthStore.getState().user).toBeNull();
    expect(useAuthStore.getState().isAuthenticated).toBe(false);
  });
});
