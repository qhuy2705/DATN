import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { Link } from 'react-router-dom';
import { ArrowLeft, MailCheck } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useRequestPasswordReset } from '@/hooks/use-auth';

const schema = z.object({
  identifier: z.string().min(1, 'Vui lòng nhập email hoặc số điện thoại'),
});

export default function ForgotPasswordPage() {
  const requestReset = useRequestPasswordReset();
  const form = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: { identifier: '' },
  });

  const onSubmit = form.handleSubmit(async (values) => {
    await requestReset.mutateAsync(values);
    form.reset();
  });

  return (
    <div className="min-h-screen bg-muted/30 px-4 py-10">
      <div className="mx-auto w-full max-w-md rounded-3xl border bg-background p-6 shadow-sm sm:p-8">
        <Link to="/login" className="mb-6 inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground">
          <ArrowLeft className="h-4 w-4" /> Quay lại đăng nhập
        </Link>

        <div className="mb-6 flex h-12 w-12 items-center justify-center rounded-2xl bg-primary/10 text-primary">
          <MailCheck className="h-6 w-6" />
        </div>
        <h1 className="text-2xl font-semibold tracking-tight">Quên mật khẩu</h1>
        <p className="mt-2 text-sm leading-6 text-muted-foreground">
          Nhập email hoặc số điện thoại đã liên kết với tài khoản PrimeCare. Nếu tài khoản tồn tại,
          hệ thống sẽ gửi liên kết thiết lập mật khẩu mới qua email hoặc SMS.
        </p>

        <form onSubmit={onSubmit} className="mt-6 space-y-4">
          <div>
            <label className="mb-1.5 block text-sm font-medium">Email hoặc số điện thoại</label>
            <Input {...form.register('identifier')} placeholder="doctor@primecare.vn hoặc 0912345678" autoFocus />
            {form.formState.errors.identifier ? (
              <p className="mt-1 text-xs text-destructive">{form.formState.errors.identifier.message}</p>
            ) : null}
          </div>

          <Button type="submit" className="w-full" disabled={requestReset.isPending}>
            {requestReset.isPending ? 'Đang gửi hướng dẫn...' : 'Gửi hướng dẫn thiết lập mật khẩu'}
          </Button>
        </form>
      </div>
    </div>
  );
}
