import { AlertTriangle } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

interface ErrorStateProps {
  title?: string;
  description?: string;
  onRetry?: () => void;
  className?: string;
}

export function ErrorState({
  title = 'Đã xảy ra lỗi',
  description = 'Chúng tôi gặp sự cố khi tải dữ liệu. Vui lòng thử lại.',
  onRetry,
  className,
}: ErrorStateProps) {
  return (
    <div
      className={cn(
        'flex min-h-56 flex-col items-center justify-center rounded-lg border border-dashed border-destructive/30 bg-destructive/5 px-ui-lg py-ui-xl text-center',
        className,
      )}
      role="alert"
    >
      <div className="mb-ui-md rounded-full bg-destructive/10 p-ui-md text-destructive">
        <AlertTriangle className="h-7 w-7" />
      </div>
      <h3 className="text-lg font-semibold leading-tight text-foreground">{title}</h3>
      <p className="mt-ui-xs max-w-sm text-sm leading-6 text-muted-foreground">{description}</p>
      {onRetry && (
        <Button variant="outline" onClick={onRetry} className="mt-ui-md">Thử lại</Button>
      )}
    </div>
  );
}
