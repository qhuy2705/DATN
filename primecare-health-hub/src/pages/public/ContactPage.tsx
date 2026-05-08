import { useState } from 'react';
import { toast } from 'sonner';

import { ScrollReveal } from '@/components/ScrollReveal';
import { SectionTitle } from '@/components/SectionTitle';
import { Phone, Mail, MapPin, Clock, MessageSquare, Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import apiClient from '@/lib/api-client';
import { getApiErrorMessage } from '@/lib/error-utils';
import type { ApiResponse, PublicContactRequest, PublicContactResponse } from '@/types/api';

export default function ContactPage() {
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [message, setMessage] = useState('');
  const [phone, setPhone] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [lastReference, setLastReference] = useState<string | null>(null);

  const resetForm = () => {
    setFullName('');
    setEmail('');
    setPhone('');
    setMessage('');
  };

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!fullName.trim() || !message.trim()) {
      toast.error('Vui lòng nhập họ tên và nội dung cần hỗ trợ.');
      return;
    }

    setSubmitting(true);
    try {
      const payload: PublicContactRequest = {
        fullName: fullName.trim(),
        email: email.trim() || undefined,
        phone: phone.trim() || undefined,
        message: message.trim(),
        sourcePage: '/contact',
      };
      const { data } = await apiClient.post<ApiResponse<PublicContactResponse>>('/public/contact', payload);
      const ref = data?.data?.referenceCode;
      setLastReference(ref || null);
      resetForm();
      toast.success(ref ? `PrimeCare đã nhận yêu cầu hỗ trợ. Mã tham chiếu: ${ref}` : 'PrimeCare đã nhận yêu cầu hỗ trợ của bạn.');
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'Không thể gửi yêu cầu hỗ trợ lúc này.'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="section-padding">
      <div className="container-wide max-w-5xl">
        <SectionTitle
          title="Liên hệ"
          subtitle="Ưu tiên hotline cho nhu cầu gấp. Biểu mẫu bên dưới sẽ gửi yêu cầu hỗ trợ trực tiếp vào hệ thống PrimeCare và tạo mã tham chiếu để bạn tiện theo dõi."
        />

        <div className="grid grid-cols-1 gap-10 lg:grid-cols-2">
          <ScrollReveal>
            <div className="space-y-6">
              {[
                { icon: Phone, title: 'Hotline đặt lịch', content: '1900 1234', sub: 'Ưu tiên hỗ trợ đổi lịch, xác nhận lịch và tư vấn nhanh' },
                { icon: MessageSquare, title: 'SMS nhắc lịch', content: 'Gửi trước giờ khám 24 giờ', sub: 'Áp dụng cho lịch đã được xác nhận và có số điện thoại hợp lệ' },
                { icon: Mail, title: 'Email hỗ trợ', content: 'info@primecare.vn', sub: 'Phù hợp cho góp ý, hồ sơ và các yêu cầu không khẩn cấp' },
                { icon: MapPin, title: 'Trụ sở chính', content: '88 Nguyễn Du, Quận 1, TP.HCM', sub: '' },
                { icon: Clock, title: 'Giờ làm việc', content: 'Thứ 2 - Thứ 7: 07:00 - 20:00', sub: 'Chủ nhật: 08:00 - 17:00' },
              ].map((item) => (
                <div key={item.title} className="flex items-start gap-4 rounded-card bg-card p-4 shadow-soft">
                  <div className="rounded-lg bg-primary/10 p-2.5">
                    <item.icon className="h-5 w-5 text-primary" />
                  </div>
                  <div>
                    <p className="font-medium text-foreground">{item.title}</p>
                    <p className="text-sm text-muted-foreground">{item.content}</p>
                    {item.sub ? <p className="mt-0.5 text-xs text-muted-foreground">{item.sub}</p> : null}
                  </div>
                </div>
              ))}
            </div>
          </ScrollReveal>

          <ScrollReveal delay={100}>
            <div className="rounded-card bg-card p-6 shadow-card">
              <h3 className="mb-2 text-lg font-semibold text-foreground">Gửi yêu cầu hỗ trợ</h3>
              <p className="mb-4 text-sm text-muted-foreground">
                Biểu mẫu này gửi trực tiếp yêu cầu hỗ trợ đến PrimeCare. Bạn sẽ nhận được một mã tham chiếu ngay sau khi gửi thành công.
              </p>
              {lastReference ? (
                <div className="mb-4 rounded-xl border border-primary/20 bg-primary/5 px-4 py-3 text-sm text-foreground">
                  Mã tham chiếu gần nhất: <span className="font-semibold text-primary">{lastReference}</span>
                </div>
              ) : null}
              <form className="space-y-4" onSubmit={handleSubmit}>
                <div>
                  <label className="mb-1.5 block text-sm font-medium text-foreground">Họ tên</label>
                  <Input placeholder="Nguyễn Văn A" value={fullName} onChange={(e) => setFullName(e.target.value)} disabled={submitting} />
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-medium text-foreground">Số điện thoại</label>
                  <Input placeholder="0901234567" value={phone} onChange={(e) => setPhone(e.target.value)} disabled={submitting} />
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-medium text-foreground">Email</label>
                  <Input type="email" placeholder="email@example.com" value={email} onChange={(e) => setEmail(e.target.value)} disabled={submitting} />
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-medium text-foreground">Nội dung</label>
                  <Textarea placeholder="Mô tả vấn đề hoặc yêu cầu cần hỗ trợ..." rows={5} value={message} onChange={(e) => setMessage(e.target.value)} disabled={submitting} />
                </div>
                <Button type="submit" className="w-full transition-transform active:scale-[0.98]" disabled={submitting}>
                  {submitting ? (
                    <>
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                      Đang gửi yêu cầu...
                    </>
                  ) : (
                    'Gửi yêu cầu hỗ trợ'
                  )}
                </Button>
              </form>
            </div>
          </ScrollReveal>
        </div>
      </div>
    </div>
  );
}
