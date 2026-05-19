import { useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  CalendarClock,
  CheckCircle2,
  PhoneCall,
  PhoneForwarded,
  RefreshCw,
  XCircle,
} from 'lucide-react';
import { toast } from 'sonner';
import { EmptyState } from '@/components/EmptyState';
import { ErrorState } from '@/components/ErrorState';
import { LoadingSkeleton } from '@/components/LoadingSkeleton';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import {
  useCancelPublicAppointmentResponse,
  useKeepPublicAppointmentResponse,
  usePublicAppointmentResponse,
  useRequestPublicAppointmentRecall,
  useUpdatePublicAppointmentPhone,
} from '@/hooks/use-public-data';
import { getApiErrorMessage } from '@/lib/error-utils';
import type { PublicAppointmentResponseInfo } from '@/types/api';

type CompletedAction = 'keep' | 'recall' | 'phone' | 'cancel';

const COMPLETED_COPY: Record<CompletedAction, string> = {
  keep: 'Lịch hẹn của Quý khách đã được giữ. Phòng khám sẽ tiếp tục hỗ trợ theo thông tin hiện có.',
  recall: 'Yêu cầu gọi lại đã được ghi nhận. Nhân viên phòng khám sẽ liên hệ lại trong thời gian phù hợp.',
  phone: 'Số điện thoại mới đã được cập nhật. Nhân viên phòng khám sẽ sử dụng số này để hỗ trợ lịch hẹn.',
  cancel: 'Lịch hẹn đã được hủy theo yêu cầu của Quý khách.',
};

function getAxiosStatus(error: unknown) {
  return (error as { response?: { status?: number } })?.response?.status;
}

function formatAppointmentDateTime(info?: PublicAppointmentResponseInfo) {
  if (!info) return '—';
  const dateText = info.visitDate || '';
  const timeText = info.slotStart
    ? `${info.slotStart}${info.slotEnd ? ` - ${info.slotEnd}` : ''}`
    : '';
  return [dateText, timeText].filter(Boolean).join(' · ') || '—';
}

function isPhoneValid(value: string) {
  const normalized = value.replace(/[\s().-]/g, '');
  return /^(\+?84|0)\d{8,10}$/.test(normalized);
}

function isExpiredOrInvalidStatus(value?: string) {
  const normalized = String(value || '').toUpperCase();
  return ['EXPIRED', 'INVALID', 'USED', 'CLOSED'].includes(normalized);
}

function InfoRow({ label, value }: { label: string; value?: string }) {
  return (
    <div className="flex items-start justify-between gap-4 text-sm">
      <span className="text-muted-foreground">{label}</span>
      <span className="text-right font-medium text-foreground">{value || '—'}</span>
    </div>
  );
}

function AppointmentInfoCard({ info }: { info: PublicAppointmentResponseInfo }) {
  return (
    <Card className="border-border/70 shadow-sm">
      <CardHeader className="pb-3">
        <CardTitle className="text-base">Thông tin lịch hẹn</CardTitle>
      </CardHeader>
      <CardContent className="space-y-2">
        <InfoRow label="Mã lịch hẹn" value={info.appointmentCode} />
        <InfoRow label="Bệnh nhân" value={info.patientFullName} />
        <InfoRow label="Số điện thoại" value={info.maskedPhone || info.patientPhone} />
        <InfoRow label="Bác sĩ" value={info.doctorName} />
        <InfoRow label="Chuyên khoa" value={info.specialtyName} />
        <InfoRow label="Thời gian" value={formatAppointmentDateTime(info)} />
        <InfoRow label="Cơ sở" value={info.branchName} />
      </CardContent>
    </Card>
  );
}

export default function AppointmentResponsePage() {
  const { token = '' } = useParams();
  const [localInfo, setLocalInfo] = useState<PublicAppointmentResponseInfo | null>(null);
  const [completedAction, setCompletedAction] = useState<CompletedAction | null>(null);
  const [phoneFormOpen, setPhoneFormOpen] = useState(false);
  const [newPhone, setNewPhone] = useState('');
  const [phoneError, setPhoneError] = useState('');
  const [cancelOpen, setCancelOpen] = useState(false);

  const responseQuery = usePublicAppointmentResponse(token);
  const keepMutation = useKeepPublicAppointmentResponse();
  const recallMutation = useRequestPublicAppointmentRecall();
  const updatePhoneMutation = useUpdatePublicAppointmentPhone();
  const cancelMutation = useCancelPublicAppointmentResponse();
  const activeInfo = localInfo ?? responseQuery.data;
  const isSubmitting =
    keepMutation.isPending ||
    recallMutation.isPending ||
    updatePhoneMutation.isPending ||
    cancelMutation.isPending;
  const completedMessage = completedAction ? COMPLETED_COPY[completedAction] : null;

  const actionsAvailable = useMemo(
    () => ({
      keep: activeInfo?.canKeepAppointment !== false,
      recall: activeInfo?.canRequestRecall !== false,
      updatePhone: activeInfo?.canUpdatePhone !== false,
      cancel: activeInfo?.canCancel !== false,
    }),
    [
      activeInfo?.canCancel,
      activeInfo?.canKeepAppointment,
      activeInfo?.canRequestRecall,
      activeInfo?.canUpdatePhone,
    ],
  );

  const handleActionResult = (
    action: CompletedAction,
    resultInfo?: PublicAppointmentResponseInfo,
    message?: string,
  ) => {
    setLocalInfo(resultInfo ?? activeInfo ?? null);
    setCompletedAction(action);
    if (message) toast.success(message);
  };

  const submitKeep = async () => {
    try {
      const result = await keepMutation.mutateAsync({ token, body: {} });
      handleActionResult('keep', result.appointment, result.message || COMPLETED_COPY.keep);
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'Không thể ghi nhận xác nhận giữ lịch.'));
      await responseQuery.refetch();
    }
  };

  const submitRecall = async () => {
    try {
      const result = await recallMutation.mutateAsync({ token, body: {} });
      handleActionResult('recall', result.appointment, result.message || COMPLETED_COPY.recall);
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'Không thể ghi nhận yêu cầu gọi lại.'));
      await responseQuery.refetch();
    }
  };

  const submitUpdatePhone = async () => {
    const trimmedPhone = newPhone.trim();
    if (!isPhoneValid(trimmedPhone)) {
      setPhoneError('Vui lòng nhập số điện thoại hợp lệ.');
      return;
    }

    try {
      const result = await updatePhoneMutation.mutateAsync({
        token,
        body: { phone: trimmedPhone },
      });
      setPhoneFormOpen(false);
      setNewPhone('');
      handleActionResult('phone', result.appointment, result.message || COMPLETED_COPY.phone);
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'Không thể cập nhật số điện thoại.'));
      await responseQuery.refetch();
    }
  };

  const submitCancel = async () => {
    try {
      const result = await cancelMutation.mutateAsync({ token, body: {} });
      setCancelOpen(false);
      handleActionResult('cancel', result.appointment, result.message || COMPLETED_COPY.cancel);
    } catch (error) {
      setCancelOpen(false);
      toast.error(getApiErrorMessage(error, 'Không thể hủy lịch hẹn.'));
      await responseQuery.refetch();
    }
  };

  if (responseQuery.isLoading) {
    return (
      <section className="mx-auto w-full max-w-4xl px-4 py-8 sm:px-6">
        <LoadingSkeleton variant="detail" />
      </section>
    );
  }

  const status = getAxiosStatus(responseQuery.error);
  if (responseQuery.isError && status !== 404 && status !== 410) {
    return (
      <section className="mx-auto w-full max-w-4xl px-4 py-8 sm:px-6">
        <ErrorState
          title="Không tải được thông tin lịch hẹn"
          description="Kết nối chưa ổn định. Quý khách vui lòng thử lại sau ít phút."
          onRetry={() => void responseQuery.refetch()}
        />
      </section>
    );
  }

  if (!activeInfo || status === 404 || status === 410 || isExpiredOrInvalidStatus(activeInfo.status)) {
    return (
      <section className="mx-auto w-full max-w-4xl px-4 py-8 sm:px-6">
        <EmptyState
          title="Liên kết không còn hiệu lực"
          description="Liên kết xác nhận lịch hẹn đã hết hạn hoặc đã được xử lý. Quý khách vui lòng liên hệ phòng khám để được hỗ trợ."
          actionLabel="Về trang chủ"
          onAction={() => {
            window.location.href = '/';
          }}
        />
      </section>
    );
  }

  return (
    <section className="mx-auto w-full max-w-5xl px-4 py-8 sm:px-6">
      <div className="space-y-5">
        <Card className="border-primary/20 bg-primary/5 shadow-sm">
          <CardContent className="flex flex-col gap-4 p-5 sm:flex-row sm:items-start">
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
              <PhoneForwarded className="h-5 w-5" />
            </span>
            <div className="min-w-0 flex-1">
              <h1 className="text-xl font-semibold tracking-tight text-foreground">
                Xác nhận thông tin liên hệ cho lịch hẹn
              </h1>
              <p className="mt-2 text-sm leading-6 text-muted-foreground">
                Chúng tôi chưa liên hệ được với bạn qua số điện thoại đã cung cấp. Vui lòng xác nhận lại để giữ lịch hẹn hoặc cập nhật số điện thoại.
              </p>
            </div>
          </CardContent>
        </Card>

        {completedMessage ? (
          <Card className="border-success/25 bg-success/5 shadow-sm">
            <CardContent className="flex items-start gap-3 p-4 text-sm text-success">
              <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0" />
              <p className="font-medium">{completedMessage}</p>
            </CardContent>
          </Card>
        ) : null}

        <div className="grid gap-4 lg:grid-cols-[1fr_1.1fr]">
          <AppointmentInfoCard info={activeInfo} />

          <Card className="border-border/70 shadow-sm">
            <CardHeader className="pb-3">
              <CardTitle className="text-base">Quý khách muốn xử lý lịch hẹn như thế nào?</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="grid gap-2">
                {actionsAvailable.keep ? (
                  <Button onClick={() => void submitKeep()} disabled={isSubmitting || Boolean(completedAction)}>
                    <CheckCircle2 className="mr-2 h-4 w-4" />
                    Tôi vẫn muốn giữ lịch
                  </Button>
                ) : null}
                {actionsAvailable.recall ? (
                  <Button
                    variant="outline"
                    onClick={() => void submitRecall()}
                    disabled={isSubmitting || Boolean(completedAction)}
                  >
                    <PhoneCall className="mr-2 h-4 w-4" />
                    Số điện thoại này đúng, vui lòng gọi lại
                  </Button>
                ) : null}
                {actionsAvailable.updatePhone ? (
                  <Button
                    variant="outline"
                    onClick={() => setPhoneFormOpen((value) => !value)}
                    disabled={isSubmitting || Boolean(completedAction)}
                  >
                    <RefreshCw className="mr-2 h-4 w-4" />
                    Tôi muốn đổi số điện thoại
                  </Button>
                ) : null}
                {actionsAvailable.cancel ? (
                  <Button
                    variant="outline"
                    className="border-destructive/25 text-destructive hover:bg-destructive/10 hover:text-destructive"
                    onClick={() => setCancelOpen(true)}
                    disabled={isSubmitting || Boolean(completedAction)}
                  >
                    <XCircle className="mr-2 h-4 w-4" />
                    Hủy lịch hẹn
                  </Button>
                ) : null}
              </div>

              {phoneFormOpen && !completedAction ? (
                <div className="rounded-lg border border-border/70 bg-muted/20 p-3">
                  <label className="mb-1.5 block text-sm font-medium">Số điện thoại mới</label>
                  <div className="flex flex-col gap-2 sm:flex-row">
                    <Input
                      value={newPhone}
                      onChange={(event) => {
                        setNewPhone(event.target.value);
                        setPhoneError('');
                      }}
                      placeholder="Ví dụ: 0901234567"
                    />
                    <Button onClick={() => void submitUpdatePhone()} disabled={isSubmitting}>
                      Cập nhật
                    </Button>
                  </div>
                  {phoneError ? <p className="mt-1 text-xs text-destructive">{phoneError}</p> : null}
                </div>
              ) : null}
            </CardContent>
          </Card>
        </div>

        <div className="flex justify-end">
          <Button asChild variant="outline">
            <Link to="/">
              <CalendarClock className="mr-2 h-4 w-4" />
              Về trang chủ
            </Link>
          </Button>
        </div>
      </div>

      <Dialog open={cancelOpen} onOpenChange={setCancelOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Hủy lịch hẹn</DialogTitle>
            <DialogDescription>
              Quý khách có chắc muốn hủy lịch hẹn này không? Phòng khám sẽ ghi nhận yêu cầu và mở lại khung giờ cho người cần đặt lịch.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCancelOpen(false)} disabled={isSubmitting}>
              Quay lại
            </Button>
            <Button variant="destructive" onClick={() => void submitCancel()} disabled={isSubmitting}>
              Hủy lịch hẹn
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </section>
  );
}
