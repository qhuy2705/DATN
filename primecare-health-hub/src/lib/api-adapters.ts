import type {
  ApiResponse,
  Appointment,
  AuditLog,
  AvailabilitySlot,
  BookingRestriction,
  BookingRestrictionDetailResponse,
  BookingRestrictionOverrideResponse,
  BookingRestrictionSummary,
  BookingViolationEvent,
  BranchMasterDataSummary,
  CashierSummary,
  Branch,
  CurrentUser,
  DashboardBreakdown,
  DashboardBreakdownItem,
  DashboardKpis,
  DashboardOverview,
  Doctor,
  DoctorAppointmentSummary,
  DoctorMasterDataSummary,
  DoctorCareerTimelineItem,
  DoctorPublicSpecialty,
  DoctorOption,
  EncounterDiagnosisResponse,
  EncounterStatus,
  DoctorCancellationAffectedAppointment,
  DoctorCancellationRecoverySummary,
  InvoiceItemResponse,
  RefundableInvoiceItem,
  MedicalService,
  MedicationBatch,
  PaymentStatus,
  PublicFaqItem,
  PaginatedData,
  RateLimitRule,
  FollowUpQueueItem,
  ExpiringBatch,
  PharmacyInventoryItem,
  PrescriptionStatus,
  ServiceOrder,
  ServiceResultAttachment,
  ServiceResultTemplateCode,
  Specialty,
  RescheduleOffer,
  RescheduleOfferAppointmentInfo,
  StaffMasterDataSummary,
  Staff,
  ChronicCondition,
  ConfidenceLevel,
  FunctionalImpact,
  PreTriageLevel,
  PreTriageSource,
  RedFlag,
  SymptomOnset,
  TriageAuditLog,
  TriageMatchedRule,
  TriageMatchedTerm,
  TriagePriority,
  TriageReviewStatus,
} from '@/types/api';
import i18n from '@/i18n';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}


function getCurrentLanguage(): 'vi' | 'en' {
  return i18n.language?.toLowerCase().startsWith('en') ? 'en' : 'vi';
}

function pickLocalizedField(
  item: Record<string, unknown>,
  viKey: string,
  enKey: string,
  ...fallbackKeys: string[]
): string {
  const language = getCurrentLanguage();
  const preferredKeys = language === 'en' ? [enKey, viKey] : [viKey, enKey];
  for (const key of [...preferredKeys, ...fallbackKeys]) {
    const value = item[key];
    if (typeof value === 'string' && value.trim()) {
      return value;
    }
  }
  return '';
}


function formatTimeDisplay(value: unknown): string {
  if (typeof value !== 'string') return '';
  const trimmed = value.trim();
  if (!trimmed) return '';

  const match = trimmed.match(/^(\d{2}:\d{2})(?::\d{2})?$/);
  if (match) return match[1];

  if (/^\d{4}-\d{2}-\d{2}/.test(trimmed)) {
    const date = new Date(trimmed.replace(' ', 'T'));
    if (!Number.isNaN(date.getTime())) {
      return date.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
    }
  }

  return trimmed;
}

function normalizeScheduleSession(value: unknown): string {
  if (value === 'AM' || value === 'MORNING') return 'MORNING';
  if (value === 'PM' || value === 'AFTERNOON') return 'AFTERNOON';
  return String(value ?? '');
}

const PRESCRIPTION_STATUSES: readonly PrescriptionStatus[] = [
  'DRAFT',
  'ISSUED',
  'PAID',
  'DISPENSED',
  'CANCELLED',
];

const ENCOUNTER_STATUSES: readonly EncounterStatus[] = [
  'IN_PROGRESS',
  'REOPENED',
  'WAITING_PAYMENT',
  'WAITING_RESULTS',
  'READY_FOR_CONCLUSION',
  'COMPLETED',
  'CANCELLED',
];

const APPOINTMENT_STATUSES: readonly Appointment['status'][] = [
  'REQUESTED',
  'CONFIRMED',
  'CHECKED_IN',
  'COMPLETED',
  'CANCELLED',
  'NO_SHOW',
];

const PRE_TRIAGE_LEVELS: readonly PreTriageLevel[] = ['NONE', 'WATCH', 'RED_FLAG'];
const TRIAGE_PRIORITIES: readonly TriagePriority[] = ['URGENT', 'PRIORITY', 'ROUTINE'];
const TRIAGE_REVIEW_STATUSES: readonly TriageReviewStatus[] = ['ACCEPTED', 'OVERRIDDEN', 'MANUAL'];
const PRE_TRIAGE_SOURCES: readonly PreTriageSource[] = ['RULE', 'AI', 'HYBRID'];
const CONFIDENCE_LEVELS: readonly ConfidenceLevel[] = ['HIGH', 'MEDIUM', 'LOW'];
const SYMPTOM_ONSETS: readonly SymptomOnset[] = [
  'TODAY',
  'DAYS_2_3',
  'WEEK_1',
  'OVER_MONTH',
  'UNKNOWN',
];
const CHRONIC_CONDITIONS: readonly ChronicCondition[] = [
  'CARDIOVASCULAR',
  'DIABETES',
  'RESPIRATORY',
  'CANCER',
  'IMMUNODEFICIENCY',
  'PREGNANCY',
  'ELDERLY',
  'NONE',
];
const FUNCTIONAL_IMPACTS: readonly FunctionalImpact[] = [
  'NORMAL',
  'MILD_DIFFICULTY',
  'SEVERE_DIFFICULTY',
  'UNABLE_SELF_CARE',
  'UNKNOWN',
];
const RED_FLAGS: readonly RedFlag[] = [
  'CHEST_PAIN',
  'DYSPNEA',
  'FAINTING',
  'SEIZURE',
  'STROKE_SIGNS',
  'HEAVY_BLEEDING',
  'SEVERE_PAIN',
  'HIGH_FEVER',
  'ALLERGIC_REACTION',
  'NONE',
];

const PAYMENT_STATUSES: readonly PaymentStatus[] = [
  'UNPAID',
  'PENDING_CONFIRMATION',
  'PAYMENT_REVIEW',
  'PAID',
  'PARTIALLY_REFUNDED',
  'REFUNDED',
  'VOID',
];

const DIAGNOSIS_TYPES: readonly EncounterDiagnosisResponse['diagnosisType'][] = [
  'PRELIMINARY',
  'FINAL',
  'SECONDARY',
];

function normalizePrescriptionStatus(value: unknown): PrescriptionStatus {
  if (typeof value === 'string' && PRESCRIPTION_STATUSES.includes(value as PrescriptionStatus)) {
    return value as PrescriptionStatus;
  }

  return 'DRAFT';
}

function normalizeEncounterStatus(value: unknown, fallback: EncounterStatus = 'IN_PROGRESS'): EncounterStatus {
  if (typeof value === 'string' && ENCOUNTER_STATUSES.includes(value as EncounterStatus)) {
    return value as EncounterStatus;
  }

  return fallback;
}

function normalizeAppointmentStatus(value: unknown): Appointment['status'] {
  const status = String(value ?? 'REQUESTED');
  return APPOINTMENT_STATUSES.includes(status as Appointment['status'])
    ? (status as Appointment['status'])
    : 'REQUESTED';
}

function normalizePaymentStatus(value: unknown): PaymentStatus {
  const status = String(value ?? 'UNPAID');
  return PAYMENT_STATUSES.includes(status as PaymentStatus)
    ? (status as PaymentStatus)
    : (status as PaymentStatus);
}

function normalizeOptionalEncounterStatus(value: unknown): EncounterStatus | undefined {
  if (typeof value === 'string' && ENCOUNTER_STATUSES.includes(value as EncounterStatus)) {
    return value as EncounterStatus;
  }

  return undefined;
}

function normalizeOptionalEnum<T extends string>(value: unknown, allowed: readonly T[]): T | null {
  if (typeof value === 'string' && allowed.includes(value as T)) {
    return value as T;
  }

  return null;
}

function normalizeEnumArray<T extends string>(value: unknown, allowed: readonly T[]): T[] | null {
  const items = normalizeStringArray(value);
  if (!items) return null;

  return items.filter((item): item is T => allowed.includes(item as T));
}

function normalizeObjectArray(value: unknown): Record<string, unknown>[] {
  const asArray = Array.isArray(value) ? value : parseJsonString<unknown[]>(value);
  if (!Array.isArray(asArray)) return [];

  return asArray.filter(isRecord);
}

function normalizeMatchedTerms(value: unknown): TriageMatchedTerm[] {
  return normalizeObjectArray(value)
    .map((item) => ({
      term: typeof item.term === 'string' ? item.term : '',
      code: typeof item.code === 'string' ? item.code : '',
      label: typeof item.label === 'string' ? item.label : null,
      category: typeof item.category === 'string' ? item.category : undefined,
      source: typeof item.source === 'string' ? item.source : undefined,
      evidenceText:
        typeof item.evidenceText === 'string'
          ? item.evidenceText
          : typeof item.evidence_text === 'string'
            ? item.evidence_text
            : null,
      weight: typeof item.weight === 'number' ? item.weight : null,
    }))
    .filter((item) => item.term || item.code);
}

function normalizeMatchedRules(value: unknown): TriageMatchedRule[] {
  return normalizeObjectArray(value)
    .map((item) => ({
      id: String(item.id ?? item.ruleId ?? ''),
      priority: typeof item.priority === 'string' ? item.priority : null,
      level: typeof item.level === 'string' ? item.level : null,
      reason: typeof item.reason === 'string' ? item.reason : null,
      source: typeof item.source === 'string' ? item.source : null,
      matchedCodes: normalizeStringArray(item.matchedCodes ?? item.matched_codes) ?? null,
    }))
    .filter((item) => item.id || item.reason);
}

function normalizeTriageAuditLogs(value: unknown): TriageAuditLog[] {
  return normalizeObjectArray(value)
    .map((item, index) => ({
      id:
        typeof item.id === 'string' || typeof item.id === 'number'
          ? item.id
          : `audit-${index}`,
      actorType:
        typeof item.actorType === 'string'
          ? item.actorType
          : typeof item.actor_type === 'string'
            ? item.actor_type
            : 'SYSTEM',
      actorName:
        typeof item.actorName === 'string'
          ? item.actorName
          : typeof item.actor_name === 'string'
            ? item.actor_name
            : null,
      action: typeof item.action === 'string' ? item.action : '',
      fromPriority:
        typeof item.fromPriority === 'string'
          ? item.fromPriority
          : typeof item.from_priority === 'string'
            ? item.from_priority
            : null,
      toPriority:
        typeof item.toPriority === 'string'
          ? item.toPriority
          : typeof item.to_priority === 'string'
            ? item.to_priority
            : null,
      reason: typeof item.reason === 'string' ? item.reason : null,
      createdAt:
        typeof item.createdAt === 'string'
          ? item.createdAt
          : typeof item.created_at === 'string'
            ? item.created_at
            : null,
    }))
    .filter((item) => item.action || item.fromPriority || item.toPriority || item.reason);
}

function pickString(item: Record<string, unknown>, ...keys: string[]): string | undefined {
  for (const key of keys) {
    const value = item[key];
    if (typeof value === 'string' && value.trim()) return value;
    if (typeof value === 'number') return String(value);
  }
  return undefined;
}

function pickJsonValue(item: Record<string, unknown>, ...keys: string[]): unknown {
  for (const key of keys) {
    if (Object.prototype.hasOwnProperty.call(item, key) && typeof item[key] !== 'undefined') {
      return item[key];
    }
  }
  return undefined;
}

function pickBoolean(item: Record<string, unknown>, ...keys: string[]): boolean | undefined {
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

function normalizeRescheduleAppointmentInfo(raw: unknown): RescheduleOfferAppointmentInfo | null {
  if (!isRecord(raw)) return null;
  return {
    id: pickString(raw, 'id', 'appointmentId'),
    code: pickString(raw, 'code', 'appointmentCode'),
    doctorName: pickLocalizedField(
      raw,
      'doctorNameVn',
      'doctorNameEn',
      'doctorName',
      'heldDoctorName',
      'proposedDoctorName',
      'originalDoctorName',
    ),
    specialtyName: pickLocalizedField(
      raw,
      'specialtyNameVn',
      'specialtyNameEn',
      'specialtyName',
      'heldSpecialtyName',
      'proposedSpecialtyName',
      'originalSpecialtyName',
    ),
    branchName: pickLocalizedField(raw, 'branchNameVn', 'branchNameEn', 'branchName'),
    visitDate: pickString(raw, 'visitDate', 'date', 'heldVisitDate', 'proposedVisitDate', 'originalVisitDate'),
    slotStart: formatTimeDisplay(
      raw.slotStart ?? raw.startTime ?? raw.heldSlotStart ?? raw.proposedSlotStart ?? raw.originalSlotStart,
    ),
    slotEnd: formatTimeDisplay(
      raw.slotEnd ?? raw.endTime ?? raw.heldSlotEnd ?? raw.proposedSlotEnd ?? raw.originalSlotEnd,
    ),
    status: pickString(raw, 'status'),
  };
}

export function normalizeRescheduleOffer(raw: unknown): RescheduleOffer {
  const item = (raw ?? {}) as Record<string, unknown>;
  const selfAppointment =
    item.doctorName != null || item.visitDate != null || item.slotStart != null
      ? normalizeRescheduleAppointmentInfo(item)
      : null;
  return {
    token: pickString(item, 'token') ?? '',
    status: pickString(item, 'status', 'holdStatus') ?? 'INVALID',
    patientFullName: pickString(item, 'patientFullName', 'patientName'),
    patientEmail: pickString(item, 'patientEmail'),
    patientPhone: pickString(item, 'patientPhone'),
    originalAppointment: normalizeRescheduleAppointmentInfo(
      item.originalAppointment ?? item.oldAppointment ?? item.cancelledAppointment,
    ),
    proposedAppointment: normalizeRescheduleAppointmentInfo(
      item.proposedAppointment ?? item.newAppointment ?? item.heldSlot,
    ),
    acceptedAppointment:
      normalizeRescheduleAppointmentInfo(
        item.acceptedAppointment ?? item.appointment ?? item.confirmedAppointment,
      ) ?? selfAppointment,
    expiresAt: pickString(item, 'expiresAt', 'holdExpiresAt') ?? null,
    sameDoctor: pickBoolean(item, 'sameDoctor'),
    sameDay: pickBoolean(item, 'sameDay'),
    sameSpecialty: pickBoolean(item, 'sameSpecialty'),
    message: pickString(item, 'message') ?? null,
  };
}

export function normalizeAffectedAppointment(raw: unknown): DoctorCancellationAffectedAppointment {
  const item = (raw ?? {}) as Record<string, unknown>;
  return {
    id: String(item.id ?? item.appointmentId ?? ''),
    code: pickString(item, 'code', 'appointmentCode'),
    patientFullName: pickString(item, 'patientFullName', 'patientName') ?? '',
    patientPhone: pickString(item, 'patientPhone'),
    patientEmail: pickString(item, 'patientEmail'),
    doctorName: pickLocalizedField(item, 'doctorNameVn', 'doctorNameEn', 'doctorName'),
    specialtyName: pickLocalizedField(item, 'specialtyNameVn', 'specialtyNameEn', 'specialtyName'),
    branchName: pickLocalizedField(item, 'branchNameVn', 'branchNameEn', 'branchName'),
    visitDate: pickString(item, 'visitDate'),
    slotStart: formatTimeDisplay(item.slotStart ?? item.startTime),
    slotEnd: formatTimeDisplay(item.slotEnd ?? item.endTime),
    status: pickString(item, 'status'),
  };
}

export function normalizeRecoverySummary(raw: unknown): DoctorCancellationRecoverySummary | null {
  if (!isRecord(raw)) return null;
  return {
    affectedAppointments: pickNumberField(raw, 'affectedAppointments', 'affectedAppointmentCount'),
    slotHoldsCreated: pickNumberField(raw, 'slotHoldsCreated', 'slotHoldCount'),
    emailsQueued: pickNumberField(raw, 'emailsQueued', 'emailQueuedCount'),
    staffFollowUpRequired: pickNumberField(raw, 'staffFollowUpRequired', 'manualFollowUpCount'),
  };
}

export function normalizeFollowUpQueueItem(raw: unknown): FollowUpQueueItem {
  const item = (raw ?? {}) as Record<string, unknown>;
  return {
    id: String(item.id ?? item.followUpId ?? item.appointmentId ?? ''),
    appointmentId: String(item.appointmentId ?? item.id ?? ''),
    appointmentCode: pickString(item, 'appointmentCode', 'code'),
    followUpType: pickString(item, 'followUpType', 'type', 'category') ?? 'NO_SHOW',
    patientFullName: pickString(item, 'patientFullName', 'patientName') ?? '',
    patientPhone: pickString(item, 'patientPhone'),
    patientEmail: pickString(item, 'patientEmail'),
    doctorName: pickLocalizedField(item, 'doctorNameVn', 'doctorNameEn', 'doctorName'),
    specialtyName: pickLocalizedField(item, 'specialtyNameVn', 'specialtyNameEn', 'specialtyName'),
    originalVisitDate: pickString(item, 'originalVisitDate', 'visitDate'),
    originalSlotStart: formatTimeDisplay(item.originalSlotStart ?? item.slotStart),
    originalSlotEnd: formatTimeDisplay(item.originalSlotEnd ?? item.slotEnd),
    heldDoctorName: pickString(item, 'heldDoctorName', 'proposedDoctorName'),
    heldVisitDate: pickString(item, 'heldVisitDate', 'proposedVisitDate'),
    heldSlotStart: formatTimeDisplay(item.heldSlotStart ?? item.proposedSlotStart),
    heldSlotEnd: formatTimeDisplay(item.heldSlotEnd ?? item.proposedSlotEnd),
    holdStatus: pickString(item, 'holdStatus'),
    expiresAt: pickString(item, 'expiresAt', 'holdExpiresAt') ?? null,
    createdAt: pickString(item, 'createdAt') ?? null,
  };
}

function normalizeDiagnosisType(value: unknown): EncounterDiagnosisResponse['diagnosisType'] {
  if (value === 'COMORBIDITY') return 'SECONDARY';
  if (typeof value === 'string' && DIAGNOSIS_TYPES.includes(value as EncounterDiagnosisResponse['diagnosisType'])) {
    return value as EncounterDiagnosisResponse['diagnosisType'];
  }

  return 'PRELIMINARY';
}

function normalizeEncounterDiagnosis(raw: unknown): EncounterDiagnosisResponse {
  const item = (raw ?? {}) as Record<string, unknown>;
  return {
    id: typeof item.id === 'number' ? item.id : undefined,
    icd10CodeId: Number(item.icd10CodeId ?? item.icd10Id ?? 0),
    icd10Code: typeof item.icd10Code === 'string' ? item.icd10Code : undefined,
    icd10NameVn:
      typeof item.icd10NameVn === 'string'
        ? item.icd10NameVn
        : typeof item.icd10Name === 'string'
          ? item.icd10Name
          : undefined,
    diagnosisType: normalizeDiagnosisType(item.diagnosisType),
    note: typeof item.note === 'string' ? item.note : undefined,
    displayOrder:
      typeof item.displayOrder === 'number'
        ? item.displayOrder
        : typeof item.displayOrder === 'string'
          ? Number(item.displayOrder)
          : undefined,
  };
}

function sanitizeMediaUrl(value: unknown): string | undefined {
  if (typeof value !== 'string') return undefined;

  const trimmed = value.trim();
  if (!trimmed) return undefined;

  const lower = trimmed.toLowerCase();
  if (lower.includes('seed.primecare.test')) return undefined;
  if (/^doc\d+\.(jpg|jpeg|png|webp)$/i.test(trimmed)) return undefined;

  const isAbsolute = /^https?:\/\//i.test(trimmed);
  const isRootRelative = trimmed.startsWith('/');
  const isDataUrl = trimmed.startsWith('data:');
  const isBlobUrl = trimmed.startsWith('blob:');
  const isLocalProtocol = trimmed.startsWith('local://');

  if (!isAbsolute && !isRootRelative && !isDataUrl && !isBlobUrl && !isLocalProtocol) {
    return undefined;
  }

  return trimmed;
}


function parseJsonString<T = unknown>(value: unknown): T | undefined {
  if (typeof value !== 'string' || !value.trim()) return undefined;

  try {
    return JSON.parse(value) as T;
  } catch {
    return undefined;
  }
}

function normalizeStringArray(value: unknown): string[] | undefined {
  if (Array.isArray(value)) {
    const items = value
      .map((item) => (typeof item === 'string' ? item.trim() : ''))
      .filter(Boolean);
    return items.length ? items : undefined;
  }

  if (typeof value === 'string' && value.trim()) {
    const parsed = parseJsonString<unknown[]>(value);
    if (Array.isArray(parsed)) {
      const items = parsed
        .map((item) => (typeof item === 'string' ? item.trim() : ''))
        .filter(Boolean);
      if (items.length) return items;
    }

    const items = value
      .split(/\n|\u2022|•|\||;/)
      .map((item) => item.trim())
      .filter(Boolean);
    return items.length ? items : undefined;
  }

  return undefined;
}

function normalizeCareerTimeline(value: unknown): DoctorCareerTimelineItem[] | undefined {
  const asArray = Array.isArray(value) ? value : parseJsonString<unknown[]>(value);
  if (!Array.isArray(asArray)) return undefined;

  const items = asArray
    .map((entry) => {
      if (!isRecord(entry)) return null;
      const title = typeof entry.title === 'string' ? entry.title.trim() : '';
      if (!title) return null;
      return {
        period: typeof entry.period === 'string' ? entry.period.trim() : undefined,
        title,
        organization: typeof entry.organization === 'string' ? entry.organization.trim() : undefined,
        description: typeof entry.description === 'string' ? entry.description.trim() : undefined,
      } satisfies DoctorCareerTimelineItem;
    })
    .filter((entry): entry is NonNullable<typeof entry> => Boolean(entry));

  return items.length ? items : undefined;
}

function normalizeServiceResultAttachments(
  rawUrlsJson: unknown,
  fallbackUrl: unknown,
  fallbackMimeType: unknown,
): ServiceResultAttachment[] {
  const parsed = parseJsonString<unknown[]>(rawUrlsJson);

  if (Array.isArray(parsed)) {
    const attachments = parsed
      .map((entry) => {
        if (typeof entry === 'string') {
          const url = sanitizeMediaUrl(entry) ?? entry.trim();
          return url ? ({ url } satisfies ServiceResultAttachment) : null;
        }

        if (!isRecord(entry)) return null;

        const urlSource = entry.url ?? entry.path ?? entry.downloadUrl;
        const url = sanitizeMediaUrl(urlSource) ?? (typeof urlSource === 'string' ? urlSource.trim() : '');
        if (!url) return null;

        return {
          url,
          mimeType: typeof entry.mimeType === 'string' ? entry.mimeType : undefined,
          fileName: typeof entry.fileName === 'string' ? entry.fileName : undefined,
          label: typeof entry.label === 'string' ? entry.label : undefined,
        } satisfies ServiceResultAttachment;
      })
      .filter((entry): entry is NonNullable<typeof entry> => Boolean(entry));

    if (attachments.length > 0) {
      return attachments;
    }
  }

  const url = sanitizeMediaUrl(fallbackUrl) ?? (typeof fallbackUrl === 'string' ? fallbackUrl.trim() : '');
  if (!url) {
    return [];
  }

  return [
    {
      url,
      mimeType: typeof fallbackMimeType === 'string' ? fallbackMimeType : undefined,
    },
  ];
}

function normalizeServiceOrderItemResultFields(row: Record<string, unknown>) {
  return {
    resultTextVn: typeof row.resultTextVn === 'string' ? row.resultTextVn : undefined,
    resultTextEn: typeof row.resultTextEn === 'string' ? row.resultTextEn : undefined,
    resultDataJson: typeof row.resultDataJson === 'string' ? row.resultDataJson : undefined,
    fieldValuesJson: typeof row.fieldValuesJson === 'string' ? row.fieldValuesJson : undefined,
    attachmentUrl: typeof row.attachmentUrl === 'string' ? row.attachmentUrl : undefined,
    attachmentMimeType:
      typeof row.attachmentMimeType === 'string' ? row.attachmentMimeType : undefined,
    attachmentUrlsJson:
      typeof row.attachmentUrlsJson === 'string' ? row.attachmentUrlsJson : undefined,
    attachments: normalizeServiceResultAttachments(
      row.attachmentUrlsJson,
      row.attachmentUrl,
      row.attachmentMimeType,
    ),
    templateCode: typeof row.templateCode === 'string' ? (row.templateCode as ServiceResultTemplateCode) : undefined,
    templateSchemaJson:
      typeof row.templateSchemaJson === 'string' ? row.templateSchemaJson : undefined,
    conclusionText:
      typeof row.conclusionText === 'string' ? row.conclusionText : undefined,
    impressionText:
      typeof row.impressionText === 'string' ? row.impressionText : undefined,
    reportTitle: typeof row.reportTitle === 'string' ? row.reportTitle : undefined,
    reportPdfUrl: typeof row.reportPdfUrl === 'string' ? row.reportPdfUrl : undefined,
    reportPdfStatus:
      typeof row.reportPdfStatus === 'string' ? row.reportPdfStatus : undefined,
    reportPdfGeneratedAt:
      typeof row.reportPdfGeneratedAt === 'string' ? row.reportPdfGeneratedAt : undefined,
    reportPdfErrorMessage:
      typeof row.reportPdfErrorMessage === 'string' ? row.reportPdfErrorMessage : undefined,
    turnaroundTargetMinutes:
      typeof row.turnaroundTargetMinutes === 'number'
        ? row.turnaroundTargetMinutes
        : undefined,
    dueAt: typeof row.dueAt === 'string' ? row.dueAt : undefined,
    elapsedMinutes:
      typeof row.elapsedMinutes === 'number' ? row.elapsedMinutes : undefined,
    turnaroundMinutes:
      typeof row.turnaroundMinutes === 'number' ? row.turnaroundMinutes : undefined,
    overdue: typeof row.overdue === 'boolean' ? row.overdue : undefined,
  };
}

function normalizeRefundFields(row: Record<string, unknown>) {
  return {
    refundStatus: pickStringField(row, 'refundStatus', 'refund_status', 'refundState', 'refund_state'),
    refunded: pickBooleanField(row, 'refunded', 'isRefunded', 'is_refunded'),
    refundedAt: pickStringField(
      row,
      'refundedAt',
      'refunded_at',
      'refundAt',
      'refund_at',
      'cancelledAt',
      'cancelled_at',
      'canceledAt',
      'canceled_at',
    ),
    refundReason: pickStringField(
      row,
      'refundReason',
      'refund_reason',
      'cancellationReason',
      'cancellation_reason',
      'cancelReason',
      'cancel_reason',
    ),
    notRefundableReason: pickStringField(
      row,
      'notRefundableReason',
      'not_refundable_reason',
      'nonRefundableReason',
      'non_refundable_reason',
    ),
    refundedAmount: pickNumberField(row, 'refundedAmount', 'refunded_amount', 'refundAmount', 'refund_amount'),
    remainingAmount: pickNumberField(row, 'remainingAmount', 'remaining_amount'),
  };
}

export function unwrapApiData<T>(payload: ApiResponse<T> | T): T {
  if (isRecord(payload) && 'data' in payload) {
    return payload.data as T;
  }
  return payload as T;
}

export function unwrapPage<T>(payload: unknown): PaginatedData<T> {
  const data = unwrapApiData(payload as ApiResponse<unknown>);

  if (isRecord(data) && Array.isArray(data.items)) {
    return {
      items: data.items as T[],
      meta: (data.meta as PaginatedData<T>['meta']) ?? {
        page: 0,
        size: (data.items as unknown[]).length,
        totalItems: (data.items as unknown[]).length,
        totalPages: 1,
        hasNext: false,
        hasPrev: false,
        sort: '',
      },
    };
  }

  if (isRecord(data) && Array.isArray((data as { content?: unknown[] }).content)) {
    const content = (data as { content: T[] }).content;
    const currentPage = Number((data as { number?: number }).number ?? 0);

    return {
      items: content,
      meta: {
        page: currentPage,
        size: Number((data as { size?: number }).size ?? content.length),
        totalItems: Number((data as { totalElements?: number }).totalElements ?? content.length),
        totalPages: Number((data as { totalPages?: number }).totalPages ?? 1),
        hasNext: (data as { last?: boolean }).last !== true,
        hasPrev: currentPage > 0,
        sort: '',
      },
    };
  }

  if (Array.isArray(data)) {
    return {
      items: data as T[],
      meta: {
        page: 0,
        size: data.length,
        totalItems: data.length,
        totalPages: 1,
        hasNext: false,
        hasPrev: false,
        sort: '',
      },
    };
  }

  return {
    items: [],
    meta: {
      page: 0,
      size: 0,
      totalItems: 0,
      totalPages: 0,
      hasNext: false,
      hasPrev: false,
      sort: '',
    },
  };
}

export function unwrapPageItems<T>(payload: unknown): T[] {
  return unwrapPage<T>(payload).items;
}

export function normalizeBookingViolationEvent(raw: unknown): BookingViolationEvent {
  const item = (raw ?? {}) as Record<string, unknown>;
  const voidInfo = isRecord(item.voidInfo) ? item.voidInfo : {};

  return {
    id: String(item.id ?? item.eventId ?? ''),
    restrictionId: pickString(item, 'restrictionId', 'bookingRestrictionId'),
    patientId: pickString(item, 'patientId'),
    patientFullName: pickString(item, 'patientFullName', 'patientName', 'fullName'),
    patientPhone: pickString(item, 'patientPhone', 'phone'),
    patientEmail: pickString(item, 'patientEmail', 'email'),
    appointmentId: pickString(item, 'appointmentId'),
    appointmentCode: pickString(item, 'appointmentCode', 'appointmentCodeSnapshot', 'code'),
    type: pickString(item, 'type', 'eventType', 'violationType') ?? 'NO_SHOW',
    points: pickNumericField(item, 'points', 'pointDelta', 'scoreDelta', 'amount') ?? 0,
    status: pickString(item, 'status', 'eventStatus'),
    note: pickString(item, 'note', 'reason', 'description'),
    createdAt: pickString(item, 'createdAt', 'occurredAt'),
    createdByName: pickString(item, 'createdByName', 'createdBy', 'actorName'),
    voidedAt: pickString(item, 'voidedAt', 'voidAt') ?? pickString(voidInfo, 'voidedAt', 'voidAt'),
    voidedByName:
      pickString(item, 'voidedByName', 'voidedBy') ??
      pickString(voidInfo, 'voidedByName', 'voidedBy'),
    voidReason:
      pickString(item, 'voidReason', 'voidedReason') ??
      pickString(voidInfo, 'reason', 'voidReason', 'voidedReason'),
    canVoid: pickBoolean(item, 'canVoid'),
  };
}

export function normalizeBookingRestrictionOverride(raw: unknown): BookingRestrictionOverrideResponse {
  const item = (raw ?? {}) as Record<string, unknown>;

  return {
    id: String(item.id ?? item.overrideId ?? ''),
    restrictionId: pickString(item, 'restrictionId', 'bookingRestrictionId'),
    patientId: pickString(item, 'patientId'),
    patientFullName: pickString(item, 'patientFullName', 'patientName', 'fullName'),
    patientPhone: pickString(item, 'patientPhone', 'phone'),
    patientEmail: pickString(item, 'patientEmail', 'email'),
    appointmentId: pickString(item, 'appointmentId'),
    appointmentCode: pickString(item, 'appointmentCode', 'code'),
    status: pickString(item, 'status', 'overrideStatus'),
    reason: pickString(item, 'reason', 'note', 'description'),
    validHours: pickNumericField(item, 'validHours', 'hours'),
    validFrom: pickString(item, 'validFrom', 'startsAt', 'createdAt'),
    validUntil: pickString(item, 'validUntil', 'expiresAt', 'overrideExpiresAt'),
    expiresAt: pickString(item, 'expiresAt', 'validUntil', 'overrideExpiresAt'),
    usedAt: pickString(item, 'usedAt'),
    usedByAppointmentId: pickString(item, 'usedByAppointmentId', 'usedAppointmentId'),
    usedByAppointmentCode: pickString(item, 'usedByAppointmentCode', 'usedAppointmentCode'),
    createdAt: pickString(item, 'createdAt'),
    createdByName: pickString(item, 'createdByName', 'createdBy', 'actorName'),
  };
}

export function normalizeBookingRestriction(raw: unknown): BookingRestriction {
  const item = (raw ?? {}) as Record<string, unknown>;
  const eventsRaw = item.events ?? item.violationEvents ?? item.violation_events;
  const overridesRaw = item.overrides ?? item.overrideHistory ?? item.overrideRecords;

  return {
    id: String(item.id ?? item.restrictionId ?? ''),
    patientId: pickString(item, 'patientId'),
    patientCode: pickString(item, 'patientCode', 'code'),
    patientFullName: pickString(item, 'patientFullName', 'patientName', 'fullName') ?? '',
    patientPhone: pickString(item, 'patientPhone', 'phone'),
    patientEmail: pickString(item, 'patientEmail', 'email'),
    patientDob: pickString(item, 'patientDob', 'dob', 'dateOfBirth'),
    appointmentId: pickString(item, 'appointmentId'),
    appointmentCode: pickString(item, 'appointmentCode', 'appointmentCodeSnapshot', 'code'),
    monthlyScore:
      pickNumericField(
        item,
        'monthlyScore',
        'currentMonthlyScore',
        'currentScore',
        'scoreSnapshot',
        'score',
        'points',
      ) ?? 0,
    level:
      pickString(item, 'level', 'restrictionLevel', 'currentLevel', 'status') ?? 'WARNING',
    status: pickString(item, 'status', 'state'),
    noShowCount:
      pickNumericField(item, 'noShowCount', 'monthlyNoShowCount', 'no_show_count') ?? 0,
    latestViolationAt:
      pickString(item, 'latestViolationAt', 'lastViolationAt', 'lastEventAt'),
    startsAt: pickString(item, 'startsAt', 'startedAt', 'effectiveFrom'),
    expiresAt: pickString(item, 'expiresAt', 'expiredAt', 'effectiveTo'),
    reason: pickString(item, 'reason', 'latestReason', 'description'),
    createdByName: pickString(item, 'createdByName', 'createdBy', 'actorName'),
    createdAt: pickString(item, 'createdAt'),
    liftedAt: pickString(item, 'liftedAt'),
    liftedByName: pickString(item, 'liftedByName', 'liftedBy'),
    liftReason: pickString(item, 'liftReason', 'liftedReason'),
    overrideExpiresAt: pickString(item, 'overrideExpiresAt', 'overrideValidUntil'),
    supportedActions: normalizeStringArray(item.supportedActions ?? item.actions ?? item.availableActions) ?? [],
    canLift: pickBoolean(item, 'canLift'),
    canOverrideOnce: pickBoolean(item, 'canOverrideOnce', 'canOverride'),
    canStaffPardon: pickBoolean(item, 'canStaffPardon', 'supportsStaffPardon'),
    canCreateManualViolation: pickBoolean(item, 'canCreateManualViolation', 'supportsManualViolation'),
    events: Array.isArray(eventsRaw) ? eventsRaw.map(normalizeBookingViolationEvent) : undefined,
    overrides: Array.isArray(overridesRaw) ? overridesRaw.map(normalizeBookingRestrictionOverride) : undefined,
  };
}

export function normalizeBookingRestrictionDetail(raw: unknown): BookingRestrictionDetailResponse {
  const item = (raw ?? {}) as Record<string, unknown>;
  const restrictionSource = isRecord(item.restriction)
    ? ({ ...item, ...item.restriction } as Record<string, unknown>)
    : item;
  const eventsRaw =
    item.events ??
    item.violationEvents ??
    item.patientViolationEvents ??
    restrictionSource.events ??
    restrictionSource.violationEvents;
  const overridesRaw =
    item.overrides ??
    item.overrideHistory ??
    item.overrideRecords ??
    restrictionSource.overrides ??
    restrictionSource.overrideHistory;
  const events = Array.isArray(eventsRaw) ? eventsRaw.map(normalizeBookingViolationEvent) : [];
  const overrides = Array.isArray(overridesRaw)
    ? overridesRaw.map(normalizeBookingRestrictionOverride)
    : [];

  return {
    restriction: {
      ...normalizeBookingRestriction({
        ...restrictionSource,
        events,
        overrides,
      }),
      events,
      overrides,
    },
    events,
    overrides,
  };
}

export function normalizeBookingRestrictionSummary(raw: unknown): BookingRestrictionSummary | null {
  if (!isRecord(raw)) return null;

  const level = pickString(raw, 'level', 'currentLevel', 'restrictionLevel', 'status') ?? 'NONE';
  const status = pickString(raw, 'status', 'restrictionStatus');
  const restricted = pickBoolean(raw, 'restricted', 'hasRestriction', 'isRestricted');
  const clear =
    pickBoolean(raw, 'clear', 'isClear') ??
    (restricted === undefined ? level === 'NONE' || level === 'CLEAR' : !restricted);

  return {
    patientId: pickString(raw, 'patientId'),
    monthlyScore:
      pickNumericField(
        raw,
        'monthlyScore',
        'currentMonthlyScore',
        'currentScore',
        'scoreSnapshot',
        'score',
        'points',
      ) ?? 0,
    level,
    status,
    restricted,
    reason: pickString(raw, 'reason', 'latestReason', 'latestViolationReason') ?? pickString(raw, 'message'),
    expiresAt: pickString(raw, 'expiresAt', 'restrictionExpiresAt'),
    latestViolationType: pickString(raw, 'latestViolationType', 'latestEventType'),
    latestViolationAt: pickString(raw, 'latestViolationAt', 'latestEventAt'),
    message: pickString(raw, 'message'),
    clear,
  };
}

export function normalizeRateLimitRule(raw: unknown): RateLimitRule {
  const item = isRecord(raw) ? raw : {};

  return {
    id: pickString(item, 'id') ?? '',
    code: pickString(item, 'code') ?? '',
    name: pickString(item, 'name') ?? '',
    description: pickString(item, 'description'),
    pathPattern: pickString(item, 'pathPattern', 'path_pattern') ?? '',
    httpMethod: pickString(item, 'httpMethod', 'http_method') ?? '',
    eventType: pickString(item, 'eventType', 'event_type') ?? '',
    limitCount: pickNumericField(item, 'limitCount', 'limit_count') ?? 0,
    windowSeconds: pickNumericField(item, 'windowSeconds', 'window_seconds') ?? 0,
    bucketSeconds: pickNumericField(item, 'bucketSeconds', 'bucket_seconds') ?? 0,
    enabled: pickBoolean(item, 'enabled') ?? false,
    priority: pickNumericField(item, 'priority') ?? 0,
    defaultLimitCount: pickNumericField(item, 'defaultLimitCount', 'default_limit_count') ?? 0,
    defaultWindowSeconds:
      pickNumericField(item, 'defaultWindowSeconds', 'default_window_seconds') ?? 0,
    defaultBucketSeconds:
      pickNumericField(item, 'defaultBucketSeconds', 'default_bucket_seconds') ?? 0,
    defaultEnabled: pickBoolean(item, 'defaultEnabled', 'default_enabled') ?? false,
    createdAt: pickString(item, 'createdAt', 'created_at'),
    updatedAt: pickString(item, 'updatedAt', 'updated_at'),
    updatedBy: pickString(item, 'updatedBy', 'updated_by', 'updatedByName', 'updated_by_name'),
  };
}

function pickNumericField(item: Record<string, unknown>, ...keys: string[]): number | undefined {
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

export function normalizeDoctorAppointmentSummary(raw: unknown): DoctorAppointmentSummary {
  const item = isRecord(raw) && isRecord(raw.summary) ? raw.summary : isRecord(raw) ? raw : {};

  const confirmed = pickNumericField(item, 'confirmed', 'CONFIRMED') ?? 0;
  const checkedIn =
    pickNumericField(item, 'checkedIn', 'checked_in', 'CHECKED_IN') ?? 0;
  const completed =
    pickNumericField(item, 'completedToday', 'completed_today', 'completed', 'COMPLETED', 'done', 'DONE') ?? 0;
  const cancelled = pickNumericField(item, 'cancelled', 'CANCELLED') ?? 0;
  const noShow = pickNumericField(item, 'noShow', 'no_show', 'NO_SHOW') ?? 0;
  const waitingPayment =
    pickNumericField(item, 'waitingPayment', 'waiting_payment', 'WAITING_PAYMENT') ?? 0;
  const waitingResults =
    pickNumericField(item, 'waitingResults', 'waiting_results', 'WAITING_RESULTS') ?? 0;
  const inProgress = pickNumericField(item, 'inProgress', 'in_progress', 'IN_PROGRESS') ?? 0;
  const reopened = pickNumericField(item, 'reopened', 'REOPENED') ?? 0;

  const waitingExam =
    pickNumericField(item, 'waitingExam', 'waiting_exam', 'WAITING_EXAM', 'checkedIn', 'checked_in', 'CHECKED_IN') ?? 0;
  const inCare =
    pickNumericField(item, 'inCare', 'in_care', 'IN_CARE') ?? inProgress + reopened;
  const waitingExternal =
    pickNumericField(item, 'waitingExternal', 'waiting_external', 'WAITING_EXTERNAL') ??
    waitingPayment + waitingResults;
  const needsReturn =
    pickNumericField(item, 'needsReturn', 'needs_return', 'NEEDS_RETURN', 'readyForConclusion', 'ready_for_conclusion', 'READY_FOR_CONCLUSION') ?? 0;
  const statusTotal = confirmed + checkedIn + completed + cancelled + noShow;
  const clinicalTotal = waitingExam + inCare + waitingExternal + needsReturn + completed;

  return {
    total:
      pickNumericField(item, 'totalToday', 'total_today', 'total', 'all', 'totalAppointments', 'total_appointments') ??
      (statusTotal > 0 ? statusTotal : clinicalTotal),
    waitingExam,
    inCare,
    waitingExternal,
    needsReturn,
    done: completed,
    completed,
    checkedIn,
    confirmed,
    cancelled,
    noShow,
  };
}

export function normalizeCashierSummary(raw: unknown): CashierSummary {
  const item = isRecord(raw) && isRecord(raw.summary) ? raw.summary : isRecord(raw) ? raw : {};
  const legacyPaidRevenue =
    pickNumericField(
      item,
      'paidRevenue',
      'paidAmount',
      'totalPaidAmount',
      'revenuePaid',
      'totalRevenue',
      'revenue',
    ) ?? 0;
  const explicitGrossPaidRevenue = pickNumericField(
    item,
    'grossPaidRevenueInRange',
    'gross_paid_revenue_in_range',
    'grossPaidRevenue',
    'gross_paid_revenue',
    'grossRevenue',
    'gross_revenue',
    'totalPaidAmount',
  );
  const refundedAmountForPaidInvoices = pickNumericField(
    item,
    'refundedAmountForPaidInvoicesInRange',
    'refunded_amount_for_paid_invoices_in_range',
    'refundedAmountForPaidInvoices',
    'refunded_amount_for_paid_invoices',
  );
  const refundedAmountInRange = pickNumericField(
    item,
    'refundedAmountInRange',
    'refunded_amount_in_range',
    'refundedAmount',
    'refunded_amount',
    'refundAmount',
    'refund_amount',
    'totalRefundedAmount',
  );
  const providedRefundedAmount = refundedAmountForPaidInvoices ?? refundedAmountInRange;
  const explicitNetPaidRevenue = pickNumericField(
    item,
    'netPaidRevenueInRange',
    'net_paid_revenue_in_range',
    'netPaidRevenue',
    'net_paid_revenue',
    'netRevenue',
    'net_revenue',
    'revenueAfterRefund',
    'revenue_after_refund',
    'revenueAfterRefunds',
  );
  const grossBase = explicitGrossPaidRevenue ?? legacyPaidRevenue;
  const netPaidRevenueInRange =
    explicitNetPaidRevenue ??
    (providedRefundedAmount !== undefined || explicitGrossPaidRevenue !== undefined
      ? Math.max(0, grossBase - (providedRefundedAmount ?? 0))
      : legacyPaidRevenue);
  const grossPaidRevenueInRange =
    explicitGrossPaidRevenue ??
    (providedRefundedAmount !== undefined
      ? netPaidRevenueInRange + providedRefundedAmount
      : legacyPaidRevenue);
  const refundedAmount = providedRefundedAmount ?? Math.max(0, grossPaidRevenueInRange - netPaidRevenueInRange);

  return {
    serviceOrdersWaiting:
      pickNumericField(
        item,
        'serviceOrdersWaiting',
        'waitingServiceOrders',
        'pendingServiceOrders',
        'unInvoicedServiceOrders',
        'notInvoicedServiceOrders',
        'serviceOrdersPendingInvoice',
        'pendingInvoiceServiceOrders',
      ) ?? 0,
    serviceOrdersUnpaidAmount:
      pickNumericField(
        item,
        'serviceOrdersUnpaidAmount',
        'unpaidServiceOrdersAmount',
        'unpaidServiceOrderAmount',
        'serviceOrderUnpaidAmount',
        'estimatedUnpaidAmount',
        'pendingAmount',
        'unpaidAmount',
      ) ?? 0,
    unpaidInvoices:
      pickNumericField(
        item,
        'unpaidInvoices',
        'unpaidInvoiceCount',
        'pendingInvoiceCount',
        'pendingPaymentInvoices',
        'paymentPendingInvoices',
        'invoicesUnpaid',
      ) ?? 0,
    paidRevenue: netPaidRevenueInRange,
    grossPaidRevenueInRange,
    refundedAmountForPaidInvoicesInRange: refundedAmount,
    refundedAmountInRange: refundedAmount,
    netPaidRevenueInRange,
    refundsProcessedInRange: pickNumericField(
      item,
      'refundsProcessedInRange',
      'refunds_processed_in_range',
      'refundsProcessed',
      'refunds_processed',
    ),
  };
}

export function normalizeBranchMasterDataSummary(raw: unknown): BranchMasterDataSummary {
  const item = isRecord(raw) && isRecord(raw.summary) ? raw.summary : isRecord(raw) ? raw : {};

  return {
    total: pickNumericField(item, 'total', 'all', 'totalBranches', 'branchCount') ?? 0,
    activeBranches:
      pickNumericField(item, 'activeBranches', 'active', 'activeCount', 'ACTIVE') ?? 0,
    inactiveBranches:
      pickNumericField(item, 'inactiveBranches', 'inactive', 'inactiveCount', 'INACTIVE') ?? 0,
  };
}

export function normalizeDoctorMasterDataSummary(raw: unknown): DoctorMasterDataSummary {
  const item = isRecord(raw) && isRecord(raw.summary) ? raw.summary : isRecord(raw) ? raw : {};

  return {
    total: pickNumericField(item, 'total', 'all', 'totalDoctors', 'total_doctors', 'doctorCount', 'doctor_count'),
    activeDoctors:
      pickNumericField(item, 'activeDoctors', 'active_doctors', 'active', 'activeCount', 'active_count', 'ACTIVE'),
    inactiveDoctors:
      pickNumericField(item, 'inactiveDoctors', 'inactive_doctors', 'inactive', 'inactiveCount', 'inactive_count', 'INACTIVE'),
    noAccountDoctors:
      pickNumericField(
        item,
        'noAccountDoctors',
        'no_account_doctors',
        'doctorsWithoutAccount',
        'doctors_without_account',
        'withoutAccountDoctors',
        'without_account_doctors',
        'noAccountCount',
        'no_account_count',
        'withoutAccount',
        'without_account',
      ),
    inactiveAccountDoctors:
      pickNumericField(
        item,
        'inactiveAccountDoctors',
        'inactive_account_doctors',
        'inactiveAccountsDoctors',
        'inactive_accounts_doctors',
        'doctorsWithInactiveAccount',
        'doctors_with_inactive_account',
        'doctorInactiveAccounts',
        'doctor_inactive_accounts',
        'inactiveAccountCount',
        'inactive_account_count',
        'inactiveAccounts',
        'inactive_accounts',
      ),
    blockedAccountDoctors:
      pickNumericField(
        item,
        'blockedAccountDoctors',
        'blocked_account_doctors',
        'doctorsWithBlockedAccount',
        'doctors_with_blocked_account',
        'blockedDoctorAccounts',
        'blocked_doctor_accounts',
        'blockedAccountCount',
        'blocked_account_count',
        'blockedAccounts',
        'blocked_accounts',
      ),
    operationalReadyDoctors:
      pickNumericField(
        item,
        'operationalReadyDoctors',
        'operational_ready_doctors',
        'readyDoctors',
        'ready_doctors',
        'readyForOperationDoctors',
        'ready_for_operation_doctors',
        'bookableDoctors',
        'bookable_doctors',
        'operationalReadyCount',
        'operational_ready_count',
      ),
    notOperationalReadyDoctors:
      pickNumericField(
        item,
        'notOperationalReadyDoctors',
        'not_operational_ready_doctors',
        'notReadyDoctors',
        'not_ready_doctors',
        'notReadyForOperationDoctors',
        'not_ready_for_operation_doctors',
        'notBookableDoctors',
        'not_bookable_doctors',
        'notOperationalReadyCount',
        'not_operational_ready_count',
      ),
  };
}

export function normalizeStaffMasterDataSummary(raw: unknown): StaffMasterDataSummary {
  const item = isRecord(raw) && isRecord(raw.summary) ? raw.summary : isRecord(raw) ? raw : {};

  return {
    total: pickNumericField(item, 'total', 'all', 'totalStaffs', 'total_staffs', 'staffCount', 'staff_count'),
    activeStaffs:
      pickNumericField(item, 'activeStaffs', 'active_staffs', 'active', 'activeCount', 'active_count', 'ACTIVE'),
    inactiveStaffs:
      pickNumericField(item, 'inactiveStaffs', 'inactive_staffs', 'inactive', 'inactiveCount', 'inactive_count', 'INACTIVE'),
    noAccountStaffs:
      pickNumericField(
        item,
        'noAccountStaffs',
        'no_account_staffs',
        'staffsWithoutAccount',
        'staffs_without_account',
        'withoutAccountStaffs',
        'without_account_staffs',
        'noAccountCount',
        'no_account_count',
        'withoutAccount',
        'without_account',
      ),
    inactiveAccountStaffs:
      pickNumericField(
        item,
        'inactiveAccountStaffs',
        'inactive_account_staffs',
        'inactiveAccountsStaffs',
        'inactive_accounts_staffs',
        'staffsWithInactiveAccount',
        'staffs_with_inactive_account',
        'staffInactiveAccounts',
        'staff_inactive_accounts',
        'inactiveAccountCount',
        'inactive_account_count',
        'inactiveAccounts',
        'inactive_accounts',
      ),
    blockedAccountStaffs:
      pickNumericField(
        item,
        'blockedAccountStaffs',
        'blocked_account_staffs',
        'staffsWithBlockedAccount',
        'staffs_with_blocked_account',
        'blockedStaffAccounts',
        'blocked_staff_accounts',
        'blockedAccountCount',
        'blocked_account_count',
        'blockedAccounts',
        'blocked_accounts',
      ),
  };
}

export function decodeJwtPayload(token: string): Record<string, unknown> | null {
  try {
    const [, payload] = token.split('.');
    if (!payload) return null;
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
    const decoded = atob(normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '='));
    return JSON.parse(decoded) as Record<string, unknown>;
  } catch {
    return null;
  }
}

export function deriveCurrentUser(accessToken: string, identifier: string): CurrentUser {
  const claims = decodeJwtPayload(accessToken) ?? {};
  const role = String(claims.role ?? 'STAFF') as CurrentUser['role'];
  const subject = String(claims.sub ?? identifier);

  return {
    id: subject,
    email: identifier.includes('@') ? identifier : '',
    fullName: identifier,
    role,
  };
}

function normalizeDashboardRevenueFields(item: Record<string, unknown>) {
  const legacyRevenue = pickNumericField(
    item,
    'totalRevenue',
    'paidRevenue',
    'paid_revenue',
    'revenuePaid',
    'revenue_paid',
    'revenue',
    'paidAmount',
    'paid_amount',
    'totalPaidAmount',
    'value',
    'amount',
  );
  const grossRevenue = pickNumericField(
    item,
    'grossRevenue',
    'gross_revenue',
    'grossPaidRevenue',
    'gross_paid_revenue',
    'grossPaidRevenueInRange',
    'gross_paid_revenue_in_range',
    'grossPaidAmount',
    'gross_paid_amount',
  );
  const refundedAmount = pickNumericField(
    item,
    'refundedAmount',
    'refunded_amount',
    'refundAmount',
    'refund_amount',
    'refundedAmountInRange',
    'refunded_amount_in_range',
    'refundedAmountForPaidInvoicesInRange',
    'refunded_amount_for_paid_invoices_in_range',
    'totalRefundedAmount',
    'total_refunded_amount',
  );
  const explicitNetRevenue = pickNumericField(
    item,
    'netRevenue',
    'net_revenue',
    'netPaidRevenue',
    'net_paid_revenue',
    'netPaidRevenueInRange',
    'net_paid_revenue_in_range',
    'revenueAfterRefund',
    'revenue_after_refund',
    'revenueAfterRefunds',
    'revenue_after_refunds',
  );
  const netRevenue =
    explicitNetRevenue ??
    (grossRevenue !== undefined || refundedAmount !== undefined
      ? Math.max(0, (grossRevenue ?? legacyRevenue ?? 0) - (refundedAmount ?? 0))
      : (legacyRevenue ?? 0));

  return {
    totalRevenue: netRevenue,
    grossRevenue: grossRevenue ?? (refundedAmount !== undefined ? netRevenue + refundedAmount : undefined),
    refundedAmount,
    netRevenue,
    refundsProcessedInRange: pickNumericField(
      item,
      'refundsProcessedInRange',
      'refunds_processed_in_range',
      'refundsProcessed',
      'refunds_processed',
    ),
  };
}

function normalizeDashboardPatientMetric(item: Record<string, unknown>) {
  const uniquePatients = pickNumericField(
    item,
    'uniquePatientsInRange',
    'unique_patients_in_range',
    'totalUniquePatients',
    'total_unique_patients',
    'uniquePatients',
    'unique_patients',
  );

  if (typeof uniquePatients === 'number') {
    return {
      totalPatientsToday: uniquePatients,
      patientMetricLabel: 'Bệnh nhân',
      patientMetricDescription: 'bệnh nhân duy nhất',
      patientMetricSource: 'uniquePatients',
    };
  }

  const explicitPatients = pickNumericField(
    item,
    'totalPatientsToday',
    'total_patients_today',
    'totalPatients',
    'total_patients',
  );

  if (typeof explicitPatients === 'number') {
    return {
      totalPatientsToday: explicitPatients,
      patientMetricLabel: 'Bệnh nhân',
      patientMetricDescription: 'bệnh nhân',
      patientMetricSource: 'totalPatients',
    };
  }

  const totalEncounters = pickNumericField(
    item,
    'totalEncounters',
    'total_encounters',
    'encounters',
    'encounterCount',
    'encounter_count',
  );

  if (typeof totalEncounters === 'number') {
    return {
      totalPatientsToday: totalEncounters,
      patientMetricLabel: 'Lượt khám',
      patientMetricDescription: 'ca khám ghi nhận',
      patientMetricSource: 'totalEncounters',
    };
  }

  const arrivedAppointments = pickNumericField(
    item,
    'arrivedAppointments',
    'arrived_appointments',
    'arrived',
    'arrivedCount',
    'arrived_count',
  ) ?? 0;

  return {
    totalPatientsToday: arrivedAppointments,
    patientMetricLabel: 'Lượt tiếp nhận',
    patientMetricDescription: 'lượt đã đến',
    patientMetricSource: 'arrivedAppointments',
  };
}

function normalizeDashboardValueSeries(value: unknown, revenue = false) {
  if (!Array.isArray(value)) return [];

  return value.map((entry) => {
    const row = isRecord(entry) ? entry : {};
    return {
      date: String(row.date ?? row.label ?? ''),
      value: revenue
        ? normalizeDashboardRevenueFields(row).totalRevenue
        : Number(row.value ?? row.count ?? 0),
    };
  });
}

function normalizeDashboardNameValueList(value: unknown) {
  if (!Array.isArray(value)) return [];

  return value.map((entry) => {
    const row = isRecord(entry) ? entry : {};
    return {
      name: String(row.name ?? row.specialtyName ?? row.label ?? '—'),
      value: normalizeDashboardRevenueFields(row).totalRevenue,
    };
  });
}

function normalizeDashboardStatusCounts(value: unknown): Record<string, number> {
  if (!isRecord(value)) return {};

  return Object.entries(value).reduce<Record<string, number>>((acc, [key, count]) => {
    acc[key] = Number(count ?? 0);
    return acc;
  }, {});
}

export function normalizeDashboardOverview(raw: unknown): DashboardOverview {
  const fallback: DashboardOverview = {
    totalAppointmentsToday: 0,
    totalPatientsToday: 0,
    patientMetricLabel: 'Lượt khám',
    patientMetricDescription: 'ca khám ghi nhận',
    patientMetricSource: 'totalEncounters',
    totalRevenue: 0,
    completionRate: 0,
    arrivedAppointments: 0,
    checkedInAppointments: 0,
    noShowAppointments: 0,
    inProgressEncounters: 0,
    waitingServiceItems: 0,
    appointmentsByStatus: {},
    revenueBySpecialty: [],
    appointmentsTrend: [],
    revenueTrend: [],
    noShowTrend: [],
  };

  if (!isRecord(raw)) {
    return fallback;
  }

  const resolvedRange = normalizeDashboardResolvedRange(raw);

  if ('today' in raw) {
    const today = isRecord(raw.today) ? raw.today : {};
    const appointmentSeries = Array.isArray(raw.appointmentSeries) ? raw.appointmentSeries : [];
    const revenueSeries = Array.isArray(raw.revenueSeries) ? raw.revenueSeries : [];
    const noShowSeries = Array.isArray(raw.noShowSeries) ? raw.noShowSeries : [];
    const revenueFields = normalizeDashboardRevenueFields(today);
    const patientMetric = normalizeDashboardPatientMetric(today);

    return {
      totalAppointmentsToday: Number(today.totalAppointments ?? 0),
      ...patientMetric,
      ...revenueFields,
      arrivedAppointments: Number(today.arrivedAppointments ?? 0),
      checkedInAppointments: Number(today.checkedInAppointments ?? 0),
      noShowAppointments: Number(today.noShowAppointments ?? 0),
      inProgressEncounters: Number(today.inProgressEncounters ?? 0),
      waitingServiceItems: Number(today.waitingServiceItems ?? 0),
      completionRate: Number(today.totalAppointments ?? 0)
        ? Math.round(
            (Number(today.totalEncounters ?? 0) / Number(today.totalAppointments ?? 0)) * 100,
          )
        : 0,
      appointmentsByStatus: {
        REQUESTED: Number(today.requestedAppointments ?? 0),
        CONFIRMED: Number(today.confirmedAppointments ?? 0),
        ARRIVED: Number(today.arrivedAppointments ?? 0),
        CHECKED_IN: Number(today.checkedInAppointments ?? 0),
        COMPLETED: Number(today.completedAppointments ?? 0),
        CANCELLED: Number(today.cancelledAppointments ?? 0),
        NO_SHOW: Number(today.noShowAppointments ?? 0),
      },
      revenueBySpecialty: [],
      appointmentsTrend: normalizeDashboardValueSeries(appointmentSeries).map((item) => ({
        date: item.date,
        count: item.value,
      })),
      revenueTrend: normalizeDashboardValueSeries(revenueSeries, true),
      noShowTrend: normalizeDashboardValueSeries(noShowSeries),
      resolvedRange,
    };
  }

  const revenueFields = normalizeDashboardRevenueFields(raw);
  const patientMetric = normalizeDashboardPatientMetric(raw);

  return {
    totalAppointmentsToday: pickNumericField(raw, 'totalAppointmentsToday', 'totalAppointments', 'appointments') ?? 0,
    ...patientMetric,
    ...revenueFields,
    completionRate: pickNumericField(raw, 'completionRate', 'completion_rate') ?? 0,
    arrivedAppointments: pickNumericField(raw, 'arrivedAppointments', 'arrived_appointments') ?? 0,
    checkedInAppointments: pickNumericField(raw, 'checkedInAppointments', 'checked_in_appointments') ?? 0,
    noShowAppointments: pickNumericField(raw, 'noShowAppointments', 'no_show_appointments') ?? 0,
    inProgressEncounters: pickNumericField(raw, 'inProgressEncounters', 'in_progress_encounters') ?? 0,
    waitingServiceItems: pickNumericField(raw, 'waitingServiceItems', 'waiting_service_items') ?? 0,
    appointmentsByStatus: normalizeDashboardStatusCounts(raw.appointmentsByStatus),
    revenueBySpecialty: normalizeDashboardNameValueList(raw.revenueBySpecialty),
    appointmentsTrend: normalizeDashboardValueSeries(raw.appointmentsTrend).map((item) => ({
      date: item.date,
      count: item.value,
    })),
    revenueTrend: normalizeDashboardValueSeries(raw.revenueTrend, true),
    noShowTrend: normalizeDashboardValueSeries(raw.noShowTrend),
    resolvedRange,
  };
}

function normalizeDashboardResolvedRange(raw: Record<string, unknown>) {
  const candidates = [raw.resolvedRange, raw.dateRange, raw.range, raw];

  for (const candidate of candidates) {
    if (!isRecord(candidate)) continue;

    const period = pickString(candidate, 'period');
    const fromDate = pickString(candidate, 'fromDate', 'from', 'startDate', 'start_date');
    const toDate = pickString(candidate, 'toDate', 'to', 'endDate', 'end_date');

    if (period || fromDate || toDate) {
      return { period, fromDate, toDate };
    }
  }

  return undefined;
}

function normalizeDashboardBreakdownItem(raw: unknown): DashboardBreakdownItem {
  const item = (raw ?? {}) as Record<string, unknown>;
  return {
    id: item.id != null ? String(item.id) : undefined,
    code: typeof item.code === 'string' ? item.code : undefined,
    name: typeof item.name === 'string' && item.name.trim() ? item.name : '—',
    value: Number(item.value ?? 0),
  };
}

export function normalizeDashboardBreakdown(raw: unknown): DashboardBreakdown {
  const item = (raw ?? {}) as Record<string, unknown>;
  const normalizeList = (value: unknown) =>
    Array.isArray(value) ? value.map(normalizeDashboardBreakdownItem) : [];

  return {
    branches: normalizeList(item.branches),
    specialties: normalizeList(item.specialties),
    topDoctors: normalizeList(item.topDoctors),
    topServices: normalizeList(item.topServices),
    resolvedRange: isRecord(raw) ? normalizeDashboardResolvedRange(raw) : undefined,
  };
}

export function normalizeDashboardKpis(raw: unknown): DashboardKpis {
  const item = (raw ?? {}) as Record<string, unknown>;
  const toRecordList = (value: unknown) => (Array.isArray(value) ? value : []);

  return {
    doctorKpis: toRecordList(item.doctorKpis).map((entry) => {
      const row = (entry ?? {}) as Record<string, unknown>;
      return {
        doctorId: row.doctorId != null ? String(row.doctorId) : undefined,
        doctorName: String(row.doctorName ?? '—'),
        totalAppointments: Number(row.totalAppointments ?? 0),
        checkedInAppointments: Number(row.checkedInAppointments ?? 0),
        noShowAppointments: Number(row.noShowAppointments ?? 0),
        fillRate: Number(row.fillRate ?? 0),
        noShowRate: Number(row.noShowRate ?? 0),
      };
    }),
    specialtyKpis: toRecordList(item.specialtyKpis).map((entry) => {
      const row = (entry ?? {}) as Record<string, unknown>;
      return {
        specialtyId: row.specialtyId != null ? String(row.specialtyId) : undefined,
        specialtyName: String(row.specialtyName ?? '—'),
        totalAppointments: Number(row.totalAppointments ?? 0),
        noShowAppointments: Number(row.noShowAppointments ?? 0),
        noShowRate: Number(row.noShowRate ?? 0),
      };
    }),
    branchRevenue: toRecordList(item.branchRevenue).map((entry) => {
      const row = (entry ?? {}) as Record<string, unknown>;
      const revenueFields = normalizeDashboardRevenueFields(row);
      return {
        branchId: row.branchId != null ? String(row.branchId) : undefined,
        branchName: String(row.branchName ?? '—'),
        paidRevenue: revenueFields.totalRevenue,
        grossRevenue: revenueFields.grossRevenue,
        refundedAmount: revenueFields.refundedAmount,
        netRevenue: revenueFields.netRevenue,
      };
    }),
    resolvedRange: isRecord(raw) ? normalizeDashboardResolvedRange(raw) : undefined,
  };
}

export function normalizeBranch(raw: unknown): Branch {
  const item = (raw ?? {}) as Record<string, unknown>;
  return {
    id: String(item.id ?? ''),
    code: typeof item.code === 'string' ? item.code : undefined,
    nameVn: typeof item.nameVn === 'string' ? item.nameVn : undefined,
    nameEn: typeof item.nameEn === 'string' ? item.nameEn : undefined,
    name: pickLocalizedField(item, 'nameVn', 'nameEn', 'name'),
    addressVn: typeof item.addressVn === 'string' ? item.addressVn : undefined,
    addressEn: typeof item.addressEn === 'string' ? item.addressEn : undefined,
    address: pickLocalizedField(item, 'addressVn', 'addressEn', 'address'),
    phone: String(item.phone ?? ''),
    email: String(item.email ?? ''),
    descriptionVn:
      typeof item.descriptionVn === 'string' ? item.descriptionVn : undefined,
    descriptionEn:
      typeof item.descriptionEn === 'string' ? item.descriptionEn : undefined,
    description: pickLocalizedField(item, 'descriptionVn', 'descriptionEn', 'description'),
    imageUrl: sanitizeMediaUrl(item.imageUrl),
    status: String(item.status ?? ''),
    createdAt: String(item.createdAt ?? ''),
  };
}

export function normalizeSpecialty(raw: unknown): Specialty {
  const item = (raw ?? {}) as Record<string, unknown>;
  return {
    id: String(item.id ?? ''),
    code: typeof item.code === 'string' ? item.code : undefined,
    nameVn: typeof item.nameVn === 'string' ? item.nameVn : undefined,
    nameEn: typeof item.nameEn === 'string' ? item.nameEn : undefined,
    name: pickLocalizedField(item, 'nameVn', 'nameEn', 'name'),
    descriptionVn:
      typeof item.descriptionVn === 'string' ? item.descriptionVn : undefined,
    descriptionEn:
      typeof item.descriptionEn === 'string' ? item.descriptionEn : undefined,
    description: pickLocalizedField(item, 'descriptionVn', 'descriptionEn', 'description'),
    iconUrl: sanitizeMediaUrl(item.iconUrl),
    imageUrl: sanitizeMediaUrl(item.imageUrl),
    status: String(item.status ?? ''),
  };
}

function normalizeDoctorPublicSpecialty(raw: unknown): DoctorPublicSpecialty | null {
  if (!isRecord(raw)) return null;

  const id = pickIdField(raw, 'specialtyId', 'specialty_id', 'id');
  if (!id) return null;

  return {
    id,
    code: pickStringField(raw, 'specialtyCode', 'specialty_code', 'code'),
    name: pickLocalizedField(
      raw,
      'specialtyNameVn',
      'specialtyNameEn',
      'nameVn',
      'nameEn',
      'specialtyName',
      'specialty_name',
      'name',
    ),
    status: pickStringField(raw, 'status'),
    effectiveStatus: pickStringField(raw, 'effectiveStatus', 'effective_status'),
    inactiveReason: pickStringField(raw, 'inactiveReason', 'inactive_reason'),
    bookable: pickBooleanField(raw, 'bookable'),
  };
}

function readArrayPayload(value: unknown): unknown[] | undefined {
  if (Array.isArray(value)) return value;
  if (typeof value === 'string' && value.trim()) {
    const parsed = parseJsonString<unknown[]>(value);
    if (Array.isArray(parsed)) return parsed;
  }
  return undefined;
}

function normalizeDoctorPublicSpecialties(item: Record<string, unknown>) {
  const publicSpecialtiesPayload = readArrayPayload(
    pickJsonValue(
      item,
      'publicSpecialties',
      'public_specialties',
      'bookableSpecialties',
      'bookable_specialties',
      'publicSpecialtyOptions',
      'public_specialty_options',
    ),
  );

  if (publicSpecialtiesPayload) {
    return publicSpecialtiesPayload
      .map(normalizeDoctorPublicSpecialty)
      .filter((specialty): specialty is DoctorPublicSpecialty => Boolean(specialty));
  }

  const genericSpecialtiesPayload = readArrayPayload(
    pickJsonValue(item, 'specialties', 'doctorSpecialties', 'doctor_specialties'),
  );
  const genericRecords = genericSpecialtiesPayload?.filter(isRecord) ?? [];
  if (!genericRecords.length) return undefined;

  return genericRecords
    .map(normalizeDoctorPublicSpecialty)
    .filter((specialty): specialty is DoctorPublicSpecialty => Boolean(specialty));
}

export function normalizeDoctor(raw: unknown): Doctor {
  const item = (raw ?? {}) as Record<string, unknown>;
  const publicSpecialties = normalizeDoctorPublicSpecialties(item);
  const rawLegacySpecialtyIds = item.specialtyIds ?? item.specialty_ids;
  const legacySpecialtyIds = Array.isArray(rawLegacySpecialtyIds)
    ? rawLegacySpecialtyIds
        .map((value) => (value !== null && typeof value !== 'undefined' ? String(value).trim() : ''))
        .filter(Boolean)
    : normalizeStringArray(rawLegacySpecialtyIds) ?? [];
  const fallbackSpecialtyId = pickStringField(item, 'specialtyId', 'specialty_id');
  const specialtyIds =
    publicSpecialties !== undefined
      ? publicSpecialties.map((specialty) => specialty.id)
      : legacySpecialtyIds.length
        ? legacySpecialtyIds
        : fallbackSpecialtyId
          ? [fallbackSpecialtyId]
          : [];
  const supportedLanguages = normalizeStringArray(item.supportedLanguages ?? item.supportedLanguagesJson);
  const featuredServices = normalizeStringArray(item.featuredServices ?? item.featuredServicesJson);
  const careerTimeline = normalizeCareerTimeline(item.careerTimeline ?? item.careerTimelineJson);
  const hasAccount = pickBooleanField(item, 'hasAccount', 'has_account');
  const accountEmail = typeof item.accountEmail === 'string' ? item.accountEmail : undefined;
  const accountPhone = typeof item.accountPhone === 'string' ? item.accountPhone : undefined;
  const accountStatus = pickStringField(item, 'accountStatus', 'account_status');

  return {
    id: String(item.id ?? ''),
    code: typeof item.code === 'string' ? item.code : undefined,
    fullName: String(item.fullName ?? ''),
    displayTitleVn:
      typeof item.displayTitleVn === 'string' ? item.displayTitleVn : undefined,
    displayTitleEn:
      typeof item.displayTitleEn === 'string' ? item.displayTitleEn : undefined,
    title: pickLocalizedField(item, 'displayTitleVn', 'displayTitleEn', 'title'),
    avatarUrl: sanitizeMediaUrl(item.avatarUrl),
    bioVn: typeof item.bioVn === 'string' ? item.bioVn : undefined,
    bioEn: typeof item.bioEn === 'string' ? item.bioEn : undefined,
    bio: pickLocalizedField(item, 'bioVn', 'bioEn', 'bio'),
    expertiseVn:
      typeof item.expertiseVn === 'string' ? item.expertiseVn : undefined,
    expertiseEn:
      typeof item.expertiseEn === 'string' ? item.expertiseEn : undefined,
    expertise: pickLocalizedField(item, 'expertiseVn', 'expertiseEn', 'expertise'),
    yearsOfExperience: Number(item.yearsExp ?? item.yearsOfExperience ?? 0),
    specialtyId: specialtyIds[0] ?? '',
    specialtyIds,
    specialtyName:
      publicSpecialties?.find((specialty) => specialty.name)?.name ??
      pickLocalizedField(item, 'specialtyNameVn', 'specialtyNameEn', 'specialtyName'),
    publicSpecialties,
    branchId: String(item.branchId ?? ''),
    branchName: pickLocalizedField(item, 'branchNameVn', 'branchNameEn', 'branchName'),
    educationVn:
      typeof item.educationVn === 'string' ? item.educationVn : undefined,
    educationEn:
      typeof item.educationEn === 'string' ? item.educationEn : undefined,
    education: pickLocalizedField(item, 'educationVn', 'educationEn', 'education'),
    achievementsVn:
      typeof item.achievementsVn === 'string' ? item.achievementsVn : undefined,
    achievementsEn:
      typeof item.achievementsEn === 'string' ? item.achievementsEn : undefined,
    achievements: pickLocalizedField(item, 'achievementsVn', 'achievementsEn', 'achievements'),
    status: String(item.status ?? 'ACTIVE'),
    effectiveStatus:
      typeof item.effectiveStatus === 'string' ? item.effectiveStatus : undefined,
    inactiveReason:
      typeof item.inactiveReason === 'string' ? item.inactiveReason : undefined,
    operationalReady: pickBooleanField(item, 'operationalReady', 'operational_ready'),
    bookable: pickBooleanField(item, 'bookable'),
    notReadyReason: pickStringField(item, 'notReadyReason', 'not_ready_reason'),
    nextAvailableDate:
      typeof item.nextAvailableDate === 'string' ? item.nextAvailableDate : undefined,
    hasUpcomingSchedule:
      typeof item.hasUpcomingSchedule === 'boolean' ? item.hasUpcomingSchedule : undefined,
    ratingAverage:
      typeof item.ratingAverage === 'number'
        ? item.ratingAverage
        : typeof item.ratingAverage === 'string'
          ? Number(item.ratingAverage)
          : undefined,
    reviewCount:
      typeof item.reviewCount === 'number'
        ? item.reviewCount
        : typeof item.reviewCount === 'string'
          ? Number(item.reviewCount)
          : undefined,
    supportedLanguages,
    featuredServices,
    careerTimeline,
    updatedAt: typeof item.updatedAt === 'string' ? item.updatedAt : undefined,
    hasAccount,
    accountId:
      typeof item.accountId !== 'undefined' && item.accountId !== null
        ? String(item.accountId)
        : undefined,
    accountEmail,
    accountPhone,
    accountRole:
      typeof item.accountRole === 'string' ? item.accountRole : undefined,
    accountStatus,
  };
}

export function normalizeDoctorOption(raw: unknown): DoctorOption {
  const item = (raw ?? {}) as Record<string, unknown>;
  const rawSpecialtyIds = normalizeStringArray(item.specialtyIds ?? item.specialty_ids);
  const rawSpecialtyNames = normalizeStringArray(
    item.specialtyNames ?? item.specialty_names ?? item.specialties,
  );
  const fallbackSpecialtyId = pickStringField(item, 'specialtyId', 'specialty_id');
  const fallbackSpecialtyName = pickStringField(
    item,
    'specialtyName',
    'specialty_name',
    'specialtyNameVn',
    'specialtyNameEn',
  );

  return {
    id: String(item.id ?? item.doctorId ?? item.doctor_id ?? ''),
    fullName: String(item.fullName ?? item.doctorName ?? item.name ?? ''),
    branchId: pickStringField(item, 'branchId', 'branch_id'),
    branchName: pickStringField(item, 'branchName', 'branch_name', 'branchNameVn', 'branchNameEn'),
    specialtyIds: rawSpecialtyIds ?? (fallbackSpecialtyId ? [fallbackSpecialtyId] : undefined),
    specialtyNames: rawSpecialtyNames ?? (fallbackSpecialtyName ? [fallbackSpecialtyName] : undefined),
    profileStatus: pickStringField(item, 'profileStatus', 'profile_status', 'status'),
    hasAccount: pickBooleanField(item, 'hasAccount', 'has_account'),
    accountStatus: pickStringField(item, 'accountStatus', 'account_status'),
    operationalReady: pickBooleanField(item, 'operationalReady', 'operational_ready'),
    bookable: pickBooleanField(item, 'bookable'),
    notReadyReason: pickStringField(item, 'notReadyReason', 'not_ready_reason'),
  };
}

export function normalizeMedicalService(raw: unknown): MedicalService {
  const item = (raw ?? {}) as Record<string, unknown>;
  return {
    id: String(item.id ?? ''),
    code: typeof item.code === 'string' ? item.code : undefined,
    nameVn: typeof item.nameVn === 'string' ? item.nameVn : undefined,
    nameEn: typeof item.nameEn === 'string' ? item.nameEn : undefined,
    name: pickLocalizedField(item, 'nameVn', 'nameEn', 'name'),
    descriptionVn:
      typeof item.descriptionVn === 'string' ? item.descriptionVn : undefined,
    descriptionEn:
      typeof item.descriptionEn === 'string' ? item.descriptionEn : undefined,
    description: pickLocalizedField(item, 'descriptionVn', 'descriptionEn', 'description'),
    price: Number(item.basePrice ?? item.price ?? 0),
    groupName: String(item.serviceType ?? item.groupName ?? ''),
    serviceType: typeof item.serviceType === 'string' ? item.serviceType : undefined,
    departmentCode:
      typeof item.departmentCode === 'string' ? item.departmentCode : undefined,
    status: String(item.status ?? ''),
    publicVisible:
      typeof item.publicVisible === 'boolean' ? item.publicVisible : undefined,
    displayOrder:
      typeof item.displayOrder === 'number' ? item.displayOrder : undefined,
    thumbnailUrl: typeof item.thumbnailUrl === 'string' ? item.thumbnailUrl : undefined,
    defaultTurnaroundMinutes:
      typeof item.defaultTurnaroundMinutes === 'number'
        ? item.defaultTurnaroundMinutes
        : undefined,
    requiresFileResult:
      typeof item.requiresFileResult === 'boolean' ? item.requiresFileResult : undefined,
    requiresNumericResult:
      typeof item.requiresNumericResult === 'boolean' ? item.requiresNumericResult : undefined,
    resultTemplateCode:
      typeof item.resultTemplateCode === 'string' ? (item.resultTemplateCode as ServiceResultTemplateCode) : undefined,
    resultTemplateSchemaJson:
      typeof item.resultTemplateSchemaJson === 'string'
        ? item.resultTemplateSchemaJson
        : undefined,
    resultReportTitle:
      typeof item.resultReportTitle === 'string' ? item.resultReportTitle : undefined,
  };
}

export function normalizeAvailability(raw: unknown): AvailabilitySlot[] {
  const item = isRecord(raw) ? raw : {};
  const slots = Array.isArray(raw)
    ? raw
    : Array.isArray(item.slots)
      ? item.slots
      : Array.isArray(item.items)
        ? item.items
        : [];

  return slots.map((slot) => {
    const row = isRecord(slot) ? slot : {};
    const explicitRemaining = row.remainingSlots ?? row.remaining ?? row.availableSlots;
    const capacity =
      typeof row.capacity === 'number'
        ? row.capacity
        : typeof row.capacity === 'string'
          ? Number(row.capacity)
          : undefined;
    const bookedCount =
      typeof row.bookedCount === 'number'
        ? row.bookedCount
        : typeof row.bookedCount === 'string'
          ? Number(row.bookedCount)
          : undefined;
    const remaining =
      typeof explicitRemaining === 'number'
        ? explicitRemaining
        : typeof explicitRemaining === 'string'
          ? Number(explicitRemaining)
          : typeof capacity === 'number' && typeof bookedCount === 'number'
            ? Math.max(0, capacity - bookedCount)
            : row.available === true
              ? 1
              : 0;

    return {
      id: String(row.id ?? row.startTime ?? row.slotStart ?? ''),
      doctorId: String(row.doctorId ?? item.doctorId ?? ''),
      doctorName:
        pickLocalizedField(row, 'doctorNameVn', 'doctorNameEn', 'doctorName') ||
        pickLocalizedField(item, 'doctorNameVn', 'doctorNameEn', 'doctorName'),
      branchId: String(row.branchId ?? item.branchId ?? ''),
      branchName:
        pickLocalizedField(row, 'branchNameVn', 'branchNameEn', 'branchName') ||
        pickLocalizedField(item, 'branchNameVn', 'branchNameEn', 'branchName'),
      specialtyId: String(row.specialtyId ?? item.specialtyId ?? ''),
      specialtyName:
        pickLocalizedField(row, 'specialtyNameVn', 'specialtyNameEn', 'specialtyName') ||
        pickLocalizedField(item, 'specialtyNameVn', 'specialtyNameEn', 'specialtyName'),
      visitDate: String(row.visitDate ?? item.visitDate ?? ''),
      session: String(row.session ?? item.session ?? 'AM') as AvailabilitySlot['session'],
      slotStart: formatTimeDisplay(row.startTime ?? row.slotStart ?? ''),
      slotEnd: formatTimeDisplay(row.endTime ?? row.slotEnd ?? ''),
      remainingSlots: remaining,
      capacity,
      bookedCount,
      status:
        remaining <= 0
          ? 'FULL'
          : typeof capacity === 'number' && capacity > 0 && remaining / capacity <= 0.3
            ? 'ALMOST_FULL'
            : 'AVAILABLE',
    };
  });
}

export function normalizePublicFaq(raw: unknown): PublicFaqItem {
  const item = (raw ?? {}) as Record<string, unknown>;
  return {
    id: String(item.id ?? ''),
    category: typeof item.category === 'string' ? item.category : undefined,
    questionVn: typeof item.questionVn === 'string' ? item.questionVn : undefined,
    questionEn: typeof item.questionEn === 'string' ? item.questionEn : undefined,
    answerVn: typeof item.answerVn === 'string' ? item.answerVn : undefined,
    answerEn: typeof item.answerEn === 'string' ? item.answerEn : undefined,
  };
}

export function normalizeAppointment(raw: unknown): Appointment {
  const item = (raw ?? {}) as Record<string, unknown>;
  const etaStart = pickString(item, 'etaStart', 'eta_start');
  const etaEnd = pickString(item, 'etaEnd', 'eta_end');

  return {
    id: String(item.id ?? ''),
    code: String(item.code ?? ''),
    patientFullName: String(item.patientFullName ?? ''),
    patientPhone: String(item.patientPhone ?? ''),
    patientEmail: String(item.patientEmail ?? ''),
    patientDob: String(item.patientDob ?? ''),
    patientGender: String(item.patientGender ?? ''),
    patientNote: typeof item.patientNote === 'string' ? item.patientNote : undefined,
    reasonForVisit: typeof item.reasonForVisit === 'string' ? item.reasonForVisit : undefined,
    visitType: typeof item.visitType === 'string' ? item.visitType : undefined,
    preTriageLevel: normalizeOptionalEnum(item.preTriageLevel, PRE_TRIAGE_LEVELS),
    preTriagePriority: normalizeOptionalEnum(item.preTriagePriority, TRIAGE_PRIORITIES),
    preTriageFlags: normalizeStringArray(item.preTriageFlags ?? item.pre_triage_flags) ?? null,
    preTriageReasons:
      normalizeStringArray(item.preTriageReasons ?? item.pre_triage_reasons) ?? null,
    preTriageSummary:
      typeof item.preTriageSummary === 'string'
        ? item.preTriageSummary
        : typeof item.pre_triage_summary === 'string'
          ? item.pre_triage_summary
          : null,
    preTriageAssessedAt:
      typeof item.preTriageAssessedAt === 'string'
        ? item.preTriageAssessedAt
        : typeof item.pre_triage_assessed_at === 'string'
          ? item.pre_triage_assessed_at
          : null,
    symptomOnset: normalizeOptionalEnum(item.symptomOnset, SYMPTOM_ONSETS),
    chronicConditions: normalizeEnumArray(item.chronicConditions, CHRONIC_CONDITIONS),
    chronicConditionOthers:
      normalizeStringArray(item.chronicConditionOthers ?? item.chronic_condition_others) ?? [],
    functionalImpact: normalizeOptionalEnum(item.functionalImpact, FUNCTIONAL_IMPACTS),
    redFlagSelections:
      normalizeEnumArray(item.redFlagSelections ?? item.redFlags, RED_FLAGS),
    preTriageMatchedTerms:
      normalizeMatchedTerms(item.preTriageMatchedTerms ?? item.pre_triage_matched_terms),
    preTriageMatchedRules:
      normalizeMatchedRules(item.preTriageMatchedRules ?? item.pre_triage_matched_rules),
    preTriageSource:
      normalizeOptionalEnum(item.preTriageSource ?? item.pre_triage_source, PRE_TRIAGE_SOURCES) ??
      (typeof item.preTriageSource === 'string'
        ? item.preTriageSource
        : typeof item.pre_triage_source === 'string'
          ? item.pre_triage_source
          : null),
    preTriageConfidenceLevel:
      normalizeOptionalEnum(
        item.preTriageConfidenceLevel ?? item.pre_triage_confidence_level,
        CONFIDENCE_LEVELS,
      ) ??
      (typeof item.preTriageConfidenceLevel === 'string'
        ? item.preTriageConfidenceLevel
        : typeof item.pre_triage_confidence_level === 'string'
          ? item.pre_triage_confidence_level
          : null),
    preTriageKnowledgeBaseVersion:
      typeof item.preTriageKnowledgeBaseVersion === 'string'
        ? item.preTriageKnowledgeBaseVersion
        : typeof item.pre_triage_knowledge_base_version === 'string'
          ? item.pre_triage_knowledge_base_version
          : null,
    preTriageRulesetVersion:
      typeof item.preTriageRulesetVersion === 'string'
        ? item.preTriageRulesetVersion
        : typeof item.pre_triage_ruleset_version === 'string'
          ? item.pre_triage_ruleset_version
          : null,
    preTriageAiModelVersion:
      typeof item.preTriageAiModelVersion === 'string'
        ? item.preTriageAiModelVersion
        : typeof item.pre_triage_ai_model_version === 'string'
          ? item.pre_triage_ai_model_version
          : null,
    triagePriority: typeof item.triagePriority === 'string' ? item.triagePriority : null,
    triageNote: typeof item.triageNote === 'string' ? item.triageNote : null,
    triageReviewStatus: normalizeOptionalEnum(
      item.triageReviewStatus ?? item.triage_review_status,
      TRIAGE_REVIEW_STATUSES,
    ),
    triageOverrideReason:
      typeof item.triageOverrideReason === 'string'
        ? item.triageOverrideReason
        : typeof item.triage_override_reason === 'string'
          ? item.triage_override_reason
          : null,
    triageReviewedByName:
      typeof item.triageReviewedByName === 'string'
        ? item.triageReviewedByName
        : typeof item.triage_reviewed_by_name === 'string'
          ? item.triage_reviewed_by_name
          : null,
    triageReviewedAt:
      typeof item.triageReviewedAt === 'string'
        ? item.triageReviewedAt
        : typeof item.triage_reviewed_at === 'string'
          ? item.triage_reviewed_at
          : null,
    triageAuditLogs: normalizeTriageAuditLogs(item.triageAuditLogs ?? item.triage_audit_logs),
    insuranceNote: typeof item.insuranceNote === 'string' ? item.insuranceNote : undefined,
    emergencyContactName:
      typeof item.emergencyContactName === 'string' ? item.emergencyContactName : undefined,
    emergencyContactPhone:
      typeof item.emergencyContactPhone === 'string' ? item.emergencyContactPhone : undefined,
    heightCm: typeof item.heightCm === 'number' ? item.heightCm : undefined,
    weightKg: typeof item.weightKg === 'number' ? item.weightKg : undefined,
    temperatureC: typeof item.temperatureC === 'number' ? item.temperatureC : undefined,
    pulse: typeof item.pulse === 'number' ? item.pulse : undefined,
    systolicBp: typeof item.systolicBp === 'number' ? item.systolicBp : undefined,
    diastolicBp: typeof item.diastolicBp === 'number' ? item.diastolicBp : undefined,
    respiratoryRate: typeof item.respiratoryRate === 'number' ? item.respiratoryRate : undefined,
    spo2: typeof item.spo2 === 'number' ? item.spo2 : undefined,
    intakeCompletedAt:
      typeof item.intakeCompletedAt === 'string' ? item.intakeCompletedAt : undefined,
    intakeCompletedByName:
      typeof item.intakeCompletedByName === 'string' ? item.intakeCompletedByName : undefined,
    bookingRestrictionSummary: normalizeBookingRestrictionSummary(
      item.bookingRestrictionSummary ??
      item.bookingRiskSummary ??
      item.restrictionSummary ??
      item.bookingRestriction,
    ),
    contactStatus: pickString(item, 'contactStatus', 'phoneContactStatus'),
    patientResponseStatus: pickString(item, 'patientResponseStatus', 'responseStatus'),
    lastCallOutcome: pickString(item, 'lastCallOutcome', 'callOutcome'),
    lastCallAt: pickString(item, 'lastCallAt', 'lastCalledAt', 'callOutcomeAt'),
    lastCallNote: pickString(item, 'lastCallNote', 'callNote'),
    lastCallByName: pickString(item, 'lastCallByName', 'lastCalledByName', 'callOutcomeByName'),
    lastPatientResponseAt: pickString(item, 'lastPatientResponseAt', 'patientRespondedAt'),
    fallbackEmailSentAt: pickString(item, 'fallbackEmailSentAt', 'contactFallbackEmailSentAt'),
    canRecordCallOutcome: pickBoolean(item, 'canRecordCallOutcome', 'canRecordCallResult'),
    canSendFallbackEmail: pickBoolean(item, 'canSendFallbackEmail', 'canSendContactFallbackEmail'),
    canMarkWrongContact: pickBoolean(item, 'canMarkWrongContact', 'canCreateWrongContactViolation'),
    contactSupportedActions:
      normalizeStringArray(item.contactSupportedActions ?? item.contactActions ?? item.supportedContactActions) ??
      undefined,
    doctorId: String(item.doctorId ?? ''),
    doctorName: pickLocalizedField(item, 'doctorNameVn', 'doctorNameEn', 'doctorName'),
    branchId: String(item.branchId ?? ''),
    branchName: pickLocalizedField(item, 'branchNameVn', 'branchNameEn', 'branchName'),
    specialtyId: String(item.specialtyId ?? ''),
    specialtyName: pickLocalizedField(item, 'specialtyNameVn', 'specialtyNameEn', 'specialtyName'),
    visitDate: String(item.visitDate ?? ''),
    session: normalizeScheduleSession(item.session),
    slotStart: formatTimeDisplay(etaStart ?? item.slotStart ?? ''),
    slotEnd: formatTimeDisplay(etaEnd ?? item.slotEnd ?? item.endTime) || undefined,
    etaStart,
    etaEnd,
    status: normalizeAppointmentStatus(item.status),
    claimedBy: typeof item.processingByName === 'string' ? item.processingByName : undefined,
    processingById:
      typeof item.processingById === 'number' || typeof item.processingById === 'string'
        ? String(item.processingById)
        : undefined,
    processingStartedAt:
      typeof item.processingStartedAt === 'string' ? item.processingStartedAt : undefined,
    processingExpiresAt:
      typeof item.processingExpiresAt === 'string' ? item.processingExpiresAt : undefined,
    checkInToken: typeof item.qrToken === 'string' ? item.qrToken : undefined,
    queueNo: typeof item.queueNo === 'number' ? item.queueNo : undefined,
    slotMinutes: typeof item.slotMinutes === 'number' ? item.slotMinutes : undefined,
    sourceType: typeof item.sourceType === 'string' ? item.sourceType : undefined,
    arrivalStatus:
      typeof item.arrivalStatus === 'string'
        ? (item.arrivalStatus as 'NOT_ARRIVED' | 'ARRIVED')
        : undefined,
    receptionQueueNo:
      typeof item.receptionQueueNo === 'number' ? item.receptionQueueNo : undefined,
    arrivedAt: typeof item.arrivedAt === 'string' ? item.arrivedAt : undefined,
    arrivedByName: typeof item.arrivedByName === 'string' ? item.arrivedByName : undefined,
    checkedInAt: typeof item.checkedInAt === 'string' ? item.checkedInAt : undefined,
    checkedInByName:
      typeof item.checkedInByName === 'string' ? item.checkedInByName : undefined,
    checkedInLate: pickBooleanField(item, 'checkedInLate', 'checked_in_late'),
    lateMinutes: pickNumberField(item, 'lateMinutes', 'late_minutes'),
    confirmedAt: typeof item.confirmedAt === 'string' ? item.confirmedAt : undefined,
    confirmedByName:
      typeof item.confirmedByName === 'string' ? item.confirmedByName : undefined,
    noShowMarkedAt:
      typeof item.noShowMarkedAt === 'string' ? item.noShowMarkedAt : undefined,
    noShowMarkedByName:
      typeof item.noShowMarkedByName === 'string' ? item.noShowMarkedByName : undefined,
    followUpPending: Boolean(item.followUpPending),
    followUpType: pickString(item, 'followUpType', 'follow_up_type') ?? null,
    doctorCancellationHoldStatus:
      pickString(item, 'doctorCancellationHoldStatus', 'holdStatus', 'hold_status') ?? null,
    doctorCancellationHoldExpiresAt:
      pickString(
        item,
        'doctorCancellationHoldExpiresAt',
        'doctor_cancellation_hold_expires_at',
        'holdExpiresAt',
        'expiresAt',
      ) ?? null,
    doctorCancellationHeldSlotStart:
      formatTimeDisplay(
        item.doctorCancellationHeldSlotStart ?? item.heldSlotStart ?? item.proposedSlotStart,
      ) || null,
    doctorCancellationHeldSlotEnd:
      formatTimeDisplay(
        item.doctorCancellationHeldSlotEnd ?? item.heldSlotEnd ?? item.proposedSlotEnd,
      ) || null,
    doctorCancellationHeldDoctorName:
      pickString(item, 'doctorCancellationHeldDoctorName', 'heldDoctorName', 'proposedDoctorName') ??
      null,
    doctorCancellationOriginalSlotStart:
      formatTimeDisplay(
        item.doctorCancellationOriginalSlotStart ?? item.originalSlotStart ?? item.slotStart,
      ) || null,
    doctorCancellationOriginalSlotEnd:
      formatTimeDisplay(item.doctorCancellationOriginalSlotEnd ?? item.originalSlotEnd ?? item.slotEnd) ||
      null,
    noShowNote: typeof item.noShowNote === 'string' ? item.noShowNote : undefined,
    canMarkNoShow:
      typeof item.canMarkNoShow === 'boolean' ? item.canMarkNoShow : undefined,
    noShowGraceMinutes: pickNumberField(item, 'noShowGraceMinutes', 'no_show_grace_minutes'),
    noShowEligibleAt:
      typeof item.noShowEligibleAt === 'string' ? item.noShowEligibleAt : undefined,
    noShowBlockedReason:
      typeof item.noShowBlockedReason === 'string' ? item.noShowBlockedReason : undefined,
    rescheduledFromAppointmentId:
      typeof item.rescheduledFromAppointmentId !== 'undefined' &&
      item.rescheduledFromAppointmentId !== null
        ? String(item.rescheduledFromAppointmentId)
        : undefined,
    rescheduledToAppointmentId:
      typeof item.rescheduledToAppointmentId !== 'undefined' &&
      item.rescheduledToAppointmentId !== null
        ? String(item.rescheduledToAppointmentId)
        : undefined,
    overdue: typeof item.overdue === 'boolean' ? item.overdue : undefined,
    autoCancelled:
      typeof item.autoCancelled === 'boolean'
        ? item.autoCancelled
        : typeof item.cancelledBySystem === 'boolean'
          ? item.cancelledBySystem
          : undefined,
    cancellationReason:
      typeof item.cancellationReason === 'string'
        ? item.cancellationReason
        : typeof item.cancelReason === 'string'
          ? item.cancelReason
          : undefined,
    cancelledAt: typeof item.cancelledAt === 'string' ? item.cancelledAt : undefined,
    cancelledByName:
      typeof item.cancelledByName === 'string' ? item.cancelledByName : undefined,
    createdAt: String(item.createdAt ?? ''),
    updatedAt: String(item.updatedAt ?? ''),
    activeEncounterId:
      typeof item.activeEncounterId !== 'undefined' && item.activeEncounterId !== null
        ? String(item.activeEncounterId)
        : undefined,
    activeEncounterCode:
      typeof item.activeEncounterCode === 'string' ? item.activeEncounterCode : undefined,
    activeEncounterStatus: normalizeOptionalEncounterStatus(item.activeEncounterStatus),
    serviceOrderCount: Number(item.serviceOrderCount ?? 0),
    pendingPaymentOrderCount: Number(item.pendingPaymentOrderCount ?? 0),
    waitingResultItemCount: Number(item.waitingResultItemCount ?? 0),
    completedResultItemCount: Number(item.completedResultItemCount ?? 0),
    issuedPrescriptionCount: Number(item.issuedPrescriptionCount ?? 0),
    hasPendingPayment: Boolean(item.hasPendingPayment),
    hasWaitingResults: Boolean(item.hasWaitingResults),
    readyForConclusion: Boolean(item.readyForConclusion),
    canCreatePrescription:
      typeof item.canCreatePrescription === 'boolean'
        ? item.canCreatePrescription
        : undefined,
    canComplete: typeof item.canComplete === 'boolean' ? item.canComplete : undefined,
  };
}

export function normalizeStaff(raw: unknown): Staff {
  const item = (raw ?? {}) as Record<string, unknown>;
  const hasAccount = Boolean(item.hasAccount ?? false);
  const accountEmail = typeof item.accountEmail === 'string' ? item.accountEmail : undefined;
  const accountPhone = typeof item.accountPhone === 'string' ? item.accountPhone : undefined;
  const accountStatus = typeof item.accountStatus === 'string' ? item.accountStatus : undefined;
  return {
    id: String(item.id ?? ''),
    fullName: String(item.fullName ?? ''),
    email: accountEmail ?? (typeof item.email === 'string' ? item.email : undefined),
    phone: accountPhone ?? (typeof item.phone === 'string' ? item.phone : undefined),
    role: String(item.role ?? item.accountRole ?? 'STAFF') as Staff['role'],
    branchId: typeof item.branchId !== 'undefined' ? String(item.branchId) : undefined,
    branchName: pickLocalizedField(item, 'branchNameVn', 'branchNameEn', 'branchName'),
    status: String(item.status ?? (hasAccount ? 'ACTIVE' : 'INACTIVE')),
    avatarUrl: sanitizeMediaUrl(item.avatarUrl),
    hasAccount,
    accountId: typeof item.accountId !== 'undefined' ? String(item.accountId) : undefined,
    accountEmail,
    accountPhone,
    accountRole: typeof item.accountRole === 'string' ? (item.accountRole as Staff['accountRole']) : undefined,
    accountStatus,
  };
}

export function normalizeBranchSpecialty(raw: unknown) {
  const item = (raw ?? {}) as Record<string, unknown>;

  return {
    id: String(item.id ?? ''),
    branchId: String(item.branchId ?? ''),
    branchName: pickLocalizedField(item, 'branchNameVn', 'branchNameEn', 'branchName'),
    specialtyId: String(item.specialtyId ?? ''),
    specialtyName: pickLocalizedField(item, 'specialtyNameVn', 'specialtyNameEn', 'specialtyName'),
    specialtyCode:
      typeof item.specialtyCode === 'string' ? item.specialtyCode : undefined,
    status: String(item.status ?? ''),
    effectiveStatus:
      typeof item.effectiveStatus === 'string' ? item.effectiveStatus : undefined,
    inactiveReason:
      typeof item.inactiveReason === 'string' ? item.inactiveReason : undefined,
    bookable:
      typeof item.bookable === 'boolean' ? item.bookable : undefined,
    displayOrder:
      typeof item.displayOrder === 'number' ? item.displayOrder : undefined,
    consultationFee:
      typeof item.consultationFee === 'number' ? item.consultationFee : undefined,
    slotMinutesOverride:
      typeof item.slotMinutesOverride === 'number'
        ? item.slotMinutesOverride
        : undefined,
    note: typeof item.note === 'string' ? item.note : undefined,
  };
}


export function normalizeAuditLog(raw: unknown): AuditLog {
  const item = (raw ?? {}) as Record<string, unknown>;
  const actorId = pickString(item, 'actorId', 'actor_id', 'userId', 'user_id');
  const actorEmail = pickString(item, 'actorEmail', 'actor_email', 'email');
  const actorName = pickString(item, 'actorName', 'actor_name', 'actorFullName', 'userName');
  const entity = pickString(item, 'entity', 'entityType', 'entity_type', 'targetType', 'target_type') ?? '';
  const entityId = pickString(item, 'entityId', 'entity_id', 'targetId', 'target_id') ?? '';

  return {
    id: String(item.id ?? item.eventId ?? item.event_id ?? ''),
    eventId: pickString(item, 'eventId', 'event_id'),
    actor: pickString(item, 'actor') ?? actorName ?? actorEmail ?? actorId ?? '',
    actorId,
    actorName,
    actorEmail,
    actorRole: pickString(item, 'actorRole', 'actor_role', 'role'),
    action: String(item.action ?? ''),
    entity,
    entityType: entity,
    entityId,
    targetType: (pickString(item, 'targetType', 'target_type') ?? entity) || undefined,
    targetId: (pickString(item, 'targetId', 'target_id') ?? entityId) || undefined,
    description: typeof item.description === 'string' ? item.description : undefined,
    metadata:
      item.metadata && typeof item.metadata === 'object'
        ? (item.metadata as Record<string, unknown>)
        : undefined,
    beforeJson: pickJsonValue(item, 'beforeJson', 'before_json', 'before'),
    afterJson: pickJsonValue(item, 'afterJson', 'after_json', 'after'),
    ipAddress: pickString(item, 'ipAddress', 'ip_address'),
    userAgent: pickString(item, 'userAgent', 'user_agent'),
    createdAt: pickString(item, 'createdAt', 'created_at') ?? '',
  };
}

export function normalizePatient(raw: unknown) {
  const item = (raw ?? {}) as Record<string, unknown>;
  const code =
    typeof item.patientCode === 'string'
      ? item.patientCode
      : typeof item.code === 'string'
        ? item.code
        : undefined;
  return {
    id: String(item.id ?? ''),
    code,
    patientCode: code,
    fullName: String(item.fullName ?? item.patientFullName ?? ''),
    phone: String(item.phone ?? item.patientPhone ?? ''),
    email:
      typeof item.email === 'string'
        ? item.email
        : typeof item.patientEmail === 'string'
          ? item.patientEmail
          : undefined,
    dob:
      typeof item.dob === 'string'
        ? item.dob
        : typeof item.patientDob === 'string'
          ? item.patientDob
          : undefined,
    gender:
      typeof item.gender === 'string'
        ? item.gender
        : typeof item.patientGender === 'string'
          ? item.patientGender
          : undefined,
    address: typeof item.address === 'string' ? item.address : undefined,
    bloodType: typeof item.bloodType === 'string' ? item.bloodType : undefined,
    ethnicity: typeof item.ethnicity === 'string' ? item.ethnicity : undefined,
    nationality: typeof item.nationality === 'string' ? item.nationality : undefined,
    occupation: typeof item.occupation === 'string' ? item.occupation : undefined,
    province: typeof item.province === 'string' ? item.province : undefined,
    district: typeof item.district === 'string' ? item.district : undefined,
    ward: typeof item.ward === 'string' ? item.ward : undefined,
    avatarUrl: sanitizeMediaUrl(item.avatarUrl),
    identityNumber:
      typeof item.identityNumber === 'string' ? item.identityNumber : undefined,
    insuranceNumber:
      typeof item.insuranceNumber === 'string'
        ? item.insuranceNumber
        : undefined,
    insuranceExpiryDate:
      typeof item.insuranceExpiryDate === 'string'
        ? item.insuranceExpiryDate
        : undefined,
    insuranceRegisteredHospital:
      typeof item.insuranceRegisteredHospital === 'string'
        ? item.insuranceRegisteredHospital
        : undefined,
    emergencyContactName:
      typeof item.emergencyContactName === 'string'
        ? item.emergencyContactName
        : undefined,
    emergencyContactPhone:
      typeof item.emergencyContactPhone === 'string'
        ? item.emergencyContactPhone
        : undefined,
    allergyNote:
      typeof item.allergyNote === 'string' ? item.allergyNote : undefined,
    allergies: Array.isArray(item.allergies) ? item.allergies : undefined,
    chronicDiseaseNote:
      typeof item.chronicDiseaseNote === 'string'
        ? item.chronicDiseaseNote
        : undefined,
    note: typeof item.note === 'string' ? item.note : undefined,
    status: typeof item.status === 'string' ? item.status : undefined,
    createdAt: String(item.createdAt ?? ''),
    updatedAt: typeof item.updatedAt === 'string' ? item.updatedAt : undefined,
  };
}

export function normalizeDoctorSchedule(raw: unknown) {
  const item = (raw ?? {}) as Record<string, unknown>;
  return {
    id: String(item.id ?? ''),
    doctorId: String(item.doctorId ?? ''),
    doctorName: pickLocalizedField(item, 'doctorNameVn', 'doctorNameEn', 'doctorName'),
    branchId:
      typeof item.branchId !== 'undefined' ? String(item.branchId) : undefined,
    branchName: pickLocalizedField(item, 'branchNameVn', 'branchNameEn', 'branchName'),
    workDate: String(item.workDate ?? ''),
    session: normalizeScheduleSession(item.session),
    startTime: typeof item.startTime === 'string' ? item.startTime : undefined,
    endTime: typeof item.endTime === 'string' ? item.endTime : undefined,
    note: typeof item.note === 'string' ? item.note : undefined,
  };
}

export function normalizeLeaveRequest(raw: unknown) {
  const item = (raw ?? {}) as Record<string, unknown>;
  return {
    id: String(item.id ?? ''),
    doctorId: String(item.doctorId ?? ''),
    doctorName: pickLocalizedField(item, 'doctorNameVn', 'doctorNameEn', 'doctorName'),
    startDate: String(item.startDate ?? ''),
    endDate: String(item.endDate ?? ''),
    startSession:
      typeof item.startSession === 'string'
        ? normalizeScheduleSession(item.startSession)
        : undefined,
    endSession:
      typeof item.endSession === 'string'
        ? normalizeScheduleSession(item.endSession)
        : undefined,
    reason: String(item.reason ?? ''),
    status: String(item.status ?? 'PENDING') as
      | 'PENDING'
      | 'APPROVED'
      | 'REJECTED'
      | 'CANCELLED',
    reviewNote: typeof item.reviewNote === 'string' ? item.reviewNote : undefined,
    reviewedAt:
      typeof item.reviewedAt === 'string' ? item.reviewedAt : undefined,
    reviewedByName:
      typeof item.reviewedByName === 'string'
        ? item.reviewedByName
        : undefined,
    createdAt: typeof item.createdAt === 'string' ? item.createdAt : undefined,
    updatedAt: typeof item.updatedAt === 'string' ? item.updatedAt : undefined,
    affectedAppointmentCount: pickNumberField(
      item,
      'affectedAppointmentCount',
      'affectedAppointments',
    ),
    recoverySummary: normalizeRecoverySummary(item.recoverySummary ?? item.doctorCancellationRecoverySummary),
  };
}

export function normalizeMedication(raw: unknown) {
  const item = (raw ?? {}) as Record<string, unknown>;
  return {
    id: String(item.id ?? ''),
    code: typeof item.code === 'string' ? item.code : undefined,
    name: String(item.name ?? ''),
    genericName:
      typeof item.genericName === 'string' ? item.genericName : undefined,
    strength: typeof item.strength === 'string' ? item.strength : undefined,
    dosageForm: String(item.dosageForm ?? ''),
    unit: String(item.unit ?? ''),
    manufacturer:
      typeof item.manufacturer === 'string' ? item.manufacturer : undefined,
    indicationNote:
      typeof item.indicationNote === 'string' ? item.indicationNote : undefined,
    contraindicationNote:
      typeof item.contraindicationNote === 'string'
        ? item.contraindicationNote
        : undefined,
    status: String(item.status ?? 'ACTIVE'),
  };
}

function pickIdField(item: Record<string, unknown>, ...keys: string[]): string | undefined {
  for (const key of keys) {
    const value = item[key];
    if (value !== null && typeof value !== 'undefined' && String(value).trim()) {
      return String(value);
    }
  }
  return undefined;
}

function pickStringField(item: Record<string, unknown>, ...keys: string[]): string | undefined {
  for (const key of keys) {
    const value = item[key];
    if (typeof value === 'string' && value.trim()) {
      return value;
    }
  }
  return undefined;
}

function pickNumberField(item: Record<string, unknown>, ...keys: string[]): number | undefined {
  for (const key of keys) {
    const value = item[key];
    if (typeof value === 'number' && Number.isFinite(value)) {
      return value;
    }
    if (typeof value === 'string' && value.trim()) {
      const parsed = Number(value);
      if (Number.isFinite(parsed)) return parsed;
    }
  }
  return undefined;
}

function pickBooleanField(item: Record<string, unknown>, ...keys: string[]): boolean | undefined {
  for (const key of keys) {
    const value = item[key];
    if (typeof value === 'boolean') return value;
    if (typeof value === 'string') {
      const normalized = value.trim().toLowerCase();
      if (normalized === 'true') return true;
      if (normalized === 'false') return false;
    }
  }
  return undefined;
}

function isExpiredDate(value?: string): boolean {
  if (!value) return false;
  const timestamp = Date.parse(value);
  if (Number.isNaN(timestamp)) return false;
  const expiryDate = new Date(timestamp);
  expiryDate.setHours(0, 0, 0, 0);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return expiryDate.getTime() < today.getTime();
}

function normalizeInventoryMedicationFields(item: Record<string, unknown>) {
  const medication = isRecord(item.medication) ? item.medication : {};

  return {
    medicationId:
      pickIdField(item, 'medicationId', 'medication_id') ??
      pickIdField(medication, 'id', 'medicationId', 'medication_id') ??
      '',
    medicationCode:
      pickStringField(item, 'medicationCode', 'medication_code', 'code', 'medication_code_snapshot') ??
      pickStringField(medication, 'code', 'medicationCode', 'medication_code'),
    medicationName:
      pickStringField(
        item,
        'medicationName',
        'medication_name',
        'medicationNameVn',
        'medication_name_vn',
        'nameVn',
        'name_vn',
        'name',
      ) ??
      pickStringField(medication, 'name', 'nameVn', 'name_vn', 'medicationName', 'medication_name') ??
      '—',
    unit:
      pickStringField(item, 'unit', 'unitName', 'unit_name') ??
      pickStringField(medication, 'unit', 'unitName', 'unit_name'),
  };
}

export function normalizePharmacyInventoryItem(raw: unknown): PharmacyInventoryItem {
  const item = (raw ?? {}) as Record<string, unknown>;
  const medicationFields = normalizeInventoryMedicationFields(item);

  return {
    ...medicationFields,
    medicationId:
      medicationFields.medicationId ||
      pickIdField(item, 'id', 'medicationId', 'medication_id') ||
      '',
    totalQuantity:
      pickNumberField(
        item,
        'totalQuantity',
        'total_quantity',
        'quantity',
        'stockQuantity',
        'stock_quantity',
        'availableQuantity',
        'available_quantity',
      ) ?? 0,
    lowStock: pickBooleanField(item, 'lowStock', 'low_stock', 'isLowStock', 'is_low_stock'),
    status: pickStringField(item, 'status', 'inventoryStatus', 'inventory_status'),
  };
}

export function normalizeMedicationBatch(raw: unknown): MedicationBatch {
  const item = (raw ?? {}) as Record<string, unknown>;
  const medicationFields = normalizeInventoryMedicationFields(item);
  const batchId = pickIdField(item, 'batchId', 'batch_id', 'id') ?? '';

  return {
    id: batchId,
    batchId,
    batchNumber: pickStringField(item, 'batchNumber', 'batch_number'),
    batchCode:
      pickStringField(item, 'batchNumber', 'batch_number', 'batchCode', 'batch_code', 'code') ??
      batchId,
    ...medicationFields,
    quantity:
      pickNumberField(
        item,
        'quantity',
        'remainingQuantity',
        'remaining_quantity',
        'stockQuantity',
        'stock_quantity',
        'availableQuantity',
        'available_quantity',
      ) ?? 0,
    expiryDate: pickStringField(
      item,
      'expiryDate',
      'expiry_date',
      'expirationDate',
      'expiration_date',
      'expireAt',
      'expire_at',
    ),
    status: pickStringField(item, 'status', 'batchStatus', 'batch_status'),
  };
}

export function normalizeExpiringBatch(raw: unknown): ExpiringBatch {
  const item = (raw ?? {}) as Record<string, unknown>;
  const batch = normalizeMedicationBatch(item);

  return {
    ...batch,
    daysUntilExpiry: pickNumberField(item, 'daysUntilExpiry', 'days_until_expiry', 'daysLeft', 'days_left'),
    expired:
      pickBooleanField(item, 'expired', 'isExpired', 'is_expired') ??
      (batch.status === 'EXPIRED' || isExpiredDate(batch.expiryDate)),
  };
}

export function normalizeAdminSpecialty(raw: unknown) {
  const item = (raw ?? {}) as Record<string, unknown>;
  return {
    id: String(item.id ?? ''),
    code: typeof item.code === 'string' ? item.code : undefined,
    nameVn: typeof item.nameVn === 'string' ? item.nameVn : undefined,
    nameEn: typeof item.nameEn === 'string' ? item.nameEn : undefined,
    name: pickLocalizedField(item, 'nameVn', 'nameEn', 'name'),
    descriptionVn:
      typeof item.descriptionVn === 'string' ? item.descriptionVn : undefined,
    descriptionEn:
      typeof item.descriptionEn === 'string' ? item.descriptionEn : undefined,
    description: pickLocalizedField(item, 'descriptionVn', 'descriptionEn', 'description'),
    iconUrl: sanitizeMediaUrl(item.iconUrl),
    status: String(item.status ?? 'ACTIVE'),
    defaultSlotMinutes:
      typeof item.defaultSlotMinutes === 'number'
        ? item.defaultSlotMinutes
        : undefined,
    maxPerSession:
      typeof item.maxPerSession === 'number' ? item.maxPerSession : undefined,
  };
}

export function normalizeEncounter(raw: unknown) {
  const item = (raw ?? {}) as Record<string, unknown>;
  return {
    id: String(item.id ?? ''),
    code: typeof item.code === 'string' ? item.code : undefined,
    appointmentId: String(item.appointmentId ?? ''),
    patientId: typeof item.patientId !== 'undefined' ? String(item.patientId) : undefined,
    patientName: String(item.patientFullName ?? item.patientName ?? ''),
    patientPhone: typeof item.patientPhone === 'string' ? item.patientPhone : undefined,
    patientEmail: typeof item.patientEmail === 'string' ? item.patientEmail : undefined,
    patientDob: typeof item.patientDob === 'string' ? item.patientDob : undefined,
    patientGender: typeof item.patientGender === 'string' ? item.patientGender : undefined,
    intakeReasonForVisit:
      typeof item.intakeReasonForVisit === 'string' ? item.intakeReasonForVisit : undefined,
    visitType: typeof item.visitType === 'string' ? item.visitType : undefined,
    triagePriority: typeof item.triagePriority === 'string' ? item.triagePriority : undefined,
    triageNote: typeof item.triageNote === 'string' ? item.triageNote : undefined,
    insuranceNote: typeof item.insuranceNote === 'string' ? item.insuranceNote : undefined,
    emergencyContactName:
      typeof item.emergencyContactName === 'string' ? item.emergencyContactName : undefined,
    emergencyContactPhone:
      typeof item.emergencyContactPhone === 'string' ? item.emergencyContactPhone : undefined,
    intakeCompletedAt:
      typeof item.intakeCompletedAt === 'string' ? item.intakeCompletedAt : undefined,
    intakeCompletedByName:
      typeof item.intakeCompletedByName === 'string' ? item.intakeCompletedByName : undefined,
    doctorId: typeof item.doctorId !== 'undefined' ? String(item.doctorId) : undefined,
    doctorName: pickLocalizedField(item, 'doctorNameVn', 'doctorNameEn', 'doctorName'),
    branchId: typeof item.branchId !== 'undefined' ? String(item.branchId) : undefined,
    branchName: pickLocalizedField(item, 'branchNameVn', 'branchNameEn', 'branchName'),
    specialtyId:
      typeof item.specialtyId !== 'undefined' ? String(item.specialtyId) : undefined,
    specialtyName: pickLocalizedField(item, 'specialtyNameVn', 'specialtyNameEn', 'specialtyName'),
    chiefComplaint:
      typeof item.chiefComplaint === 'string' ? item.chiefComplaint : undefined,
    clinicalNote: typeof item.clinicalNote === 'string' ? item.clinicalNote : undefined,
    preliminaryDiagnosis:
      typeof item.preliminaryDiagnosis === 'string'
        ? item.preliminaryDiagnosis
        : undefined,
    finalDiagnosis:
      typeof item.finalDiagnosis === 'string' ? item.finalDiagnosis : undefined,
    diagnoses: Array.isArray(item.diagnoses)
      ? item.diagnoses.map(normalizeEncounterDiagnosis)
      : [],
    conclusion: typeof item.conclusion === 'string' ? item.conclusion : undefined,
    heightCm: typeof item.heightCm === 'number' ? item.heightCm : undefined,
    weightKg: typeof item.weightKg === 'number' ? item.weightKg : undefined,
    temperatureC:
      typeof item.temperatureC === 'number' ? item.temperatureC : undefined,
    pulse: typeof item.pulse === 'number' ? item.pulse : undefined,
    systolicBp: typeof item.systolicBp === 'number' ? item.systolicBp : undefined,
    diastolicBp: typeof item.diastolicBp === 'number' ? item.diastolicBp : undefined,
    respiratoryRate:
      typeof item.respiratoryRate === 'number' ? item.respiratoryRate : undefined,
    spo2: typeof item.spo2 === 'number' ? item.spo2 : undefined,
    allergySnapshot:
      typeof item.allergySnapshot === 'string' ? item.allergySnapshot : undefined,
    chronicDiseaseSnapshot:
      typeof item.chronicDiseaseSnapshot === 'string'
        ? item.chronicDiseaseSnapshot
        : undefined,
    pastMedicalHistory:
      typeof item.pastMedicalHistory === 'string'
        ? item.pastMedicalHistory
        : undefined,
    physicalExamination:
      typeof item.physicalExamination === 'string'
        ? item.physicalExamination
        : undefined,
    treatmentPlan:
      typeof item.treatmentPlan === 'string' ? item.treatmentPlan : undefined,
    followUpDate: typeof item.followUpDate === 'string' ? item.followUpDate : undefined,
    followUpNote: typeof item.followUpNote === 'string' ? item.followUpNote : undefined,
    status: normalizeEncounterStatus(item.status),
    createdAt: typeof item.createdAt === 'string' ? item.createdAt : undefined,
    updatedAt: typeof item.updatedAt === 'string' ? item.updatedAt : undefined,
    startedAt: typeof item.startedAt === 'string' ? item.startedAt : undefined,
    completedAt: typeof item.completedAt === 'string' ? item.completedAt : undefined,
    serviceOrderCount:
      typeof item.serviceOrderCount === 'number' ? item.serviceOrderCount : 0,
    pendingPaymentOrderCount:
      typeof item.pendingPaymentOrderCount === 'number'
        ? item.pendingPaymentOrderCount
        : 0,
    activeServiceOrderCount:
      typeof item.activeServiceOrderCount === 'number'
        ? item.activeServiceOrderCount
        : 0,
    waitingResultItemCount:
      typeof item.waitingResultItemCount === 'number'
        ? item.waitingResultItemCount
        : 0,
    completedResultItemCount:
      typeof item.completedResultItemCount === 'number'
        ? item.completedResultItemCount
        : 0,
    issuedPrescriptionCount:
      typeof item.issuedPrescriptionCount === 'number'
        ? item.issuedPrescriptionCount
        : 0,
    hasPendingPayment: Boolean(item.hasPendingPayment),
    hasWaitingResults: Boolean(item.hasWaitingResults),
    readyForConclusion: Boolean(item.readyForConclusion),
    canCreatePrescription:
      typeof item.canCreatePrescription === 'boolean'
        ? item.canCreatePrescription
        : true,
    canComplete:
      typeof item.canComplete === 'boolean' ? item.canComplete : false,
  };
}

export function normalizePrescription(raw: unknown) {
  const item = (raw ?? {}) as Record<string, unknown>;
  const rawItems = Array.isArray(item.items) ? item.items : [];
  return {
    id: String(item.id ?? ''),
    code: typeof item.code === 'string' ? item.code : undefined,
    encounterId: String(item.encounterId ?? ''),
    encounterCode: typeof item.encounterCode === 'string' ? item.encounterCode : undefined,
    doctorUserId:
      typeof item.doctorUserId !== 'undefined' ? String(item.doctorUserId) : undefined,
    patientName: pickStringField(item, 'patientName', 'patient_name', 'patientFullName', 'patient_full_name'),
    doctorName: pickStringField(item, 'doctorName', 'doctor_name', 'doctorFullName', 'doctor_full_name'),
    issuedDate: typeof item.issuedDate === 'string' ? item.issuedDate : undefined,
    generalNote: typeof item.generalNote === 'string' ? item.generalNote : undefined,
    status: normalizePrescriptionStatus(item.status),
    invoiceId:
      typeof item.invoiceId !== 'undefined' && item.invoiceId !== null
        ? String(item.invoiceId)
        : undefined,
    invoiceCode:
      typeof item.invoiceCode === 'string' ? item.invoiceCode : undefined,
    paymentUrl:
      pickStringField(item, 'paymentUrl', 'payment_url', 'vnpPaymentUrl', 'vnp_payment_url'),
    paymentRoute:
      pickStringField(item, 'paymentRoute', 'payment_route', 'route', 'actionUrl', 'action_url'),
    canDispense: typeof item.canDispense === 'boolean' ? item.canDispense : undefined,
    items: rawItems.map((x) => {
      const row = (x ?? {}) as Record<string, unknown>;
      return {
        id: typeof row.id !== 'undefined' ? String(row.id) : undefined,
        medicationId: String(row.medicationId ?? ''),
        medicationName: String(row.medicationName ?? ''),
        medicationCode:
          typeof row.medicationCode === 'string' ? row.medicationCode : undefined,
        strength: typeof row.strength === 'string' ? row.strength : undefined,
        dosageForm: typeof row.dosageForm === 'string' ? row.dosageForm : undefined,
        unit: typeof row.unit === 'string' ? row.unit : undefined,
        quantity: Number(row.quantity ?? 0),
        dose: String(row.dose ?? ''),
        frequency: String(row.frequency ?? ''),
        durationDays: Number(row.durationDays ?? 0),
        route: String(row.route ?? ''),
        instruction: typeof row.instruction === 'string' ? row.instruction : undefined,
        status: pickStringField(row, 'status', 'itemStatus', 'item_status'),
        ...normalizeRefundFields(row),
      };
    }),
    refundedAmount: pickNumberField(item, 'refundedAmount', 'refunded_amount', 'refundAmount', 'refund_amount'),
    remainingAmount: pickNumberField(item, 'remainingAmount', 'remaining_amount'),
    createdAt: typeof item.createdAt === 'string' ? item.createdAt : undefined,
  };
}


export function normalizeServiceOrder(raw: unknown): ServiceOrder {
  const item = (raw ?? {}) as Record<string, unknown>;
  const rawItems = Array.isArray(item.items) ? item.items : [];

  return {
    id: String(item.id ?? ''),
    code: String(item.code ?? ''),
    encounterId: String(item.encounterId ?? ''),
    status: typeof item.status === 'string' ? item.status : undefined,
    paymentStatus:
      typeof item.paymentStatus === 'undefined'
        ? undefined
        : normalizePaymentStatus(item.paymentStatus),
    estimatedTotalAmount:
      typeof item.estimatedTotalAmount === 'number' ? item.estimatedTotalAmount : undefined,
    note: typeof item.note === 'string' ? item.note : undefined,
    orderedAt: typeof item.orderedAt === 'string' ? item.orderedAt : undefined,
    paidAt: typeof item.paidAt === 'string' ? item.paidAt : undefined,
    createdAt: typeof item.createdAt === 'string' ? item.createdAt : undefined,
    updatedAt: typeof item.updatedAt === 'string' ? item.updatedAt : undefined,
    items: rawItems.map((x) => {
      const row = (x ?? {}) as Record<string, unknown>;
      return {
        id: String(row.id ?? ''),
        medicalServiceId: String(row.medicalServiceId ?? ''),
        serviceCode: typeof row.serviceCode === 'string' ? row.serviceCode : undefined,
        serviceNameVn: typeof row.serviceNameVn === 'string' ? row.serviceNameVn : undefined,
        serviceNameEn: typeof row.serviceNameEn === 'string' ? row.serviceNameEn : undefined,
        price: typeof row.price === 'number' ? row.price : undefined,
        quantity: Number(row.quantity ?? 0),
        lineTotalAmount:
          typeof row.lineTotalAmount === 'number' ? row.lineTotalAmount : undefined,
        departmentCode:
          typeof row.departmentCode === 'string' ? row.departmentCode : undefined,
        queueNo: typeof row.queueNo === 'number' ? row.queueNo : undefined,
        status: typeof row.status === 'string' ? row.status : undefined,
        resultStatus: typeof row.resultStatus === 'string' ? row.resultStatus : undefined,
        note: typeof row.note === 'string' ? row.note : undefined,
        ...normalizeServiceOrderItemResultFields(row),
        resultPerformedAt:
          typeof row.resultPerformedAt === 'string' ? row.resultPerformedAt : undefined,
        ...normalizeRefundFields(row),
      };
    }),
  };
}

export function normalizeCashierServiceOrder(raw: unknown) {
  const item = (raw ?? {}) as Record<string, unknown>;
  const rawItems = Array.isArray(item.items) ? item.items : [];
  return {
    id: String(item.id ?? ''),
    code: String(item.code ?? ''),
    encounterId:
      typeof item.encounterId !== 'undefined' ? String(item.encounterId) : undefined,
    patientName: typeof item.patientName === 'string' ? item.patientName : undefined,
    doctorName: typeof item.doctorName === 'string' ? item.doctorName : undefined,
    branchName: typeof item.branchName === 'string' ? item.branchName : undefined,
    status: typeof item.status === 'string' ? item.status : undefined,
    paymentStatus:
      typeof item.paymentStatus === 'undefined'
        ? undefined
        : normalizePaymentStatus(item.paymentStatus),
    invoiced: Boolean(item.invoiced),
    invoiceId: typeof item.invoiceId !== 'undefined' ? String(item.invoiceId) : undefined,
    invoiceCode: typeof item.invoiceCode === 'string' ? item.invoiceCode : undefined,
    estimatedTotalAmount:
      typeof item.estimatedTotalAmount === 'number'
        ? item.estimatedTotalAmount
        : undefined,
    note: typeof item.note === 'string' ? item.note : undefined,
    orderedAt: typeof item.orderedAt === 'string' ? item.orderedAt : undefined,
    paidAt: typeof item.paidAt === 'string' ? item.paidAt : undefined,
    createdAt: typeof item.createdAt === 'string' ? item.createdAt : undefined,
    updatedAt: typeof item.updatedAt === 'string' ? item.updatedAt : undefined,
    items: rawItems.map((x) => {
      const row = (x ?? {}) as Record<string, unknown>;
      return {
        id: String(row.id ?? ''),
        medicalServiceId: String(row.medicalServiceId ?? ''),
        serviceCode:
          typeof row.serviceCode === 'string' ? row.serviceCode : undefined,
        serviceNameVn:
          typeof row.serviceNameVn === 'string' ? row.serviceNameVn : undefined,
        serviceNameEn:
          typeof row.serviceNameEn === 'string' ? row.serviceNameEn : undefined,
        price: typeof row.price === 'number' ? row.price : undefined,
        quantity: Number(row.quantity ?? 0),
        lineTotalAmount:
          typeof row.lineTotalAmount === 'number' ? row.lineTotalAmount : undefined,
        departmentCode:
          typeof row.departmentCode === 'string' ? row.departmentCode : undefined,
        queueNo: typeof row.queueNo === 'number' ? row.queueNo : undefined,
        status: typeof row.status === 'string' ? row.status : undefined,
        resultStatus:
          typeof row.resultStatus === 'string' ? row.resultStatus : undefined,
        ...normalizeRefundFields(row),
      };
    }),
  };
}

export function normalizeServiceDeskQueueItem(raw: unknown) {
  const item = (raw ?? {}) as Record<string, unknown>;
  return {
    itemId: String(item.itemId ?? ''),
    serviceOrderId:
      typeof item.serviceOrderId !== 'undefined' ? String(item.serviceOrderId) : undefined,
    serviceOrderCode:
      typeof item.serviceOrderCode === 'string' ? item.serviceOrderCode : undefined,
    encounterId: typeof item.encounterId !== 'undefined' ? String(item.encounterId) : undefined,
    encounterCode:
      typeof item.encounterCode === 'string' ? item.encounterCode : undefined,
    appointmentCode:
      typeof item.appointmentCode === 'string' ? item.appointmentCode : undefined,
    patientName: typeof item.patientName === 'string' ? item.patientName : undefined,
    doctorName: typeof item.doctorName === 'string' ? item.doctorName : undefined,
    branchName: typeof item.branchName === 'string' ? item.branchName : undefined,
    departmentCode:
      typeof item.departmentCode === 'string' ? item.departmentCode : undefined,
    serviceCode: typeof item.serviceCode === 'string' ? item.serviceCode : undefined,
    serviceNameVn:
      typeof item.serviceNameVn === 'string' ? item.serviceNameVn : undefined,
    serviceNameEn:
      typeof item.serviceNameEn === 'string' ? item.serviceNameEn : undefined,
    queueNo: typeof item.queueNo === 'number' ? item.queueNo : undefined,
    itemStatus: typeof item.itemStatus === 'string' ? item.itemStatus : undefined,
    resultStatus: typeof item.resultStatus === 'string' ? item.resultStatus : undefined,
    queuedAt: typeof item.queuedAt === 'string' ? item.queuedAt : undefined,
    startedAt: typeof item.startedAt === 'string' ? item.startedAt : undefined,
    completedAt: typeof item.completedAt === 'string' ? item.completedAt : undefined,
    resultId: typeof item.resultId !== 'undefined' ? String(item.resultId) : undefined,
    ...normalizeServiceOrderItemResultFields(item),
    performedAt: typeof item.performedAt === 'string' ? item.performedAt : undefined,
    performedByName:
      typeof item.performedByName === 'string' ? item.performedByName : undefined,
    verifiedAt: typeof item.verifiedAt === 'string' ? item.verifiedAt : undefined,
    verifiedByName:
      typeof item.verifiedByName === 'string' ? item.verifiedByName : undefined,
    ...normalizeRefundFields(item),
  };
}

export function normalizeInvoiceItem(raw: unknown): InvoiceItemResponse {
  const item = (raw ?? {}) as Record<string, unknown>;
  return {
    id: Number(item.id ?? item.invoiceItemId ?? item.invoice_item_id ?? 0),
    itemType: pickStringField(item, 'itemType', 'item_type', 'type'),
    referenceType: pickStringField(item, 'referenceType', 'reference_type'),
    nameSnapshot: pickStringField(item, 'nameSnapshot', 'name_snapshot', 'name', 'itemName', 'item_name'),
    unitPrice: pickNumberField(item, 'unitPrice', 'unit_price', 'price'),
    quantity: pickNumberField(item, 'quantity'),
    taxRate: pickNumberField(item, 'taxRate', 'tax_rate'),
    subtotalAmount: pickNumberField(item, 'subtotalAmount', 'subtotal_amount', 'subTotalAmount', 'sub_total_amount'),
    taxAmount: pickNumberField(item, 'taxAmount', 'tax_amount'),
    totalAmount: pickNumberField(item, 'totalAmount', 'total_amount', 'amount'),
    status: pickStringField(item, 'status'),
    itemStatus: pickStringField(item, 'itemStatus', 'item_status'),
    refundable: pickBooleanField(item, 'refundable', 'canRefund', 'can_refund', 'isRefundable', 'is_refundable'),
    refundableAmount: pickNumberField(
      item,
      'refundableAmount',
      'refundable_amount',
      'availableRefundAmount',
      'available_refund_amount',
    ),
    ...normalizeRefundFields(item),
  };
}

export function normalizeRefundableInvoiceItem(raw: unknown): RefundableInvoiceItem {
  const item = normalizeInvoiceItem(raw);
  return {
    ...item,
    refundable: Boolean(item.refundable),
  };
}

export function normalizeInvoice(raw: unknown) {
  const item = (raw ?? {}) as Record<string, unknown>;
  return {
    id: String(item.id ?? ''),
    code: typeof item.code === 'string' ? item.code : undefined,
    serviceOrderId: String(item.serviceOrderId ?? ''),
    serviceOrderCode:
      typeof item.serviceOrderCode === 'string' ? item.serviceOrderCode : undefined,
    invoiceType: pickStringField(item, 'invoiceType', 'invoice_type', 'billingType', 'billing_type'),
    type: pickStringField(item, 'type'),
    referenceType: pickStringField(item, 'referenceType', 'reference_type'),
    prescriptionId:
      typeof item.prescriptionId !== 'undefined' && item.prescriptionId !== null
        ? String(item.prescriptionId)
        : undefined,
    prescriptionCode:
      typeof item.prescriptionCode === 'string' ? item.prescriptionCode : undefined,
    encounterId:
      typeof item.encounterId !== 'undefined' ? String(item.encounterId) : undefined,
    patientName: typeof item.patientName === 'string' ? item.patientName : undefined,
    doctorName: typeof item.doctorName === 'string' ? item.doctorName : undefined,
    branchName: typeof item.branchName === 'string' ? item.branchName : undefined,
    subtotalAmount:
      typeof item.subtotalAmount === 'number' ? item.subtotalAmount : undefined,
    discountAmount:
      typeof item.discountAmount === 'number' ? item.discountAmount : undefined,
    taxAmount: typeof item.taxAmount === 'number' ? item.taxAmount : undefined,
    totalAmount: Number(item.totalAmount ?? 0),
    refundedAmount: pickNumberField(item, 'refundedAmount', 'refunded_amount', 'refundAmount', 'refund_amount'),
    remainingAmount: pickNumberField(item, 'remainingAmount', 'remaining_amount'),
    items: Array.isArray(item.items) ? item.items.map(normalizeInvoiceItem) : undefined,
    paymentMethod:
      typeof item.paymentMethod === 'string' ? item.paymentMethod : undefined,
    paymentStatus: normalizePaymentStatus(item.paymentStatus),
    paymentReference:
      typeof item.paymentReference === 'string' ? item.paymentReference : undefined,
    transferContent:
      typeof item.transferContent === 'string' ? item.transferContent : undefined,
    paymentReviewReason:
      typeof item.paymentReviewReason === 'string' ? item.paymentReviewReason : undefined,
    bankCode: typeof item.bankCode === 'string' ? item.bankCode : undefined,
    bankAccountNo:
      typeof item.bankAccountNo === 'string' ? item.bankAccountNo : undefined,
    bankAccountName:
      typeof item.bankAccountName === 'string' ? item.bankAccountName : undefined,
    qrPayload: typeof item.qrPayload === 'string' ? item.qrPayload : undefined,
    qrCodeBase64:
      typeof item.qrCodeBase64 === 'string' ? item.qrCodeBase64 : undefined,
    vnpTxnRef: typeof item.vnpTxnRef === 'string' ? item.vnpTxnRef : undefined,
    vnpPaymentUrl:
      typeof item.vnpPaymentUrl === 'string' ? item.vnpPaymentUrl : undefined,
    paymentUrl:
      pickStringField(item, 'paymentUrl', 'payment_url', 'vnpPaymentUrl', 'vnp_payment_url'),
    paymentDetectedAt:
      typeof item.paymentDetectedAt === 'string' ? item.paymentDetectedAt : undefined,
    paidAt: typeof item.paidAt === 'string' ? item.paidAt : undefined,
    createdAt: typeof item.createdAt === 'string' ? item.createdAt : undefined,
  };
}

export function normalizePdfJob(raw: unknown) {
  const item = (raw ?? {}) as Record<string, unknown>;
  return {
    id: String(item.id ?? ''),
    invoiceId: typeof item.invoiceId !== 'undefined' ? String(item.invoiceId) : undefined,
    status: String(item.status ?? 'PENDING') as
      | 'PENDING'
      | 'PROCESSING'
      | 'COMPLETED'
      | 'FAILED',
    errorMessage:
      typeof item.errorMessage === 'string' ? item.errorMessage : undefined,
    downloadUrl:
      typeof item.downloadUrl === 'string' ? item.downloadUrl : undefined,
  };
}

export function normalizeBranchSpecialtyOption(raw: unknown): Specialty {
  const item = (raw ?? {}) as Record<string, unknown>;

  return {
    id: String(item.specialtyId ?? item.id ?? ''),
    code: typeof item.specialtyCode === 'string' ? item.specialtyCode : undefined,
    name: pickLocalizedField(
      item,
      'specialtyNameVn',
      'specialtyNameEn',
      'nameVn',
      'nameEn',
      'specialtyName',
      'name',
    ),
    description: pickLocalizedField(
      item,
      'specialtyDescriptionVn',
      'specialtyDescriptionEn',
      'descriptionVn',
      'descriptionEn',
      'description',
    ),
    iconUrl: sanitizeMediaUrl(item.iconUrl),
    imageUrl: sanitizeMediaUrl(item.imageUrl),
    status: String(item.effectiveStatus ?? item.status ?? ''),
    consultationFee:
      typeof item.consultationFee === 'number' ? item.consultationFee : undefined,
  };
}
