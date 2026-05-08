import { cn } from '@/lib/utils';
import type { AppointmentSummary } from '@/types/api';
import { getAppointmentStatusLabel } from '@/lib/filter-options';

type StatusKey =
  | '__all__'
  | 'REQUESTED'
  | 'CONFIRMED'
  | 'CHECKED_IN'
  | 'COMPLETED'
  | 'NO_SHOW'
  | 'CANCELLED';

interface AppointmentSummaryCardsProps {
  summary?: AppointmentSummary;
  activeStatus: StatusKey;
  onSelect: (status: StatusKey) => void;
}

const CARD_CONFIG: Array<{
  key: StatusKey;
  label: string;
  getValue: (summary?: AppointmentSummary) => number;
}> = [
  { key: '__all__', label: 'Tất cả', getValue: (s) => s?.all ?? 0 },
  { key: 'REQUESTED', label: getAppointmentStatusLabel('REQUESTED'), getValue: (s) => s?.pending ?? 0 },
  { key: 'CONFIRMED', label: getAppointmentStatusLabel('CONFIRMED'), getValue: (s) => s?.confirmed ?? 0 },
  { key: 'CHECKED_IN', label: getAppointmentStatusLabel('CHECKED_IN'), getValue: (s) => s?.checkedIn ?? 0 },
  { key: 'COMPLETED', label: getAppointmentStatusLabel('COMPLETED'), getValue: (s) => s?.completed ?? 0 },
  { key: 'NO_SHOW', label: getAppointmentStatusLabel('NO_SHOW'), getValue: (s) => s?.noShow ?? 0 },
  { key: 'CANCELLED', label: getAppointmentStatusLabel('CANCELLED'), getValue: (s) => s?.cancelled ?? 0 },
];

export function AppointmentSummaryCards({
  summary,
  activeStatus,
  onSelect,
}: AppointmentSummaryCardsProps) {
  return (
    <div className="mb-ui-md grid grid-cols-2 gap-ui-sm md:grid-cols-4 xl:grid-cols-7">
      {CARD_CONFIG.map((card) => {
        const active = activeStatus === card.key;

        return (
          <button
            key={card.key}
            type="button"
            onClick={() => onSelect(card.key)}
            className={cn(
              'rounded-lg border border-border bg-card p-ui-md text-left shadow-sm transition-[border-color,box-shadow,background-color] duration-200 hover:border-primary/40 hover:shadow-md focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25 focus-visible:ring-offset-0',
              active && 'border-primary bg-primary/5 shadow-md'
            )}
          >
            <p className="text-xs font-medium text-muted-foreground">{card.label}</p>
            <p className="mt-2 text-2xl font-semibold text-foreground">
              {card.getValue(summary)}
            </p>
          </button>
        );
      })}
    </div>
  );
}
