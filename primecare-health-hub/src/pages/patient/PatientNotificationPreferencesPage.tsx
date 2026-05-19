import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Switch } from '@/components/ui/switch';
import { Label } from '@/components/ui/label';
import { useNotificationPreferences, useUpdateNotificationPreferences } from '@/hooks/use-patient-portal';

const SMS_NOT_CONFIGURED_MESSAGE = 'SMS chưa được cấu hình trong môi trường hiện tại.';
const APPOINTMENT_REMINDER_EMAIL_MESSAGE =
  'PrimeCare sẽ gửi email nhắc lịch khi lịch khám được xác nhận và còn đủ xa thời điểm khám.';

const notificationPreferenceRows = [
  { field: 'allowEmail', label: 'Cho phép nhận email' },
  {
    field: 'allowSms',
    label: 'Cho phép nhận SMS',
    description: `${SMS_NOT_CONFIGURED_MESSAGE} Nhắc lịch hẹn hiện được gửi qua email.`,
    disabled: true,
  },
  {
    field: 'appointmentReminders',
    label: 'Nhắc lịch hẹn qua email',
    description: APPOINTMENT_REMINDER_EMAIL_MESSAGE,
  },
  { field: 'resultReadyAlerts', label: 'Thông báo có kết quả' },
  { field: 'invoiceUpdates', label: 'Cập nhật hóa đơn' },
  { field: 'securityAlerts', label: 'Cảnh báo bảo mật' },
] as const;

export default function PatientNotificationPreferencesPage() {
  const { data, isLoading } = useNotificationPreferences();
  const updateMutation = useUpdateNotificationPreferences();

  const updateField = (patch: Record<string, unknown>) => {
    updateMutation.mutate({ ...(data || {}), ...patch });
  };
  const preferredChannel = data?.preferredChannel ?? 'EMAIL';

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

            {notificationPreferenceRows.map((row) => (
              <div key={row.field} className="flex items-center justify-between gap-4 rounded-lg border p-4">
                <div className="space-y-1">
                  <Label htmlFor={row.field} className="text-sm font-medium">{row.label}</Label>
                  {'description' in row && (
                    <p className="text-xs leading-5 text-muted-foreground">{row.description}</p>
                  )}
                </div>
                <Switch
                  id={row.field}
                  checked={Boolean((data as Record<string, unknown> | undefined)?.[row.field])}
                  onCheckedChange={(checked) => updateField({ [row.field]: checked })}
                  disabled={updateMutation.isPending || ('disabled' in row && row.disabled)}
                />
              </div>
            ))}

            <div className="rounded-lg border p-4">
              <Label className="text-sm font-medium">Kênh ưu tiên</Label>
              <select
                className="mt-2 flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                value={preferredChannel}
                onChange={(e) => updateField({ preferredChannel: e.target.value })}
                disabled={updateMutation.isPending}
              >
                <option value="EMAIL">Email</option>
                <option value="SMS" disabled>SMS - chưa cấu hình</option>
              </select>
              <p className="mt-2 text-xs leading-5 text-muted-foreground">Nhắc lịch hẹn hiện được gửi qua email.</p>
            </div>
          </>
        )}
      </CardContent>
    </Card>
  );
}
