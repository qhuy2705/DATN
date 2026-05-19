import type { PublicAssistantBookingDraft } from '@/types/api';

export const AI_BOOKING_DRAFT_STORAGE_KEY = 'primecare_ai_booking_draft_v1';

export interface AiBookingRouteState {
  aiBookingDraft?: PublicAssistantBookingDraft;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function toText(value: unknown): string | undefined {
  if (typeof value === 'string' && value.trim()) return value.trim();
  if (typeof value === 'number' && Number.isFinite(value)) return String(value);
  return undefined;
}

type BookingDraftNavigationAction = {
  payload?: {
    bookingDraft?: unknown;
  };
};

export function normalizeAiBookingDraft(value: unknown): PublicAssistantBookingDraft | null {
  if (!isRecord(value)) return null;

  const slotId = toText(value.slotId);
  const doctorId = toText(value.doctorId);
  const doctorName = toText(value.doctorName);
  const specialtyId = toText(value.specialtyId);
  const specialtyName = toText(value.specialtyName);
  const facilityId = toText(value.facilityId) ?? toText(value.branchId);
  const facilityName = toText(value.facilityName) ?? toText(value.branchName);
  const appointmentDate = toText(value.appointmentDate) ?? toText(value.visitDate);
  const startTime = toText(value.startTime) ?? toText(value.slotStart);
  const endTime = toText(value.endTime);

  if (
    !slotId ||
    !doctorId ||
    !doctorName ||
    !specialtyId ||
    !specialtyName ||
    !facilityId ||
    !facilityName ||
    !appointmentDate ||
    !startTime ||
    !endTime
  ) {
    return null;
  }

  return {
    source: toText(value.source) ?? 'AI_ASSISTANT',
    slotId,
    doctorId,
    doctorName,
    specialtyId,
    specialtyName,
    facilityId,
    facilityName,
    facilityAddress: toText(value.facilityAddress),
    appointmentDate,
    startTime,
    endTime,
  };
}

export function getBestBookingDraftForNavigation(
  action?: BookingDraftNavigationAction | null,
  currentResponseBookingDraft?: unknown,
  assistantMemoryBookingDraft?: unknown,
  assistantMemoryPendingBookingDraft?: unknown,
) {
  const candidates = [
    action?.payload?.bookingDraft,
    currentResponseBookingDraft,
    assistantMemoryBookingDraft,
    assistantMemoryPendingBookingDraft,
  ];

  for (const candidate of candidates) {
    const draft = normalizeAiBookingDraft(candidate);
    if (draft) return draft;
  }

  return null;
}

export function isAiBookingDraft(value: unknown): value is PublicAssistantBookingDraft {
  return normalizeAiBookingDraft(value) !== null;
}

export function persistAiBookingDraft(draft: PublicAssistantBookingDraft) {
  if (typeof window === 'undefined') return;
  const normalizedDraft = normalizeAiBookingDraft(draft);
  if (!normalizedDraft) return;
  window.sessionStorage.setItem(AI_BOOKING_DRAFT_STORAGE_KEY, JSON.stringify(normalizedDraft));
}

export function readPersistedAiBookingDraft() {
  if (typeof window === 'undefined') return null;

  try {
    const raw = window.sessionStorage.getItem(AI_BOOKING_DRAFT_STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    return normalizeAiBookingDraft(parsed);
  } catch {
    return null;
  }
}

export function clearPersistedAiBookingDraft() {
  if (typeof window === 'undefined') return;
  window.sessionStorage.removeItem(AI_BOOKING_DRAFT_STORAGE_KEY);
}

export function getAiBookingDraftFromRouteState(state: unknown) {
  if (!isRecord(state)) return null;
  return normalizeAiBookingDraft(state.aiBookingDraft);
}

export function createAiBookingSearchParams(draft: PublicAssistantBookingDraft) {
  const params = new URLSearchParams();
  params.set('prefill', '1');
  params.set('source', 'AI_ASSISTANT');
  params.set('aiDraft', '1');
  params.set('branchId', draft.facilityId);
  params.set('specialtyId', draft.specialtyId);
  params.set('doctorId', draft.doctorId);
  params.set('date', draft.appointmentDate);
  params.set('slot', draft.startTime);
  params.set('slotId', draft.slotId);
  return params;
}

export function getSessionFromAiBookingDraft(draft?: PublicAssistantBookingDraft | null) {
  const normalizedDraft = normalizeAiBookingDraft(draft);
  if (!normalizedDraft?.startTime) return undefined;
  const hour = Number(normalizedDraft.startTime.slice(0, 2));
  if (!Number.isFinite(hour)) return undefined;
  return hour < 12 ? 'AM' : 'PM';
}
