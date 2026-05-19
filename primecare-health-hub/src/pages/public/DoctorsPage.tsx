import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { CalendarDays, Search, Users } from 'lucide-react';
import { ScrollReveal } from '@/components/ScrollReveal';
import { DoctorCard } from '@/components/DoctorCard';
import { AppPagination } from '@/components/AppPagination';
import { useBranchSpecialties, useBranches, useDoctors, useSpecialties } from '@/hooks/use-public-data';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Button } from '@/components/ui/button';
import { useTranslation } from 'react-i18next';
import { isPublicDoctorBookable } from '@/lib/doctor-readiness';

export default function DoctorsPage() {
  const { i18n } = useTranslation();
  const isEn = i18n.language.startsWith('en');
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [specialty, setSpecialty] = useState('all');
  const [branch, setBranch] = useState('all');
  const [page, setPage] = useState(0);
  const pageSize = 12;

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedSearch(search.trim());
    }, 300);
    return () => window.clearTimeout(timer);
  }, [search]);

  useEffect(() => {
    setPage(0);
  }, [debouncedSearch, specialty, branch]);

  const { data: allSpecialties = [] } = useSpecialties();
  const { data: branchSpecialties = [] } = useBranchSpecialties(branch !== 'all' ? branch : '');
  const { data: branches = [] } = useBranches();
  const specialties = branch === 'all' ? allSpecialties : branchSpecialties;

  useEffect(() => {
    if (specialty === 'all') return;
    if (specialties.some((item) => item.id === specialty)) return;
    setSpecialty('all');
  }, [specialties, specialty]);

  const doctorParams = useMemo(
    () => ({
      page: String(page),
      size: String(pageSize),
      ...(debouncedSearch ? { q: debouncedSearch } : {}),
      ...(specialty !== 'all' ? { specialtyId: specialty } : {}),
      ...(branch !== 'all' ? { branchId: branch } : {}),
    }),
    [branch, debouncedSearch, page, specialty],
  );

  const { data: doctorPage, isLoading } = useDoctors(doctorParams);
  const doctors = useMemo(
    () => (doctorPage?.items ?? []).filter(isPublicDoctorBookable),
    [doctorPage?.items],
  );
  const meta = doctorPage?.meta;
  const totalItems = meta?.totalItems ?? 0;

  return (
    <div className="public-page">
      <div className="container-page py-12">
        <div className="mb-8 flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
          <div>
            <h1 className="public-page-title">{isEn ? 'Doctors' : 'Đội ngũ bác sĩ'}</h1>
            <p className="public-page-subtitle">
              {isEn ? 'Search and filter doctors before viewing details or booking.' : 'Hơn 200 bác sĩ chuyên khoa giàu kinh nghiệm.'}
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
          <div className="grid gap-3 md:grid-cols-[minmax(220px,1fr)_220px_220px_auto] md:items-center">
            <div className="relative">
              <Search className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                id="doctor-search"
                placeholder={isEn ? 'Search by doctor name...' : 'Tìm theo tên bác sĩ...'}
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                className="h-11 rounded-lg bg-card pl-11"
              />
            </div>

            <Select value={specialty} onValueChange={setSpecialty}>
              <SelectTrigger className="h-11 rounded-lg bg-card">
                <SelectValue placeholder={isEn ? 'Specialty' : 'Chuyên khoa'} />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">{isEn ? 'All specialties' : 'Tất cả chuyên khoa'}</SelectItem>
                {specialties.map((item) => (
                  <SelectItem key={item.id} value={item.id}>{item.name}</SelectItem>
                ))}
              </SelectContent>
            </Select>

            <Select value={branch} onValueChange={setBranch}>
              <SelectTrigger className="h-11 rounded-lg bg-card">
                <SelectValue placeholder={isEn ? 'Branch' : 'Cơ sở'} />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">{isEn ? 'All branches' : 'Tất cả cơ sở'}</SelectItem>
                {branches.map((item) => (
                  <SelectItem key={item.id} value={item.id}>{item.name}</SelectItem>
                ))}
              </SelectContent>
            </Select>

            <div className="inline-flex items-center gap-2 rounded-full bg-primary-soft px-3 py-1.5 text-sm font-medium text-primary md:justify-center">
              <Users className="h-4 w-4" />
              {isEn ? `${totalItems} doctors` : `${totalItems} bác sĩ`}
            </div>
          </div>
        </ScrollReveal>

        {isLoading ? (
          <div className="public-page-card border-dashed px-6 py-14 text-center text-muted-foreground">
            {isEn ? 'Loading doctors...' : 'Đang tải bác sĩ...'}
          </div>
        ) : doctors.length > 0 ? (
          <>
            <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
              {doctors.map((doctor, index) => (
                <ScrollReveal key={doctor.id} delay={index * 40}>
                  <DoctorCard doctor={doctor} />
                </ScrollReveal>
              ))}
            </div>

            <AppPagination page={meta?.page ?? 0} totalPages={meta?.totalPages ?? 0} onPageChange={setPage} className="mt-10" />
          </>
        ) : (
          <div className="public-page-card border-dashed px-6 py-14 text-center">
            <p className="text-lg font-semibold text-foreground">
              {isEn ? 'No doctors found.' : 'Không tìm thấy bác sĩ phù hợp.'}
            </p>
            <p className="mx-auto mt-3 max-w-xl text-sm leading-6 text-muted-foreground">
              {isEn ? 'Try fewer filters or clear the keyword.' : 'Thử giảm bộ lọc hoặc xóa từ khóa.'}
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
