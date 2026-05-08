import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link } from 'react-router-dom';
import { ChevronDown, Eye, EyeOff, FileText, Phone, ShieldCheck } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useRegisterPatientAccount } from '@/hooks/use-patient-portal';
import {
  AuthField,
  AuthPageShell,
  authInputClassName,
  authSelectClassName,
  authToggleButtonClassName,
} from '@/components/auth/AuthPageShell';

const schema = z.object({
  fullName: z.string().min(1, 'Vui lòng nhập họ tên'),
  phone: z.string().min(10, 'Vui lòng nhập số điện thoại hợp lệ'),
  email: z.string().email('Email không hợp lệ').optional().or(z.literal('')),
  dob: z.string().optional(),
  gender: z.enum(['MALE', 'FEMALE', 'OTHER']).optional(),
  address: z.string().optional(),
  insuranceNumber: z.string().optional(),
  password: z.string().min(8, 'Mật khẩu tối thiểu 8 ký tự'),
  confirmPassword: z.string().min(8, 'Vui lòng xác nhận mật khẩu'),
}).refine((data) => data.password === data.confirmPassword, {
  path: ['confirmPassword'],
  message: 'Mật khẩu xác nhận không khớp',
});

type FormValues = z.infer<typeof schema>;

export default function RegisterPatientAccountPage() {
  const registerMutation = useRegisterPatientAccount();
  const [showPw, setShowPw] = useState(false);
  const [showConfirmPw, setShowConfirmPw] = useState(false);
  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      fullName: '',
      phone: '',
      email: '',
      dob: '',
      gender: 'MALE',
      address: '',
      insuranceNumber: '',
      password: '',
      confirmPassword: '',
    },
  });

  const onSubmit = form.handleSubmit((values) => {
    registerMutation.mutate({
      fullName: values.fullName,
      phone: values.phone,
      email: values.email || undefined,
      dob: values.dob || undefined,
      gender: values.gender,
      address: values.address || undefined,
      insuranceNumber: values.insuranceNumber || undefined,
      password: values.password,
    });
  });

  return (
    <AuthPageShell
      contentClassName="max-w-3xl"
      backHref="/"
      backLabel="Quay lại trang chủ"
      eyebrow="Tài khoản bệnh nhân"
      title="Tạo tài khoản bệnh nhân"
      description="Bạn vẫn có thể đặt lịch và tra cứu công khai như bình thường. Tài khoản bệnh nhân giúp việc quay lại lịch sử khám, kết quả và hóa đơn thuận tiện hơn sau mỗi lần đến cơ sở."
      notice="Tài khoản này không thay thế bước xác nhận lịch từ phòng khám. Nó tạo ra một lối vào riêng để bạn xem lại thông tin đã phát sinh trong quá trình chăm sóc."
      asideEyebrow="Điền một lần, dùng lâu dài"
      asideTitle="Thiết lập hồ sơ truy cập riêng cho những lần quay lại"
      asideDescription="Trang đăng ký được giữ sáng, gọn và ưu tiên những trường cần thiết để bệnh nhân hoặc người nhà hoàn tất nhanh trên điện thoại."
      asideItems={[
        {
          icon: Phone,
          title: 'Liên hệ rõ ràng cho những lần sau',
          description: 'Số điện thoại có thể được dùng để đăng nhập lại và giúp bạn giữ một đầu mối quen thuộc khi cần tra cứu.',
        },
        {
          icon: FileText,
          title: 'Một nơi để xem lại hồ sơ sau khám',
          description: 'Khi đã có tài khoản, bạn có thể quay lại lịch hẹn, kết quả cũ và hóa đơn thuận tiện hơn trong các lần theo dõi tiếp theo.',
        },
        {
          icon: ShieldCheck,
          title: 'Thiết lập mật khẩu riêng cho tài khoản',
          description: 'Biểu mẫu tách phần thông tin bệnh nhân và phần mật khẩu để thao tác rõ ràng, giảm nhầm lẫn trên màn hình nhỏ.',
        },
      ]}
      asideFootnote={
        <>
          <p className="font-semibold text-foreground">Đã có tài khoản?</p>
          <p className="mt-1">
            Bạn có thể quay lại màn hình đăng nhập bất cứ lúc nào để tiếp tục xem thông tin bệnh nhân hoặc truy cập khu vực nội bộ.
          </p>
        </>
      }
    >
      <form onSubmit={onSubmit} className="grid gap-5 md:grid-cols-2">
        <div className="space-y-1 md:col-span-2">
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">Thông tin cơ bản</p>
          <p className="text-sm leading-6 text-muted-foreground">
            Điền những chi tiết nhận diện chính xác cho hồ sơ bệnh nhân và kênh liên hệ khi cần.
          </p>
        </div>

        <AuthField
          className="md:col-span-2"
          htmlFor="fullName"
          label="Họ và tên"
          hint="Nên dùng đúng họ tên trên hồ sơ khám để thuận tiện đối chiếu về sau."
          error={form.formState.errors.fullName?.message}
        >
          <Input
            id="fullName"
            {...form.register('fullName')}
            autoComplete="name"
            autoFocus
            className={authInputClassName}
            placeholder="Nguyễn Văn A"
          />
        </AuthField>

        <AuthField
          htmlFor="phone"
          label="Số điện thoại"
          hint="Số này có thể dùng để đăng nhập và nhận liên hệ về tài khoản."
          error={form.formState.errors.phone?.message}
        >
          <Input
            id="phone"
            {...form.register('phone')}
            autoComplete="tel"
            className={authInputClassName}
            inputMode="tel"
            placeholder="0901234567"
          />
        </AuthField>

        <AuthField
          htmlFor="email"
          label="Email"
          optionalLabel="Tùy chọn"
          hint="Thêm email nếu bạn muốn có thêm một kênh đăng nhập hoặc nhận thông tin."
          error={form.formState.errors.email?.message}
        >
          <Input
            id="email"
            {...form.register('email')}
            autoCapitalize="none"
            autoComplete="email"
            className={authInputClassName}
            inputMode="email"
            placeholder="ban@example.com"
            spellCheck={false}
          />
        </AuthField>

        <AuthField
          htmlFor="dob"
          label="Ngày sinh"
          optionalLabel="Tùy chọn"
        >
          <Input
            id="dob"
            {...form.register('dob')}
            autoComplete="bday"
            className={authInputClassName}
            type="date"
          />
        </AuthField>

        <AuthField
          htmlFor="gender"
          label="Giới tính"
          optionalLabel="Tùy chọn"
        >
          <div className="relative">
            <select
              id="gender"
              {...form.register('gender')}
              className={authSelectClassName}
            >
              <option value="MALE">Nam</option>
              <option value="FEMALE">Nữ</option>
              <option value="OTHER">Khác</option>
            </select>
            <ChevronDown
              aria-hidden
              className="pointer-events-none absolute right-4 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground"
            />
          </div>
        </AuthField>

        <div className="space-y-1 border-t border-border/70 pt-2 md:col-span-2 md:pt-4">
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">Hồ sơ hỗ trợ</p>
          <p className="text-sm leading-6 text-muted-foreground">
            Những thông tin này giúp bạn hoàn thiện hồ sơ hơn, nhưng có thể bổ sung sau nếu cần.
          </p>
        </div>

        <AuthField
          className="md:col-span-2"
          htmlFor="address"
          label="Địa chỉ liên hệ"
          optionalLabel="Tùy chọn"
        >
          <Input
            id="address"
            {...form.register('address')}
            autoComplete="street-address"
            className={authInputClassName}
            placeholder="Số nhà, đường, phường/xã, quận/huyện"
          />
        </AuthField>

        <AuthField
          className="md:col-span-2"
          htmlFor="insuranceNumber"
          label="Mã bảo hiểm"
          optionalLabel="Tùy chọn"
        >
          <Input
            id="insuranceNumber"
            {...form.register('insuranceNumber')}
            className={authInputClassName}
            placeholder="Nếu bạn muốn lưu sẵn trong hồ sơ"
          />
        </AuthField>

        <div className="space-y-1 border-t border-border/70 pt-2 md:col-span-2 md:pt-4">
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">Bảo mật tài khoản</p>
          <p className="text-sm leading-6 text-muted-foreground">
            Thiết lập mật khẩu riêng để lần sau bạn có thể quay lại nhanh mà vẫn giữ quyền truy cập gọn gàng.
          </p>
        </div>

        <AuthField
          htmlFor="password"
          label="Mật khẩu"
          hint="Tối thiểu 8 ký tự."
          error={form.formState.errors.password?.message}
        >
          <div className="relative">
            <Input
              id="password"
              {...form.register('password')}
              autoComplete="new-password"
              className={`${authInputClassName} pr-12`}
              placeholder="Tạo mật khẩu"
              type={showPw ? 'text' : 'password'}
            />
            <button
              type="button"
              aria-label={showPw ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
              aria-pressed={showPw}
              className={authToggleButtonClassName}
              onClick={() => setShowPw((v) => !v)}
            >
              {showPw ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
            </button>
          </div>
        </AuthField>

        <AuthField
          htmlFor="confirmPassword"
          label="Xác nhận mật khẩu"
          error={form.formState.errors.confirmPassword?.message}
        >
          <div className="relative">
            <Input
              id="confirmPassword"
              {...form.register('confirmPassword')}
              autoComplete="new-password"
              className={`${authInputClassName} pr-12`}
              placeholder="Nhập lại mật khẩu"
              type={showConfirmPw ? 'text' : 'password'}
            />
            <button
              type="button"
              aria-label={showConfirmPw ? 'Ẩn mật khẩu xác nhận' : 'Hiện mật khẩu xác nhận'}
              aria-pressed={showConfirmPw}
              className={authToggleButtonClassName}
              onClick={() => setShowConfirmPw((v) => !v)}
            >
              {showConfirmPw ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
            </button>
          </div>
        </AuthField>

        <div className="flex flex-col-reverse gap-4 border-t border-border/70 pt-2 md:col-span-2 md:pt-5 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-sm leading-6 text-muted-foreground">
            Đã có tài khoản?{' '}
            <Link className="font-medium text-primary transition-colors duration-200 hover:text-primary/80" to="/login">
              Đăng nhập
            </Link>
          </p>

          <Button
            type="submit"
            className="h-12 w-full rounded-2xl px-6 text-sm font-semibold shadow-[0_14px_30px_rgba(8,46,95,0.14)] transition-all duration-200 hover:-translate-y-0.5 hover:bg-primary/95 active:translate-y-0 sm:w-auto"
            disabled={registerMutation.isPending}
          >
            {registerMutation.isPending ? 'Đang tạo...' : 'Tạo tài khoản'}
          </Button>
        </div>
      </form>
    </AuthPageShell>
  );
}
