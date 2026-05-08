import { cn } from '@/lib/utils';

export function LivePulseBadge({ label = 'Realtime', connected = true }: { label?: string; connected?: boolean }) {
  return (
    <span className={cn(
      'inline-flex items-center gap-ui-xs rounded-full border px-3 py-1 text-xs font-medium leading-none',
      connected ? 'border-success/20 bg-success/10 text-success' : 'border-warning/20 bg-warning/10 text-warning',
    )}>
      <span className={cn('h-2 w-2 rounded-full', connected ? 'animate-pulse bg-success' : 'bg-warning')} />
      {label}
    </span>
  );
}
