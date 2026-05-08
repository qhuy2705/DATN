import type {
  ApiResponse,
  Appointment,
  AuditLog,
  AvailabilitySlot,
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
  EncounterDiagnosisResponse,
  EncounterStatus,
  InvoiceItemResponse,
  MedicalService,
  MedicationBatch,
  PaymentStatus,
  PublicFaqItem,
  PaginatedData,
  ExpiringBatch,
  PharmacyInventoryItem,
  PrescriptionStatus,
  ServiceOrder,
  ServiceResultAttachment,
  Specialty,
  StaffMasterDataSummary,
  Staff,
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

const PAYMENT_STATUSES: readonly PaymentStatus[] = [
  'UNPAID',
  'PENDING_CONFIRMATION',
  'PAYMENT_REVIEW',
  'PAID',
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
    .filter((entry): entry is DoctorCareerTimelineItem => Boolean(entry));

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
      .filter((entry): entry is ServiceResultAttachment => Boolean(entry));

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
    templateCode: typeof row.templateCode === 'string' ? row.templateCode : undefined,
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
    paidRevenue:
      pickNumericField(
        item,
        'paidRevenue',
        'paidAmount',
        'totalPaidAmount',
        'revenuePaid',
        'totalRevenue',
        'revenue',
      ) ?? 0,
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
    total: pickNumericField(item, 'total', 'all', 'totalDoctors', 'doctorCount') ?? 0,
    activeDoctors:
      pickNumericField(item, 'activeDoctors', 'active', 'activeCount', 'ACTIVE') ?? 0,
    noAccountDoctors:
      pickNumericField(
        item,
        'noAccountDoctors',
        'doctorsWithoutAccount',
        'withoutAccountDoctors',
        'noAccountCount',
        'withoutAccount',
      ) ?? 0,
    blockedDoctors:
      pickNumericField(
        item,
        'blockedDoctors',
        'blocked',
        'blockedCount',
        'dependencyBlocked',
        'blockedByDependency',
        'inactiveByDependency',
      ) ?? 0,
  };
}

export function normalizeStaffMasterDataSummary(raw: unknown): StaffMasterDataSummary {
  const item = isRecord(raw) && isRecord(raw.summary) ? raw.summary : isRecord(raw) ? raw : {};

  return {
    total: pickNumericField(item, 'total', 'all', 'totalStaffs', 'staffCount') ?? 0,
    activeStaffs:
      pickNumericField(item, 'activeStaffs', 'active', 'activeCount', 'ACTIVE') ?? 0,
    noAccountStaffs:
      pickNumericField(
        item,
        'noAccountStaffs',
        'staffsWithoutAccount',
        'withoutAccountStaffs',
        'noAccountCount',
        'withoutAccount',
      ) ?? 0,
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

export function normalizeDashboardOverview(raw: unknown): DashboardOverview {
  const fallback: DashboardOverview = {
    totalAppointmentsToday: 0,
    totalPatientsToday: 0,
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

  if ('today' in raw) {
    const today = isRecord(raw.today) ? raw.today : {};
    const appointmentSeries = Array.isArray(raw.appointmentSeries) ? raw.appointmentSeries : [];
    const revenueSeries = Array.isArray(raw.revenueSeries) ? raw.revenueSeries : [];
    const noShowSeries = Array.isArray(raw.noShowSeries) ? raw.noShowSeries : [];

    const normalizeSeries = (series: unknown[]) =>
      series.map((item) => {
        const row = isRecord(item) ? item : {};
        return {
          date: String(row.date ?? row.label ?? ''),
          value: Number(row.value ?? row.count ?? 0),
        };
      });

    return {
      totalAppointmentsToday: Number(today.totalAppointments ?? 0),
      totalPatientsToday: Number(today.totalEncounters ?? today.arrivedAppointments ?? 0),
      totalRevenue: Number(today.paidRevenue ?? 0),
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
      appointmentsTrend: normalizeSeries(appointmentSeries).map((item) => ({
        date: item.date,
        count: item.value,
      })),
      revenueTrend: normalizeSeries(revenueSeries),
      noShowTrend: normalizeSeries(noShowSeries),
    };
  }

  if (
    typeof raw.totalAppointmentsToday === 'number' &&
    typeof raw.totalPatientsToday === 'number' &&
    typeof raw.totalRevenue === 'number' &&
    typeof raw.completionRate === 'number' &&
    isRecord(raw.appointmentsByStatus) &&
    Array.isArray(raw.revenueBySpecialty) &&
    Array.isArray(raw.appointmentsTrend)
  ) {
    return raw as unknown as DashboardOverview;
  }

  return fallback;
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
      return {
        branchId: row.branchId != null ? String(row.branchId) : undefined,
        branchName: String(row.branchName ?? '—'),
        paidRevenue: Number(row.paidRevenue ?? 0),
      };
    }),
  };
}

export function normalizeBranch(raw: unknown): Branch {
  const item = (raw ?? {}) as Record<string, unknown>;
  return {
    id: String(item.id ?? ''),
    code: typeof item.code === 'string' ? item.code : undefined,
    name: pickLocalizedField(item, 'nameVn', 'nameEn', 'name'),
    address: pickLocalizedField(item, 'addressVn', 'addressEn', 'address'),
    phone: String(item.phone ?? ''),
    email: String(item.email ?? ''),
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
    name: pickLocalizedField(item, 'nameVn', 'nameEn', 'name'),
    description: pickLocalizedField(item, 'descriptionVn', 'descriptionEn', 'description'),
    iconUrl: sanitizeMediaUrl(item.iconUrl),
    imageUrl: sanitizeMediaUrl(item.imageUrl),
    status: String(item.status ?? ''),
  };
}

export function normalizeDoctor(raw: unknown): Doctor {
  const item = (raw ?? {}) as Record<string, unknown>;
  const specialtyIds = Array.isArray(item.specialtyIds) ? item.specialtyIds : [];
  const supportedLanguages = normalizeStringArray(item.supportedLanguages ?? item.supportedLanguagesJson);
  const featuredServices = normalizeStringArray(item.featuredServices ?? item.featuredServicesJson);
  const careerTimeline = normalizeCareerTimeline(item.careerTimeline ?? item.careerTimelineJson);

  return {
    id: String(item.id ?? ''),
    fullName: String(item.fullName ?? ''),
    title: pickLocalizedField(item, 'displayTitleVn', 'displayTitleEn', 'title'),
    avatarUrl: sanitizeMediaUrl(item.avatarUrl),
    bio: pickLocalizedField(item, 'bioVn', 'bioEn', 'bio'),
    expertise: pickLocalizedField(item, 'expertiseVn', 'expertiseEn', 'expertise'),
    yearsOfExperience: Number(item.yearsExp ?? item.yearsOfExperience ?? 0),
    specialtyId: String(specialtyIds[0] ?? item.specialtyId ?? ''),
    specialtyIds: specialtyIds.map((value) => String(value)),
    specialtyName: pickLocalizedField(item, 'specialtyNameVn', 'specialtyNameEn', 'specialtyName'),
    branchId: String(item.branchId ?? ''),
    branchName: pickLocalizedField(item, 'branchNameVn', 'branchNameEn', 'branchName'),
    education: pickLocalizedField(item, 'educationVn', 'educationEn', 'education'),
    achievements: pickLocalizedField(item, 'achievementsVn', 'achievementsEn', 'achievements'),
    status: String(item.status ?? 'ACTIVE'),
    effectiveStatus:
      typeof item.effectiveStatus === 'string' ? item.effectiveStatus : undefined,
    inactiveReason:
      typeof item.inactiveReason === 'string' ? item.inactiveReason : undefined,
    bookable:
      typeof item.bookable === 'boolean' ? item.bookable : undefined,
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
      typeof item.resultTemplateCode === 'string' ? item.resultTemplateCode : undefined,
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
    triagePriority: typeof item.triagePriority === 'string' ? item.triagePriority : undefined,
    triageNote: typeof item.triageNote === 'string' ? item.triageNote : undefined,
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
    doctorId: String(item.doctorId ?? ''),
    doctorName: pickLocalizedField(item, 'doctorNameVn', 'doctorNameEn', 'doctorName'),
    branchId: String(item.branchId ?? ''),
    branchName: pickLocalizedField(item, 'branchNameVn', 'branchNameEn', 'branchName'),
    specialtyId: String(item.specialtyId ?? ''),
    specialtyName: pickLocalizedField(item, 'specialtyNameVn', 'specialtyNameEn', 'specialtyName'),
    visitDate: String(item.visitDate ?? ''),
    session: normalizeScheduleSession(item.session),
    slotStart: formatTimeDisplay(item.etaStart ?? item.slotStart ?? ''),
    slotEnd: typeof item.etaEnd !== 'undefined' ? formatTimeDisplay(item.etaEnd) : undefined,
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
    confirmedAt: typeof item.confirmedAt === 'string' ? item.confirmedAt : undefined,
    confirmedByName:
      typeof item.confirmedByName === 'string' ? item.confirmedByName : undefined,
    noShowMarkedAt:
      typeof item.noShowMarkedAt === 'string' ? item.noShowMarkedAt : undefined,
    noShowMarkedByName:
      typeof item.noShowMarkedByName === 'string' ? item.noShowMarkedByName : undefined,
    followUpPending: Boolean(item.followUpPending),
    noShowNote: typeof item.noShowNote === 'string' ? item.noShowNote : undefined,
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
  return {
    id: String(item.id ?? ''),
    actor: String(item.actorName ?? item.actorId ?? item.actor ?? ''),
    actorId:
      typeof item.actorId !== 'undefined' ? String(item.actorId) : undefined,
    actorName:
      typeof item.actorName === 'string'
        ? item.actorName
        : typeof item.actorId !== 'undefined'
          ? String(item.actorId)
          : undefined,
    actorRole: typeof item.actorRole === 'string' ? item.actorRole : undefined,
    action: String(item.action ?? ''),
    entityType: String(item.entityType ?? item.entity ?? ''),
    entityId: typeof item.entityId !== 'undefined' ? String(item.entityId) : '',
    targetType:
      typeof item.targetType === 'string'
        ? item.targetType
        : typeof item.entity === 'string'
          ? item.entity
          : undefined,
    targetId:
      typeof item.targetId !== 'undefined'
        ? String(item.targetId)
        : typeof item.entityId !== 'undefined'
          ? String(item.entityId)
          : undefined,
    description: typeof item.description === 'string' ? item.description : undefined,
    metadata:
      item.metadata && typeof item.metadata === 'object'
        ? (item.metadata as Record<string, unknown>)
        : undefined,
    beforeJson: typeof item.beforeJson === 'string' ? item.beforeJson : undefined,
    afterJson: typeof item.afterJson === 'string' ? item.afterJson : undefined,
    ipAddress: typeof item.ipAddress === 'string' ? item.ipAddress : undefined,
    userAgent: typeof item.userAgent === 'string' ? item.userAgent : undefined,
    createdAt: typeof item.createdAt === 'string' ? item.createdAt : String(item.createdAt ?? ''),
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
    identityNumber:
      typeof item.identityNumber === 'string' ? item.identityNumber : undefined,
    insuranceNumber:
      typeof item.insuranceNumber === 'string'
        ? item.insuranceNumber
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
    batchCode: pickStringField(item, 'batchCode', 'batch_code', 'code') ?? batchId,
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
    name: pickLocalizedField(item, 'nameVn', 'nameEn', 'name'),
    description: String(
      item.descriptionVn ?? item.description ?? item.descriptionEn ?? '',
    ),
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
    issuedDate: typeof item.issuedDate === 'string' ? item.issuedDate : undefined,
    generalNote: typeof item.generalNote === 'string' ? item.generalNote : undefined,
    status: normalizePrescriptionStatus(item.status),
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
      };
    }),
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
    items: Array.isArray(item.items) ? (item.items as InvoiceItemResponse[]) : undefined,
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
  };
}
