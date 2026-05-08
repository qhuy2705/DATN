import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/lib/api-client';
import { normalizeAppointment, normalizePatient, unwrapApiData, unwrapPage } from '@/lib/api-adapters';
import { getApiErrorMessage } from '@/lib/error-utils';
import type { Appointment, Patient, ReceptionQueueSummary, WalkInFormRequest } from '@/types/api';
import { toast } from 'sonner';

export const receptionQueryKeys = {
  queue: (params?: Record<string, string>) => ['reception', 'queue', params] as const,
  queueSummary: (params?: Record<string, string>) => ['reception', 'queue', 'summary', params] as const,
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
    onError: (error) => toast.error(getApiErrorMessage(error, 'Tạo lượt khám walk-in thất bại')),
  });
}

export function useMarkArrived() {
  const qc = useQueryClient();

  return useMutation({
    mutationFn: async (appointmentId: string) => {
      const { data } = await apiClient.post(`/reception/appointments/${appointmentId}/arrive`);
      return normalizeAppointment(unwrapApiData(data));
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['reception', 'queue'] });
      qc.invalidateQueries({ queryKey: ['reception', 'queue', 'summary'] });
      qc.invalidateQueries({ queryKey: ['admin', 'appointments'] });
      qc.invalidateQueries({ queryKey: ['admin', 'appointments', 'detail'] });
      qc.invalidateQueries({ queryKey: ['admin', 'appointments', 'summary'] });
      toast.success('Đã đánh dấu bệnh nhân đã đến');
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Đánh dấu đến thất bại')),
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
