interface JsonViewerProps {
  data: string | undefined | null;
  title?: string;
}

export function JsonViewer({ data, title }: JsonViewerProps) {
  if (!data) return <p className="text-sm text-muted-foreground italic">—</p>;

  let formatted: string;
  try {
    formatted = JSON.stringify(JSON.parse(data), null, 2);
  } catch {
    formatted = data;
  }

  return (
    <div>
      {title && <p className="text-sm font-medium text-foreground mb-2">{title}</p>}
      <pre className="text-xs bg-muted rounded-lg p-4 overflow-x-auto max-h-64 font-mono text-foreground/80 leading-relaxed">
        {formatted}
      </pre>
    </div>
  );
}
