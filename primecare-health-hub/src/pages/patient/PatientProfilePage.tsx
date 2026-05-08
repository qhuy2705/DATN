import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { useSearchParams, Link } from 'react-router-dom';
import { BellRing, LockKeyhole, Shield, UserRound } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Switch } from '@/components/ui/switch';
import { Label } from '@/components/ui/label';
import { UploadField } from '@/components/UploadField';
import { useChangePassword } from '@/hooks/use-auth';
import { useNotificationPreferences, usePatientProfile, useUpdateNotificationPreferences, useUpdatePatientProfile } from '@/hooks/use-patient-portal';
import {
  CONTACT_PASSWORD_REQUIRED_MESSAGE,
  getCurrentPasswordFieldError,
  hasContactChanged,
  toPatientProfileFormValues,
  toPatientProfilePayload,
  type PatientProfileFormValues,
} from './patient-profile-utils';

interface PasswordFormValues {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

const PROFILE_TABS = [
  { value: 'personal', label: 'Thông tin cá nhân', icon: UserRound },
  { value: 'notifications', label: 'Thông báo', icon: BellRing },
  { value: 'security', label: 'Bảo mật', icon: Shield },
] as const;

export default function PatientProfilePage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const activeTab = searchParams.get('tab') || 'personal';

  const { data, isLoading } = usePatientProfile();
  const updateMutation = useUpdatePatientProfile();
  const notificationsQuery = useNotificationPreferences(activeTab === 'notifications' || activeTab === 'security');
  const updateNotificationMutation = useUpdateNotificationPreferences();
  const changePasswordMutation = useChangePassword();

  const form = useForm<PatientProfileFormValues>({ defaultValues: { currentPassword: '' } });
  const passwordForm = useForm<PasswordFormValues>({
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  });

  useEffect(() => {
    if (data) {
      form.reset(toPatientProfileFormValues(data));
    }
  }, [data, form]);

  const watchedEmail = form.watch('email');
  const watchedPhone = form.watch('phone');
  const contactFieldsChanged = hasContactChanged({ email: watchedEmail, phone: watchedPhone }, data);

  const onSubmit = form.handleSubmit((values) => {
    if (!data) return;

    form.clearErrors('currentPassword');
    const contactChanged = hasContactChanged(values, data);
    const currentPassword = values.currentPassword?.trim();

    if (contactChanged && !currentPassword) {
      form.setError('currentPassword', { type: 'validate', message: CONTACT_PASSWORD_REQUIRED_MESSAGE });
      return;
    }

    updateMutation.mutate(toPatientProfilePayload(values, contactChanged), {
      onSuccess: (patient) => {
        form.reset(toPatientProfileFormValues(patient));
      },
      onError: (error) => {
        const message = getCurrentPasswordFieldError(error);
        if (message) {
          form.setError('currentPassword', { type: 'server', message });
        }
      },
    });
  });

  const onPasswordSubmit = passwordForm.handleSubmit((values) => {
    if (values.newPassword !== values.confirmPassword) {
      passwordForm.setError('confirmPassword', { type: 'validate', message: 'Mật khẩu xác nhận không khớp' });
      return;
    }
    changePasswordMutation.mutate({
      currentPassword: values.currentPassword,
      newPassword: values.newPassword,
    });
  });

  const updateField = (patch: Record<string, unknown>) => {
    updateNotificationMutation.mutate({ ...(notificationsQuery.data || {}), ...patch });
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
        <div>
          <div className="mb-2 inline-flex rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary">
            Hồ sơ & cài đặt
          </div>
          <h1 className="text-2xl font-semibold tracking-tight text-foreground md:text-3xl">Quản lý thông tin cá nhân</h1>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-muted-foreground">
            Cập nhật hồ sơ, tùy chọn thông báo và bảo mật tài khoản. Đây là nơi bạn quản lý phần “bản thân” trong PrimeCare.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button asChild variant="outline" className="rounded-full">
            <Link to="/me/results">Xem kết quả của tôi</Link>
          </Button>
          <Button asChild variant="outline" className="rounded-full">
            <Link to="/me/appointments">Xem phiếu hẹn của tôi</Link>
          </Button>
        </div>
      </div>

      <Tabs value={activeTab} onValueChange={(value) => setSearchParams({ tab: value })} className="space-y-5">
        <TabsList className="h-auto w-full flex-wrap justify-start rounded-2xl bg-muted/50 p-1">
          {PROFILE_TABS.map((tab) => {
            const Icon = tab.icon;
            return (
              <TabsTrigger key={tab.value} value={tab.value} className="rounded-xl px-4 py-2.5 data-[state=active]:shadow-sm">
                <Icon className="mr-2 h-4 w-4" />
                {tab.label}
              </TabsTrigger>
            );
          })}
        </TabsList>

        <TabsContent value="personal" className="space-y-5">
          <div className="grid gap-5 xl:grid-cols-[320px_minmax(0,1fr)]">
            <Card className="rounded-[24px] border border-border/70 bg-background/90 shadow-sm">
              <CardHeader>
                <CardTitle className="text-base">Ảnh đại diện & trạng thái hồ sơ</CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <UploadField
                  value={form.watch('avatarUrl') || ''}
                  onChange={(url) => form.setValue('avatarUrl', url, { shouldDirty: true })}
                  label="Ảnh đại diện"
                  ownerType="PATIENT"
                  ownerId={data?.id}
                  helperText={data?.id ? 'Upload ảnh để hồ sơ cá nhân trông thân thiện hơn.' : 'Đang tải hồ sơ bệnh nhân...'}
                />
                <div className="rounded-2xl border bg-muted/30 p-4 text-sm text-muted-foreground">
                  <p className="font-medium text-foreground">Mã hồ sơ</p>
                  <p className="mt-1">{data?.code || 'Đang cập nhật'}</p>
                </div>
                <div className="rounded-2xl border bg-muted/30 p-4 text-sm text-muted-foreground">
                  <p className="font-medium text-foreground">Bảo hiểm y tế</p>
                  <p className="mt-1">{form.watch('insuranceNumber') || 'Chưa khai báo mã bảo hiểm'}</p>
                </div>
              </CardContent>
            </Card>

            <Card className="rounded-[24px] border border-border/70 bg-background/90 shadow-sm">
              <CardHeader>
                <CardTitle className="text-base">Thông tin hồ sơ</CardTitle>
              </CardHeader>
              <CardContent>
                {isLoading ? (
                  <p className="text-sm text-muted-foreground">Đang tải hồ sơ...</p>
                ) : (
                  <form onSubmit={onSubmit} className="grid gap-4 md:grid-cols-2">
                    <div>
                      <label className="mb-1.5 block text-sm font-medium">Họ và tên</label>
                      <Input {...form.register('fullName')} />
                    </div>
                    <div>
                      <label className="mb-1.5 block text-sm font-medium">Số điện thoại</label>
                      <Input {...form.register('phone')} />
                    </div>
                    <div>
                      <label className="mb-1.5 block text-sm font-medium">Email</label>
                      <Input {...form.register('email')} />
                    </div>
                    <div>
                      <label className="mb-1.5 block text-sm font-medium">Mật khẩu hiện tại</label>
                      <Input
                        type="password"
                        autoComplete="current-password"
                        aria-invalid={Boolean(form.formState.errors.currentPassword)}
                        {...form.register('currentPassword')}
                      />
                      {form.formState.errors.currentPassword ? (
                        <p className="mt-1 text-xs text-destructive">{form.formState.errors.currentPassword.message}</p>
                      ) : (
                        <p className="mt-1 text-xs text-muted-foreground">
                          Chỉ cần nhập khi bạn thay đổi email hoặc số điện thoại.
                        </p>
                      )}
                    </div>
                    {contactFieldsChanged && (
                      <div className="md:col-span-2 rounded-2xl border border-primary/30 bg-primary/5 p-3 text-sm font-medium text-primary">
                        Bạn đang thay đổi thông tin liên lạc. Vui lòng xác nhận bằng mật khẩu hiện tại.
                      </div>
                    )}
                    <div>
                      <label className="mb-1.5 block text-sm font-medium">Ngày sinh</label>
                      <Input type="date" {...form.register('dob')} />
                    </div>
                    <div>
                      <label className="mb-1.5 block text-sm font-medium">Giới tính</label>
                      <select {...form.register('gender')} className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm">
                        <option value="">Chọn</option>
                        <option value="MALE">Nam</option>
                        <option value="FEMALE">Nữ</option>
                        <option value="OTHER">Khác</option>
                      </select>
                    </div>
                    <div>
                      <label className="mb-1.5 block text-sm font-medium">Mã bảo hiểm</label>
                      <Input {...form.register('insuranceNumber')} />
                    </div>
                    <div className="md:col-span-2">
                      <label className="mb-1.5 block text-sm font-medium">Địa chỉ</label>
                      <Input {...form.register('address')} />
                    </div>
                    <div>
                      <label className="mb-1.5 block text-sm font-medium">Người liên hệ khẩn cấp</label>
                      <Input {...form.register('emergencyContactName')} />
                    </div>
                    <div>
                      <label className="mb-1.5 block text-sm font-medium">SĐT khẩn cấp</label>
                      <Input {...form.register('emergencyContactPhone')} />
                    </div>
                    <div>
                      <label className="mb-1.5 block text-sm font-medium">Ghi chú dị ứng</label>
                      <Textarea rows={4} {...form.register('allergyNote')} />
                    </div>
                    <div>
                      <label className="mb-1.5 block text-sm font-medium">Ghi chú bệnh nền</label>
                      <Textarea rows={4} {...form.register('chronicDiseaseNote')} />
                    </div>
                    <div className="md:col-span-2">
                      <label className="mb-1.5 block text-sm font-medium">Ghi chú thêm cho PrimeCare</label>
                      <Textarea rows={4} {...form.register('note')} />
                    </div>
                    <div className="md:col-span-2 flex justify-end">
                      <Button type="submit" className="rounded-full" disabled={updateMutation.isPending}>
                        {updateMutation.isPending ? 'Đang lưu...' : 'Lưu thay đổi'}
                      </Button>
                    </div>
                  </form>
                )}
              </CardContent>
            </Card>
          </div>
        </TabsContent>

        <TabsContent value="notifications" className="space-y-5">
          <Card className="rounded-[24px] border border-border/70 bg-background/90 shadow-sm">
            <CardHeader>
              <CardTitle className="text-base">Tuỳ chọn thông báo</CardTitle>
            </CardHeader>
            <CardContent className="space-y-5">
              {notificationsQuery.isLoading ? (
                <p className="text-sm text-muted-foreground">Đang tải tuỳ chọn thông báo...</p>
              ) : (
                <>
                  <div className="grid gap-4 md:grid-cols-2">
                    <div className="rounded-2xl border bg-muted/30 p-4 text-sm text-muted-foreground">
                      <p className="font-medium text-foreground">Email hiện tại</p>
                      <p className="mt-1">{notificationsQuery.data?.maskedEmail || 'Chưa có'}</p>
                    </div>
                    <div className="rounded-2xl border bg-muted/30 p-4 text-sm text-muted-foreground">
                      <p className="font-medium text-foreground">Số điện thoại hiện tại</p>
                      <p className="mt-1">{notificationsQuery.data?.maskedPhone || 'Chưa có'}</p>
                    </div>
                  </div>

                  {[
                    ['allowEmail', 'Cho phép nhận email'],
                    ['allowSms', 'Cho phép nhận SMS'],
                    ['appointmentReminders', 'Nhắc lịch hẹn'],
                    ['resultReadyAlerts', 'Thông báo có kết quả'],
                    ['invoiceUpdates', 'Cập nhật hóa đơn'],
                    ['securityAlerts', 'Cảnh báo bảo mật'],
                  ].map(([field, label]) => (
                    <div key={field} className="flex items-center justify-between rounded-2xl border p-4">
                      <Label htmlFor={field} className="text-sm font-medium">{label}</Label>
                      <Switch
                        id={field}
                        checked={Boolean((notificationsQuery.data as Record<string, unknown> | undefined)?.[field])}
                        onCheckedChange={(checked) => updateField({ [field]: checked })}
                        disabled={updateNotificationMutation.isPending}
                      />
                    </div>
                  ))}

                  <div className="rounded-2xl border p-4">
                    <Label className="text-sm font-medium">Kênh ưu tiên</Label>
                    <select
                      className="mt-2 flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                      value={notificationsQuery.data?.preferredChannel || 'SMS'}
                      onChange={(e) => updateField({ preferredChannel: e.target.value })}
                      disabled={updateNotificationMutation.isPending}
                    >
                      <option value="SMS">SMS</option>
                      <option value="EMAIL">Email</option>
                    </select>
                  </div>
                </>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="security" className="space-y-5">
          <div className="grid gap-5 xl:grid-cols-[1fr_0.95fr]">
            <Card className="rounded-[24px] border border-border/70 bg-background/90 shadow-sm">
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-base"><LockKeyhole className="h-4 w-4 text-primary" /> Đổi mật khẩu</CardTitle>
              </CardHeader>
              <CardContent>
                <form onSubmit={onPasswordSubmit} className="grid gap-4">
                  <div>
                    <label className="mb-1.5 block text-sm font-medium">Mật khẩu hiện tại</label>
                    <Input type="password" {...passwordForm.register('currentPassword', { required: 'Vui lòng nhập mật khẩu hiện tại' })} />
                    {passwordForm.formState.errors.currentPassword && <p className="mt-1 text-xs text-destructive">{passwordForm.formState.errors.currentPassword.message}</p>}
                  </div>
                  <div>
                    <label className="mb-1.5 block text-sm font-medium">Mật khẩu mới</label>
                    <Input type="password" {...passwordForm.register('newPassword', { required: 'Vui lòng nhập mật khẩu mới', minLength: { value: 8, message: 'Mật khẩu tối thiểu 8 ký tự' } })} />
                    {passwordForm.formState.errors.newPassword && <p className="mt-1 text-xs text-destructive">{passwordForm.formState.errors.newPassword.message}</p>}
                  </div>
                  <div>
                    <label className="mb-1.5 block text-sm font-medium">Xác nhận mật khẩu mới</label>
                    <Input type="password" {...passwordForm.register('confirmPassword', { required: 'Vui lòng xác nhận mật khẩu mới' })} />
                    {passwordForm.formState.errors.confirmPassword && <p className="mt-1 text-xs text-destructive">{passwordForm.formState.errors.confirmPassword.message}</p>}
                  </div>
                  <div className="flex justify-end">
                    <Button type="submit" className="rounded-full" disabled={changePasswordMutation.isPending}>
                      {changePasswordMutation.isPending ? 'Đang cập nhật...' : 'Cập nhật mật khẩu'}
                    </Button>
                  </div>
                </form>
              </CardContent>
            </Card>

            <Card className="rounded-[24px] border border-border/70 bg-background/90 shadow-sm">
              <CardHeader>
                <CardTitle className="text-base">Khuyến nghị bảo mật</CardTitle>
              </CardHeader>
              <CardContent className="space-y-4 text-sm text-muted-foreground">
                <div className="rounded-2xl border bg-muted/30 p-4">
                  <p className="font-medium text-foreground">Giữ thông tin liên lạc luôn đúng</p>
                  <p className="mt-1">PrimeCare sẽ dùng email hoặc số điện thoại hiện tại để gửi cảnh báo đăng nhập, nhắc lịch và thông báo kết quả.</p>
                </div>
                <div className="rounded-2xl border bg-muted/30 p-4">
                  <p className="font-medium text-foreground">Mật khẩu mạnh</p>
                  <p className="mt-1">Nên dùng ít nhất 8 ký tự, kết hợp chữ hoa, chữ thường và số để đảm bảo an toàn cho hồ sơ khám của bạn.</p>
                </div>
              </CardContent>
            </Card>
          </div>
        </TabsContent>
      </Tabs>
    </div>
  );
}
