import { beforeEach, describe, expect, it } from 'vitest';
import {
  AI_BOOKING_DRAFT_STORAGE_KEY,
  getAiBookingDraftFromRouteState,
  getBestBookingDraftForNavigation,
  isAiBookingDraft,
  normalizeAiBookingDraft,
  readPersistedAiBookingDraft,
} from '@/lib/ai-booking-draft';

const backendDraft = {
  slotId: 'SLOT|1|2|3|2026-05-13|AM|09:00',
  doctorId: 3,
  doctorName: 'BS. Nguyen An',
  specialtyId: 2,
  specialtyName: 'Tieu hoa',
  branchId: 1,
  facilityName: 'PrimeCare Quan 1',
  visitDate: '2026-05-13',
  slotStart: '09:00',
  endTime: '09:30',
};

describe('AI booking draft helpers', () => {
  beforeEach(() => {
    window.sessionStorage.clear();
  });

  it('normalizes backend numeric IDs and supported field aliases', () => {
    expect(normalizeAiBookingDraft(backendDraft)).toEqual({
      source: 'AI_ASSISTANT',
      slotId: 'SLOT|1|2|3|2026-05-13|AM|09:00',
      doctorId: '3',
      doctorName: 'BS. Nguyen An',
      specialtyId: '2',
      specialtyName: 'Tieu hoa',
      facilityId: '1',
      facilityName: 'PrimeCare Quan 1',
      facilityAddress: undefined,
      appointmentDate: '2026-05-13',
      startTime: '09:00',
      endTime: '09:30',
    });
    expect(isAiBookingDraft(backendDraft)).toBe(true);
    expect(
      normalizeAiBookingDraft({
        ...backendDraft,
        facilityName: undefined,
        branchName: 'PrimeCare Quan 3',
      }),
    ).toMatchObject({
      facilityId: '1',
      facilityName: 'PrimeCare Quan 3',
    });
  });

  it('rejects drafts with missing required fields', () => {
    expect(
      normalizeAiBookingDraft({
        ...backendDraft,
        doctorName: ' ',
      }),
    ).toBeNull();
  });

  it('normalizes persisted and route-state drafts before returning them', () => {
    window.sessionStorage.setItem(AI_BOOKING_DRAFT_STORAGE_KEY, JSON.stringify(backendDraft));

    expect(readPersistedAiBookingDraft()).toMatchObject({
      doctorId: '3',
      specialtyId: '2',
      facilityId: '1',
      appointmentDate: '2026-05-13',
      startTime: '09:00',
    });
    expect(getAiBookingDraftFromRouteState({ aiBookingDraft: backendDraft })).toMatchObject({
      doctorId: '3',
      specialtyId: '2',
      facilityId: '1',
    });
  });

  it('selects the first complete navigation draft in action, response, memory priority', () => {
    const incompleteActionDraft = {
      ...backendDraft,
      startTime: undefined,
      slotStart: undefined,
    };
    const responseDraft = { ...backendDraft, doctorId: 'response-doctor' };
    const memoryDraft = { ...backendDraft, doctorId: 'memory-doctor' };
    const pendingDraft = { ...backendDraft, doctorId: 'pending-doctor' };

    expect(
      getBestBookingDraftForNavigation(
        { payload: { bookingDraft: incompleteActionDraft } },
        responseDraft,
        memoryDraft,
        pendingDraft,
      ),
    ).toMatchObject({ doctorId: 'response-doctor' });
    expect(
      getBestBookingDraftForNavigation(
        { payload: { bookingDraft: { ...backendDraft, doctorId: 'action-doctor' } } },
        responseDraft,
        memoryDraft,
        pendingDraft,
      ),
    ).toMatchObject({ doctorId: 'action-doctor' });
    expect(getBestBookingDraftForNavigation(undefined, undefined, undefined, pendingDraft)).toMatchObject({
      doctorId: 'pending-doctor',
    });
  });
});
