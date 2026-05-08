import { CalendarClock, ClipboardCheck, CreditCard, FileCheck2, FilePlus2, HeartPulse, Pill, PlayCircle, Stethoscope } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { cn } from '@/lib/utils';
import type { EncounterTimelineItem } from '@/types/api';

const stageMeta: Record<string, { label: string; icon: LucideIcon; tone: string }> = {
  BOOKING: { label: 'Đặt lịch', icon: CalendarClock, tone: 'bg-primary/10 text-primary border-primary/20' },
  RECEPTION: { label: 'Tiếp đón', icon: HeartPulse, tone: 'bg-accent/10 text-accent border-accent/20' },
  CLINICAL: { label: 'Khám bệnh', icon: Stethoscope, tone: 'bg-success/10 text-success border-success/20' },
  BILLING: { label: 'Thanh toán', icon: CreditCard, tone: 'bg-warning/10 text-warning border-warning/20' },
  DIAGNOSTICS: { label: 'Cận lâm sàng', icon: FilePlus2, tone: 'bg-accent/10 text-accent border-accent/20' },
  PRESCRIPTION: { label: 'Đơn thuốc', icon: Pill, tone: 'bg-destructive/10 text-destructive border-destructive/20' },
  COMPLETION: { label: 'Hoàn tất', icon: FileCheck2, tone: 'bg-muted text-muted-foreground border-border' },
};

function formatDateTime(value?: string) {
  if (!value) return 'Đang chờ cập nhật';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('vi-VN');
}

export function PatientJourneyTimeline({
  items,
  compact = false,
  emptyText = 'Chưa có timeline cho hồ sơ này.',
}: {
  items: EncounterTimelineItem[];
  compact?: boolean;
  emptyText?: string;
}) {
  if (!items.length) {
    return <p className="text-sm text-muted-foreground">{emptyText}</p>;
  }

  return (
    <div className={cn('space-y-ui-sm', compact && 'space-y-ui-xs')}>
      {items.map((item, index) => {
        const meta = stageMeta[item.stage || 'CLINICAL'] || {
          label: item.stage || 'Cập nhật',
          icon: PlayCircle,
          tone: 'bg-muted text-muted-foreground border-border',
        };
        const Icon = meta.icon;

        return (
          <div key={item.id || `${item.eventType}-${item.occurredAt || index}`} className="flex gap-3">
            <div className="flex flex-col items-center">
              <div className={cn('flex h-9 w-9 items-center justify-center rounded-full border', meta.tone)}>
                <Icon className="h-4 w-4" />
              </div>
              {index < items.length - 1 ? <div className="mt-2 h-full min-h-5 w-px bg-border" /> : null}
            </div>
            <div className={cn('min-w-0 flex-1 rounded-lg border border-border bg-muted/15 p-ui-sm', compact && 'p-2.5')}>
              <div className="flex flex-wrap items-center gap-ui-xs">
                <p className="text-sm font-semibold text-foreground">{item.title}</p>
                <span className={cn('rounded-full border px-2 py-1 text-[11px] font-medium leading-none', meta.tone)}>
                  {meta.label}
                </span>
                {item.status ? (
                  <span className="rounded-full border border-border bg-background px-2 py-1 text-[11px] leading-none text-muted-foreground">
                    {item.status}
                  </span>
                ) : null}
              </div>
              {item.description ? (
                <p className="mt-1 whitespace-pre-wrap text-sm text-muted-foreground">{item.description}</p>
              ) : null}
              <div className="mt-2 flex flex-wrap items-center gap-3 text-xs text-muted-foreground">
                <span>{formatDateTime(item.occurredAt)}</span>
                {item.actorName ? <span>{item.actorName}</span> : null}
                {item.referenceCode ? <span>{item.referenceCode}</span> : null}
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}
