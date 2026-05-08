import { MemoryRouter } from 'react-router-dom';
import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import BookingPage from '@/pages/public/BookingPage';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, options?: { defaultValue?: string }) => options?.defaultValue ?? key,
    i18n: {
      language: 'vi',
      changeLanguage: () => Promise.resolve(),
    },
  }),
}));

vi.mock('@/hooks/use-auth', () => ({
  useCurrentUser: () => null,
}));

vi.mock('@/hooks/use-patient-portal', () => ({
  usePatientProfile: () => ({
    data: undefined,
    isLoading: false,
    isError: false,
  }),
}));

vi.mock('@/hooks/use-public-data', () => {
  const branch = { id: 'branch-1', name: 'PrimeCare Quận 1' };
  const specialty = {
    id: 'specialty-1',
    branchId: 'branch-1',
    branchName: 'PrimeCare Quận 1',
    specialtyId: 'specialty-1',
    specialtyName: 'Tim mạch',
    name: 'Tim mạch',
    consultationFee: 200000,
  };
  const doctor = {
    id: 'doctor-1',
    fullName: 'BS. Nguyễn An',
    branchId: 'branch-1',
    branchName: 'PrimeCare Quận 1',
    specialtyId: 'specialty-1',
    specialtyIds: ['specialty-1'],
    specialtyName: 'Tim mạch',
  };

  return {
    useBranches: () => ({ data: [branch] }),
    useBranchSpecialties: (branchId: string) => ({ data: branchId ? [specialty] : [] }),
    useDoctor: (doctorId: string) => ({ data: doctorId ? doctor : undefined }),
    useDoctors: () => ({
      data: {
        items: [doctor],
        meta: {
          page: 0,
          size: 1000,
          totalItems: 1,
          totalPages: 1,
          hasNext: false,
          hasPrev: false,
        },
      },
    }),
    usePublicAvailabilityRealtime: () => ({
      data: [],
      isLoading: false,
      isError: false,
      isPlaceholderData: false,
      isLiveConnected: false,
      lastSyncAt: null,
    }),
    useCreatePublicAppointment: () => ({
      mutateAsync: vi.fn(),
      isPending: false,
    }),
  };
});

describe('BookingPage smoke', () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it('renders step 1 with next button disabled until branch, specialty and doctor are selected', () => {
    render(
      <MemoryRouter initialEntries={['/booking']}>
        <BookingPage />
      </MemoryRouter>,
    );

    expect(screen.getByRole('heading', { name: /Đặt lịch khám bệnh|Book an appointment/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Chọn giờ khám|Choose time/i })).toBeDisabled();
  });

  it('enables moving to time selection when required selection values are present', () => {
    render(
      <MemoryRouter initialEntries={['/booking?branchId=branch-1&specialtyId=specialty-1&doctorId=doctor-1']}>
        <BookingPage />
      </MemoryRouter>,
    );

    expect(screen.getByRole('button', { name: /Chọn giờ khám|Choose time/i })).toBeEnabled();
    expect(screen.getAllByText('PrimeCare Quận 1').length).toBeGreaterThan(0);
    expect(screen.getAllByText('BS. Nguyễn An').length).toBeGreaterThan(0);
  });
});
