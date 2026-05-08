import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { CalendarDays, Search, Stethoscope } from 'lucide-react';
import { ScrollReveal } from '@/components/ScrollReveal';
import { SpecialtyCard } from '@/components/SpecialtyCard';
import { useSpecialties } from '@/hooks/use-public-data';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { useTranslation } from 'react-i18next';

export default function SpecialtiesPage() {
  const { i18n } = useTranslation();
  const isEn = i18n.language.startsWith('en');
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const { data: specialties = [], isLoading } = useSpecialties();

  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedSearch(search.trim()), 300);
    return () => window.clearTimeout(timer);
  }, [search]);

  const filteredSpecialties = useMemo(() => {
    if (!debouncedSearch) return specialties;

    const normalizedQuery = debouncedSearch.toLowerCase();
    return specialties.filter((specialty) =>
      [specialty.name, specialty.description, specialty.code]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(normalizedQuery)),
    );
  }, [debouncedSearch, specialties]);

  return (
    <div className="public-page">
      <div className="container-page py-12">
        <div className="mb-8 flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
          <div>
            <h1 className="public-page-title">{isEn ? 'Specialties' : 'Chuyên khoa'}</h1>
            <p className="public-page-subtitle">
              {isEn ? 'Explore care areas and continue to availability or booking.' : 'Khám phá toàn bộ chuyên khoa tại PrimeCare.'}
            </p>
          </div>
          <Button asChild className="w-fit rounded-lg">
            <Link to="/booking">
              {isEn ? 'Book appointment' : 'Đặt lịch khám'}
              <CalendarDays className="ml-2 h-4 w-4" />
            </Link>
          </Button>
        </div>

        <ScrollReveal className="mb-6">
          <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
            <div className="relative w-full md:max-w-md">
              <Search className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                id="specialty-search"
                placeholder={isEn ? 'Search specialties...' : 'Tìm chuyên khoa...'}
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                className="h-11 rounded-lg bg-card pl-11"
              />
            </div>
            <div className="inline-flex w-fit items-center gap-2 rounded-full bg-primary-soft px-3 py-1.5 text-sm font-medium text-primary">
              <Stethoscope className="h-4 w-4" />
              {isEn ? `${filteredSpecialties.length} specialties` : `${filteredSpecialties.length} chuyên khoa`}
            </div>
          </div>
        </ScrollReveal>

        {isLoading ? (
          <div className="public-page-card border-dashed px-6 py-14 text-center text-muted-foreground">
            {isEn ? 'Loading specialties...' : 'Đang tải chuyên khoa...'}
          </div>
        ) : filteredSpecialties.length > 0 ? (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {filteredSpecialties.map((specialty, index) => (
              <ScrollReveal key={specialty.id} delay={index * 40}>
                <SpecialtyCard specialty={specialty} />
              </ScrollReveal>
            ))}
          </div>
        ) : (
          <div className="public-page-card border-dashed px-6 py-14 text-center">
            <p className="text-lg font-semibold text-foreground">
              {isEn ? 'No specialties found.' : 'Không tìm thấy chuyên khoa phù hợp.'}
            </p>
            <p className="mx-auto mt-3 max-w-xl text-sm leading-6 text-muted-foreground">
              {isEn ? 'Try a broader keyword or clear search.' : 'Thử từ khóa rộng hơn hoặc xóa tìm kiếm.'}
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
