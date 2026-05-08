import { Skeleton } from '@/components/ui/skeleton';

interface LoadingSkeletonProps {
  variant?: 'card' | 'table' | 'detail' | 'list';
  count?: number;
}

export function LoadingSkeleton({ variant = 'card', count = 3 }: LoadingSkeletonProps) {
  if (variant === 'table') {
    return (
      <div className="space-y-ui-sm" aria-hidden>
        <Skeleton className="h-10 w-full rounded-lg" />
        {Array.from({ length: count }).map((_, i) => (
          <Skeleton key={i} className="h-14 w-full rounded-lg" />
        ))}
      </div>
    );
  }

  if (variant === 'detail') {
    return (
      <div className="space-y-ui-lg" aria-hidden>
        <Skeleton className="h-48 w-full rounded-lg" />
        <div className="space-y-ui-sm">
          <Skeleton className="h-8 w-1/3" />
          <Skeleton className="h-4 w-2/3" />
          <Skeleton className="h-4 w-1/2" />
        </div>
      </div>
    );
  }

  if (variant === 'list') {
    return (
      <div className="space-y-ui-md" aria-hidden>
        {Array.from({ length: count }).map((_, i) => (
          <div key={i} className="flex items-center gap-ui-md rounded-lg border border-border bg-card p-ui-md">
            <Skeleton className="h-12 w-12 rounded-full" />
            <div className="flex-1 space-y-ui-xs">
              <Skeleton className="h-4 w-1/3" />
              <Skeleton className="h-3 w-1/2" />
            </div>
          </div>
        ))}
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 gap-ui-lg md:grid-cols-2 lg:grid-cols-3" aria-hidden>
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className="space-y-ui-md rounded-lg border border-border bg-card p-ui-lg">
          <Skeleton className="h-40 w-full rounded-lg" />
          <Skeleton className="h-5 w-2/3" />
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-1/2" />
        </div>
      ))}
    </div>
  );
}
