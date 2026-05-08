import { ChevronsLeft, ChevronsRight, ChevronLeft, ChevronRight } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

type PageItem = number | 'ellipsis';

function buildPageItems(currentPage: number, totalPages: number): PageItem[] {
  if (totalPages <= 1) return [0];
  if (totalPages <= 7) {
    return Array.from({ length: totalPages }, (_, index) => index);
  }

  const pages: PageItem[] = [0];
  const start = Math.max(1, currentPage - 1);
  const end = Math.min(totalPages - 2, currentPage + 1);

  if (start > 1) {
    pages.push('ellipsis');
  }

  for (let page = start; page <= end; page += 1) {
    pages.push(page);
  }

  if (end < totalPages - 2) {
    pages.push('ellipsis');
  }

  pages.push(totalPages - 1);
  return pages;
}

interface AppPaginationProps {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  className?: string;
}

export function AppPagination({ page, totalPages, onPageChange, className }: AppPaginationProps) {
  const { i18n } = useTranslation();
  const isEn = i18n.language.startsWith('en');

  if (totalPages <= 1) return null;

  const items = buildPageItems(page, totalPages);
  const canGoPrev = page > 0;
  const canGoNext = page < totalPages - 1;

  return (
    <div className={cn('mt-10 flex flex-col items-center justify-center gap-3', className)}>
      <div className="text-sm text-muted-foreground">
        {isEn ? `Page ${page + 1} / ${totalPages}` : `Trang ${page + 1} / ${totalPages}`}
      </div>
      <div className="flex flex-wrap items-center justify-center gap-2">
        <Button
          variant="outline"
          size="sm"
          onClick={() => onPageChange(0)}
          disabled={!canGoPrev}
          aria-label={isEn ? 'First page' : 'Trang đầu'}
        >
          <ChevronsLeft className="h-4 w-4" />
        </Button>
        <Button
          variant="outline"
          size="sm"
          onClick={() => onPageChange(page - 1)}
          disabled={!canGoPrev}
          aria-label={isEn ? 'Previous page' : 'Trang trước'}
        >
          <ChevronLeft className="h-4 w-4" />
        </Button>

        {items.map((item, index) =>
          item === 'ellipsis' ? (
            <span key={`ellipsis-${index}`} className="px-2 text-sm text-muted-foreground">
              ...
            </span>
          ) : (
            <Button
              key={item}
              variant={item === page ? 'default' : 'outline'}
              size="sm"
              onClick={() => onPageChange(item)}
              className={cn('min-w-10 rounded-xl', item === page && 'shadow-soft')}
              aria-current={item === page ? 'page' : undefined}
            >
              {item + 1}
            </Button>
          ),
        )}

        <Button
          variant="outline"
          size="sm"
          onClick={() => onPageChange(page + 1)}
          disabled={!canGoNext}
          aria-label={isEn ? 'Next page' : 'Trang sau'}
        >
          <ChevronRight className="h-4 w-4" />
        </Button>
        <Button
          variant="outline"
          size="sm"
          onClick={() => onPageChange(totalPages - 1)}
          disabled={!canGoNext}
          aria-label={isEn ? 'Last page' : 'Trang cuối'}
        >
          <ChevronsRight className="h-4 w-4" />
        </Button>
      </div>
    </div>
  );
}
