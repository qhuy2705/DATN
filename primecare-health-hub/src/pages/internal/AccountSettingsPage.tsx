import { useEffect, useMemo, useState } from 'react';
import { KeyRound, Save, ShieldCheck, UserRound } from 'lucide-react';
import { PageHeader } from '@/components/PageHeader';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Button } from '@/components/ui/button';
import { UploadField } from '@/components/UploadField';
import { useAuthStore } from '@/stores/auth-store';
import { useDoctorMyProfile, useUpdateDoctorMyProfile } from '@/hooks/use-account-settings';
import { useChangePassword } from '@/hooks/use-auth';
import { toast } from 'sonner';

const defaultDoctorForm = {
  fullName: '',
  displayTitleVn: '',
  displayTitleEn: '',
  bioVn: '',
  bioEn: '',
  expertiseVn: '',
  expertiseEn: '',
  educationVn: '',
  educationEn: '',
  achievementsVn: '',
  achievementsEn: '',
  yearsExp: 0,
  avatarUrl: '',
};

export default function AccountSettingsPage() {
  const user = useAuthStore((s) => s.user);
  const isDoctor = user?.role === 'DOCTOR';
  const { data: doctorProfile } = useDoctorMyProfile(isDoctor);
  const updateDoctorProfile = useUpdateDoctorMyProfile();
  const changePassword = useChangePassword();

  const [doctorForm, setDoctorForm] = useState(defaultDoctorForm);
  const [securityForm, setSecurityForm] = useState({
    currentPassword: '',
    newPassword: '',
    confirmPassword: '',
  });

  useEffect(() => {
    if (!doctorProfile) return;
    setDoctorForm({
      fullName: doctorProfile.fullName || '',
      displayTitleVn: doctorProfile.title || '',
      displayTitleEn: doctorProfile.title || '',
      bioVn: doctorProfile.bio || '',
      bioEn: doctorProfile.bio || '',
      expertiseVn: doctorProfile.expertise || '',
      expertiseEn: doctorProfile.expertise || '',
      educationVn: doctorProfile.education || '',
      educationEn: doctorProfile.education || '',
      achievementsVn: doctorProfile.achievements || '',
      achievementsEn: doctorProfile.achievements || '',
      yearsExp: Number(doctorProfile.yearsOfExperience || 0),
      avatarUrl: doctorProfile.avatarUrl || '',
    });
  }, [doctorProfile]);

  const doctorSummary = useMemo(() => {
    if (!doctorProfile) return [];
    return [
      { label: 'Chi nhánh', value: doctorProfile.branchName || '-' },
      { label: 'Chuyên khoa', value: doctorProfile.specialtyName || '-' },
      { label: 'Trạng thái vận hành', value: doctorProfile.effectiveStatus || doctorProfile.status || '-' },
      { label: 'Tài khoản', value: doctorProfile.accountStatus || 'Chưa có tài khoản' },
    ];
  }, [doctorProfile]);

  const saveDoctorProfile = async () => {
    await updateDoctorProfile.mutateAsync(doctorForm);
  };

  const submitPassword = async () => {
    if (securityForm.newPassword !== securityForm.confirmPassword) {
      toast.error('Mật khẩu xác nhận không khớp');
      return;
    }
    await changePassword.mutateAsync({
      currentPassword: securityForm.currentPassword,
      newPassword: securityForm.newPassword,
    });
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Tài khoản & bảo mật"
        description="Quản lý thông tin cá nhân, avatar và đổi mật khẩu theo chuẩn nội bộ PrimeCare"
      />

      <Tabs defaultValue={isDoctor ? 'profile' : 'security'} className="space-y-4">
        <TabsList>
          {isDoctor && (
            <TabsTrigger value="profile">
              <UserRound className="mr-2 h-4 w-4" /> Hồ sơ bác sĩ
            </TabsTrigger>
          )}
          <TabsTrigger value="security">
            <ShieldCheck className="mr-2 h-4 w-4" /> Bảo mật
          </TabsTrigger>
        </TabsList>

        {isDoctor && (
          <TabsContent value="profile">
            <div className="grid gap-6 lg:grid-cols-[1.3fr_0.7fr]">
              <Card>
                <CardHeader>
                  <CardTitle>Thông tin hiển thị bác sĩ</CardTitle>
                  <CardDescription>
                    Bác sĩ có thể tự cập nhật avatar, mô tả chuyên môn và thông tin giới thiệu. Chi nhánh và chuyên khoa vẫn do admin quản lý.
                  </CardDescription>
                </CardHeader>
                <CardContent className="space-y-4">
                  <div className="grid gap-4 sm:grid-cols-2">
                    <div>
                      <label className="mb-1.5 block text-sm font-medium">Họ tên</label>
                      <Input value={doctorForm.fullName} onChange={(e) => setDoctorForm((prev) => ({ ...prev, fullName: e.target.value }))} />
                    </div>
                    <div>
                      <label className="mb-1.5 block text-sm font-medium">Số năm kinh nghiệm</label>
                      <Input type="number" value={doctorForm.yearsExp} onChange={(e) => setDoctorForm((prev) => ({ ...prev, yearsExp: Number(e.target.value) }))} />
                    </div>
                  </div>

                  <div className="grid gap-4 sm:grid-cols-2">
                    <div>
                      <label className="mb-1.5 block text-sm font-medium">Chức danh VN</label>
                      <Input value={doctorForm.displayTitleVn} onChange={(e) => setDoctorForm((prev) => ({ ...prev, displayTitleVn: e.target.value }))} />
                    </div>
                    <div>
                      <label className="mb-1.5 block text-sm font-medium">Chức danh EN</label>
                      <Input value={doctorForm.displayTitleEn} onChange={(e) => setDoctorForm((prev) => ({ ...prev, displayTitleEn: e.target.value }))} />
                    </div>
                  </div>

                  <UploadField
                    label="Avatar bác sĩ"
                    value={doctorForm.avatarUrl}
                    ownerType="DOCTOR"
                    ownerId={doctorProfile?.id}
                    onChange={(url) => setDoctorForm((prev) => ({ ...prev, avatarUrl: url }))}
                    helperText="Ảnh được upload vào hệ thống file hiện tại và lưu URL vào hồ sơ bác sĩ."
                  />

                  <div className="grid gap-4 sm:grid-cols-2">
                    <div>
                      <label className="mb-1.5 block text-sm font-medium">Bio VN</label>
                      <Textarea rows={5} value={doctorForm.bioVn} onChange={(e) => setDoctorForm((prev) => ({ ...prev, bioVn: e.target.value }))} />
                    </div>
                    <div>
                      <label className="mb-1.5 block text-sm font-medium">Bio EN</label>
                      <Textarea rows={5} value={doctorForm.bioEn} onChange={(e) => setDoctorForm((prev) => ({ ...prev, bioEn: e.target.value }))} />
                    </div>
                  </div>

                  <div className="grid gap-4 sm:grid-cols-2">
                    <div>
                      <label className="mb-1.5 block text-sm font-medium">Chuyên môn VN</label>
                      <Textarea rows={4} value={doctorForm.expertiseVn} onChange={(e) => setDoctorForm((prev) => ({ ...prev, expertiseVn: e.target.value }))} />
                    </div>
                    <div>
                      <label className="mb-1.5 block text-sm font-medium">Chuyên môn EN</label>
                      <Textarea rows={4} value={doctorForm.expertiseEn} onChange={(e) => setDoctorForm((prev) => ({ ...prev, expertiseEn: e.target.value }))} />
                    </div>
                  </div>

                  <div className="grid gap-4 sm:grid-cols-2">
                    <div>
                      <label className="mb-1.5 block text-sm font-medium">Học vấn VN</label>
                      <Textarea rows={4} value={doctorForm.educationVn} onChange={(e) => setDoctorForm((prev) => ({ ...prev, educationVn: e.target.value }))} />
                    </div>
                    <div>
                      <label className="mb-1.5 block text-sm font-medium">Học vấn EN</label>
                      <Textarea rows={4} value={doctorForm.educationEn} onChange={(e) => setDoctorForm((prev) => ({ ...prev, educationEn: e.target.value }))} />
                    </div>
                  </div>

                  <div className="grid gap-4 sm:grid-cols-2">
                    <div>
                      <label className="mb-1.5 block text-sm font-medium">Thành tựu VN</label>
                      <Textarea rows={4} value={doctorForm.achievementsVn} onChange={(e) => setDoctorForm((prev) => ({ ...prev, achievementsVn: e.target.value }))} />
                    </div>
                    <div>
                      <label className="mb-1.5 block text-sm font-medium">Thành tựu EN</label>
                      <Textarea rows={4} value={doctorForm.achievementsEn} onChange={(e) => setDoctorForm((prev) => ({ ...prev, achievementsEn: e.target.value }))} />
                    </div>
                  </div>

                  <div className="flex justify-end">
                    <Button onClick={() => void saveDoctorProfile()} disabled={updateDoctorProfile.isPending}>
                      <Save className="mr-2 h-4 w-4" /> Lưu hồ sơ
                    </Button>
                  </div>
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle>Tóm tắt tài khoản bác sĩ</CardTitle>
                  <CardDescription>Thông tin vận hành hiện tại của hồ sơ và tài khoản đăng nhập.</CardDescription>
                </CardHeader>
                <CardContent className="space-y-3 text-sm">
                  {doctorSummary.map((item) => (
                    <div key={item.label} className="flex items-start justify-between gap-4 border-b pb-3 last:border-b-0 last:pb-0">
                      <span className="text-muted-foreground">{item.label}</span>
                      <span className="text-right font-medium">{item.value}</span>
                    </div>
                  ))}
                  <div className="rounded-lg border bg-muted/30 p-3 text-xs text-muted-foreground">
                    Trạng thái đăng nhập của bác sĩ được đồng bộ theo trạng thái hoạt động ở hồ sơ. Khi bác sĩ ngưng hoạt động, tài khoản sẽ tự động bị vô hiệu hóa.
                  </div>
                </CardContent>
              </Card>
            </div>
          </TabsContent>
        )}

        <TabsContent value="security">
          <div className="grid gap-6 lg:grid-cols-[0.9fr_1.1fr]">
            <Card>
              <CardHeader>
                <CardTitle>Thông tin tài khoản</CardTitle>
                <CardDescription>Dữ liệu dùng để đăng nhập và xác định quyền truy cập.</CardDescription>
              </CardHeader>
              <CardContent className="space-y-3 text-sm">
                <div className="flex justify-between gap-4 border-b pb-3">
                  <span className="text-muted-foreground">Họ tên</span>
                  <span className="font-medium text-right">{user?.fullName || '-'}</span>
                </div>
                <div className="flex justify-between gap-4 border-b pb-3">
                  <span className="text-muted-foreground">Email</span>
                  <span className="font-medium text-right">{user?.email || '-'}</span>
                </div>
                <div className="flex justify-between gap-4 border-b pb-3">
                  <span className="text-muted-foreground">Vai trò</span>
                  <span className="font-medium text-right">{user?.role || '-'}</span>
                </div>
                <div className="flex justify-between gap-4">
                  <span className="text-muted-foreground">Chi nhánh</span>
                  <span className="font-medium text-right">{user?.branchName || '-'}</span>
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Đổi mật khẩu</CardTitle>
                <CardDescription>
                  PrimeCare hiện hỗ trợ quên mật khẩu bằng liên kết thiết lập lại qua email hoặc SMS. Sau khi đổi mật khẩu thành công, tất cả phiên đăng nhập cũ sẽ bị thu hồi.
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div>
                  <label className="mb-1.5 block text-sm font-medium">Mật khẩu hiện tại</label>
                  <Input type="password" value={securityForm.currentPassword} onChange={(e) => setSecurityForm((prev) => ({ ...prev, currentPassword: e.target.value }))} />
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-medium">Mật khẩu mới</label>
                  <Input type="password" value={securityForm.newPassword} onChange={(e) => setSecurityForm((prev) => ({ ...prev, newPassword: e.target.value }))} />
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-medium">Xác nhận mật khẩu mới</label>
                  <Input type="password" value={securityForm.confirmPassword} onChange={(e) => setSecurityForm((prev) => ({ ...prev, confirmPassword: e.target.value }))} />
                </div>
                <div className="flex justify-end">
                  <Button onClick={() => void submitPassword()} disabled={changePassword.isPending}>
                    <KeyRound className="mr-2 h-4 w-4" /> Cập nhật mật khẩu
                  </Button>
                </div>
              </CardContent>
            </Card>
          </div>
        </TabsContent>
      </Tabs>
    </div>
  );
}
