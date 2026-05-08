import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Activity,
  ArrowRight,
  Award,
  Baby,
  Bone,
  CalendarCheck,
  CheckCircle2,
  ClipboardCheck,
  Clock,
  Ear,
  Eye,
  FileText,
  HeartPulse,
  Search,
  ShieldCheck,
  Sparkles,
  Stethoscope,
  UserCheck,
  Users,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from '@/components/ui/accordion';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { ScrollReveal } from '@/components/ScrollReveal';
import {
  useBranchSpecialties,
  useBranches,
  useDoctors,
  useMedicalServices,
  usePublicFaqs,
  useSpecialties,
} from '@/hooks/use-public-data';
import { toLocalDateInputValue } from '@/lib/date';
import type { Branch, Doctor, MedicalService, PublicFaqItem, Specialty } from '@/types/api';
import heroDoctor from '@/assets/hero-doctor.jpg';

const specialtyIcons: LucideIcon[] = [Stethoscope, HeartPulse, Baby, Sparkles, Ear, Bone, Eye, Activity];

function formatCurrency(value: number | undefined, locale: string) {
  if (typeof value !== 'number' || Number.isNaN(value)) return locale === 'en-US' ? 'Contact' : 'Liên hệ';
  return new Intl.NumberFormat(locale, { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(value);
}

function getInitial(name: string) {
  return name.trim().split(/\s+/).pop()?.[0]?.toUpperCase() || 'P';
}

function buildAvailabilityUrl(params: { branchId: string; specialtyId: string; doctorId: string; visitDate: string; session: string }) {
  const query = new URLSearchParams();
  if (params.branchId) query.set('branchId', params.branchId);
  if (params.specialtyId) query.set('specialtyId', params.specialtyId);
  if (params.doctorId) query.set('doctorId', params.doctorId);
  if (params.visitDate) query.set('date', params.visitDate);
  if (params.session) query.set('session', params.session);
  const queryString = query.toString();
  return queryString ? `/availability?${queryString}` : '/availability';
}

function SectionHeader({ eyebrow, title, desc, align = 'center' }: { eyebrow: string; title: string; desc?: string; align?: 'left' | 'center' }) {
  return (
    <div className={`mb-10 ${align === 'center' ? 'mx-auto max-w-2xl text-center' : ''}`}>
      <div className="text-xs font-bold uppercase tracking-widest text-primary">{eyebrow}</div>
      <h2 className="mt-2 text-3xl font-bold tracking-tight text-foreground md:text-4xl">{title}</h2>
      {desc && <p className="mt-3 text-muted-foreground">{desc}</p>}
    </div>
  );
}

function QuickAvailabilitySearch({ branches, isEn }: { branches: Branch[]; isEn: boolean }) {
  const today = toLocalDateInputValue();
  const [branchId, setBranchId] = useState('');
  const [specialtyId, setSpecialtyId] = useState('');
  const [doctorId, setDoctorId] = useState('');
  const [visitDate, setVisitDate] = useState(today);
  const [session, setSession] = useState('AM');

  const { data: branchSpecialties = [] } = useBranchSpecialties(branchId);
  const doctorParams = useMemo(
    () => ({ page: '0', size: '1000', ...(branchId ? { branchId } : {}), ...(specialtyId ? { specialtyId } : {}) }),
    [branchId, specialtyId],
  );
  const { data: doctorsPage } = useDoctors(doctorParams);
  const doctors = doctorsPage?.items ?? [];
  const targetUrl = buildAvailabilityUrl({ branchId, specialtyId, doctorId, visitDate, session });

  return (
    <section className="container-page relative z-10 -mt-8 md:-mt-12">
      <Card className="grid gap-3 p-5 shadow-elevated md:grid-cols-2 lg:grid-cols-6 lg:p-6">
        <Select value={branchId} onValueChange={(value) => { setBranchId(value); setSpecialtyId(''); setDoctorId(''); }}>
          <SelectTrigger className="h-11 rounded-xl"><SelectValue placeholder={isEn ? 'Branch' : 'Cơ sở'} /></SelectTrigger>
          <SelectContent>{branches.map((branch) => <SelectItem key={branch.id} value={branch.id}>{branch.name}</SelectItem>)}</SelectContent>
        </Select>
        <Select value={specialtyId} onValueChange={(value) => { setSpecialtyId(value); setDoctorId(''); }} disabled={!branchId}>
          <SelectTrigger className="h-11 rounded-xl"><SelectValue placeholder={isEn ? 'Specialty' : 'Chuyên khoa'} /></SelectTrigger>
          <SelectContent>{branchSpecialties.map((specialty) => <SelectItem key={specialty.id} value={specialty.id}>{specialty.name}</SelectItem>)}</SelectContent>
        </Select>
        <Select value={doctorId} onValueChange={setDoctorId} disabled={!specialtyId}>
          <SelectTrigger className="h-11 rounded-xl"><SelectValue placeholder={isEn ? 'Doctor' : 'Bác sĩ'} /></SelectTrigger>
          <SelectContent>{doctors.map((doctor) => <SelectItem key={doctor.id} value={doctor.id}>{doctor.fullName}</SelectItem>)}</SelectContent>
        </Select>
        <Input className="h-11 rounded-xl" type="date" min={today} value={visitDate} onChange={(event) => setVisitDate(event.target.value)} aria-label={isEn ? 'Visit date' : 'Ngày khám'} />
        <Select value={session} onValueChange={setSession}>
          <SelectTrigger className="h-11 rounded-xl"><SelectValue placeholder={isEn ? 'Session' : 'Buổi khám'} /></SelectTrigger>
          <SelectContent>
            <SelectItem value="AM">{isEn ? 'Morning' : 'Buổi sáng'}</SelectItem>
            <SelectItem value="PM">{isEn ? 'Afternoon' : 'Buổi chiều'}</SelectItem>
          </SelectContent>
        </Select>
        <Button asChild className="h-11 rounded-xl shadow-elevated">
          <Link to={targetUrl}><Search className="mr-2 h-4 w-4" />{isEn ? 'Find slots' : 'Tìm lịch trống'}</Link>
        </Button>
      </Card>
    </section>
  );
}

function SpecialtiesSection({ specialties, isEn }: { specialties: Specialty[]; isEn: boolean }) {
  const visibleSpecialties = specialties.slice(0, 8);
  return (
    <section className="container-page py-16 md:py-24">
      <SectionHeader
        eyebrow={isEn ? 'Specialties' : 'Chuyên khoa'}
        title={isEn ? 'Care areas for common patient needs' : 'Đầy đủ chuyên khoa, đáp ứng nhiều nhu cầu'}
        desc={isEn ? 'Browse public specialties before choosing a doctor or checking availability.' : 'Xem chuyên khoa công khai trước khi chọn bác sĩ hoặc tra cứu lịch trống.'}
      />
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {visibleSpecialties.length > 0 ? visibleSpecialties.map((specialty, index) => {
          const Icon = specialtyIcons[index % specialtyIcons.length];
          return (
            <Link to="/specialties" key={specialty.id}>
              <Card className="group h-full p-6 transition-all hover:-translate-y-1 hover:border-primary/30 hover:shadow-elevated">
                <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-xl bg-primary-soft text-primary transition-colors group-hover:bg-primary group-hover:text-primary-foreground"><Icon className="h-6 w-6" /></div>
                <h3 className="mb-1 font-semibold text-foreground">{specialty.name}</h3>
                <p className="line-clamp-3 text-sm text-muted-foreground">{specialty.description || (isEn ? 'Details and booking context are being updated.' : 'Thông tin và bối cảnh đặt lịch đang được cập nhật.')}</p>
              </Card>
            </Link>
          );
        }) : (
          <Card className="p-6 text-sm text-muted-foreground sm:col-span-2 lg:col-span-4">{isEn ? 'Specialty data will appear when public records are ready.' : 'Dữ liệu chuyên khoa sẽ hiển thị khi hồ sơ công khai sẵn sàng.'}</Card>
        )}
      </div>
    </section>
  );
}

function DoctorsSection({ doctors, isEn, locale }: { doctors: Doctor[]; isEn: boolean; locale: string }) {
  return (
    <section className="bg-muted/30 py-16 md:py-24">
      <div className="container-page">
        <SectionHeader eyebrow={isEn ? 'Medical team' : 'Đội ngũ'} title={isEn ? 'Featured public doctor profiles' : 'Bác sĩ nổi bật'} desc={isEn ? 'Review specialty, branch, experience, and next schedule context before booking.' : 'Xem chuyên khoa, cơ sở, kinh nghiệm và bối cảnh lịch trước khi đặt khám.'} />
        <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {doctors.length > 0 ? doctors.slice(0, 6).map((doctor) => (
            <Card key={doctor.id} className="overflow-hidden transition-all hover:-translate-y-1 hover:shadow-elevated">
              <div className="flex gap-4 p-5">
                <div className="flex h-20 w-20 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-primary-soft to-accent-soft text-2xl font-bold text-primary">{getInitial(doctor.fullName)}</div>
                <div className="min-w-0 flex-1">
                  <h3 className="truncate font-semibold text-foreground">{doctor.fullName}</h3>
                  <p className="text-sm text-primary">{doctor.specialtyName || doctor.title || (isEn ? 'Doctor' : 'Bác sĩ')}</p>
                  <p className="mt-1 truncate text-xs text-muted-foreground">{doctor.branchName || (isEn ? 'Branch is being updated' : 'Cơ sở đang cập nhật')}</p>
                  <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
                    {typeof doctor.ratingAverage === 'number' && <span>★ {doctor.ratingAverage.toFixed(1)}</span>}
                    {typeof doctor.yearsOfExperience === 'number' && <span>{doctor.yearsOfExperience} {isEn ? 'yrs exp.' : 'năm KN'}</span>}
                    {doctor.nextAvailableDate && <span>{new Intl.DateTimeFormat(locale, { day: '2-digit', month: '2-digit' }).format(new Date(`${doctor.nextAvailableDate}T00:00:00`))}</span>}
                  </div>
                </div>
              </div>
              <div className="flex items-center justify-between border-t bg-muted/20 px-5 py-3">
                <span className="text-xs text-muted-foreground">{doctor.hasUpcomingSchedule ? (isEn ? 'Schedule available' : 'Có lịch sắp tới') : (isEn ? 'View profile' : 'Xem hồ sơ')}</span>
                <Button asChild size="sm" variant="ghost" className="text-primary hover:text-primary"><Link to={`/doctors/${doctor.id}`}>{isEn ? 'View' : 'Chi tiết'} <ArrowRight className="ml-1 h-3.5 w-3.5" /></Link></Button>
              </div>
            </Card>
          )) : (
            <Card className="p-6 text-sm text-muted-foreground sm:col-span-2 lg:col-span-3">{isEn ? 'Doctor previews will appear when public profiles are ready.' : 'Hồ sơ bác sĩ sẽ hiển thị khi dữ liệu công khai sẵn sàng.'}</Card>
          )}
        </div>
      </div>
    </section>
  );
}

function ServicesSection({ services, isEn, locale }: { services: MedicalService[]; isEn: boolean; locale: string }) {
  const visibleServices = services.slice(0, 6);
  return (
    <section className="container-page py-16 md:py-24">
      <SectionHeader eyebrow={isEn ? 'Clinical services' : 'Dịch vụ cận lâm sàng'} title={isEn ? 'Tests, imaging, and supporting services' : 'Xét nghiệm, chẩn đoán hình ảnh'} desc={isEn ? 'Review public service information and preparation notes before choosing a care path.' : 'Xem thông tin dịch vụ và ghi chú chuẩn bị trước khi chọn hướng khám.'} />
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {visibleServices.length > 0 ? visibleServices.map((service) => (
          <Link to={`/medical-services/${service.id}`} key={service.id}>
            <Card className="flex h-full items-center justify-between gap-4 p-5 transition-all hover:border-primary/30 hover:shadow-card">
              <div className="min-w-0">
                <div className="text-xs font-medium uppercase tracking-wider text-primary">{service.serviceType || service.groupName || (isEn ? 'Service' : 'Dịch vụ')}</div>
                <h3 className="mt-1 line-clamp-2 font-semibold text-foreground">{service.name}</h3>
                <div className="mt-1 text-xs text-muted-foreground">{service.code ? `${isEn ? 'Code' : 'Mã'}: ${service.code}` : (isEn ? 'Public service' : 'Dịch vụ công khai')}</div>
              </div>
              <div className="shrink-0 text-right text-sm font-bold text-foreground">{formatCurrency(service.price, locale)}</div>
            </Card>
          </Link>
        )) : (
          <Card className="p-6 text-sm text-muted-foreground sm:col-span-2 lg:col-span-3">{isEn ? 'Service data will appear when public records are ready.' : 'Dữ liệu dịch vụ sẽ hiển thị khi hồ sơ công khai sẵn sàng.'}</Card>
        )}
      </div>
    </section>
  );
}

function StepsSection({ isEn }: { isEn: boolean }) {
  const steps = [
    { icon: CalendarCheck, title: isEn ? 'Choose a slot' : 'Chọn lịch', desc: isEn ? 'Pick branch, specialty, doctor, date, and session that match your need.' : 'Chọn cơ sở, chuyên khoa, bác sĩ, ngày và buổi khám phù hợp.' },
    { icon: ClipboardCheck, title: isEn ? 'Confirm details' : 'Xác nhận', desc: isEn ? 'Continue only after checking the appointment context.' : 'Tiếp tục sau khi đã kiểm tra bối cảnh lịch khám.' },
    { icon: UserCheck, title: isEn ? 'Check in' : 'Check-in', desc: isEn ? 'Use your appointment code when arriving at the clinic.' : 'Dùng mã lịch hẹn khi đến cơ sở khám.' },
    { icon: FileText, title: isEn ? 'Return for results' : 'Nhận kết quả', desc: isEn ? 'Look up appointment or result information from the public portal.' : 'Tra cứu lịch hẹn hoặc kết quả từ cổng công khai.' },
  ];
  return (
    <section className="bg-muted/30 py-16 md:py-24">
      <div className="container-page">
        <SectionHeader eyebrow={isEn ? 'Process' : 'Quy trình'} title={isEn ? 'A simple flow in four steps' : 'Đơn giản chỉ với 4 bước'} />
        <div className="grid gap-6 md:grid-cols-4">
          {steps.map((step, index) => (
            <Card className="h-full p-6 text-center" key={step.title}>
              <div className="mx-auto mb-3 flex h-14 w-14 items-center justify-center rounded-2xl gradient-hero text-primary-foreground shadow-elevated"><step.icon className="h-7 w-7" /></div>
              <div className="text-xs font-bold uppercase text-primary">{isEn ? 'Step' : 'Bước'} {index + 1}</div>
              <h3 className="mt-1 font-semibold text-foreground">{step.title}</h3>
              <p className="mt-2 text-sm text-muted-foreground">{step.desc}</p>
            </Card>
          ))}
        </div>
      </div>
    </section>
  );
}

function FaqSection({ faqs, isEn }: { faqs: PublicFaqItem[]; isEn: boolean }) {
  const fallbackFaqs = [
    { question: isEn ? 'Can I check availability before booking?' : 'Tôi có thể tra cứu lịch trống trước khi đặt không?', answer: isEn ? 'Yes. Use the availability page or the quick form on the homepage to review open slots first.' : 'Có. Bạn có thể dùng trang Lịch trống hoặc form nhanh trên trang chủ để xem khung giờ còn trống trước.' },
    { question: isEn ? 'Can I look up an existing appointment?' : 'Tôi có thể tra cứu lịch hẹn đã đặt không?', answer: isEn ? 'Yes. Use the appointment lookup flow with your appointment code and OTP verification.' : 'Có. Bạn dùng mã lịch hẹn và xác thực OTP trong luồng tra cứu lịch hẹn.' },
    { question: isEn ? 'Where can I view test results?' : 'Tôi xem kết quả ở đâu?', answer: isEn ? 'Use the result lookup page or your patient portal when your account is connected.' : 'Bạn dùng trang tra cứu kết quả hoặc cổng bệnh nhân khi tài khoản đã được liên kết.' },
  ];
  const visibleFaqs = faqs.length > 0
    ? faqs.slice(0, 5).map((faq) => ({ question: (isEn ? faq.questionEn : faq.questionVn) || faq.questionVn || faq.questionEn || '', answer: (isEn ? faq.answerEn : faq.answerVn) || faq.answerVn || faq.answerEn || '' })).filter((faq) => faq.question && faq.answer)
    : fallbackFaqs;

  return (
    <section id="faq" className="container-page py-16 md:py-24">
      <div className="grid gap-10 md:grid-cols-2">
        <div>
          <SectionHeader eyebrow="FAQ" title={isEn ? 'Frequently asked questions' : 'Câu hỏi thường gặp'} align="left" />
          <p className="text-muted-foreground">{isEn ? 'Need help? Contact the clinic hotline ' : 'Không tìm thấy câu trả lời? Liên hệ tổng đài '}<span className="font-semibold text-foreground">1900 1234</span>{isEn ? ' or email ' : ' hoặc email '}<span className="font-semibold text-foreground">hello@primecare.vn</span>.</p>
        </div>
        <Accordion type="single" collapsible className="w-full">
          {visibleFaqs.map((faq, index) => (
            <AccordionItem key={`${faq.question}-${index}`} value={`q${index}`}>
              <AccordionTrigger className="text-left font-medium">{faq.question}</AccordionTrigger>
              <AccordionContent className="text-muted-foreground">{faq.answer}</AccordionContent>
            </AccordionItem>
          ))}
        </Accordion>
      </div>
    </section>
  );
}

export default function HomePage() {
  const { i18n } = useTranslation();
  const isEn = i18n.language.startsWith('en');
  const locale = isEn ? 'en-US' : 'vi-VN';
  const branchesQuery = useBranches();
  const specialtiesQuery = useSpecialties();
  const doctorsQuery = useDoctors({ page: '0', size: '6' });
  const servicesQuery = useMedicalServices();
  const faqsQuery = usePublicFaqs();
  const branches = branchesQuery.data ?? [];
  const specialties = specialtiesQuery.data ?? [];
  const doctors = doctorsQuery.data?.items ?? [];
  const doctorCount = doctorsQuery.data?.meta.totalItems ?? doctors.length;
  const services = servicesQuery.data ?? [];
  const faqs = faqsQuery.data ?? [];
  const numberFormatter = new Intl.NumberFormat(locale);
  const heroFacts = [
    { icon: Award, text: isEn ? 'Trusted care network' : 'Mạng lưới chăm sóc tin cậy' },
    { icon: Users, text: isEn ? `${numberFormatter.format(doctorCount)} doctor profiles` : `${numberFormatter.format(doctorCount)} hồ sơ bác sĩ` },
    { icon: Clock, text: isEn ? 'Availability-first booking' : 'Ưu tiên tra cứu lịch trống' },
  ];

  return (
    <div className="overflow-hidden bg-background">
      <section className="relative overflow-hidden gradient-soft">
        <div className="absolute -left-24 top-10 h-72 w-72 rounded-full bg-primary/10 blur-3xl" aria-hidden />
        <div className="absolute -right-24 bottom-10 h-80 w-80 rounded-full bg-accent/10 blur-3xl" aria-hidden />
        <div className="container-page relative grid items-center gap-12 py-16 md:grid-cols-2 md:py-24">
          <ScrollReveal>
            <div className="space-y-6">
              <span className="inline-flex items-center gap-2 rounded-full border border-primary/20 bg-primary-soft px-3 py-1 text-xs font-medium text-primary"><ShieldCheck className="h-3.5 w-3.5" />{isEn ? 'PrimeCare appointment and patient self-service' : 'PrimeCare đặt lịch và tự phục vụ người bệnh'}</span>
              <h1 className="text-4xl font-bold leading-tight tracking-tight text-foreground md:text-5xl lg:text-6xl">{isEn ? 'Book care faster at ' : 'Đặt lịch khám nhanh tại '}<span className="bg-gradient-to-r from-primary to-accent bg-clip-text text-transparent">PrimeCare</span></h1>
              <p className="text-lg text-muted-foreground md:text-xl">{isEn ? 'Choose a branch, specialty, doctor, and open slot before continuing to the booking flow. Appointment and result lookup remain available after the visit.' : 'Chọn cơ sở, chuyên khoa, bác sĩ và lịch trống trước khi chuyển sang đặt khám. Sau buổi khám vẫn có thể tra cứu lịch hẹn và kết quả.'}</p>
              <div className="flex flex-wrap gap-3">
                <Button asChild size="lg" className="rounded-xl shadow-elevated"><Link to="/booking"><CalendarCheck className="mr-2 h-5 w-5" />{isEn ? 'Book now' : 'Đặt lịch ngay'}</Link></Button>
                <Button asChild size="lg" variant="outline" className="rounded-xl bg-background/70"><Link to="/availability"><Search className="mr-2 h-5 w-5" />{isEn ? 'Check availability' : 'Tra cứu lịch trống'}</Link></Button>
              </div>
              <div className="flex flex-wrap gap-6 pt-4 text-sm text-foreground/85">
                {heroFacts.map((fact) => <div className="flex items-center gap-2" key={fact.text}><fact.icon className="h-4 w-4 text-primary" />{fact.text}</div>)}
              </div>
            </div>
          </ScrollReveal>
          <ScrollReveal delay={120}>
            <div className="relative">
              <div className="absolute -inset-4 rounded-3xl gradient-hero opacity-20 blur-2xl" aria-hidden />
              <img src={heroDoctor} alt={isEn ? 'PrimeCare doctor' : 'Bác sĩ PrimeCare'} width={1024} height={1024} className="relative aspect-[4/3] w-full rounded-3xl object-cover shadow-elevated" />
              <Card className="absolute -bottom-6 -left-6 hidden gap-3 p-4 shadow-elevated md:flex">
                <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-success-soft text-success"><CheckCircle2 className="h-5 w-5" /></div>
                <div><div className="text-xs text-muted-foreground">{isEn ? 'Public data' : 'Dữ liệu công khai'}</div><div className="text-lg font-bold text-foreground">{branchesQuery.isSuccess ? `${numberFormatter.format(branches.length)} ${isEn ? 'branches' : 'cơ sở'}` : 'PrimeCare'}</div></div>
              </Card>
            </div>
          </ScrollReveal>
        </div>
      </section>
      <QuickAvailabilitySearch branches={branches} isEn={isEn} />
      <SpecialtiesSection specialties={specialties} isEn={isEn} />
      <DoctorsSection doctors={doctors} isEn={isEn} locale={locale} />
      <ServicesSection services={services} isEn={isEn} locale={locale} />
      <StepsSection isEn={isEn} />
      <FaqSection faqs={faqs} isEn={isEn} />
      <section className="container-page pb-20">
        <Card className="overflow-hidden border-0 gradient-hero p-10 text-primary-foreground md:p-14">
          <div className="grid items-center gap-6 md:grid-cols-[1fr_auto]">
            <div>
              <h2 className="text-2xl font-bold md:text-3xl">{isEn ? 'Your health, thoughtfully coordinated.' : 'Sức khoẻ của bạn, ưu tiên của chúng tôi.'}</h2>
              <p className="mt-2 opacity-90">{isEn ? 'Start with availability, booking, or lookup from one public portal.' : 'Bắt đầu từ lịch trống, đặt khám hoặc tra cứu trong cùng một cổng công khai.'}</p>
            </div>
            <div className="flex flex-col gap-3 sm:flex-row">
              <Button asChild size="lg" variant="secondary" className="rounded-xl shadow-elevated"><Link to="/availability">{isEn ? 'Find available slots' : 'Tìm lịch trống'} <ArrowRight className="ml-2 h-4 w-4" /></Link></Button>
              <Button asChild size="lg" variant="outline" className="rounded-xl border-white/30 bg-white/10 text-white hover:bg-white/20 hover:text-white"><Link to="/booking">{isEn ? 'Book now' : 'Đặt lịch ngay'}</Link></Button>
            </div>
          </div>
        </Card>
      </section>
    </div>
  );
}
