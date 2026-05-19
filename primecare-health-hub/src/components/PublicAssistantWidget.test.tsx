import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { toast } from 'sonner';
import { PublicAssistantWidget } from '@/components/PublicAssistantWidget';
import { AI_BOOKING_DRAFT_STORAGE_KEY } from '@/lib/ai-booking-draft';
import type { PublicAssistantAvailableSlot, PublicAssistantBookingDraft } from '@/types/api';

const mocks = vi.hoisted(() => ({
  mutateAsync: vi.fn(),
  navigate: vi.fn(),
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    i18n: {
      language: 'vi',
      changeLanguage: () => Promise.resolve(),
    },
  }),
}));

vi.mock('@/hooks/use-auth', () => ({
  useCurrentUser: () => ({
    id: 'user-1',
    role: 'PATIENT',
    patientId: 'patient-1',
    fullName: 'Nguyễn Văn B',
  }),
}));

vi.mock('@/hooks/use-public-data', () => ({
  useAskPublicAssistant: () => ({
    mutateAsync: mocks.mutateAsync,
    isPending: false,
  }),
}));

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>();
  return {
    ...actual,
    useNavigate: () => mocks.navigate,
  };
});

vi.mock('sonner', () => ({
  toast: {
    error: vi.fn(),
    success: vi.fn(),
  },
}));

function renderAssistant() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <PublicAssistantWidget />
    </MemoryRouter>,
  );
}

function openAssistant() {
  fireEvent.click(screen.getByRole('button', { name: /PrimeCare AI/i }));
  return screen.getByPlaceholderText(/Mô tả triệu chứng/i);
}

function sendQuestion(input: HTMLElement, value: string) {
  fireEvent.change(input, { target: { value } });
  fireEvent.keyDown(input, { key: 'Enter', code: 'Enter' });
}

const MEMORY_STORAGE_KEY = 'primecare_public_ai_context_v1';

function createBookingDraft(overrides: Partial<PublicAssistantBookingDraft> = {}): PublicAssistantBookingDraft {
  return {
    source: 'AI_ASSISTANT',
    slotId: 'slot-nav',
    doctorId: 'doctor-nav',
    doctorName: 'BS. Nguyễn An',
    specialtyId: 'specialty-nav',
    specialtyName: 'Tiêu hóa',
    facilityId: 'branch-nav',
    facilityName: 'PrimeCare Quận 1',
    facilityAddress: '12 Nguyễn Huệ, Quận 1',
    appointmentDate: '2026-05-13',
    startTime: '09:00',
    endTime: '09:30',
    ...overrides,
  };
}

describe('PublicAssistantWidget payload', () => {
  beforeEach(() => {
    mocks.mutateAsync.mockReset();
    mocks.navigate.mockReset();
    vi.mocked(toast.error).mockClear();
    window.sessionStorage.clear();
    Element.prototype.scrollIntoView = vi.fn();
  });

  it('keeps full assistant text in UI but sends truncated history with structured context', async () => {
    const longAnswer = 'Đoạn trả lời dài từ AI về triệu chứng và hướng đặt lịch. '.repeat(35);
    renderAssistant();
    const input = openAssistant();

    mocks.mutateAsync
      .mockResolvedValueOnce({
        conversationId: 'conv-1',
        provider: 'GEMINI_2_5_FLASH',
        message: longAnswer,
        context: {
          specialtyId: 'specialty-1',
          doctorId: 'doctor-1',
          facilityId: 'branch-1',
        },
        suggestedDoctor: {
          doctorId: 'doctor-1',
          doctorName: 'BS. Nguyễn An',
          specialtyId: 'specialty-1',
          specialtyName: 'Tiêu hóa',
          facilityId: 'branch-1',
          facilityName: 'PrimeCare Quận 1',
        },
      })
      .mockResolvedValueOnce({
        conversationId: 'conv-1',
        message: 'PrimeCare Quận 1 ở 12 Nguyễn Huệ, Quận 1.',
        context: {
          specialtyId: 'specialty-1',
          doctorId: 'doctor-1',
          facilityId: 'branch-1',
        },
      });

    sendQuestion(input, 'Tôi đau dạ dày');
    await waitFor(() => expect(mocks.mutateAsync).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(document.body.textContent).toContain(longAnswer.trim()));
    expect(screen.getAllByText('PrimeCare AI').length).toBeGreaterThan(0);
    expect(document.body.textContent).not.toContain('Gemini');

    sendQuestion(input, 'Cơ sở này ở đâu?');
    await waitFor(() => expect(mocks.mutateAsync).toHaveBeenCalledTimes(2));

    const followUpPayload = mocks.mutateAsync.mock.calls[1][0];
    const assistantHistory = followUpPayload.history.filter((entry: { role: string }) => entry.role === 'assistant');

    expect(followUpPayload.question).toBe('Cơ sở này ở đâu?');
    expect(followUpPayload.conversationId).toBe('conv-1');
    expect(followUpPayload.context.facilityId).toBe('branch-1');
    expect(followUpPayload.currentFacilityId).toBe('branch-1');
    expect(followUpPayload.history.length).toBeLessThanOrEqual(6);
    expect(assistantHistory.every((entry: { text: string }) => entry.text.length <= 300)).toBe(true);
    expect(JSON.stringify(followUpPayload.history)).not.toContain(longAnswer);
  });

  it('does not render or resend technical JSON blocks from assistant text', async () => {
    const rawAnswer = [
      'Tôi đã kiểm tra dữ liệu phù hợp.',
      '```json{"slotId":"slot-json","doctorId":"doctor-json"}```',
      'Bạn có thể hỏi tiếp.',
    ].join('\n');
    renderAssistant();
    const input = openAssistant();

    mocks.mutateAsync
      .mockResolvedValueOnce({
        conversationId: 'conv-json',
        message: rawAnswer,
      })
      .mockResolvedValueOnce({
        conversationId: 'conv-json',
        message: 'PrimeCare Quận 1 ở 12 Nguyễn Huệ, Quận 1.',
      });

    sendQuestion(input, 'Tôi đau dạ dày');
    await waitFor(() => expect(screen.getByText(/Tôi đã kiểm tra dữ liệu phù hợp/i)).toBeInTheDocument());
    expect(document.body.textContent).not.toContain('```json');
    expect(document.body.textContent).not.toContain('slot-json');

    sendQuestion(input, 'Cơ sở này ở đâu?');
    await waitFor(() => expect(mocks.mutateAsync).toHaveBeenCalledTimes(2));

    const followUpPayload = mocks.mutateAsync.mock.calls[1][0];
    expect(JSON.stringify(followUpPayload.history)).not.toContain('```json');
    expect(JSON.stringify(followUpPayload.history)).not.toContain('slot-json');
  });

  it('sends slotId with full slot context and navigates only after receiving bookingDraft', async () => {
    const slot: PublicAssistantAvailableSlot = {
      slotId: 'SLOT|1|2|3|2026-05-13|AM|09:00',
      doctorId: '3',
      doctorName: 'BS. Nguyễn An',
      specialtyId: '2',
      specialtyName: 'Tiêu hóa',
      facilityId: '1',
      facilityName: 'PrimeCare Quận 1',
      facilityAddress: '12 Nguyễn Huệ, Quận 1',
      appointmentDate: '2026-05-13',
      startTime: '09:00',
      endTime: '09:30',
      displayLabel: '09:00',
    };
    const backendDraft = {
      source: 'AI_ASSISTANT',
      slotId: 'SLOT|1|2|3|2026-05-13|AM|09:00',
      doctorId: 3,
      doctorName: 'BS. Nguyễn An',
      specialtyId: 2,
      specialtyName: 'Tiêu hóa',
      facilityId: 1,
      facilityName: 'PrimeCare Quận 1',
      facilityAddress: '12 Nguyễn Huệ, Quận 1',
      appointmentDate: '2026-05-13',
      startTime: '09:00',
      endTime: '09:30',
    };
    const normalizedDraft: PublicAssistantBookingDraft = {
      source: 'AI_ASSISTANT',
      slotId: 'SLOT|1|2|3|2026-05-13|AM|09:00',
      doctorId: '3',
      doctorName: 'BS. Nguyễn An',
      specialtyId: '2',
      specialtyName: 'Tiêu hóa',
      facilityId: '1',
      facilityName: 'PrimeCare Quận 1',
      facilityAddress: '12 Nguyễn Huệ, Quận 1',
      appointmentDate: '2026-05-13',
      startTime: '09:00',
      endTime: '09:30',
    };
    renderAssistant();
    const input = openAssistant();

    mocks.mutateAsync
      .mockResolvedValueOnce({
        conversationId: 'conv-slot',
        provider: 'CONTROLLED_AI_BOOKING_ASSISTANT',
        message: 'Tôi tìm thấy ca gần nhất.',
        context: {
          specialtyId: '2',
          doctorId: '3',
          facilityId: '1',
          lastAvailableSlots: [slot],
        },
        suggestedDoctor: {
          doctorId: '3',
          doctorName: 'BS. Nguyễn An',
          specialtyId: '2',
          specialtyName: 'Tiêu hóa',
          facilityId: '1',
          facilityName: 'PrimeCare Quận 1',
        },
        availableSlots: [slot],
      })
      .mockResolvedValueOnce({
        conversationId: 'conv-slot',
        message: 'Tôi sẽ chuyển bạn sang màn đặt lịch.',
        bookingDraft: backendDraft as unknown as PublicAssistantBookingDraft,
      });

    sendQuestion(input, 'Tôi đau dạ dày');
    await waitFor(() => expect(screen.getByRole('button', { name: /Chọn ca khám 09:00/i })).toBeInTheDocument());
    expect(document.body.textContent).not.toContain('CONTROLLED_AI_BOOKING_ASSISTANT');

    fireEvent.click(screen.getByRole('button', { name: /Chọn ca khám 09:00/i }));
    await waitFor(() => expect(mocks.mutateAsync).toHaveBeenCalledTimes(2));

    const slotPayload = mocks.mutateAsync.mock.calls[1][0];
    expect(slotPayload.actionType).toBe('SELECT_SLOT');
    expect(slotPayload.slotId).toBe('SLOT|1|2|3|2026-05-13|AM|09:00');
    expect(slotPayload.selectedSlot).toEqual(slot);
    expect(slotPayload.context.facilityId).toBe('1');
    expect(mocks.navigate).toHaveBeenCalledWith(
      expect.stringContaining('/booking?'),
      expect.objectContaining({
        state: { aiBookingDraft: normalizedDraft },
      }),
    );
    expect(mocks.navigate.mock.calls[0][0]).toContain('branchId=1');
    expect(mocks.navigate.mock.calls[0][0]).toContain('specialtyId=2');
    expect(mocks.navigate.mock.calls[0][0]).toContain('doctorId=3');

    const persistedDraft = JSON.parse(window.sessionStorage.getItem(AI_BOOKING_DRAFT_STORAGE_KEY) ?? '{}');
    expect(persistedDraft).toMatchObject({
      doctorId: '3',
      specialtyId: '2',
      facilityId: '1',
    });
    expect(document.body.textContent).not.toContain(
      'PrimeCare AI chưa thể chuẩn bị thông tin đặt lịch. Bạn vui lòng thử chọn lại ca khám.',
    );
  });

  it('uses the current response booking draft when BOOK_APPOINTMENT action has no draft payload', async () => {
    const draft = createBookingDraft({ doctorId: 'doctor-response', slotId: 'slot-response' });
    renderAssistant();
    const input = openAssistant();

    mocks.mutateAsync.mockResolvedValueOnce({
      conversationId: 'conv-response-draft',
      message: 'Tôi đã chuẩn bị bản nháp đặt lịch.',
      autoNavigate: false,
      bookingDraft: draft,
      actions: [{ type: 'BOOK_APPOINTMENT', label: 'Đặt lịch' }],
    });

    sendQuestion(input, 'Tôi muốn đặt lịch');
    await waitFor(() => expect(screen.getByRole('button', { name: 'Đặt lịch' })).toBeInTheDocument());
    expect(mocks.navigate).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'Đặt lịch' }));
    await waitFor(() => expect(mocks.navigate).toHaveBeenCalled());

    expect(mocks.navigate).toHaveBeenCalledWith(
      expect.stringContaining('/booking?'),
      expect.objectContaining({
        state: {
          aiBookingDraft: expect.objectContaining({
            doctorId: 'doctor-response',
            slotId: 'slot-response',
          }),
        },
      }),
    );
  });

  it('prioritizes action payload bookingDraft for booking navigation', async () => {
    const responseDraft = createBookingDraft({ doctorId: 'doctor-response', slotId: 'slot-response' });
    const actionDraft = createBookingDraft({ doctorId: 'doctor-payload', slotId: 'slot-payload' });
    renderAssistant();
    const input = openAssistant();

    mocks.mutateAsync.mockResolvedValueOnce({
      conversationId: 'conv-action-draft',
      message: 'Bạn có thể tiếp tục đặt lịch.',
      autoNavigate: false,
      bookingDraft: responseDraft,
      actions: [
        {
          type: 'BOOK_APPOINTMENT',
          label: 'Đặt lịch từ payload',
          payload: { bookingDraft: actionDraft },
        },
      ],
    });

    sendQuestion(input, 'Tôi chọn ca này');
    await waitFor(() => expect(screen.getByRole('button', { name: 'Đặt lịch từ payload' })).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: 'Đặt lịch từ payload' }));
    await waitFor(() => expect(mocks.navigate).toHaveBeenCalled());

    expect(mocks.navigate).toHaveBeenCalledWith(
      expect.stringContaining('/booking?'),
      expect.objectContaining({
        state: {
          aiBookingDraft: expect.objectContaining({
            doctorId: 'doctor-payload',
            slotId: 'slot-payload',
          }),
        },
      }),
    );
  });

  it('uses assistantMemory.pendingBookingDraft when BOOK_APPOINTMENT action has no draft payload', async () => {
    const pendingDraft = createBookingDraft({ doctorId: 'doctor-memory', slotId: 'slot-memory' });
    window.sessionStorage.setItem(MEMORY_STORAGE_KEY, JSON.stringify({ pendingBookingDraft: pendingDraft }));
    renderAssistant();
    const input = openAssistant();

    mocks.mutateAsync.mockResolvedValueOnce({
      conversationId: 'conv-memory-draft',
      message: 'Bạn có thể tiếp tục đặt lịch.',
      actions: [{ type: 'BOOK_APPOINTMENT', label: 'Đặt lịch từ bộ nhớ' }],
    });

    sendQuestion(input, 'Tiếp tục đặt lịch');
    await waitFor(() => expect(screen.getByRole('button', { name: 'Đặt lịch từ bộ nhớ' })).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: 'Đặt lịch từ bộ nhớ' }));
    await waitFor(() => expect(mocks.navigate).toHaveBeenCalled());

    expect(mocks.navigate).toHaveBeenCalledWith(
      expect.stringContaining('/booking?'),
      expect.objectContaining({
        state: {
          aiBookingDraft: expect.objectContaining({
            doctorId: 'doctor-memory',
            slotId: 'slot-memory',
          }),
        },
      }),
    );
  });

  it('shows an error and does not use fake prefill query params when BOOK_APPOINTMENT_PREFILL has no full draft', async () => {
    renderAssistant();
    const input = openAssistant();

    mocks.mutateAsync.mockResolvedValueOnce({
      conversationId: 'conv-missing-prefill',
      message: 'Tôi cần thêm thông tin để chuẩn bị đặt lịch.',
      actions: [
        {
          type: 'BOOK_APPOINTMENT_PREFILL',
          label: 'Chuẩn bị đặt lịch',
          payload: {
            specialtyId: 'specialty-1',
            doctorId: 'doctor-1',
            date: '2026-05-13',
          },
        },
      ],
    });

    sendQuestion(input, 'Chuẩn bị đặt lịch');
    await waitFor(() => expect(screen.getByRole('button', { name: 'Chuẩn bị đặt lịch' })).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: 'Chuẩn bị đặt lịch' }));

    expect(mocks.navigate).not.toHaveBeenCalled();
    expect(toast.error).toHaveBeenCalledWith(
      'PrimeCare AI chưa thể chuẩn bị đầy đủ thông tin đặt lịch. Bạn vui lòng chọn lại ca khám.',
    );
  });

  it('opens manual booking for normal BOOK_APPOINTMENT when no draft exists', async () => {
    renderAssistant();
    const input = openAssistant();

    mocks.mutateAsync.mockResolvedValueOnce({
      conversationId: 'conv-manual-booking',
      message: 'Bạn có thể đặt lịch thủ công.',
      actions: [{ type: 'BOOK_APPOINTMENT', label: 'Đặt lịch thủ công', value: '/booking' }],
    });

    sendQuestion(input, 'Đặt lịch thủ công');
    await waitFor(() => expect(screen.getByRole('button', { name: 'Đặt lịch thủ công' })).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: 'Đặt lịch thủ công' }));

    expect(mocks.navigate).toHaveBeenCalledWith('/booking');
  });

  it('does not render stale doctor and slot panels again for facility follow-up responses', async () => {
    const slot: PublicAssistantAvailableSlot = {
      slotId: 'slot-0830',
      doctorId: 'doctor-1',
      doctorName: 'BS. Nguyễn An',
      specialtyId: 'specialty-1',
      specialtyName: 'Tiêu hóa',
      facilityId: 'branch-1',
      facilityName: 'PrimeCare Quận 1',
      facilityAddress: '12 Nguyễn Huệ, Quận 1',
      appointmentDate: '2026-05-13',
      startTime: '08:30',
      endTime: '09:00',
      displayLabel: '08:30',
    };
    renderAssistant();
    const input = openAssistant();

    mocks.mutateAsync
      .mockResolvedValueOnce({
        conversationId: 'conv-address',
        intent: 'RECOMMEND_DOCTOR_AND_SHOW_SLOTS',
        message: 'Tôi gợi ý bác sĩ phù hợp. [08:30] [09:00]',
        context: {
          specialtyId: 'specialty-1',
          doctorId: 'doctor-1',
          facilityId: 'branch-1',
          lastAvailableSlots: [slot],
        },
        suggestedDoctor: {
          doctorId: 'doctor-1',
          doctorName: 'BS. Nguyễn An',
          specialtyId: 'specialty-1',
          specialtyName: 'Tiêu hóa',
          facilityId: 'branch-1',
          facilityName: 'PrimeCare Quận 1',
        },
        availableSlots: [slot],
      })
      .mockResolvedValueOnce({
        conversationId: 'conv-address',
        intent: 'ANSWER_FACILITY_INFO',
        message: 'PrimeCare Quận 1 ở 12 Nguyễn Huệ, Quận 1.',
        context: {
          specialtyId: 'specialty-1',
          doctorId: 'doctor-1',
          facilityId: 'branch-1',
          lastAvailableSlots: [slot],
        },
      });

    sendQuestion(input, 'Tôi đau dạ dày');
    await waitFor(() => expect(screen.getByRole('button', { name: /Chọn ca khám 08:30/i })).toBeInTheDocument());
    expect(document.body.textContent).not.toContain('[08:30] [09:00]');
    expect(screen.getAllByText('BS. Nguyễn An')).toHaveLength(1);

    sendQuestion(input, 'Cơ sở này ở đâu?');
    await waitFor(() => expect(screen.getByText('PrimeCare Quận 1 ở 12 Nguyễn Huệ, Quận 1.')).toBeInTheDocument());

    expect(screen.getAllByRole('button', { name: /Chọn ca khám 08:30/i })).toHaveLength(1);
    expect(screen.getAllByText('BS. Nguyễn An')).toHaveLength(1);
    const followUpPayload = mocks.mutateAsync.mock.calls[1][0];
    expect(followUpPayload.context.facilityId).toBe('branch-1');
    expect(followUpPayload.currentFacilityId).toBe('branch-1');
  });

  it('renders safety responses without repeating stale booking blocks or unsafe suggestions', async () => {
    const slot: PublicAssistantAvailableSlot = {
      slotId: 'slot-1000',
      doctorId: 'doctor-1',
      doctorName: 'BS. Nguyễn An',
      specialtyId: 'specialty-1',
      specialtyName: 'Tiêu hóa',
      facilityId: 'branch-1',
      facilityName: 'PrimeCare Quận 1',
      facilityAddress: '12 Nguyễn Huệ, Quận 1',
      appointmentDate: '2026-05-13',
      startTime: '10:00',
      endTime: '10:30',
      displayLabel: '10:00',
    };
    renderAssistant();
    const input = openAssistant();

    mocks.mutateAsync
      .mockResolvedValueOnce({
        conversationId: 'conv-safety',
        intent: 'RECOMMEND_DOCTOR_AND_SHOW_SLOTS',
        message: 'Tôi đã tìm thấy lựa chọn phù hợp.',
        context: {
          specialtyId: 'specialty-1',
          doctorId: 'doctor-1',
          facilityId: 'branch-1',
          lastAvailableSlots: [slot],
        },
        suggestedDoctor: {
          doctorId: 'doctor-1',
          doctorName: 'BS. Nguyễn An',
          specialtyId: 'specialty-1',
          specialtyName: 'Tiêu hóa',
          facilityId: 'branch-1',
          facilityName: 'PrimeCare Quận 1',
        },
        availableSlots: [slot],
      })
      .mockResolvedValueOnce({
        conversationId: 'conv-safety',
        intent: 'GENERAL_FAQ',
        safetyType: 'MEDICATION_SAFETY_BLOCK',
        provider: 'GEMINI_2_5_FLASH',
        message: 'PrimeCare AI không thể tư vấn thuốc hoặc kê đơn trong cuộc trò chuyện này.',
        suggestedDoctor: {
          doctorId: 'doctor-1',
          doctorName: 'BS. Nguyễn An',
          specialtyId: 'specialty-1',
          specialtyName: 'Tiêu hóa',
          facilityId: 'branch-1',
          facilityName: 'PrimeCare Quận 1',
        },
        availableSlots: [slot],
        actions: [{ type: 'NAVIGATE', label: 'Xem đơn thuốc', value: '/prescriptions' }],
        suggestions: ['Uống thuốc gì?', 'Xem đơn thuốc'],
      });

    sendQuestion(input, 'Tôi đau dạ dày');
    await waitFor(() => expect(screen.getByRole('button', { name: /Chọn ca khám 10:00/i })).toBeInTheDocument());

    sendQuestion(input, 'Tôi đau đầu uống thuốc gì?');
    await waitFor(() => {
      expect(screen.getByText(/không thể tư vấn thuốc/i)).toBeInTheDocument();
    });

    expect(screen.getAllByRole('button', { name: /Chọn ca khám 10:00/i })).toHaveLength(1);
    expect(screen.getAllByText('BS. Nguyễn An')).toHaveLength(1);
    expect(screen.queryByRole('button', { name: /Uống thuốc gì|Xem đơn thuốc/i })).not.toBeInTheDocument();
    expect(document.body.textContent).not.toContain('GEMINI_2_5_FLASH');
  });

  it('shows only continue booking when booking draft auto navigation is disabled', async () => {
    const slot: PublicAssistantAvailableSlot = {
      slotId: 'slot-1430',
      doctorId: 'doctor-1',
      doctorName: 'BS. Nguyễn An',
      specialtyId: 'specialty-1',
      specialtyName: 'Tiêu hóa',
      facilityId: 'branch-1',
      facilityName: 'PrimeCare Quận 1',
      facilityAddress: '12 Nguyễn Huệ, Quận 1',
      appointmentDate: '2026-05-13',
      startTime: '14:30',
      endTime: '15:00',
      displayLabel: '14:30',
    };
    const draft: PublicAssistantBookingDraft = {
      source: 'AI_ASSISTANT',
      ...slot,
      facilityAddress: slot.facilityAddress,
    };
    renderAssistant();
    const input = openAssistant();

    mocks.mutateAsync
      .mockResolvedValueOnce({
        conversationId: 'conv-draft-manual',
        intent: 'RECOMMEND_DOCTOR_AND_SHOW_SLOTS',
        message: 'Tôi đã tìm thấy ca phù hợp.',
        context: {
          specialtyId: 'specialty-1',
          doctorId: 'doctor-1',
          facilityId: 'branch-1',
          lastAvailableSlots: [slot],
        },
        suggestedDoctor: {
          doctorId: 'doctor-1',
          doctorName: 'BS. Nguyễn An',
          specialtyId: 'specialty-1',
          specialtyName: 'Tiêu hóa',
          facilityId: 'branch-1',
          facilityName: 'PrimeCare Quận 1',
        },
        availableSlots: [slot],
      })
      .mockResolvedValueOnce({
        conversationId: 'conv-draft-manual',
        intent: 'BOOKING_DRAFT_CREATED',
        autoNavigate: false,
        message: 'Tôi đã chuẩn bị bản nháp đặt lịch.',
        bookingDraft: draft,
        availableSlots: [slot],
        actions: [
          { type: 'VIEW_MORE_SLOTS', label: 'Xem thêm ca' },
          { type: 'GO_TO_BOOKING', label: 'Tiếp tục đặt lịch', payload: { bookingDraft: draft, slotId: draft.slotId } },
        ],
        suggestions: ['Xem thêm ca', 'Tiếp tục đặt lịch'],
      });

    sendQuestion(input, 'Tôi đau dạ dày');
    await waitFor(() => expect(screen.getByRole('button', { name: /Chọn ca khám 14:30/i })).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: /Chọn ca khám 14:30/i }));
    await waitFor(() => expect(screen.getByRole('button', { name: 'Tiếp tục đặt lịch' })).toBeInTheDocument());

    expect(mocks.navigate).not.toHaveBeenCalled();
    expect(screen.getAllByRole('button', { name: /Chọn ca khám 14:30/i })).toHaveLength(1);
    expect(screen.queryByRole('button', { name: 'Xem thêm ca' })).not.toBeInTheDocument();
  });

  it('does not navigate when booking draft is missing required fields', async () => {
    const invalidDraft = {
      source: 'AI_ASSISTANT',
      slotId: 'slot-invalid',
      doctorId: 'doctor-1',
      doctorName: 'BS. Nguyễn An',
      specialtyId: 'specialty-1',
      specialtyName: 'Tiêu hóa',
      facilityId: 'branch-1',
      facilityName: 'PrimeCare Quận 1',
      appointmentDate: '2026-05-13',
      endTime: '09:30',
    } as unknown as PublicAssistantBookingDraft;
    renderAssistant();
    const input = openAssistant();

    mocks.mutateAsync.mockResolvedValueOnce({
      conversationId: 'conv-invalid-draft',
      intent: 'BOOKING_DRAFT_CREATED',
      message: 'Tôi đã chuẩn bị bản nháp đặt lịch.',
      bookingDraft: invalidDraft,
    });

    sendQuestion(input, 'Tôi chọn 9h');
    await waitFor(() => {
      expect(screen.getByText(/chưa thể chuẩn bị đầy đủ thông tin đặt lịch/i)).toBeInTheDocument();
    });

    expect(mocks.navigate).not.toHaveBeenCalled();
    expect(screen.queryByRole('button', { name: /Tiếp tục đặt lịch/i })).not.toBeInTheDocument();
  });

  it('deduplicates backend actions from quick suggestions and filters unsafe suggestion labels', async () => {
    renderAssistant();
    const input = openAssistant();

    mocks.mutateAsync.mockResolvedValueOnce({
      conversationId: 'conv-actions',
      intent: 'GENERAL_FAQ',
      message: 'Bạn có thể hỏi thêm hoặc đổi khung giờ.',
      actions: [
        { type: 'VIEW_MORE_SLOTS', label: 'Xem thêm ca', payload: { slotId: 'slot-1' } },
        { type: 'VIEW_MORE_SLOTS', label: 'Xem thêm ca', payload: { slotId: 'slot-1' } },
      ],
      suggestions: ['Xem thêm ca', 'Bác sĩ khác thì sao?', 'Uống thuốc gì?'],
    });

    sendQuestion(input, 'Tôi muốn hỏi thêm');
    await waitFor(() => expect(screen.getByText('Bạn có thể hỏi thêm hoặc đổi khung giờ.')).toBeInTheDocument());

    expect(screen.getAllByRole('button', { name: 'Xem thêm ca' })).toHaveLength(1);
    expect(screen.getByRole('button', { name: 'Bác sĩ khác thì sao?' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Uống thuốc gì?' })).not.toBeInTheDocument();
  });
});
