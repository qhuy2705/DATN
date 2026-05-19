import { Link } from 'react-router-dom';
import { CalendarClock, ClipboardList, FileSearch, HeartPulse, Pill, Receipt, ShieldCheck, UserRound } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Progress } from '@/components/ui/progress';
import { usePatientOverview } from '@/hooks/use-patient-portal';

function formatDateTime(value?: string) {
  if (!value) return 'Chưa có dữ liệu';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}

function formatDate(value?: string) {
  if (!value) return 'Đang cập nhật';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit',
    month: 'long',
    year: 'numeric',
  }).format(date);
}

function formatCurrency(value?: number) {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(value || 0);
}

export default function PatientOverviewPage() {
  const { data, isLoading } = usePatientOverview();

  const stats = [
    { label: 'Lịch hẹn sắp tới', value: data?.upcomingAppointments ?? 0, href: '/me/appointments', icon: CalendarClock },
    { label: 'Tổng lần đặt lịch', value: data?.totalAppointments ?? 0, href: '/me/appointments', icon: ClipboardList },
    { label: 'Kết quả đã sẵn sàng', value: data?.availableResults ?? 0, href: '/me/results', icon: FileSearch },
    { label: 'Hóa đơn đã phát sinh', value: data?.totalInvoices ?? 0, href: '/me/invoices', icon: Receipt },
  ];

  const quickLinks = [
    { label: 'Cập nhật hồ sơ cá nhân', href: '/me/profile', icon: UserRound },
    { label: 'Xem lịch sử đặt lịch', href: '/me/appointments', icon: CalendarClock },
    { label: 'Xem kết quả xét nghiệm', href: '/me/results', icon: FileSearch },
    { label: 'Tra cứu phiếu hẹn nhanh', href: '/appointments/lookup', icon: ClipboardList },
  ];

  const latestResult = data?.recentResults?.[0];
  const latestInvoice = data?.recentInvoices?.[0];

  return (
    <div className="space-y-6">
      <section className="grid gap-4 xl:grid-cols-[1.5fr_0.9fr]">
        <Card className="overflow-hidden border-0 bg-slate-950 text-white shadow-xl">
          <CardContent className="relative p-6 md:p-8">
            <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_right,_rgba(59,130,246,0.35),_transparent_35%),radial-gradient(circle_at_bottom_left,_rgba(245,158,11,0.25),_transparent_30%)]" />
            <div className="relative space-y-6">
              <div className="inline-flex items-center rounded-full border border-white/10 bg-foreground/5 px-3 py-1 text-xs font-medium text-sky-200">
                Hồ sơ cá nhân PrimeCare
              </div>
              <div>
                <h1 className="text-3xl font-semibold tracking-tight md:text-4xl">Tóm tắt hoạt động y tế</h1>
                <p className="mt-3 max-w-2xl text-sm leading-6 text-slate-300 md:text-base">
                  {isLoading
                    ? 'PrimeCare đang tổng hợp hồ sơ, lịch hẹn và kết quả gần đây của bạn.'
                    : `Xin chào ${data?.fullName || 'bạn'}. Đây là không gian cá nhân giúp bạn theo dõi hành trình khám chữa thuận tiện hơn ngay trong website PrimeCare.`}
                </p>
              </div>
              <div className="grid gap-3 sm:grid-cols-2">
                {quickLinks.map((item) => {
                  const Icon = item.icon;
                  return (
                    <Button key={item.href} asChild variant="secondary" className="justify-start rounded-2xl border border-white/10 bg-foreground/10 px-4 py-6 text-white hover:bg-foreground/20">
                      <Link to={item.href}>
                        <Icon className="mr-2 h-4 w-4" />
                        {item.label}
                      </Link>
                    </Button>
                  );
                })}
              </div>
            </div>
          </CardContent>
        </Card>

        <Card className="rounded-[24px] border border-border/70 bg-background/90 shadow-sm">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <ShieldCheck className="h-4 w-4 text-primary" />
              Mức độ hoàn thiện hồ sơ
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div>
              <div className="mb-2 flex items-center justify-between text-sm">
                <span className="text-muted-foreground">Thông tin cá nhân</span>
                <span className="font-medium text-foreground">{data?.profileCompletionPercent ?? 0}%</span>
              </div>
              <Progress value={data?.profileCompletionPercent ?? 0} className="h-2.5" />
            </div>
            <div className="rounded-2xl border bg-muted/30 p-4 text-sm text-muted-foreground">
              <p className="font-medium text-foreground">Mẹo sử dụng</p>
              <p className="mt-2">Hoàn thiện hồ sơ để việc đặt lịch sau này nhanh hơn và để PrimeCare gửi nhắc lịch, kết quả chính xác hơn.</p>
            </div>
            <Button asChild className="w-full rounded-full">
              <Link to="/me/profile">Mở hồ sơ cá nhân</Link>
            </Button>
          </CardContent>
        </Card>
      </section>

      <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {stats.map((item) => {
          const Icon = item.icon;
          return (
            <Link key={item.label} to={item.href}>
              <Card className="h-full rounded-[24px] border border-border/70 bg-background/90 shadow-sm transition hover:-translate-y-0.5 hover:shadow-md">
                <CardContent className="flex items-start justify-between p-5">
                  <div>
                    <p className="text-sm text-muted-foreground">{item.label}</p>
                    <p className="mt-2 text-3xl font-semibold text-foreground">{item.value}</p>
                  </div>
                  <div className="rounded-2xl bg-primary/10 p-3 text-primary">
                    <Icon className="h-5 w-5" />
                  </div>
                </CardContent>
              </Card>
            </Link>
          );
        })}
      </section>

      <section className="grid gap-4 xl:grid-cols-[1.45fr_0.95fr]">
        <Card className="rounded-[24px] border border-border/70 bg-background/90 shadow-sm">
          <CardHeader className="flex flex-row items-start justify-between gap-4">
            <div>
              <CardTitle>Lịch hẹn sắp tới</CardTitle>
              <p className="mt-1 text-sm text-muted-foreground">Lịch gần nhất của bạn sẽ luôn được hiển thị ở đây để dễ theo dõi.</p>
            </div>
            <Button asChild variant="outline" className="rounded-full">
              <Link to="/me/appointments">Quản lý lịch hẹn</Link>
            </Button>
          </CardHeader>
          <CardContent>
            {data?.nextAppointment ? (
              <div className="grid gap-4 rounded-[24px] bg-slate-950 p-5 text-white md:grid-cols-[1.4fr_0.8fr]">
                <div className="space-y-4">
                  <div>
                    <div className="mb-2 inline-flex items-center gap-2 rounded-full border border-white/10 bg-foreground/5 px-3 py-1 text-xs font-medium text-sky-200">
                      <CalendarClock className="h-3.5 w-3.5" />
                      LỊCH HẸN SẮP TỚI
                    </div>
                    <h2 className="text-2xl font-semibold">{data.nextAppointment.specialtyName || 'Khám tổng quát'}</h2>
                    <p className="mt-1 text-sm text-slate-300">{data.nextAppointment.doctorName || 'Bác sĩ đang cập nhật'} · {data.nextAppointment.branchName || 'Cơ sở đang cập nhật'}</p>
                  </div>
                  <div className="grid gap-3 md:grid-cols-2">
                    <div className="rounded-2xl bg-foreground/5 p-4">
                      <p className="text-xs uppercase tracking-[0.16em] text-slate-400">Ngày khám</p>
                      <p className="mt-2 font-medium">{formatDate(data.nextAppointment.visitDate)}</p>
                    </div>
                    <div className="rounded-2xl bg-foreground/5 p-4">
                      <p className="text-xs uppercase tracking-[0.16em] text-slate-400">Khung giờ</p>
                      <p className="mt-2 font-medium">{data.nextAppointment.etaStart || data.nextAppointment.session || 'Đang cập nhật'}</p>
                    </div>
                  </div>
                </div>
                <div className="space-y-3 rounded-[24px] bg-foreground/5 p-4">
                  <div>
                    <p className="text-sm text-slate-400">Mã phiếu hẹn</p>
                    <p className="mt-1 font-semibold">{data.nextAppointment.code}</p>
                  </div>
                  <div>
                    <p className="text-sm text-slate-400">Trạng thái</p>
                    <Badge variant="secondary" className="mt-2 bg-foreground/10 text-white hover:bg-foreground/10">{data.nextAppointment.status || 'Đang xử lý'}</Badge>
                  </div>
                  <Button asChild className="mt-4 w-full rounded-full bg-primary text-primary-foreground hover:bg-primary/90">
                    <Link to="/me/appointments">Xem lịch sử đặt lịch</Link>
                  </Button>
                </div>
              </div>
            ) : (
              <div className="rounded-[24px] border border-dashed p-6 text-sm text-muted-foreground">
                Bạn chưa có lịch hẹn sắp tới. Khi đặt lịch thành công, PrimeCare sẽ hiển thị lịch gần nhất của bạn ở đây.
              </div>
            )}
          </CardContent>
        </Card>

        <div className="grid gap-4">
          <Card className="rounded-[24px] border border-border/70 bg-background/90 shadow-sm">
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-base"><HeartPulse className="h-4 w-4 text-sky-500" /> Chỉ số gần nhất</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3 text-sm">
              <div className="rounded-2xl border bg-muted/30 p-4">
                <p className="font-medium text-foreground">Chưa có dữ liệu sinh tồn</p>
                <p className="mt-1 text-muted-foreground">PrimeCare sẽ bổ sung khi hồ sơ khám có ghi nhận chỉ số huyết áp, nhịp tim hoặc dấu hiệu sinh tồn.</p>
                <p className="mt-3 text-xs text-muted-foreground">Đồng bộ lúc {formatDateTime(data?.summaryGeneratedAt)}</p>
              </div>
            </CardContent>
          </Card>

          <Card className="rounded-[24px] border border-border/70 bg-background/90 shadow-sm">
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-base"><Pill className="h-4 w-4 text-indigo-500" /> Điều trị hiện tại</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3 text-sm">
              <div className="rounded-2xl border bg-muted/30 p-4">
                <p className="font-medium text-foreground">Chưa có đơn thuốc đang theo dõi</p>
                <p className="mt-1 text-muted-foreground">Khi hệ thống có kê đơn hoặc ghi nhận điều trị, phần này sẽ hiển thị ngay trên hồ sơ của bạn.</p>
                <p className="mt-3 text-xs text-muted-foreground">Đồng bộ lúc {formatDateTime(data?.summaryGeneratedAt)}</p>
              </div>
            </CardContent>
          </Card>
        </div>
      </section>

      <section className="grid gap-4 lg:grid-cols-3">
        <Card className="rounded-[24px] border border-border/70 bg-background/90 shadow-sm">
          <CardHeader className="flex flex-row items-center justify-between gap-4">
            <CardTitle className="text-base">Kết quả mới nhất</CardTitle>
            <Button asChild variant="ghost" className="rounded-full px-2 text-primary">
              <Link to="/me/results">Xem tất cả</Link>
            </Button>
          </CardHeader>
          <CardContent>
            {latestResult ? (
              <div className="space-y-3 rounded-2xl border p-4">
                <div>
                  <p className="font-medium text-foreground">{latestResult.serviceName || 'Kết quả cận lâm sàng'}</p>
                  <p className="mt-1 text-sm text-muted-foreground">Mã lần khám: {latestResult.encounterCode || 'Đang cập nhật'}</p>
                </div>
                <div className="flex items-center justify-between gap-3 text-sm">
                  <Badge variant={latestResult.pdfReady ? 'default' : 'secondary'}>{latestResult.pdfReady ? 'Sẵn sàng' : 'Đang xử lý'}</Badge>
                  <span className="text-muted-foreground">{formatDateTime(latestResult.verifiedAt || latestResult.performedAt)}</span>
                </div>
              </div>
            ) : (
              <div className="rounded-2xl border border-dashed p-4 text-sm text-muted-foreground">Chưa có kết quả nào sẵn sàng để xem.</div>
            )}
          </CardContent>
        </Card>

        <Card className="rounded-[24px] border border-border/70 bg-background/90 shadow-sm">
          <CardHeader className="flex flex-row items-center justify-between gap-4">
            <CardTitle className="text-base">Hóa đơn gần đây</CardTitle>
            <Button asChild variant="ghost" className="rounded-full px-2 text-primary">
              <Link to="/me/invoices">Xem tất cả</Link>
            </Button>
          </CardHeader>
          <CardContent>
            {latestInvoice ? (
              <div className="space-y-3 rounded-2xl border p-4">
                <div>
                  <p className="font-medium text-foreground">{latestInvoice.code}</p>
                  <p className="mt-1 text-sm text-muted-foreground">{latestInvoice.serviceOrderCode || 'Đơn dịch vụ đang cập nhật'}</p>
                </div>
                <div className="flex items-center justify-between gap-3 text-sm">
                  <Badge variant={latestInvoice.paymentStatus === 'PAID' ? 'default' : 'secondary'}>{latestInvoice.paymentStatus || 'Đang cập nhật'}</Badge>
                  <span className="font-medium text-foreground">{formatCurrency(latestInvoice.totalAmount)}</span>
                </div>
              </div>
            ) : (
              <div className="rounded-2xl border border-dashed p-4 text-sm text-muted-foreground">Chưa có hóa đơn nào phát sinh.</div>
            )}
          </CardContent>
        </Card>

        <Card className="rounded-[24px] border border-border/70 bg-background/90 shadow-sm">
          <CardHeader>
            <CardTitle className="text-base">Nhắc bạn hôm nay</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3 text-sm text-muted-foreground">
            <div className="rounded-2xl border bg-muted/30 p-4">
              <p className="font-medium text-foreground">Giữ hồ sơ luôn sẵn sàng</p>
              <p className="mt-1">Cập nhật số điện thoại, email và người liên hệ khẩn cấp để PrimeCare gửi nhắc lịch và hỗ trợ nhanh hơn khi cần.</p>
            </div>
            <div className="rounded-2xl border bg-muted/30 p-4">
              <p className="font-medium text-foreground">Cần xem nhanh tài liệu?</p>
              <p className="mt-1">Bạn vẫn có thể dùng tra cứu công khai nếu chỉ cần mở lại một phiếu hẹn hoặc một kết quả riêng lẻ.</p>
            </div>
          </CardContent>
        </Card>
      </section>
    </div>
  );
}
