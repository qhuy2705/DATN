import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { toast } from 'sonner';
import { useLogin } from '@/hooks/use-auth';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Link } from 'react-router-dom';
import { BriefcaseMedical, Eye, EyeOff, HeartPulse, ShieldCheck } from 'lucide-react';
import {
  AuthField,
  AuthPageShell,
  authInputClassName,
  authToggleButtonClassName,
} from '@/components/auth/AuthPageShell';

const loginSchema = z.object({
  identifier: z.string().min(1, 'Vui lòng nhập email hoặc số điện thoại'),
  password: z.string().min(1, 'Vui lòng nhập mật khẩu'),
});

export default function LoginPage() {
  const [showPw, setShowPw] = useState(false);
  const loginMutation = useLogin();
  const form = useForm<z.infer<typeof loginSchema>>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      identifier: '',
      password: '',
    },
  });

  const onSubmit = form.handleSubmit(async (data) => {
    try {
      await loginMutation.mutateAsync({ identifier: data.identifier, password: data.password });
      toast.success('Đăng nhập thành công');
    } catch {
      // useLogin already shows the backend login error.
    }
  });

  return (
    <AuthPageShell
      eyebrow="Truy cập PrimeCare"
      title="Đăng nhập"
      description="Dùng tài khoản PrimeCare để vào khu vực bệnh nhân hoặc khu vực nội bộ. Nếu bạn chỉ cần đặt lịch hoặc tra cứu công khai, bạn có thể tiếp tục mà không cần đăng nhập."
      notice="Sau khi xác thực, hệ thống sẽ đưa bạn đến đúng khu vực theo vai trò. Bệnh nhân dùng cùng một lối vào để xem lại lịch hẹn, kết quả và hóa đơn đã có."
      asideEyebrow="Rõ ràng trước khi tiếp tục"
      asideTitle="Một lối vào bình tĩnh cho cả bệnh nhân và nhân sự"
      asideDescription="PrimeCare giữ trang đăng nhập sáng, tiết chế và dễ đọc để thao tác xác thực không tạo thêm áp lực trong bối cảnh chăm sóc sức khỏe."
      asideItems={[
        {
          icon: ShieldCheck,
          title: 'Tập trung vào việc cần làm',
          description: 'Biểu mẫu chỉ giữ lại các trường cần thiết, có focus rõ ràng và thao tác tốt trên bàn phím lẫn điện thoại.',
        },
        {
          icon: HeartPulse,
          title: 'Dành cho bệnh nhân quay lại sau buổi khám',
          description: 'Tài khoản bệnh nhân giúp mở lại lịch hẹn, kết quả cũ và hóa đơn trong một nơi quen thuộc.',
        },
        {
          icon: BriefcaseMedical,
          title: 'Cũng là điểm vào cho vận hành nội bộ',
          description: 'Nhân sự phòng khám đăng nhập từ cùng cổng rồi tiếp tục vào đúng khu vực làm việc của mình.',
        },
      ]}
      asideFootnote={
        <>
          <p className="font-semibold text-foreground">Không có tài khoản bệnh nhân?</p>
          <p className="mt-1">
            Bạn vẫn có thể đặt lịch và tra cứu công khai. Tạo tài khoản chỉ giúp việc quay lại thông tin sau khám thuận tiện hơn.
          </p>
        </>
      }
      footer={
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <Link
            to="/register"
            className="text-sm font-medium text-primary transition-colors duration-200 hover:text-primary/80"
          >
            Tạo tài khoản bệnh nhân
          </Link>
          <Link
            to="/forgot-password"
            className="text-sm font-medium text-primary transition-colors duration-200 hover:text-primary/80"
          >
            Quên mật khẩu?
          </Link>
        </div>
      }
    >
      <form onSubmit={onSubmit} className="space-y-5">
        <AuthField
          htmlFor="identifier"
          label="Email hoặc số điện thoại"
          hint="Dùng email hoặc số điện thoại đã liên kết với tài khoản PrimeCare."
          error={form.formState.errors.identifier?.message}
        >
          <Input
            id="identifier"
            {...form.register('identifier')}
            autoCapitalize="none"
            autoComplete="username"
            autoFocus
            className={authInputClassName}
            placeholder="doctor@primecare.vn hoặc 0912345678"
            spellCheck={false}
          />
        </AuthField>

        <AuthField
          htmlFor="password"
          label="Mật khẩu"
          error={form.formState.errors.password?.message}
        >
          <div className="relative">
            <Input
              id="password"
              {...form.register('password')}
              autoComplete="current-password"
              className={`${authInputClassName} pr-12`}
              placeholder="Nhập mật khẩu"
              type={showPw ? 'text' : 'password'}
            />
            <button
              type="button"
              aria-label={showPw ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
              aria-pressed={showPw}
              className={authToggleButtonClassName}
              onClick={() => setShowPw(!showPw)}
            >
              {showPw ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
            </button>
          </div>
        </AuthField>

        <Button
          type="submit"
          className="h-12 w-full rounded-2xl text-sm font-semibold shadow-[0_14px_30px_rgba(8,46,95,0.14)] transition-all duration-200 hover:-translate-y-0.5 hover:bg-primary/95 active:translate-y-0"
          disabled={loginMutation.isPending}
        >
          {loginMutation.isPending ? 'Đang đăng nhập...' : 'Đăng nhập'}
        </Button>
      </form>
    </AuthPageShell>
  );
}
