import { afterEach, describe, expect, it, vi } from 'vitest';
import { AxiosHeaders, type AxiosResponse } from 'axios';
import apiClient from '@/lib/api-client';
import { buildApiUrl, isLikelyImageUrl, resolveImagePreviewUrl } from '@/lib/api-url';
import { fetchProtectedFile } from '@/lib/download-file';

const originalBaseUrl = apiClient.defaults.baseURL;

function blobResponse(blob: Blob): AxiosResponse<Blob> {
  return {
    data: blob,
    status: 200,
    statusText: 'OK',
    headers: {
      'content-type': blob.type || 'application/octet-stream',
    },
    config: {
      headers: new AxiosHeaders(),
    },
  };
}

describe('api url helpers', () => {
  afterEach(() => {
    apiClient.defaults.baseURL = originalBaseUrl;
    vi.restoreAllMocks();
  });

  it('builds a protected PDF URL under a relative /api base', () => {
    apiClient.defaults.baseURL = '/api';

    expect(buildApiUrl('/patient/results/1/pdf')).toBe('/api/patient/results/1/pdf');
  });

  it('preserves /api when building under an absolute API base', () => {
    apiClient.defaults.baseURL = 'http://localhost:8080/api';

    expect(buildApiUrl('/patient/results/1/pdf')).toBe('http://localhost:8080/api/patient/results/1/pdf');
  });

  it('does not duplicate /api when the path already includes it', () => {
    apiClient.defaults.baseURL = 'http://localhost:8080/api';

    expect(buildApiUrl('/api/files/content?path=x')).toBe('http://localhost:8080/api/files/content?path=x');

    apiClient.defaults.baseURL = '/api';

    expect(buildApiUrl('/api/files/content?path=x')).toBe('/api/files/content?path=x');
  });

  it('recognizes public file content URLs as image previews', () => {
    apiClient.defaults.baseURL = '/api';

    expect(isLikelyImageUrl('/api/files/public/content?path=avatars%2Fdoctor-1')).toBe(true);
    expect(resolveImagePreviewUrl('/api/files/public/content?path=avatars%2Fdoctor-1', 'image/*')).toBe(
      '/api/files/public/content?path=avatars%2Fdoctor-1',
    );
  });

  it('builds public file preview URLs without duplicating /api', () => {
    apiClient.defaults.baseURL = 'http://localhost:8080/api';

    expect(resolveImagePreviewUrl('/api/files/public/content?path=avatars%2Fdoctor-1', 'image/*')).toBe(
      'http://localhost:8080/api/files/public/content?path=avatars%2Fdoctor-1',
    );
  });

  it('does not treat protected file content URLs as direct image previews', () => {
    apiClient.defaults.baseURL = '/api';

    expect(isLikelyImageUrl('/api/files/content?path=avatars%2Fdoctor-1.jpg', 'image/*')).toBe(false);
    expect(isLikelyImageUrl('local://avatars/doctor-1.jpg', 'image/*')).toBe(false);
    expect(resolveImagePreviewUrl('/api/files/content?path=avatars%2Fdoctor-1.jpg', 'image/*')).toBeUndefined();
  });

  it('keeps direct browser image URL support', () => {
    expect(isLikelyImageUrl('data:image/png;base64,abc')).toBe(true);
    expect(isLikelyImageUrl('data:application/pdf;base64,abc', 'image/*')).toBe(false);
    expect(isLikelyImageUrl('blob:http://localhost/avatar-preview')).toBe(true);
    expect(isLikelyImageUrl('https://cdn.example.test/avatar.webp')).toBe(true);
    expect(isLikelyImageUrl('https://bucket.s3.amazonaws.com/avatar-preview', 'image/*')).toBe(true);
    expect(isLikelyImageUrl('/uploads/avatar.jpeg')).toBe(true);
  });

  it('uses apiClient for protected API URLs so auth interceptors still run', async () => {
    apiClient.defaults.baseURL = 'http://localhost:8080/api';
    const getSpy = vi
      .spyOn(apiClient, 'get')
      .mockResolvedValue(blobResponse(new Blob(['pdf'], { type: 'application/pdf' })));
    const fetchSpy = vi.spyOn(globalThis, 'fetch');

    await fetchProtectedFile('http://localhost:8080/api/patient/results/1/pdf', 'result.pdf');

    expect(getSpy).toHaveBeenCalledWith('/patient/results/1/pdf', { responseType: 'blob' });
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('uses apiClient without double-prefixing relative API URLs', async () => {
    apiClient.defaults.baseURL = '/api';
    const getSpy = vi
      .spyOn(apiClient, 'get')
      .mockResolvedValue(blobResponse(new Blob(['pdf'], { type: 'application/pdf' })));

    await fetchProtectedFile('/patient/results/1/pdf', 'result.pdf');

    expect(getSpy).toHaveBeenCalledWith('/patient/results/1/pdf', { responseType: 'blob' });
  });
});
