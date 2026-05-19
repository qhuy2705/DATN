import { describe, expect, it } from 'vitest';
import { extractQrToken } from './qr-check-in';

describe('extractQrToken', () => {
  it('extracts qrToken from an absolute URL', () => {
    expect(extractQrToken('https://primecare.test/check-in?qrToken=abc-123')).toBe('abc-123');
  });

  it('falls back to token query parameter', () => {
    expect(extractQrToken('https://primecare.test/check-in?token=token-456')).toBe('token-456');
  });

  it('returns trimmed raw text when the value is not a URL', () => {
    expect(extractQrToken('  raw-token-789  ')).toBe('raw-token-789');
  });
});
