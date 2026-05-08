import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Badge } from '@/components/ui/badge';
import { ScrollArea } from '@/components/ui/scroll-area';
import type { PatientStatusHistoryItem } from '@/types/api';

interface PatientStatusHistoryDialogProps {
  formatStatus?: (status?: string) => { description?: string; label: string };
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  items?: PatientStatusHistoryItem[];
  isLoading?: boolean;
}

export default function PatientStatusHistoryDialog({
  formatStatus,
  open,
  onOpenChange,
  title,
  items,
  isLoading,
}: PatientStatusHistoryDialogProps) {
  const getStatusDisplay = (status?: string, fallback = '-') =>
    status ? formatStatus?.(status) ?? { label: status } : { label: fallback };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
        </DialogHeader>
        {isLoading ? (
          <p className="text-sm text-muted-foreground">Đang tải lịch sử trạng thái...</p>
        ) : !items || items.length === 0 ? (
          <p className="text-sm text-muted-foreground">Chưa có lịch sử trạng thái để hiển thị.</p>
        ) : (
          <ScrollArea className="max-h-[60vh] pr-4">
            <div className="space-y-4">
              {items.map((item) => {
                const fromStatus = getStatusDisplay(item.fromStatus, 'Khởi tạo');
                const toStatus = getStatusDisplay(item.toStatus);

                return (
                  <div key={item.id} className="rounded-lg border bg-card p-4 shadow-sm">
                    <div className="flex flex-wrap items-center gap-2">
                      <Badge variant="outline">{fromStatus.label}</Badge>
                      <span className="text-muted-foreground">→</span>
                      <Badge>{toStatus.label}</Badge>
                    </div>
                    {toStatus.description ? (
                      <p className="mt-3 text-sm leading-6 text-muted-foreground">
                        {toStatus.description}
                      </p>
                    ) : null}
                    <div className="mt-3 space-y-1 text-sm text-muted-foreground">
                      <p>Thời điểm: {item.changedAt || '-'}</p>
                      <p>Người thay đổi: {item.changedBy || 'Hệ thống'}</p>
                      {item.note ? <p>Ghi chú: {item.note}</p> : null}
                    </div>
                  </div>
                );
              })}
            </div>
          </ScrollArea>
        )}
      </DialogContent>
    </Dialog>
  );
}
