import { Link, useParams } from 'react-router-dom';
import {
  BadgeCheck,
  Building2,
  Calendar,
  ChevronRight,
  Clock3,
  GraduationCap,
  Languages,
  ShieldCheck,
  Star,
  Stethoscope,
} from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { useQueries } from '@tanstack/react-query';
import { UserAvatar } from '@/components/UserAvatar';
import { Button } from '@/components/ui/button';
import { ScrollReveal } from '@/components/ScrollReveal';
import { useDoctor } from '@/hooks/use-public-data';
import { useTranslation } from 'react-i18next';
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from '@/components/ui/breadcrumb';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import apiClient from '@/lib/api-client';
import { normalizeAvailability, unwrapApiData } from '@/lib/api-adapters';
import type {
  ApiResponse,
  AvailabilitySlot,
  BranchSessionType,
  DoctorCareerTimelineItem,
} from '@/types/api';

function splitContent(value?: string) {
  if (!value) return [] as string[];
  return value
    .split(/\n|•|\u2022|\||;/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function formatLongDate(value: string | undefined, locale: string) {
  if (!value) return null;
  const date = new Date(/^\d{4}-\d{2}-\d{2}$/.test(value) ? `${value}T00:00:00` : value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat(locale, {
    weekday: 'long',
    day: '2-digit',
    month: 'long',
    year: 'numeric',
  }).format(date);
}

function formatShortDate(value: string | undefined, locale: string) {
  if (!value) return null;
  const date = new Date(/^\d{4}-\d{2}-\d{2}$/.test(value) ? `${value}T00:00:00` : value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat(locale, {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(date);
}

function formatDateTime(value: string | undefined, locale: string) {
  if (!value) return null;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return null;
  return new Intl.DateTimeFormat(locale, {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}

function addDays(dateStr: string, days: number) {
  const date = new Date(dateStr);
  date.setDate(date.getDate() + days);
  return date.toISOString().slice(0, 10);
}

function getSessionLabel(session: BranchSessionType, isEn: boolean) {
  return session === 'MORNING' || session === 'AM'
    ? isEn
      ? 'Morning'
      : 'Buổi sáng'
    : isEn
      ? 'Afternoon'
      : 'Buổi chiều';
}

function parseTimelineText(items: string[]): DoctorCareerTimelineItem[] {
  const parsed = items
    .map((item) => {
      const normalized = item.replace(/^[•\-\s]+/, '').trim();
      if (!normalized) return null;

      const match = normalized.match(/^((?:\d{4}|Hiện tại|Present)(?:\s*[-–]\s*(?:\d{4}|Hiện tại|Present))?)\s*[:|-]?\s*(.+)$/i);
      const period = match?.[1]?.trim();
      const body = (match?.[2] ?? normalized).trim();
      const [title, organization, description] = body.split('|').map((part) => part.trim()).filter(Boolean);

      return {
        period,
        title: title || normalized,
        organization,
        description,
      } satisfies DoctorCareerTimelineItem;
    })
    .filter((item): item is DoctorCareerTimelineItem => Boolean(item));

  return parsed;
}

function shortenText(value: string | undefined, maxLength = 280) {
  if (!value) return null;
  const normalized = value.replace(/\s+/g, ' ').trim();
  if (normalized.length <= maxLength) return normalized;

  const sliced = normalized.slice(0, maxLength);
  const sentenceCut = Math.max(sliced.lastIndexOf('. '), sliced.lastIndexOf('! '), sliced.lastIndexOf('? '));
  if (sentenceCut > Math.floor(maxLength * 0.55)) {
    return `${sliced.slice(0, sentenceCut + 1).trim()}…`;
  }

  const lastSpace = sliced.lastIndexOf(' ');
  return `${sliced.slice(0, Math.max(lastSpace, 0)).trim()}…`;
}

export default function DoctorDetailPage() {
  const { id = '' } = useParams();
  const { i18n } = useTranslation();
  const isEn = i18n.language.startsWith('en');
  const locale = isEn ? 'en-US' : 'vi-VN';
  const { data: doctor, isLoading } = useDoctor(id);
  const [availabilityPreviewRequested, setAvailabilityPreviewRequested] = useState(false);

  const expertiseItems = useMemo(() => splitContent(doctor?.expertise), [doctor?.expertise]);
  const educationItems = useMemo(() => splitContent(doctor?.education), [doctor?.education]);
  const achievementItems = useMemo(() => splitContent(doctor?.achievements), [doctor?.achievements]);
  const shortBio = useMemo(() => shortenText(doctor?.bio, 260), [doctor?.bio]);
  const nextAvailableLong = formatLongDate(doctor?.nextAvailableDate, locale);
  const nextAvailableShort = formatShortDate(doctor?.nextAvailableDate, locale);
  const profileUpdatedAt = formatDateTime(doctor?.updatedAt, locale);

  const doctorSpecialtyId = doctor?.specialtyId || doctor?.specialtyIds?.[0] || '';
  const doctorBranchId = doctor?.branchId || '';

  useEffect(() => {
    setAvailabilityPreviewRequested(false);
  }, [doctor?.id]);

  const schedulePreviewInputs = useMemo(() => {
    if (!doctor?.id || !doctorBranchId || !doctorSpecialtyId) return [] as Array<{ visitDate: string; session: BranchSessionType }>;
    const startDate = doctor.nextAvailableDate || new Date().toISOString().slice(0, 10);
    return Array.from({ length: 3 }, (_, index) => addDays(startDate, index)).flatMap((visitDate) => [
      { visitDate, session: 'AM' as const },
      { visitDate, session: 'PM' as const },
    ]);
  }, [doctor?.id, doctor?.nextAvailableDate, doctorBranchId, doctorSpecialtyId]);

  const availabilityPreviewQueries = useQueries({
    queries: schedulePreviewInputs.map((input) => ({
      queryKey: ['public', 'doctor-availability-preview', doctor?.id, doctorBranchId, doctorSpecialtyId, input.visitDate, input.session],
      queryFn: async () => {
        const { data } = await apiClient.get<ApiResponse<unknown>>('/public/availability', {
          params: {
            branchId: doctorBranchId,
            specialtyId: doctorSpecialtyId,
            doctorId: doctor?.id,
            visitDate: input.visitDate,
            session: input.session,
            onlyAvailable: 'true',
          },
        });

        return {
          ...input,
          slots: normalizeAvailability(unwrapApiData(data)),
        };
      },
      enabled: availabilityPreviewRequested && Boolean(doctor?.id && doctorBranchId && doctorSpecialtyId),
      staleTime: 60_000,
      refetchOnWindowFocus: false,
      placeholderData: (previousData: { visitDate: string; session: BranchSessionType; slots: AvailabilitySlot[] } | undefined) => previousData,
    })),
  });

  const availabilityPreview = useMemo(() => {
    const grouped = new Map<string, Array<{ session: BranchSessionType; slots: AvailabilitySlot[] }>>();

    availabilityPreviewQueries.forEach((query) => {
      const payload = query.data;
      if (!payload || !payload.slots.length) return;
      const items = grouped.get(payload.visitDate) ?? [];
      items.push({ session: payload.session, slots: payload.slots });
      grouped.set(payload.visitDate, items);
    });

    return Array.from(grouped.entries())
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([visitDate, sessions]) => ({
        visitDate,
        sessions: sessions.sort((a, b) => (a.session === 'AM' ? -1 : 1) - (b.session === 'AM' ? -1 : 1)),
      }))
      .slice(0, 4);
  }, [availabilityPreviewQueries]);

  const availabilityPreviewLoading = availabilityPreviewQueries.some((query) => query.isLoading || query.isFetching);
  const availabilityPreviewError = availabilityPreviewQueries.some((query) => query.isError);

  const timelineItems = useMemo(() => {
    if (doctor?.careerTimeline?.length) return doctor.careerTimeline;
    return parseTimelineText(educationItems);
  }, [doctor?.careerTimeline, educationItems]);

  const credentialHighlights = useMemo(() => {
    if (doctor?.careerTimeline?.length) {
      return doctor.careerTimeline
        .map((item) => item.organization ? `${item.title} • ${item.organization}` : item.title)
        .filter(Boolean)
        .slice(0, 3);
    }

    if (educationItems.length) {
      return educationItems.slice(0, 3);
    }

    return achievementItems.slice(0, 3);
  }, [achievementItems, doctor?.careerTimeline, educationItems]);

  const languages = doctor?.supportedLanguages ?? [];
  const featuredServices = doctor?.featuredServices ?? [];

  if (isLoading) {
    return (
      <div className="section-padding bg-[#F5F7FB]">
        <div className="container-wide max-w-[1240px]">
          <div className="rounded-[32px] border border-border/70 bg-white/90 p-8 text-center text-muted-foreground shadow-sm">
            {isEn ? 'Loading doctor profile...' : 'Đang tải hồ sơ bác sĩ...'}
          </div>
        </div>
      </div>
    );
  }

  if (!doctor) {
    return (
      <div className="section-padding bg-[#F5F7FB]">
        <div className="container-wide max-w-[1240px]">
          <div className="rounded-[32px] border border-dashed border-border/70 bg-white/90 p-8 text-center shadow-sm">
            <p className="text-lg font-semibold text-foreground">
              {isEn ? 'Doctor profile not found.' : 'Không tìm thấy hồ sơ bác sĩ.'}
            </p>
            <p className="mt-3 text-sm leading-6 text-muted-foreground">
              {isEn
                ? 'Please return to the doctor directory to review another public profile.'
                : 'Vui lòng quay lại danh mục bác sĩ để xem một hồ sơ công khai khác.'}
            </p>
          </div>
        </div>
      </div>
    );
  }

  const profileBadges = [
    doctor.specialtyName,
    doctor.branchName,
    doctor.yearsOfExperience ? `${doctor.yearsOfExperience} ${isEn ? 'years experience' : 'năm kinh nghiệm'}` : null,
  ].filter(Boolean) as string[];

  const ratingValue = typeof doctor.ratingAverage === 'number' && Number.isFinite(doctor.ratingAverage)
    ? doctor.ratingAverage.toFixed(1)
    : null;
  const reviewCount = typeof doctor.reviewCount === 'number' ? doctor.reviewCount : 0;
  const hasPublicReviews = Boolean(ratingValue && reviewCount > 0);
  const educationHighlights = educationItems.slice(0, 4);
  const expertiseHighlights = expertiseItems.slice(0, 4);
  const showSeparateEducationList = Boolean(doctor.careerTimeline?.length && educationItems.length);
  const bookingParams = new URLSearchParams({ doctorId: doctor.id });
  if (doctorBranchId) bookingParams.set('branchId', doctorBranchId);
  if (doctorSpecialtyId) bookingParams.set('specialtyId', doctorSpecialtyId);
  const bookingPath = `/booking?${bookingParams.toString()}`;
  const availabilityParams = new URLSearchParams({ doctorId: doctor.id });
  if (doctorBranchId) availabilityParams.set('branchId', doctorBranchId);
  if (doctorSpecialtyId) availabilityParams.set('specialtyId', doctorSpecialtyId);
  const availabilityPath = `/availability?${availabilityParams.toString()}`;
  const bookingStatusText = doctor.bookable === false
    ? isEn
      ? 'Online booking status is being updated for this profile.'
      : 'Trạng thái đặt lịch trực tuyến của hồ sơ này đang được cập nhật.'
    : isEn
      ? 'Online booking can continue from the live slots shown below.'
      : 'Bạn có thể tiếp tục đặt lịch từ các khung giờ trống hiển thị bên dưới.';
  const shortBioText = shortBio || (isEn
    ? 'This public profile is still being completed. You can already review specialty, branch, and current schedule information below.'
    : 'Hồ sơ công khai này đang được hoàn thiện thêm. Bạn vẫn có thể xem chuyên khoa, cơ sở và lịch trống hiện tại ở bên dưới.');

  return (
    <div className="section-padding bg-[#F5F7FB]">
      <div className="container-wide max-w-[1240px] space-y-8">
        <ScrollReveal>
          <div className="relative overflow-hidden rounded-[34px] border border-border/70 bg-card/95 p-6 shadow-card md:p-8">
            <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_right,hsl(var(--primary)/0.16),transparent_34%),radial-gradient(circle_at_bottom_left,hsl(var(--accent)/0.12),transparent_30%)]" />

            <div className="relative">
              <Breadcrumb>
                <BreadcrumbList>
                  <BreadcrumbItem>
                    <BreadcrumbLink asChild>
                      <Link to="/">{isEn ? 'Home' : 'Trang chủ'}</Link>
                    </BreadcrumbLink>
                  </BreadcrumbItem>
                  <BreadcrumbSeparator />
                  <BreadcrumbItem>
                    <BreadcrumbLink asChild>
                      <Link to="/doctors">{isEn ? 'Doctors' : 'Bác sĩ'}</Link>
                    </BreadcrumbLink>
                  </BreadcrumbItem>
                  <BreadcrumbSeparator />
                  <BreadcrumbItem>
                    <BreadcrumbPage>{doctor.fullName}</BreadcrumbPage>
                  </BreadcrumbItem>
                </BreadcrumbList>
              </Breadcrumb>

              <div className="mt-6 grid gap-6 xl:grid-cols-[minmax(0,1.05fr)_340px]">
                <div className="space-y-6">
                  <section className="rounded-[30px] border border-border/70 bg-background/92 p-6 shadow-sm md:p-7">
                    <div className="flex flex-col gap-5 md:flex-row md:items-start">
                      <UserAvatar
                        name={doctor.fullName}
                        avatarUrl={doctor.avatarUrl}
                        size="lg"
                        className="!h-28 !w-28 rounded-[28px] border-4 border-background shadow-lg"
                      />

                      <div className="min-w-0 flex-1">
                        <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">
                          {isEn ? 'PrimeCare doctor profile' : 'Hồ sơ bác sĩ PrimeCare'}
                        </p>
                        <h1 className="mt-2 text-3xl font-semibold tracking-tight text-foreground md:text-4xl">
                          {doctor.fullName}
                        </h1>
                        <p className="mt-2 text-base font-medium text-primary">
                          {doctor.title || (isEn ? 'Consultant doctor' : 'Bác sĩ chuyên khoa')}
                        </p>
                        <p className="mt-4 max-w-3xl text-sm leading-7 text-muted-foreground">
                          {shortBioText}
                        </p>

                        <div className="mt-5 flex flex-wrap gap-2.5">
                          {profileBadges.map((badge) => (
                            <span key={badge} className="rounded-full bg-primary/10 px-3.5 py-2 text-xs font-medium text-primary">
                              {badge}
                            </span>
                          ))}
                        </div>
                      </div>
                    </div>

                    <div className="mt-6 grid gap-4 md:grid-cols-3">
                      <div className="rounded-[1.5rem] border border-border/70 bg-muted/20 p-4">
                        <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.18em] text-primary">
                          <Stethoscope className="h-4 w-4" />
                          {isEn ? 'Specialty' : 'Chuyên khoa tiếp nhận'}
                        </div>
                        <p className="mt-3 text-base font-semibold text-foreground">
                          {doctor.specialtyName || (isEn ? 'Specialty is being updated' : 'Đang cập nhật chuyên khoa')}
                        </p>
                        <p className="mt-2 text-sm leading-6 text-muted-foreground">
                          {expertiseHighlights.length
                            ? expertiseHighlights.slice(0, 2).join(' • ')
                            : isEn
                              ? 'Use the sections below to review detailed clinical focus and services.'
                              : 'Xem các phần bên dưới để kiểm tra chi tiết chuyên môn và dịch vụ tiếp nhận.'}
                        </p>
                      </div>

                      <div className="rounded-[1.5rem] border border-border/70 bg-muted/20 p-4">
                        <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.18em] text-primary">
                          <Building2 className="h-4 w-4" />
                          {isEn ? 'Branch' : 'Cơ sở tiếp nhận'}
                        </div>
                        <p className="mt-3 text-base font-semibold text-foreground">
                          {doctor.branchName || (isEn ? 'Branch is being updated' : 'Đang cập nhật cơ sở tiếp nhận')}
                        </p>
                        <p className="mt-2 text-sm leading-6 text-muted-foreground">
                          {languages.length
                            ? isEn
                              ? `Languages: ${languages.join(' • ')}`
                              : `Ngôn ngữ hỗ trợ: ${languages.join(' • ')}`
                            : isEn
                              ? 'The branch and consultation language details are being completed.'
                              : 'Thông tin về cơ sở và ngôn ngữ trao đổi đang được hoàn thiện thêm.'}
                        </p>
                      </div>

                      <div className="rounded-[1.5rem] border border-border/70 bg-muted/20 p-4">
                        <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.18em] text-primary">
                          <Calendar className="h-4 w-4" />
                          {isEn ? 'Availability' : 'Lịch đặt khám'}
                        </div>
                        <p className="mt-3 text-base font-semibold text-foreground">
                          {nextAvailableShort || (isEn ? 'Updating live schedule' : 'Đang cập nhật lịch trực tuyến')}
                        </p>
                        <p className="mt-2 text-sm leading-6 text-muted-foreground">
                          {bookingStatusText}
                        </p>
                      </div>
                    </div>
                  </section>

                  <section className="rounded-[28px] border border-primary/10 bg-primary/[0.07] p-6 shadow-card md:p-7">
                    <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
                      <div className="max-w-2xl">
                        <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">
                          {isEn ? 'Live schedule preview' : 'Xem trước lịch trống'}
                        </p>
                        <h2 className="mt-2 text-2xl font-semibold tracking-tight text-foreground">
                          {isEn ? `Available slots for ${doctor.fullName}` : `Lịch trống của ${doctor.fullName}`}
                        </h2>
                        <p className="mt-3 text-sm leading-7 text-muted-foreground">
                          {isEn
                            ? 'Review the nearest available days and time slots here before you continue to the full booking flow. The schedule below is refreshed from the current public availability service.'
                            : 'Xem trước các ngày và khung giờ còn trống gần nhất tại đây trước khi tiếp tục sang luồng đặt lịch đầy đủ. Lịch bên dưới được đồng bộ từ dịch vụ lịch trống công khai hiện tại.'}
                        </p>
                      </div>

                      <div className="rounded-full bg-background px-4 py-2 text-xs font-medium text-muted-foreground">
                        {profileUpdatedAt
                          ? isEn
                            ? `Updated ${profileUpdatedAt}`
                            : `Cập nhật ${profileUpdatedAt}`
                          : isEn
                            ? 'Profile information is being completed'
                            : 'Hồ sơ đang được hoàn thiện'}
                      </div>
                    </div>

                    <div className="mt-5 grid gap-3 md:grid-cols-3">
                      <div className="rounded-[1.25rem] border border-border/70 bg-background/85 p-4">
                        <p className="text-xs font-semibold uppercase tracking-[0.16em] text-primary">
                          {isEn ? 'Branch' : 'Cơ sở'}
                        </p>
                        <p className="mt-2 text-sm font-semibold text-foreground">
                          {doctor.branchName || (isEn ? 'Branch is being updated' : 'Đang cập nhật cơ sở')}
                        </p>
                      </div>

                      <div className="rounded-[1.25rem] border border-border/70 bg-background/85 p-4">
                        <p className="text-xs font-semibold uppercase tracking-[0.16em] text-primary">
                          {isEn ? 'Nearest opening' : 'Lịch gần nhất'}
                        </p>
                        <p className="mt-2 text-sm font-semibold text-foreground">
                          {nextAvailableLong || (isEn ? 'Live schedule is being refreshed' : 'Lịch trống trực tiếp đang được cập nhật')}
                        </p>
                      </div>

                      <div className="rounded-[1.25rem] border border-border/70 bg-background/85 p-4">
                        <p className="text-xs font-semibold uppercase tracking-[0.16em] text-primary">
                          {isEn ? 'Booking status' : 'Tình trạng đặt lịch'}
                        </p>
                        <p className="mt-2 text-sm font-semibold text-foreground">
                          {doctor.bookable === false
                            ? isEn
                              ? 'Temporarily unavailable online'
                              : 'Tạm thời chưa mở trực tuyến'
                            : isEn
                              ? 'Available from live slots'
                              : 'Có thể tiếp tục từ lịch trống'}
                        </p>
                      </div>
                    </div>

                    {!availabilityPreviewRequested ? (
                      <div className="mt-6 rounded-[1.5rem] border border-dashed border-border/70 bg-background/80 p-6 text-sm text-muted-foreground">
                        <p className="font-semibold text-foreground">
                          {isEn ? 'Availability preview is loaded on demand.' : 'Lịch trống sẽ chỉ tải khi bạn cần xem.'}
                        </p>
                        <p className="mt-2 leading-7">
                          {isEn
                            ? 'Use the quick lookup to check the nearest openings without making this profile page fire background availability requests.'
                            : 'Bấm tìm giờ gần nhất để kiểm tra nhanh khung trống, tránh để trang hồ sơ tự gọi nhiều request lịch nền.'}
                        </p>
                        <div className="mt-4 flex flex-col gap-3 sm:flex-row">
                          <Button
                            type="button"
                            className="rounded-2xl"
                            onClick={() => setAvailabilityPreviewRequested(true)}
                            disabled={schedulePreviewInputs.length === 0}
                          >
                            <Clock3 className="mr-2 h-4 w-4" />
                            {isEn ? 'Find nearest times' : 'Tìm giờ gần nhất'}
                          </Button>
                          <Button variant="outline" asChild className="rounded-2xl bg-background">
                            <Link to={availabilityPath}>
                              {isEn ? 'View full availability' : 'Xem toàn bộ lịch trống'}
                            </Link>
                          </Button>
                        </div>
                      </div>
                    ) : availabilityPreviewLoading && availabilityPreview.length === 0 ? (
                      <div className="mt-6 rounded-[1.5rem] border border-dashed border-border/70 bg-background/80 p-6 text-sm text-muted-foreground">
                        {isEn ? 'Loading available slots...' : 'Đang tải khung giờ trống...'}
                      </div>
                    ) : availabilityPreviewError && availabilityPreview.length === 0 ? (
                      <div className="mt-6 rounded-[1.5rem] border border-dashed border-destructive/30 bg-background/80 p-6 text-sm text-muted-foreground">
                        <p className="font-semibold text-foreground">
                          {isEn ? 'Unable to load available slots.' : 'Không thể tải khung giờ trống.'}
                        </p>
                        <p className="mt-2 leading-7">
                          {isEn ? 'Please try again or open the full availability page.' : 'Vui lòng thử lại hoặc mở trang lịch trống đầy đủ.'}
                        </p>
                      </div>
                    ) : availabilityPreview.length ? (
                      <div className="mt-6 space-y-4">
                        {availabilityPreview.map((day) => (
                          <div key={day.visitDate} className="rounded-[1.5rem] border border-border/70 bg-background/85 p-5 shadow-sm">
                            <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                              <div>
                                <p className="text-sm font-semibold text-foreground">
                                  {formatLongDate(day.visitDate, locale) || day.visitDate}
                                </p>
                                <p className="mt-1 text-sm text-muted-foreground">
                                  {isEn
                                    ? 'Currently available sessions and time slots'
                                    : 'Các buổi và khung giờ hiện đang còn trống'}
                                </p>
                              </div>

                              <Link
                                to={availabilityPath}
                                className="inline-flex items-center gap-1 text-sm font-medium text-primary transition-colors duration-200 hover:text-primary/80"
                              >
                                {isEn ? 'View full schedule' : 'Xem lịch chi tiết'}
                                <ChevronRight className="h-4 w-4" />
                              </Link>
                            </div>

                            <div className="mt-4 grid gap-4 lg:grid-cols-2">
                              {day.sessions.map((session) => (
                                <div key={`${day.visitDate}-${session.session}`} className="rounded-2xl bg-muted/20 p-4">
                                  <div className="mb-3 flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">
                                    <Clock3 className="h-3.5 w-3.5 text-primary" />
                                    {getSessionLabel(session.session, isEn)}
                                  </div>

                                  <div className="flex flex-wrap gap-2.5">
                                    {session.slots.slice(0, 6).map((slot) => (
                                      <Link
                                        key={slot.id}
                                        to={`/booking?branchId=${slot.branchId}&specialtyId=${slot.specialtyId}&doctorId=${slot.doctorId}&date=${slot.visitDate}&session=${slot.session}&slot=${slot.slotStart}`}
                                        className="inline-flex min-h-[44px] min-w-[106px] items-center justify-center rounded-full border border-border/70 bg-background px-4 py-2.5 text-sm font-medium text-foreground transition-colors duration-200 hover:border-primary/40 hover:bg-primary/5"
                                      >
                                        {slot.slotStart}
                                      </Link>
                                    ))}
                                  </div>
                                </div>
                              ))}
                            </div>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <div className="mt-6 rounded-[1.5rem] border border-dashed border-border/70 bg-background/80 p-6 text-sm text-muted-foreground">
                        <p className="font-semibold text-foreground">
                          {nextAvailableLong || (isEn ? 'No live slots available right now' : 'Hiện chưa có khung giờ trống trực tiếp')}
                        </p>
                        <p className="mt-2 leading-7">
                          {isEn
                            ? 'Live availability changes as bookings are confirmed. Review the full schedule page for the latest opening, or check back later.'
                            : 'Lịch trống thay đổi theo các lượt đặt mới. Hãy xem trang lịch đầy đủ để kiểm tra khung giờ mới nhất hoặc quay lại sau.'}
                        </p>
                      </div>
                    )}
                  </section>

                  <section className="rounded-[28px] border border-border/70 bg-background/95 p-6 shadow-card md:p-7">
                    <Tabs defaultValue="overview" className="w-full">
                      <TabsList className="h-auto flex-wrap justify-start rounded-[1.25rem] bg-muted/50 p-1.5">
                        <TabsTrigger value="overview" className="rounded-xl px-4 py-2.5">
                          {isEn ? 'Overview' : 'Tổng quan'}
                        </TabsTrigger>
                        <TabsTrigger value="expertise" className="rounded-xl px-4 py-2.5">
                          {isEn ? 'Expertise & services' : 'Chuyên môn & dịch vụ'}
                        </TabsTrigger>
                        <TabsTrigger value="experience" className="rounded-xl px-4 py-2.5">
                          {isEn ? 'Training & experience' : 'Đào tạo & kinh nghiệm'}
                        </TabsTrigger>
                      </TabsList>

                      <TabsContent value="overview" className="mt-6 space-y-6">
                        <div className="grid gap-6 lg:grid-cols-[1.05fr_0.95fr]">
                          <div className="rounded-[1.5rem] border border-border/70 bg-muted/20 p-5">
                            <div className="flex items-center gap-2 text-sm font-semibold text-primary">
                              <ShieldCheck className="h-4 w-4" />
                              {isEn ? 'What patients can confirm here' : 'Những gì bạn có thể xác nhận tại đây'}
                            </div>

                            <div className="mt-4 space-y-3 text-sm text-muted-foreground">
                              <div className="rounded-2xl bg-background px-4 py-3">
                                <p className="font-medium text-foreground">{isEn ? 'Specialty' : 'Chuyên khoa'}</p>
                                <p className="mt-1 leading-6">
                                  {doctor.specialtyName || (isEn ? 'Specialty information is being updated.' : 'Thông tin chuyên khoa đang được cập nhật.')}
                                </p>
                              </div>

                              <div className="rounded-2xl bg-background px-4 py-3">
                                <p className="font-medium text-foreground">{isEn ? 'Branch' : 'Cơ sở'}</p>
                                <p className="mt-1 leading-6">
                                  {doctor.branchName || (isEn ? 'Branch information is being updated.' : 'Thông tin cơ sở đang được cập nhật.')}
                                </p>
                              </div>

                              <div className="rounded-2xl bg-background px-4 py-3">
                                <p className="font-medium text-foreground">{isEn ? 'Supported languages' : 'Ngôn ngữ hỗ trợ'}</p>
                                <p className="mt-1 leading-6">
                                  {languages.length
                                    ? languages.join(' • ')
                                    : isEn
                                      ? 'Supported language information is being updated.'
                                      : 'Thông tin ngôn ngữ hỗ trợ đang được cập nhật.'}
                                </p>
                              </div>

                              <div className="rounded-2xl bg-background px-4 py-3">
                                <p className="font-medium text-foreground">{isEn ? 'Public reviews' : 'Đánh giá công khai'}</p>
                                <p className="mt-1 leading-6">
                                  {hasPublicReviews
                                    ? isEn
                                      ? `${ratingValue}/5 from ${reviewCount} public review(s).`
                                      : `${ratingValue}/5 từ ${reviewCount} lượt đánh giá công khai.`
                                    : isEn
                                      ? 'Public review data is being updated for this profile.'
                                      : 'Dữ liệu đánh giá công khai của hồ sơ này đang được cập nhật.'}
                                </p>
                              </div>

                              <div className="rounded-2xl bg-background px-4 py-3">
                                <p className="font-medium text-foreground">{isEn ? 'Booking status' : 'Tình trạng đặt lịch'}</p>
                                <p className="mt-1 leading-6">{bookingStatusText}</p>
                              </div>
                            </div>
                          </div>

                          <div className="rounded-[1.5rem] border border-border/70 bg-muted/20 p-5">
                            <div className="flex items-center gap-2 text-sm font-semibold text-primary">
                              <GraduationCap className="h-4 w-4" />
                              {isEn ? 'Training & credentials' : 'Đào tạo & nền tảng chuyên môn'}
                            </div>

                            {credentialHighlights.length ? (
                              <div className="mt-4 space-y-3">
                                {credentialHighlights.map((item, index) => (
                                  <div key={`${item}-${index}`} className="rounded-2xl bg-background px-4 py-3 text-sm leading-6 text-foreground">
                                    {item}
                                  </div>
                                ))}
                              </div>
                            ) : (
                              <p className="mt-4 text-sm leading-7 text-muted-foreground">
                                {isEn
                                  ? 'Training and credential details are being completed. You can still use the branch, specialty, and schedule information above.'
                                  : 'Thông tin đào tạo và nền tảng chuyên môn đang được hoàn thiện thêm. Bạn vẫn có thể dùng các thông tin về cơ sở, chuyên khoa và lịch trống ở phía trên.'}
                              </p>
                            )}
                          </div>
                        </div>
                      </TabsContent>

                      <TabsContent value="expertise" className="mt-6 space-y-6">
                        <div className="grid gap-6 lg:grid-cols-2">
                          <div className="rounded-[1.5rem] border border-border/70 bg-muted/20 p-5">
                            <div className="flex items-center gap-2 text-sm font-semibold text-primary">
                              <Stethoscope className="h-4 w-4" />
                              {isEn ? 'Clinical focus' : 'Chuyên môn nổi bật'}
                            </div>

                            {expertiseItems.length ? (
                              <div className="mt-4 flex flex-wrap gap-2.5">
                                {expertiseItems.map((item) => (
                                  <span key={item} className="rounded-full bg-background px-3.5 py-2 text-sm font-medium text-foreground shadow-sm">
                                    {item}
                                  </span>
                                ))}
                              </div>
                            ) : (
                              <p className="mt-4 text-sm leading-7 text-muted-foreground">
                                {doctor.specialtyName
                                  ? isEn
                                    ? `This profile is currently published under ${doctor.specialtyName}. More detailed clinical focus will appear here when the public profile is expanded.`
                                    : `Hồ sơ này hiện đang công khai dưới chuyên khoa ${doctor.specialtyName}. Phần mô tả chuyên môn chi tiết sẽ xuất hiện tại đây khi hồ sơ được bổ sung thêm.`
                                  : isEn
                                    ? 'Detailed clinical focus is being updated.'
                                    : 'Đang cập nhật thêm thông tin chuyên môn chi tiết.'}
                              </p>
                            )}
                          </div>

                          <div className="rounded-[1.5rem] border border-border/70 bg-muted/20 p-5">
                            <div className="flex items-center gap-2 text-sm font-semibold text-primary">
                              <BadgeCheck className="h-4 w-4" />
                              {isEn ? 'Common services and visit types' : 'Dịch vụ và nhu cầu thường gặp'}
                            </div>

                            {featuredServices.length ? (
                              <div className="mt-4 space-y-3">
                                {featuredServices.map((item) => (
                                  <div key={item} className="rounded-2xl bg-background px-4 py-3 text-sm leading-6 text-foreground">
                                    {item}
                                  </div>
                                ))}
                              </div>
                            ) : (
                              <div className="mt-4 rounded-2xl border border-dashed border-border/70 bg-background/80 p-4 text-sm leading-6 text-muted-foreground">
                                {doctor.specialtyName
                                  ? isEn
                                    ? `Service details for ${doctor.specialtyName} are still being prepared. Use the booking and schedule area above to continue with this doctor.`
                                    : `Chi tiết dịch vụ cho chuyên khoa ${doctor.specialtyName} đang được chuẩn bị thêm. Bạn có thể dùng phần đặt lịch và lịch trống ở phía trên để tiếp tục với bác sĩ này.`
                                  : isEn
                                    ? 'Featured service details are still being prepared.'
                                    : 'Chi tiết dịch vụ tiêu biểu đang được chuẩn bị thêm.'}
                              </div>
                            )}
                          </div>
                        </div>
                      </TabsContent>

                      <TabsContent value="experience" className="mt-6 space-y-6">
                        <div className="grid gap-6 lg:grid-cols-[1.1fr_0.9fr]">
                          <div className="rounded-[1.5rem] border border-border/70 bg-muted/20 p-5">
                            <div className="flex items-center gap-2 text-sm font-semibold text-primary">
                              <GraduationCap className="h-4 w-4" />
                              {isEn ? 'Career timeline' : 'Lịch công tác & kinh nghiệm'}
                            </div>

                            {timelineItems.length ? (
                              <div className="mt-5 space-y-5">
                                {timelineItems.map((item, index) => (
                                  <div key={`${item.title}-${index}`} className="relative pl-8">
                                    <span className="absolute left-0 top-1.5 h-3 w-3 rounded-full bg-primary" />
                                    {index < timelineItems.length - 1 ? (
                                      <span className="absolute left-[5px] top-5 h-[calc(100%-0.25rem)] w-px bg-border" />
                                    ) : null}

                                    {item.period ? (
                                      <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">
                                        {item.period}
                                      </p>
                                    ) : null}
                                    <p className="mt-1 text-base font-semibold text-foreground">{item.title}</p>
                                    {item.organization ? (
                                      <p className="mt-1 text-sm text-muted-foreground">{item.organization}</p>
                                    ) : null}
                                    {item.description ? (
                                      <p className="mt-2 text-sm leading-7 text-muted-foreground">{item.description}</p>
                                    ) : null}
                                  </div>
                                ))}
                              </div>
                            ) : (
                              <div className="mt-4 rounded-2xl border border-dashed border-border/70 bg-background/80 p-4 text-sm leading-6 text-muted-foreground">
                                {isEn
                                  ? 'Career timeline data has not been standardized yet. This section will become more specific as the public profile is completed.'
                                  : 'Dữ liệu lịch công tác chưa được chuẩn hóa. Phần này sẽ cụ thể hơn khi hồ sơ công khai được hoàn thiện thêm.'}
                              </div>
                            )}
                          </div>

                          <div className="space-y-6">
                            {showSeparateEducationList ? (
                              <div className="rounded-[1.5rem] border border-border/70 bg-muted/20 p-5">
                                <div className="flex items-center gap-2 text-sm font-semibold text-primary">
                                  <GraduationCap className="h-4 w-4" />
                                  {isEn ? 'Education & training' : 'Đào tạo & chứng chỉ'}
                                </div>

                                <div className="mt-4 space-y-3">
                                  {educationHighlights.map((item, index) => (
                                    <div key={`${item}-${index}`} className="rounded-2xl bg-background px-4 py-3 text-sm leading-6 text-foreground">
                                      {item}
                                    </div>
                                  ))}
                                </div>
                              </div>
                            ) : null}

                            <div className="rounded-[1.5rem] border border-border/70 bg-muted/20 p-5">
                              <div className="flex items-center gap-2 text-sm font-semibold text-primary">
                                <BadgeCheck className="h-4 w-4" />
                                {isEn ? 'Achievements & recognition' : 'Thành tựu & ghi nhận'}
                              </div>

                              {achievementItems.length ? (
                                <div className="mt-4 space-y-3">
                                  {achievementItems.map((item, index) => (
                                    <div key={`${item}-${index}`} className="rounded-2xl bg-background px-4 py-3 text-sm leading-6 text-foreground">
                                      {item}
                                    </div>
                                  ))}
                                </div>
                              ) : (
                                <p className="mt-4 text-sm leading-7 text-muted-foreground">
                                  {isEn
                                    ? 'Public achievement details are still being updated.'
                                    : 'Thông tin thành tựu công khai đang được cập nhật thêm.'}
                                </p>
                              )}
                            </div>
                          </div>
                        </div>
                      </TabsContent>
                    </Tabs>
                  </section>
                </div>

                <aside className="space-y-4 xl:sticky xl:top-28">
                  <div className="rounded-[30px] border border-primary/10 bg-primary/[0.06] p-5 shadow-card">
                    <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">
                      {isEn ? 'Next step' : 'Bước tiếp theo'}
                    </p>
                    <h2 className="mt-2 text-2xl font-semibold tracking-tight text-foreground">
                      {isEn ? `Continue to booking for ${doctor.fullName}` : `Tiếp tục đặt lịch với ${doctor.fullName}`}
                    </h2>
                    <p className="mt-3 text-sm leading-7 text-muted-foreground">
                      {isEn
                        ? 'Open the booking flow if you are ready to confirm the branch, date, and time slot for this doctor. Use the full schedule page if you want to browse more availability first.'
                        : 'Mở luồng đặt lịch khi bạn đã sẵn sàng xác nhận cơ sở, ngày khám và khung giờ cho bác sĩ này. Dùng trang lịch đầy đủ nếu bạn muốn xem thêm nhiều khung giờ trước.'}
                    </p>

                    <div className="mt-5 rounded-[1.5rem] border border-primary/10 bg-background/90 p-4">
                      <div className="space-y-3 text-sm text-muted-foreground">
                        <div className="flex items-start gap-3">
                          <Calendar className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
                          <div>
                            <p className="font-medium text-foreground">
                              {nextAvailableLong || (isEn ? 'Live schedule is being refreshed' : 'Lịch trống trực tuyến đang được cập nhật')}
                            </p>
                            <p className="mt-1 leading-6">
                              {isEn ? 'Nearest published opening' : 'Khung lịch gần nhất đang được công khai'}
                            </p>
                          </div>
                        </div>

                        <div className="flex items-start gap-3">
                          <Building2 className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
                          <div>
                            <p className="font-medium text-foreground">
                              {doctor.branchName || (isEn ? 'Branch is being updated' : 'Đang cập nhật cơ sở')}
                            </p>
                            <p className="mt-1 leading-6">
                              {isEn ? 'Branch shown in the current public profile' : 'Cơ sở đang hiển thị trong hồ sơ công khai này'}
                            </p>
                          </div>
                        </div>

                        <div className="flex items-start gap-3">
                          <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
                          <div>
                            <p className="font-medium text-foreground">
                              {doctor.bookable === false
                                ? isEn
                                  ? 'Booking status updating'
                                  : 'Trạng thái đặt lịch đang cập nhật'
                                : isEn
                                  ? 'Live slot booking available'
                                  : 'Có thể tiếp tục từ lịch trống'}
                            </p>
                            <p className="mt-1 leading-6">{bookingStatusText}</p>
                          </div>
                        </div>
                      </div>
                    </div>

                    <Button asChild className="mt-5 h-12 w-full rounded-2xl shadow-[0_14px_30px_rgba(8,46,95,0.14)]">
                      <Link to={bookingPath}>
                        <Calendar className="mr-2 h-4 w-4" />
                        {isEn ? 'Open booking flow' : 'Mở luồng đặt lịch'}
                      </Link>
                    </Button>

                    <Button
                      variant="outline"
                      asChild
                      className="mt-3 h-12 w-full rounded-2xl border-border/70 bg-background/80"
                    >
                      <Link to={availabilityPath}>
                        {isEn ? 'View full availability' : 'Xem toàn bộ lịch trống'}
                      </Link>
                    </Button>
                  </div>

                  <div className="rounded-[30px] border border-border/70 bg-background/95 p-5 shadow-sm">
                    <div className="grid grid-cols-2 gap-3">
                      <div className="rounded-2xl bg-muted/20 p-4">
                        <p className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                          {isEn ? 'Experience' : 'Kinh nghiệm'}
                        </p>
                        <p className="mt-2 text-xl font-semibold text-foreground">
                          {doctor.yearsOfExperience || '—'}
                        </p>
                        <p className="mt-1 text-xs leading-5 text-muted-foreground">
                          {isEn ? 'Years in public profile' : 'Số năm hiển thị công khai'}
                        </p>
                      </div>

                      <div className="rounded-2xl bg-muted/20 p-4">
                        <p className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                          {isEn ? 'Rating' : 'Đánh giá'}
                        </p>
                        <div className="mt-2 flex items-center gap-2 text-foreground">
                          <Star className="h-4 w-4 fill-primary text-primary" />
                          <span className="text-xl font-semibold">{hasPublicReviews ? ratingValue : '—'}</span>
                        </div>
                        <p className="mt-1 text-xs leading-5 text-muted-foreground">
                          {hasPublicReviews
                            ? isEn
                              ? `${reviewCount} public review(s)`
                              : `${reviewCount} lượt đánh giá công khai`
                            : isEn
                              ? 'No public reviews yet'
                              : 'Chưa có đánh giá công khai'}
                        </p>
                      </div>
                    </div>

                    <div className="mt-4 rounded-[1.5rem] bg-muted/20 p-4">
                      <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
                        <Languages className="h-4 w-4 text-primary" />
                        {isEn ? 'Supported languages' : 'Ngôn ngữ hỗ trợ'}
                      </div>
                      <p className="mt-2 text-sm font-medium text-foreground">
                        {languages.length
                          ? languages.join(' • ')
                          : isEn
                            ? 'Language details are being updated'
                            : 'Đang cập nhật thông tin ngôn ngữ'}
                      </p>
                      <p className="mt-2 text-sm leading-6 text-muted-foreground">
                        {isEn
                          ? 'Useful if you want to confirm how the consultation can be conducted before the visit.'
                          : 'Hữu ích nếu bạn muốn xác nhận cách trao đổi trong buổi khám trước khi đến cơ sở.'}
                      </p>
                    </div>
                  </div>
                </aside>
              </div>
            </div>
          </div>
        </ScrollReveal>
      </div>
    </div>
  );
}
