import { useEffect, useMemo, useState } from 'react';
import { ScrollReveal } from '@/components/ScrollReveal';
import { AppPagination } from '@/components/AppPagination';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Link, useSearchParams } from 'react-router-dom';
import { Activity, ArrowRight, Calendar, CalendarSearch, Clock } from 'lucide-react';
import { useAvailability, useBranchSpecialties, useBranches, useDoctor, useDoctors } from '@/hooks/use-public-data';
import { useTranslation } from 'react-i18next';
import { toLocalDateInputValue } from '@/lib/date';

export default function AvailabilityPage() {
  const [searchParams] = useSearchParams();
  const { i18n } = useTranslation();
  const isEn = i18n.language.startsWith('en');

  const [branchId, setBranchId] = useState(searchParams.get('branchId') ?? '');
  const [specialtyId, setSpecialtyId] = useState(searchParams.get('specialtyId') ?? '');
  const [doctorId, setDoctorId] = useState(searchParams.get('doctorId') ?? '');
  const [visitDate, setVisitDate] = useState(searchParams.get('date') ?? '');
  const [session, setSession] = useState(searchParams.get('session') ?? 'AM');
  const [slotPage, setSlotPage] = useState(0);
  const today = toLocalDateInputValue();
  const slotPageSize = 9;

  const { data: branches = [] } = useBranches();
  const {
    data: branchSpecialties = [],
    isLoading: branchSpecialtiesLoading,
    isFetching: branchSpecialtiesFetching,
  } = useBranchSpecialties(branchId);
  const { data: doctorDetail } = useDoctor(doctorId);
  const doctorParams = useMemo(
    () => ({
      page: '0',
      size: '1000',
      ...(branchId ? { branchId } : {}),
      ...(specialtyId ? { specialtyId } : {}),
    }),
    [branchId, specialtyId],
  );
  const { data: doctorsPage } = useDoctors(doctorParams);

  useEffect(() => {
    if (!doctorDetail) return;
    if (!branchId && doctorDetail.branchId) {
      setBranchId(doctorDetail.branchId);
    }
    const preferredSpecialty = doctorDetail.specialtyId || doctorDetail.specialtyIds?.[0];
    if (!specialtyId && preferredSpecialty) {
      setSpecialtyId(preferredSpecialty);
    }
  }, [doctorDetail, branchId, specialtyId]);

  const doctors = useMemo(() => doctorsPage?.items ?? [], [doctorsPage?.items]);

  useEffect(() => {
    if (!branchId || branchSpecialtiesLoading || branchSpecialtiesFetching) return;
    if (!specialtyId) return;
    if (branchSpecialties.some((item) => item.id === specialtyId)) return;
    setSpecialtyId('');
    setDoctorId('');
  }, [branchId, branchSpecialties, branchSpecialtiesFetching, branchSpecialtiesLoading, specialtyId]);

  useEffect(() => {
    if (!doctorId) return;
    if (doctors.some((item) => item.id === doctorId)) return;
    setDoctorId('');
  }, [doctorId, doctors]);

  const availabilityParams = useMemo(() => {
    if (!branchId || !specialtyId || !doctorId || !visitDate || !session) {
      return undefined;
    }

    return {
      branchId,
      specialtyId,
      doctorId,
      visitDate,
      session,
      onlyAvailable: 'true',
    };
  }, [branchId, specialtyId, doctorId, visitDate, session]);

  const { data: slots = [], isLoading, error } = useAvailability(availabilityParams);

  useEffect(() => {
    setSlotPage(0);
  }, [branchId, specialtyId, doctorId, visitDate, session]);

  const totalSlotPages = Math.ceil(slots.length / slotPageSize);
  const pagedSlots = slots.slice(slotPage * slotPageSize, slotPage * slotPageSize + slotPageSize);

  return (
    <div className="public-page">
      <div className="container-page py-12">
        <div className="mb-8 text-center">
          <div className="mx-auto mb-3 flex h-14 w-14 items-center justify-center rounded-2xl bg-primary-soft text-primary">
            <CalendarSearch className="h-7 w-7" />
          </div>
          <h1 className="public-page-title">{isEn ? 'Check availability' : 'Tra cứu lịch trống'}</h1>
          <p className="public-page-subtitle mx-auto max-w-2xl">
            {isEn
              ? 'Choose branch, specialty, doctor, date and session to browse available time slots.'
              : 'Chọn cơ sở, chuyên khoa, bác sĩ, ngày khám và buổi để xem các khung giờ còn trống.'}
          </p>
        </div>

        <ScrollReveal className="mb-8">
          <div className="public-page-card p-5 md:p-6">
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
              <div className="space-y-2">
                <label className="text-sm font-medium text-foreground">{isEn ? 'Branch' : 'Cơ sở'}</label>
                <Select value={branchId} onValueChange={(value) => { setBranchId(value); setSpecialtyId(''); setDoctorId(''); }}>
                  <SelectTrigger className="h-11 rounded-lg bg-background"><SelectValue placeholder={isEn ? 'Branch' : 'Cơ sở'} /></SelectTrigger>
                  <SelectContent>{branches.map((branch) => <SelectItem key={branch.id} value={branch.id}>{branch.name}</SelectItem>)}</SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <label className="text-sm font-medium text-foreground">{isEn ? 'Specialty' : 'Chuyên khoa'}</label>
                <Select value={specialtyId} onValueChange={(value) => { setSpecialtyId(value); setDoctorId(''); }} disabled={!branchId}>
                  <SelectTrigger className="h-11 rounded-lg bg-background"><SelectValue placeholder={isEn ? 'Specialty' : 'Chuyên khoa'} /></SelectTrigger>
                  <SelectContent>{branchSpecialties.map((specialty) => <SelectItem key={specialty.id} value={specialty.id}>{specialty.name}</SelectItem>)}</SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <label className="text-sm font-medium text-foreground">{isEn ? 'Doctor' : 'Bác sĩ'}</label>
                <Select value={doctorId} onValueChange={setDoctorId} disabled={!specialtyId}>
                  <SelectTrigger className="h-11 rounded-lg bg-background"><SelectValue placeholder={isEn ? 'Doctor' : 'Bác sĩ'} /></SelectTrigger>
                  <SelectContent>{doctors.map((doctor) => <SelectItem key={doctor.id} value={doctor.id}>{doctor.fullName}</SelectItem>)}</SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <label className="text-sm font-medium text-foreground">{isEn ? 'Date' : 'Ngày khám'}</label>
                <Input className="h-11 rounded-lg bg-background" type="date" value={visitDate} min={today} onChange={(event) => setVisitDate(event.target.value)} />
              </div>

              <div className="space-y-2">
                <label className="text-sm font-medium text-foreground">{isEn ? 'Session' : 'Buổi khám'}</label>
                <Select value={session} onValueChange={setSession}>
                  <SelectTrigger className="h-11 rounded-lg bg-background"><SelectValue placeholder={isEn ? 'Session' : 'Buổi khám'} /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="AM">{isEn ? 'Morning' : 'Buổi sáng'}</SelectItem>
                    <SelectItem value="PM">{isEn ? 'Afternoon' : 'Buổi chiều'}</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>
          </div>
        </ScrollReveal>

        {!availabilityParams ? (
          <div className="public-page-card border-dashed px-6 py-14 text-center">
            <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-primary-soft text-primary">
              <Calendar className="h-7 w-7" />
            </div>
            <p className="text-sm font-medium text-muted-foreground">{isEn ? 'Select filters to view availability.' : 'Chọn các bộ lọc để xem lịch trống.'}</p>
          </div>
        ) : error ? (
          <div className="public-page-card border-dashed border-destructive/30 bg-destructive/5 px-6 py-14 text-center">
            <p className="text-sm font-medium text-destructive">{isEn ? 'No schedule found for the selected filters.' : 'Không tìm thấy lịch phù hợp với bộ lọc đã chọn.'}</p>
          </div>
        ) : isLoading ? (
          <div className="public-page-card border-dashed px-6 py-14 text-center">
            <div className="mx-auto mb-4 h-10 w-10 animate-spin rounded-full border-4 border-primary/20 border-t-primary" />
            <p className="text-sm font-medium text-muted-foreground">{isEn ? 'Loading available slots...' : 'Đang tải các khung giờ còn trống...'}</p>
          </div>
        ) : slots.length === 0 ? (
          <div className="public-page-card border-dashed px-6 py-14 text-center">
            <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-muted text-muted-foreground">
              <Clock className="h-7 w-7" />
            </div>
            <p className="text-sm font-medium text-muted-foreground">{isEn ? 'No remaining slots for this doctor and session.' : 'Không còn khung giờ trống cho bác sĩ và buổi đã chọn.'}</p>
          </div>
        ) : (
          <div className="space-y-6">
            <div className="flex items-center justify-center">
              <div className="inline-flex items-center gap-2 rounded-full bg-primary-soft px-4 py-2 text-sm font-semibold text-primary">
                <Activity className="h-4 w-4" />
                {isEn ? `${slots.length} slot(s) available` : `${slots.length} khung giờ đang mở`}
                <span className="h-4 w-px bg-primary/30" />
                {session === 'AM' ? (isEn ? 'Morning' : 'Buổi sáng') : (isEn ? 'Afternoon' : 'Buổi chiều')}
              </div>
            </div>

            <div className="grid gap-5 md:grid-cols-2 lg:grid-cols-3">
              {pagedSlots.map((slot, index) => (
                <ScrollReveal key={`${slot.doctorId}-${slot.visitDate}-${slot.slotStart}`} delay={index * 40}>
                  <div className="public-page-card overflow-hidden transition-all hover:shadow-elevated">
                    <div className="flex items-start justify-between gap-4 p-5">
                      <div className="min-w-0">
                        <p className="truncate font-semibold text-foreground">{slot.doctorName}</p>
                        <p className="mt-1 truncate text-sm text-primary">{slot.specialtyName}</p>
                        <p className="mt-1 truncate text-xs text-muted-foreground">{slot.branchName}</p>
                      </div>
                      <span className="shrink-0 rounded-full bg-primary-soft px-2.5 py-1 text-xs font-semibold text-primary">
                        {slot.session === 'AM' ? (isEn ? 'AM' : 'Sáng') : (isEn ? 'PM' : 'Chiều')}
                      </span>
                    </div>

                    <div className="border-t border-border bg-muted/20 px-5 py-4">
                      <div className="grid gap-2 text-sm text-muted-foreground">
                        <div className="flex items-center gap-2">
                          <Calendar className="h-4 w-4 text-primary" />
                          {slot.visitDate}
                        </div>
                        <div className="flex items-center gap-2">
                          <Clock className="h-4 w-4 text-primary" />
                          {slot.slotStart} - {slot.slotEnd}
                        </div>
                      </div>
                      <Button className="mt-4 w-full rounded-lg" asChild>
                        <Link to={`/booking?branchId=${slot.branchId}&specialtyId=${slot.specialtyId}&doctorId=${slot.doctorId}&date=${slot.visitDate}&session=${slot.session}&slot=${slot.slotStart}`}>
                          {isEn ? 'Select this slot' : 'Chọn khung giờ này'} <ArrowRight className="ml-2 h-4 w-4" />
                        </Link>
                      </Button>
                    </div>
                  </div>
                </ScrollReveal>
              ))}
            </div>

            <AppPagination page={slotPage} totalPages={totalSlotPages} onPageChange={setSlotPage} />
          </div>
        )}
      </div>
    </div>
  );
}
