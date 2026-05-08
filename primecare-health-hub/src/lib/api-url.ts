import apiClient from '@/lib/api-client';

function isAbsoluteHttpUrl(value: string) {
  return /^https?:\/\//i.test(value);
}

function isBrowserHandledUrl(value: string) {
  return value.startsWith('data:') || value.startsWith('blob:');
}

function acceptsImages(accept?: string) {
  return accept
    ?.split(',')
    .map((item) => item.trim().toLowerCase())
    .some((item) => item === 'image/*' || item.startsWith('image/')) ?? false;
}

export function getApiBaseUrl() {
  const baseURL = apiClient.defaults.baseURL;
  return typeof baseURL === 'string' ? baseURL.trim() : '';
}

function ensureLeadingSlash(value: string) {
  return value.startsWith('/') ? value : `/${value}`;
}

function stripTrailingSlash(value: string) {
  return value.length > 1 ? value.replace(/\/+$/, '') : value;
}

function splitPathAndSuffix(value: string) {
  const match = value.match(/^([^?#]*)([?#].*)?$/);
  return {
    pathname: match?.[1] || '',
    suffix: match?.[2] || '',
  };
}

function getUrlPathname(value: string) {
  if (isAbsoluteHttpUrl(value)) {
    try {
      return new URL(value).pathname;
    } catch {
      return '';
    }
  }

  return splitPathAndSuffix(value).pathname;
}

function hasPathPrefix(pathname: string, prefix: string) {
  if (!prefix || prefix === '/') return true;
  return pathname === prefix || pathname.startsWith(`${prefix}/`);
}

function getApiBasePath(apiBaseUrl = getApiBaseUrl()) {
  if (!apiBaseUrl) return '';

  try {
    const pathname = isAbsoluteHttpUrl(apiBaseUrl)
      ? new URL(apiBaseUrl).pathname
      : splitPathAndSuffix(apiBaseUrl).pathname;
    return stripTrailingSlash(ensureLeadingSlash(pathname || '/'));
  } catch {
    return '';
  }
}

function getCurrentOrigin() {
  return typeof window !== 'undefined' && window.location?.origin
    ? window.location.origin
    : '';
}

function buildPathUnderApiBase(path: string, apiBasePath = getApiBasePath()) {
  const { pathname, suffix } = splitPathAndSuffix(path);
  const requestPath = ensureLeadingSlash(pathname);
  const normalizedBasePath = stripTrailingSlash(apiBasePath);

  if (!normalizedBasePath || normalizedBasePath === '/' || hasPathPrefix(requestPath, normalizedBasePath)) {
    return `${requestPath}${suffix}`;
  }

  return `${normalizedBasePath}${requestPath}${suffix}`;
}

function stripApiBasePath(path: string, apiBasePath = getApiBasePath()) {
  const { pathname, suffix } = splitPathAndSuffix(path);
  const requestPath = ensureLeadingSlash(pathname);
  const normalizedBasePath = stripTrailingSlash(apiBasePath);

  if (!normalizedBasePath || normalizedBasePath === '/' || !hasPathPrefix(requestPath, normalizedBasePath)) {
    return `${requestPath}${suffix}`;
  }

  const stripped = requestPath.slice(normalizedBasePath.length);
  return `${stripped || '/'}${suffix}`;
}

export function buildApiUrl(path: string) {
  const trimmed = path.trim();
  if (!trimmed) return trimmed;
  if (isAbsoluteHttpUrl(trimmed)) return trimmed;

  const apiBaseUrl = getApiBaseUrl();
  if (isAbsoluteHttpUrl(apiBaseUrl)) {
    const base = new URL(apiBaseUrl);
    return `${base.origin}${buildPathUnderApiBase(trimmed, getApiBasePath(apiBaseUrl))}`;
  }

  if (apiBaseUrl) {
    return buildPathUnderApiBase(trimmed, getApiBasePath(apiBaseUrl));
  }

  return trimmed.startsWith('/') ? trimmed : `/${trimmed}`;
}

export function normalizeFileUrl(url: string) {
  const trimmed = url.trim();
  if (!trimmed) return trimmed;

  if (trimmed.startsWith('local://')) {
    const storagePath = trimmed.slice('local://'.length);
    return `/api/files/content?path=${encodeURIComponent(storagePath)}`;
  }

  if (trimmed.startsWith('/api/files/content?') || trimmed.startsWith('/files/content?')) {
    return trimmed;
  }

  return trimmed;
}

function getFileContentPath(url: string) {
  const normalized = normalizeFileUrl(url);
  const pathname = getUrlPathname(normalized);
  if (!pathname) return '';

  const strippedPath = splitPathAndSuffix(stripApiBasePath(pathname)).pathname;
  return strippedPath || ensureLeadingSlash(pathname);
}

export function isPublicFileContentUrl(url: string) {
  const pathname = ensureLeadingSlash(getUrlPathname(normalizeFileUrl(url)));
  const fileContentPath = getFileContentPath(url);
  return fileContentPath === '/files/public/content' || pathname === '/api/files/public/content';
}

export function isProtectedFileContentUrl(url: string) {
  const pathname = ensureLeadingSlash(getUrlPathname(normalizeFileUrl(url)));
  const fileContentPath = getFileContentPath(url);
  return fileContentPath === '/files/content' || pathname === '/api/files/content';
}

export function isLikelyImageUrl(url?: string, accept?: string) {
  if (!url) return false;

  const normalized = normalizeFileUrl(url);
  if (!normalized) return false;
  if (/^data:image\//i.test(normalized)) return true;
  if (normalized.startsWith('data:')) return false;
  if (normalized.startsWith('blob:')) return true;
  if (isPublicFileContentUrl(normalized)) return true;
  if (isProtectedFileContentUrl(normalized)) return false;
  if (/\.(png|jpe?g|gif|webp)(?:[?#].*)?$/i.test(normalized)) return true;
  return isAbsoluteHttpUrl(normalized) && acceptsImages(accept);
}

export function resolveImagePreviewUrl(url?: string, accept?: string) {
  if (!url || !isLikelyImageUrl(url, accept)) return undefined;

  const normalized = normalizeFileUrl(url);
  if (isPublicFileContentUrl(normalized)) {
    return resolveApiRequestUrl(normalized);
  }

  return normalized;
}

export function resolveApiRequestUrl(url: string) {
  const trimmed = normalizeFileUrl(url);
  if (!trimmed) return trimmed;
  if (isAbsoluteHttpUrl(trimmed) || isBrowserHandledUrl(trimmed)) return trimmed;
  if (trimmed.startsWith('/')) {
    return buildApiUrl(trimmed);
  }
  return trimmed;
}

export function isApiRequestUrl(url: string) {
  const trimmed = url.trim();
  if (!trimmed || isBrowserHandledUrl(trimmed)) return false;

  const apiBaseUrl = getApiBaseUrl();
  const apiBasePath = getApiBasePath(apiBaseUrl);

  if (isAbsoluteHttpUrl(trimmed)) {
    try {
      const target = new URL(trimmed);

      if (isAbsoluteHttpUrl(apiBaseUrl)) {
        const base = new URL(apiBaseUrl);
        return target.origin === base.origin && hasPathPrefix(target.pathname, getApiBasePath(apiBaseUrl));
      }

      const currentOrigin = getCurrentOrigin();
      return Boolean(currentOrigin && target.origin === currentOrigin && hasPathPrefix(target.pathname, apiBasePath));
    } catch {
      return false;
    }
  }

  if (trimmed.startsWith('/')) {
    return hasPathPrefix(splitPathAndSuffix(trimmed).pathname, apiBasePath);
  }

  return true;
}

export function toApiClientRequestUrl(url: string) {
  const trimmed = url.trim();
  if (!trimmed || !isApiRequestUrl(trimmed)) return trimmed;

  const apiBasePath = getApiBasePath();

  if (isAbsoluteHttpUrl(trimmed)) {
    const target = new URL(trimmed);
    return stripApiBasePath(`${target.pathname}${target.search}${target.hash}`, apiBasePath);
  }

  if (trimmed.startsWith('/')) {
    return stripApiBasePath(trimmed, apiBasePath);
  }

  return trimmed;
}

export function isDirectBrowserUrl(url: string) {
  const trimmed = url.trim();
  if (!trimmed) return false;
  return isAbsoluteHttpUrl(trimmed) || trimmed.startsWith('data:') || trimmed.startsWith('blob:');
}
