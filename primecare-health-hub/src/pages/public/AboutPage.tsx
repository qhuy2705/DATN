import { ScrollReveal } from '@/components/ScrollReveal';
import { SectionTitle } from '@/components/SectionTitle';
import { Shield, Heart, Users, Building2, Clock3, BadgeCheck } from 'lucide-react';

const pillars = [
  {
    icon: Shield,
    title: 'An toàn & chuẩn hóa',
    desc: 'PrimeCare thiết kế quy trình theo hướng an toàn người bệnh, có hướng dẫn trước khám, nhắc lịch và theo dõi trạng thái dịch vụ trong cùng một hệ thống.',
  },
  {
    icon: Heart,
    title: 'Lấy người bệnh làm trung tâm',
    desc: 'Trải nghiệm được xây dựng quanh nhu cầu thực tế: chọn bác sĩ, theo dõi khung giờ còn chỗ, tra cứu OTP, nhận PDF phiếu hẹn và xem kết quả sau khám.',
  },
  {
    icon: Users,
    title: 'Liên thông đội ngũ',
    desc: 'Từ lễ tân, bác sĩ, kỹ thuật viên đến thu ngân, các vai trò vận hành cùng trên một nền tảng để giảm nhập liệu lặp và chậm trễ nội bộ.',
  },
  {
    icon: Building2,
    title: 'Chuẩn multi-branch',
    desc: 'Hỗ trợ quản lý nhiều cơ sở, nhiều chuyên khoa, nhiều lịch bác sĩ và khả năng mở rộng thêm dịch vụ công khai trong tương lai.',
  },
];

const highlights = [
  'Đặt lịch khám trực tuyến theo bác sĩ, cơ sở và khung giờ',
  'Tra cứu phiếu hẹn và kết quả bằng OTP',
  'Dữ liệu lịch trống tự cập nhật thay vì yêu cầu tải lại thủ công',
  'Quy trình nội bộ cho lễ tân, bác sĩ, kỹ thuật viên và thu ngân',
  'Hạ tầng sẵn sàng để mở rộng CMS, self-service và thông báo đa kênh',
];

export default function AboutPage() {
  return (
    <div className="section-padding">
      <div className="container-wide max-w-5xl">
        <SectionTitle
          title="Về PrimeCare"
          subtitle="Nền tảng quản lý và đặt lịch khám được thiết kế để tiến gần hơn tới một trải nghiệm y tế hiện đại, minh bạch và vận hành hiệu quả."
        />

        <ScrollReveal>
          <div className="rounded-card bg-card p-8 shadow-card">
            <p className="text-base leading-8 text-muted-foreground">
              PrimeCare hướng tới mô hình hệ thống y tế số nơi người bệnh có thể chủ động đặt lịch,
              theo dõi hành trình khám và nhận thông tin hậu khám rõ ràng, trong khi đội ngũ vận hành
              có một nguồn dữ liệu thống nhất để phối hợp giữa các bộ phận. Mục tiêu không chỉ là hiển thị
              lịch trống, mà còn là tạo ra trải nghiệm đáng tin cậy từ trước khám đến sau khám.
            </p>
          </div>
        </ScrollReveal>

        <div className="mt-10 grid gap-6 md:grid-cols-2">
          {pillars.map((item, index) => (
            <ScrollReveal key={item.title} delay={index * 60}>
              <div className="rounded-card bg-card p-6 shadow-card">
                <item.icon className="mb-3 h-8 w-8 text-primary" />
                <h3 className="text-lg font-semibold text-foreground">{item.title}</h3>
                <p className="mt-2 text-muted-foreground">{item.desc}</p>
              </div>
            </ScrollReveal>
          ))}
        </div>

        <ScrollReveal className="mt-10">
          <div className="grid gap-6 rounded-card bg-card p-8 shadow-card md:grid-cols-[1.2fr,0.8fr]">
            <div>
              <h3 className="text-lg font-semibold text-foreground">Điểm nổi bật của trải nghiệm hiện đại</h3>
              <div className="mt-4 space-y-3">
                {highlights.map((item) => (
                  <div key={item} className="flex items-start gap-3 text-sm text-muted-foreground">
                    <BadgeCheck className="mt-0.5 h-4 w-4 text-primary" />
                    <span>{item}</span>
                  </div>
                ))}
              </div>
            </div>
            <div className="rounded-2xl bg-muted/40 p-5">
              <div className="flex items-center gap-2 text-sm font-medium text-foreground">
                <Clock3 className="h-4 w-4 text-primary" /> Lưu ý vận hành
              </div>
              <p className="mt-3 text-sm leading-7 text-muted-foreground">
                PrimeCare tiếp tục hoàn thiện theo hướng product-grade: tăng tính chi tiết của hồ sơ bác sĩ,
                bổ sung self-service sau đặt lịch, đồng bộ nội dung FAQ/CMS và làm giàu dữ liệu chi nhánh, dịch vụ.
              </p>
            </div>
          </div>
        </ScrollReveal>
      </div>
    </div>
  );
}
