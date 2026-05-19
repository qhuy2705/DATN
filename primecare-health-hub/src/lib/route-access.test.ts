import { describe, expect, it } from 'vitest';
import { canAccessInternalRoute, getInternalRouteRoles } from './route-access';
import type { AppRole } from '@/types/api';

const nonSystemAdminRoles: AppRole[] = [
  'OPERATIONS_ADMIN',
  'STAFF',
  'DOCTOR',
  'CASHIER',
  'PHARMACIST',
  'SERVICE_TECHNICIAN',
  'PATIENT',
];

describe('rate limit route access', () => {
  it('allows only SYSTEM_ADMIN to access rate limit management', () => {
    expect(getInternalRouteRoles('/app/admin/rate-limits')).toEqual(['SYSTEM_ADMIN']);
    expect(canAccessInternalRoute('/app/admin/rate-limits', 'SYSTEM_ADMIN')).toBe(true);

    nonSystemAdminRoles.forEach((role) => {
      expect(canAccessInternalRoute('/app/admin/rate-limits', role)).toBe(false);
    });
  });
});
