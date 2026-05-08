import type { ReactNode } from 'react';

import { cn } from '@/lib/utils';

type LookupDetailItemProps = {
  label: ReactNode;
  value?: ReactNode;
  className?: string;
  valueClassName?: string;
};

export function LookupDetailItem({ label, value, className, valueClassName }: LookupDetailItemProps) {
  if (value === undefined || value === null || value === '') return null;

  return (
    <div className={cn('min-w-0 space-y-1', className)}>
      <span className="block text-xs font-medium leading-5 text-muted-foreground">{label}</span>
      <div
        className={cn(
          'min-w-0 break-words text-sm font-medium leading-5 text-foreground [overflow-wrap:anywhere]',
          valueClassName,
        )}
      >
        {value}
      </div>
    </div>
  );
}
