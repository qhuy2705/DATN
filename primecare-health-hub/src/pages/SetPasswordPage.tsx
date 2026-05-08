import { useMemo } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { Link, useSearchParams } from 'react-router-dom';
import { AlertTriangle, ArrowLeft, KeyRound } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { useCompleteCredentialSetup, useInspectCredentialToken } from '@/hooks/use-auth';

const schema = z
  .object({
    newPassword: z.string().min(8, 'Mật khẩu cần tối thiểu 8 ký tự'),
    confirmPassword: z.string().min(8, 'Vui lòng nhập lại mật khẩu'),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    path: ['confirmPassword'],
    message: 'Mật khẩu xác nhận không khớp',
  });

export default function SetPasswordPage() {
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const inspectToken = useInspectCredentialToken(token);
  const completeSetup = useCompleteCredentialSetup();

  const form = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: {
      newPassword: '',
      confirmPassword: '',
    },
  });

  const tokenState = useMemo(() => {
    if (!token) return 'missing';
    if (inspectToken.isLoading) return 'loading';
    if (inspectToken.isError) return 'invalid';
    if (inspectToken.data?.expired || inspectToken.data?.used) return 'invalid';
    return 'ready';
  }, [inspectToken.data?.expired, inspectToken.data?.used, inspectToken.isError, inspectToken.isLoading, token]);

  const onSubmit = form.handleSubmit(async (values) => {
    await completeSetup.mutateAsync({ token, newPassword: values.newPassword });
  });

  return (
    <div className="min-h-screen bg-muted/30 px-4 py-10">
      <div className="mx-auto flex w-full max-w-xl flex-col gap-6">
        <Link to="/login" className="inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground">
          <ArrowLeft className="h-4 w-4" /> Quay lại đăng nhập
        </Link>

        <Card className="rounded-3xl shadow-sm">
          <CardHeader>
            <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-2xl bg-primary/10 text-primary">
              <KeyRound className="h-6 w-6" />
            </div>
            <CardTitle>Thiết lập mật khẩu PrimeCare</CardTitle>
            <CardDescription>
              {tokenState === 'ready'
                ? 'Liên kết hợp lệ. Hãy đặt mật khẩu mới để kích hoạt hoặc khôi phục tài khoản của bạn.'
                : 'PrimeCare đang kiểm tra liên kết thiết lập mật khẩu.'}
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-5">
            {tokenState === 'loading' ? (
              <p className="text-sm text-muted-foreground">Đang kiểm tra liên kết...</p>
            ) : null}

            {tokenState === 'invalid' || tokenState === 'missing' ? (
              <div className="rounded-2xl border border-destructive/30 bg-destructive/5 p-4 text-sm text-destructive">
                <div className="flex items-start gap-3">
                  <AlertTriangle className="mt-0.5 h-5 w-5" />
                  <div>
                    <p className="font-medium">Liên kết không hợp lệ hoặc đã hết hạn</p>
                    <p className="mt-1 text-destructive/90">
                      Vui lòng yêu cầu gửi lại liên kết thiết lập mật khẩu từ quản trị viên hoặc từ màn hình quên mật khẩu.
                    </p>
                  </div>
                </div>
              </div>
            ) : null}

            {tokenState === 'ready' ? (
              <>
                <div className="rounded-2xl border bg-muted/20 p-4 text-sm">
                  <div className="grid gap-2 sm:grid-cols-2">
                    <div>
                      <p className="text-xs uppercase tracking-wide text-muted-foreground">Người dùng</p>
                      <p className="font-medium">{inspectToken.data?.fullName || '-'}</p>
                    </div>
                    <div>
                      <p className="text-xs uppercase tracking-wide text-muted-foreground">Mục đích</p>
                      <p className="font-medium">
                        {inspectToken.data?.purpose === 'ACCOUNT_SETUP' ? 'Kích hoạt tài khoản' : 'Đặt lại mật khẩu'}
                      </p>
                    </div>
                    <div>
                      <p className="text-xs uppercase tracking-wide text-muted-foreground">Email</p>
                      <p className="font-medium">{inspectToken.data?.email || '-'}</p>
                    </div>
                    <div>
                      <p className="text-xs uppercase tracking-wide text-muted-foreground">Số điện thoại</p>
                      <p className="font-medium">{inspectToken.data?.phone || '-'}</p>
                    </div>
                  </div>
                </div>

                <form onSubmit={onSubmit} className="space-y-4">
                  <div>
                    <label className="mb-1.5 block text-sm font-medium">Mật khẩu mới</label>
                    <Input type="password" {...form.register('newPassword')} placeholder="Tối thiểu 8 ký tự" />
                    {form.formState.errors.newPassword ? (
                      <p className="mt-1 text-xs text-destructive">{form.formState.errors.newPassword.message}</p>
                    ) : null}
                  </div>
                  <div>
                    <label className="mb-1.5 block text-sm font-medium">Nhập lại mật khẩu mới</label>
                    <Input type="password" {...form.register('confirmPassword')} placeholder="Nhập lại mật khẩu" />
                    {form.formState.errors.confirmPassword ? (
                      <p className="mt-1 text-xs text-destructive">{form.formState.errors.confirmPassword.message}</p>
                    ) : null}
                  </div>

                  <Button type="submit" className="w-full" disabled={completeSetup.isPending}>
                    {completeSetup.isPending ? 'Đang lưu mật khẩu...' : 'Hoàn tất thiết lập mật khẩu'}
                  </Button>
                </form>
              </>
            ) : null}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
