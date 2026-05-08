import { SearchX } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

interface EmptyStateProps {
  title?: string;
  description?: string;
  actionLabel?: string;
  onAction?: () => void;
  className?: string;
}

export function EmptyState({
  title = 'Không tìm thấy dữ liệu',
  description = 'Hiện chưa có dữ liệu phù hợp với tiêu chí tìm kiếm.',
  actionLabel,
  onAction,
  className,
}: EmptyStateProps) {
  return (
    <div
      className={cn(
        'flex min-h-56 flex-col items-center justify-center rounded-lg border border-dashed border-border/70 bg-muted/10 px-ui-lg py-ui-xl text-center',
        className,
      )}
      role="status"
    >
      <div className="mb-ui-md rounded-full bg-primary/10 p-ui-md text-primary">
        <SearchX className="h-7 w-7" />
      </div>
      <h3 className="text-lg font-semibold leading-tight text-foreground">{title}</h3>
      <p className="mt-ui-xs max-w-sm text-sm leading-6 text-muted-foreground">{description}</p>
      {actionLabel && onAction && (
        <Button onClick={onAction} className="mt-ui-md">{actionLabel}</Button>
      )}
    </div>
  );
}
