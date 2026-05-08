import { Link, useLocation } from 'react-router-dom';
import { CheckCircle2, Home, Calendar } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { ScrollReveal } from '@/components/ScrollReveal';
import type { Appointment } from '@/types/api';
import { useTranslation } from 'react-i18next';

type SuccessState = {
  appointment?: Appointment;
  branchName?: string;
  specialtyName?: string;
  doctorName?: string;
  slotEnd?: string;
};

export default function BookingSuccessPage() {
  const location = useLocation();
  const { i18n } = useTranslation();
  const isEn = i18n.language.startsWith('en');
  const state = (location.state as SuccessState | null) ?? null;
  const appointment = state?.appointment;
  const statusLabel = isEn ? 'Pending confirmation' : 'Chờ xác nhận';

  return (
    <div className="section-padding">
      <div className="container-wide max-w-2xl text-center">
        <ScrollReveal>
          <div className="bg-card rounded-card shadow-card p-10">
            <div className="mx-auto w-16 h-16 rounded-full bg-success/10 flex items-center justify-center mb-6">
              <CheckCircle2 className="h-8 w-8 text-success" />
            </div>
            <h1 className="text-2xl font-semibold text-foreground">{isEn ? 'Request received' : 'Yêu cầu đặt lịch đã được ghi nhận'}</h1>
            <p className="text-muted-foreground mt-3 leading-relaxed">
              {isEn ? 'Your appointment request has been received. The clinic will review it, confirm the slot, and send reminders by SMS to the registered phone number.' : 'Yêu cầu đặt lịch của bạn đã được ghi nhận. Phòng khám sẽ duyệt lịch, xác nhận khung giờ và gửi nhắc lịch qua SMS đến số điện thoại đã đăng ký.'}
            </p>

            {appointment ? (
              <div className="mt-8 text-left rounded-xl border bg-muted/30 p-5 space-y-3 text-sm">
                <div><span className="text-muted-foreground">{isEn ? 'Appointment code:' : 'Mã lịch:'}</span> <span className="font-semibold text-foreground">{appointment.code}</span></div>
                <div><span className="text-muted-foreground">{isEn ? 'Patient:' : 'Bệnh nhân:'}</span> <span className="font-medium text-foreground">{appointment.patientFullName}</span></div>
                <div><span className="text-muted-foreground">{isEn ? 'Branch:' : 'Cơ sở:'}</span> <span className="font-medium text-foreground">{state?.branchName}</span></div>
                <div><span className="text-muted-foreground">{isEn ? 'Specialty:' : 'Chuyên khoa:'}</span> <span className="font-medium text-foreground">{state?.specialtyName}</span></div>
                <div><span className="text-muted-foreground">{isEn ? 'Doctor:' : 'Bác sĩ:'}</span> <span className="font-medium text-foreground">{state?.doctorName ?? appointment.doctorName}</span></div>
                <div><span className="text-muted-foreground">{isEn ? 'Visit date:' : 'Ngày khám:'}</span> <span className="font-medium text-foreground">{appointment.visitDate}</span></div>
                <div><span className="text-muted-foreground">{isEn ? 'Time:' : 'Giờ khám:'}</span> <span className="font-medium text-foreground">{appointment.slotStart}{state?.slotEnd ? ` - ${state.slotEnd}` : appointment.slotEnd ? ` - ${appointment.slotEnd}` : ''}</span></div>
                <div><span className="text-muted-foreground">{isEn ? 'Status:' : 'Trạng thái:'}</span> <span className="font-medium text-foreground">{statusLabel}</span></div>
                <div><span className="text-muted-foreground">{isEn ? 'Reminder channel:' : 'Kênh nhắc lịch:'}</span> <span className="font-medium text-foreground">{isEn ? 'SMS to registered phone number' : 'SMS tới số điện thoại đã đăng ký'}</span></div>
              </div>
            ) : null}

            <div className="flex gap-3 justify-center mt-8">
              <Button variant="outline" asChild>
                <Link to="/"><Home className="h-4 w-4 mr-2" /> {isEn ? 'Back to home' : 'Trang chủ'}</Link>
              </Button>
              <Button asChild>
                <Link to="/booking"><Calendar className="h-4 w-4 mr-2" /> {isEn ? 'Book another visit' : 'Đặt lịch mới'}</Link>
              </Button>
            </div>
          </div>
        </ScrollReveal>
      </div>
    </div>
  );
}
