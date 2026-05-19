import { MemoryRouter } from 'react-router-dom';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import BookingPage from '@/pages/public/BookingPage';

Element.prototype.scrollIntoView = vi.fn();

const mocks = vi.hoisted(() => ({
  currentUser: null as { id?: string; role?: string; patientId?: string; email?: string; emailVerified?: boolean } | null,
  patientProfile: undefined as
    | {
        id: string;
        fullName: string;
        phone: string;
        email: string;
        emailVerified?: boolean;
        dob: string;
        gender: string;
      }
    | undefined,
  createPublicAppointmentMock: vi.fn(),
  requestBookingEmailOtpMock: vi.fn(),
  verifyBookingEmailOtpMock: vi.fn(),
}));

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
  useCurrentUser: () => mocks.currentUser,
}));

vi.mock('@/hooks/use-patient-portal', () => ({
  usePatientProfile: () => ({
    data: mocks.patientProfile,
    isLoading: false,
    isError: false,
  }),
}));

vi.mock('sonner', () => ({
  toast: {
    error: vi.fn(),
    success: vi.fn(),
    info: vi.fn(),
  },
}));

vi.mock('@/hooks/use-public-data', () => {
  const branch = { id: 'branch-1', name: 'PrimeCare Quận 1', phone: '1900 1234' };
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
      mutateAsync: mocks.createPublicAppointmentMock,
      isPending: false,
    }),
    useRequestBookingEmailOtp: () => ({
      mutateAsync: mocks.requestBookingEmailOtpMock,
      isPending: false,
    }),
    useVerifyBookingEmailOtp: () => ({
      mutateAsync: mocks.verifyBookingEmailOtpMock,
      isPending: false,
    }),
  };
});

const aiBookingEntry = {
  pathname: '/booking',
  search: '?prefill=1&source=AI_ASSISTANT&aiDraft=1',
  state: {
    aiBookingDraft: {
      source: 'AI_ASSISTANT',
      slotId: 'slot-0900',
      doctorId: 'doctor-1',
      doctorName: 'BS. Nguyễn An',
      specialtyId: 'specialty-1',
      specialtyName: 'Tim mạch',
      facilityId: 'branch-1',
      facilityName: 'PrimeCare Quận 1',
      facilityAddress: '12 Nguyễn Huệ, Quận 1',
      appointmentDate: '2026-05-13',
      startTime: '09:00',
      endTime: '09:30',
    },
  },
};

function renderAiBookingPage() {
  render(
    <MemoryRouter initialEntries={[aiBookingEntry]}>
      <BookingPage />
    </MemoryRouter>,
  );
}

function getInputByLabel(label: RegExp) {
  const labelElement = screen.getByText(label, { selector: 'label' });
  const input = labelElement.parentElement?.querySelector('input');
  if (!input) throw new Error(`Input not found for ${label}`);
  return input;
}

async function selectGender() {
  fireEvent.click(screen.getByText('Chọn giới tính'));
  fireEvent.click(await screen.findByRole('option', { name: 'Nam' }));
}

async function fillGuestPatientDetails(email = 'guest@example.com') {
  await waitFor(() => {
    expect(screen.getByRole('heading', { name: /Thông tin bệnh nhân/i })).toBeInTheDocument();
  });

  fireEvent.change(getInputByLabel(/Họ và tên/), { target: { value: 'Nguyễn Văn Guest' } });
  fireEvent.change(getInputByLabel(/Số điện thoại/), { target: { value: '0901234567' } });
  fireEvent.change(getInputByLabel(/^Email/), { target: { value: email } });
  fireEvent.change(getInputByLabel(/Ngày sinh/), { target: { value: '1990-01-01' } });
  await selectGender();

  await waitFor(() => {
    expect(screen.getByRole('button', { name: /Xem lại yêu cầu/i })).toBeEnabled();
  });
}

async function goToReviewStep() {
  await waitFor(() => {
    expect(screen.getByRole('button', { name: /Xem lại yêu cầu/i })).toBeEnabled();
  });
  fireEvent.click(screen.getByRole('button', { name: /Xem lại yêu cầu/i }));

  await waitFor(() => {
    expect(screen.getByRole('heading', { name: /Gửi yêu cầu đặt lịch/i })).toBeInTheDocument();
  });
}

describe('BookingPage smoke', () => {
  beforeEach(() => {
    mocks.currentUser = null;
    mocks.patientProfile = undefined;
    mocks.createPublicAppointmentMock.mockReset();
    mocks.requestBookingEmailOtpMock.mockReset();
    mocks.verifyBookingEmailOtpMock.mockReset();
  });

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

  it('keeps an AI booking draft and advances to patient details without reselecting schedule', async () => {
    render(
      <MemoryRouter
        initialEntries={[
          {
            pathname: '/booking',
            search: '?prefill=1&source=AI_ASSISTANT&aiDraft=1',
            state: {
              aiBookingDraft: {
                source: 'AI_ASSISTANT',
                slotId: 'slot-0900',
                doctorId: 'doctor-1',
                doctorName: 'BS. Nguyễn An',
                specialtyId: 'specialty-1',
                specialtyName: 'Tim mạch',
                facilityId: 'branch-1',
                facilityName: 'PrimeCare Quận 1',
                facilityAddress: '12 Nguyễn Huệ, Quận 1',
                appointmentDate: '2026-05-13',
                startTime: '09:00',
                endTime: '09:30',
              },
            },
          },
        ]}
      >
        <BookingPage />
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /Thông tin bệnh nhân|Patient details/i })).toBeInTheDocument();
    });

    expect(screen.getByText(/Lịch này được chọn từ PrimeCare AI|This schedule was selected from PrimeCare AI/i)).toBeInTheDocument();
    expect(screen.getByText('Sàng lọc sơ bộ')).toBeInTheDocument();
    expect(screen.queryByText('Kiểm tra trước khi tiếp tục')).not.toBeInTheDocument();
    expect(screen.getByText(/Đây không phải chẩn đoán y tế/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /\+ Thêm bệnh nền khác/i })).toBeInTheDocument();
    expect(screen.getAllByText(/09:00/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/BS\. Nguyễn An/).length).toBeGreaterThan(0);
  });

  it('shows staff assistance dialog without a generic error toast when backend requires staff confirmation', async () => {
    const { toast } = await import('sonner');
    mocks.currentUser = {
      id: 'user-1',
      role: 'PATIENT',
      patientId: 'patient-1',
      email: 'patient@example.com',
      emailVerified: true,
    };
    mocks.patientProfile = {
      id: 'patient-1',
      fullName: 'Nguyễn Văn A',
      phone: '0901234567',
      email: 'patient@example.com',
      emailVerified: true,
      dob: '1990-01-01',
      gender: 'MALE',
    };
    mocks.createPublicAppointmentMock.mockRejectedValueOnce({
      response: {
        data: {
          code: 'BOOKING_REQUIRES_STAFF_ASSISTANCE',
          message: 'Internal restriction detail that must not be shown',
        },
      },
    });

    render(
      <MemoryRouter
        initialEntries={[
          {
            pathname: '/booking',
            search: '?prefill=1&source=AI_ASSISTANT&aiDraft=1',
            state: {
              aiBookingDraft: {
                source: 'AI_ASSISTANT',
                slotId: 'slot-0900',
                doctorId: 'doctor-1',
                doctorName: 'BS. Nguyễn An',
                specialtyId: 'specialty-1',
                specialtyName: 'Tim mạch',
                facilityId: 'branch-1',
                facilityName: 'PrimeCare Quận 1',
                facilityAddress: '12 Nguyễn Huệ, Quận 1',
                appointmentDate: '2026-05-13',
                startTime: '09:00',
                endTime: '09:30',
              },
            },
          },
        ]}
      >
        <BookingPage />
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /Thông tin bệnh nhân/i })).toBeInTheDocument();
    });

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Xem lại yêu cầu/i })).toBeEnabled();
    });
    fireEvent.click(screen.getByRole('button', { name: /Xem lại yêu cầu/i }));

    fireEvent.click(screen.getByRole('button', { name: /^Gửi yêu cầu$/i }));

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByRole('heading', { name: 'Cần nhân viên hỗ trợ xác nhận' })).toBeInTheDocument();
    expect(
      within(dialog).getByText(
        'Yêu cầu đặt lịch này cần được phòng khám hỗ trợ xác nhận trực tiếp để đảm bảo thông tin liên hệ và thời gian khám phù hợp. Vui lòng liên hệ hotline hoặc để lại yêu cầu để nhân viên hỗ trợ.',
      ),
    ).toBeInTheDocument();
    expect(within(dialog).getByRole('link', { name: 'Liên hệ hotline' })).toHaveAttribute('href', 'tel:19001234');
    expect(within(dialog).getByRole('button', { name: 'Quay lại chọn lịch' })).toBeInTheDocument();
    expect(dialog.textContent).not.toMatch(/violation|bị phạt|bị cấm|bị khóa|restriction|banned/i);
    expect(toast.error).not.toHaveBeenCalled();
    expect(toast.success).not.toHaveBeenCalled();
    expect(screen.queryByText(/Yêu cầu đặt lịch của bạn đã được ghi nhận/i)).not.toBeInTheDocument();
  });

  it('requires guest email OTP before submitting and sends the verification token', async () => {
    mocks.requestBookingEmailOtpMock.mockResolvedValueOnce({
      verificationId: 'booking-email-verification-1',
      maskedEmail: 'g***@example.com',
      resendAvailableInSeconds: 0,
    });
    mocks.verifyBookingEmailOtpMock.mockResolvedValueOnce({
      bookingEmailVerificationToken: 'booking-email-token-1',
    });
    mocks.createPublicAppointmentMock.mockResolvedValueOnce({ id: 'appointment-1' });

    renderAiBookingPage();
    await fillGuestPatientDetails();
    await goToReviewStep();

    expect(screen.getByRole('button', { name: /^Gửi yêu cầu$/i })).toBeDisabled();

    fireEvent.click(screen.getByRole('button', { name: 'Gửi mã xác thực' }));
    await waitFor(() => {
      expect(mocks.requestBookingEmailOtpMock).toHaveBeenCalledWith({ email: 'guest@example.com' });
    });

    fireEvent.change(screen.getByLabelText('Mã xác thực email'), { target: { value: '123456' } });
    fireEvent.click(screen.getByRole('button', { name: 'Xác thực email' }));

    await waitFor(() => {
      expect(mocks.verifyBookingEmailOtpMock).toHaveBeenCalledWith({
        verificationId: 'booking-email-verification-1',
        otp: '123456',
      });
    });

    await waitFor(() => {
      expect(screen.getByText('Email đã được xác thực.')).toBeInTheDocument();
    });

    const submitButton = screen.getByRole('button', { name: /^Gửi yêu cầu$/i });
    expect(submitButton).toBeEnabled();
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(mocks.createPublicAppointmentMock).toHaveBeenCalledWith(
        expect.objectContaining({
          patientEmail: 'guest@example.com',
          bookingEmailVerificationToken: 'booking-email-token-1',
        }),
      );
    });
  });

  it('clears guest email verification when the contact email changes', async () => {
    mocks.requestBookingEmailOtpMock.mockResolvedValueOnce({
      verificationId: 'booking-email-verification-1',
      maskedEmail: 'g***@example.com',
      resendAvailableInSeconds: 0,
    });
    mocks.verifyBookingEmailOtpMock.mockResolvedValueOnce({
      bookingEmailVerificationToken: 'booking-email-token-1',
    });

    renderAiBookingPage();
    await fillGuestPatientDetails();
    await goToReviewStep();

    fireEvent.click(screen.getByRole('button', { name: 'Gửi mã xác thực' }));
    await waitFor(() => {
      expect(mocks.requestBookingEmailOtpMock).toHaveBeenCalledWith({ email: 'guest@example.com' });
    });
    fireEvent.change(screen.getByLabelText('Mã xác thực email'), { target: { value: '123456' } });
    fireEvent.click(screen.getByRole('button', { name: 'Xác thực email' }));

    await waitFor(() => {
      expect(screen.getByText('Email đã được xác thực.')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: /^Quay lại$/i }));
    fireEvent.change(getInputByLabel(/^Email/), { target: { value: 'new-guest@example.com' } });
    await goToReviewStep();

    expect(screen.queryByText('Email đã được xác thực.')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^Gửi yêu cầu$/i })).toBeDisabled();
  });

  it('lets a logged-in verified user submit without booking email OTP when using the account email', async () => {
    mocks.currentUser = {
      id: 'user-1',
      role: 'PATIENT',
      patientId: 'patient-1',
      email: 'patient@example.com',
      emailVerified: true,
    };
    mocks.patientProfile = {
      id: 'patient-1',
      fullName: 'Nguyễn Văn A',
      phone: '0901234567',
      email: 'patient@example.com',
      emailVerified: true,
      dob: '1990-01-01',
      gender: 'MALE',
    };
    mocks.createPublicAppointmentMock.mockResolvedValueOnce({ id: 'appointment-1' });

    renderAiBookingPage();
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /Thông tin bệnh nhân/i })).toBeInTheDocument();
    });
    await goToReviewStep();

    expect(screen.getByText('Email đã được xác thực.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Gửi mã xác thực' })).not.toBeInTheDocument();

    const submitButton = screen.getByRole('button', { name: /^Gửi yêu cầu$/i });
    expect(submitButton).toBeEnabled();
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(mocks.createPublicAppointmentMock).toHaveBeenCalledWith(
        expect.not.objectContaining({
          bookingEmailVerificationToken: expect.any(String),
        }),
      );
    });
  });
});
