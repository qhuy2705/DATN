import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import apiClient from '@/lib/api-client';
import { unwrapApiData, unwrapPage } from '@/lib/api-adapters';
import { downloadProtectedFile, openProtectedFile } from '@/lib/download-file';
import { getApiErrorMessage } from '@/lib/error-utils';
import type {
  ApiResponse,
  LoginResponse,
  NotificationPreference,
  PageResponse,
  Patient,
  PatientAppointmentHistoryItem,
  PatientInvoiceHistoryItem,
  PatientOverview,
  PatientResultHistoryItem,
  PatientStatusHistoryItem,
  RegisterPatientAccountRequest,
  CancelPatientAppointmentRequest,
  UpdatePatientSelfProfileRequest,
} from '@/types/api';
import { toast } from 'sonner';
import { useAuthStore } from '@/stores/auth-store';
import { DEFAULT_ROLE_HOME } from '@/lib/route-access';

function getErrorMessage(error: unknown) {
  const maybeAxios = error as { response?: { data?: { message?: string } } };
  return maybeAxios.response?.data?.message || 'Đã có lỗi xảy ra. Vui lòng thử lại.';
}

export function useRegisterPatientAccount() {
  const navigate = useNavigate();
  const login = useAuthStore((s) => s.login);
  return useMutation({
    mutationFn: async (body: RegisterPatientAccountRequest) => {
      const { data } = await apiClient.post<ApiResponse<LoginResponse>>('/auth/patient/register', body);
      return unwrapApiData(data);
    },
    onSuccess: (payload) => {
      login(payload.accessToken, payload.user);
      toast.success('Đăng ký tài khoản bệnh nhân thành công');
      navigate(DEFAULT_ROLE_HOME[payload.user.role] || '/', { replace: true });
    },
    onError: (error) => {
      toast.error(getErrorMessage(error));
    },
  });
}

export function usePatientOverview(enabled = true) {
  return useQuery({
    queryKey: ['patient', 'overview'],
    enabled,
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<PatientOverview>>('/patient/overview');
      return unwrapApiData(data);
    },
  });
}

export function usePatientProfile(enabled = true) {
  return useQuery({
    queryKey: ['patient', 'profile'],
    enabled,
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<Patient>>('/patient/me');
      return unwrapApiData(data);
    },
  });
}

export function useUpdatePatientProfile() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: UpdatePatientSelfProfileRequest) => {
      const { data } = await apiClient.put<ApiResponse<Patient>>('/patient/me', body);
      return unwrapApiData(data);
    },
    onSuccess: (patient) => {
      qc.invalidateQueries({ queryKey: ['patient', 'profile'] });
      qc.invalidateQueries({ queryKey: ['patient', 'overview'] });
      const currentUser = useAuthStore.getState().user;
      if (currentUser) {
        useAuthStore.getState().setUser({
          ...currentUser,
          fullName: patient.fullName,
          email: patient.email,
          patientId: patient.id,
          avatarUrl: patient.avatarUrl,
        });
      }
      toast.success('Cập nhật hồ sơ thành công');
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, 'Cập nhật hồ sơ thất bại'));
    },
  });
}

export function usePatientAppointments(pageOrParams: number | Record<string, string> = 0, size = 10) {
  const params =
    typeof pageOrParams === 'number'
      ? { page: String(pageOrParams), size: String(size) }
      : pageOrParams;

  return useQuery({
    queryKey: ['patient', 'appointments', params],
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<PageResponse<PatientAppointmentHistoryItem>>>('/patient/appointments', {
        params,
      });
      return unwrapPage<PatientAppointmentHistoryItem>(data);
    },
    staleTime: 30_000,
    refetchOnWindowFocus: false,
    placeholderData: (previousData) => previousData,
  });
}

export function usePatientAppointmentStatusHistory(appointmentId?: string, enabled = true) {
  return useQuery({
    queryKey: ['patient', 'appointments', appointmentId, 'status-history'],
    enabled: enabled && Boolean(appointmentId),
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<PatientStatusHistoryItem[]>>(`/patient/appointments/${appointmentId}/status-history`);
      return unwrapApiData(data);
    },
  });
}

export function useCancelPatientAppointment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ appointmentId, body }: { appointmentId: string; body?: CancelPatientAppointmentRequest }) => {
      const { data } = await apiClient.post<ApiResponse<PatientAppointmentHistoryItem>>(`/patient/appointments/${appointmentId}/cancel`, body || {});
      return unwrapApiData(data);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['patient', 'appointments'] });
      qc.invalidateQueries({ queryKey: ['patient', 'overview'] });
      toast.success('Đã hủy lịch hẹn thành công');
    },
    onError: (error) => {
      toast.error(getErrorMessage(error));
    },
  });
}

export function usePatientResults(page = 0, size = 10) {
  return useQuery({
    queryKey: ['patient', 'results', page, size],
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<PageResponse<PatientResultHistoryItem>>>(`/patient/results?page=${page}&size=${size}`);
      return unwrapPage<PatientResultHistoryItem>(data);
    },
  });
}

export function usePatientResultStatusHistory(resultId?: string, enabled = true) {
  return useQuery({
    queryKey: ['patient', 'results', resultId, 'status-history'],
    enabled: enabled && Boolean(resultId),
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<PatientStatusHistoryItem[]>>(`/patient/results/${resultId}/status-history`);
      return unwrapApiData(data);
    },
  });
}

export function useOpenPatientResultPdf() {
  return useMutation({
    mutationFn: async (resultId: string) => {
      await openProtectedFile(`/patient/results/${resultId}/pdf`, `service-result-${resultId}.pdf`);
    },
    onError: (error) => {
      toast.error(getErrorMessage(error));
    },
  });
}

export function usePatientInvoices(page = 0, size = 10) {
  return useQuery({
    queryKey: ['patient', 'invoices', page, size],
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<PageResponse<PatientInvoiceHistoryItem>>>(`/patient/invoices?page=${page}&size=${size}`);
      return unwrapPage<PatientInvoiceHistoryItem>(data);
    },
  });
}

export function usePatientInvoiceStatusHistory(invoiceId?: string, enabled = true) {
  return useQuery({
    queryKey: ['patient', 'invoices', invoiceId, 'status-history'],
    enabled: enabled && Boolean(invoiceId),
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<PatientStatusHistoryItem[]>>(`/patient/invoices/${invoiceId}/status-history`);
      return unwrapApiData(data);
    },
  });
}

export function useDownloadPatientInvoicePdf() {
  return useMutation({
    mutationFn: async (invoiceId: string) => {
      await downloadProtectedFile(`/patient/invoices/${invoiceId}/pdf`, `invoice-${invoiceId}.pdf`);
    },
    onError: (error) => {
      toast.error(getErrorMessage(error));
    },
  });
}

export function useNotificationPreferences(enabled = true) {
  return useQuery({
    queryKey: ['account', 'notification-preferences'],
    enabled,
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<NotificationPreference>>('/account/notification-preferences');
      return unwrapApiData(data);
    },
  });
}

export function useUpdateNotificationPreferences() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: Partial<NotificationPreference>) => {
      const { data } = await apiClient.put<ApiResponse<NotificationPreference>>('/account/notification-preferences', body);
      return unwrapApiData(data);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['account', 'notification-preferences'] });
      toast.success('Đã cập nhật tuỳ chọn thông báo');
    },
    onError: (error) => {
      toast.error(getErrorMessage(error));
    },
  });
}
