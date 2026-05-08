import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Building2, CalendarDays, Mail, MapPin, Phone } from 'lucide-react';
import { Button } from '@/components/ui/button';
import type { Branch } from '@/types/api';
import { useTranslation } from 'react-i18next';

interface BranchCardProps {
  branch: Branch;
}

export function BranchCard({ branch }: BranchCardProps) {
  const { i18n } = useTranslation();
  const isEn = i18n.language.startsWith('en');
  const [imageFailed, setImageFailed] = useState(false);

  return (
    <div className="group flex h-full flex-col rounded-xl border border-border bg-card p-6 shadow-card transition-all hover:shadow-elevated">
      <div className="flex items-start gap-4">
        {branch.imageUrl && !imageFailed ? (
          <img
            src={branch.imageUrl}
            alt={branch.name}
            className="h-16 w-16 shrink-0 rounded-xl object-cover"
            loading="lazy"
            decoding="async"
            onError={() => setImageFailed(true)}
          />
        ) : (
          <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-primary-soft text-primary">
            <Building2 className="h-6 w-6" />
          </div>
        )}

        <div className="min-w-0 flex-1">
          <h3 className="font-semibold text-foreground">{branch.name}</h3>
          {branch.code ? <p className="mt-1 text-sm text-primary">{isEn ? 'Code' : 'Mã'} {branch.code}</p> : null}
        </div>
      </div>

      <div className="mt-4 space-y-2 text-sm text-muted-foreground">
        <div className="flex items-start gap-2">
          <MapPin className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
          <span className="line-clamp-2">{branch.address || (isEn ? 'Address updating' : 'Đang cập nhật địa chỉ')}</span>
        </div>
        <div className="flex items-center gap-2">
          <Phone className="h-4 w-4 shrink-0 text-primary" />
          <span className="truncate">{branch.phone || (isEn ? 'Phone updating' : 'Đang cập nhật số điện thoại')}</span>
        </div>
        <div className="flex items-center gap-2">
          <Mail className="h-4 w-4 shrink-0 text-primary" />
          <span className="truncate">{branch.email || (isEn ? 'Email updating' : 'Đang cập nhật email')}</span>
        </div>
      </div>

      {branch.description ? (
        <p className="mt-4 flex-1 text-sm leading-6 text-muted-foreground line-clamp-2">{branch.description}</p>
      ) : (
        <div className="flex-1" />
      )}

      <div className="mt-5 flex items-center justify-between gap-3 text-sm">
        <Button asChild variant="ghost" size="sm" className="rounded-lg px-3 text-primary hover:bg-primary-soft hover:text-primary">
          <Link to={`/availability?branchId=${branch.id}`}>
            <CalendarDays className="mr-1.5 h-4 w-4" />
            {isEn ? 'Availability' : 'Lịch trống'}
          </Link>
        </Button>
        <Button asChild size="sm" className="rounded-lg px-3">
          <Link to={`/booking?branchId=${branch.id}`}>{isEn ? 'Book' : 'Đặt lịch'}</Link>
        </Button>
      </div>
    </div>
  );
}
