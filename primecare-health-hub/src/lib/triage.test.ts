import { describe, expect, it } from 'vitest';
import {
  getConfidenceDisplay,
  formatTriagePriority,
  getPreTriageLevelDisplay,
  getPriorityDisplay,
  getSourceDisplay,
  normalizePriority,
} from '@/lib/triage';

describe('triage helpers', () => {
  it('maps backend priority values to patient-facing P labels', () => {
    expect(getPriorityDisplay('URGENT')).toEqual({ label: 'P1', description: 'Ưu tiên cao' });
    expect(getPriorityDisplay('PRIORITY')).toEqual({ label: 'P2', description: 'Cần ưu tiên' });
    expect(getPriorityDisplay('ROUTINE')).toEqual({ label: 'P3', description: 'Thông thường' });
  });

  it('accepts legacy display aliases without changing backend enum names', () => {
    expect(normalizePriority('P1')).toBe('URGENT');
    expect(normalizePriority('HIGH')).toBe('PRIORITY');
    expect(normalizePriority('NORMAL')).toBe('ROUTINE');
    expect(formatTriagePriority('P2')).toBe('P2 - Cần ưu tiên');
  });

  it('maps pre-triage levels to safe Vietnamese labels', () => {
    expect(getPreTriageLevelDisplay('RED_FLAG')).toBe('Cảnh báo cần xác minh');
    expect(getPreTriageLevelDisplay('WATCH')).toBe('Cần chú ý');
    expect(getPreTriageLevelDisplay('NONE')).toBe('Thông thường');
  });

  it('maps source and confidence labels for staff verification', () => {
    expect(getSourceDisplay('RULE').label).toBe('Rule-based');
    expect(getSourceDisplay('AI').label).toBe('AI đọc mô tả tự do');
    expect(getSourceDisplay('HYBRID').label).toBe('Card + Knowledge base + AI');
    expect(getConfidenceDisplay('HIGH')).toBe('Cao');
    expect(getConfidenceDisplay('MEDIUM')).toBe('Trung bình');
    expect(getConfidenceDisplay('LOW')).toBe('Thấp');
  });
});
