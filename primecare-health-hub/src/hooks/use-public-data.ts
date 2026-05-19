import { useEffect, useMemo, useState } from 'react';
import { Client } from '@stomp/stompjs';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import apiClient from '@/lib/api-client';
import type {
  ApiResponse,
  Appointment,
  AppointmentLookupVerifyResult,
  BookingEmailOtpRequestResult,
  BookingEmailOtpVerifyResult,
  BookingRequest,
  Branch,
  PublicAppointmentActionResult,
  PublicAppointmentResponseActionResult,
  PublicAppointmentResponseInfo,
  PublicAssistantRequestPayload,
  PublicAssistantResponse,
  PublicFaqItem,
  PublicLookupOtpResult,
  RescheduleOffer,
  ResultLookupVerifyResult,
} from '@/types/api';
import {
  normalizeAvailability,
  normalizeAppointment,
  normalizeBranch,
  normalizeBranchSpecialtyOption,
  normalizeDoctor,
  normalizeMedicalService,
  normalizePublicFaq,
  normalizeRescheduleOffer,
  normalizeSpecialty,
  unwrapApiData,
  unwrapPage,
  unwrapPageItems,
} from '@/lib/api-adapters';

export const queryKeys = {
  branches: (language: string) => ['public', 'branches', language] as const,
  branchesPage: (params: Record<string, string> | undefined, language: string) =>
    ['public', 'branches-page', params, language] as const,
  branch: (id: string, language: string) => ['public', 'branches', id, language] as const,
  specialties: (language: string) => ['public', 'specialties', language] as const,
  specialty: (id: string, language: string) => ['public', 'specialties', id, language] as const,
  branchSpecialties: (branchId: string, language: string) =>
    ['public', 'branches', branchId, 'specialties', language] as const,
  doctors: (params: Record<string, string> | undefined, language: string) =>
    ['public', 'doctors', params, language] as const,
  doctor: (id: string, language: string) => ['public', 'doctors', id, language] as const,
  medicalServices: (language: string) => ['public', 'medical-services', language] as const,
  availability: (params: Record<string, string> | undefined, language: string) =>
    ['public', 'availability', params, language] as const,
  faqs: (language: string) => ['public', 'faqs', language] as const,
  rescheduleOffer: (token: string) => ['public', 'reschedule-offer', token] as const,
  appointmentResponse: (token: string) => ['public', 'appointment-response', token] as const,
};

function normalizeAvailabilityParams(params?: {
  branchId?: string;
  specialtyId?: string;
  doctorId?: string;
  visitDate?: string;
  session?: string;
  onlyAvailable?: string;
}) {
  if (
    !params?.branchId ||
    !params?.specialtyId ||
    !params?.doctorId ||
    !params?.visitDate ||
    !params?.session
  ) {
    return undefined;
  }

  return {
    branchId: params.branchId,
    specialtyId: params.specialtyId,
    doctorId: params.doctorId,
    visitDate: params.visitDate,
    session: params.session,
    ...(params.onlyAvailable ? { onlyAvailable: params.onlyAvailable } : {}),
  };
}

function buildPublicWebSocketUrl() {
  if (typeof window === 'undefined') return null;

  const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || '/api';
  const normalizedApiBaseUrl = apiBaseUrl.replace(/\/$/, '');
  const absoluteHttpBase = normalizedApiBaseUrl.startsWith('http')
    ? normalizedApiBaseUrl
    : `${window.location.origin}${normalizedApiBaseUrl.startsWith('/') ? '' : '/'}${normalizedApiBaseUrl}`;
  const appBaseUrl = absoluteHttpBase.replace(/\/api$/, '');
  return `${appBaseUrl.replace(/^http/, 'ws')}/ws`;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function pickNumber(item: Record<string, unknown>, ...keys: string[]) {
  for (const key of keys) {
    const value = item[key];
    if (typeof value === 'number' && Number.isFinite(value)) return value;
    if (typeof value === 'string' && value.trim()) {
      const parsed = Number(value);
      if (Number.isFinite(parsed)) return parsed;
    }
  }

  return undefined;
}

function pickString(item: Record<string, unknown>, ...keys: string[]) {
  for (const key of keys) {
    const value = item[key];
    if (typeof value === 'string' && value.trim()) return value;
    if (typeof value === 'number') return String(value);
  }

  return undefined;
}

function pickBoolean(item: Record<string, unknown>, ...keys: string[]) {
  for (const key of keys) {
    const value = item[key];
    if (typeof value === 'boolean') return value;
    if (typeof value === 'string') {
      if (value.toLowerCase() === 'true') return true;
      if (value.toLowerCase() === 'false') return false;
    }
  }

  return undefined;
}

function normalizePublicAppointmentResponseInfo(value: unknown): PublicAppointmentResponseInfo {
  const record = isRecord(value) ? value : {};
  const appointment = isRecord(record.appointment) ? record.appointment : {};
  const source = Object.keys(appointment).length > 0 ? { ...record, ...appointment } : record;

  return {
    token: pickString(source, 'token'),
    status: pickString(record, 'status') ?? pickString(source, 'status'),
    appointmentId: pickString(source, 'appointmentId', 'id'),
    appointmentCode: pickString(source, 'appointmentCode', 'code'),
    patientFullName: pickString(source, 'patientFullName', 'patientName', 'fullName'),
    patientPhone: pickString(source, 'patientPhone', 'phone'),
    maskedPhone: pickString(source, 'maskedPhone', 'phoneMasked'),
    patientEmail: pickString(source, 'patientEmail', 'email'),
    maskedEmail: pickString(source, 'maskedEmail', 'emailMasked', 'maskedDestination'),
    doctorName: pickString(source, 'doctorName'),
    specialtyName: pickString(source, 'specialtyName'),
    branchName: pickString(source, 'branchName'),
    visitDate: pickString(source, 'visitDate'),
    slotStart: pickString(source, 'slotStart', 'startTime'),
    slotEnd: pickString(source, 'slotEnd', 'endTime'),
    expiresAt: pickString(source, 'expiresAt'),
    contactStatus: pickString(source, 'contactStatus', 'phoneContactStatus'),
    patientResponseStatus: pickString(source, 'patientResponseStatus', 'responseStatus'),
    canKeepAppointment: pickBoolean(source, 'canKeepAppointment', 'canKeep'),
    canRequestRecall: pickBoolean(source, 'canRequestRecall', 'canRequestCallBack'),
    canUpdatePhone: pickBoolean(source, 'canUpdatePhone'),
    canCancel: pickBoolean(source, 'canCancel'),
    message: pickString(source, 'message'),
  };
}

function normalizePublicAppointmentResponseAction(value: unknown): PublicAppointmentResponseActionResult {
  const record = isRecord(value) ? value : {};
  return {
    status: pickString(record, 'status'),
    message: pickString(record, 'message'),
    appointment: normalizePublicAppointmentResponseInfo(record.appointment ?? record),
  };
}

function normalizeBookingEmailOtpRequestResult(value: unknown): BookingEmailOtpRequestResult {
  if (!isRecord(value)) return {};

  return {
    verificationId: typeof value.verificationId === 'string' ? value.verificationId : undefined,
    channel: typeof value.channel === 'string' ? value.channel : undefined,
    email: typeof value.email === 'string' ? value.email : undefined,
    maskedDestination: typeof value.maskedDestination === 'string' ? value.maskedDestination : undefined,
    maskedEmail: typeof value.maskedEmail === 'string'
      ? value.maskedEmail
      : typeof value.maskedDestination === 'string'
        ? value.maskedDestination
        : undefined,
    expiresInSeconds: pickNumber(value, 'expiresInSeconds', 'ttlSeconds'),
    resendAvailableInSeconds: pickNumber(value, 'resendAvailableInSeconds', 'retryAfterSeconds', 'cooldownSeconds'),
    retryAfterSeconds: pickNumber(value, 'retryAfterSeconds'),
    cooldownSeconds: pickNumber(value, 'cooldownSeconds'),
  };
}

function normalizeBookingEmailOtpVerifyResult(value: unknown): BookingEmailOtpVerifyResult {
  const record = isRecord(value) ? value : {};
  const token =
    typeof record.bookingEmailVerificationToken === 'string'
      ? record.bookingEmailVerificationToken
      : typeof record.verificationToken === 'string'
        ? record.verificationToken
        : typeof record.token === 'string'
          ? record.token
          : '';

  return {
    bookingEmailVerificationToken: token,
    email: typeof record.email === 'string' ? record.email : undefined,
    normalizedEmail: typeof record.normalizedEmail === 'string' ? record.normalizedEmail : undefined,
    expiresAt: typeof record.expiresAt === 'string' ? record.expiresAt : undefined,
    expiresInSeconds: pickNumber(record, 'expiresInSeconds', 'ttlSeconds'),
  };
}

export function useCreatePublicAppointment() {
  return useMutation({
    mutationFn: async (payload: BookingRequest) => {
      const { data } = await apiClient.post<ApiResponse<Appointment>>('/public/appointments', payload);
      return normalizeAppointment(unwrapApiData<Appointment>(data));
    },
  });
}

export function useRequestBookingEmailOtp() {
  return useMutation({
    mutationFn: async (payload: { email: string }) => {
      const { data } = await apiClient.post<ApiResponse<unknown>>(
        '/public/booking-email-otp/request',
        payload,
      );
      return normalizeBookingEmailOtpRequestResult(unwrapApiData(data));
    },
  });
}

export function useVerifyBookingEmailOtp() {
  return useMutation({
    mutationFn: async ({ verificationId, otp }: { verificationId: string; otp: string }) => {
      const { data } = await apiClient.post<ApiResponse<unknown>>(
        '/public/booking-email-otp/verify',
        { verificationId, otp },
      );
      return normalizeBookingEmailOtpVerifyResult(unwrapApiData(data));
    },
  });
}

export function useBranches() {
  const { i18n } = useTranslation();
  const language = i18n.language?.startsWith('en') ? 'en' : 'vi';

  return useQuery({
    queryKey: queryKeys.branches(language),
    queryFn: async () => {
      const { data } = await apiClient.get('/public/branches', { params: { page: 0, size: 1000 } });
      return unwrapPageItems<unknown>(data).map(normalizeBranch);
    },
  });
}

export function useBranchesPage(params?: Record<string, string>) {
  const { i18n } = useTranslation();
  const language = i18n.language?.startsWith('en') ? 'en' : 'vi';

  return useQuery({
    queryKey: queryKeys.branchesPage(params, language),
    queryFn: async () => {
      const { data } = await apiClient.get('/public/branches', { params });
      const page = unwrapPage<unknown>(data);
      return {
        ...page,
        items: page.items.map(normalizeBranch) as Branch[],
      };
    },
  });
}

export function useBranch(id: string) {
  const { i18n } = useTranslation();
  const language = i18n.language?.startsWith('en') ? 'en' : 'vi';

  return useQuery({
    queryKey: queryKeys.branch(id, language),
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<unknown>>(`/public/branches/${id}`);
      return normalizeBranch(unwrapApiData(data));
    },
    enabled: !!id,
  });
}

export function useSpecialties() {
  const { i18n } = useTranslation();
  const language = i18n.language?.startsWith('en') ? 'en' : 'vi';

  return useQuery({
    queryKey: queryKeys.specialties(language),
    queryFn: async () => {
      const { data } = await apiClient.get('/public/specialties', { params: { page: 0, size: 100 } });
      return unwrapPageItems<unknown>(data).map(normalizeSpecialty);
    },
  });
}

export function useSpecialty(id: string) {
  const { i18n } = useTranslation();
  const language = i18n.language?.startsWith('en') ? 'en' : 'vi';

  return useQuery({
    queryKey: queryKeys.specialty(id, language),
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<unknown>>(`/public/specialties/${id}`);
      return normalizeSpecialty(unwrapApiData(data));
    },
    enabled: !!id,
  });
}

export function useBranchSpecialties(branchId: string) {
  const { i18n } = useTranslation();
  const language = i18n.language?.startsWith('en') ? 'en' : 'vi';

  return useQuery({
    queryKey: queryKeys.branchSpecialties(branchId, language),
    queryFn: async () => {
      const { data } = await apiClient.get(`/public/branches/${branchId}/specialties`);
      return unwrapPageItems<unknown>(data).map(normalizeBranchSpecialtyOption);
    },
    enabled: !!branchId,
  });
}

export function useDoctors(params?: Record<string, string>) {
  const { i18n } = useTranslation();
  const language = i18n.language?.startsWith('en') ? 'en' : 'vi';

  return useQuery({
    queryKey: queryKeys.doctors(params, language),
    queryFn: async () => {
      const { data } = await apiClient.get('/public/doctors', { params });
      const page = unwrapPage<unknown>(data);
      return {
        ...page,
        items: page.items.map(normalizeDoctor),
      };
    },
  });
}

export function useDoctor(id: string) {
  const { i18n } = useTranslation();
  const language = i18n.language?.startsWith('en') ? 'en' : 'vi';

  return useQuery({
    queryKey: queryKeys.doctor(id, language),
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<unknown>>(`/public/doctors/${id}`);
      return normalizeDoctor(unwrapApiData(data));
    },
    enabled: !!id,
  });
}

export function useMedicalServices() {
  const { i18n } = useTranslation();
  const language = i18n.language?.startsWith('en') ? 'en' : 'vi';

  return useQuery({
    queryKey: queryKeys.medicalServices(language),
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<unknown[]>>('/public/medical-services');
      return unwrapApiData<unknown[]>(data).map(normalizeMedicalService);
    },
  });
}


export function useMedicalService(id: string) {
  const { i18n } = useTranslation();
  const language = i18n.language?.startsWith('en') ? 'en' : 'vi';

  return useQuery({
    queryKey: [...queryKeys.medicalServices(language), 'detail', id],
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<unknown>>(`/public/medical-services/${id}`);
      return normalizeMedicalService(unwrapApiData(data));
    },
    enabled: !!id,
  });
}

export function useAvailability(params?: Record<string, string>) {
  const { i18n } = useTranslation();
  const language = i18n.language?.startsWith('en') ? 'en' : 'vi';
  const branchId = params?.branchId;
  const specialtyId = params?.specialtyId;
  const doctorId = params?.doctorId;
  const visitDate = params?.visitDate;
  const session = params?.session;
  const onlyAvailable = params?.onlyAvailable;
  const normalizedParams = useMemo(
    () => normalizeAvailabilityParams({ branchId, specialtyId, doctorId, visitDate, session, onlyAvailable }),
    [branchId, doctorId, onlyAvailable, session, specialtyId, visitDate],
  );

  return useQuery({
    queryKey: queryKeys.availability(normalizedParams, language),
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<unknown>>('/public/availability', {
        params: normalizedParams,
      });
      return normalizeAvailability(unwrapApiData(data));
    },
    enabled: Boolean(normalizedParams),
    staleTime: 60_000,
    refetchOnWindowFocus: false,
    refetchInterval: false,
    placeholderData: (previousData) => previousData,
  });
}

export function usePublicAvailabilityRealtime(params?: Record<string, string>) {
  const qc = useQueryClient();
  const branchId = params?.branchId;
  const specialtyId = params?.specialtyId;
  const doctorId = params?.doctorId;
  const visitDate = params?.visitDate;
  const session = params?.session;
  const onlyAvailable = params?.onlyAvailable;
  const normalizedParams = useMemo(
    () => normalizeAvailabilityParams({ branchId, specialtyId, doctorId, visitDate, session, onlyAvailable }),
    [branchId, doctorId, onlyAvailable, session, specialtyId, visitDate],
  );
  const query = useAvailability(normalizedParams);
  const [isLiveConnected, setIsLiveConnected] = useState(false);
  const [lastSyncAt, setLastSyncAt] = useState<string | null>(null);
  const destination = useMemo(() => {
    if (!normalizedParams?.doctorId || !normalizedParams?.visitDate || !normalizedParams?.session) return null;
    return `/topic/public/availability/${normalizedParams.doctorId}/${normalizedParams.visitDate}/${normalizedParams.session}`;
  }, [normalizedParams?.doctorId, normalizedParams?.visitDate, normalizedParams?.session]);

  useEffect(() => {
    const wsUrl = buildPublicWebSocketUrl();
    if (!destination || !wsUrl) {
      setIsLiveConnected(false);
      return;
    }

    const client = new Client({
      brokerURL: wsUrl,
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: () => {},
    });

    client.onConnect = () => {
      setIsLiveConnected(true);
      client.subscribe(destination, () => {
        setLastSyncAt(new Date().toISOString());
        qc.invalidateQueries({ queryKey: queryKeys.availability(normalizedParams, 'vi') });
        qc.invalidateQueries({ queryKey: queryKeys.availability(normalizedParams, 'en') });
      });
    };
    client.onStompError = () => setIsLiveConnected(false);
    client.onWebSocketClose = () => setIsLiveConnected(false);

    client.activate();
    return () => {
      setIsLiveConnected(false);
      client.deactivate();
    };
  }, [destination, normalizedParams, qc]);

  return {
    ...query,
    slots: query.data ?? [],
    isLiveConnected,
    lastSyncAt,
  };
}

export function usePublicFaqs() {
  const { i18n } = useTranslation();
  const language = i18n.language?.startsWith('en') ? 'en' : 'vi';

  return useQuery({
    queryKey: queryKeys.faqs(language),
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<unknown[]>>('/public/faqs');
      return unwrapApiData<unknown[]>(data).map(normalizePublicFaq) as PublicFaqItem[];
    },
  });
}

export function useRescheduleOffer(token: string | undefined) {
  const normalizedToken = token?.trim() ?? '';

  return useQuery({
    queryKey: queryKeys.rescheduleOffer(normalizedToken),
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<unknown>>(
        `/public/reschedule/${encodeURIComponent(normalizedToken)}`,
      );
      return normalizeRescheduleOffer(unwrapApiData(data));
    },
    enabled: Boolean(normalizedToken),
    retry: false,
  });
}

function useRescheduleOfferAction(action: 'accept' | 'request-contact' | 'cancel') {
  return useMutation({
    mutationFn: async (token: string) => {
      const { data } = await apiClient.post<ApiResponse<unknown>>(
        `/public/reschedule/${encodeURIComponent(token)}/${action}`,
      );
      return normalizeRescheduleOffer(unwrapApiData(data)) as RescheduleOffer;
    },
  });
}

export function useAcceptRescheduleOffer() {
  return useRescheduleOfferAction('accept');
}

export function useRequestRescheduleContact() {
  return useRescheduleOfferAction('request-contact');
}

export function useCancelRescheduleOffer() {
  return useRescheduleOfferAction('cancel');
}

export function usePublicAppointmentResponse(token: string | undefined) {
  const normalizedToken = token?.trim() ?? '';

  return useQuery({
    queryKey: queryKeys.appointmentResponse(normalizedToken),
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<unknown>>(
        `/public/appointment-response/${encodeURIComponent(normalizedToken)}`,
      );
      return normalizePublicAppointmentResponseInfo(unwrapApiData(data));
    },
    enabled: Boolean(normalizedToken),
    retry: false,
  });
}

function usePublicAppointmentResponseAction(
  action: 'keep' | 'request-recall' | 'update-phone' | 'cancel',
) {
  return useMutation({
    mutationFn: async ({ token, body }: { token: string; body?: Record<string, unknown> }) => {
      const { data } = await apiClient.post<ApiResponse<unknown>>(
        `/public/appointment-response/${encodeURIComponent(token)}/${action}`,
        body ?? {},
      );
      return normalizePublicAppointmentResponseAction(unwrapApiData(data));
    },
  });
}

export function useKeepPublicAppointmentResponse() {
  return usePublicAppointmentResponseAction('keep');
}

export function useRequestPublicAppointmentRecall() {
  return usePublicAppointmentResponseAction('request-recall');
}

export function useUpdatePublicAppointmentPhone() {
  return usePublicAppointmentResponseAction('update-phone');
}

export function useCancelPublicAppointmentResponse() {
  return usePublicAppointmentResponseAction('cancel');
}

export function useRequestAppointmentLookupOtp() {
  return useMutation({
    mutationFn: async (code: string) => {
      const { data } = await apiClient.post<ApiResponse<PublicLookupOtpResult>>('/public/lookup/appointments/request-otp', { code, channel: 'EMAIL' });
      return unwrapApiData(data);
    },
  });
}

export function useCancelAppointmentLookup() {
  return useMutation({
    mutationFn: async (payload: { code: string; token: string; reason?: string }) => {
      const { data } = await apiClient.post<ApiResponse<PublicAppointmentActionResult>>(
        `/public/lookup/appointments/${encodeURIComponent(payload.code)}/cancel`,
        { reason: payload.reason ?? '' },
        { params: { token: payload.token } },
      );
      return unwrapApiData(data);
    },
  });
}

export function useVerifyAppointmentLookupOtp() {
  return useMutation({
    mutationFn: async (payload: { code: string; otp: string }) => {
      const { data } = await apiClient.post<ApiResponse<AppointmentLookupVerifyResult>>('/public/lookup/appointments/verify-otp', payload);
      return unwrapApiData(data);
    },
  });
}

export function useRequestResultLookupOtp() {
  return useMutation({
    mutationFn: async (code: string) => {
      const { data } = await apiClient.post<ApiResponse<PublicLookupOtpResult>>('/public/lookup/results/request-otp', { code });
      return unwrapApiData(data);
    },
  });
}

export function useVerifyResultLookupOtp() {
  return useMutation({
    mutationFn: async (payload: { code: string; otp: string }) => {
      const { data } = await apiClient.post<ApiResponse<ResultLookupVerifyResult>>('/public/lookup/results/verify-otp', payload);
      return unwrapApiData(data);
    },
  });
}

export function useAskPublicAssistant() {
  return useMutation({
    mutationFn: async (payload: PublicAssistantRequestPayload) => {
      const { data } = await apiClient.post<ApiResponse<PublicAssistantResponse>>('/public/assistant/ask', payload);
      return unwrapApiData(data);
    },
  });
}
