import { Link } from 'react-router-dom';
import type { LucideIcon } from 'lucide-react';
import {
  ArrowRight,
  Building2,
  Calendar,
  CheckCircle2,
  ClipboardCheck,
  HeartPulse,
  MapPin,
  Phone,
  Stethoscope,
  Users,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { ScrollReveal } from '@/components/ScrollReveal';
import type { Branch, Doctor, Specialty } from '@/types/api';
import heroImage from '@/assets/hero-hospital.png';

export interface HomeHeroStat {
  value: string;
  label: string;
  detail: string;
}

export interface HomeTextIconItem {
  icon: LucideIcon;
  text: string;
}

export interface HomePatientPathItem {
  to: string;
  icon: LucideIcon;
  title: string;
  text: string;
  primary?: boolean;
}

export interface HomeProofItem {
  icon: LucideIcon;
  title: string;
  text: string;
}

export interface HomeBookingStep {
  step: string;
  title: string;
  desc: string;
}

function formatPreviewDate(value: string | undefined, locale: string) {
  if (!value) return null;
  const date = new Date(/^\d{4}-\d{2}-\d{2}$/.test(value) ? `${value}T00:00:00` : value);
  if (Number.isNaN(date.getTime())) return value;

  return new Intl.DateTimeFormat(locale, {
    weekday: 'short',
    day: '2-digit',
    month: '2-digit',
  }).format(date);
}

export function HomeHeroSection({
  isEn,
  heroStats,
  heroAssurance,
  sampleDoctor,
  sampleBranch,
}: {
  isEn: boolean;
  heroStats: HomeHeroStat[];
  heroAssurance: HomeTextIconItem[];
  sampleDoctor?: Doctor;
  sampleBranch?: Branch;
}) {
  return (
    <section className="relative overflow-hidden bg-primary text-primary-foreground">
      <div className="absolute inset-0">
        <img src={heroImage} alt="PrimeCare" className="h-full w-full object-cover opacity-20" />
        <div className="absolute inset-0 bg-[linear-gradient(115deg,rgba(4,32,68,0.98),rgba(7,45,91,0.95)_48%,rgba(18,93,167,0.74))]" />
        <div className="absolute left-[-12%] top-[-20%] h-72 w-72 rounded-full bg-foreground/10 blur-3xl" />
        <div className="absolute bottom-[-18%] right-[-8%] h-96 w-96 rounded-full bg-accent/20 blur-3xl" />
      </div>

      <div className="container-wide relative z-10 py-14 md:py-20 lg:py-24">
        <div className="grid gap-10 lg:grid-cols-[minmax(0,1.05fr)_minmax(360px,0.95fr)] lg:items-center">
          <div className="max-w-4xl">
            <span className="inline-flex items-center rounded-full border border-white/20 bg-foreground/10 px-4 py-1.5 text-xs font-semibold uppercase tracking-[0.18em] text-white/82">
              {isEn ? 'PrimeCare appointment and patient self-service' : 'PrimeCare đặt lịch và tra cứu chăm sóc'}
            </span>

            <h1
              className="mt-5 max-w-4xl text-[2.65rem] font-semibold tracking-[-0.045em] text-white sm:text-5xl lg:text-[4rem]"
              style={{ lineHeight: '1.03' }}
            >
              {isEn
                ? 'Book care with less uncertainty, from doctor choice to result lookup'
                : 'Đặt lịch khám dễ yên tâm hơn, từ chọn bác sĩ đến tra cứu kết quả'}
            </h1>

            <p className="mt-5 max-w-2xl text-base leading-8 text-white/82 md:text-lg">
              {isEn
                ? 'PrimeCare helps patients and caregivers choose the right care path, confirm clinic details, request an appointment, and return later for appointment or result information.'
                : 'PrimeCare giúp người bệnh và người nhà chọn đúng hướng khám, kiểm tra thông tin cơ sở, gửi yêu cầu đặt lịch và quay lại tra cứu lịch hẹn hoặc kết quả khi cần.'}
            </p>

            <div className="mt-8 flex flex-col gap-3 sm:flex-row sm:items-center">
              <Button
                size="lg"
                asChild
                className="h-12 w-full rounded-full bg-foreground px-8 font-semibold text-background shadow-[0_18px_36px_rgba(0,0,0,0.18)] transition-transform hover:-translate-y-0.5 hover:bg-foreground/90 sm:w-auto"
              >
                <Link to="/booking">
                  <Calendar className="mr-2 h-5 w-5" />
                  {isEn ? 'Book an appointment' : 'Đặt lịch khám'}
                </Link>
              </Button>

              <Button
                size="lg"
                variant="outline"
                asChild
                className="h-12 w-full rounded-full border-white/30 bg-foreground/10 px-6 text-white hover:bg-foreground/20 hover:text-white sm:w-auto"
              >
                <Link to="/doctors">
                  <Users className="mr-2 h-5 w-5" />
                  {isEn ? 'Find a doctor' : 'Tìm bác sĩ'}
                </Link>
              </Button>
            </div>

            <div className="mt-5 flex flex-wrap gap-x-5 gap-y-2 text-sm text-white/76">
              <Link to="/medical-services" className="inline-flex items-center gap-1.5 transition-colors hover:text-white">
                {isEn ? 'View services' : 'Xem dịch vụ'}
                <ArrowRight className="h-3.5 w-3.5" />
              </Link>
              <Link to="/appointments/lookup" className="inline-flex items-center gap-1.5 transition-colors hover:text-white">
                {isEn ? 'Appointment lookup' : 'Tra cứu lịch hẹn'}
                <ArrowRight className="h-3.5 w-3.5" />
              </Link>
              <Link to="/results/lookup" className="inline-flex items-center gap-1.5 transition-colors hover:text-white">
                {isEn ? 'Result lookup' : 'Tra cứu kết quả'}
                <ArrowRight className="h-3.5 w-3.5" />
              </Link>
            </div>

            <div className="mt-8 grid gap-3 text-sm text-white/78 sm:grid-cols-3">
              {heroAssurance.map((item) => (
                <div key={item.text} className="flex items-start gap-2.5 rounded-2xl border border-white/14 bg-foreground/[0.07] p-3">
                  <item.icon className="mt-0.5 h-4 w-4 shrink-0 text-white" />
                  <span className="leading-5">{item.text}</span>
                </div>
              ))}
            </div>
          </div>

          <ScrollReveal className="lg:justify-self-end">
            <div className="relative">
              <div className="absolute -inset-3 rounded-[2.3rem] bg-foreground/10 blur-2xl" aria-hidden />
              <div className="relative rounded-[2rem] border border-border/70 bg-card/95 p-4 text-card-foreground shadow-[0_34px_90px_rgba(3,26,54,0.34)] md:p-6">
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">
                      {isEn ? 'Live public context' : 'Dữ liệu công khai đang dùng'}
                    </p>
                    <h2 className="mt-2 text-2xl font-semibold tracking-tight text-foreground">
                      {isEn ? 'Details patients can verify first' : 'Thông tin người bệnh có thể kiểm tra trước'}
                    </h2>
                  </div>
                  <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-primary text-primary-foreground">
                    <ClipboardCheck className="h-5 w-5" />
                  </div>
                </div>

                <div className="mt-5 grid grid-cols-1 gap-3 min-[420px]:grid-cols-3">
                  {heroStats.map((item) => (
                    <div key={item.label} className="rounded-2xl border border-border/70 bg-surface-alt/70 px-4 py-3">
                      <p className="text-2xl font-semibold tracking-tight text-primary">{item.value}</p>
                      <p className="mt-1 text-sm font-semibold text-foreground">{item.label}</p>
                      <p className="mt-1 text-xs leading-5 text-muted-foreground">{item.detail}</p>
                    </div>
                  ))}
                </div>

                <div className="mt-5 grid gap-3">
                  <div className="rounded-[1.35rem] border border-primary/10 bg-primary/[0.045] p-4">
                    <div className="flex items-start gap-3">
                      <Users className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
                      <div>
                        <p className="text-sm font-semibold text-foreground">
                          {sampleDoctor?.fullName || (isEn ? 'Doctor profiles are loading' : 'Đang tải hồ sơ bác sĩ')}
                        </p>
                        <p className="mt-1 text-sm leading-5 text-muted-foreground">
                          {sampleDoctor
                            ? [sampleDoctor.title, sampleDoctor.specialtyName, sampleDoctor.branchName].filter(Boolean).join(' / ')
                            : isEn
                              ? 'Profiles can show specialty, branch, and schedule context when available.'
                              : 'Hồ sơ có thể hiển thị chuyên khoa, cơ sở và bối cảnh lịch khám khi có dữ liệu.'}
                        </p>
                      </div>
                    </div>
                  </div>

                  <div className="rounded-[1.35rem] border border-primary/10 bg-background p-4">
                    <div className="flex items-start gap-3">
                      <Phone className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
                      <div>
                        <p className="text-sm font-semibold text-foreground">
                          {sampleBranch?.name || (isEn ? 'Clinic contact is loading' : 'Đang tải thông tin cơ sở')}
                        </p>
                        <p className="mt-1 text-sm leading-5 text-muted-foreground">
                          {sampleBranch
                            ? [sampleBranch.phone, sampleBranch.address].filter(Boolean).join(' / ') ||
                              (isEn ? 'Address and phone update from branch records.' : 'Địa chỉ và số điện thoại được cập nhật từ hồ sơ cơ sở.')
                            : isEn
                              ? 'Branch records keep phone and address close to the booking path.'
                              : 'Hồ sơ cơ sở giữ số điện thoại và địa chỉ gần với bước đặt lịch.'}
                        </p>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </ScrollReveal>
        </div>
      </div>
    </section>
  );
}

export function HomePatientPathsSection({ patientPaths }: { patientPaths: HomePatientPathItem[] }) {
  return (
    <section className="relative z-20 -mt-8">
      <div className="container-wide">
        <ScrollReveal>
          <div className="rounded-[2rem] border border-border/70 bg-card p-3 shadow-[0_20px_60px_hsl(var(--shadow-color)_/_0.12)] md:p-4">
            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-6">
              {patientPaths.map((item) => (
                <Link
                  key={item.to}
                  to={item.to}
                  className={
                    item.primary
                      ? 'group rounded-[1.5rem] bg-primary p-5 text-primary-foreground shadow-soft transition-all duration-300 hover:-translate-y-1 hover:bg-primary/92 xl:col-span-2'
                      : 'group rounded-[1.5rem] border border-border/70 bg-background p-5 transition-all duration-300 hover:-translate-y-1 hover:border-primary/20 hover:bg-primary/[0.035] hover:shadow-card'
                  }
                >
                  <div className="flex min-h-[9rem] flex-col justify-between gap-5">
                    <div className="flex items-start justify-between gap-4">
                      <div
                        className={
                          item.primary
                            ? 'flex h-11 w-11 items-center justify-center rounded-2xl bg-primary-foreground/20 text-primary-foreground'
                            : 'flex h-11 w-11 items-center justify-center rounded-2xl bg-primary/10 text-primary'
                        }
                      >
                        <item.icon className="h-5 w-5" />
                      </div>
                      <ArrowRight
                        className={
                          item.primary
                            ? 'h-4 w-4 text-white/70 transition-transform group-hover:translate-x-0.5 group-hover:text-white'
                            : 'h-4 w-4 text-muted-foreground transition-transform group-hover:translate-x-0.5 group-hover:text-primary'
                        }
                      />
                    </div>
                    <div>
                      <h2 className="text-lg font-semibold tracking-tight">{item.title}</h2>
                      <p className={item.primary ? 'mt-2 text-sm leading-6 text-white/78' : 'mt-2 text-sm leading-6 text-muted-foreground'}>
                        {item.text}
                      </p>
                    </div>
                  </div>
                </Link>
              ))}
            </div>
          </div>
        </ScrollReveal>
      </div>
    </section>
  );
}

export function HomeTrustSection({
  isEn,
  proofItems,
  publicDataSummary,
}: {
  isEn: boolean;
  proofItems: HomeProofItem[];
  publicDataSummary: string;
}) {
  return (
    <section className="section-padding">
      <div className="container-wide">
        <div className="grid gap-8 lg:grid-cols-[0.88fr_1.12fr] lg:items-start">
          <ScrollReveal>
            <div className="lg:sticky lg:top-24">
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">
                {isEn ? 'Trust through visible details' : 'Tin cậy nhờ thông tin rõ ràng'}
              </p>
              <h2 className="mt-3 max-w-xl text-3xl font-semibold tracking-tight text-foreground md:text-4xl">
                {isEn ? 'A calmer first step for patients who need clarity' : 'Bước đầu rõ ràng hơn cho người bệnh cần sự yên tâm'}
              </h2>
              <p className="mt-4 max-w-xl text-base leading-7 text-muted-foreground">
                {isEn
                  ? 'PrimeCare does not ask patients to guess. The homepage leads people toward the practical details they need before booking: care type, doctor, location, time, and where to return after the visit.'
                  : 'PrimeCare không để người bệnh phải tự đoán. Trang chủ dẫn người dùng đến những thông tin cần kiểm tra trước khi đặt lịch: nhu cầu khám, bác sĩ, cơ sở, thời gian và nơi quay lại sau buổi khám.'}
              </p>

              <div className="mt-6 rounded-[1.5rem] border border-primary/10 bg-primary/[0.04] p-5">
                <div className="flex items-center gap-2 text-sm font-semibold text-primary">
                  <CheckCircle2 className="h-4 w-4" />
                  {isEn ? 'Current public data' : 'Dữ liệu công khai hiện có'}
                </div>
                <p className="mt-3 text-sm leading-6 text-muted-foreground">{publicDataSummary}</p>
              </div>
            </div>
          </ScrollReveal>

          <div className="grid gap-4 sm:grid-cols-3">
            {proofItems.map((item, index) => (
              <ScrollReveal key={item.title} delay={index * 80}>
                <div className="h-full rounded-[1.5rem] border border-border/70 bg-card p-5 shadow-card">
                  <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-primary/10 text-primary">
                    <item.icon className="h-5 w-5" />
                  </div>
                  <h3 className="mt-5 text-lg font-semibold tracking-tight text-foreground">{item.title}</h3>
                  <p className="mt-3 text-sm leading-6 text-muted-foreground">{item.text}</p>
                </div>
              </ScrollReveal>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}

export function HomePreviewSection({
  isEn,
  locale,
  featuredSpecialties,
  featuredDoctors,
  featuredBranches,
  doctorCount,
  branchCount,
  doctorsReady,
  branchesReady,
  countLabel,
}: {
  isEn: boolean;
  locale: string;
  featuredSpecialties: Specialty[];
  featuredDoctors: Doctor[];
  featuredBranches: Branch[];
  doctorCount: number;
  branchCount: number;
  doctorsReady: boolean;
  branchesReady: boolean;
  countLabel: (count: number, label: string, fallback: string) => string;
}) {
  return (
    <section className="section-padding bg-surface-alt">
      <div className="container-wide">
        <ScrollReveal className="mb-8 flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">
              {isEn ? 'Preview the system' : 'Xem nhanh trước khi quyết định'}
            </p>
            <h2 className="mt-3 max-w-3xl text-3xl font-semibold tracking-tight text-foreground md:text-4xl">
              {isEn ? 'Explore services, specialties, doctors, and facilities without starting over' : 'Xem dịch vụ, chuyên khoa, bác sĩ và cơ sở mà không phải bắt đầu lại'}
            </h2>
          </div>
          <Button variant="outline" asChild className="rounded-full bg-background">
            <Link to="/booking">
              {isEn ? 'Start booking' : 'Bắt đầu đặt lịch'}
              <ArrowRight className="ml-2 h-4 w-4" />
            </Link>
          </Button>
        </ScrollReveal>

        <div className="grid grid-cols-1 gap-5 lg:grid-cols-2 xl:grid-cols-4">
          <ScrollReveal>
            <div className="flex h-full flex-col rounded-[1.75rem] border border-border/70 bg-card p-5 shadow-card">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">
                    {isEn ? 'Medical services' : 'Dịch vụ y tế'}
                  </p>
                  <h3 className="mt-2 text-xl font-semibold tracking-tight text-foreground">
                    {isEn ? 'Start from the service you need' : 'Bắt đầu từ dịch vụ bạn cần'}
                  </h3>
                </div>
                <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-primary/10 text-primary">
                  <HeartPulse className="h-5 w-5" />
                </div>
              </div>
              <p className="mt-5 flex-1 text-sm leading-6 text-muted-foreground">
                {isEn
                  ? 'Open the service directory to review service information, pricing context, and the next booking step.'
                  : 'Mở danh mục dịch vụ để xem thông tin dịch vụ, giá tham khảo và bước tiếp theo để đặt lịch.'}
              </p>
              <Button variant="outline" asChild className="mt-5 rounded-full">
                <Link to="/medical-services">
                  {isEn ? 'View services' : 'Xem dịch vụ'}
                  <ArrowRight className="ml-2 h-4 w-4" />
                </Link>
              </Button>
            </div>
          </ScrollReveal>

          <ScrollReveal delay={60}>
            <div className="flex h-full flex-col rounded-[1.75rem] border border-border/70 bg-card p-5 shadow-card">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">
                    {isEn ? 'Specialties' : 'Chuyên khoa'}
                  </p>
                  <h3 className="mt-2 text-xl font-semibold tracking-tight text-foreground">
                    {countLabel(featuredSpecialties.length, isEn ? 'care areas previewed' : 'chuyên khoa hiển thị', isEn ? 'Care areas' : 'Nhóm chăm sóc')}
                  </h3>
                </div>
                <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-primary/10 text-primary">
                  <Stethoscope className="h-5 w-5" />
                </div>
              </div>

              <div className="mt-5 grid gap-2">
                {featuredSpecialties.length > 0 ? (
                  featuredSpecialties.map((specialty) => (
                    <Link
                      key={specialty.id}
                      to="/specialties"
                      className="group rounded-2xl bg-muted/30 px-4 py-3 transition-colors hover:bg-primary/5"
                    >
                      <div className="flex items-center justify-between gap-3">
                        <div className="min-w-0">
                          <p className="truncate text-sm font-semibold text-foreground">{specialty.name}</p>
                          <p className="mt-1 text-xs text-muted-foreground">
                            {specialty.code ? (isEn ? `Code ${specialty.code}` : `Mã ${specialty.code}`) : isEn ? 'Public booking' : 'Đặt lịch công khai'}
                          </p>
                        </div>
                        <ArrowRight className="h-4 w-4 shrink-0 text-muted-foreground transition-transform group-hover:translate-x-0.5 group-hover:text-primary" />
                      </div>
                    </Link>
                  ))
                ) : (
                  <div className="rounded-2xl border border-dashed border-border/70 px-4 py-5 text-sm leading-6 text-muted-foreground">
                    {isEn ? 'Specialty previews will appear when public data is ready.' : 'Chuyên khoa sẽ hiển thị khi dữ liệu công khai sẵn sàng.'}
                  </div>
                )}
              </div>

              <Button variant="outline" asChild className="mt-5 rounded-full">
                <Link to="/specialties">
                  {isEn ? 'View specialties' : 'Xem chuyên khoa'}
                  <ArrowRight className="ml-2 h-4 w-4" />
                </Link>
              </Button>
            </div>
          </ScrollReveal>

          <ScrollReveal delay={120}>
            <div className="flex h-full flex-col rounded-[1.75rem] border border-border/70 bg-card p-5 shadow-card">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">
                    {isEn ? 'Doctors' : 'Bác sĩ'}
                  </p>
                  <h3 className="mt-2 text-xl font-semibold tracking-tight text-foreground">
                    {countLabel(doctorsReady ? doctorCount : 0, isEn ? 'public profiles' : 'hồ sơ công khai', isEn ? 'Doctor profiles' : 'Hồ sơ bác sĩ')}
                  </h3>
                </div>
                <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-primary/10 text-primary">
                  <Users className="h-5 w-5" />
                </div>
              </div>

              <div className="mt-5 grid gap-2">
                {featuredDoctors.length > 0 ? (
                  featuredDoctors.map((doctor) => {
                    const nextAvailable = formatPreviewDate(doctor.nextAvailableDate, locale);

                    return (
                      <Link
                        key={doctor.id}
                        to={`/doctors/${doctor.id}`}
                        className="group rounded-2xl bg-muted/30 px-4 py-3 transition-colors hover:bg-primary/5"
                      >
                        <div className="flex items-start justify-between gap-3">
                          <div className="min-w-0">
                            <p className="truncate text-sm font-semibold text-foreground">{doctor.fullName}</p>
                            <p className="mt-1 line-clamp-2 text-xs leading-5 text-muted-foreground">
                              {[doctor.title, doctor.specialtyName, doctor.branchName].filter(Boolean).join(' / ') ||
                                (isEn ? 'Profile details are being updated' : 'Thông tin hồ sơ đang được cập nhật')}
                            </p>
                            <p className="mt-2 inline-flex items-center gap-1.5 text-xs font-semibold text-primary">
                              <Calendar className="h-3.5 w-3.5" />
                              {nextAvailable || (isEn ? 'Schedule in booking' : 'Lịch trong đặt khám')}
                            </p>
                          </div>
                          <ArrowRight className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground transition-transform group-hover:translate-x-0.5 group-hover:text-primary" />
                        </div>
                      </Link>
                    );
                  })
                ) : (
                  <div className="rounded-2xl border border-dashed border-border/70 px-4 py-5 text-sm leading-6 text-muted-foreground">
                    {isEn ? 'Doctor previews will appear when public profiles are ready.' : 'Hồ sơ bác sĩ sẽ hiển thị khi dữ liệu công khai sẵn sàng.'}
                  </div>
                )}
              </div>

              <Button variant="outline" asChild className="mt-5 rounded-full">
                <Link to="/doctors">
                  {isEn ? 'Compare doctors' : 'So sánh bác sĩ'}
                  <ArrowRight className="ml-2 h-4 w-4" />
                </Link>
              </Button>
            </div>
          </ScrollReveal>

          <ScrollReveal delay={180}>
            <div className="flex h-full flex-col rounded-[1.75rem] border border-border/70 bg-card p-5 shadow-card">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">
                    {isEn ? 'Facilities' : 'Cơ sở'}
                  </p>
                  <h3 className="mt-2 text-xl font-semibold tracking-tight text-foreground">
                    {countLabel(branchesReady ? branchCount : 0, isEn ? 'clinic locations' : 'cơ sở khám', isEn ? 'Clinic locations' : 'Cơ sở khám')}
                  </h3>
                </div>
                <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-primary/10 text-primary">
                  <Building2 className="h-5 w-5" />
                </div>
              </div>

              <div className="mt-5 grid gap-2">
                {featuredBranches.length > 0 ? (
                  featuredBranches.map((branch) => (
                    <Link
                      key={branch.id}
                      to={`/availability?branchId=${branch.id}`}
                      className="group rounded-2xl bg-muted/30 px-4 py-3 transition-colors hover:bg-primary/5"
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                          <p className="truncate text-sm font-semibold text-foreground">{branch.name}</p>
                          <p className="mt-1 flex items-start gap-1.5 text-xs leading-5 text-muted-foreground">
                            <MapPin className="mt-0.5 h-3.5 w-3.5 shrink-0 text-primary" />
                            <span className="line-clamp-2">{branch.address || (isEn ? 'Address is being updated' : 'Đang cập nhật địa chỉ')}</span>
                          </p>
                          <p className="mt-2 flex items-center gap-1.5 text-xs font-semibold text-primary">
                            <Phone className="h-3.5 w-3.5" />
                            <span className="truncate">{branch.phone || (isEn ? 'Phone is being updated' : 'Đang cập nhật số điện thoại')}</span>
                          </p>
                        </div>
                        <ArrowRight className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground transition-transform group-hover:translate-x-0.5 group-hover:text-primary" />
                      </div>
                    </Link>
                  ))
                ) : (
                  <div className="rounded-2xl border border-dashed border-border/70 px-4 py-5 text-sm leading-6 text-muted-foreground">
                    {isEn ? 'Facility previews will appear when branch data is ready.' : 'Thông tin cơ sở sẽ hiển thị khi dữ liệu công khai sẵn sàng.'}
                  </div>
                )}
              </div>

              <Button variant="outline" asChild className="mt-5 rounded-full">
                <Link to="/branches">
                  {isEn ? 'View facilities' : 'Xem cơ sở'}
                  <ArrowRight className="ml-2 h-4 w-4" />
                </Link>
              </Button>
            </div>
          </ScrollReveal>
        </div>
      </div>
    </section>
  );
}

export function HomeBookingStepsSection({
  isEn,
  bookingSteps,
}: {
  isEn: boolean;
  bookingSteps: HomeBookingStep[];
}) {
  return (
    <section className="section-padding">
      <div className="container-wide">
        <div className="grid gap-8 lg:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)] lg:items-center">
          <ScrollReveal>
            <div>
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">
                {isEn ? 'A guided booking path' : 'Luồng đặt lịch có hướng dẫn'}
              </p>
              <h2 className="mt-3 max-w-2xl text-3xl font-semibold tracking-tight text-foreground md:text-4xl">
                {isEn ? 'Three steps keep the visit request clear' : 'Ba bước giúp yêu cầu khám rõ ràng hơn'}
              </h2>
              <p className="mt-4 max-w-xl text-base leading-7 text-muted-foreground">
                {isEn
                  ? 'The booking flow keeps clinical choices before patient details, so people understand what they are requesting before submitting.'
                  : 'Luồng đặt lịch đặt lựa chọn khám lên trước thông tin cá nhân, giúp người bệnh hiểu rõ mình đang gửi yêu cầu gì trước khi xác nhận.'}
              </p>
            </div>
          </ScrollReveal>

          <div className="grid gap-4">
            {bookingSteps.map((item, index) => (
              <ScrollReveal key={item.step} delay={index * 80}>
                <div className="grid gap-4 rounded-[1.5rem] border border-border/70 bg-card p-5 shadow-card sm:grid-cols-[4.5rem_1fr] sm:items-center">
                  <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-primary/[0.07] text-2xl font-semibold tracking-tight text-primary">
                    {item.step}
                  </div>
                  <div>
                    <h3 className="text-lg font-semibold tracking-tight text-foreground">{item.title}</h3>
                    <p className="mt-1 text-sm leading-6 text-muted-foreground">{item.desc}</p>
                  </div>
                </div>
              </ScrollReveal>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}

export function HomeCtaSection({ isEn }: { isEn: boolean }) {
  return (
    <section className="section-padding pt-0">
      <div className="container-wide">
        <ScrollReveal>
          <div className="relative overflow-hidden rounded-[2rem] bg-primary p-6 text-primary-foreground shadow-[0_24px_70px_hsl(var(--shadow-color)_/_0.16)] md:p-8">
            <div className="absolute right-[-6rem] top-[-6rem] h-64 w-64 rounded-full bg-foreground/10 blur-3xl" aria-hidden />
            <div className="relative grid gap-7 lg:grid-cols-[minmax(0,1fr)_auto] lg:items-center">
              <div>
                <p className="text-sm font-semibold uppercase tracking-[0.16em] text-white/70">
                  {isEn ? 'When you are ready' : 'Khi bạn đã sẵn sàng'}
                </p>
                <h2 className="mt-3 max-w-3xl text-3xl font-semibold tracking-tight text-white md:text-4xl">
                  {isEn ? 'Start a booking request or return to existing appointment information' : 'Bắt đầu đặt lịch hoặc quay lại thông tin lịch hẹn đã có'}
                </h2>
                <p className="mt-3 max-w-3xl text-sm leading-6 text-white/76">
                  {isEn
                    ? 'PrimeCare keeps the main patient actions in one place: book, reopen an appointment slip, or check whether a result summary is available.'
                    : 'PrimeCare giữ các thao tác chính của người bệnh ở cùng một nơi: đặt lịch, mở lại phiếu hẹn hoặc kiểm tra xem tóm tắt kết quả đã có chưa.'}
                </p>
              </div>

              <div className="flex flex-col gap-3 sm:flex-row lg:justify-end">
                <Button size="lg" asChild className="h-12 rounded-full bg-foreground px-8 font-semibold text-background hover:bg-foreground/90">
                  <Link to="/booking">
                    <Calendar className="mr-2 h-5 w-5" />
                    {isEn ? 'Book appointment' : 'Đặt lịch khám'}
                  </Link>
                </Button>
                <Button size="lg" variant="outline" asChild className="h-12 rounded-full border-white/30 bg-foreground/10 text-white hover:bg-foreground/20 hover:text-white">
                  <Link to="/appointments/lookup">{isEn ? 'Appointment lookup' : 'Tra cứu lịch hẹn'}</Link>
                </Button>
                <Button size="lg" variant="outline" asChild className="h-12 rounded-full border-white/30 bg-foreground/10 text-white hover:bg-foreground/20 hover:text-white">
                  <Link to="/results/lookup">{isEn ? 'Result lookup' : 'Tra cứu kết quả'}</Link>
                </Button>
              </div>
            </div>
          </div>
        </ScrollReveal>
      </div>
    </section>
  );
}
