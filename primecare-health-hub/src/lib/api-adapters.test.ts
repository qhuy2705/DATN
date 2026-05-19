import { describe, expect, it } from 'vitest';
import {
  normalizeAppointment,
  normalizeBookingRestriction,
  normalizeBookingRestrictionSummary,
  normalizeDashboardOverview,
  normalizeDoctor,
  normalizeDoctorMasterDataSummary,
  normalizeFollowUpQueueItem,
  normalizeRescheduleOffer,
  normalizeStaffMasterDataSummary,
} from '@/lib/api-adapters';
import {
  getDoctorReadinessState,
  getPublicDoctorBookingSpecialty,
  isDoctorBookable,
  isPublicDoctorBookable,
  isPublicDoctorBookableForSpecialty,
} from '@/lib/doctor-readiness';

describe('doctor adapter', () => {
  it('preserves backend doctor account fields', () => {
    const doctor = normalizeDoctor({
      id: 'doctor-1',
      fullName: 'BS. Nguyen An',
      hasAccount: true,
      accountId: 123,
      accountEmail: 'doctor@example.com',
      accountPhone: '0909000000',
      accountRole: 'DOCTOR',
      accountStatus: 'ACTIVE',
    });

    expect(doctor.hasAccount).toBe(true);
    expect(doctor.accountId).toBe('123');
    expect(doctor.accountEmail).toBe('doctor@example.com');
    expect(doctor.accountPhone).toBe('0909000000');
    expect(doctor.accountRole).toBe('DOCTOR');
    expect(doctor.accountStatus).toBe('ACTIVE');
  });

  it('preserves backend doctor readiness fields', () => {
    const doctor = normalizeDoctor({
      id: 'doctor-1',
      fullName: 'BS. Nguyen An',
      operationalReady: false,
      bookable: false,
      notReadyReason: 'NO_ACCOUNT',
    });

    expect(doctor.operationalReady).toBe(false);
    expect(doctor.bookable).toBe(false);
    expect(doctor.notReadyReason).toBe('NO_ACCOUNT');
  });

  it('does not treat active profile alone as operationally ready', () => {
    const doctor = normalizeDoctor({
      id: 'doctor-1',
      fullName: 'BS. Nguyen An',
      status: 'ACTIVE',
    });

    expect(getDoctorReadinessState(doctor).ready).toBeUndefined();
    expect(isDoctorBookable(doctor)).toBe(true);
  });

  it('marks doctors without an active account as not bookable', () => {
    const noAccountDoctor = normalizeDoctor({
      id: 'doctor-1',
      fullName: 'BS. Nguyen An',
      status: 'ACTIVE',
      hasAccount: false,
    });
    const inactiveAccountDoctor = normalizeDoctor({
      id: 'doctor-2',
      fullName: 'BS. Tran Binh',
      status: 'ACTIVE',
      hasAccount: true,
      accountStatus: 'INACTIVE',
    });

    expect(getDoctorReadinessState(noAccountDoctor).label).toBe('Chưa sẵn sàng: chưa cấp tài khoản');
    expect(isDoctorBookable(noAccountDoctor)).toBe(false);
    expect(getDoctorReadinessState(inactiveAccountDoctor).label).toBe('Chưa sẵn sàng: tài khoản chưa hoạt động');
    expect(isDoctorBookable(inactiveAccountDoctor)).toBe(false);
  });

  it('keeps public bookability based on public-safe readiness fields', () => {
    const publicBookableDoctor = normalizeDoctor({
      id: 'doctor-1',
      fullName: 'BS. Nguyen An',
      hasAccount: false,
      bookable: true,
    });
    const publicOperationalDoctor = normalizeDoctor({
      id: 'doctor-2',
      fullName: 'BS. Tran Binh',
      hasAccount: false,
      operationalReady: true,
    });
    const publicBlockedDoctor = normalizeDoctor({
      id: 'doctor-3',
      fullName: 'BS. Le Chi',
      bookable: false,
      operationalReady: true,
    });

    expect(isDoctorBookable(publicBookableDoctor)).toBe(false);
    expect(isPublicDoctorBookable(publicBookableDoctor)).toBe(true);
    expect(isPublicDoctorBookable(publicOperationalDoctor)).toBe(true);
    expect(isPublicDoctorBookable(publicBlockedDoctor)).toBe(false);
  });

  it('uses public specialties for public booking links', () => {
    const doctor = normalizeDoctor({
      id: 'doctor-1',
      fullName: 'BS. Nguyen An',
      bookable: true,
      specialtyId: 'legacy-specialty',
      specialtyName: 'Legacy specialty',
      publicSpecialties: [
        {
          specialtyId: 'inactive-specialty',
          specialtyName: 'Inactive specialty',
          status: 'INACTIVE',
          bookable: true,
        },
        {
          specialtyId: 'public-specialty',
          specialtyName: 'Tim mạch',
          status: 'ACTIVE',
          bookable: true,
        },
      ],
    });

    expect(doctor.specialtyId).toBe('inactive-specialty');
    expect(getPublicDoctorBookingSpecialty(doctor)?.id).toBe('public-specialty');
    expect(isPublicDoctorBookableForSpecialty(doctor, 'public-specialty')).toBe(true);
    expect(isPublicDoctorBookableForSpecialty(doctor, 'inactive-specialty')).toBe(false);
    expect(isPublicDoctorBookableForSpecialty(doctor, 'legacy-specialty')).toBe(false);
  });
});

describe('admin master data summary adapters', () => {
  it('maps backend doctor account and readiness summary fields', () => {
    const summary = normalizeDoctorMasterDataSummary({
      summary: {
        totalDoctors: 12,
        activeDoctors: 8,
        inactiveDoctors: 4,
        noAccountDoctors: 3,
        inactiveAccountDoctors: 2,
        blockedAccountDoctors: 1,
        operationalReadyDoctors: 7,
        notOperationalReadyDoctors: 5,
      },
    });

    expect(summary).toMatchObject({
      total: 12,
      activeDoctors: 8,
      inactiveDoctors: 4,
      noAccountDoctors: 3,
      inactiveAccountDoctors: 2,
      blockedAccountDoctors: 1,
      operationalReadyDoctors: 7,
      notOperationalReadyDoctors: 5,
    });
  });

  it('maps backend staff account summary fields', () => {
    const summary = normalizeStaffMasterDataSummary({
      totalStaffs: 10,
      activeStaffs: 6,
      inactiveStaffs: 4,
      noAccountStaffs: 2,
      inactiveAccountStaffs: 1,
      blockedAccountStaffs: 1,
    });

    expect(summary).toMatchObject({
      total: 10,
      activeStaffs: 6,
      inactiveStaffs: 4,
      noAccountStaffs: 2,
      inactiveAccountStaffs: 1,
      blockedAccountStaffs: 1,
    });
  });
});

describe('dashboard adapters', () => {
  it('labels encounter totals as visits instead of unique patients', () => {
    const overview = normalizeDashboardOverview({
      today: {
        totalAppointments: 12,
        totalEncounters: 8,
        arrivedAppointments: 10,
        netPaidRevenue: 700_000,
        grossPaidRevenue: 1_000_000,
        refundedAmount: 300_000,
      },
    });

    expect(overview.totalPatientsToday).toBe(8);
    expect(overview.patientMetricLabel).toBe('Lượt khám');
    expect(overview.totalRevenue).toBe(700_000);
    expect(overview.grossRevenue).toBe(1_000_000);
    expect(overview.refundedAmount).toBe(300_000);
  });

  it('uses unique patient counts when backend provides them', () => {
    const overview = normalizeDashboardOverview({
      uniquePatientsInRange: 5,
      totalEncounters: 9,
      netRevenue: 120_000,
    });

    expect(overview.totalPatientsToday).toBe(5);
    expect(overview.patientMetricLabel).toBe('Bệnh nhân');
  });
});

describe('appointment adapter', () => {
  it('normalizes pre-triage explainability arrays and ignores invalid JSON strings', () => {
    const appointment = normalizeAppointment({
      id: 1,
      code: 'APT-1',
      patientFullName: 'Nguyen Van A',
      patientPhone: '0909000000',
      doctorId: 2,
      doctorName: 'BS. An',
      branchId: 3,
      branchName: 'PrimeCare',
      specialtyId: 4,
      specialtyName: 'Tim mạch',
      visitDate: '2026-05-14',
      slotStart: '09:00:00',
      status: 'REQUESTED',
      chronicConditionOthers: '["Suy thận mạn"]',
      preTriageMatchedTerms: '[{"term":"tức ngực","code":"CHEST_PAIN","label":"Đau ngực"}]',
      preTriageMatchedRules: '{bad json',
      triageAuditLogs: '[{"actorType":"SYSTEM","action":"SUGGEST","toPriority":"URGENT"}]',
    });

    expect(appointment.chronicConditionOthers).toEqual(['Suy thận mạn']);
    expect(appointment.preTriageMatchedTerms?.[0]).toMatchObject({
      term: 'tức ngực',
      code: 'CHEST_PAIN',
    });
    expect(appointment.preTriageMatchedRules).toEqual([]);
    expect(appointment.triageAuditLogs?.[0]).toMatchObject({
      actorType: 'SYSTEM',
      toPriority: 'URGENT',
    });
  });

  it('preserves appointment ETA and late check-in fields for frontend timing', () => {
    const appointment = normalizeAppointment({
      id: 1,
      code: 'APT-1',
      patientFullName: 'Nguyen Van A',
      patientPhone: '0909000000',
      doctorId: 2,
      doctorName: 'BS. An',
      branchId: 3,
      branchName: 'PrimeCare',
      specialtyId: 4,
      specialtyName: 'Tim mạch',
      visitDate: '2026-05-14',
      etaStart: '09:00:00',
      etaEnd: '09:30:00',
      checkedInLate: true,
      lateMinutes: 8,
      status: 'CHECKED_IN',
    });

    expect(appointment.etaStart).toBe('09:00:00');
    expect(appointment.etaEnd).toBe('09:30:00');
    expect(appointment.slotStart).toBe('09:00');
    expect(appointment.slotEnd).toBe('09:30');
    expect(appointment.checkedInLate).toBe(true);
    expect(appointment.lateMinutes).toBe(8);
  });
});

describe('booking restriction adapter', () => {
  it('maps backend currentScore to restriction monthlyScore', () => {
    const restriction = normalizeBookingRestriction({
      id: 'restriction-1',
      patientFullName: 'Nguyen Van A',
      currentScore: 5,
    });

    expect(restriction.monthlyScore).toBe(5);
  });

  it('maps backend scoreSnapshot to restriction monthlyScore', () => {
    const restriction = normalizeBookingRestriction({
      id: 'restriction-1',
      patientFullName: 'Nguyen Van A',
      scoreSnapshot: 5,
    });

    expect(restriction.monthlyScore).toBe(5);
  });

  it('maps backend score fields to summary monthlyScore', () => {
    expect(
      normalizeBookingRestrictionSummary({
        patientId: 'patient-1',
        currentScore: 5,
        level: 'WARNING',
        restricted: true,
      })?.monthlyScore,
    ).toBe(5);

    const summary = normalizeBookingRestrictionSummary({
      patientId: 'patient-1',
      scoreSnapshot: 5,
      level: 'WARNING',
      restricted: true,
    });

    expect(summary?.monthlyScore).toBe(5);
    expect(summary?.restricted).toBe(true);
  });
});

describe('doctor cancellation recovery adapter', () => {
  it('normalizes public reschedule offer appointment slots', () => {
    const offer = normalizeRescheduleOffer({
      token: 'token-1',
      status: 'HELD',
      expiresAt: '2026-05-14T15:00:00Z',
      sameDoctor: true,
      sameSpecialty: true,
      originalAppointment: {
        id: 1,
        doctorName: 'BS. An',
        specialtyName: 'Tim mạch',
        visitDate: '2026-05-14',
        slotStart: '09:00:00',
        slotEnd: '09:30:00',
      },
      proposedAppointment: {
        id: 2,
        doctorName: 'BS. An',
        specialtyName: 'Tim mạch',
        visitDate: '2026-05-15',
        slotStart: '10:00:00',
      },
    });

    expect(offer.status).toBe('HELD');
    expect(offer.sameDoctor).toBe(true);
    expect(offer.originalAppointment?.slotStart).toBe('09:00');
    expect(offer.proposedAppointment?.slotStart).toBe('10:00');
  });

  it('normalizes follow-up queue items without crashing on missing doctor cancellation fields', () => {
    const item = normalizeFollowUpQueueItem({
      id: 'fu-1',
      appointmentId: 'apt-1',
      followUpType: 'DOCTOR_CANCELLATION_CONTACT_REQUESTED',
      patientFullName: 'Nguyen Van A',
      holdStatus: 'CONTACT_REQUESTED',
      originalVisitDate: '2026-05-14',
      originalSlotStart: '09:00:00',
      heldSlotStart: '10:00:00',
    });

    expect(item.followUpType).toBe('DOCTOR_CANCELLATION_CONTACT_REQUESTED');
    expect(item.holdStatus).toBe('CONTACT_REQUESTED');
    expect(item.originalSlotStart).toBe('09:00');
    expect(item.heldSlotStart).toBe('10:00');
  });
});
