import type { ReactNode } from 'react';
import type { LucideIcon } from 'lucide-react';
import { Loader2, SearchX } from 'lucide-react';

import { cn } from '@/lib/utils';

type PublicFlowStateTone = 'info' | 'empty' | 'error' | 'loading';

type PublicFlowStateProps = {
  title: string;
  description?: string;
  icon?: LucideIcon;
  tone?: PublicFlowStateTone;
  action?: ReactNode;
  compact?: boolean;
  className?: string;
};

const toneClassNames: Record<PublicFlowStateTone, string> = {
  info: 'border-primary/20 bg-primary/5 text-primary',
  empty: 'border-border bg-muted/20 text-muted-foreground',
  error: 'border-destructive/25 bg-destructive/5 text-destructive',
  loading: 'border-primary/20 bg-primary/5 text-primary',
};

export function PublicFlowState({
  title,
  description,
  icon: Icon = SearchX,
  tone = 'info',
  action,
  compact = false,
  className,
}: PublicFlowStateProps) {
  const isLoading = tone === 'loading';

  return (
    <div
      role={tone === 'error' ? 'alert' : 'status'}
      aria-live={tone === 'error' ? 'assertive' : 'polite'}
      className={cn(
        'flex flex-col items-center justify-center rounded-lg border border-dashed px-4 text-center',
        compact ? 'min-h-[132px] py-5' : 'min-h-[164px] py-6',
        toneClassNames[tone],
        className,
      )}
    >
      <div className="mb-3 flex h-9 w-9 items-center justify-center rounded-lg bg-background/80 text-current shadow-soft">
        {isLoading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Icon className="h-4 w-4" />}
      </div>
      <h3 className="max-w-xl break-words text-base font-semibold leading-6 text-foreground [overflow-wrap:anywhere]">
        {title}
      </h3>
      {description ? (
        <p className="mt-1 max-w-xl break-words text-sm leading-5 text-muted-foreground [overflow-wrap:anywhere]">
          {description}
        </p>
      ) : null}
      {action ? <div className="mt-4 flex flex-wrap justify-center gap-2">{action}</div> : null}
    </div>
  );
}
