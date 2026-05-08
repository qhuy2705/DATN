import { ScrollReveal } from '@/components/ScrollReveal';
import { SectionTitle } from '@/components/SectionTitle';
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from '@/components/ui/accordion';
import { usePublicFaqs } from '@/hooks/use-public-data';
import { useTranslation } from 'react-i18next';

const fallbackFaqs = [
  {
    id: 'booking',
    questionVn: 'Làm thế nào để đặt lịch khám?',
    questionEn: 'How do I book an appointment?',
    answerVn:
      'Bạn có thể đặt lịch trực tuyến qua website, gọi hotline hoặc đến trực tiếp quầy lễ tân tại các cơ sở.',
    answerEn:
      'You can book online via the website, call the hotline, or visit the front desk at any branch.',
  },
  {
    id: 'reschedule',
    questionVn: 'Tôi có thể hủy lịch sau khi xác thực OTP không?',
    questionEn: 'Can I cancel after OTP verification?',
    answerVn:
      'Có. Sau khi xác thực OTP tại trang Tra cứu, bạn có thể tự hủy các lịch còn ở trạng thái chờ xác nhận hoặc đã xác nhận.',
    answerEn:
      'Yes. After OTP verification on the Lookup page, you can cancel appointments that are still requested or confirmed.',
  },
  {
    id: 'results',
    questionVn: 'Kết quả xét nghiệm có trong bao lâu?',
    questionEn: 'How long does lab result turnaround take?',
    answerVn:
      'Tùy loại dịch vụ. Một số dịch vụ có kết quả trong ngày, các dịch vụ khác cần 1–3 ngày làm việc.',
    answerEn:
      'It depends on the service. Some results are available on the same day, while others take 1–3 business days.',
  },
];

export default function FaqPage() {
  const { i18n } = useTranslation();
  const isEn = i18n.language?.startsWith('en');
  const { data, isLoading } = usePublicFaqs();
  const faqs = data && data.length > 0 ? data : fallbackFaqs;

  return (
    <div className="section-padding">
      <div className="container-wide max-w-3xl">
        <SectionTitle
          title={isEn ? 'Frequently asked questions' : 'Câu hỏi thường gặp'}
          subtitle={
            isEn
              ? 'Booking, lookup, result turnaround, branch operations, and preparation guidance.'
              : 'Các câu hỏi phổ biến về đặt lịch, tra cứu, thời gian có kết quả, vận hành cơ sở và hướng dẫn chuẩn bị.'
          }
        />
        <ScrollReveal>
          {isLoading ? (
            <div className="rounded-card bg-card px-6 py-10 text-center text-muted-foreground shadow-soft">
              {isEn ? 'Loading FAQs...' : 'Đang tải FAQ...'}
            </div>
          ) : (
            <Accordion type="single" collapsible className="space-y-3">
              {faqs.map((faq, index) => (
                <AccordionItem
                  key={faq.id || index}
                  value={`faq-${index}`}
                  className="rounded-card border-0 bg-card px-6 shadow-soft"
                >
                  <AccordionTrigger className="text-left font-medium text-foreground hover:no-underline">
                    {isEn ? faq.questionEn || faq.questionVn : faq.questionVn || faq.questionEn}
                  </AccordionTrigger>
                  <AccordionContent className="text-muted-foreground">
                    {isEn ? faq.answerEn || faq.answerVn : faq.answerVn || faq.answerEn}
                  </AccordionContent>
                </AccordionItem>
              ))}
            </Accordion>
          )}
        </ScrollReveal>
      </div>
    </div>
  );
}
