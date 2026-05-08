import apiClient from '@/lib/api-client';
import { isApiRequestUrl, resolveApiRequestUrl, toApiClientRequestUrl } from '@/lib/api-url';

function extractFilenameFromDisposition(contentDisposition?: string) {
  if (!contentDisposition) return null;

  const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i);
  if (utf8Match?.[1]) {
    try {
      return decodeURIComponent(utf8Match[1].replace(/['"]/g, ''));
    } catch {
      return utf8Match[1].replace(/['"]/g, '');
    }
  }

  const filenameMatch = contentDisposition.match(/filename=([^;]+)/i);
  if (filenameMatch?.[1]) {
    return filenameMatch[1].trim().replace(/^"|"$/g, '');
  }

  return null;
}

function shouldUseApiClient(url: string) {
  return isApiRequestUrl(url);
}

async function fetchExternalFile(url: string, fallbackFilename: string) {
  const response = await fetch(url, { credentials: 'include' });
  if (!response.ok) {
    throw new Error(`Unable to fetch file (${response.status})`);
  }

  const contentType = response.headers.get('content-type') || 'application/octet-stream';
  const disposition = response.headers.get('content-disposition') || undefined;
  const fileName = extractFilenameFromDisposition(disposition) || fallbackFilename;
  const blob = await response.blob();
  return { blob: blob.type ? blob : new Blob([blob], { type: contentType }), fileName };
}

export async function fetchProtectedFile(url: string, fallbackFilename: string) {
  const requestUrl = resolveApiRequestUrl(url);

  if (!requestUrl) {
    throw new Error('Invalid file URL');
  }

  if (!shouldUseApiClient(requestUrl)) {
    return fetchExternalFile(requestUrl, fallbackFilename);
  }

  const response = await apiClient.get(toApiClientRequestUrl(requestUrl), {
    responseType: 'blob',
  });

  const contentType = response.headers['content-type'] || 'application/octet-stream';
  const disposition = response.headers['content-disposition'];
  const fileName = extractFilenameFromDisposition(disposition) || fallbackFilename;
  const blob = response.data instanceof Blob
    ? response.data
    : new Blob([response.data], { type: contentType });

  return { blob, fileName };
}

export async function downloadProtectedFile(url: string, fallbackFilename = 'download.pdf') {
  const { blob, fileName } = await fetchProtectedFile(url, fallbackFilename);

  const objectUrl = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = objectUrl;
  link.download = fileName;
  link.rel = 'noopener';
  document.body.appendChild(link);
  link.click();
  link.remove();

  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
}

export async function openProtectedFile(url: string, fallbackFilename = 'document.pdf') {
  const previewWindow = typeof window !== 'undefined' ? window.open('', '_blank') : null;

  try {
    const { blob } = await fetchProtectedFile(url, fallbackFilename);
    const objectUrl = URL.createObjectURL(blob);

    if (previewWindow) {
      previewWindow.location.href = objectUrl;
    } else {
      const link = document.createElement('a');
      link.href = objectUrl;
      link.target = '_blank';
      link.rel = 'noopener';
      document.body.appendChild(link);
      link.click();
      link.remove();
    }

    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
  } catch (error) {
    previewWindow?.close();
    throw error;
  }
}
