export function getApiErrorMessage(error: unknown, fallback = 'Thao tác thất bại') {
  const maybeAxios = error as {
    response?: {
      data?: {
        message?: string;
        error?: string;
        detail?: string;
        title?: string;
        details?: {
          fields?: Record<string, string | string[]>;
        };
      };
    };
  };

  const fieldErrors = maybeAxios.response?.data?.details?.fields;
  if (fieldErrors && typeof fieldErrors === 'object') {
    for (const value of Object.values(fieldErrors)) {
      if (typeof value === 'string' && value.trim()) {
        return value;
      }
      if (Array.isArray(value)) {
        const first = value.find((item) => typeof item === 'string' && item.trim());
        if (first) return first;
      }
    }
  }

  const message = maybeAxios.response?.data?.message;
  if (typeof message === 'string' && message.trim()) {
    return message;
  }

  for (const key of ['error', 'detail', 'title'] as const) {
    const value = maybeAxios.response?.data?.[key];
    if (typeof value === 'string' && value.trim()) {
      return value;
    }
  }

  return fallback;
}
