import { Link } from 'react-router-dom';
import { CalendarDays, Clock3, Stethoscope, Users } from 'lucide-react';
import { Button } from '@/components/ui/button';
import type { Specialty } from '@/types/api';
import { useTranslation } from 'react-i18next';

interface SpecialtyCardProps {
  specialty: Specialty;
}

export function SpecialtyCard({ specialty }: SpecialtyCardProps) {
  const { i18n } = useTranslation();
  const isEn = i18n.language.startsWith('en');

  const slotGuidance =
    typeof specialty.defaultSlotMinutes === 'number' && specialty.defaultSlotMinutes > 0
      ? isEn
        ? `${specialty.defaultSlotMinutes} min slots`
        : `${specialty.defaultSlotMinutes} phút/lượt`
      : null;

  const sessionCapacity =
    typeof specialty.maxPerSession === 'number' && specialty.maxPerSession > 0
      ? isEn
        ? `Up to ${specialty.maxPerSession}/session`
        : `Tối đa ${specialty.maxPerSession}/buổi`
      : null;

  return (
    <div className="group flex h-full flex-col rounded-xl border border-border bg-card p-6 shadow-card transition-all hover:shadow-elevated">
      <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-xl bg-primary-soft text-primary">
        <Stethoscope className="h-6 w-6" />
      </div>

      <div className="min-w-0 flex-1">
        <h3 className="font-semibold text-foreground">{specialty.name}</h3>
        <p className="mt-1 text-sm leading-6 text-muted-foreground line-clamp-2">
          {specialty.description || (isEn ? 'Choose this specialty to narrow doctors and available times.' : 'Chọn chuyên khoa này để thu hẹp bác sĩ và lịch trống.')}
        </p>
      </div>

      <div className="mt-4 flex flex-wrap gap-2 text-xs text-muted-foreground">
        {specialty.code ? <span className="rounded-full bg-muted/60 px-2.5 py-1">{isEn ? 'Code' : 'Mã'} {specialty.code}</span> : null}
        {slotGuidance ? (
          <span className="inline-flex items-center gap-1 rounded-full bg-muted/60 px-2.5 py-1">
            <Clock3 className="h-3.5 w-3.5 text-primary" />
            {slotGuidance}
          </span>
        ) : null}
        {sessionCapacity ? (
          <span className="inline-flex items-center gap-1 rounded-full bg-muted/60 px-2.5 py-1">
            <Users className="h-3.5 w-3.5 text-primary" />
            {sessionCapacity}
          </span>
        ) : null}
      </div>

      <div className="mt-5 flex items-center justify-between gap-3 text-sm">
        <Button asChild variant="ghost" size="sm" className="rounded-lg px-3 text-primary hover:bg-primary-soft hover:text-primary">
          <Link to={`/availability?specialtyId=${specialty.id}`}>
            <CalendarDays className="mr-1.5 h-4 w-4" />
            {isEn ? 'Availability' : 'Lịch trống'}
          </Link>
        </Button>
        <Button asChild size="sm" className="rounded-lg px-3">
          <Link to={`/booking?specialtyId=${specialty.id}`}>{isEn ? 'Book' : 'Đặt lịch'}</Link>
        </Button>
      </div>
    </div>
  );
}
