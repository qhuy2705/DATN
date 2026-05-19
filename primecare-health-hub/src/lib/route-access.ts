import type { AppRole } from '@/types/api';

export const INTERNAL_ROLES: AppRole[] = [
  'SYSTEM_ADMIN',
  'OPERATIONS_ADMIN',
  'STAFF',
  'SERVICE_TECHNICIAN',
  'CASHIER',
  'PHARMACIST',
  'DOCTOR',
];

export const DEFAULT_ROLE_HOME: Record<AppRole, string> = {
  SYSTEM_ADMIN: '/app/dashboard',
  OPERATIONS_ADMIN: '/app/dashboard',
  STAFF: '/app/reception/queue',
  SERVICE_TECHNICIAN: '/app/service-desk/results',
  CASHIER: '/app/cashier/invoices',
  PHARMACIST: '/app/pharmacy/dispense',
  DOCTOR: '/app/doctor/appointments',
  PATIENT: '/me',
};

export const ROUTE_ROLES = {
  dashboard: ['SYSTEM_ADMIN', 'OPERATIONS_ADMIN'] as AppRole[],
  branches: ['SYSTEM_ADMIN'] as AppRole[],
  specialties: ['OPERATIONS_ADMIN'] as AppRole[],
  branchSpecialties: ['OPERATIONS_ADMIN'] as AppRole[],
  doctors: ['SYSTEM_ADMIN'] as AppRole[],
  staffs: ['SYSTEM_ADMIN'] as AppRole[],
  patients: ['SYSTEM_ADMIN', 'OPERATIONS_ADMIN', 'STAFF'] as AppRole[],
  medications: ['OPERATIONS_ADMIN'] as AppRole[],
  medicalServices: ['OPERATIONS_ADMIN'] as AppRole[],
  doctorSchedulesAdmin: ['OPERATIONS_ADMIN'] as AppRole[],
  doctorLeavesAdmin: ['OPERATIONS_ADMIN'] as AppRole[],
  auditLogs: ['SYSTEM_ADMIN'] as AppRole[],
  rateLimits: ['SYSTEM_ADMIN'] as AppRole[],
  notifications: ['SYSTEM_ADMIN', 'OPERATIONS_ADMIN'] as AppRole[],
  appointments: ['OPERATIONS_ADMIN', 'STAFF'] as AppRole[],
  appointmentFollowUps: ['STAFF'] as AppRole[],
  bookingRestrictions: ['OPERATIONS_ADMIN'] as AppRole[],
  receptionQueue: ['STAFF'] as AppRole[],
  walkIn: ['STAFF'] as AppRole[],
  doctor: ['DOCTOR'] as AppRole[],
  cashier: ['CASHIER'] as AppRole[],
  pharmacy: ['PHARMACIST'] as AppRole[],
  serviceDesk: ['SERVICE_TECHNICIAN'] as AppRole[],
  patientPortal: ['PATIENT'] as AppRole[],
  account: INTERNAL_ROLES,
};

function normalizePath(path: string) {
  if (!path.startsWith('/')) return '';
  return path.split(/[?#]/)[0];
}

export function getInternalRouteRoles(path: string): AppRole[] | null {
  const normalized = normalizePath(path);
  if (!normalized) return null;

  if (normalized === '/app/dashboard') return ROUTE_ROLES.dashboard;
  if (normalized === '/app/admin/branches') return ROUTE_ROLES.branches;
  if (normalized === '/app/admin/specialties') return ROUTE_ROLES.specialties;
  if (normalized === '/app/admin/branch-specialties') return ROUTE_ROLES.branchSpecialties;
  if (normalized === '/app/admin/doctors') return ROUTE_ROLES.doctors;
  if (normalized === '/app/admin/staffs') return ROUTE_ROLES.staffs;
  if (normalized === '/app/admin/patients') return ROUTE_ROLES.patients;
  if (normalized === '/app/admin/medications') return ROUTE_ROLES.medications;
  if (normalized === '/app/admin/medical-services') return ROUTE_ROLES.medicalServices;
  if (normalized === '/app/admin/doctor-schedules') return ROUTE_ROLES.doctorSchedulesAdmin;
  if (normalized === '/app/admin/doctor-leaves') return ROUTE_ROLES.doctorLeavesAdmin;
  if (normalized === '/app/admin/audit-logs') return ROUTE_ROLES.auditLogs;
  if (normalized === '/app/admin/rate-limits') return ROUTE_ROLES.rateLimits;
  if (normalized === '/app/admin/notifications') return ROUTE_ROLES.notifications;
  if (normalized === '/app/appointments' || /^\/app\/appointments\/[^/]+\/process$/.test(normalized)) {
    return ROUTE_ROLES.appointments;
  }
  if (normalized === '/app/booking-restrictions') return ROUTE_ROLES.bookingRestrictions;
  if (normalized === '/app/appointment-follow-ups') return ROUTE_ROLES.appointmentFollowUps;
  if (normalized === '/app/reception/queue') return ROUTE_ROLES.receptionQueue;
  if (normalized === '/app/reception/walk-in') return ROUTE_ROLES.walkIn;
  if (normalized === '/app/service-desk/results') return ROUTE_ROLES.serviceDesk;
  if (
    normalized === '/app/doctor/appointments' ||
    normalized === '/app/doctor/prescriptions' ||
    normalized === '/app/doctor/schedules' ||
    normalized === '/app/doctor/leave-requests' ||
    /^\/app\/doctor\/encounters\/[^/]+$/.test(normalized)
  ) {
    return ROUTE_ROLES.doctor;
  }
  if (
    normalized === '/app/cashier/invoices' ||
    /^\/app\/cashier\/invoice-pdf-jobs\/[^/]+$/.test(normalized)
  ) {
    return ROUTE_ROLES.cashier;
  }
  if (normalized === '/app/pharmacy/dispense' || normalized === '/app/pharmacy/inventory') {
    return ROUTE_ROLES.pharmacy;
  }
  if (normalized === '/app/account') return ROUTE_ROLES.account;

  return null;
}

export function canAccessInternalRoute(path: string, role?: AppRole) {
  if (!role) return false;
  const roles = getInternalRouteRoles(path);
  return Boolean(roles?.includes(role));
}
