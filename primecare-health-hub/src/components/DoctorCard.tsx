import { Link } from 'react-router-dom';
import { Award, BadgeCheck, CalendarDays, Clock3, Languages, Stethoscope } from 'lucide-react';
import { UserAvatar } from './UserAvatar';
import { Button } from '@/components/ui/button';
import type { Doctor } from '@/types/api';
import { useTranslation } from 'react-i18next';

interface DoctorCardProps {
  doctor: Doctor;
}

function formatNextAvailableDate(value: string | undefined, locale: string) {
  if (!value) return null;
  const date = new Date(/^\d{4}-\d{2}-\d{2}$/.test(value) ? `${value}T00:00:00` : value);
  if (Number.isNaN(date.getTime())) return value;

  return new Intl.DateTimeFormat(locale, {
    weekday: 'short',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(date);
}

export function DoctorCard({ doctor }: DoctorCardProps) {
  const { i18n } = useTranslation();
  const isEn = i18n.language.startsWith('en');
  const locale = isEn ? 'en-US' : 'vi-VN';
  const nextAvailableLabel = formatNextAvailableDate(doctor.nextAvailableDate, locale);
  const languages = doctor.supportedLanguages ?? [];
  const featuredServices = doctor.featuredServices ?? [];
  const yearsOfExperience =
    typeof doctor.yearsOfExperience === 'number' && doctor.yearsOfExperience > 0
      ? `${doctor.yearsOfExperience} ${isEn ? 'years' : 'năm KN'}`
      : isEn
        ? 'Updating exp.'
        : 'Đang cập nhật KN';

  const bookingParams = new URLSearchParams({ doctorId: doctor.id });
  if (doctor.branchId) bookingParams.set('branchId', doctor.branchId);
  const primarySpecialtyId = doctor.specialtyId || doctor.specialtyIds?.[0];
  if (primarySpecialtyId) bookingParams.set('specialtyId', primarySpecialtyId);

  return (
    <div className="group flex h-full flex-col overflow-hidden rounded-xl border border-border bg-card shadow-card transition-all hover:shadow-elevated">
      <div className="flex flex-1 gap-4 p-5">
        <div className="flex h-20 w-20 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-primary-soft to-accent-soft">
          <UserAvatar name={doctor.fullName} avatarUrl={doctor.avatarUrl} size="lg" className="h-16 w-16 ring-0" />
        </div>

        <div className="min-w-0 flex-1">
          <h3 className="truncate font-semibold text-foreground">{doctor.fullName}</h3>
          <p className="mt-1 truncate text-sm text-primary">
            {doctor.specialtyName || doctor.title || (isEn ? 'Specialty updating' : 'Đang cập nhật chuyên khoa')}
          </p>
          <p className="mt-1 truncate text-xs text-muted-foreground">
            {doctor.branchName || (isEn ? 'Branch updating' : 'Đang cập nhật cơ sở')}
          </p>

          <div className="mt-2 flex flex-wrap gap-x-3 gap-y-1 text-xs text-muted-foreground">
            <span className="inline-flex items-center gap-1">
              <Award className="h-3.5 w-3.5 text-champagne" />
              {yearsOfExperience}
            </span>
            {nextAvailableLabel ? (
              <span className="inline-flex items-center gap-1">
                <Clock3 className="h-3.5 w-3.5 text-primary" />
                {nextAvailableLabel}
              </span>
            ) : null}
          </div>

          {(languages.length || featuredServices.length) ? (
            <div className="mt-3 flex flex-wrap gap-1.5">
              {languages.length ? (
                <span className="inline-flex items-center gap-1 rounded-full bg-muted/60 px-2 py-1 text-[11px] font-medium text-muted-foreground">
                  <Languages className="h-3 w-3 text-primary" />
                  {languages.slice(0, 2).join(' • ')}
                </span>
              ) : null}
              {featuredServices.slice(0, 1).map((item) => (
                <span key={item} className="inline-flex items-center gap-1 rounded-full bg-primary-soft px-2 py-1 text-[11px] font-medium text-primary">
                  <BadgeCheck className="h-3 w-3" />
                  {item}
                </span>
              ))}
            </div>
          ) : null}
        </div>
      </div>

      {doctor.bio ? (
        <p className="px-5 pb-4 text-sm leading-6 text-muted-foreground line-clamp-2">{doctor.bio}</p>
      ) : null}

      <div className="flex items-center justify-between gap-3 border-t border-border bg-muted/20 px-5 py-3">
        <div className="min-w-0 text-xs text-muted-foreground">
          <span className="inline-flex items-center gap-1">
            <Stethoscope className="h-3.5 w-3.5 text-primary" />
            {doctor.bookable === false ? (isEn ? 'Booking unavailable' : 'Chưa đặt trực tuyến') : (isEn ? 'Profile available' : 'Có hồ sơ chi tiết')}
          </span>
        </div>
        <div className="flex shrink-0 gap-2">
          <Button asChild variant="ghost" size="sm" className="rounded-lg px-3">
            <Link to={`/doctors/${doctor.id}`}>{isEn ? 'Details' : 'Xem chi tiết'}</Link>
          </Button>
          <Button asChild size="sm" className="rounded-lg px-3">
            <Link to={`/booking?${bookingParams.toString()}`}>
              <CalendarDays className="mr-1.5 h-4 w-4" />
              {isEn ? 'Book' : 'Đặt lịch'}
            </Link>
          </Button>
        </div>
      </div>
    </div>
  );
}
