import { describe, expect, it } from 'vitest';
import {
  buildAssistantContextSnapshot,
  buildAssistantHistory,
  MAX_ASSISTANT_HISTORY_TEXT,
  MAX_HISTORY_MESSAGES,
  MAX_USER_HISTORY_TEXT,
  stripAssistantTechnicalJsonBlocks,
} from '@/lib/public-assistant-history';
import type { PublicAssistantAvailableSlot } from '@/types/api';

describe('public assistant API history', () => {
  it('strips technical JSON fences before assistant history is sent', () => {
    const assistantText = [
      'Tôi đã tìm thấy lịch phù hợp.',
      '```json',
      '{"slotId":"slot-1","doctorId":"doctor-1"}',
      '```',
      'Bạn có thể chọn ca bên dưới.',
      '```JSON{"tool":"availability"} ```',
    ].join('\n');

    expect(stripAssistantTechnicalJsonBlocks(assistantText)).toBe(
      'Tôi đã tìm thấy lịch phù hợp.\nBạn có thể chọn ca bên dưới.',
    );

    const history = buildAssistantHistory([
      { role: 'assistant', text: assistantText },
      { role: 'user', text: 'Cơ sở này ở đâu?' },
    ]);

    expect(JSON.stringify(history)).not.toContain('```json');
    expect(JSON.stringify(history)).not.toContain('slot-1');
    expect(history[0].text).toContain('Tôi đã tìm thấy lịch phù hợp.');
  });

  it('keeps full UI text out of API history when an assistant response is long', () => {
    const longAssistantText = 'AI trả lời dài. '.repeat(120);
    const history = buildAssistantHistory([
      { role: 'user', text: 'Tôi đau dạ dày' },
      { role: 'assistant', text: longAssistantText },
      { role: 'user', text: 'Cơ sở này ở đâu?' },
    ]);

    const assistantEntry = history.find((entry) => entry.role === 'assistant');

    expect(longAssistantText.length).toBeGreaterThan(1000);
    expect(assistantEntry?.text.length).toBeLessThanOrEqual(MAX_ASSISTANT_HISTORY_TEXT);
    expect(assistantEntry?.text.endsWith('...')).toBe(true);
    expect(assistantEntry?.text).not.toBe(longAssistantText);
    expect(JSON.stringify(history)).not.toContain(longAssistantText);
  });

  it('limits multi-turn chat history to the latest safe window', () => {
    const messages = Array.from({ length: 10 }, (_, index) => ({
      role: index % 2 === 0 ? 'user' as const : 'assistant' as const,
      text: `Lượt ${index + 1} `.repeat(200),
    }));

    const history = buildAssistantHistory(messages);

    expect(history).toHaveLength(MAX_HISTORY_MESSAGES);
    expect(history[0].text).toContain('Lượt 5');
    expect(history.some((entry) => entry.text.includes('Lượt 1 '))).toBe(false);
    expect(history.every((entry) => entry.text.length <= MAX_USER_HISTORY_TEXT)).toBe(true);
    expect(history.filter((entry) => entry.role === 'assistant').every((entry) => entry.text.length <= MAX_ASSISTANT_HISTORY_TEXT)).toBe(true);
  });

  it('keeps facility and slot context in structured payload fields instead of text history', () => {
    const slots: PublicAssistantAvailableSlot[] = [
      {
        slotId: 'slot-0900',
        doctorId: 'doctor-1',
        doctorName: 'BS. Nguyễn An',
        specialtyId: 'specialty-1',
        specialtyName: 'Tiêu hóa',
        facilityId: 'branch-1',
        facilityName: 'PrimeCare Quận 1',
        appointmentDate: '2026-05-13',
        startTime: '09:00',
        endTime: '09:30',
      },
    ];

    const snapshot = buildAssistantContextSnapshot({
      context: {
        specialtyId: 'specialty-1',
        doctorId: 'doctor-1',
        facilityId: 'branch-1',
        lastAvailableSlots: slots,
      },
      suggestedDoctor: {
        doctorId: 'doctor-1',
        doctorName: 'BS. Nguyễn An',
        specialtyId: 'specialty-1',
        specialtyName: 'Tiêu hóa',
        facilityId: 'branch-1',
        facilityName: 'PrimeCare Quận 1',
      },
    });

    expect(snapshot.currentFacilityId).toBe('branch-1');
    expect(snapshot.currentDoctorId).toBe('doctor-1');
    expect(snapshot.currentSpecialtyId).toBe('specialty-1');
    expect(snapshot.lastAvailableSlots?.[0]).toEqual(slots[0]);
    expect(snapshot.suggestedDoctor?.facilityId).toBe('branch-1');
  });
});
