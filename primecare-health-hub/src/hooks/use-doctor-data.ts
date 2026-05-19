import { useEffect, useState } from 'react';
import { Client, type StompSubscription } from '@stomp/stompjs';
import { AxiosError } from 'axios';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/lib/api-client';
import {
  normalizeAppointment,
  normalizeDoctorAppointmentSummary,
  normalizeEncounter,
  normalizeMedication,
  normalizePrescription,
  normalizeDoctorSchedule,
  normalizeLeaveRequest,
  normalizeServiceOrder,
  unwrapApiData,
  unwrapPage,
} from '@/lib/api-adapters';
import { useAuthStore } from '@/stores/auth-store';
import type {
  Appointment,
  DoctorAppointmentSummary,
  Encounter,
  EncounterDiagnosisResponse,
  EncounterAiSummary,
  EncounterTimelineItem,
  LeaveRequest,
  Medication,
  Prescription,
  ServiceOrder,
  Icd10CodeResponse,
  PatientAllergyResponse,
} from '@/types/api';
import { toast } from 'sonner';
import { getApiErrorMessage } from '@/lib/error-utils';

export const doctorQueryKeys = {
  appointments: (params?: Record<string, string>) => ['doctor', 'appointments', params] as const,
  waitingAppointments: (params?: Record<string, string>) =>
    ['doctor', 'appointments', 'waiting', params] as const,
  appointmentSummary: (params?: Record<string, string>) =>
    ['doctor', 'appointments', 'summary', params] as const,
  encounter: (id: string) => ['doctor', 'encounter', id] as const,
  encounterAiSummary: (id: string) => ['doctor', 'encounter', id, 'ai-summary'] as const,
  encounterTimeline: (id: string) => ['doctor', 'encounter', id, 'timeline'] as const,
  serviceOrders: (encounterId: string) => ['doctor', 'encounter', encounterId, 'service-orders'] as const,
  prescriptions: (encounterId: string) => ['doctor', 'encounter', encounterId, 'prescriptions'] as const,
  medications: (params?: Record<string, string>) => ['doctor', 'medications', params] as const,
  schedules: (params?: Record<string, string>) => ['doctor', 'schedules', params] as const,
  leaveRequests: (params?: Record<string, string>) => ['doctor', 'leave-requests', params] as const,
};

const DIAGNOSIS_TYPES: EncounterDiagnosisResponse['diagnosisType'][] = [
  'PRELIMINARY',
  'FINAL',
  'SECONDARY',
];

type SaveEncounterDiagnosesInput =
  | EncounterDiagnosisResponse[]
  | {
      current: EncounterDiagnosisResponse[];
      previous?: EncounterDiagnosisResponse[];
    };

function invalidateDoctorWorklist(qc: ReturnType<typeof useQueryClient>) {
  qc.invalidateQueries({ queryKey: ['doctor', 'appointments'] });
  qc.invalidateQueries({ queryKey: ['doctor', 'appointments', 'waiting'] });
  qc.invalidateQueries({ queryKey: ['doctor', 'appointments', 'summary'] });
}

function invalidateEncounterWorkspace(qc: ReturnType<typeof useQueryClient>, encounterId: string) {
  qc.invalidateQueries({ queryKey: doctorQueryKeys.encounter(encounterId) });
  qc.invalidateQueries({ queryKey: doctorQueryKeys.encounterTimeline(encounterId) });
  qc.invalidateQueries({ queryKey: doctorQueryKeys.encounterAiSummary(encounterId) });
  invalidateDoctorWorklist(qc);
}

function buildDoctorAppointmentSummary(items: Appointment[], total?: number): DoctorAppointmentSummary {
  const summary: DoctorAppointmentSummary = {
    total: total ?? items.length,
    waitingExam: 0,
    inCare: 0,
    waitingExternal: 0,
    needsReturn: 0,
    done: 0,
  };

  items.forEach((item) => {
    if (item.status === 'CHECKED_IN' && !item.activeEncounterId) {
      summary.waitingExam += 1;
      return;
    }

    switch (item.activeEncounterStatus) {
      case 'IN_PROGRESS':
      case 'REOPENED':
        summary.inCare += 1;
        break;
      case 'WAITING_PAYMENT':
      case 'WAITING_RESULTS':
        summary.waitingExternal += 1;
        break;
      case 'READY_FOR_CONCLUSION':
        summary.needsReturn += 1;
        break;
      case 'COMPLETED':
        summary.done += 1;
        break;
      default:
        break;
    }
  });

  return summary;
}

function mapDoctorAppointmentSummaryParams(params?: Record<string, string>) {
  if (!params) return undefined;

  const { from, to, ...summaryParams } = params;
  const hasSummaryDate =
    Boolean(summaryParams.visitDate) ||
    Boolean(summaryParams.fromDate && summaryParams.toDate);

  if (!hasSummaryDate) {
    if (from && to && from === to) {
      summaryParams.visitDate = from;
    } else {
      if (from) summaryParams.fromDate = from;
      if (to) summaryParams.toDate = to;
    }
  }

  return summaryParams;
}

function hasDoctorAppointmentSummaryDate(params?: Record<string, string>) {
  return Boolean(
    params?.visitDate ||
      (params?.fromDate && params?.toDate) ||
      (params?.from && params?.to),
  );
}

function resolveBrokerUrl() {
  const baseUrl = import.meta.env.VITE_API_BASE_URL?.replace(/\/$/, '') || '/api';
  const apiBase = baseUrl.endsWith('/api') ? baseUrl.slice(0, -4) : baseUrl;

  if (apiBase.startsWith('http://')) {
    return `${apiBase.replace(/^http/, 'ws')}/ws`;
  }
  if (apiBase.startsWith('https://')) {
    return `${apiBase.replace(/^https/, 'wss')}/ws`;
  }

  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const host = window.location.host;
  const normalizedPath = apiBase.startsWith('/') ? apiBase : `/${apiBase}`;
  return `${protocol}//${host}${normalizedPath}/ws`;
}

export function useDoctorAppointments(params?: Record<string, string>) {
  return useQuery({
    queryKey: doctorQueryKeys.appointments(params),
    queryFn: async () => {
      const { data } = await apiClient.get('/doctor/appointments', { params });
      const page = unwrapPage<unknown>(data);
      return {
        ...page,
        items: page.items.map(normalizeAppointment),
      };
    },
    enabled: !!params?.from && !!params?.to,
    staleTime: 30_000,
    refetchOnWindowFocus: false,
    placeholderData: (previousData) => previousData,
  });
}

export function useDoctorWaitingAppointments(params?: Record<string, string>, enabled = true) {
  return useQuery({
    queryKey: doctorQueryKeys.waitingAppointments(params),
    queryFn: async () => {
      try {
        const { data } = await apiClient.get('/doctor/appointments/waiting', { params });
        const page = unwrapPage<unknown>(data);
        return {
          ...page,
          items: page.items.map(normalizeAppointment),
        };
      } catch (error) {
        const status = (error as AxiosError).response?.status;
        if (status !== 404 && status !== 405 && status !== 501) {
          throw error;
        }

        const visitDate = params?.visitDate;
        const { data } = await apiClient.get('/doctor/appointments', {
          params: {
            from: visitDate,
            to: visitDate,
            page: params?.page ?? '0',
            size: params?.size ?? '50',
          },
        });
        const page = unwrapPage<unknown>(data);
        const items = page.items
          .map(normalizeAppointment)
          .filter((appointment) => appointment.status === 'CHECKED_IN' && !appointment.activeEncounterId);
        return {
          ...page,
          items,
        };
      }
    },
    enabled: enabled && !!params?.visitDate,
    staleTime: 30_000,
    refetchOnWindowFocus: false,
    placeholderData: (previousData) => previousData,
  });
}

export function useDoctorAppointmentSummary(params?: Record<string, string>) {
  return useQuery({
    queryKey: doctorQueryKeys.appointmentSummary(params),
    queryFn: async () => {
      try {
        const { data } = await apiClient.get('/doctor/appointments/summary', {
          params: mapDoctorAppointmentSummaryParams(params),
        });
        return normalizeDoctorAppointmentSummary(unwrapApiData(data));
      } catch (error) {
        const status = (error as AxiosError).response?.status;
        if (status !== 404 && status !== 405 && status !== 501) {
          throw error;
        }

        const { data } = await apiClient.get('/doctor/appointments', {
          params: { ...params, page: params?.page ?? '0', size: params?.size ?? '200' },
        });
        const page = unwrapPage<unknown>(data);
        const items = page.items.map(normalizeAppointment);
        return buildDoctorAppointmentSummary(items, page.meta.totalItems);
      }
    },
    enabled: hasDoctorAppointmentSummaryDate(params),
    staleTime: 30_000,
    refetchOnWindowFocus: false,
    placeholderData: (previousData) => previousData,
  });
}

export function useCreateEncounterFromAppointment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (appointmentId: string) => {
      const { data } = await apiClient.post(`/doctor/encounters/from-appointment/${appointmentId}`);
      return normalizeEncounter(unwrapApiData(data)) as Encounter;
    },
    onSuccess: () => {
      invalidateDoctorWorklist(qc);
      qc.invalidateQueries({ queryKey: ['doctor', 'encounter'] });
      toast.success('Bắt đầu lần khám thành công');
    },
    onError: (error) => {
      const axiosError = error as AxiosError<{ message?: string; code?: string }>;
      const status = axiosError.response?.status;
      const message = getApiErrorMessage(error, 'Không thể bắt đầu lần khám');

      if (status === 409) {
        toast.info(message || 'Lần khám đã được tạo trước đó. Đang mở hồ sơ hiện có nếu có.');
        return;
      }

      toast.error(message || 'Không thể bắt đầu lần khám');
    },
  });
}

export function useDoctorEncounter(id: string) {
  return useQuery({
    queryKey: doctorQueryKeys.encounter(id),
    queryFn: async () => {
      const { data } = await apiClient.get(`/doctor/encounters/${id}`);
      return normalizeEncounter(unwrapApiData(data));
    },
    enabled: !!id,
  });
}



export function useDoctorEncounterTimeline(id: string) {
  return useQuery({
    queryKey: doctorQueryKeys.encounterTimeline(id),
    queryFn: async () => {
      const { data } = await apiClient.get(`/doctor/encounters/${id}/timeline`);
      return unwrapApiData<EncounterTimelineItem[]>(data);
    },
    enabled: !!id,
  });
}

export function useDoctorEncounterAiSummary(id: string, enabled = false) {
  return useQuery({
    queryKey: doctorQueryKeys.encounterAiSummary(id),
    queryFn: async () => {
      const { data } = await apiClient.get(`/doctor/encounters/${id}/ai-summary`);
      return unwrapApiData<EncounterAiSummary>(data);
    },
    enabled: enabled && !!id,
    staleTime: 60_000,
  });
}

export function useDoctorEncounterRealtime(encounterId: string) {
  const qc = useQueryClient();
  const [isConnected, setIsConnected] = useState(false);

  useEffect(() => {
    const token = useAuthStore.getState().accessToken;
    if (!token || !encounterId) {
      setIsConnected(false);
      return;
    }

    let everConnected = false;
    let encounterSubscription: StompSubscription | undefined;

    const client = new Client({
      brokerURL: resolveBrokerUrl(),
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: () => {},
      onConnect: () => {
        everConnected = true;
        setIsConnected(true);
        encounterSubscription?.unsubscribe();
        encounterSubscription = client.subscribe(`/topic/encounter/${encounterId}`, () => {
          qc.invalidateQueries({ queryKey: doctorQueryKeys.encounter(encounterId) });
          qc.invalidateQueries({ queryKey: doctorQueryKeys.serviceOrders(encounterId) });
          qc.invalidateQueries({ queryKey: doctorQueryKeys.prescriptions(encounterId) });
          qc.invalidateQueries({ queryKey: doctorQueryKeys.encounterTimeline(encounterId) });
          invalidateDoctorWorklist(qc);
        });
      },
      onDisconnect: () => setIsConnected(false),
      onWebSocketClose: () => {
        setIsConnected(false);
        if (!everConnected) {
          void client.deactivate();
        }
      },
      onStompError: () => {
        setIsConnected(false);
        if (!everConnected) {
          void client.deactivate();
        }
      },
    });

    client.activate();

    return () => {
      setIsConnected(false);
      encounterSubscription?.unsubscribe();
      void client.deactivate();
    };
  }, [encounterId, qc]);

  return { isConnected };
}

export function useUpdateEncounter(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: Record<string, unknown>) => {
      const { data } = await apiClient.patch(`/doctor/encounters/${id}`, body);
      return normalizeEncounter(unwrapApiData(data));
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: doctorQueryKeys.encounter(id) });
      qc.invalidateQueries({ queryKey: doctorQueryKeys.encounterAiSummary(id) });
      invalidateDoctorWorklist(qc);
      toast.success('Lưu hồ sơ khám thành công');
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Lưu hồ sơ khám thất bại')),
  });
}

export function useSaveEncounterDiagnoses(encounterId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (input: SaveEncounterDiagnosesInput) => {
      const current = Array.isArray(input) ? input : input.current;
      const previous = Array.isArray(input) ? [] : input.previous ?? [];
      const normalized = current.map((diagnosis, index) => ({
        ...diagnosis,
        diagnosisType:
          String(diagnosis.diagnosisType) === 'COMORBIDITY'
            ? 'SECONDARY'
            : diagnosis.diagnosisType,
        displayOrder: diagnosis.displayOrder ?? index + 1,
      }));

      const responses = [];
      const typesToSave = DIAGNOSIS_TYPES.filter((diagnosisType) => {
        const hasCurrentItems = normalized.some(
          (item) => item.diagnosisType === diagnosisType && item.icd10CodeId,
        );
        const hadPreviousItems = previous.some(
          (item) => item.diagnosisType === diagnosisType && item.icd10CodeId,
        );
        return hasCurrentItems || hadPreviousItems;
      });

      for (const diagnosisType of typesToSave) {
        const items = normalized
          .filter((item) => item.diagnosisType === diagnosisType && item.icd10CodeId)
          .map((item, index) => ({
            icd10CodeId: Number(item.icd10CodeId),
            note: item.note || undefined,
            displayOrder: item.displayOrder ?? index + 1,
          }));

        // TODO: If backend removes support for empty items as "clear this type",
        // replace this branch with the dedicated delete endpoint.
        const { data } = await apiClient.post(`/doctor/encounters/${encounterId}/diagnoses`, {
          diagnosisType,
          items,
        });
        responses.push(unwrapApiData(data));
      }

      return responses;
    },
    onSuccess: () => {
      invalidateEncounterWorkspace(qc, encounterId);
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Lưu chẩn đoán ICD-10 thất bại')),
  });
}

export function useCompleteEncounter(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: Record<string, unknown>) => {
      const { data } = await apiClient.post(`/doctor/encounters/${id}/complete`, body);
      return normalizeEncounter(unwrapApiData(data));
    },
    onSuccess: () => {
      invalidateEncounterWorkspace(qc, id);
      toast.success('Hoàn tất lần khám thành công');
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, 'Hoàn tất lần khám thất bại'));
    },
  });
}

export function useReopenEncounter(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (reason: string) => {
      const { data } = await apiClient.post(`/doctor/encounters/${id}/reopen`, null, {
        params: { reason },
      });
      return normalizeEncounter(unwrapApiData(data));
    },
    onSuccess: () => {
      invalidateEncounterWorkspace(qc, id);
      toast.success('Đã mở lại hồ sơ khám');
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, 'Mở lại hồ sơ thất bại'));
    },
  });
}

export function useValidateVitals(id: string) {
  return useMutation({
    mutationFn: async (body: Record<string, unknown>) => {
      const { data } = await apiClient.post(`/doctor/encounters/${id}/validate-vitals`, body);
      return data;
    },
  });
}

export function useDoctorServiceOrders(encounterId: string) {
  return useQuery({
    queryKey: doctorQueryKeys.serviceOrders(encounterId),
    queryFn: async () => {
      const { data } = await apiClient.get(`/doctor/encounters/${encounterId}/service-orders`);
      const raw = unwrapApiData<unknown[]>(data);
      return Array.isArray(raw) ? raw.map(normalizeServiceOrder) : [];
    },
    enabled: !!encounterId,
  });
}

export function useCreateDoctorServiceOrder(encounterId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: Record<string, unknown>) => {
      const { data } = await apiClient.post(`/doctor/encounters/${encounterId}/service-orders`, body);
      return normalizeServiceOrder(unwrapApiData(data)) as ServiceOrder;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: doctorQueryKeys.serviceOrders(encounterId) });
      invalidateEncounterWorkspace(qc, encounterId);
      toast.success('Tạo chỉ định dịch vụ thành công');
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, 'Tạo chỉ định dịch vụ thất bại'));
    },
  });
}

export function useDoctorEncounterPrescriptions(encounterId: string) {
  return useQuery({
    queryKey: doctorQueryKeys.prescriptions(encounterId),
    queryFn: async () => {
      const { data } = await apiClient.get(`/doctor/encounters/${encounterId}/prescriptions`);
      const page = unwrapPage<unknown>(data);
      return {
        ...page,
        items: page.items.map(normalizePrescription),
      };
    },
    enabled: !!encounterId,
  });
}

export function useDoctorMedications(params?: Record<string, string>) {
  return useQuery({
    queryKey: doctorQueryKeys.medications(params),
    queryFn: async () => {
      const { data } = await apiClient.get('/doctor/medications', { params });
      const page = unwrapPage<unknown>(data);
      return {
        ...page,
        items: page.items.map(normalizeMedication) as Medication[],
      };
    },
  });
}

export function useCreatePrescription(encounterId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: Record<string, unknown>) => {
      const { data } = await apiClient.post(`/doctor/encounters/${encounterId}/prescriptions`, body);
      return normalizePrescription(unwrapApiData(data)) as Prescription;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: doctorQueryKeys.prescriptions(encounterId) });
      invalidateEncounterWorkspace(qc, encounterId);
      toast.success('Tạo đơn thuốc thành công');
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Tạo đơn thuốc thất bại')),
  });
}

export function useUpdatePrescription(encounterId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      prescriptionId,
      body,
    }: {
      prescriptionId: string;
      body: Record<string, unknown>;
    }) => {
      const { data } = await apiClient.put(
        `/doctor/encounters/${encounterId}/prescriptions/${prescriptionId}`,
        body,
      );
      return normalizePrescription(unwrapApiData(data)) as Prescription;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: doctorQueryKeys.prescriptions(encounterId) });
      invalidateEncounterWorkspace(qc, encounterId);
      toast.success('Cập nhật đơn thuốc thành công');
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Cập nhật đơn thuốc thất bại')),
  });
}

export function useCancelPrescription(encounterId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (prescriptionId: string) => {
      const { data } = await apiClient.post(
        `/doctor/encounters/${encounterId}/prescriptions/${prescriptionId}/cancel`,
      );
      return normalizePrescription(unwrapApiData(data)) as Prescription;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: doctorQueryKeys.prescriptions(encounterId) });
      invalidateEncounterWorkspace(qc, encounterId);
      toast.success('Hủy đơn thuốc thành công');
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Hủy đơn thuốc thất bại')),
  });
}

export function useDoctorSchedules(params?: Record<string, string>) {
  return useQuery({
    queryKey: doctorQueryKeys.schedules(params),
    queryFn: async () => {
      const { data } = await apiClient.get('/doctor/schedules', { params });
      const page = unwrapPage<unknown>(data);
      return {
        ...page,
        items: page.items.map(normalizeDoctorSchedule),
      };
    },
    enabled: !!params?.from && !!params?.to,
  });
}

export function useDoctorLeaveRequests(params?: Record<string, string>) {
  return useQuery({
    queryKey: doctorQueryKeys.leaveRequests(params),
    queryFn: async () => {
      const { data } = await apiClient.get('/doctor/leave-requests', { params });
      const page = unwrapPage<unknown>(data);
      return {
        ...page,
        items: page.items.map(normalizeLeaveRequest) as LeaveRequest[],
      };
    },
  });
}

export function useCreateDoctorLeaveRequest() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: Record<string, unknown>) => {
      const { data } = await apiClient.post('/doctor/leave-requests', body);
      return normalizeLeaveRequest(unwrapApiData(data)) as LeaveRequest;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['doctor', 'leave-requests'] });
      toast.success('Tạo đơn nghỉ thành công');
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Tạo đơn nghỉ thất bại')),
  });
}

export function useCancelDoctorLeaveRequest() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (leaveRequestId: string) => {
      const { data } = await apiClient.patch(`/doctor/leave-requests/${leaveRequestId}/cancel`);
      return normalizeLeaveRequest(unwrapApiData(data)) as LeaveRequest;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['doctor', 'leave-requests'] });
      toast.success('Hủy đơn nghỉ thành công');
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Hủy đơn nghỉ thất bại')),
  });
}

export function useIcd10Codes(params?: Record<string, string>) {
  return useQuery({
    queryKey: ['doctor', 'icd10-codes', params],
    queryFn: async () => {
      const { data } = await apiClient.get('/doctor/icd10-codes', { params });
      return unwrapPage<Icd10CodeResponse>(data);
    },
    enabled: !!params?.q,
    staleTime: 60_000,
    refetchOnWindowFocus: false,
    placeholderData: (previousData) => previousData,
  });
}

export function useDoctorPatientAllergies(patientId?: string) {
  return useQuery({
    queryKey: ['doctor', 'patients', patientId, 'allergies'],
    queryFn: async () => {
      const { data } = await apiClient.get(`/doctor/patients/${patientId}/allergies`);
      return unwrapApiData<PatientAllergyResponse[]>(data);
    },
    enabled: !!patientId,
  });
}

export function useAddDoctorPatientAllergy(patientId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: Record<string, unknown>) => {
      const { data } = await apiClient.post(`/doctor/patients/${patientId}/allergies`, body);
      return unwrapApiData<PatientAllergyResponse>(data);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['doctor', 'patients', patientId, 'allergies'] });
      toast.success('Thêm dị ứng thành công');
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Thêm dị ứng thất bại')),
  });
}

export function useDeleteDoctorPatientAllergy(patientId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (allergyId: number) => {
      await apiClient.delete(`/doctor/patients/allergies/${allergyId}`);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['doctor', 'patients', patientId, 'allergies'] });
      toast.success('Xóa dị ứng thành công');
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Xóa dị ứng thất bại')),
  });
}

export function usePatientTimeline(patientId?: string, type?: string) {
  return useQuery({
    queryKey: ['doctor', 'patients', patientId, 'timeline', type],
    queryFn: async () => {
      const { data } = await apiClient.get(`/doctor/patients/${patientId}/timeline`, {
        params: { type: type === '__all__' ? undefined : type },
      });
      return unwrapApiData<unknown[]>(data);
    },
    enabled: !!patientId,
  });
}
