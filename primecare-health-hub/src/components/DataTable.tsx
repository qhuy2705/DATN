import type { ReactNode } from 'react';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { ChevronLeft, ChevronRight, Search } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { EmptyState } from '@/components/EmptyState';
import { ErrorState } from '@/components/ErrorState';
import { LoadingSkeleton } from '@/components/LoadingSkeleton';
import { cn } from '@/lib/utils';

export interface Column<T> {
  key: string;
  header: string;
  cell?: (row: T) => ReactNode;
  className?: string;
}

interface DataTableProps<T> {
  columns: Column<T>[];
  data: T[];
  searchValue?: string;
  onSearchChange?: (v: string) => void;
  searchPlaceholder?: string;
  page?: number;
  totalPages?: number;
  onPageChange?: (p: number) => void;
  actions?: (row: T) => ReactNode;
  toolbar?: ReactNode;
  emptyMessage?: string;
  isLoading?: boolean;
  isError?: boolean;
  errorMessage?: string;
  onRetry?: () => void;
  loadingRows?: number;
  keyExtractor?: (row: T) => string;
  onRowClick?: (row: T) => void;
}

export function DataTable<T>({
  columns, data, searchValue, onSearchChange, searchPlaceholder,
  page = 1, totalPages = 1, onPageChange, actions, toolbar, emptyMessage,
  isLoading = false, isError = false, errorMessage, onRetry, loadingRows = 5, keyExtractor, onRowClick,
}: DataTableProps<T>) {
  const { t } = useTranslation();
  const previousPageLabel = t('common.previousPage', { defaultValue: 'Trang trước' });
  const nextPageLabel = t('common.nextPage', { defaultValue: 'Trang sau' });

  return (
    <div className="space-y-ui-md">
      {(onSearchChange || toolbar) && (
        <div className="flex flex-col items-start justify-between gap-ui-sm rounded-lg border border-border bg-card p-ui-sm shadow-sm sm:flex-row sm:items-center">
          {onSearchChange && (
            <div className="relative w-full sm:w-80">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                value={searchValue}
                onChange={(e) => onSearchChange(e.target.value)}
                placeholder={searchPlaceholder || t('common.search')}
                className="pl-9"
              />
            </div>
          )}
          {toolbar && <div className="flex flex-wrap gap-ui-xs">{toolbar}</div>}
        </div>
      )}
      {isError ? (
        <ErrorState
          description={errorMessage}
          onRetry={onRetry}
          className="min-h-48"
        />
      ) : isLoading ? (
        <LoadingSkeleton variant="table" count={loadingRows} />
      ) : (
      <div className="overflow-hidden rounded-lg border border-border bg-card shadow-sm">
        <div className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow className="bg-muted/45 hover:bg-muted/45">
                {columns.map((col) => (
                  <TableHead key={col.key} className={col.className}>
                    {col.header}
                  </TableHead>
                ))}
                {actions && <TableHead className="w-[100px]">{t('common.actions')}</TableHead>}
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={columns.length + (actions ? 1 : 0)} className="py-ui-lg">
                    <EmptyState
                      title={emptyMessage || t('common.noData')}
                      description={t('common.noMatchingData', {
                        defaultValue: 'Thử điều chỉnh bộ lọc hoặc từ khóa tìm kiếm.',
                      })}
                      className="min-h-48 border-0 bg-transparent py-ui-md"
                    />
                  </TableCell>
                </TableRow>
              ) : (
                data.map((row, i) => (
                  <TableRow
                    key={keyExtractor ? keyExtractor(row) : i}
                    className={cn(
                      'transition-colors hover:bg-primary/[0.035]',
                      onRowClick && 'cursor-pointer',
                    )}
                    tabIndex={onRowClick ? 0 : undefined}
                    onClick={() => onRowClick?.(row)}
                    onKeyDown={(event) => {
                      if (!onRowClick) return;
                      if (event.key === 'Enter' || event.key === ' ') {
                        event.preventDefault();
                        onRowClick(row);
                      }
                    }}
                  >
                    {columns.map((col) => (
                      <TableCell key={col.key} className={col.className}>
                        {col.cell ? col.cell(row) : String((row as Record<string, unknown>)[col.key] ?? '')}
                      </TableCell>
                    ))}
                    {actions && <TableCell onClick={(event) => event.stopPropagation()}>{actions(row)}</TableCell>}
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </div>
      </div>
      )}
      {totalPages > 1 && onPageChange && (
        <div className="flex items-center justify-between rounded-lg border border-border bg-card px-ui-md py-ui-sm shadow-sm">
          <p className="text-sm text-muted-foreground">{t('common.page')} {page} {t('common.of')} {totalPages}</p>
          <div className="flex gap-ui-xs">
            <Button
              variant="outline"
              size="icon"
              onClick={() => onPageChange(page - 1)}
              disabled={page <= 1}
              aria-label={previousPageLabel}
            >
              <ChevronLeft className="h-4 w-4" />
            </Button>
            <Button
              variant="outline"
              size="icon"
              onClick={() => onPageChange(page + 1)}
              disabled={page >= totalPages}
              aria-label={nextPageLabel}
            >
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
