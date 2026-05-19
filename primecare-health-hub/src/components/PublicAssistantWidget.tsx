import { useEffect, useRef, useState } from 'react';
import {
  AlertTriangle,
  Bot,
  CalendarDays,
  Clock3,
  Loader2,
  MapPin,
  SendHorizonal,
  Sparkles,
  Stethoscope,
  Trash2,
  UserRound,
} from 'lucide-react';
import { format, parseISO } from 'date-fns';
import { enUS, vi } from 'date-fns/locale';
import { useTranslation } from 'react-i18next';
import { useLocation, useNavigate } from 'react-router-dom';
import { toast } from 'sonner';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { useCurrentUser } from '@/hooks/use-auth';
import { useAskPublicAssistant } from '@/hooks/use-public-data';
import {
  createAiBookingSearchParams,
  getBestBookingDraftForNavigation,
  normalizeAiBookingDraft,
  persistAiBookingDraft,
  type AiBookingRouteState,
} from '@/lib/ai-booking-draft';
import { getApiErrorMessage } from '@/lib/error-utils';
import {
  ASSISTANT_QUESTION_MAX_LENGTH,
  buildAssistantContextSnapshot,
  buildAssistantHistory,
  stripAssistantTechnicalJsonBlocks,
} from '@/lib/public-assistant-history';
import { cn } from '@/lib/utils';
import type {
  PublicAssistantAction,
  PublicAssistantActionType,
  PublicAssistantAvailableSlot,
  PublicAssistantBookingDraft,
  PublicAssistantContext,
  PublicAssistantRequestPayload,
  PublicAssistantResponse,
  PublicAssistantSuggestedDoctor,
} from '@/types/api';

interface ChatMessage {
  id: string;
  role: 'assistant' | 'user';
  text: string;
  provider?: string;
  caution?: string;
  safetyNote?: string;
  actions?: PublicAssistantAction[];
  suggestions?: string[];
  suggestedDoctor?: PublicAssistantSuggestedDoctor;
  availableSlots?: PublicAssistantAvailableSlot[];
  bookingDraft?: PublicAssistantBookingDraft | null;
  intent?: string;
  isSafety?: boolean;
  safetyLevel?: SafetyLevel;
}

interface AssistantMemory {
  conversationId?: string;
  context?: PublicAssistantContext;
  currentSpecialtyId?: string;
  currentDoctorId?: string;
  currentFacilityId?: string;
  suggestedDoctor?: PublicAssistantSuggestedDoctor;
  lastAvailableSlots?: PublicAssistantAvailableSlot[];
  selectedSlot?: PublicAssistantAvailableSlot;
  pendingBookingDraft?: PublicAssistantBookingDraft;
  bookingDraft?: PublicAssistantBookingDraft;
}

const STORAGE_KEY = 'primecare_public_ai_chat_v2';
const MEMORY_STORAGE_KEY = 'primecare_public_ai_context_v1';
const ASSISTANT_DISPLAY_NAME = 'PrimeCare AI';
type SafetyLevel = 'standard' | 'emergency';
type PendingRequestKind = 'message' | 'slots' | 'slot' | 'booking' | 'slow';
const createId = () => `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

const createWelcomeMessage = (isEn: boolean): ChatMessage => ({
  id: createId(),
  role: 'assistant',
  text: isEn
    ? 'Hello. I am PrimeCare AI. I can help you choose the right specialty, review suitable doctors, and prepare an appointment draft before you confirm.'
    : 'Xin chào. Tôi là PrimeCare AI. Tôi có thể hỗ trợ chọn chuyên khoa, gợi ý bác sĩ phù hợp và chuẩn bị bản nháp đặt lịch trước khi bạn xác nhận.',
  provider: ASSISTANT_DISPLAY_NAME,
  caution: isEn
    ? 'I support specialty guidance and booking assistance only. I do not replace a doctor’s diagnosis.'
    : 'AI chỉ hỗ trợ gợi ý chuyên khoa và đặt lịch, không thay thế chẩn đoán của bác sĩ.',
  suggestions: isEn
    ? ['I have stomach pain', 'I want the earliest appointment', 'Where is this clinic?']
    : ['Tôi đau dạ dày', 'Tôi muốn khám sớm nhất', 'Cơ sở này ở đâu?'],
});

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function readContextString(context: PublicAssistantContext | undefined, ...keys: string[]) {
  for (const key of keys) {
    const value = context?.[key];
    if (typeof value === 'string' && value.trim()) {
      return value;
    }
  }
  return undefined;
}

const normalizeStoredMessages = (value: unknown): ChatMessage[] | null => {
  if (!Array.isArray(value)) return null;
  const messages = value
    .filter((item): item is Partial<ChatMessage> => Boolean(item && typeof item === 'object'))
    .map((item) => ({
      id: typeof item.id === 'string' ? item.id : createId(),
      role: item.role === 'user' ? 'user' : 'assistant',
      text: typeof item.text === 'string'
        ? item.role === 'user'
          ? item.text
          : sanitizeAssistantDisplayText(item.text)
        : '',
      provider: typeof item.provider === 'string' ? item.provider : undefined,
      caution: typeof item.caution === 'string' ? item.caution : undefined,
      safetyNote: typeof item.safetyNote === 'string' ? item.safetyNote : undefined,
      actions: Array.isArray(item.actions) ? item.actions : undefined,
      suggestions: Array.isArray(item.suggestions)
        ? item.suggestions.filter((entry): entry is string => typeof entry === 'string')
        : undefined,
      suggestedDoctor: isRecord(item.suggestedDoctor)
        ? (item.suggestedDoctor as PublicAssistantSuggestedDoctor)
        : undefined,
      availableSlots: Array.isArray(item.availableSlots)
        ? (item.availableSlots as PublicAssistantAvailableSlot[])
        : undefined,
      bookingDraft: item.bookingDraft === null ? null : normalizeAiBookingDraft(item.bookingDraft) ?? undefined,
      intent: typeof item.intent === 'string' ? item.intent : undefined,
      isSafety: item.isSafety === true,
      safetyLevel: item.safetyLevel === 'emergency' ? 'emergency' : item.isSafety === true ? 'standard' : undefined,
    }))
    .filter(
      (item) =>
        item.text.trim().length > 0 ||
        Boolean(item.suggestedDoctor) ||
        Boolean(item.availableSlots?.length) ||
        Boolean(item.bookingDraft),
    );
  return messages.length ? messages : null;
};

const normalizeStoredMemory = (value: unknown): AssistantMemory | null => {
  if (!isRecord(value)) return null;
  return {
    conversationId: typeof value.conversationId === 'string' ? value.conversationId : undefined,
    context: isRecord(value.context) ? (value.context as PublicAssistantContext) : undefined,
    currentSpecialtyId: typeof value.currentSpecialtyId === 'string' ? value.currentSpecialtyId : undefined,
    currentDoctorId: typeof value.currentDoctorId === 'string' ? value.currentDoctorId : undefined,
    currentFacilityId: typeof value.currentFacilityId === 'string' ? value.currentFacilityId : undefined,
    suggestedDoctor: isRecord(value.suggestedDoctor)
      ? (value.suggestedDoctor as PublicAssistantSuggestedDoctor)
      : undefined,
    lastAvailableSlots: Array.isArray(value.lastAvailableSlots)
      ? (value.lastAvailableSlots as PublicAssistantAvailableSlot[])
      : undefined,
    selectedSlot: isRecord(value.selectedSlot) ? (value.selectedSlot as PublicAssistantAvailableSlot) : undefined,
    pendingBookingDraft: normalizeAiBookingDraft(value.pendingBookingDraft) ?? undefined,
    bookingDraft: normalizeAiBookingDraft(value.bookingDraft) ?? undefined,
  };
};

const getCurrentPageTitle = (pathname: string, isEn: boolean) => {
  switch (pathname) {
    case '/booking':
      return isEn ? 'Booking page' : 'Trang đặt lịch';
    case '/appointments/lookup':
      return isEn ? 'Lookup page' : 'Trang tra cứu';
    case '/specialties':
      return isEn ? 'Specialties page' : 'Trang chuyên khoa';
    case '/doctors':
      return isEn ? 'Doctors page' : 'Trang bác sĩ';
    case '/branches':
      return isEn ? 'Branches page' : 'Trang cơ sở';
    case '/medical-services':
      return isEn ? 'Medical services page' : 'Trang dịch vụ';
    case '/faq':
      return isEn ? 'FAQ page' : 'Trang câu hỏi thường gặp';
    case '/contact':
      return isEn ? 'Contact page' : 'Trang liên hệ';
    default:
      return isEn ? 'PrimeCare website' : 'Website PrimeCare';
  }
};

const normalizeProviderLabel = (provider?: string, isEn?: boolean) => {
  return ASSISTANT_DISPLAY_NAME;
};

const normalizeIntent = (intent?: string) => intent?.toUpperCase() ?? '';

const getResponseType = (response: PublicAssistantResponse) => {
  const value = (response as Record<string, unknown>).type;
  return typeof value === 'string' ? value : undefined;
};

const getResponseSafetyType = (response: PublicAssistantResponse) => {
  const value = (response as Record<string, unknown>).safetyType;
  return typeof value === 'string' ? value : undefined;
};

const getResponseIntentText = (response: PublicAssistantResponse) =>
  `${normalizeIntent(response.intent)} ${normalizeIntent(getResponseType(response))} ${normalizeIntent(getResponseSafetyType(response))}`.trim();

const isFacilityInfoIntent = (intent?: string) => {
  const value = normalizeIntent(intent);
  return value.includes('FACILITY') || value.includes('ADDRESS') || value.includes('LOCATION');
};

const isBookingDraftIntent = (intent?: string) => normalizeIntent(intent).includes('BOOKING_DRAFT');

const isGeneralFaqIntent = (intent?: string) => {
  const value = normalizeIntent(intent);
  return value.includes('GENERAL_FAQ') || value.includes('FAQ');
};

const isSystemInfoIntent = (intent?: string) => {
  const value = normalizeIntent(intent);
  return value.includes('SYSTEM_INFO') || value.includes('SYSTEM');
};

const isClarificationIntent = (intent?: string) => normalizeIntent(intent).includes('CLARIFICATION');

const isToolFallbackIntent = (intent?: string) => {
  const value = normalizeIntent(intent);
  return value.includes('TOOL_ERROR') || value.includes('TOOL_FAILED') || value.includes('FALLBACK');
};

const isRecommendationIntent = (intent?: string) => {
  const value = normalizeIntent(intent);
  if (
    isFacilityInfoIntent(intent) ||
    isBookingDraftIntent(intent) ||
    isGeneralFaqIntent(intent) ||
    isSystemInfoIntent(intent) ||
    isClarificationIntent(intent) ||
    isToolFallbackIntent(intent) ||
    value.includes('SAFETY') ||
    value.includes('BLOCK')
  ) {
    return false;
  }

  return (
    !value ||
    value.includes('RECOMMEND') ||
    value.includes('SHOW_SLOTS') ||
    value.includes('SHOW_AVAILABLE_SLOTS') ||
    value.includes('VIEW_MORE_SLOTS') ||
    value.includes('CHANGE_DOCTOR')
  );
};

const isSlotRefreshIntent = (intent?: string) => {
  const value = normalizeIntent(intent);
  if (
    isFacilityInfoIntent(intent) ||
    isBookingDraftIntent(intent) ||
    isGeneralFaqIntent(intent) ||
    isSystemInfoIntent(intent) ||
    isClarificationIntent(intent) ||
    isToolFallbackIntent(intent) ||
    value.includes('SAFETY') ||
    value.includes('BLOCK')
  ) {
    return false;
  }

  return (
    !value ||
    value.includes('SHOW_AVAILABLE_SLOTS') ||
    value.includes('SHOW_SLOTS') ||
    value.includes('VIEW_MORE_SLOTS') ||
    value.includes('CHANGE_DOCTOR') ||
    value.includes('RECOMMEND_DOCTOR_AND_SHOW_SLOTS')
  );
};

const isNoSlotsIntent = (intent?: string) => {
  const value = normalizeIntent(intent);
  return value.includes('NO_SLOTS') || value.includes('NO_AVAILABLE_SLOTS');
};

const shouldForceStructuredRefresh = (intent?: string) => {
  const value = normalizeIntent(intent);
  return value.includes('VIEW_MORE_SLOTS') || value.includes('CHANGE_DOCTOR');
};

const readContextBoolean = (context: PublicAssistantContext | undefined, ...keys: string[]) => {
  for (const key of keys) {
    if (context?.[key] === true) return true;
  }
  return false;
};

const shouldForceDoctorCardRefresh = (response: PublicAssistantResponse) =>
  shouldForceStructuredRefresh(response.intent) ||
  readContextBoolean(
    response.context,
    'renderDoctorCard',
    'forceDoctorCard',
    'forceDoctorCardRefresh',
    'updateDoctorCard',
  );

const shouldForceSlotsRefresh = (response: PublicAssistantResponse) =>
  shouldForceStructuredRefresh(response.intent) ||
  readContextBoolean(
    response.context,
    'renderAvailableSlots',
    'forceAvailableSlots',
    'forceSlotsRefresh',
    'updateAvailableSlots',
  );

const safetyBlockTypes = [
  'MEDICATION_SAFETY_BLOCK',
  'DIAGNOSIS_SAFETY_BLOCK',
  'EMERGENCY_SAFETY_BLOCK',
];

const getSafetyBlockType = (response: PublicAssistantResponse) => {
  const signal = getResponseIntentText(response);
  return safetyBlockTypes.find((type) => signal.includes(type));
};

const hasFixedSafetyText = (response: PublicAssistantResponse) => {
  const text = `${response.message ?? ''} ${response.answer ?? ''}`.toLowerCase();
  return (
    text.includes('không thể tư vấn thuốc') ||
    text.includes('không thể kê đơn') ||
    text.includes('không thể chỉ định thuốc') ||
    text.includes('không thể kết luận') ||
    text.includes('không thể chẩn đoán') ||
    text.includes('không đưa ra chẩn đoán') ||
    text.includes('không thay thế chẩn đoán') ||
    text.includes('gọi cấp cứu') ||
    text.includes('cannot advise medication') ||
    text.includes('cannot prescribe') ||
    text.includes('cannot diagnose') ||
    text.includes('emergency services')
  );
};

const isSafetyResponse = (response: PublicAssistantResponse) => {
  const intent = getResponseIntentText(response);
  return (
    Boolean(getSafetyBlockType(response)) ||
    intent.includes('SAFETY') ||
    intent.includes('BLOCK') ||
    hasFixedSafetyText(response)
  );
};

const getSafetyLevel = (response: PublicAssistantResponse): SafetyLevel => {
  const safetyBlockType = getSafetyBlockType(response);
  if (safetyBlockType === 'EMERGENCY_SAFETY_BLOCK') return 'emergency';
  const intent = getResponseIntentText(response);
  const text = `${response.message ?? ''} ${response.answer ?? ''} ${response.safetyNote ?? ''} ${response.caution ?? ''}`.toLowerCase();
  return intent.includes('EMERGENCY') ||
    intent.includes('URGENT') ||
    text.includes('cấp cứu') ||
    text.includes('115') ||
    text.includes('emergency')
    ? 'emergency'
    : 'standard';
};

const buildDoctorBlockKey = (doctor?: PublicAssistantSuggestedDoctor) => {
  if (!doctor?.doctorId) return '';
  return [doctor.doctorId, doctor.facilityId].filter(Boolean).join('|');
};

const buildStructuredBlockKey = (
  doctor?: PublicAssistantSuggestedDoctor,
  slots?: PublicAssistantAvailableSlot[],
  fallback?: { doctorId?: string; facilityId?: string; appointmentDate?: string },
) => {
  const firstSlot = slots?.[0];
  return [
    doctor?.doctorId ?? firstSlot?.doctorId ?? fallback?.doctorId ?? '',
    doctor?.facilityId ?? firstSlot?.facilityId ?? fallback?.facilityId ?? '',
    firstSlot?.appointmentDate ?? fallback?.appointmentDate ?? '',
    slots?.map((slot) => slot.slotId).filter(Boolean).join(',') ?? '',
  ].join('|');
};

const buildSlotsBlockKey = (slots?: PublicAssistantAvailableSlot[]) => {
  if (!slots?.length) return '';
  const first = slots[0];
  return buildStructuredBlockKey(undefined, slots, {
    doctorId: first.doctorId,
    facilityId: first.facilityId,
    appointmentDate: first.appointmentDate,
  });
};

const stripRawJsonText = (text: string) => {
  const trimmed = stripAssistantTechnicalJsonBlocks(text).trim();
  if (!trimmed) return '';
  const looksLikeJson =
    (trimmed.startsWith('{') && trimmed.endsWith('}')) ||
    (trimmed.startsWith('[') && trimmed.endsWith(']'));
  if (!looksLikeJson) return trimmed;

  try {
    JSON.parse(trimmed);
    return '';
  } catch {
    return text;
  }
};

const sanitizeAssistantDisplayText = (text: string) => stripRawJsonText(text);

const includesInsensitive = (text: string, value?: string) =>
  Boolean(value?.trim() && text.toLowerCase().includes(value.trim().toLowerCase()));

const getStructuredSummaryText = (
  isEn: boolean,
  doctor?: PublicAssistantSuggestedDoctor,
  slots?: PublicAssistantAvailableSlot[],
) => {
  if (doctor && slots?.length) {
    return isEn
      ? 'I found a suitable doctor and available appointment times. Please choose a slot below.'
      : 'Tôi đã tìm thấy bác sĩ và các ca khám phù hợp. Bạn có thể chọn khung giờ bên dưới.';
  }
  if (doctor) {
    return isEn
      ? 'I found a suitable doctor for your request.'
      : 'Tôi đã tìm thấy bác sĩ phù hợp với nhu cầu của bạn.';
  }
  if (slots?.length) {
    return isEn
      ? 'I found available appointment times. Please choose a slot below.'
      : 'Tôi đã tìm thấy các ca khám phù hợp. Bạn có thể chọn một khung giờ bên dưới.';
  }
  return '';
};

const stripDuplicateStructuredText = (
  text: string,
  doctor: PublicAssistantSuggestedDoctor | undefined,
  slots: PublicAssistantAvailableSlot[] | undefined,
  isEn: boolean,
) => {
  const withoutJson = stripRawJsonText(text);
  if (!doctor && !slots?.length) return withoutJson.trim();

  const slotLabels = new Set((slots ?? []).map(getSlotDisplayLabel).filter(Boolean));
  const cleanedLines = withoutJson
    .split(/\r?\n/)
    .filter((line) => {
      const lower = line.toLowerCase();
      const bracketTimes = line.match(/\[\s*\d{1,2}:\d{2}\s*\]/g) ?? [];
      if (bracketTimes.length >= 2) return false;
      if ([...slotLabels].some((label) => line.includes(`[${label}]`) || line.trim() === label)) {
        return false;
      }
      const looksLikeDoctorFact =
        /^(bác sĩ|bac si|doctor|chuyên khoa|chuyen khoa|specialty|cơ sở|co so|clinic|địa chỉ|dia chi|address)\b/i.test(
          line.trim(),
        );
      return !(
        doctor &&
        looksLikeDoctorFact &&
        (includesInsensitive(lower, doctor.doctorName) ||
          includesInsensitive(lower, doctor.specialtyName) ||
          includesInsensitive(lower, doctor.facilityName))
      );
    })
    .join('\n')
    .trim();

  const slotMentions = [...slotLabels].filter((label) => cleanedLines.includes(label)).length;
  const stillRepeatsDoctor =
    doctor &&
    includesInsensitive(cleanedLines, doctor.doctorName) &&
    (includesInsensitive(cleanedLines, doctor.specialtyName) ||
      includesInsensitive(cleanedLines, doctor.facilityName) ||
      slotMentions > 0);

  if (!cleanedLines || stillRepeatsDoctor || slotMentions >= 2) {
    return getStructuredSummaryText(isEn, doctor, slots);
  }

  return cleanedLines;
};

const getEmptySlotsText = (isEn: boolean) =>
  isEn
    ? 'There are no suitable available slots right now. You can try another doctor or time.'
    : 'Hiện chưa có lịch trống phù hợp. Bạn có thể thử bác sĩ khác hoặc thời gian khác.';

const getAssistantText = (response: PublicAssistantResponse, isEn: boolean) => {
  const text = typeof response.message === 'string' && response.message.trim()
    ? response.message
    : typeof response.answer === 'string'
      ? response.answer
      : '';

  if (isSafetyResponse(response)) {
    return text.trim() || response.safetyNote || response.caution || (isEn
      ? 'PrimeCare AI can help you choose care, but cannot provide medication instructions or a diagnosis.'
      : 'PrimeCare AI có thể hỗ trợ chọn hướng khám, nhưng không tư vấn thuốc hoặc chẩn đoán thay bác sĩ.');
  }
  if (text.trim()) return text;
  if (response.bookingDraft) {
    return isEn
      ? 'I prepared a booking draft. Please review it before confirming.'
      : 'Tôi đã chuẩn bị bản nháp đặt lịch. Bạn hãy kiểm tra trước khi xác nhận.';
  }
  if (isNoSlotsIntent(response.intent)) {
    return getEmptySlotsText(isEn);
  }
  if (response.availableSlots?.length) {
    return isEn
      ? 'I found available appointment times for you.'
      : 'Tôi đã tìm thấy các ca khám còn trống phù hợp.';
  }
  return isEn
    ? 'I received the update. Please continue when ready.'
    : 'Tôi đã nhận thông tin. Bạn có thể tiếp tục khi sẵn sàng.';
};

const formatAssistantDate = (value: string | undefined, isEn: boolean) => {
  if (!value) return '';
  try {
    return format(parseISO(value), 'EEEE, dd/MM/yyyy', { locale: isEn ? enUS : vi });
  } catch {
    return value;
  }
};

const getSlotDisplayLabel = (slot: PublicAssistantAvailableSlot) =>
  slot.displayLabel || slot.startTime || '';

const normalizeChoiceText = (value?: string) =>
  value
    ?.trim()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'd')
    .replace(/\s+/g, ' ')
    .toLowerCase() ?? '';

const inferQuestionLoadingKind = (value: string): PendingRequestKind => {
  const normalized = normalizeChoiceText(value);
  return [
    'lich',
    'slot',
    'ca',
    'gio',
    'khung',
    'som',
    'ngay mai',
    'buoi',
    'bac si khac',
  ].some((term) => normalized.includes(term))
    ? 'slots'
    : 'message';
};

const getActionLoadingKind = (action: PublicAssistantAction): PendingRequestKind => {
  if (action.type === 'SELECT_SLOT') return 'slot';
  if (action.type === 'GO_TO_BOOKING' || action.type === 'BOOK_APPOINTMENT_PREFILL') return 'booking';
  if (action.type === 'VIEW_MORE_SLOTS' || action.type === 'CHANGE_DOCTOR') return 'slots';
  return 'message';
};

const getLoadingText = (kind: PendingRequestKind | null, isEn: boolean) => {
  if (kind === 'slow') {
    return isEn
      ? 'PrimeCare AI is still checking suitable data...'
      : 'PrimeCare AI vẫn đang tra cứu dữ liệu phù hợp...';
  }

  switch (kind) {
    case 'slot':
      return isEn ? 'Checking this appointment slot...' : 'Đang kiểm tra ca khám...';
    case 'booking':
      return isEn ? 'Preparing booking details...' : 'Đang chuẩn bị thông tin đặt lịch...';
    case 'slots':
      return isEn
        ? 'PrimeCare AI is finding suitable appointment times...'
        : 'PrimeCare AI đang tìm lịch phù hợp...';
    case 'message':
    default:
      return isEn ? 'PrimeCare AI is responding...' : 'PrimeCare AI đang phản hồi...';
  }
};

const getIncompleteBookingDraftMessage = (isEn: boolean) =>
  isEn
    ? 'PrimeCare AI could not prepare complete booking details. Please choose the appointment slot again.'
    : 'PrimeCare AI chưa thể chuẩn bị đầy đủ thông tin đặt lịch. Bạn vui lòng chọn lại ca khám.';

const unsafeSuggestionTerms = [
  'uống thuốc',
  'thuốc gì',
  'xem đơn thuốc',
  'kê đơn',
  'chẩn đoán',
  'chan doan',
  'diagnose',
  'diagnosis',
  'medication',
  'prescription',
];

const isUnsafeSuggestion = (value: string) => {
  const normalized = normalizeChoiceText(value);
  return unsafeSuggestionTerms.some((term) => normalized.includes(normalizeChoiceText(term)));
};

const getActionFallbackLabel = (action: PublicAssistantAction, isEn: boolean) => {
  if (action.label?.trim()) return action.label;
  switch (action.type) {
    case 'GO_TO_BOOKING':
      return isEn ? 'Continue booking' : 'Tiếp tục đặt lịch';
    case 'VIEW_MORE_SLOTS':
      return isEn ? 'View more slots' : 'Xem thêm ca';
    case 'CHANGE_DOCTOR':
      return isEn ? 'Try another doctor' : 'Đổi bác sĩ';
    case 'VIEW_FACILITY_INFO':
      return isEn ? 'Where is this clinic?' : 'Cơ sở này ở đâu?';
    default:
      return isEn ? 'Continue' : 'Tiếp tục';
  }
};

const getActionDedupeKey = (action: PublicAssistantAction, isEn: boolean) => {
  const slotId = typeof action.payload?.slotId === 'string' ? action.payload.slotId : '';
  return `${action.type}-${normalizeChoiceText(getActionFallbackLabel(action, isEn))}-${slotId}`;
};

const shouldAutoNavigateToBooking = (response: PublicAssistantResponse) => {
  const record = response as Record<string, unknown>;
  return record.autoNavigate !== false && record.autoNavigateToBooking !== false;
};

const withContinueBookingAction = (
  response: PublicAssistantResponse,
  draft: PublicAssistantBookingDraft,
  isEn: boolean,
  includeAction: boolean,
): PublicAssistantResponse => {
  const continueAction: PublicAssistantAction = {
    type: 'GO_TO_BOOKING',
    label: isEn ? 'Continue booking' : 'Tiếp tục đặt lịch',
    payload: { bookingDraft: draft, slotId: draft.slotId },
  };

  return {
    ...response,
    bookingDraft: draft,
    actions: includeAction ? [continueAction, ...(response.actions ?? [])] : response.actions,
  };
};

const findFirstNormalizedBookingDraft = (...values: unknown[]) => {
  for (const value of values) {
    const draft = normalizeAiBookingDraft(value);
    if (draft) return draft;
  }
  return null;
};

const buildVisibleActions = (
  actions: PublicAssistantAction[] | undefined,
  response: PublicAssistantResponse,
  isEn: boolean,
  suppressActions?: boolean,
) => {
  if (suppressActions || isSafetyResponse(response)) return undefined;

  const bookingDraftIntent =
    isBookingDraftIntent(response.intent) || Boolean(response.bookingDraft || response.pendingBookingDraft);
  const seen = new Set<string>();
  const visibleActions = (actions ?? []).filter((action) => {
    if (action.type === 'SELECT_SLOT') return false;
    if (
      bookingDraftIntent &&
      action.type !== 'BOOK_APPOINTMENT' &&
      action.type !== 'BOOK_APPOINTMENT_PREFILL' &&
      action.type !== 'GO_TO_BOOKING'
    ) {
      return false;
    }

    const key = getActionDedupeKey(action, isEn);
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });

  return visibleActions.length ? visibleActions : undefined;
};

const buildVisibleSuggestions = (
  suggestions: string[] | undefined,
  actions: PublicAssistantAction[] | undefined,
  safetyResponse: boolean,
  isEn: boolean,
) => {
  if (safetyResponse || !suggestions?.length) return undefined;

  const actionLabels = new Set(
    (actions ?? []).map((action) => normalizeChoiceText(getActionFallbackLabel(action, isEn))),
  );
  const seen = new Set<string>();
  const visibleSuggestions = suggestions.filter((suggestion) => {
    const key = normalizeChoiceText(suggestion);
    if (!key || seen.has(key) || actionLabels.has(key) || isUnsafeSuggestion(suggestion)) return false;
    seen.add(key);
    return true;
  });

  return visibleSuggestions.length ? visibleSuggestions : undefined;
};

function DoctorSuggestionCard({
  doctor,
  isEn,
}: {
  doctor: PublicAssistantSuggestedDoctor;
  isEn: boolean;
}) {
  return (
    <div className="mt-3 rounded-xl border border-primary/15 bg-primary/[0.04] p-3 text-sm">
      <div className="flex items-start gap-3">
        <div className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
          <UserRound className="h-4 w-4" />
        </div>
        <div className="min-w-0 flex-1">
          <p className="break-words font-semibold text-foreground">{doctor.doctorName}</p>
          <div className="mt-2 space-y-1 text-xs leading-5 text-muted-foreground">
            <div className="flex items-start gap-2">
              <Stethoscope className="mt-0.5 h-3.5 w-3.5 shrink-0 text-primary" />
              <span className="break-words">
                {isEn ? 'Specialty' : 'Chuyên khoa'}: {doctor.specialtyName}
              </span>
            </div>
            <div className="flex items-start gap-2">
              <MapPin className="mt-0.5 h-3.5 w-3.5 shrink-0 text-primary" />
              <span className="break-words">
                {doctor.facilityName}
                {doctor.facilityAddress ? ` - ${doctor.facilityAddress}` : ''}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function AvailableSlotsPanel({
  slots,
  isEn,
  selectingSlotId,
  disabled,
  onSelectSlot,
}: {
  slots: PublicAssistantAvailableSlot[];
  isEn: boolean;
  selectingSlotId: string | null;
  disabled: boolean;
  onSelectSlot: (slot: PublicAssistantAvailableSlot) => void;
}) {
  if (!slots.length) {
    return (
      <div className="mt-3 rounded-xl border border-dashed border-border/70 bg-muted/15 px-3 py-4 text-xs leading-5 text-muted-foreground">
        {getEmptySlotsText(isEn)}
      </div>
    );
  }

  const firstDate = slots[0]?.appointmentDate;

  return (
    <div className="mt-3 rounded-xl border border-border/70 bg-muted/15 p-3">
      <div className="flex items-center gap-2 text-xs font-semibold text-foreground">
        <CalendarDays className="h-4 w-4 text-primary" />
        <span>{isEn ? 'Nearest slots' : 'Lịch gần nhất'}</span>
      </div>
      {firstDate ? (
        <p className="mt-1 text-xs leading-5 text-muted-foreground">
          {formatAssistantDate(firstDate, isEn)}
        </p>
      ) : null}
      <div className="mt-3 grid grid-cols-2 gap-2 min-[380px]:grid-cols-3">
        {slots.map((slot) => {
          const isSelecting = selectingSlotId === slot.slotId;
          return (
            <Button
              key={slot.slotId}
              type="button"
              size="sm"
              variant="outline"
              className="h-11 min-w-0 rounded-xl border-primary/20 bg-background px-2 text-sm font-semibold text-primary hover:bg-primary/5"
              disabled={disabled}
              onClick={() => onSelectSlot(slot)}
              aria-label={
                isEn
                  ? `Select ${getSlotDisplayLabel(slot)} appointment slot`
                  : `Chọn ca khám ${getSlotDisplayLabel(slot)}`
              }
            >
              {isSelecting ? <Loader2 className="h-4 w-4 shrink-0 animate-spin" /> : <Clock3 className="h-4 w-4 shrink-0" />}
              <span className="truncate">
                {isSelecting ? (isEn ? 'Checking...' : 'Đang kiểm tra') : getSlotDisplayLabel(slot)}
              </span>
            </Button>
          );
        })}
      </div>
    </div>
  );
}

function AiActionButtons({
  actions,
  isEn,
  pendingActionKey,
  disabled,
  onAction,
}: {
  actions?: PublicAssistantAction[];
  isEn: boolean;
  pendingActionKey: string | null;
  disabled: boolean;
  onAction: (action: PublicAssistantAction, actionKey: string) => void;
}) {
  const seen = new Set<string>();
  const visibleActions = (actions ?? []).filter((action) => {
    if (action.type === 'SELECT_SLOT') return false;
    const key = `${action.type}-${normalizeChoiceText(getActionFallbackLabel(action, isEn))}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
  if (!visibleActions.length) return null;

  return (
    <div className="mt-3 flex flex-wrap gap-2">
      {visibleActions.map((action, index) => {
        const actionKey = `${action.type}-${action.label}-${index}`;
        const isPending = pendingActionKey === actionKey;
        return (
          <Button
            key={actionKey}
            type="button"
            variant={action.type === 'GO_TO_BOOKING' ? 'default' : 'outline'}
            size="sm"
            className="min-h-9 rounded-xl text-xs"
            disabled={disabled}
            onClick={() => onAction(action, actionKey)}
          >
            {isPending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : null}
            {getActionFallbackLabel(action, isEn)}
          </Button>
        );
      })}
    </div>
  );
}

function SafetyNotice({ text }: { text?: string }) {
  if (!text) return null;
  return (
    <div className="mt-3 rounded-2xl border border-warning/20 bg-warning/10 px-3 py-2 text-xs leading-5 text-warning">
      {text}
    </div>
  );
}

function SafetyResponseMessage({ text, level }: { text: string; level: SafetyLevel }) {
  const emergency = level === 'emergency';
  return (
    <div
      className={cn(
        'rounded-xl border px-3 py-2.5 text-sm leading-6',
        emergency
          ? 'border-destructive/30 bg-destructive/10 text-foreground'
          : 'border-warning/20 bg-warning/10 text-foreground',
      )}
    >
      <div className="flex items-start gap-2.5">
        <AlertTriangle
          className={cn(
            'mt-0.5 h-4 w-4 shrink-0',
            emergency ? 'text-destructive' : 'text-warning',
          )}
        />
        <p className="whitespace-pre-wrap break-words">{text}</p>
      </div>
    </div>
  );
}

export function PublicAssistantWidget() {
  const { i18n } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const currentUser = useCurrentUser();
  const isEn = i18n.language?.startsWith('en');
  const askAssistant = useAskPublicAssistant();
  const endRef = useRef<HTMLDivElement | null>(null);
  const lastRenderedStructuredBlockKeyRef = useRef<string | null>(null);
  const lastRenderedDoctorCardKeyRef = useRef<string | null>(null);
  const lastRenderedSlotsBlockKeyRef = useRef<string | null>(null);

  const [open, setOpen] = useState(false);
  const [question, setQuestion] = useState('');
  const [selectingSlotId, setSelectingSlotId] = useState<string | null>(null);
  const [pendingActionKey, setPendingActionKey] = useState<string | null>(null);
  const [pendingRequestKind, setPendingRequestKind] = useState<PendingRequestKind | null>(null);
  const [isSlowResponse, setIsSlowResponse] = useState(false);
  const [assistantMemory, setAssistantMemory] = useState<AssistantMemory>(() => {
    if (typeof window === 'undefined') return {};
    try {
      const raw = window.sessionStorage.getItem(MEMORY_STORAGE_KEY);
      if (!raw) return {};
      return normalizeStoredMemory(JSON.parse(raw)) ?? {};
    } catch {
      return {};
    }
  });
  const [messages, setMessages] = useState<ChatMessage[]>(() => {
    if (typeof window === 'undefined') return [createWelcomeMessage(false)];
    try {
      const raw = window.sessionStorage.getItem(STORAGE_KEY);
      if (!raw) return [createWelcomeMessage(false)];
      const parsed = JSON.parse(raw);
      return normalizeStoredMessages(parsed) ?? [createWelcomeMessage(false)];
    } catch {
      return [createWelcomeMessage(false)];
    }
  });

  const isBusy = askAssistant.isPending || Boolean(selectingSlotId) || Boolean(pendingActionKey) || Boolean(pendingRequestKind);
  const questionLength = question.length;
  const isQuestionNearLimit = questionLength >= ASSISTANT_QUESTION_MAX_LENGTH * 0.9;

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
  }, [messages, open, isBusy]);

  useEffect(() => {
    if (!askAssistant.isPending && !pendingRequestKind) {
      setIsSlowResponse(false);
      return;
    }

    const timer = window.setTimeout(() => setIsSlowResponse(true), 5000);
    return () => window.clearTimeout(timer);
  }, [askAssistant.isPending, pendingRequestKind]);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(messages.slice(-30)));
  }, [messages]);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    window.sessionStorage.setItem(MEMORY_STORAGE_KEY, JSON.stringify(assistantMemory));
  }, [assistantMemory]);

  useEffect(() => {
    const latestDoctor = [...messages].reverse().find((message) => message.suggestedDoctor)?.suggestedDoctor;
    const latestSlots = [...messages].reverse().find((message) => message.availableSlots?.length)?.availableSlots;
    if (latestDoctor && !lastRenderedDoctorCardKeyRef.current) {
      lastRenderedDoctorCardKeyRef.current = buildDoctorBlockKey(latestDoctor);
    }
    if (latestSlots?.length && !lastRenderedSlotsBlockKeyRef.current) {
      lastRenderedSlotsBlockKeyRef.current = buildSlotsBlockKey(latestSlots);
    }
    if ((latestDoctor || latestSlots?.length) && !lastRenderedStructuredBlockKeyRef.current) {
      lastRenderedStructuredBlockKeyRef.current = buildStructuredBlockKey(latestDoctor, latestSlots);
    }
  }, [messages]);

  useEffect(() => {
    setMessages((current) =>
      current.length === 1 &&
      current[0]?.role === 'assistant' &&
      normalizeProviderLabel(current[0]?.provider, isEn) === ASSISTANT_DISPLAY_NAME
        ? [createWelcomeMessage(isEn)]
        : current,
    );
  }, [isEn]);

  const normalizeActionPath = (value?: string) => {
    if (!value) return '/';
    return value === '/lookup' ? '/appointments/lookup' : value;
  };

  const focusLookupField = (target: 'appointmentLookupCode' | 'resultLookupCode') => {
    const targetPath = target === 'resultLookupCode' ? '/results/lookup' : '/appointments/lookup';
    if (location.pathname !== targetPath) {
      navigate(`${targetPath}?focus=${encodeURIComponent(target)}`);
      setOpen(false);
      return;
    }
    const element = document.getElementById(target) as HTMLInputElement | null;
    element?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    window.setTimeout(() => element?.focus(), 180);
    setOpen(false);
  };

  const navigateToBookingWithDraft = (draft: unknown) => {
    const normalizedDraft = normalizeAiBookingDraft(draft);
    if (!normalizedDraft) {
      if (import.meta.env.DEV) {
        console.warn('[PrimeCare AI] Invalid booking draft for navigation', draft);
      }
      toast.error(getIncompleteBookingDraftMessage(isEn));
      return false;
    }

    try {
      persistAiBookingDraft(normalizedDraft);
      setAssistantMemory((current) => ({ ...current, bookingDraft: normalizedDraft }));
      const params = createAiBookingSearchParams(normalizedDraft);
      navigate(`/booking?${params.toString()}`, {
        state: { aiBookingDraft: normalizedDraft } satisfies AiBookingRouteState,
      });
      setOpen(false);
      return true;
    } catch {
      toast.error(
        isEn
          ? 'Unable to open the booking page. Please try continuing again.'
          : 'Không thể mở trang đặt lịch. Vui lòng thử tiếp tục lại.',
      );
      return false;
    }
  };

  const appendAssistantResponse = (
    response: PublicAssistantResponse,
    selectedSlot?: PublicAssistantAvailableSlot,
    options?: { suppressActions?: boolean; suppressStructuredBlocks?: boolean },
  ) => {
    const slotsForMemory = response.availableSlots ?? response.context?.lastAvailableSlots;
    const bookingDraft = findFirstNormalizedBookingDraft(response.bookingDraft, response.pendingBookingDraft);
    const safetyResponse = isSafetyResponse(response);
    const safetyLevel = safetyResponse ? getSafetyLevel(response) : undefined;
    const directAvailableSlots = response.availableSlots?.length ? response.availableSlots : undefined;
    const candidateDoctor =
      !options?.suppressStructuredBlocks &&
      !safetyResponse &&
      Boolean(response.suggestedDoctor) &&
      isRecommendationIntent(response.intent)
        ? response.suggestedDoctor
        : undefined;
    const candidateSlots =
      !options?.suppressStructuredBlocks &&
      !safetyResponse &&
      Boolean(directAvailableSlots?.length) &&
      isSlotRefreshIntent(response.intent)
        ? directAvailableSlots
        : undefined;

    const doctorKey = buildDoctorBlockKey(candidateDoctor);
    const slotsKey = buildSlotsBlockKey(candidateSlots);
    const doctorRefresh = shouldForceDoctorCardRefresh(response);
    const slotsRefresh = shouldForceSlotsRefresh(response);
    const visibleDoctor =
      candidateDoctor && (doctorKey !== lastRenderedDoctorCardKeyRef.current || doctorRefresh)
        ? candidateDoctor
        : undefined;
    const visibleSlots =
      candidateSlots && (slotsKey !== lastRenderedSlotsBlockKeyRef.current || slotsRefresh)
        ? candidateSlots
        : undefined;
    const nextStructuredKey = buildStructuredBlockKey(visibleDoctor, visibleSlots);
    const rawText = getAssistantText(response, isEn);
    const strippedText = safetyResponse
      ? rawText.trim()
      : stripDuplicateStructuredText(rawText, visibleDoctor, visibleSlots, isEn);
    const displayText = strippedText || (visibleSlots?.length
      ? getStructuredSummaryText(isEn, visibleDoctor, visibleSlots)
      : rawText);
    const sanitizedDisplayText = sanitizeAssistantDisplayText(displayText);
    const visibleActions = buildVisibleActions(response.actions, response, isEn, options?.suppressActions);
    const visibleSuggestions =
      options?.suppressActions || isBookingDraftIntent(response.intent) || Boolean(bookingDraft)
        ? undefined
        : buildVisibleSuggestions(response.suggestions, visibleActions, safetyResponse, isEn);

    if (visibleDoctor) {
      lastRenderedDoctorCardKeyRef.current = doctorKey;
    }
    if (visibleSlots) {
      lastRenderedSlotsBlockKeyRef.current = slotsKey;
    }
    if (nextStructuredKey && (visibleDoctor || visibleSlots)) {
      lastRenderedStructuredBlockKeyRef.current = nextStructuredKey;
    }

    setAssistantMemory((current) => ({
      conversationId: response.conversationId ?? current.conversationId,
      context: response.context ?? current.context,
      currentSpecialtyId:
        response.currentSpecialtyId ??
        readContextString(response.context, 'currentSpecialtyId', 'specialtyId') ??
        response.suggestedDoctor?.specialtyId ??
        bookingDraft?.specialtyId ??
        current.currentSpecialtyId,
      currentDoctorId:
        response.currentDoctorId ??
        readContextString(response.context, 'currentDoctorId', 'doctorId') ??
        response.suggestedDoctor?.doctorId ??
        bookingDraft?.doctorId ??
        current.currentDoctorId,
      currentFacilityId:
        response.currentFacilityId ??
        readContextString(response.context, 'currentFacilityId', 'facilityId') ??
        response.suggestedDoctor?.facilityId ??
        bookingDraft?.facilityId ??
        current.currentFacilityId,
      suggestedDoctor: response.suggestedDoctor ?? current.suggestedDoctor,
      lastAvailableSlots: slotsForMemory?.length ? slotsForMemory : current.lastAvailableSlots,
      selectedSlot: selectedSlot ?? (slotsForMemory?.length ? undefined : current.selectedSlot),
      pendingBookingDraft: bookingDraft ?? (slotsForMemory?.length ? undefined : current.pendingBookingDraft),
      bookingDraft: bookingDraft ?? (slotsForMemory?.length ? undefined : current.bookingDraft),
    }));

    setMessages((prev) => [
      ...prev,
      {
        id: createId(),
        role: 'assistant',
        text: sanitizedDisplayText,
        provider: ASSISTANT_DISPLAY_NAME,
        caution: safetyResponse ? undefined : response.safetyNote || response.caution,
        safetyNote: safetyResponse ? undefined : response.safetyNote,
        actions: visibleActions,
        suggestions: visibleSuggestions,
        suggestedDoctor: visibleDoctor,
        availableSlots: visibleSlots,
        bookingDraft,
        intent: response.intent,
        isSafety: safetyResponse,
        safetyLevel,
      },
    ]);
  };

  const buildPayload = (
    nextQuestion: string,
    overrides?: Partial<PublicAssistantRequestPayload>,
  ): PublicAssistantRequestPayload => {
    const history = buildAssistantHistory(messages);
    const contextSnapshot = buildAssistantContextSnapshot(assistantMemory);

    return {
      question: nextQuestion,
      message: nextQuestion,
      locale: isEn ? 'en' : 'vi',
      pagePath: location.pathname,
      pageTitle: getCurrentPageTitle(location.pathname, isEn),
      history,
      conversationId: assistantMemory.conversationId,
      ...contextSnapshot,
      selectedSlot: assistantMemory.selectedSlot,
      authState: {
        isAuthenticated: Boolean(currentUser),
        role: currentUser?.role,
        patientId: currentUser?.patientId,
      },
      ...overrides,
    };
  };

  const handleAssistantResponse = (
    response: PublicAssistantResponse,
    selectedSlot?: PublicAssistantAvailableSlot,
  ) => {
    const actionDraftCandidates = response.actions?.map((action) => action.payload?.bookingDraft) ?? [];
    const hasDraftCandidate = Boolean(
      response.bookingDraft ||
        response.pendingBookingDraft ||
        actionDraftCandidates.some((draft) => Boolean(draft)),
    );
    const draftForNavigation = findFirstNormalizedBookingDraft(
      response.bookingDraft,
      response.pendingBookingDraft,
      ...actionDraftCandidates,
    );
    const memoryDraftForNavigation = getBestBookingDraftForNavigation(
      undefined,
      undefined,
      assistantMemory.bookingDraft,
      assistantMemory.pendingBookingDraft,
    );

    if (hasDraftCandidate && !draftForNavigation && !memoryDraftForNavigation) {
      if (import.meta.env.DEV) {
        console.warn('[PrimeCare AI] Booking draft is missing required navigation fields', {
          bookingDraft: response.bookingDraft,
          pendingBookingDraft: response.pendingBookingDraft,
          actionDraftCandidates,
        });
      }
      appendAssistantResponse(
        {
          ...response,
          message: getIncompleteBookingDraftMessage(isEn),
          bookingDraft: null,
          pendingBookingDraft: null,
          availableSlots: undefined,
          actions: undefined,
          suggestions: undefined,
        },
        selectedSlot,
        {
          suppressActions: true,
          suppressStructuredBlocks: true,
        },
      );
      return;
    }

    const shouldAutoNavigate = draftForNavigation ? shouldAutoNavigateToBooking(response) : false;
    const navigated = draftForNavigation && shouldAutoNavigate ? navigateToBookingWithDraft(draftForNavigation) : false;
    const responseForAppend = draftForNavigation
      ? withContinueBookingAction(response, draftForNavigation, isEn, !navigated)
      : response;

    appendAssistantResponse(responseForAppend, selectedSlot, {
      suppressActions: navigated,
      suppressStructuredBlocks: Boolean(draftForNavigation),
    });

    if (isBookingDraftIntent(response.intent) && !draftForNavigation && !memoryDraftForNavigation) {
      toast.error(
        isEn
          ? 'The booking draft is missing. Please choose the slot again.'
          : 'Thiếu bản nháp đặt lịch. Vui lòng chọn lại ca khám.',
      );
    }
  };

  const appendSystemError = (text: string) => {
    setMessages((prev) => [
      ...prev,
      {
        id: createId(),
        role: 'assistant',
        text,
        provider: ASSISTANT_DISPLAY_NAME,
      },
    ]);
  };

  const submitAssistantPayload = async (
    label: string,
    overrides?: Partial<PublicAssistantRequestPayload>,
    selectedSlot?: PublicAssistantAvailableSlot,
    loadingKind: PendingRequestKind = 'message',
  ) => {
    setOpen(true);
    setPendingRequestKind(loadingKind);
    setMessages((prev) => [...prev, { id: createId(), role: 'user', text: label }]);
    try {
      const response = await askAssistant.mutateAsync(buildPayload(label, overrides));
      handleAssistantResponse(response, selectedSlot);
    } finally {
      setPendingRequestKind(null);
    }
  };

  const handleSubmit = async (preset?: string) => {
    const nextQuestion = (preset ?? question).trim();
    if (!nextQuestion) {
      toast.error(isEn ? 'Please enter a question.' : 'Vui lòng nhập câu hỏi.');
      return;
    }
    if (nextQuestion.length > ASSISTANT_QUESTION_MAX_LENGTH) {
      toast.error(
        isEn
          ? `Please keep your question under ${ASSISTANT_QUESTION_MAX_LENGTH} characters.`
          : `Vui lòng nhập câu hỏi dưới ${ASSISTANT_QUESTION_MAX_LENGTH} ký tự.`,
      );
      return;
    }

    setQuestion('');

    try {
      await submitAssistantPayload(nextQuestion, undefined, undefined, inferQuestionLoadingKind(nextQuestion));
    } catch (error) {
      toast.error(
        getApiErrorMessage(
          error,
          isEn ? 'Unable to ask the assistant right now.' : 'Không thể gọi trợ lý lúc này.',
        ),
      );
      appendSystemError(
        isEn
          ? 'I cannot answer right now. Please try again in a moment.'
          : 'Tôi chưa thể phản hồi lúc này. Vui lòng thử lại sau ít phút.',
      );
    }
  };

  const handleSelectSlot = async (slot: PublicAssistantAvailableSlot) => {
    const label = isEn
      ? `I choose ${getSlotDisplayLabel(slot)}`
      : `Tôi chọn ${getSlotDisplayLabel(slot)}`;

    setSelectingSlotId(slot.slotId);
    setAssistantMemory((current) => ({ ...current, selectedSlot: slot }));

    try {
      await submitAssistantPayload(
        label,
        {
          actionType: 'SELECT_SLOT',
          actionPayload: { slotId: slot.slotId, selectedSlot: slot },
          slotId: slot.slotId,
          selectedSlot: slot,
        },
        slot,
        'slot',
      );
    } catch (error) {
      toast.error(
        getApiErrorMessage(
          error,
          isEn
            ? 'Unable to prepare the booking draft for this slot.'
            : 'Không thể chuẩn bị bản nháp đặt lịch cho ca này.',
        ),
      );
      appendSystemError(
        isEn
          ? 'I could not verify this slot. Please choose another slot or try again.'
          : 'Tôi chưa kiểm tra được ca khám này. Bạn vui lòng chọn ca khác hoặc thử lại.',
      );
    } finally {
      setSelectingSlotId(null);
    }
  };

  const sendAssistantAction = async (action: PublicAssistantAction, actionKey: string) => {
    setPendingActionKey(actionKey);
    try {
      await submitAssistantPayload(action.label, {
        actionType: action.type as PublicAssistantActionType,
        actionPayload: action.payload,
        slotId: action.payload?.slotId,
      }, undefined, getActionLoadingKind(action));
    } catch (error) {
      toast.error(
        getApiErrorMessage(
          error,
          isEn ? 'Unable to process this action.' : 'Không thể xử lý thao tác này.',
        ),
      );
      appendSystemError(
        isEn
          ? 'This action could not be completed. Please try again.'
          : 'Thao tác này chưa thực hiện được. Vui lòng thử lại.',
      );
    } finally {
      setPendingActionKey(null);
    }
  };

  const navigateWithBestBookingDraft = (
    action: PublicAssistantAction,
    actionKey: string,
    currentResponseBookingDraft?: PublicAssistantBookingDraft | null,
  ) => {
    const draft = getBestBookingDraftForNavigation(
      action,
      currentResponseBookingDraft,
      assistantMemory.bookingDraft,
      assistantMemory.pendingBookingDraft,
    );

    if (!draft) return false;

    setPendingActionKey(actionKey);
    setPendingRequestKind('booking');
    try {
      return navigateToBookingWithDraft(draft);
    } finally {
      setPendingActionKey(null);
      setPendingRequestKind(null);
    }
  };

  const handleAssistantAction = (
    action: PublicAssistantAction,
    actionKey: string,
    currentResponseBookingDraft?: PublicAssistantBookingDraft | null,
  ) => {
    if (action.type === 'BOOK_APPOINTMENT_PREFILL') {
      if (navigateWithBestBookingDraft(action, actionKey, currentResponseBookingDraft)) {
        return;
      }
      toast.error(getIncompleteBookingDraftMessage(isEn));
      return;
    }
    if (action.type === 'BOOK_APPOINTMENT') {
      if (navigateWithBestBookingDraft(action, actionKey, currentResponseBookingDraft)) {
        return;
      }
      navigate(normalizeActionPath(action.value || '/booking'));
      setOpen(false);
      return;
    }
    if (action.type === 'LOOKUP_RESULT') {
      focusLookupField('resultLookupCode');
      return;
    }
    if (action.type === 'LOOKUP_APPOINTMENT') {
      focusLookupField('appointmentLookupCode');
      return;
    }
    if (action.type === 'GO_TO_BOOKING') {
      if (navigateWithBestBookingDraft(action, actionKey, currentResponseBookingDraft)) {
        return;
      }
      navigate(normalizeActionPath(action.value || '/booking'));
      setOpen(false);
      return;
    }
    if (action.type === 'SELECT_SLOT') {
      const slotId = action.payload?.slotId;
      const slot = assistantMemory.lastAvailableSlots?.find((item) => item.slotId === slotId);
      if (slot) {
        void handleSelectSlot(slot);
        return;
      }
      void sendAssistantAction(action, actionKey);
      return;
    }
    if (
      action.type === 'VIEW_MORE_SLOTS' ||
      action.type === 'CHANGE_DOCTOR' ||
      action.type === 'VIEW_FACILITY_INFO'
    ) {
      void sendAssistantAction(action, actionKey);
      return;
    }
    if (action.type === 'NAVIGATE') {
      navigate(normalizeActionPath(action.value));
      setOpen(false);
      return;
    }

    void sendAssistantAction(action, actionKey);
  };

  const clearConversation = () => {
    setMessages([createWelcomeMessage(isEn)]);
    setAssistantMemory({});
    lastRenderedStructuredBlockKeyRef.current = null;
    lastRenderedDoctorCardKeyRef.current = null;
    lastRenderedSlotsBlockKeyRef.current = null;
    if (typeof window !== 'undefined') {
      window.sessionStorage.removeItem(STORAGE_KEY);
      window.sessionStorage.removeItem(MEMORY_STORAGE_KEY);
    }
  };

  return (
    <>
      <div className="fixed bottom-5 right-5 z-40 flex flex-col items-end gap-2">
        {!open ? (
          <div className="hidden rounded-full border border-border/60 bg-background/95 px-3 py-1.5 text-xs text-muted-foreground shadow-sm backdrop-blur md:block">
            {isEn ? 'Need help booking?' : 'Cần hỗ trợ đặt lịch?'}
          </div>
        ) : null}
        <Button
          type="button"
          size="lg"
          onClick={() => setOpen(true)}
          className="h-14 rounded-full px-5 shadow-lg"
        >
          <Sparkles className="mr-2 h-4 w-4" />
          PrimeCare AI
        </Button>
      </div>

      <Sheet open={open} onOpenChange={setOpen}>
        <SheetContent side="right" className="flex h-full w-full flex-col gap-0 p-0 sm:max-w-[460px]">
          <SheetHeader className="border-b px-5 py-4">
            <div className="flex items-start justify-between gap-3 pr-10">
              <div className="space-y-1 text-left">
                <div className="flex items-center gap-2">
                  <div className="rounded-2xl border border-primary/15 bg-primary/10 p-2 text-primary">
                    <Bot className="h-4 w-4" />
                  </div>
                  <div>
                    <SheetTitle>{ASSISTANT_DISPLAY_NAME}</SheetTitle>
                    <div className="mt-1 flex flex-wrap items-center gap-2">
                      <Badge variant="secondary">{isEn ? 'Booking support' : 'Hỗ trợ đặt lịch'}</Badge>
                      <Badge variant="outline">{getCurrentPageTitle(location.pathname, isEn)}</Badge>
                    </div>
                  </div>
                </div>
                <SheetDescription>
                  {isEn
                    ? 'Ask naturally, choose a suggested slot, then review the booking draft before confirming.'
                    : 'Hỏi tự nhiên, chọn ca gợi ý, rồi kiểm tra bản nháp đặt lịch trước khi xác nhận.'}
                </SheetDescription>
              </div>
              <Button type="button" variant="ghost" size="icon" onClick={clearConversation}>
                <Trash2 className="h-4 w-4" />
              </Button>
            </div>
          </SheetHeader>

          <ScrollArea className="flex-1 bg-muted/20 px-3 py-4 sm:px-4">
            <div className="space-y-4 pb-2">
              {messages.map((message) => (
                <div
                  key={message.id}
                  className={cn('flex', message.role === 'user' ? 'justify-end' : 'justify-start')}
                >
                  <div
                    className={cn(
                      'max-w-[94%] overflow-hidden rounded-2xl px-3.5 py-3 text-sm shadow-sm sm:max-w-[90%] sm:px-4',
                      message.role === 'user'
                        ? 'rounded-br-md bg-primary text-primary-foreground'
                        : 'rounded-bl-md border bg-background text-foreground',
                    )}
                  >
                    {message.role === 'assistant' ? (
                      <Badge variant="secondary" className="mb-2">
                        {normalizeProviderLabel(message.provider, isEn)}
                      </Badge>
                    ) : null}
                    {message.isSafety && sanitizeAssistantDisplayText(message.text) ? (
                      <SafetyResponseMessage text={sanitizeAssistantDisplayText(message.text)} level={message.safetyLevel ?? 'standard'} />
                    ) : sanitizeAssistantDisplayText(message.text) ? (
                      <p className="whitespace-pre-wrap break-words leading-6">{sanitizeAssistantDisplayText(message.text)}</p>
                    ) : null}
                    {message.suggestedDoctor ? (
                      <DoctorSuggestionCard doctor={message.suggestedDoctor} isEn={isEn} />
                    ) : null}
                    {message.availableSlots ? (
                      <AvailableSlotsPanel
                        slots={message.availableSlots}
                        isEn={isEn}
                        selectingSlotId={selectingSlotId}
                        disabled={isBusy}
                        onSelectSlot={(slot) => void handleSelectSlot(slot)}
                      />
                    ) : null}
                    {!message.isSafety ? <SafetyNotice text={message.safetyNote || message.caution} /> : null}
                    <AiActionButtons
                      actions={message.actions}
                      isEn={isEn}
                      pendingActionKey={pendingActionKey}
                      disabled={isBusy}
                      onAction={(action, actionKey) => handleAssistantAction(action, actionKey, message.bookingDraft)}
                    />
                    {message.suggestions?.length ? (
                      <div className="mt-3 flex flex-wrap gap-2">
                        {message.suggestions.map((suggestion, index) => (
                          <Button
                            key={`${message.id}-suggestion-${index}`}
                            type="button"
                            variant="ghost"
                            size="sm"
                            className="h-auto rounded-full border bg-muted/50 px-3 py-1.5 text-xs"
                            disabled={isBusy}
                            onClick={() => void handleSubmit(suggestion)}
                          >
                            {suggestion}
                          </Button>
                        ))}
                      </div>
                    ) : null}
                  </div>
                </div>
              ))}
              {askAssistant.isPending || pendingRequestKind === 'booking' ? (
                <div className="flex justify-start">
                  <div className="flex max-w-[88%] items-center gap-2 rounded-2xl rounded-bl-md border bg-background px-4 py-3 text-sm text-muted-foreground shadow-sm">
                    <Loader2 className="h-4 w-4 animate-spin" />
                    {getLoadingText(isSlowResponse ? 'slow' : selectingSlotId ? 'slot' : pendingRequestKind, isEn)}
                  </div>
                </div>
              ) : null}
              <div ref={endRef} />
            </div>
          </ScrollArea>

          <div className="border-t bg-background px-4 py-4">
            <div className="mb-2 flex items-start justify-between gap-3 text-xs leading-5 text-muted-foreground">
              <span>
                {isEn
                  ? 'You can type naturally, for example “I choose 9 AM”, “first slot”, or “where is this clinic?”.'
                  : 'Bạn có thể gõ tự nhiên, ví dụ “tôi chọn 9h”, “ca đầu”, hoặc “cơ sở này ở đâu?”.'}
              </span>
              <span
                className={cn(
                  'shrink-0 tabular-nums',
                  isQuestionNearLimit && 'font-medium text-warning',
                )}
              >
                {questionLength}/{ASSISTANT_QUESTION_MAX_LENGTH}
              </span>
            </div>
            <div className="flex items-end gap-2">
              <Input
                value={question}
                onChange={(event) => setQuestion(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' && !event.shiftKey) {
                    event.preventDefault();
                    void handleSubmit();
                  }
                }}
                placeholder={
                  isEn
                    ? 'Describe symptoms or appointment needs...'
                    : 'Bạn có thể mô tả triệu chứng hoặc nhu cầu đặt lịch...'
                }
                className="h-12 rounded-2xl"
                disabled={isBusy}
                maxLength={ASSISTANT_QUESTION_MAX_LENGTH}
              />
              <Button
                type="button"
                className="h-12 rounded-2xl px-4"
                onClick={() => void handleSubmit()}
                disabled={isBusy}
              >
                {isBusy ? <Loader2 className="h-4 w-4 animate-spin" /> : <SendHorizonal className="h-4 w-4" />}
              </Button>
            </div>
          </div>
        </SheetContent>
      </Sheet>
    </>
  );
}
