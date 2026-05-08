import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { cn } from '@/lib/utils';
import type { ReactNode } from 'react';

export type QueueBoardColumn<T> = {
  id: string;
  title: string;
  description?: string;
  accentClassName?: string;
  items: T[];
  emptyText?: string;
};

export function QueueBoard<T>({
  columns,
  renderCard,
  className,
}: {
  columns: QueueBoardColumn<T>[];
  renderCard: (item: T, columnId: string) => ReactNode;
  className?: string;
}) {
  return (
    <div className={cn('grid gap-4 xl:grid-cols-4', className)}>
      {columns.map((column) => (
        <Card key={column.id} className="border-border/70 shadow-sm">
          <CardHeader className="pb-3">
            <div className="flex items-start justify-between gap-3">
              <div>
                <CardTitle className="text-base">{column.title}</CardTitle>
                {column.description ? (
                  <p className="mt-1 text-sm text-muted-foreground">{column.description}</p>
                ) : null}
              </div>
              <span className={cn('rounded-full px-2.5 py-1 text-xs font-semibold', column.accentClassName || 'bg-muted text-muted-foreground')}>
                {column.items.length}
              </span>
            </div>
          </CardHeader>
          <CardContent className="space-y-3">
            {column.items.length > 0 ? (
              column.items.map((item) => renderCard(item, column.id))
            ) : (
              <div className="rounded-xl border border-dashed px-3 py-6 text-center text-sm text-muted-foreground">
                {column.emptyText || 'Chưa có dữ liệu'}
              </div>
            )}
          </CardContent>
        </Card>
      ))}
    </div>
  );
}
