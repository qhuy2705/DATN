import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Switch } from '@/components/ui/switch';
import { Label } from '@/components/ui/label';
import { useNotificationPreferences, useUpdateNotificationPreferences } from '@/hooks/use-patient-portal';

export default function PatientNotificationPreferencesPage() {
  const { data, isLoading } = useNotificationPreferences();
  const updateMutation = useUpdateNotificationPreferences();

  const updateField = (patch: Record<string, unknown>) => {
    updateMutation.mutate({ ...(data || {}), ...patch });
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>Tuỳ chọn thông báo</CardTitle>
      </CardHeader>
      <CardContent className="space-y-5">
        {isLoading ? (
          <p className="text-sm text-muted-foreground">Đang tải tuỳ chọn thông báo...</p>
        ) : (
          <>
            <div className="rounded-lg border p-4 text-sm text-muted-foreground">
              <p>Email hiện tại: <span className="font-medium text-foreground">{data?.maskedEmail || 'Chưa có'}</span></p>
              <p>Số điện thoại hiện tại: <span className="font-medium text-foreground">{data?.maskedPhone || 'Chưa có'}</span></p>
            </div>

            {[
              ['allowEmail', 'Cho phép nhận email'],
              ['allowSms', 'Cho phép nhận SMS'],
              ['appointmentReminders', 'Nhắc lịch hẹn'],
              ['resultReadyAlerts', 'Thông báo có kết quả'],
              ['invoiceUpdates', 'Cập nhật hóa đơn'],
              ['securityAlerts', 'Cảnh báo bảo mật'],
            ].map(([field, label]) => (
              <div key={field} className="flex items-center justify-between rounded-lg border p-4">
                <Label htmlFor={field} className="text-sm font-medium">{label}</Label>
                <Switch
                  id={field}
                  checked={Boolean((data as Record<string, unknown> | undefined)?.[field])}
                  onCheckedChange={(checked) => updateField({ [field]: checked })}
                  disabled={updateMutation.isPending}
                />
              </div>
            ))}

            <div className="rounded-lg border p-4">
              <Label className="text-sm font-medium">Kênh ưu tiên</Label>
              <select
                className="mt-2 flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                value={data?.preferredChannel || 'SMS'}
                onChange={(e) => updateField({ preferredChannel: e.target.value })}
                disabled={updateMutation.isPending}
              >
                <option value="SMS">SMS</option>
                <option value="EMAIL">Email</option>
              </select>
            </div>
          </>
        )}
      </CardContent>
    </Card>
  );
}
