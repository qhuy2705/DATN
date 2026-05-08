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
  PATIENT: '/',
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
  auditLogs: ['SYSTEM_ADMIN', 'OPERATIONS_ADMIN'] as AppRole[],
  appointments: ['OPERATIONS_ADMIN', 'STAFF'] as AppRole[],
  receptionQueue: ['OPERATIONS_ADMIN', 'STAFF'] as AppRole[],
  walkIn: ['OPERATIONS_ADMIN', 'STAFF'] as AppRole[],
  doctor: ['DOCTOR'] as AppRole[],
  cashier: ['CASHIER'] as AppRole[],
  pharmacy: ['PHARMACIST', 'OPERATIONS_ADMIN'] as AppRole[],
  serviceDesk: ['SERVICE_TECHNICIAN', 'OPERATIONS_ADMIN'] as AppRole[],
  patientPortal: ['PATIENT'] as AppRole[],
  account: INTERNAL_ROLES,
};
