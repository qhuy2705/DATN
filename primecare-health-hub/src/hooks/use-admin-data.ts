import { useEffect, useMemo, useState } from 'react';
import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs';
import { useAuthStore } from '@/stores/auth-store';
import type { AppRole, AppointmentSummary } from '@/types/api';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/lib/api-client';
import type {
  AdminSpecialty,
  Appointment,
  AppointmentListQueryParams,
  AuditLog,
  AvailabilityQueryParams,
  Branch,
  BranchMasterDataSummary,
  BranchSpecialty,
  DashboardBreakdown,
  DashboardKpis,
  DashboardOverview,
  Doctor,
  DoctorMasterDataSummary,
  DoctorSchedule,
  DoctorScheduleImportResult,
  DoctorScheduleDoctorOption,
  LeaveRequest,
  MedicalService,
  Medication,
  Patient,
  PdfJob,
  Staff,
  StaffMasterDataSummary,
} from '@/types/api';
import {
  normalizeAdminSpecialty,
  normalizeAppointment,
  normalizeAuditLog,
  normalizeBranchMasterDataSummary,
  normalizeAvailability,
  normalizeBranch,
  normalizeBranchSpecialty,
  normalizeDashboardBreakdown,
  normalizeDashboardKpis,
  normalizeDashboardOverview,
  normalizeDoctor,
  normalizeDoctorMasterDataSummary,
  normalizeDoctorSchedule,
  normalizeLeaveRequest,
  normalizeMedicalService,
  normalizeMedication,
  normalizePatient,
  normalizeStaff,
  normalizeStaffMasterDataSummary,
  unwrapApiData,
  unwrapPage,
} from '@/lib/api-adapters';
import { toast } from 'sonner';
import { getApiErrorMessage } from '@/lib/error-utils';

export const adminQueryKeys = {
  dashboard: ['admin', 'dashboard'] as const,
  appointments: (params?: AppointmentListQueryParams) => ['admin', 'appointments', params] as const,
  appointmentDetail: (id?: string) => ['admin', 'appointments', 'detail', id] as const,
  appointmentSummary: (params?: Record<string, string>) => ['admin', 'appointments', 'summary', params] as const,
  rescheduleAvailability: (appointmentId?: string, params?: AvailabilityQueryParams) =>
    ['admin', 'appointments', appointmentId, 'reschedule-availability', params] as const,
  auditLogs: (params?: Record<string, string>) => ['admin', 'audit-logs', params] as const,
  branches: (params?: Record<string, string>) => ['admin', 'branches', params] as const,
  branchesSummary: (params?: Record<string, string>) =>
    ['admin', 'branches', 'summary', params] as const,
  doctors: (params?: Record<string, string>) => ['admin', 'doctors', params] as const,
  doctorsSummary: (params?: Record<string, string>) =>
    ['admin', 'doctors', 'summary', params] as const,
  staffs: (params?: Record<string, string>) => ['admin', 'staffs', params] as const,
  staffsSummary: (params?: Record<string, string>) =>
    ['admin', 'staffs', 'summary', params] as const,
  medications: (params?: Record<string, string>) => ['admin', 'medications', params] as const,
  medicalServices: (params?: Record<string, string>) => ['admin', 'medical-services', params] as const,
  specialties: (params?: Record<string, string>) => ['admin', 'specialties', params] as const,
  branchSpecialties: (params?: Record<string, string>) =>
    ['admin', 'branch-specialties', params] as const,
  patients: (params?: Record<string, string>) => ['admin', 'patients', params] as const,
  doctorSchedules: (params?: Record<string, string>) =>
    ['admin', 'doctor-schedules', params] as const,
  doctorScheduleDoctorOptions: ['admin', 'doctor-schedules', 'doctor-options'] as const,
  doctorLeaves: (params?: Record<string, string>) =>
    ['admin', 'doctor-leaves', params] as const,
};

export function useDashboardOverview(params?: Record<string, string>) {
  return useQuery<DashboardOverview>({
    queryKey: [...adminQueryKeys.dashboard, params],
    queryFn: async () => {
      const { data } = await apiClient.get('/admin/dashboard/overview', { params });
      return normalizeDashboardOverview(unwrapApiData(data));
    },
  });
}

export function useDashboardBreakdown(params?: Record<string, string>) {
  return useQuery<DashboardBreakdown>({
    queryKey: [...adminQueryKeys.dashboard, 'breakdown', params],
    queryFn: async () => {
      const { data } = await apiClient.get('/admin/dashboard/breakdown', { params });
      return normalizeDashboardBreakdown(unwrapApiData(data));
    },
  });
}

export function useDashboardKpis(params?: Record<string, string>) {
  return useQuery<DashboardKpis>({
    queryKey: [...adminQueryKeys.dashboard, 'kpis', params],
    queryFn: async () => {
      const { data } = await apiClient.get('/admin/dashboard/kpis', { params });
      return normalizeDashboardKpis(unwrapApiData(data));
    },
  });
}

export function useAdminAppointments(
  params?: AppointmentListQueryParams,
  options?: { enabled?: boolean; refetchInterval?: number | false; refetchOnWindowFocus?: boolean; staleTime?: number },
) {
  return useQuery({
    queryKey: adminQueryKeys.appointments(params),
    queryFn: async () => {
      const { data } = await apiClient.get('/admin/appointments', { params });
      const page = unwrapPage<unknown>(data);
      return {
        ...page,
        items: page.items.map(normalizeAppointment),
      };
    },
    staleTime: 15_000,
    placeholderData: (previousData) => previousData,
    ...options,
  });
}

export function useAdminAppointmentDetail(
  id?: string,
  options?: { enabled?: boolean; refetchOnWindowFocus?: boolean },
) {
  return useQuery({
    queryKey: adminQueryKeys.appointmentDetail(id),
    enabled: Boolean(id) && (options?.enabled ?? true),
    refetchOnWindowFocus: options?.refetchOnWindowFocus ?? true,
    queryFn: async () => {
      const { data } = await apiClient.get(`/admin/appointments/${id}`);
      return normalizeAppointment(unwrapApiData(data));
    },
  });
}

function getHttpStatus(error: unknown) {
  return (error as { response?: { status?: number } })?.response?.status;
}

export function useRescheduleAvailability(
  appointmentId?: string,
  params?: AvailabilityQueryParams,
  options?: { enabled?: boolean },
) {
  return useQuery({
    queryKey: adminQueryKeys.rescheduleAvailability(appointmentId, params),
    enabled: Boolean(appointmentId && params) && (options?.enabled ?? true),
    queryFn: async () => {
      try {
        const { data } = await apiClient.get(
          `/admin/appointments/${appointmentId}/reschedule-availability`,
          { params },
        );
        return normalizeAvailability(unwrapApiData(data));
      } catch (error) {
        const status = getHttpStatus(error);
        if (status !== 404 && status !== 405) {
          throw error;
        }

        const { data } = await apiClient.get('/public/availability', {
          params: {
            ...params,
            onlyAvailable: true,
          },
        });
        return normalizeAvailability(unwrapApiData(data));
      }
    },
    staleTime: 30_000,
    refetchOnWindowFocus: false,
    placeholderData: (previousData) => previousData,
  });
}

function getAppointmentActionSuccessMessage(action: string) {
  switch (action) {
    case 'resolve-no-show':
      return 'Đã đóng follow-up lịch hẹn.';
    case 'reschedule':
      return 'Đã dời lịch hẹn.';
    default:
      return 'Thao tác thành công';
  }
}

function getAppointmentActionErrorFallback(action: string) {
  switch (action) {
    case 'check-in':
      return 'Mã QR check-in không hợp lệ.';
    case 'release-claim':
      return 'Không thể nhả quyền xử lý lịch hẹn.';
    case 'claim':
      return 'Không thể nhận quyền xử lý lịch hẹn.';
    case 'no-show':
      return 'Đánh dấu không đến thất bại.';
    case 'reschedule':
      return 'Dời lịch hẹn thất bại.';
    case 'resolve-no-show':
      return 'Đóng follow-up thất bại.';
    default:
      return 'Thao tác thất bại';
  }
}

function shouldForceRefreshAppointmentViews(action: string) {
  return action === 'resolve-no-show' || action === 'reschedule';
}

export function useAppointmentAction() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      id,
      action,
      body,
    }: {
      id?: string;
      action: string;
      body?: Record<string, unknown>;
    }) => {
      const path = id ? `/admin/appointments/${id}/${action}` : `/admin/appointments/${action}`;
      const { data } = await apiClient.post(path, body ?? {});
      return normalizeAppointment(unwrapApiData(data)) as Appointment;
    },
    onSuccess: (result, variables) => {
      const isHeartbeat = variables?.action === 'heartbeat';
      const previous = result?.id ? findCachedAppointmentSnapshot(qc, result.id) : undefined;

      if (result?.id) {
        patchAppointmentDetailCacheWithAppointment(qc, result);
        patchAppointmentAcrossListCaches(qc, result);
      } else if (variables.id) {
        qc.invalidateQueries({ queryKey: adminQueryKeys.appointmentDetail(variables.id) });
      }

      if (result?.id && !isHeartbeat) {
        patchSummaryCachesForTransition(qc, previous, result);
      }

      qc.invalidateQueries({ queryKey: ['admin', 'dashboard'] });

      if (isHeartbeat) {
        return;
      }

      if (shouldForceRefreshAppointmentViews(variables.action)) {
        if (variables.id) {
          qc.invalidateQueries({ queryKey: adminQueryKeys.appointmentDetail(variables.id) });
        }
        qc.invalidateQueries({ queryKey: ['admin', 'appointments'] });
        qc.invalidateQueries({ queryKey: ['admin', 'appointments', 'summary'] });
        qc.invalidateQueries({ queryKey: ['reception', 'queue'] });
        qc.invalidateQueries({ queryKey: ['reception', 'queue', 'summary'] });
      }

      qc.invalidateQueries({
        queryKey: ['admin', 'appointments'],
        refetchType: 'inactive',
      });
      qc.invalidateQueries({
        queryKey: ['admin', 'appointments', 'summary'],
        refetchType: 'inactive',
      });
      qc.invalidateQueries({ queryKey: ['reception', 'queue'], refetchType: 'inactive' });
      qc.invalidateQueries({ queryKey: ['reception', 'queue', 'summary'], refetchType: 'inactive' });
      toast.success(getAppointmentActionSuccessMessage(variables.action));
    },
    onError: (error: unknown, variables) => {
      toast.error(getApiErrorMessage(error, getAppointmentActionErrorFallback(variables.action)));
    },
  });
}

export function useUpdateAppointmentIntake() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, body }: { id: string; body: Record<string, unknown> }) => {
      const { data } = await apiClient.patch(`/admin/appointments/${id}/intake`, body);
      return normalizeAppointment(unwrapApiData(data)) as Appointment;
    },
    onSuccess: (result) => {
      const previous = result?.id ? findCachedAppointmentSnapshot(qc, result.id) : undefined;
      if (result?.id) {
        patchAppointmentDetailCacheWithAppointment(qc, result);
        patchAppointmentAcrossListCaches(qc, result);
        patchSummaryCachesForTransition(qc, previous, result);
      }
      qc.invalidateQueries({ queryKey: ['admin', 'appointments'], refetchType: 'inactive' });
      toast.success('Lưu thông tin tiếp đón thành công');
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Không thể lưu thông tin tiếp đón')),
  });
}

export function useAuditLogs(params?: Record<string, string>) {
  return useQuery({
    queryKey: adminQueryKeys.auditLogs(params),
    queryFn: async () => {
      const { data } = await apiClient.get('/admin/audit-logs', { params });
      const page = unwrapPage<unknown>(data);
      return {
        ...page,
        items: page.items.map(normalizeAuditLog) as AuditLog[],
      };
    },
  });
}

export function useAdminBranches(params?: Record<string, string>) {
  return useQuery({
    queryKey: adminQueryKeys.branches(params),
    queryFn: async () => {
      const { data } = await apiClient.get('/admin/branches', { params });
      const page = unwrapPage<unknown>(data);
      return {
        ...page,
        items: page.items.map(normalizeBranch),
      };
    },
    staleTime: 30_000,
    refetchOnWindowFocus: false,
    placeholderData: (previousData) => previousData,
  });
}

export function useAdminBranchesSummary(params?: Record<string, string>) {
  return useQuery<BranchMasterDataSummary>({
    queryKey: adminQueryKeys.branchesSummary(params),
    queryFn: async () => {
      const { data } = await apiClient.get('/admin/branches/summary', { params });
      return normalizeBranchMasterDataSummary(unwrapApiData(data));
    },
    staleTime: 30_000,
    refetchOnWindowFocus: false,
    placeholderData: (previousData) => previousData,
  });
}

export function useSaveBranch() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, body }: { id?: string; body: Record<string, unknown> }) => {
      const { data } = id
        ? await apiClient.put(`/admin/branches/${id}`, body)
        : await apiClient.post('/admin/branches', body);
      return normalizeBranch(unwrapApiData(data));
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'branches'] });
      qc.invalidateQueries({ queryKey: ['public', 'branches'] });
      toast.success('Lưu chi nhánh thành công');
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Lưu chi nhánh thất bại')),
  });
}

export function useUpdateBranchStatus() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, status }: { id: string; status: string }) => {
      const { data } = await apiClient.patch(`/admin/branches/${id}/status`, { status });
      return normalizeBranch(unwrapApiData(data));
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'branches'] });
      qc.invalidateQueries({ queryKey: ['public', 'branches'] });
      toast.success('Cập nhật trạng thái thành công');
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Cập nhật trạng thái thất bại')),
  });
}

export function useAdminDoctors(params?: Record<string, string>) {
  return useQuery({
    queryKey: adminQueryKeys.doctors(params),
    queryFn: async () => {
      const { data } = await apiClient.get('/admin/doctors', { params });
      const page = unwrapPage<unknown>(data);
      return {
        ...page,
        items: page.items.map(normalizeDoctor),
      };
    },
    staleTime: 30_000,
    refetchOnWindowFocus: false,
    placeholderData: (previousData) => previousData,
  });
}

export function useAdminDoctorsSummary(params?: Record<string, string>) {
  return useQuery<DoctorMasterDataSummary>({
    queryKey: adminQueryKeys.doctorsSummary(params),
    queryFn: async () => {
      const { data } = await apiClient.get('/admin/doctors/summary', { params });
      return normalizeDoctorMasterDataSummary(unwrapApiData(data));
    },
    staleTime: 30_000,
    refetchOnWindowFocus: false,
    placeholderData: (previousData) => previousData,
  });
}

export function useSaveDoctor() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, body }: { id?: string; body: Record<string, unknown> }) => {
      const { data } = id
        ? await apiClient.put(`/admin/doctors/${id}`, body)
        : await apiClient.post('/admin/doctors', body);
      return normalizeDoctor(unwrapApiData(data));
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'doctors'] });
      qc.invalidateQueries({ queryKey: ['public', 'doctors'] });
      toast.success('Lưu bác sĩ thành công');
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Lưu bác sĩ thất bại')),
  });
}

export function useUpdateDoctorStatus() {
  const qc = useQueryClient();

  return useMutation({
    mutationFn: async ({ id, status }: { id: string; status: string }) => {
      const { data } = await apiClient.patch(`/admin/doctors/${id}/status`, { status });
      return normalizeDoctor(unwrapApiData(data));
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'doctors'] });
      qc.invalidateQueries({ queryKey: ['public', 'doctors'] });
      toast.success('Cập nhật trạng thái bác sĩ thành công');
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Cập nhật trạng thái bác sĩ thất bại')),
  });
}

export function useProvisionDoctorAccount() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      doctorId,
      body,
    }: {
      doctorId: string;
      body: { email?: string; phone?: string };
    }) => {
      const { data } = await apiClient.post(`/admin/doctors/${doctorId}/account`, body);
      return unwrapApiData<{ deliveryChannel?: string; deliveryTarget?: string; deliverySent?: boolean; setupUrl?: string; setupExpiresAt?: string; status?: string }>(data);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'doctors'] });
      toast.success('Tạo tài khoản bác sĩ thành công');
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Tạo tài khoản bác sĩ thất bại')),
  });
}

export function useAdminStaffs(params?: Record<string, string>) {
  return useQuery({
    queryKey: adminQueryKeys.staffs(params),
    queryFn: async () => {
      const { data } = await apiClient.get('/admin/staffs', { params });
      const page = unwrapPage<unknown>(data);
      return {
        ...page,
        items: page.items.map(normalizeStaff),
      };
    },
    staleTime: 30_000,
    refetchOnWindowFocus: false,
    placeholderData: (previousData) => previousData,
  });
}

export function useAdminStaffsSummary(params?: Record<string, string>) {
  return useQuery<StaffMasterDataSummary>({
    queryKey: adminQueryKeys.staffsSummary(params),
    queryFn: async () => {
      const { data } = await apiClient.get('/admin/staffs/summary', { params });
      return normalizeStaffMasterDataSummary(unwrapApiData(data));
    },
    staleTime: 30_000,
    refetchOnWindowFocus: false,
    placeholderData: (previousData) => previousData,
  });
}

export function useSaveStaff() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, body }: { id?: string; body: Record<string, unknown> }) => {
      const { data } = id
        ? await apiClient.put(`/admin/staffs/${id}`, body)
        : await apiClient.post('/admin/staffs', body);
      return normalizeStaff(unwrapApiData(data));
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'staffs'] });
      toast.success('Lưu nhân sự thành công');
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Lưu nhân sự thất bại')),
  });
}

export function useProvisionStaffAccount() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      staffId,
      body,
    }: {
      staffId: string;
      body: { email?: string; phone?: string; role?: string };
    }) => {
      const { data } = await apiClient.post(`/admin/staffs/${staffId}/account`, body);
      return unwrapApiData<{ deliveryChannel?: string; deliveryTarget?: string; deliverySent?: boolean; setupUrl?: string; setupExpiresAt?: string; status?: string }>(data);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'staffs'] });
      toast.success('Tạo tài khoản nhân sự thành công');
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Tạo tài khoản nhân sự thất bại')),
  });
}


export function useResetStaffAccountPassword() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ staffId }: { staffId: string }) => {
      const { data } = await apiClient.post(`/admin/staffs/${staffId}/account/reset-password`);
      return unwrapApiData<{ deliveryChannel?: string; deliveryTarget?: string; deliverySent?: boolean; setupUrl?: string; expiresAt?: string; status?: string }>(data);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'staffs'] });
      toast.success('Reset mật khẩu nhân sự thành công');
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Reset mật khẩu nhân sự thất bại')),
  });
}

export function useAdminBranchSpecialties(params?: Record<string, string>) {
  return useQuery({
    queryKey: adminQueryKeys.branchSpecialties(params),
    queryFn: async () => {
      const { data } = await apiClient.get('/admin/branch-specialties', { params });
      const page = unwrapPage<unknown>(data);
      return {
        ...page,
        items: page.items.map(normalizeBranchSpecialty),
      };
    },
    enabled: !!params?.branchId,
  });
}

export function useSaveBranchSpecialty() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      id,
      body,
    }: {
      id?: string;
      body: Record<string, unknown>;
    }) => {
      const { data } = id
        ? await apiClient.put(`/admin/branch-specialties/${id}`, body)
        : await apiClient.post('/admin/branch-specialties', body);
      return normalizeBranchSpecialty(unwrapApiData(data));
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'branch-specialties'] });
      toast.success('Lưu cấu hình chuyên khoa thành công');
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Lưu cấu hình chuyên khoa thất bại')),
  });
}

export function useAdminPatients(params?: Record<string, string>) {
  return useQuery({
    queryKey: adminQueryKeys.patients(params),
    queryFn: async () => {
      const { data } = await apiClient.get('/admin/patients', { params });
      const page = unwrapPage<unknown>(data);
      return {
        ...page,
        items: page.items.map(normalizePatient),
      };
    },
  });
}

export function useSavePatient() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      id,
      body,
    }: {
      id?: string;
      body: Record<string, unknown>;
    }) => {
      const { data } = id
        ? await apiClient.put(`/admin/patients/${id}`, body)
        : await apiClient.post('/admin/patients', body);
      return normalizePatient(unwrapApiData(data));
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'patients'] });
      toast.success('Lưu bệnh nhân thành công');
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Lưu bệnh nhân thất bại')),
  });
}

export function useAdminDoctorSchedules(params?: Record<string, string>) {
  return useQuery({
    queryKey: adminQueryKeys.doctorSchedules(params),
    queryFn: async () => {
      const { data } = await apiClient.get('/admin/doctor-schedules', { params });
      const page = unwrapPage<unknown>(data);
      return {
        ...page,
        items: page.items.map(normalizeDoctorSchedule),
      };
    },
    enabled: !!params?.doctorId && !!params?.from && !!params?.to,
  });
}


export function useAdminDoctorScheduleDoctorOptions() {
  return useQuery<DoctorScheduleDoctorOption[]>({
    queryKey: adminQueryKeys.doctorScheduleDoctorOptions,
    queryFn: async () => {
      const { data } = await apiClient.get('/admin/doctor-schedules/doctors/options');
      const rawItems = Array.isArray(data) ? data : unwrapApiData<unknown[]>(data);
      return rawItems.map((item) => {
        const value = item as Record<string, unknown>;
        return {
          id: String(value.id ?? ''),
          fullName: String(value.fullName ?? ''),
          displayTitleVn:
            typeof value.displayTitleVn === 'string' ? value.displayTitleVn : undefined,
          branchId: value.branchId != null ? String(value.branchId) : undefined,
          branchNameVn:
            typeof value.branchNameVn === 'string' ? value.branchNameVn : undefined,
          status: typeof value.status === 'string' ? value.status : undefined,
        } satisfies DoctorScheduleDoctorOption;
      });
    },
  });
}

export function useUpsertDoctorSchedule() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: Record<string, unknown>) => {
      const { data } = await apiClient.post('/admin/doctor-schedules', body);
      return normalizeDoctorSchedule(unwrapApiData(data));
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'doctor-schedules'] });
      toast.success('Lưu lịch làm việc thành công');
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Lưu lịch làm việc thất bại')),
  });
}

export function useDeleteDoctorSchedule() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (params: {
      doctorId: string;
      workDate: string;
      session: string;
    }) => {
      await apiClient.delete('/admin/doctor-schedules', { params });
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'doctor-schedules'] });
      toast.success('Xóa lịch làm việc thành công');
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Xóa lịch làm việc thất bại')),
  });
}

export function useImportDoctorSchedules() {
  const qc = useQueryClient();

  return useMutation({
    mutationFn: async ({
      doctorId,
      month,
      clearMonthFirst,
      file,
    }: {
      doctorId: string;
      month: string;
      clearMonthFirst: boolean;
      file: File;
    }) => {
      const formData = new FormData();
      formData.append('doctorId', doctorId);
      formData.append('month', month);
      formData.append('clearMonthFirst', String(clearMonthFirst));
      formData.append('file', file);

      const { data } = await apiClient.post('/admin/doctor-schedules/import-xlsx', formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      });

      const payload = unwrapApiData<Record<string, unknown>>(data);
      return {
        doctorId: payload.doctorId != null ? String(payload.doctorId) : undefined,
        doctorName: typeof payload.doctorName === 'string' ? payload.doctorName : undefined,
        month: String(payload.month ?? ''),
        totalRows: Number(payload.totalRows ?? 0),
        importedRows: Number(payload.importedRows ?? 0),
        skippedRows: Number(payload.skippedRows ?? 0),
        warnings: Array.isArray(payload.warnings)
          ? payload.warnings.map((item) => String(item))
          : [],
      } satisfies DoctorScheduleImportResult;
    },
    onSuccess: (result) => {
      qc.invalidateQueries({ queryKey: ['admin', 'doctor-schedules'] });
      const baseMessage = `Đã nhập ${result.importedRows}/${result.totalRows} dòng lịch làm việc`;
      toast.success(result.skippedRows > 0 ? `${baseMessage}. Có ${result.skippedRows} dòng bị bỏ qua.` : baseMessage);
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Nhập lịch làm việc từ Excel thất bại')),
  });
}

export function useAdminDoctorLeaves(params?: Record<string, string>) {
  return useQuery({
    queryKey: adminQueryKeys.doctorLeaves(params),
    queryFn: async () => {
      const { data } = await apiClient.get('/admin/doctor-leaves', { params });
      const page = unwrapPage<unknown>(data);
      return {
        ...page,
        items: page.items.map(normalizeLeaveRequest),
      };
    },
  });
}

export function useReviewDoctorLeave() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      id,
      action,
      body,
    }: {
      id: string;
      action: 'approve' | 'reject';
      body?: { reviewNote?: string };
    }) => {
      const { data } = await apiClient.patch(
        `/admin/doctor-leaves/${id}/${action}`,
        body ?? {},
      );
      return normalizeLeaveRequest(unwrapApiData(data));
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'doctor-leaves'] });
      toast.success('Cập nhật đơn nghỉ thành công');
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Cập nhật đơn nghỉ thất bại')),
  });
}

export function usePdfJobPolling(type: 'invoice' | 'prescription', jobId: string | null) {
  return useQuery({
    queryKey: [type, 'pdf-job', jobId],
    queryFn: async () => {
      const prefix = type === 'invoice' ? '/cashier/invoice-pdf-jobs' : '/doctor/prescription-pdf-jobs';
      const { data } = await apiClient.get(`${prefix}/${jobId}`);
      return unwrapApiData<PdfJob>(data);
    },
    enabled: !!jobId,
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status === 'PENDING' || status === 'PROCESSING' ? 2000 : false;
    },
  });
}

export function useAdminMedications(params?: Record<string, string>) {
  return useQuery({
    queryKey: adminQueryKeys.medications(params),
    queryFn: async () => {
      const { data } = await apiClient.get('/admin/medications', { params });
      const page = unwrapPage<unknown>(data);
      return {
        ...page,
        items: page.items.map(normalizeMedication),
      };
    },
  });
}

export function useSaveMedication() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      id,
      body,
    }: {
      id?: string;
      body: Record<string, unknown>;
    }) => {
      const { data } = id
        ? await apiClient.put(`/admin/medications/${id}`, body)
        : await apiClient.post('/admin/medications', body);
      return normalizeMedication(unwrapApiData(data));
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'medications'] });
      toast.success('Lưu thuốc thành công');
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Lưu thuốc thất bại')),
  });
}

export function useAdminMedicalServices(params?: Record<string, string>) {
  return useQuery({
    queryKey: adminQueryKeys.medicalServices(params),
    queryFn: async () => {
      const { data } = await apiClient.get('/admin/medical-services', { params });
      const page = unwrapPage<unknown>(data);
      return {
        ...page,
        items: page.items.map(normalizeMedicalService),
      };
    },
  });
}

export function useUpdateMedicalServiceStatus() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, status }: { id: string; status: string }) => {
      const { data } = await apiClient.patch(`/admin/medical-services/${id}/status`, { status });
      return normalizeMedicalService(unwrapApiData(data));
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'medical-services'] });
      qc.invalidateQueries({ queryKey: ['public', 'medical-services'] });
      toast.success('Cập nhật trạng thái dịch vụ thành công');
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Cập nhật trạng thái dịch vụ thất bại')),
  });
}

export function useSaveMedicalService() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      id,
      body,
    }: {
      id?: string;
      body: Record<string, unknown>;
    }) => {
      const { data } = id
        ? await apiClient.put(`/admin/medical-services/${id}`, body)
        : await apiClient.post('/admin/medical-services', body);
      return normalizeMedicalService(unwrapApiData(data));
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'medical-services'] });
      qc.invalidateQueries({ queryKey: ['public', 'medical-services'] });
      toast.success('Lưu dịch vụ thành công');
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Lưu dịch vụ thất bại')),
  });
}

export function useAdminSpecialties(params?: Record<string, string>) {
  return useQuery({
    queryKey: adminQueryKeys.specialties(params),
    queryFn: async () => {
      const { data } = await apiClient.get('/admin/specialties', { params });
      const page = unwrapPage<unknown>(data);
      return {
        ...page,
        items: page.items.map(normalizeAdminSpecialty),
      };
    },
  });
}

export function useUpdateSpecialtyStatus() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, status }: { id: string; status: string }) => {
      const { data } = await apiClient.patch(`/admin/specialties/${id}/status`, { status });
      return normalizeAdminSpecialty(unwrapApiData(data));
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'specialties'] });
      qc.invalidateQueries({ queryKey: ['public', 'specialties'] });
      toast.success('Cập nhật trạng thái chuyên khoa thành công');
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Cập nhật trạng thái chuyên khoa thất bại')),
  });
}

export function useSaveSpecialty() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      id,
      body,
    }: {
      id?: string;
      body: Record<string, unknown>;
    }) => {
      const { data } = id
        ? await apiClient.put(`/admin/specialties/${id}`, body)
        : await apiClient.post('/admin/specialties', body);
      return normalizeAdminSpecialty(unwrapApiData(data));
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'specialties'] });
      qc.invalidateQueries({ queryKey: ['public', 'specialties'] });
      toast.success('Lưu chuyên khoa thành công');
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Lưu chuyên khoa thất bại')),
  });
}

export function useUpdateStaffStatus() {
  const qc = useQueryClient();

  return useMutation({
    mutationFn: async ({ id, status }: { id: string; status: string }) => {
      const { data } = await apiClient.patch(`/admin/staffs/${id}/status`, { status });
      return normalizeStaff(unwrapApiData(data));
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'staffs'] });
      toast.success('Cập nhật trạng thái nhân sự thành công');
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Cập nhật trạng thái nhân sự thất bại')),
  });
}

export function useUpdateMedicationStatus() {
  const qc = useQueryClient();

  return useMutation({
    mutationFn: async ({ id, status }: { id: string; status: string }) => {
      const { data } = await apiClient.patch(`/admin/medications/${id}/status`, { status });
      return normalizeMedication(unwrapApiData(data));
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'medications'] });
      qc.invalidateQueries({ queryKey: ['public', 'medications'] });
      toast.success('Cập nhật trạng thái thuốc thành công');
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Cập nhật trạng thái thuốc thất bại')),
  });
}

export function useAppointmentSummary(
  params?: Record<string, string>,
  options?: { enabled?: boolean; refetchInterval?: number | false; refetchOnWindowFocus?: boolean; staleTime?: number },
) {
  return useQuery<AppointmentSummary>({
    queryKey: adminQueryKeys.appointmentSummary(params),
    queryFn: async () => {
      const { data } = await apiClient.get('/admin/appointments/summary', { params });
      return normalizeAppointmentSummary(unwrapApiData(data));
    },
    staleTime: 30_000,
    ...options,
  });
}

function readSummaryNumber(item: Record<string, unknown>, ...keys: string[]) {
  for (const key of keys) {
    const value = item[key];
    if (typeof value === 'number') return value;
    if (typeof value === 'string' && value.trim()) {
      const parsed = Number(value);
      if (Number.isFinite(parsed)) return parsed;
    }
  }
  return 0;
}

function normalizeAppointmentSummary(raw: unknown): AppointmentSummary {
  const item = raw && typeof raw === 'object' ? (raw as Record<string, unknown>) : {};
  return {
    all: readSummaryNumber(item, 'all', 'total', 'totalItems', 'totalAppointments'),
    pending: readSummaryNumber(item, 'requested', 'pending', 'REQUESTED'),
    confirmed: readSummaryNumber(item, 'confirmed', 'CONFIRMED'),
    checkedIn: readSummaryNumber(item, 'checkedIn', 'checked_in', 'CHECKED_IN'),
    completed: readSummaryNumber(item, 'completed', 'COMPLETED'),
    cancelled: readSummaryNumber(item, 'cancelled', 'CANCELLED'),
    noShow: readSummaryNumber(item, 'noShow', 'no_show', 'NO_SHOW'),
  };
}

function buildWebSocketUrl() {
  if (typeof window === 'undefined') return null;

  const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || '/api';
  const normalizedApiBaseUrl = apiBaseUrl.replace(/\/$/, '');

  const absoluteHttpBase = normalizedApiBaseUrl.startsWith('http')
    ? normalizedApiBaseUrl
    : `${window.location.origin}${normalizedApiBaseUrl.startsWith('/') ? '' : '/'}${normalizedApiBaseUrl}`;

  const appBaseUrl = absoluteHttpBase.replace(/\/api$/, '');
  return `${appBaseUrl.replace(/^http/, 'ws')}/ws`;
}

function normalizeRealtimeBranchId(branchId?: string | number | null) {
  if (branchId == null) return null;
  const normalized = String(branchId).trim();
  return normalized || null;
}

function resolveAppointmentSummaryDestination(
  role?: AppRole,
  userBranchId?: string | number | null,
  filterBranchId?: string | number | null,
) {
  const scopedBranchId =
    role === 'STAFF'
      ? normalizeRealtimeBranchId(userBranchId)
      : normalizeRealtimeBranchId(filterBranchId);

  if (scopedBranchId) {
    return `/topic/branches/${scopedBranchId}/appointments/summary`;
  }

  if (role === 'STAFF') {
    return null;
  }

  if (role === 'OPERATIONS_ADMIN' || role === 'SYSTEM_ADMIN') {
    return '/topic/appointments/summary';
  }

  return null;
}

type AppointmentRealtimePayload = {
  event?: string;
  appointmentId?: number | string | null;
  branchId?: number | string | null;
  doctorId?: number | string | null;
  visitDate?: string | null;
  previousStatus?: Appointment['status'] | string | null;
  status?: Appointment['status'] | string | null;
  processingById?: number | string | null;
  processingByName?: string | null;
  processingStartedAt?: string | null;
  processingExpiresAt?: string | null;
  arrivalStatus?: Appointment['arrivalStatus'] | string | null;
  queueNo?: number | null;
  receptionQueueNo?: number | null;
  arrivedAt?: string | null;
  arrivedByName?: string | null;
  checkedInAt?: string | null;
  checkedInByName?: string | null;
  confirmedAt?: string | null;
  confirmedByName?: string | null;
  noShowMarkedAt?: string | null;
  noShowMarkedByName?: string | null;
  followUpPending?: boolean | null;
  noShowNote?: string | null;
  rescheduledFromAppointmentId?: string | number | null;
  rescheduledToAppointmentId?: string | number | null;
  overdue?: boolean | null;
  autoCancelled?: boolean | null;
  cancelledBySystem?: boolean | null;
  cancellationReason?: string | null;
  cancelledAt?: string | null;
  cancelledByName?: string | null;
};

type AppointmentListCache = {
  items: Appointment[];
  meta?: Record<string, unknown>;
};


type AppointmentSummaryCounterKey =
  | 'all'
  | 'pending'
  | 'confirmed'
  | 'checkedIn'
  | 'completed'
  | 'cancelled'
  | 'noShow';

const STATUS_TO_SUMMARY_KEY: Partial<Record<Appointment['status'], AppointmentSummaryCounterKey>> = {
  REQUESTED: 'pending',
  CONFIRMED: 'confirmed',
  CHECKED_IN: 'checkedIn',
  COMPLETED: 'completed',
  CANCELLED: 'cancelled',
  NO_SHOW: 'noShow',
};

function createEmptyAppointmentSummary(): AppointmentSummary {
  return {
    all: 0,
    pending: 0,
    confirmed: 0,
    checkedIn: 0,
    completed: 0,
    cancelled: 0,
    noShow: 0,
  };
}

function matchesAppointmentSummaryFilters(
  appointment: Appointment,
  params?: AppointmentListQueryParams,
) {
  if (!params) return true;

  if (params.branchId && String(appointment.branchId) !== String(params.branchId)) {
    return false;
  }

  if (params.doctorId && String(appointment.doctorId) !== String(params.doctorId)) {
    return false;
  }

  if (params.visitDate && String(appointment.visitDate) !== String(params.visitDate)) {
    return false;
  }

  return true;
}

function adjustAppointmentSummary(
  summary: AppointmentSummary | undefined,
  appointment: Appointment,
  delta: 1 | -1,
): AppointmentSummary {
  const next = { ...(summary ?? createEmptyAppointmentSummary()) };
  next.all = Math.max(0, (next.all ?? 0) + delta);

  const summaryKey = STATUS_TO_SUMMARY_KEY[appointment.status];
  if (summaryKey) {
    next[summaryKey] = Math.max(0, (next[summaryKey] ?? 0) + delta);
  }

  return next;
}

function patchSummaryCachesForTransition(
  qc: ReturnType<typeof useQueryClient>,
  previous: Appointment | undefined,
  next: Appointment | undefined,
) {
  if (!previous && !next) return;

  const summaryQueries = qc.getQueriesData({
    queryKey: ['admin', 'appointments', 'summary'],
  });

  summaryQueries.forEach(([queryKey, cacheValue]) => {
    if (!Array.isArray(queryKey) || queryKey[2] !== 'summary') return;

    const summaryParams = isQueryParamRecord(queryKey[3]) ? queryKey[3] : undefined;
    const previousMatches = previous ? matchesAppointmentSummaryFilters(previous, summaryParams) : false;
    const nextMatches = next ? matchesAppointmentSummaryFilters(next, summaryParams) : false;

    if (!previousMatches && !nextMatches) return;

    let patched = (cacheValue as AppointmentSummary | undefined) ?? createEmptyAppointmentSummary();
    if (previous && previousMatches) {
      patched = adjustAppointmentSummary(patched, previous, -1);
    }
    if (next && nextMatches) {
      patched = adjustAppointmentSummary(patched, next, 1);
    }

    qc.setQueryData(queryKey, patched);
  });
}

function findCachedAppointmentSnapshot(
  qc: ReturnType<typeof useQueryClient>,
  appointmentId: string,
): Appointment | undefined {
  const detailCache = qc.getQueryData<Appointment>(adminQueryKeys.appointmentDetail(appointmentId));
  if (detailCache) {
    return detailCache;
  }

  const listQueries = qc.getQueriesData({
    queryKey: ['admin', 'appointments'],
  });

  for (const [queryKey, cacheValue] of listQueries) {
    if (!Array.isArray(queryKey)) continue;
    const thirdPart = queryKey[2];
    if (typeof thirdPart === 'string') continue;
    if (!isAppointmentListCache(cacheValue)) continue;

    const found = cacheValue.items.find((item) => String(item.id) === appointmentId);
    if (found) {
      return found;
    }
  }

  return undefined;
}

function patchAppointmentDetailCacheWithAppointment(
  qc: ReturnType<typeof useQueryClient>,
  appointment: Appointment,
) {
  qc.setQueryData(adminQueryKeys.appointmentDetail(String(appointment.id)), appointment);
}

function patchAppointmentAcrossListCaches(
  qc: ReturnType<typeof useQueryClient>,
  appointment: Appointment,
) {
  const listQueries = qc.getQueriesData({
    queryKey: ['admin', 'appointments'],
  });

  listQueries.forEach(([queryKey, cacheValue]) => {
    if (!Array.isArray(queryKey)) return;

    const thirdPart = queryKey[2];
    if (typeof thirdPart === 'string') return;
    if (!isAppointmentListCache(cacheValue)) return;

    const listParams = isQueryParamRecord(thirdPart) ? thirdPart : undefined;
    const hasTarget = cacheValue.items.some((item) => String(item.id) === String(appointment.id));
    if (!hasTarget) return;

    const patchedItems = cacheValue.items
      .map((item) => (String(item.id) === String(appointment.id) ? appointment : item))
      .filter((item) => matchesAppointmentFilters(item, listParams));

    const removedCount = cacheValue.items.length - patchedItems.length;
    const nextMeta =
      removedCount > 0 && cacheValue.meta
        ? {
            ...cacheValue.meta,
            totalItems:
              typeof cacheValue.meta.totalItems === 'number'
                ? Math.max(0, cacheValue.meta.totalItems - removedCount)
                : cacheValue.meta.totalItems,
          }
        : cacheValue.meta;

    qc.setQueryData(queryKey, {
      ...cacheValue,
      items: patchedItems,
      ...(nextMeta ? { meta: nextMeta } : {}),
    });
  });
}

function buildSummaryPatchAppointmentFromPayload(
  payload: AppointmentRealtimePayload,
  status: Appointment['status'],
): Appointment {
  return {
    id: String(payload.appointmentId ?? ''),
    code: '',
    patientFullName: '',
    patientPhone: '',
    patientEmail: '',
    patientDob: '',
    patientGender: '',
    doctorId: String(payload.doctorId ?? ''),
    doctorName: '',
    branchId: String(payload.branchId ?? ''),
    branchName: '',
    specialtyId: '',
    specialtyName: '',
    visitDate: String(payload.visitDate ?? ''),
    session: '',
    slotStart: '',
    status,
    createdAt: '',
    updatedAt: '',
  };
}

function isQueryParamRecord(value: unknown): value is AppointmentListQueryParams {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
  return Object.values(value).every(
    (item) =>
      item == null ||
      typeof item === 'string' ||
      typeof item === 'number' ||
      typeof item === 'boolean',
  );
}

function isAppointmentListCache(value: unknown): value is AppointmentListCache {
  return (
    Boolean(value) &&
    typeof value === 'object' &&
    Array.isArray((value as { items?: unknown }).items)
  );
}

function normalizeStatusFilter(value?: AppointmentListQueryParams['status'] | null) {
  if (!value) return null;
  return String(value);
}

function matchesAppointmentFilters(
  appointment: Appointment,
  params?: AppointmentListQueryParams,
) {
  if (!params) return true;

  if (params.branchId && String(appointment.branchId) !== String(params.branchId)) {
    return false;
  }

  if (params.doctorId && String(appointment.doctorId) !== String(params.doctorId)) {
    return false;
  }

  if (params.visitDate && String(appointment.visitDate) !== String(params.visitDate)) {
    return false;
  }

  const normalizedStatus = normalizeStatusFilter(params.status);
  if (normalizedStatus && String(appointment.status) !== normalizedStatus) {
    return false;
  }

  if (params.followUpPending != null) {
    const expected = String(params.followUpPending) === 'true';
    if (appointment.followUpPending !== expected) {
      return false;
    }
  }

  if (params.overdue != null) {
    const expected = String(params.overdue) === 'true';
    if (appointment.overdue !== expected) {
      return false;
    }
  }

  return true;
}

function patchAppointmentWithRealtimePayload(
  appointment: Appointment,
  payload: AppointmentRealtimePayload,
): Appointment {
  let next = appointment;

  if (payload.event === 'APPOINTMENT_PROCESSING_CHANGED') {
    const hasProcessor = payload.processingById !== null && typeof payload.processingById !== 'undefined';

    next = {
      ...next,
      claimedBy: payload.processingByName ?? undefined,
      processingById: hasProcessor ? String(payload.processingById) : undefined,
      processingStartedAt: hasProcessor ? payload.processingStartedAt ?? next.processingStartedAt : undefined,
      processingExpiresAt: hasProcessor ? payload.processingExpiresAt ?? undefined : undefined,
    };
  }

  if (payload.event === 'APPOINTMENT_UPDATED' && payload.status) {
    next = {
      ...next,
      status: String(payload.status) as Appointment['status'],
      arrivalStatus:
        payload.arrivalStatus != null
          ? (String(payload.arrivalStatus) as Appointment['arrivalStatus'])
          : next.arrivalStatus,
      queueNo: typeof payload.queueNo === 'number' ? payload.queueNo : next.queueNo,
      receptionQueueNo:
        typeof payload.receptionQueueNo === 'number'
          ? payload.receptionQueueNo
          : next.receptionQueueNo,
      arrivedAt: payload.arrivedAt ?? next.arrivedAt,
      arrivedByName: payload.arrivedByName ?? next.arrivedByName,
      checkedInAt: payload.checkedInAt ?? next.checkedInAt,
      checkedInByName: payload.checkedInByName ?? next.checkedInByName,
      confirmedAt: payload.confirmedAt ?? next.confirmedAt,
      confirmedByName: payload.confirmedByName ?? next.confirmedByName,
      noShowMarkedAt: payload.noShowMarkedAt ?? next.noShowMarkedAt,
      noShowMarkedByName: payload.noShowMarkedByName ?? next.noShowMarkedByName,
      followUpPending:
        typeof payload.followUpPending === 'boolean'
          ? payload.followUpPending
          : next.followUpPending,
      noShowNote: payload.noShowNote ?? next.noShowNote,
      rescheduledFromAppointmentId:
        payload.rescheduledFromAppointmentId != null
          ? String(payload.rescheduledFromAppointmentId)
          : next.rescheduledFromAppointmentId,
      rescheduledToAppointmentId:
        payload.rescheduledToAppointmentId != null
          ? String(payload.rescheduledToAppointmentId)
          : next.rescheduledToAppointmentId,
      overdue:
        typeof payload.overdue === 'boolean'
          ? payload.overdue
          : next.overdue,
      autoCancelled:
        typeof payload.autoCancelled === 'boolean'
          ? payload.autoCancelled
          : typeof payload.cancelledBySystem === 'boolean'
            ? payload.cancelledBySystem
            : next.autoCancelled,
      cancellationReason: payload.cancellationReason ?? next.cancellationReason,
      cancelledAt: payload.cancelledAt ?? next.cancelledAt,
      cancelledByName: payload.cancelledByName ?? next.cancelledByName,
    };
  }

  return next;
}

export function useAppointmentSummaryRealtime(
  params?: Record<string, string>,
  options?: { appointmentId?: string | number | null },
) {
  const qc = useQueryClient();
  const accessToken = useAuthStore((state) => state.accessToken);
  const currentUser = useAuthStore((state) => state.user);
  const [isConnected, setIsConnected] = useState(false);
  const userRole = currentUser?.role;
  const userBranchId = currentUser?.branchId;

  const filters = useMemo(
    () => ({
      branchId: params?.branchId ? String(params.branchId) : null,
      visitDate: params?.visitDate ? String(params.visitDate) : null,
    }),
    [params?.branchId, params?.visitDate],
  );

  useEffect(() => {
    const wsUrl = buildWebSocketUrl();
    const destination = resolveAppointmentSummaryDestination(
      userRole,
      userBranchId,
      filters.branchId,
    );

    if (!accessToken || !wsUrl || !userRole || userRole === 'PATIENT' || !destination) {
      if (import.meta.env.DEV && userRole === 'STAFF' && !userBranchId) {
        console.debug('[appointments-stomp] Skip appointment realtime for STAFF: missing branchId');
      }
      setIsConnected(false);
      return;
    }

    let isMounted = true;
    let summarySubscription: StompSubscription | undefined;

    const appointmentIdFilter =
      options?.appointmentId != null ? String(options.appointmentId) : null;

    const invalidateActive = (queryKey: readonly unknown[]) => {
      qc.invalidateQueries({ queryKey, refetchType: 'active' });
    };

    const invalidateSummaryQueries = () => {
      invalidateActive(['admin', 'appointments', 'summary']);
    };

    const invalidateAppointmentListQueries = () => {
      invalidateActive(['admin', 'appointments']);
      invalidateActive(['reception', 'queue']);
      invalidateActive(['reception', 'queue', 'summary']);
    };

    const invalidateAllAppointmentViews = () => {
      invalidateSummaryQueries();
      invalidateAppointmentListQueries();
      invalidateAppointmentDetail();
    };

    const invalidateAppointmentDetail = () => {
      if (appointmentIdFilter) {
        invalidateActive(adminQueryKeys.appointmentDetail(appointmentIdFilter));
      }
    };

    const shouldHandlePayload = (payload: AppointmentRealtimePayload) => {
      if (
        payload.event &&
        !['APPOINTMENT_SUMMARY_CHANGED', 'APPOINTMENT_PROCESSING_CHANGED', 'APPOINTMENT_UPDATED'].includes(
          payload.event,
        )
      ) {
        return false;
      }

      if (appointmentIdFilter) {
        if (payload.appointmentId == null) {
          return false;
        }
        if (String(payload.appointmentId) !== appointmentIdFilter) {
          return false;
        }
      }

      if (filters.branchId && String(payload.branchId ?? '') !== filters.branchId) {
        return false;
      }

      if (filters.visitDate && String(payload.visitDate ?? '') !== filters.visitDate) {
        return false;
      }

      return true;
    };

    const patchListCaches = (payload: AppointmentRealtimePayload) => {
      if (payload.appointmentId == null) return;

      const targetId = String(payload.appointmentId);

      const listQueries = qc.getQueriesData({
        queryKey: ['admin', 'appointments'],
      });

      listQueries.forEach(([queryKey, cacheValue]) => {
        if (!Array.isArray(queryKey)) return;

        const thirdPart = queryKey[2];

        // Skip summary/detail keys, only patch paginated list caches
        if (typeof thirdPart === 'string') return;
        if (!isAppointmentListCache(cacheValue)) return;

        const listParams = isQueryParamRecord(thirdPart) ? thirdPart : undefined;
        const hasTarget = cacheValue.items.some((item) => String(item.id) === targetId);
        if (!hasTarget) return;

        const patchedItems = cacheValue.items
          .map((item) =>
            String(item.id) === targetId
              ? patchAppointmentWithRealtimePayload(item, payload)
              : item,
          )
          .filter((item) => matchesAppointmentFilters(item, listParams));

        const removedCount = cacheValue.items.length - patchedItems.length;
        const nextMeta =
          removedCount > 0 && cacheValue.meta
            ? {
                ...cacheValue.meta,
                totalItems:
                  typeof cacheValue.meta.totalItems === 'number'
                    ? Math.max(0, cacheValue.meta.totalItems - removedCount)
                    : cacheValue.meta.totalItems,
              }
            : cacheValue.meta;

        qc.setQueryData(queryKey, {
          ...cacheValue,
          items: patchedItems,
          ...(nextMeta ? { meta: nextMeta } : {}),
        });
      });
    };

    const patchDetailCache = (payload: AppointmentRealtimePayload) => {
      if (!appointmentIdFilter) return;
      if (payload.appointmentId == null) return;
      if (String(payload.appointmentId) !== appointmentIdFilter) return;

      qc.setQueryData(
        adminQueryKeys.appointmentDetail(appointmentIdFilter),
        (old: Appointment | undefined) => {
          if (!old) return old;
          return patchAppointmentWithRealtimePayload(old, payload);
        },
      );
    };

    let everConnected = false;

    const client = new Client({
      brokerURL: wsUrl,
      connectHeaders: {
        Authorization: `Bearer ${accessToken}`,
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: import.meta.env.DEV ? (message) => console.debug('[appointments-stomp]', message) : () => {},
      onConnect: () => {
        if (!isMounted) return;

        everConnected = true;
        setIsConnected(true);

        summarySubscription?.unsubscribe();
        summarySubscription = client.subscribe(destination, (message: IMessage) => {
          try {
            const payload = JSON.parse(message.body) as AppointmentRealtimePayload;
            if (!shouldHandlePayload(payload)) return;

            if (payload.event === 'APPOINTMENT_PROCESSING_CHANGED') {
              patchListCaches(payload);
              patchDetailCache(payload);
              return;
            }

            if (payload.event === 'APPOINTMENT_UPDATED') {
              const previous =
                payload.appointmentId != null
                  ? findCachedAppointmentSnapshot(qc, String(payload.appointmentId))
                  : undefined;

              patchListCaches(payload);
              patchDetailCache(payload);

              const next = previous ? patchAppointmentWithRealtimePayload(previous, payload) : undefined;
              if (previous && next) {
                patchSummaryCachesForTransition(qc, previous, next);
                return;
              }

              if (payload.previousStatus && payload.status) {
                const previousFromPayload = buildSummaryPatchAppointmentFromPayload(
                  payload,
                  String(payload.previousStatus) as Appointment['status'],
                );
                const nextFromPayload = buildSummaryPatchAppointmentFromPayload(
                  payload,
                  String(payload.status) as Appointment['status'],
                );
                patchSummaryCachesForTransition(qc, previousFromPayload, nextFromPayload);
                invalidateAppointmentListQueries();
                return;
              }

              invalidateSummaryQueries();
              invalidateAppointmentListQueries();
              return;
            }

            // Fallback cho summary changed / payload lạ
            invalidateSummaryQueries();
            invalidateAppointmentListQueries();
          } catch {
            invalidateSummaryQueries();
            invalidateAppointmentListQueries();
            invalidateAppointmentDetail();
          }
        });
      },
      onStompError: () => {
        if (isMounted) setIsConnected(false);
        if (!everConnected) {
          client.deactivate();
        }
      },
      onWebSocketClose: () => {
        if (isMounted) setIsConnected(false);
        if (!everConnected) {
          client.deactivate();
        }
      },
      onWebSocketError: () => {
        if (isMounted) setIsConnected(false);
        if (!everConnected) {
          client.deactivate();
        }
      },
    });

    client.activate();

    return () => {
      isMounted = false;
      setIsConnected(false);
      summarySubscription?.unsubscribe();
      void client.deactivate();
    };
  }, [
    accessToken,
    filters.branchId,
    filters.visitDate,
    options?.appointmentId,
    qc,
    userBranchId,
    userRole,
  ]);

  return { isConnected };
}

export function useResetDoctorAccountPassword() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ doctorId }: { doctorId: string }) => {
      const { data } = await apiClient.post(`/admin/doctors/${doctorId}/account/reset-password`);
      return unwrapApiData<{ deliveryChannel?: string; deliveryTarget?: string; deliverySent?: boolean; setupUrl?: string; expiresAt?: string; status?: string; email?: string; phone?: string }>(data);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'doctors'] });
      toast.success('Đã reset mật khẩu tài khoản bác sĩ');
    },
    onError: (error: unknown) => {
      const maybeAxios = error as { response?: { data?: { message?: string } } };
      toast.error(maybeAxios.response?.data?.message || 'Reset mật khẩu thất bại');
    },
  });
}
