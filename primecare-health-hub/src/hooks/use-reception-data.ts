import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/lib/api-client';
import {
  normalizeAppointment,
  normalizeFollowUpQueueItem,
  normalizePatient,
  unwrapApiData,
  unwrapPage,
} from '@/lib/api-adapters';
import { getApiErrorMessage } from '@/lib/error-utils';
import type {
  Appointment,
  FollowUpQueueItem,
  Patient,
  ReceptionQueueSummary,
  WalkInFormRequest,
} from '@/types/api';
import { toast } from 'sonner';

const DOCTOR_OPERATION_NOT_READY_CODES = new Set([
  'DOCTOR_NOT_READY',
  'DOCTOR_NOT_BOOKABLE',
  'DOCTOR_NOT_OPERATIONAL_READY',
  'DOCTOR_NOT_OPERATIONALLY_READY',
  'DOCTOR_ACCOUNT_MISSING',
  'DOCTOR_ACCOUNT_INACTIVE',
  'DOCTOR_ACCOUNT_BLOCKED',
]);

function getApiErrorCode(error: unknown) {
  const code = (error as { response?: { data?: { code?: unknown; errorCode?: unknown } } })
    .response?.data;
  const value = code?.code ?? code?.errorCode;
  return typeof value === 'string' ? value : undefined;
}

function isDoctorOperationNotReadyError(error: unknown) {
  const code = getApiErrorCode(error);
  if (code && DOCTOR_OPERATION_NOT_READY_CODES.has(code)) return true;

  const message = getApiErrorMessage(error, '').toLowerCase();
  return (
    (message.includes('doctor') || message.includes('bác sĩ') || message.includes('bac si')) &&
    (message.includes('ready') ||
      message.includes('sẵn sàng') ||
      message.includes('san sang') ||
      message.includes('bookable') ||
      message.includes('operational') ||
      message.includes('account') ||
      message.includes('tài khoản') ||
      message.includes('tai khoan'))
  );
}

export const receptionQueryKeys = {
  queue: (params?: Record<string, string>) => ['reception', 'queue', params] as const,
  queueSummary: (params?: Record<string, string>) => ['reception', 'queue', 'summary', params] as const,
  followUpQueue: (params?: Record<string, string>) => ['reception', 'follow-up', params] as const,
  patientSearch: (q: string) => ['reception', 'patients', 'search', q] as const,
};

function normalizeReceptionQueueSummary(raw: unknown): ReceptionQueueSummary {
  const item = (raw ?? {}) as Record<string, unknown>;
  const readNumber = (...keys: string[]) => {
    for (const key of keys) {
      const value = item[key];
      if (typeof value === 'number') return value;
      if (typeof value === 'string' && value.trim()) {
        const parsed = Number(value);
        if (Number.isFinite(parsed)) return parsed;
      }
    }
    return 0;
  };
  const hasPriority = item.priority != null || item.priorityCount != null;
  const hasOverdue = item.overdueCount != null || item.overdue != null;
  const hasNoShowFollowUp =
    item.noShowFollowUpPending != null || item.noShowFollowUpPendingCount != null;
  const hasDoctorCancellationNoResponse =
    item.doctorCancellationNoResponse != null ||
    item.doctorCancellationNoResponseCount != null ||
    item.doctorCancellationNoResponsePending != null;
  const hasDoctorCancellationContactRequested =
    item.doctorCancellationContactRequested != null ||
    item.doctorCancellationContactRequestedCount != null ||
    item.doctorCancellationContactRequestedPending != null;
  const hasFollowUpPending =
    item.followUpPending != null || item.followUpPendingCount != null || item.followUpTotal != null;

  return {
    total: readNumber('total', 'all', 'totalItems'),
    requested: readNumber('requested', 'pending', 'REQUESTED', 'PENDING'),
    confirmed: readNumber('confirmed', 'CONFIRMED'),
    arrived: readNumber('arrived', 'ARRIVED'),
    checkedIn: readNumber('checkedIn', 'checked_in', 'CHECKED_IN'),
    notArrived: readNumber('notArrived', 'not_arrived', 'NOT_ARRIVED'),
    walkIn: readNumber('walkIn', 'walk_in', 'WALK_IN'),
    priority: hasPriority ? readNumber('priority', 'priorityCount') : undefined,
    overdueCount: hasOverdue ? readNumber('overdueCount', 'overdue') : undefined,
    noShowFollowUpPending: hasNoShowFollowUp
      ? readNumber('noShowFollowUpPending', 'noShowFollowUpPendingCount')
      : undefined,
    doctorCancellationNoResponse: hasDoctorCancellationNoResponse
      ? readNumber(
          'doctorCancellationNoResponse',
          'doctorCancellationNoResponseCount',
          'doctorCancellationNoResponsePending',
        )
      : undefined,
    doctorCancellationContactRequested: hasDoctorCancellationContactRequested
      ? readNumber(
          'doctorCancellationContactRequested',
          'doctorCancellationContactRequestedCount',
          'doctorCancellationContactRequestedPending',
        )
      : undefined,
    followUpPending: hasFollowUpPending
      ? readNumber('followUpPending', 'followUpPendingCount', 'followUpTotal')
      : undefined,
  };
}

export function useReceptionQueue(params?: Record<string, string>) {
  return useQuery({
    queryKey: receptionQueryKeys.queue(params),
    queryFn: async () => {
      const { data } = await apiClient.get('/reception/appointments/queue', { params });
      const page = unwrapPage<unknown>(data);
      return {
        ...page,
        items: page.items.map(normalizeAppointment),
      };
    },
    staleTime: 15_000,
    refetchOnWindowFocus: false,
    placeholderData: (previousData) => previousData,
  });
}

export function useReceptionQueueSummary(params?: Record<string, string>) {
  return useQuery({
    queryKey: receptionQueryKeys.queueSummary(params),
    queryFn: async () => {
      const { data } = await apiClient.get('/reception/appointments/queue/summary', { params });
      return normalizeReceptionQueueSummary(unwrapApiData(data));
    },
    staleTime: 15_000,
    refetchOnWindowFocus: false,
    placeholderData: (previousData) => previousData,
  });
}

export function useFollowUpQueue(params?: Record<string, string>) {
  return useQuery({
    queryKey: receptionQueryKeys.followUpQueue(params),
    queryFn: async () => {
      const { data } = await apiClient.get('/reception/follow-up/queue', { params });
      const payload = unwrapApiData<unknown>(data);
      const page = unwrapPage<unknown>(data);
      const directItems =
        payload && typeof payload === 'object' && !Array.isArray(payload)
          ? (payload as Record<string, unknown>).followUps ??
            (payload as Record<string, unknown>).cases ??
            (payload as Record<string, unknown>).queue
          : undefined;
      return {
        ...page,
        items: (Array.isArray(directItems) ? directItems : page.items).map(
          normalizeFollowUpQueueItem,
        ) as FollowUpQueueItem[],
      };
    },
    staleTime: 15_000,
    refetchOnWindowFocus: false,
    placeholderData: (previousData) => previousData,
  });
}

function unwrapPatientSearchItems(raw: unknown) {
  const payload = unwrapApiData(raw);
  if (Array.isArray(payload)) return payload;
  if (payload && typeof payload === 'object') {
    const record = payload as Record<string, unknown>;
    if (Array.isArray(record.items)) return record.items;
    if (Array.isArray(record.content)) return record.content;
  }
  return [];
}

export function useReceptionPatientSearch(q: string) {
  const normalizedQuery = q.trim();

  return useQuery<Patient[]>({
    queryKey: receptionQueryKeys.patientSearch(normalizedQuery),
    enabled: normalizedQuery.length >= 2,
    queryFn: async () => {
      const { data } = await apiClient.get('/reception/patients/search', {
        params: { q: normalizedQuery },
      });
      return unwrapPatientSearchItems(data).map(normalizePatient);
    },
    staleTime: 30_000,
    refetchOnWindowFocus: false,
  });
}

function toApiId(value: string) {
  return /^\d+$/.test(value) ? Number(value) : value;
}

export function useCreateWalkIn() {
  const qc = useQueryClient();

  return useMutation({
    mutationFn: async (body: WalkInFormRequest) => {
      const payload = {
        patientId: body.patientId ? toApiId(body.patientId) : undefined,
        branchId: Number(body.branchId),
        specialtyId: Number(body.specialtyId),
        doctorId: Number(body.doctorId),
        visitDate: body.visitDate,
        session: body.session,
        slotStart: body.slotStart,
        patientFullName: body.patientFullName,
        patientPhone: body.patientPhone,
        patientEmail: body.patientEmail || undefined,
        patientDob: body.patientDob || undefined,
        patientGender: body.patientGender || undefined,
        patientNote: body.patientNote || undefined,
        arrived: body.arrived ?? true,
      };

      const { data } = await apiClient.post('/reception/appointments/walk-in', payload);
      return normalizeAppointment(unwrapApiData(data));
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['reception', 'queue'] });
      qc.invalidateQueries({ queryKey: ['reception', 'queue', 'summary'] });
      qc.invalidateQueries({ queryKey: ['admin', 'appointments'] });
      qc.invalidateQueries({ queryKey: ['admin', 'appointments', 'summary'] });
      toast.success('Tạo lượt khám walk-in thành công');
    },
    onError: (error) =>
      toast.error(
        isDoctorOperationNotReadyError(error)
          ? 'Bác sĩ chưa sẵn sàng vận hành vì chưa có tài khoản hoạt động.'
          : getApiErrorMessage(error, 'Tạo lượt khám walk-in thất bại'),
      ),
  });
}

export function useManualCheckIn() {
  const qc = useQueryClient();

  return useMutation({
    mutationFn: async (appointmentId: string) => {
      const { data } = await apiClient.post(
        `/reception/appointments/${appointmentId}/manual-check-in`,
      );
      return normalizeAppointment(unwrapApiData(data));
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['reception', 'queue'] });
      qc.invalidateQueries({ queryKey: ['reception', 'queue', 'summary'] });
      qc.invalidateQueries({ queryKey: ['admin', 'appointments'] });
      qc.invalidateQueries({ queryKey: ['admin', 'appointments', 'detail'] });
      qc.invalidateQueries({ queryKey: ['admin', 'appointments', 'summary'] });
      toast.success('Check-in thủ công thành công');
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Check-in thủ công thất bại')),
  });
}

export function useQrCheckIn() {
  const qc = useQueryClient();

  return useMutation({
    mutationFn: async (body: { qrToken: string }) => {
      const { data } = await apiClient.post('/reception/appointments/check-in/qr', body);
      return normalizeAppointment(unwrapApiData(data));
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['reception', 'queue'] });
      qc.invalidateQueries({ queryKey: ['reception', 'queue', 'summary'] });
      qc.invalidateQueries({ queryKey: ['admin', 'appointments'] });
      qc.invalidateQueries({ queryKey: ['admin', 'appointments', 'detail'] });
      qc.invalidateQueries({ queryKey: ['admin', 'appointments', 'summary'] });
      toast.success('Check-in QR thành công');
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Mã QR check-in không hợp lệ.')),
  });
}
