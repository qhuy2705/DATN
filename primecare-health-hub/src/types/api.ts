export type AppRole =
  | 'SYSTEM_ADMIN'
  | 'OPERATIONS_ADMIN'
  | 'STAFF'
  | 'SERVICE_TECHNICIAN'
  | 'CASHIER'
  | 'PHARMACIST'
  | 'DOCTOR'
  | 'PATIENT';

export type Gender = 'MALE' | 'FEMALE' | 'OTHER' | '';
export type BranchSessionType = 'MORNING' | 'AFTERNOON' | 'AM' | 'PM' | '';

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}

export type ApiQueryParamValue = string | number | boolean | null | undefined;
export type ApiQueryParams = Record<string, ApiQueryParamValue>;

export interface PageMeta {
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
  hasNext: boolean;
  hasPrev: boolean;
  sort?: string;
}

export interface PageResponse<T> {
  items: T[];
  meta: PageMeta;
}

export type PaginatedData<T> = PageResponse<T>;

export interface PublicFaqItem {
  id: string;
  category?: string;
  questionVn?: string;
  questionEn?: string;
  answerVn?: string;
  answerEn?: string;
}

export interface PublicAppointmentActionResult {
  code: string;
  status: string;
  message?: string;
}

export interface LoginRequest {
  identifier: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken?: string;
  expiresIn?: number;
  tokenType?: string;
  user?: CurrentUser;
}

export interface CredentialSetupDelivery {
  deliveryChannel?: string;
  maskedDestination?: string;
  delivered?: boolean;
  setupUrl?: string;
  expiresAt?: string;
}

export interface CredentialSetupTokenInfo {
  purpose?: string;
  email?: string;
  phone?: string;
  fullName?: string;
  status?: string;
  expired?: boolean;
  used?: boolean;
  expiresAt?: string;
}

export interface ForgotPasswordRequest {
  identifier: string;
}

export interface PublicContactRequest {
  fullName: string;
  email?: string;
  phone?: string;
  message: string;
  sourcePage?: string;
}

export interface PublicContactResponse {
  id: string;
  referenceCode?: string;
  status?: string;
}

export interface CompleteCredentialSetupRequest {
  token: string;
  newPassword: string;
}

export interface CurrentUser {
  id: string;
  email?: string;
  fullName: string;
  role: AppRole;
  avatarUrl?: string;
  branchId?: string;
  branchName?: string;
  patientId?: string;
}


export interface NotificationPreference {
  userId: string;
  allowEmail: boolean;
  allowSms: boolean;
  appointmentReminders: boolean;
  resultReadyAlerts: boolean;
  invoiceUpdates: boolean;
  securityAlerts: boolean;
  preferredChannel?: 'SMS' | 'EMAIL';
  maskedEmail?: string;
  maskedPhone?: string;
}

export interface RegisterPatientAccountRequest {
  fullName: string;
  phone: string;
  email?: string;
  dob?: string;
  gender?: Gender;
  address?: string;
  insuranceNumber?: string;
  password: string;
}

export interface UpdatePatientSelfProfileRequest {
  avatarUrl?: string;
  fullName?: string;
  phone?: string;
  email?: string;
  dob?: string;
  gender?: string;
  insuranceNumber?: string;
  address?: string;
  emergencyContactName?: string;
  emergencyContactPhone?: string;
  allergyNote?: string;
  chronicDiseaseNote?: string;
  note?: string;
  currentPassword?: string;
}

export interface PatientOverview {
  patientId: string;
  patientCode?: string;
  fullName: string;
  upcomingAppointments: number;
  totalAppointments: number;
  availableResults: number;
  totalInvoices: number;
  profileCompletionPercent?: number;
  summaryGeneratedAt?: string;
  nextAppointment?: PatientAppointmentHistoryItem;
  recentAppointments?: PatientAppointmentHistoryItem[];
  recentResults?: PatientResultHistoryItem[];
  recentInvoices?: PatientInvoiceHistoryItem[];
}

export interface PatientAppointmentHistoryItem {
  id: string;
  code: string;
  status?: string;
  visitDate?: string;
  session?: string;
  branchName?: string;
  specialtyName?: string;
  doctorName?: string;
  etaStart?: string;
  etaEnd?: string;
  createdAt?: string;
  canCancel?: boolean;
  cancelBlockedReason?: string;
}

export interface PatientStatusHistoryItem {
  id: string;
  fromStatus?: string;
  toStatus?: string;
  changedAt?: string;
  changedBy?: string;
  note?: string;
}

export interface CancelPatientAppointmentRequest {
  reason?: string;
}

export interface PatientInvoiceHistoryItem {
  id: string;
  code: string;
  serviceOrderCode?: string;
  totalAmount?: number;
  paymentStatus?: PaymentStatus;
  paymentMethod?: string;
  paidAt?: string;
  createdAt?: string;
  canDownloadPdf?: boolean;
}

export interface PatientResultHistoryItem {
  resultId: string;
  serviceOrderItemId: string;
  serviceName?: string;
  encounterCode?: string;
  status?: string;
  reportPdfStatus?: string;
  pdfReady?: boolean;
  verifiedAt?: string;
  performedAt?: string;
}

export interface Branch {
  id: string;
  code?: string;
  name: string;
  address?: string;
  phone?: string;
  email?: string;
  description?: string;
  imageUrl?: string;
  status?: string;
  createdAt?: string;
}

export interface Specialty {
  id: string;
  code?: string;
  name: string;
  description?: string;
  iconUrl?: string;
  imageUrl?: string;
  status?: string;
  defaultSlotMinutes?: number;
  maxPerSession?: number;
}

export type AdminSpecialty = Specialty;

export interface BranchSpecialty {
  id: string;
  branchId: string;
  branchName: string;
  specialtyId: string;
  specialtyName: string;
  specialtyCode?: string;
  status?: string;
  effectiveStatus?: string;
  inactiveReason?: string;
  bookable?: boolean;
  displayOrder?: number;
  consultationFee?: number;
  slotMinutesOverride?: number;
  note?: string;
}

export interface DoctorCareerTimelineItem {
  period?: string;
  title: string;
  organization?: string;
  description?: string;
}

export interface Doctor {
  id: string;
  code?: string;
  fullName: string;
  title?: string;
  bio?: string;
  expertise?: string;
  education?: string;
  achievements?: string;
  yearsOfExperience?: number;
  specialtyId?: string;
  specialtyIds?: string[];
  specialtyName?: string;
  branchId?: string;
  branchName?: string;
  avatarUrl?: string;
  slotMinutesOverride?: number;
  status?: string;
  effectiveStatus?: string;
  inactiveReason?: string;
  bookable?: boolean;
  nextAvailableDate?: string;
  hasUpcomingSchedule?: boolean;
  ratingAverage?: number;
  reviewCount?: number;
  supportedLanguages?: string[];
  featuredServices?: string[];
  careerTimeline?: DoctorCareerTimelineItem[];
  updatedAt?: string;
  hasAccount?: boolean;
  accountId?: string;
  accountEmail?: string;
  accountPhone?: string;
  accountRole?: string;
  accountStatus?: string;
}

export interface MedicalService {
  id: string;
  code?: string;
  nameVn?: string;
  nameEn?: string;
  name: string;
  descriptionVn?: string;
  descriptionEn?: string;
  description?: string;
  price: number;
  groupName?: string;
  serviceType?: string;
  departmentCode?: string;
  status?: string;
  publicVisible?: boolean;
  displayOrder?: number;
  thumbnailUrl?: string;
  defaultTurnaroundMinutes?: number;
  requiresFileResult?: boolean;
  requiresNumericResult?: boolean;
  resultTemplateCode?: ServiceResultTemplateCode;
  resultTemplateSchemaJson?: string;
  resultReportTitle?: string;
}

export interface AvailabilitySlot {
  id: string;
  doctorId: string;
  doctorName: string;
  branchId: string;
  branchName: string;
  specialtyId: string;
  specialtyName: string;
  visitDate: string;
  session: BranchSessionType;
  slotStart: string;
  slotEnd: string;
  remainingSlots: number;
  capacity?: number;
  bookedCount?: number;
  status: 'AVAILABLE' | 'ALMOST_FULL' | 'FULL';
}

export interface DashboardOverview {
  totalAppointmentsToday: number;
  totalPatientsToday: number;
  totalRevenue: number;
  completionRate: number;
  arrivedAppointments: number;
  checkedInAppointments: number;
  noShowAppointments: number;
  inProgressEncounters: number;
  waitingServiceItems: number;
  appointmentsByStatus: Record<string, number>;
  revenueBySpecialty: Array<{ name: string; value: number }>;
  appointmentsTrend: Array<{ date: string; count: number }>;
  revenueTrend: Array<{ date: string; value: number }>;
  noShowTrend: Array<{ date: string; value: number }>;
}

export interface DashboardBreakdownItem {
  id?: string;
  code?: string;
  name: string;
  value: number;
}

export interface DashboardBreakdown {
  branches: DashboardBreakdownItem[];
  specialties: DashboardBreakdownItem[];
  topDoctors: DashboardBreakdownItem[];
  topServices: DashboardBreakdownItem[];
}

export interface DashboardDoctorKpi {
  doctorId?: string;
  doctorName: string;
  totalAppointments: number;
  checkedInAppointments: number;
  noShowAppointments: number;
  fillRate: number;
  noShowRate: number;
}

export interface DashboardSpecialtyKpi {
  specialtyId?: string;
  specialtyName: string;
  totalAppointments: number;
  noShowAppointments: number;
  noShowRate: number;
}

export interface DashboardBranchRevenue {
  branchId?: string;
  branchName: string;
  paidRevenue: number;
}

export interface DashboardKpis {
  doctorKpis: DashboardDoctorKpi[];
  specialtyKpis: DashboardSpecialtyKpi[];
  branchRevenue: DashboardBranchRevenue[];
}

export interface AppointmentSummary {
  all: number;
  pending: number;
  confirmed: number;
  checkedIn: number;
  completed: number;
  cancelled: number;
  noShow: number;
}

export interface CashierSummary {
  serviceOrdersWaiting: number;
  serviceOrdersUnpaidAmount: number;
  unpaidInvoices: number;
  paidRevenue: number;
}

export interface BranchMasterDataSummary {
  total: number;
  activeBranches: number;
  inactiveBranches: number;
}

export interface DoctorMasterDataSummary {
  total: number;
  activeDoctors: number;
  noAccountDoctors: number;
  blockedDoctors: number;
}

export interface StaffMasterDataSummary {
  total: number;
  activeStaffs: number;
  noAccountStaffs: number;
}

export interface DoctorAppointmentSummary {
  total: number;
  waitingExam: number;
  inCare: number;
  waitingExternal: number;
  needsReturn: number;
  done: number;
  completed?: number;
  confirmed?: number;
  checkedIn?: number;
  cancelled?: number;
  noShow?: number;
}

export interface ReceptionQueueSummary {
  total: number;
  requested: number;
  confirmed: number;
  arrived: number;
  checkedIn: number;
  notArrived: number;
  walkIn: number;
  priority?: number;
  overdueCount?: number;
  noShowFollowUpPending?: number;
}

export type NotificationSeverity = 'INFO' | 'SUCCESS' | 'WARNING' | 'ERROR' | string;

export interface InternalNotification {
  id: string;
  title: string;
  message: string;
  route?: string;
  entityType?: string;
  entityId?: string;
  type?: string;
  severity?: NotificationSeverity;
  createdAt: string;
  read: boolean;
  readAt?: string;
}

export type AppointmentStatus =
  | 'REQUESTED'
  | 'CONFIRMED'
  | 'CHECKED_IN'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'NO_SHOW';

export type AppointmentListQueryParams = ApiQueryParams & {
  page?: string | number;
  size?: string | number;
  q?: string;
  status?: AppointmentStatus;
  followUpPending?: boolean;
  overdue?: boolean | string;
  branchId?: string | number;
  doctorId?: string | number;
  visitDate?: string;
};

export type AvailabilityQueryParams = ApiQueryParams & {
  branchId?: string | number;
  specialtyId?: string | number;
  doctorId?: string | number;
  visitDate?: string;
  session?: BranchSessionType | string;
  onlyAvailable?: boolean | string;
};

export interface Appointment {
  id: string;
  code: string;
  patientFullName: string;
  patientPhone: string;
  patientEmail?: string;
  patientDob?: string;
  patientGender?: Gender | string;
  patientNote?: string;
  reasonForVisit?: string;
  visitType?: string;
  triagePriority?: string;
  triageNote?: string;
  insuranceNote?: string;
  emergencyContactName?: string;
  emergencyContactPhone?: string;
  heightCm?: number;
  weightKg?: number;
  temperatureC?: number;
  pulse?: number;
  systolicBp?: number;
  diastolicBp?: number;
  respiratoryRate?: number;
  spo2?: number;
  intakeCompletedAt?: string;
  intakeCompletedByName?: string;
  patientId?: string;
  doctorId: string;
  doctorName: string;
  branchId: string;
  branchName: string;
  specialtyId: string;
  specialtyName: string;
  visitDate: string;
  session: string;
  slotStart: string;
  slotEnd?: string;
  status: AppointmentStatus;
  claimedBy?: string;
  processingById?: string;
  processingStartedAt?: string;
  processingExpiresAt?: string;
  checkInToken?: string;
  queueNo?: number;
  slotMinutes?: number;
  sourceType?: string;
  arrivalStatus?: 'NOT_ARRIVED' | 'ARRIVED';
  receptionQueueNo?: number;
  arrivedAt?: string;
  arrivedByName?: string;
  checkedInAt?: string;
  checkedInByName?: string;
  confirmedAt?: string;
  confirmedByName?: string;
  noShowMarkedAt?: string;
  noShowMarkedByName?: string;
  followUpPending?: boolean;
  noShowNote?: string;
  rescheduledFromAppointmentId?: string;
  rescheduledToAppointmentId?: string;
  overdue?: boolean;
  autoCancelled?: boolean;
  cancellationReason?: string;
  cancelledAt?: string;
  cancelledByName?: string;
  createdAt?: string;
  updatedAt?: string;
  activeEncounterId?: string;
  activeEncounterCode?: string;
  activeEncounterStatus?: EncounterStatus;
  serviceOrderCount?: number;
  pendingPaymentOrderCount?: number;
  waitingResultItemCount?: number;
  completedResultItemCount?: number;
  issuedPrescriptionCount?: number;
  hasPendingPayment?: boolean;
  hasWaitingResults?: boolean;
  readyForConclusion?: boolean;
  canCreatePrescription?: boolean;
  canComplete?: boolean;
}

export interface WalkInFormRequest {
  patientId?: string;
  branchId: string;
  specialtyId: string;
  doctorId: string;
  visitDate: string;
  session: BranchSessionType;
  slotStart: string;
  patientFullName: string;
  patientPhone: string;
  patientEmail?: string;
  patientDob?: string;
  patientGender?: Gender | string;
  patientNote?: string;
  reasonForVisit?: string;
  visitType?: string;
  triagePriority?: string;
  triageNote?: string;
  insuranceNote?: string;
  emergencyContactName?: string;
  emergencyContactPhone?: string;
  heightCm?: number;
  weightKg?: number;
  temperatureC?: number;
  pulse?: number;
  systolicBp?: number;
  diastolicBp?: number;
  respiratoryRate?: number;
  spo2?: number;
  intakeCompletedAt?: string;
  intakeCompletedByName?: string;
  arrived?: boolean;
}

export interface BookingRequest {
  branchId: string;
  specialtyId: string;
  doctorId: string;
  visitDate: string;
  session: string;
  slotStart: string;
  patientFullName: string;
  patientPhone: string;
  patientEmail?: string;
  patientDob?: string;
  patientGender?: Gender | string;
  patientNote?: string;
  reasonForVisit?: string;
  visitType?: string;
  triagePriority?: string;
  triageNote?: string;
  insuranceNote?: string;
  emergencyContactName?: string;
  emergencyContactPhone?: string;
  heightCm?: number;
  weightKg?: number;
  temperatureC?: number;
  pulse?: number;
  systolicBp?: number;
  diastolicBp?: number;
  respiratoryRate?: number;
  spo2?: number;
  intakeCompletedAt?: string;
  intakeCompletedByName?: string;
}

export interface Icd10CodeResponse {
  id: number;
  code: string;
  nameVn: string;
  nameEn?: string;
  category?: string;
}

export interface EncounterDiagnosisResponse {
  id?: number;
  icd10CodeId: number;
  icd10Code?: string;
  icd10NameVn?: string;
  diagnosisType: 'PRELIMINARY' | 'FINAL' | 'SECONDARY';
  note?: string;
  displayOrder?: number;
}

export interface PatientAllergyResponse {
  id: number;
  patientId: string;
  allergenName: string;
  allergyType: 'DRUG' | 'FOOD' | 'ENVIRONMENTAL' | 'OTHER';
  severity: 'MILD' | 'MODERATE' | 'SEVERE' | 'LIFE_THREATENING';
  reaction?: string;
  notedById?: string;
  notedByName?: string;
  createdAt?: string;
}

export type EncounterStatus =
  | 'IN_PROGRESS'
  | 'REOPENED'
  | 'WAITING_PAYMENT'
  | 'WAITING_RESULTS'
  | 'READY_FOR_CONCLUSION'
  | 'COMPLETED'
  | 'CANCELLED';

export interface Encounter {
  id: string;
  code?: string;
  appointmentId: string;
  patientId?: string;
  patientName: string;
  diagnosis?: string;
  symptoms?: string;
  notes?: string;
  patientPhone?: string;
  patientEmail?: string;
  patientDob?: string;
  patientGender?: string;
  intakeReasonForVisit?: string;
  visitType?: string;
  triagePriority?: string;
  triageNote?: string;
  insuranceNote?: string;
  emergencyContactName?: string;
  emergencyContactPhone?: string;
  intakeCompletedAt?: string;
  intakeCompletedByName?: string;
  doctorId?: string;
  doctorName: string;
  branchId?: string;
  branchName?: string;
  specialtyId?: string;
  specialtyIds?: string[];
  specialtyName?: string;
  chiefComplaint?: string;
  clinicalNote?: string;
  preliminaryDiagnosis?: string;
  finalDiagnosis?: string;
  diagnoses?: EncounterDiagnosisResponse[];
  conclusion?: string;
  heightCm?: number;
  weightKg?: number;
  temperatureC?: number;
  pulse?: number;
  systolicBp?: number;
  diastolicBp?: number;
  respiratoryRate?: number;
  spo2?: number;
  allergySnapshot?: string;
  chronicDiseaseSnapshot?: string;
  pastMedicalHistory?: string;
  physicalExamination?: string;
  treatmentPlan?: string;
  followUpDate?: string;
  followUpNote?: string;
  status: EncounterStatus;
  createdAt?: string;
  updatedAt?: string;
  startedAt?: string;
  completedAt?: string;
  serviceOrderCount?: number;
  pendingPaymentOrderCount?: number;
  activeServiceOrderCount?: number;
  waitingResultItemCount?: number;
  completedResultItemCount?: number;
  issuedPrescriptionCount?: number;
  hasPendingPayment?: boolean;
  hasWaitingResults?: boolean;
  readyForConclusion?: boolean;
  canCreatePrescription?: boolean;
  canComplete?: boolean;
}


export interface EncounterTimelineItem {
  id: string;
  eventType: string;
  stage: 'BOOKING' | 'RECEPTION' | 'CLINICAL' | 'BILLING' | 'DIAGNOSTICS' | 'PRESCRIPTION' | 'COMPLETION' | string;
  title: string;
  description?: string;
  occurredAt?: string;
  actorName?: string;
  status?: string;
  referenceCode?: string;
}

export interface EncounterAiSummary {
  quickSummary: string;
  highlightedResults: string[];
  riskFlags: string[];
  nextStepSuggestions: string[];
  draftDoctorNote: string;
  disclaimer?: string;
  provider?: string;
}

export type PrescriptionStatus = 'DRAFT' | 'ISSUED' | 'PAID' | 'DISPENSED' | 'CANCELLED';

export interface PrescriptionItem {
  id?: string;
  medicationId: string;
  medicationName: string;
  medicationCode?: string;
  strength?: string;
  dosageForm?: string;
  unit?: string;
  quantity?: number;
  dose: string;
  frequency: string;
  durationDays?: number;
  duration?: string;
  route: string;
  instruction?: string;
}

export interface Prescription {
  id: string;
  code?: string;
  encounterId: string;
  encounterCode?: string;
  doctorUserId?: string;
  issuedDate?: string;
  generalNote?: string;
  status: PrescriptionStatus;
  items: PrescriptionItem[];
  createdAt?: string;
}

export interface Medication {
  id: string;
  code?: string;
  name: string;
  genericName?: string;
  strength?: string;
  dosageForm: string;
  unit: string;
  manufacturer?: string;
  indicationNote?: string;
  contraindicationNote?: string;
  status?: string;
}

export interface PharmacyInventoryItem {
  medicationId: string;
  medicationCode?: string;
  medicationName: string;
  unit?: string;
  totalQuantity: number;
  lowStock?: boolean;
  status?: string;
}

export interface MedicationBatch {
  id: string;
  batchId: string;
  batchCode: string;
  medicationId: string;
  medicationCode?: string;
  medicationName: string;
  unit?: string;
  quantity: number;
  expiryDate?: string;
  status?: string;
}

export interface ExpiringBatch extends MedicationBatch {
  daysUntilExpiry?: number;
  expired?: boolean;
}

export interface CreateBatchRequest {
  medicationId: string;
  batchCode: string;
  quantity: number;
  expiryDate: string;
  status?: string;
}

export interface UpdateBatchRequest {
  medicationId?: string;
  batchCode?: string;
  quantity?: number;
  expiryDate?: string;
  status?: string;
}

export interface Patient {
  id: string;
  code?: string;
  patientCode?: string;
  avatarUrl?: string;
  fullName: string;
  phone: string;
  email?: string;
  dob?: string;
  gender?: string;
  bloodType?: string;
  ethnicity?: string;
  nationality?: string;
  occupation?: string;
  province?: string;
  district?: string;
  ward?: string;
  address?: string;
  identityNumber?: string;
  insuranceNumber?: string;
  insuranceExpiryDate?: string;
  insuranceRegisteredHospital?: string;
  emergencyContactName?: string;
  emergencyContactPhone?: string;
  allergyNote?: string;
  allergies?: PatientAllergyResponse[];
  chronicDiseaseNote?: string;
  note?: string;
  status?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface Staff {
  id: string;
  fullName: string;
  email?: string;
  phone?: string;
  role?: AppRole;
  branchId?: string;
  branchName?: string;
  status?: string;
  avatarUrl?: string;
  hasAccount?: boolean;
  accountId?: string;
  accountEmail?: string;
  accountPhone?: string;
  accountRole?: AppRole;
  accountStatus?: string;
}

export interface DoctorScheduleDoctorOption {
  id: string;
  fullName: string;
  displayTitleVn?: string;
  branchId?: string;
  branchNameVn?: string;
  status?: string;
}

export interface DoctorSchedule {
  id: string;
  doctorId: string;
  doctorName: string;
  workDate?: string;
  dayOfWeek?: string;
  session: string;
  startTime?: string;
  endTime?: string;
  maxPatients?: number;
  note?: string;
  status?: string;
  branchId?: string;
  branchName?: string;
  patientId?: string;
}

export interface DoctorScheduleImportResult {
  doctorId?: string;
  doctorName?: string;
  month: string;
  totalRows: number;
  importedRows: number;
  skippedRows: number;
  warnings: string[];
}

export type LeaveRequestStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'CANCELLED';


export type LeaveRequest = {
  id: string;
  doctorId: string;
  doctorName: string;
  startDate: string;
  endDate: string;
  startSession: string;
  endSession: string;
  reason: string;
  status: LeaveRequestStatus;
  reviewNote: string | null;
  reviewedAt: string | null;
  reviewedByName: string | null;
  createdAt: string;
};

export interface AuditLog {
  id: string;
  actor: string;
  actorId?: string;
  actorName?: string;
  actorRole?: string;
  action: string;
  entityType: string;
  entityId: string;
  targetType?: string;
  targetId?: string;
  description?: string;
  metadata?: Record<string, unknown>;
  beforeJson?: string;
  afterJson?: string;
  ipAddress?: string;
  userAgent?: string;
  createdAt?: string;
}

export type ServiceOrderStatus =
  | 'PENDING_PAYMENT'
  | 'PAID'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'CANCELLED';

export type ServiceOrderItemStatus =
  | 'PENDING_PAYMENT'
  | 'WAITING_EXECUTION'
  | 'IN_PROGRESS'
  | 'DONE'
  | 'CANCELLED';

export type ServiceResultStatus = 'DRAFT' | 'COMPLETED' | 'VERIFIED';

export type ServiceResultTemplateCode =
  | 'GENERIC_NARRATIVE'
  | 'LAB_TABLE'
  | 'IMAGING_REPORT'
  | 'PROCEDURE_REPORT';

export type PdfGenerationStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

export type PaymentStatus =
  | 'UNPAID'
  | 'PENDING_CONFIRMATION'
  | 'PAYMENT_REVIEW'
  | 'PAID'
  | 'REFUNDED'
  | 'VOID';

export interface ServiceResultAttachment {
  url: string;
  mimeType?: string;
  fileName?: string;
  label?: string;
}

export interface ServiceOrderItem {
  id: string;
  medicalServiceId: string;
  serviceCode?: string;
  serviceNameVn?: string;
  serviceNameEn?: string;
  price?: number;
  quantity: number;
  lineTotalAmount?: number;
  departmentCode?: string;
  queueNo?: number;
  status?: ServiceOrderItemStatus | string;
  resultStatus?: ServiceResultStatus | string;
  note?: string;
  resultTextVn?: string;
  resultTextEn?: string;
  resultDataJson?: string;
  fieldValuesJson?: string;
  attachmentUrl?: string;
  attachmentMimeType?: string;
  attachmentUrlsJson?: string;
  attachments?: ServiceResultAttachment[];
  templateCode?: ServiceResultTemplateCode;
  templateSchemaJson?: string;
  conclusionText?: string;
  impressionText?: string;
  reportTitle?: string;
  reportPdfUrl?: string;
  reportPdfStatus?: PdfGenerationStatus | string;
  reportPdfGeneratedAt?: string;
  reportPdfErrorMessage?: string;
  turnaroundTargetMinutes?: number;
  dueAt?: string;
  elapsedMinutes?: number;
  turnaroundMinutes?: number;
  overdue?: boolean;
  resultPerformedAt?: string;
}

export interface ServiceOrder {
  id: string;
  code: string;
  encounterId: string;
  status?: ServiceOrderStatus | string;
  paymentStatus?: PaymentStatus;
  estimatedTotalAmount?: number;
  note?: string;
  orderedAt?: string;
  paidAt?: string;
  createdAt?: string;
  updatedAt?: string;
  items: ServiceOrderItem[];
}

export interface CashierServiceOrder {
  id: string;
  code: string;
  encounterId?: string;
  patientName?: string;
  doctorName?: string;
  branchName?: string;
  status?: string;
  paymentStatus?: PaymentStatus;
  invoiced?: boolean;
  invoiceId?: string;
  invoiceCode?: string;
  estimatedTotalAmount?: number;
  note?: string;
  orderedAt?: string;
  createdAt?: string;
  items: ServiceOrderItem[];
}

export interface ServiceDeskQueueItem {
  itemId: string;
  serviceOrderId?: string;
  serviceOrderCode?: string;
  encounterId?: string;
  encounterCode?: string;
  appointmentCode?: string;
  patientName?: string;
  doctorName?: string;
  branchName?: string;
  departmentCode?: string;
  serviceCode?: string;
  serviceNameVn?: string;
  serviceNameEn?: string;
  queueNo?: number;
  itemStatus?: ServiceOrderItemStatus | string;
  resultStatus?: ServiceResultStatus | string;
  queuedAt?: string;
  startedAt?: string;
  completedAt?: string;
  resultId?: string;
  resultTextVn?: string;
  resultTextEn?: string;
  resultDataJson?: string;
  fieldValuesJson?: string;
  attachmentUrl?: string;
  attachmentMimeType?: string;
  attachmentUrlsJson?: string;
  attachments?: ServiceResultAttachment[];
  templateCode?: ServiceResultTemplateCode;
  templateSchemaJson?: string;
  conclusionText?: string;
  impressionText?: string;
  reportTitle?: string;
  reportPdfUrl?: string;
  reportPdfStatus?: PdfGenerationStatus | string;
  reportPdfGeneratedAt?: string;
  reportPdfErrorMessage?: string;
  turnaroundTargetMinutes?: number;
  dueAt?: string;
  elapsedMinutes?: number;
  turnaroundMinutes?: number;
  overdue?: boolean;
  performedAt?: string;
  performedByName?: string;
  verifiedAt?: string;
  verifiedByName?: string;
}

export interface ServiceDeskSummary {
  waitingCount: number;
  inProgressCount: number;
  completedTodayCount: number;
  overdueCount: number;
  readyForDoctorCount: number;
  pdfPendingCount: number;
  averageTurnaroundMinutes: number;
  turnaroundBreachRate: number;
}

export interface InvoiceItemResponse {
  id: number;
  referenceType?: string;
  nameSnapshot?: string;
  unitPrice?: number;
  quantity?: number;
  taxRate?: number;
  subtotalAmount?: number;
  taxAmount?: number;
  totalAmount?: number;
}

export interface Invoice {
  id: string;
  code?: string;
  serviceOrderId: string;
  serviceOrderCode?: string;
  encounterId?: string;
  patientName?: string;
  doctorName?: string;
  branchName?: string;
  subtotalAmount?: number;
  discountAmount?: number;
  taxAmount?: number;
  totalAmount: number;
  items?: InvoiceItemResponse[];
  paymentMethod?: string;
  paymentStatus: PaymentStatus;
  paymentReference?: string;
  transferContent?: string;
  paymentReviewReason?: string;
  bankCode?: string;
  bankAccountNo?: string;
  bankAccountName?: string;
  qrPayload?: string;
  qrCodeBase64?: string;
  vnpTxnRef?: string;
  vnpPaymentUrl?: string;
  paymentUrl?: string;
  paymentDetectedAt?: string;
  paidAt?: string;
  createdAt?: string;
}

export interface PdfJob {
  id: string;
  invoiceId?: string;
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  errorMessage?: string;
  downloadUrl?: string;
  createdAt?: string;
}


export interface AppointmentLookupOtpResult {
  appointmentCode: string;
  deliveryChannel: string;
  maskedDestination: string;
  expiresInSeconds?: number;
  resendAvailableInSeconds?: number;
}

export interface AppointmentLookupResult {
  appointment: Appointment;
  pdfAvailable: boolean;
  pdfDownloadToken?: string;
}

export interface PublicLookupOtpResult {
  channel?: string;
  deliveryChannel?: string;
  maskedDestination?: string;
  expiresInSeconds?: number;
  resendAvailableInSeconds?: number;
}

export interface AppointmentLookupSummary {
  code: string;
  status: string;
  patientFullName: string;
  doctorName: string;
  specialtyName?: string;
  branchName?: string;
  visitDate?: string;
  session?: string;
  slotRange?: string;
  queueNo?: number;
  pdfReady?: boolean;
}

export interface ResultLookupSummary {
  appointmentCode: string;
  encounterCode?: string;
  patientFullName: string;
  doctorName: string;
  specialtyName?: string;
  branchName?: string;
  visitDate?: string;
  encounterStatus?: string;
  finalDiagnosis?: string;
  conclusion?: string;
  serviceResultCount?: number;
  completedResultCount?: number;
  verifiedResultCount?: number;
  doctorConcluded?: boolean;
  pdfReady?: boolean;
}

export interface AppointmentLookupVerifyResult {
  accessToken: string;
  expiresAt?: string;
  appointment: AppointmentLookupSummary;
}

export interface ResultLookupVerifyResult {
  accessToken: string;
  expiresAt?: string;
  result: ResultLookupSummary;
}

export interface PublicAssistantAction {
  type: 'BOOK_APPOINTMENT' | 'LOOKUP_RESULT' | 'LOOKUP_APPOINTMENT' | 'NAVIGATE';
  label: string;
  value?: string;
}

export interface PublicAssistantMessagePayload {
  role: 'assistant' | 'user';
  text: string;
}

export interface PublicAssistantRequestPayload {
  question: string;
  locale?: string;
  pagePath?: string;
  pageTitle?: string;
  history?: PublicAssistantMessagePayload[];
}

export interface PublicAssistantResponse {
  answer: string;
  provider?: string;
  caution?: string;
  actions?: PublicAssistantAction[];
  suggestions?: string[];
}
