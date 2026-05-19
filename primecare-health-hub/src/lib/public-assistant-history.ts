import type {
  PublicAssistantAvailableSlot,
  PublicAssistantBookingDraft,
  PublicAssistantContext,
  PublicAssistantMessagePayload,
  PublicAssistantSuggestedDoctor,
} from '@/types/api';

export const ASSISTANT_QUESTION_MAX_LENGTH = 2000;
export const MAX_USER_HISTORY_TEXT = 800;
export const MAX_ASSISTANT_HISTORY_TEXT = 300;
export const MAX_HISTORY_MESSAGES = 6;

type AssistantHistorySource = Pick<PublicAssistantMessagePayload, 'role' | 'text'>;

export interface AssistantBusinessMemory {
  context?: PublicAssistantContext;
  currentSpecialtyId?: string;
  currentDoctorId?: string;
  currentFacilityId?: string;
  lastAvailableSlots?: PublicAssistantAvailableSlot[];
  pendingBookingDraft?: PublicAssistantBookingDraft;
  bookingDraft?: PublicAssistantBookingDraft;
  suggestedDoctor?: PublicAssistantSuggestedDoctor;
}

export interface AssistantContextSnapshot {
  context?: PublicAssistantContext;
  currentSpecialtyId?: string;
  currentDoctorId?: string;
  currentFacilityId?: string;
  lastAvailableSlots?: PublicAssistantAvailableSlot[];
  pendingBookingDraft?: PublicAssistantBookingDraft;
  bookingDraft?: PublicAssistantBookingDraft;
  suggestedDoctor?: PublicAssistantSuggestedDoctor;
}

function readStringField(source: Record<string, unknown> | undefined, ...keys: string[]) {
  for (const key of keys) {
    const value = source?.[key];
    if (typeof value === 'string' && value.trim()) {
      return value;
    }
  }
  return undefined;
}

export function truncateAssistantHistoryText(text: string, maxLength: number) {
  const value = text.trim();
  if (value.length <= maxLength) return value;

  const suffix = '...';
  const sliceLength = Math.max(0, maxLength - suffix.length);
  return `${value.slice(0, sliceLength).trimEnd()}${suffix}`;
}

export function stripAssistantTechnicalJsonBlocks(text: string) {
  if (!text) return '';

  return text
    .replace(/```json\s*[\s\S]*?```/gi, '')
    .replace(/```JSON\s*[\s\S]*?```/g, '')
    .replace(/```json\s*(?:\{[\s\S]*?\}|\[[\s\S]*?\])\s*```/gi, '')
    .replace(/```(?:\{[\s\S]*?\}|\[[\s\S]*?\])\s*```/g, '')
    .replace(/[ \t]+\n/g, '\n')
    .replace(/\n{2,}/g, '\n')
    .trim();
}

export function buildAssistantHistory(messages: AssistantHistorySource[]) {
  return messages
    .map((message) => ({
      role: message.role,
      text: message.role === 'assistant' ? stripAssistantTechnicalJsonBlocks(message.text) : message.text,
    }))
    .filter((message) => message.text.trim())
    .slice(-MAX_HISTORY_MESSAGES)
    .map((message) => ({
      role: message.role,
      text:
        message.role === 'assistant'
          ? truncateAssistantHistoryText(message.text, MAX_ASSISTANT_HISTORY_TEXT)
          : truncateAssistantHistoryText(message.text, MAX_USER_HISTORY_TEXT),
    }));
}

export function buildAssistantContextSnapshot(memory: AssistantBusinessMemory): AssistantContextSnapshot {
  const context = memory.context;
  const pendingBookingDraft = memory.pendingBookingDraft ?? memory.bookingDraft;
  const suggestedDoctor = memory.suggestedDoctor;

  return {
    context,
    currentSpecialtyId:
      memory.currentSpecialtyId ??
      readStringField(context, 'currentSpecialtyId', 'specialtyId') ??
      pendingBookingDraft?.specialtyId ??
      suggestedDoctor?.specialtyId,
    currentDoctorId:
      memory.currentDoctorId ??
      readStringField(context, 'currentDoctorId', 'doctorId') ??
      pendingBookingDraft?.doctorId ??
      suggestedDoctor?.doctorId,
    currentFacilityId:
      memory.currentFacilityId ??
      readStringField(context, 'currentFacilityId', 'facilityId') ??
      pendingBookingDraft?.facilityId ??
      suggestedDoctor?.facilityId,
    lastAvailableSlots: memory.lastAvailableSlots ?? context?.lastAvailableSlots,
    pendingBookingDraft,
    bookingDraft: memory.bookingDraft,
    suggestedDoctor,
  };
}
