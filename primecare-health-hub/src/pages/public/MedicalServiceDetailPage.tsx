import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, Calendar, Clock3, ReceiptText, Activity } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { useTranslation } from 'react-i18next';
import { useMedicalService } from '@/hooks/use-public-data';

export default function MedicalServiceDetailPage() {
  const { id = '' } = useParams();
  const { i18n } = useTranslation();
  const isEn = i18n.language.startsWith('en');
  const { data: service, isLoading } = useMedicalService(id);

  if (isLoading) {
    return (
      <div className="min-h-[60vh] flex flex-col items-center justify-center section-padding relative overflow-hidden bg-gradient-to-b from-background via-primary/5 to-background">
        <div className="h-12 w-12 rounded-full border-4 border-primary/20 border-t-primary animate-spin mb-4"></div>
        <div className="text-lg font-medium text-muted-foreground">{isEn ? 'Loading service detail...' : 'Đang tải chi tiết dịch vụ...'}</div>
      </div>
    );
  }

  if (!service) {
    return (
      <div className="min-h-[60vh] flex flex-col items-center justify-center section-padding relative overflow-hidden bg-gradient-to-b from-background via-primary/5 to-background">
        <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-primary/10 text-primary">
          <Activity className="h-8 w-8" />
        </div>
        <div className="text-xl font-medium text-foreground">{isEn ? 'Medical service not found.' : 'Không tìm thấy dịch vụ.'}</div>
        <Button variant="outline" asChild className="mt-6 rounded-xl">
          <Link to="/medical-services">{isEn ? 'View all services' : 'Xem tất cả dịch vụ'}</Link>
        </Button>
      </div>
    );
  }

  return (
    <div className="section-padding relative min-h-[calc(100vh-5rem)] overflow-hidden bg-gradient-to-b from-background via-primary/5 to-background">
      {/* Decorative background elements */}
      <div className="absolute left-0 top-0 -translate-x-1/2 translate-y-1/4 h-[800px] w-[800px] rounded-full bg-primary/5 blur-[100px] pointer-events-none"></div>
      <div className="absolute right-0 bottom-0 translate-x-1/3 translate-y-1/3 h-[600px] w-[600px] rounded-full bg-primary/10 blur-[80px] pointer-events-none"></div>

      <div className="container-wide max-w-4xl space-y-6 relative z-10 py-10">
        <Button variant="ghost" asChild className="mb-4 h-auto rounded-xl bg-card/60 px-4 py-2 backdrop-blur-sm transition-all hover:bg-card/80">
          <Link to="/medical-services"><ArrowLeft className="mr-2 h-4 w-4" />{isEn ? 'Back to services' : 'Quay lại danh sách dịch vụ'}</Link>
        </Button>

        <Card className="relative overflow-hidden rounded-[2.5rem] border-0 bg-card/70 shadow-[0_8px_30px_rgb(0,0,0,0.04)] backdrop-blur-xl">
          <div className="absolute top-0 right-0 h-40 w-40 bg-gradient-to-br from-primary/20 to-primary/0 rounded-full blur-2xl -translate-y-1/2 translate-x-1/2"></div>
          
          <CardContent className="space-y-10 p-8 md:p-12 relative z-10">
            <div className="space-y-4">
              <div className="inline-flex items-center gap-2 rounded-full bg-primary/10 px-4 py-1.5 text-sm font-bold uppercase tracking-wider text-primary">
                <Activity className="h-4 w-4" />
                {service.groupName || (isEn ? 'Medical service' : 'Dịch vụ y tế')}
              </div>
              <h1 className="text-4xl md:text-5xl font-extrabold text-foreground tracking-tight bg-clip-text text-transparent bg-gradient-to-r from-foreground to-foreground/70">{service.name}</h1>
              <p className="text-lg text-muted-foreground leading-relaxed max-w-2xl">{service.description || (isEn ? 'Service details are being updated.' : 'Chi tiết dịch vụ đang được cập nhật.')}</p>
            </div>

            <div className="grid gap-6 md:grid-cols-3">
              <div className="rounded-3xl border border-primary/10 bg-card/70 p-6 shadow-sm backdrop-blur-md transition-all hover:-translate-y-1 hover:shadow-md">
                <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-primary/10 text-primary">
                  <ReceiptText className="h-6 w-6" />
                </div>
                <div className="mb-1 text-sm font-medium text-muted-foreground">{isEn ? 'Base price' : 'Giá cơ sở'}</div>
                <div className="text-2xl font-bold text-foreground">
                  {service.price
                    ? new Intl.NumberFormat(isEn ? 'en-US' : 'vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(service.price)
                    : isEn ? 'Contact branch' : 'Liên hệ cơ sở'}
                </div>
              </div>
              
              <div className="rounded-3xl border border-primary/10 bg-card/70 p-6 shadow-sm backdrop-blur-md transition-all hover:-translate-y-1 hover:shadow-md">
                <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-primary/10 text-primary">
                  <Clock3 className="h-6 w-6" />
                </div>
                <div className="mb-1 text-sm font-medium text-muted-foreground">{isEn ? 'Estimated turnaround' : 'Thời gian trả kết quả'}</div>
                <div className="text-2xl font-bold text-foreground">{service.defaultTurnaroundMinutes ? `${service.defaultTurnaroundMinutes} ${isEn ? 'minutes' : 'phút'}` : (isEn ? 'Depending on workflow' : 'Phụ thuộc quy trình')}</div>
              </div>
              
              <div className="rounded-3xl border border-primary/10 bg-card/70 p-6 shadow-sm backdrop-blur-md transition-all hover:-translate-y-1 hover:shadow-md">
                <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-primary/10 text-primary">
                  <Calendar className="h-6 w-6" />
                </div>
                <div className="mb-1 text-sm font-medium text-muted-foreground">{isEn ? 'Next step' : 'Bước tiếp theo'}</div>
                <div className="text-2xl font-bold text-foreground">{isEn ? 'Book or ask for guidance' : 'Đặt lịch hoặc hỏi thêm'}</div>
              </div>
            </div>

            <div className="flex flex-wrap gap-4 pt-4 border-t border-border/50">
              <Button size="lg" asChild className="rounded-xl shadow-lg shadow-primary/20 hover:shadow-primary/40 transition-all hover:-translate-y-0.5"><Link to="/booking">{isEn ? 'Book now' : 'Đặt lịch ngay'}</Link></Button>
              <Button size="lg" asChild variant="outline" className="rounded-xl border-primary/20 hover:bg-primary/5 transition-colors"><Link to="/contact">{isEn ? 'Contact PrimeCare' : 'Liên hệ PrimeCare'}</Link></Button>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
