interface JsonViewerProps {
  data: unknown;
  title?: string;
}

export function JsonViewer({ data, title }: JsonViewerProps) {
  if (data === null || typeof data === 'undefined' || data === '') {
    return <p className="text-sm italic text-muted-foreground">-</p>;
  }

  let formatted: string;
  try {
    const value = typeof data === 'string' ? JSON.parse(data) : data;
    formatted = JSON.stringify(value, null, 2) ?? String(value);
  } catch {
    formatted = String(data);
  }

  return (
    <div>
      {title && <p className="mb-2 text-sm font-medium text-foreground">{title}</p>}
      <pre className="max-h-72 overflow-auto rounded-lg bg-muted p-4 font-mono text-xs leading-relaxed text-foreground/80">
        {formatted}
      </pre>
    </div>
  );
}
