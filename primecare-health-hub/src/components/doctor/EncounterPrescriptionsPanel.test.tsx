import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { EncounterPrescriptionsPanel } from '@/components/doctor/EncounterPrescriptionsPanel';
import type { Prescription, PrescriptionStatus } from '@/types/api';

const mocks = vi.hoisted(() => ({
  prescription: undefined as Prescription | undefined,
  mutate: vi.fn(),
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: {
      language: 'vi',
      changeLanguage: () => Promise.resolve(),
    },
  }),
}));

vi.mock('@/hooks/use-doctor-data', () => ({
  useDoctorEncounterPrescriptions: () => ({
    data: {
      items: mocks.prescription ? [mocks.prescription] : [],
    },
    isLoading: false,
  }),
  useCancelPrescription: () => ({
    mutate: mocks.mutate,
    isPending: false,
  }),
}));

vi.mock('./PrescriptionEditorDialog', () => ({
  PrescriptionEditorDialog: () => null,
}));

function createPrescription(status: PrescriptionStatus): Prescription {
  return {
    id: `prescription-${status}`,
    code: `RX-${status}`,
    encounterId: 'encounter-1',
    issuedDate: '2026-05-12T08:00:00.000Z',
    status,
    items: [
      {
        id: `item-${status}`,
        medicationId: 'medication-1',
        medicationName: 'Paracetamol',
        quantity: 10,
        unit: 'viên',
        dose: '1 viên',
        frequency: '2 lần/ngày',
        route: 'Uống',
      },
    ],
  };
}

function renderPanel(status: PrescriptionStatus) {
  mocks.prescription = createPrescription(status);

  render(
    <EncounterPrescriptionsPanel
      encounterId="encounter-1"
      canCreatePrescription
      canEdit
    />,
  );
}

describe('EncounterPrescriptionsPanel cancellation rules', () => {
  beforeEach(() => {
    mocks.prescription = undefined;
    mocks.mutate.mockReset();
  });

  it.each([
    ['DRAFT', true],
    ['ISSUED', true],
    ['PAID', false],
    ['DISPENSED', false],
    ['CANCELLED', false],
  ] satisfies Array<[PrescriptionStatus, boolean]>)(
    'renders cancel button for %s according to backend prescription cancel rule',
    (status, canCancel) => {
      renderPanel(status);

      const cancelButton = screen.queryByRole('button', { name: /Hủy/i });
      if (canCancel) {
        expect(cancelButton).toBeInTheDocument();
      } else {
        expect(cancelButton).not.toBeInTheDocument();
      }
    },
  );
});
