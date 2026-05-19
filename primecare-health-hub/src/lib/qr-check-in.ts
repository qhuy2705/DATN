export function extractQrToken(rawValue: string): string {
  const value = rawValue.trim();

  try {
    const url = new URL(value);
    return (
      url.searchParams.get('qrToken') ||
      url.searchParams.get('token') ||
      value
    );
  } catch {
    return value;
  }
}
