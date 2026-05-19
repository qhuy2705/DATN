import { useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  AlertCircle,
  CalendarClock,
  CheckCircle2,
  Clock3,
  PhoneCall,
  Stethoscope,
  XCircle,
} from 'lucide-react';
import { toast } from 'sonner';
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
import { Badge } from '@/components/ui/badge';
import { EmptyState } from '@/components/EmptyState';
import { ErrorState } from '@/components/ErrorState';
import { LoadingSkeleton } from '@/components/LoadingSkeleton';
import {
  useAcceptRescheduleOffer,
  useCancelRescheduleOffer,
  useRequestRescheduleContact,
  useRescheduleOffer,
} from '@/hooks/use-public-data';
import {
  formatAppointmentDateTime,
  formatHoldDeadline,
  getHoldStatusClass,
  getHoldStatusDisplay,
} from '@/lib/doctor-cancellation';
import { getApiErrorMessage } from '@/lib/error-utils';
import { cn } from '@/lib/utils';
import type { RescheduleOffer, RescheduleOfferAppointmentInfo } from '@/types/api';

type RescheduleAction = 'accept' | 'contact' | 'cancel';

const ACTION_COPY: Record<
  RescheduleAction,
  {
    title: string;
    description: string;
    confirmLabel: string;
    success: string;
  }
> = {
  accept: {
    title: 'Xác nhận lịch mới',
    description: 'Quý khách xác nhận sử dụng lịch thay thế đang được phòng khám giữ tạm.',
    confirmLabel: 'Chấp nhận lịch mới',
    success: 'Lịch hẹn mới của Quý khách đã được xác nhận.',
  },
  contact: {
    title: 'Yêu cầu lễ tân liên hệ',
    description: 'Lễ tân sẽ liên hệ với Quý khách để hỗ trợ đổi lịch.',
    confirmLabel: 'Yêu cầu liên hệ',
    success: 'Yêu cầu đã được ghi nhận. Lễ tân sẽ liên hệ để hỗ trợ Quý khách.',
  },
  cancel: {
    title: 'Hủy lịch khám',
    description:
      'Quý khách có chắc muốn hủy lịch khám này không? Slot được giữ sẽ được mở lại cho bệnh nhân khác.',
    confirmLabel: 'Hủy lịch khám',
    success: 'Lịch khám đã được hủy theo yêu cầu của Quý khách.',
  },
};

function isPast(value?: string | null) {
  if (!value) return false;
  const date = new Date(value);
  return !Number.isNaN(date.getTime()) && date.getTime() <= Date.now();
}

function getAxiosStatus(error: unknown) {
  const maybeAxios = error as { response?: { status?: number } };
  return maybeAxios.response?.status;
}

function AppointmentCard({
  title,
  appointment,
  badge,
}: {
  title: string;
  appointment?: RescheduleOfferAppointmentInfo | null;
  badge?: ReactNode;
}) {
  return (
    <Card className="border-border/70 shadow-sm">
      <CardHeader className="pb-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <CardTitle className="text-base">{title}</CardTitle>
          {badge}
        </div>
      </CardHeader>
      <CardContent className="space-y-2 text-sm">
        <InfoRow label="Bác sĩ" value={appointment?.doctorName || 'Chưa có thông tin'} />
        <InfoRow label="Chuyên khoa" value={appointment?.specialtyName || 'Chưa có thông tin'} />
        <InfoRow label="Thời gian" value={formatAppointmentDateTime(appointment)} />
        {appointment?.branchName ? <InfoRow label="Cơ sở" value={appointment.branchName} /> : null}
        {appointment?.status ? <InfoRow label="Trạng thái" value={appointment.status} /> : null}
      </CardContent>
    </Card>
  );
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-start justify-between gap-4">
      <span className="text-muted-foreground">{label}</span>
      <span className="text-right font-medium text-foreground">{value}</span>
    </div>
  );
}

function getOfferState(offer?: RescheduleOffer) {
  if (!offer) return 'INVALID';
  if (offer.status === 'EXPIRED' || isPast(offer.expiresAt)) return 'EXPIRED';
  if (offer.status === 'ACCEPTED') return 'ACCEPTED';
  if (offer.status === 'CONTACT_REQUESTED') return 'CONTACT_REQUESTED';
  if (offer.status === 'CANCELLED') return 'CANCELLED';
  if (offer.status === 'HELD') return 'HELD';
  if (offer.status === 'USED') return 'USED';
  return 'INVALID';
}

export default function RescheduleOfferPage() {
  const { token } = useParams();
  const normalizedToken = token ?? '';
  const [dialogAction, setDialogAction] = useState<RescheduleAction | null>(null);
  const [localResult, setLocalResult] = useState<RescheduleOffer | null>(null);
  const [completedAction, setCompletedAction] = useState<RescheduleAction | null>(null);

  const offerQuery = useRescheduleOffer(normalizedToken);
  const acceptMutation = useAcceptRescheduleOffer();
  const contactMutation = useRequestRescheduleContact();
  const cancelMutation = useCancelRescheduleOffer();
  const activeOffer = localResult ?? offerQuery.data;
  const offerState = completedAction
    ? completedAction === 'accept'
      ? 'ACCEPTED'
      : completedAction === 'contact'
        ? 'CONTACT_REQUESTED'
        : 'CANCELLED'
    : getOfferState(activeOffer);
  const isSubmitting =
    acceptMutation.isPending || contactMutation.isPending || cancelMutation.isPending;
  const dialogCopy = dialogAction ? ACTION_COPY[dialogAction] : null;

  const holdTags = useMemo(() => {
    const tags = ['Đã được giữ tạm cho Quý khách'];
    if (activeOffer?.sameDoctor) tags.push('Cùng bác sĩ');
    if (activeOffer?.sameDay) tags.push('Cùng ngày');
    if (activeOffer?.sameSpecialty) tags.push('Cùng chuyên khoa');
    return tags;
  }, [activeOffer?.sameDay, activeOffer?.sameDoctor, activeOffer?.sameSpecialty]);

  const submitAction = async () => {
    if (!dialogAction || !normalizedToken || isSubmitting) return;
    try {
      const nextOffer =
        dialogAction === 'accept'
          ? await acceptMutation.mutateAsync(normalizedToken)
          : dialogAction === 'contact'
            ? await contactMutation.mutateAsync(normalizedToken)
            : await cancelMutation.mutateAsync(normalizedToken);
      setLocalResult({
        ...(activeOffer ?? nextOffer),
        ...nextOffer,
        originalAppointment: nextOffer.originalAppointment ?? activeOffer?.originalAppointment ?? null,
        proposedAppointment: nextOffer.proposedAppointment ?? activeOffer?.proposedAppointment ?? null,
        acceptedAppointment:
          nextOffer.acceptedAppointment ??
          nextOffer.proposedAppointment ??
          activeOffer?.acceptedAppointment ??
          null,
        expiresAt: nextOffer.expiresAt ?? activeOffer?.expiresAt ?? null,
      });
      setCompletedAction(dialogAction);
      setDialogAction(null);
      toast.success(ACTION_COPY[dialogAction].success);
    } catch (error) {
      setDialogAction(null);
      toast.error(getApiErrorMessage(error, 'Trạng thái lịch giữ chỗ đã thay đổi, vui lòng kiểm tra lại.'));
      await offerQuery.refetch();
    }
  };

  if (offerQuery.isLoading) {
    return (
      <section className="mx-auto w-full max-w-5xl px-4 py-8 sm:px-6">
        <LoadingSkeleton variant="detail" />
      </section>
    );
  }

  if (offerQuery.isError && getAxiosStatus(offerQuery.error) == null) {
    return (
      <section className="mx-auto w-full max-w-4xl px-4 py-8 sm:px-6">
        <ErrorState
          title="Không tải được thông tin hỗ trợ dời lịch"
          description="Kết nối chưa ổn định. Quý khách vui lòng thử lại sau ít phút."
          onRetry={() => void offerQuery.refetch()}
        />
      </section>
    );
  }

  if (offerState === 'EXPIRED') {
    return (
      <section className="mx-auto w-full max-w-4xl px-4 py-8 sm:px-6">
        <EmptyState
          title="Lịch giữ chỗ đã hết thời hạn xác nhận"
          description="Lịch thay thế được giữ cho Quý khách đã hết thời hạn xác nhận. Lễ tân sẽ liên hệ để hỗ trợ Quý khách đặt lịch mới."
          actionLabel="Về trang chủ"
          onAction={() => {
            window.location.href = '/';
          }}
        />
      </section>
    );
  }

  if (!activeOffer || offerState === 'INVALID') {
    return (
      <section className="mx-auto w-full max-w-4xl px-4 py-8 sm:px-6">
        <EmptyState
          title="Liên kết không còn hiệu lực"
          description="Liên kết hỗ trợ dời lịch không hợp lệ hoặc đã được xử lý. Lễ tân sẽ liên hệ nếu lịch hẹn của Quý khách cần hỗ trợ thêm."
          actionLabel="Về trang chủ"
          onAction={() => {
            window.location.href = '/';
          }}
        />
      </section>
    );
  }

  const successMessage =
    completedAction
      ? ACTION_COPY[completedAction].success
      : offerState === 'ACCEPTED'
      ? ACTION_COPY.accept.success
      : offerState === 'CONTACT_REQUESTED'
        ? ACTION_COPY.contact.success
        : offerState === 'CANCELLED'
          ? ACTION_COPY.cancel.success
          : null;

  return (
    <section className="mx-auto w-full max-w-5xl px-4 py-8 sm:px-6">
      <div className="space-y-5">
        <Card className="border-primary/20 bg-primary/5 shadow-sm">
          <CardContent className="flex flex-col gap-4 p-5 sm:flex-row sm:items-start">
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
              <CalendarClock className="h-5 w-5" />
            </span>
            <div className="min-w-0 flex-1">
              <h1 className="text-xl font-semibold tracking-tight text-foreground">
                Lịch hẹn của Quý khách cần được hỗ trợ dời lịch
              </h1>
              <p className="mt-2 text-sm leading-6 text-muted-foreground">
                Lịch hẹn của Quý khách cần được thay đổi do bác sĩ/phòng khám có lịch đột xuất.
                Phòng khám đã giữ tạm cho Quý khách một lịch thay thế.
              </p>
              {activeOffer.patientFullName ? (
                <p className="mt-2 text-sm font-medium text-foreground">
                  Bệnh nhân: {activeOffer.patientFullName}
                </p>
              ) : null}
            </div>
            <Badge className={cn('shrink-0', getHoldStatusClass(activeOffer.status))}>
              {getHoldStatusDisplay(activeOffer.status)}
            </Badge>
          </CardContent>
        </Card>

        {successMessage ? (
          <Card className="border-success/25 bg-success/5 shadow-sm">
            <CardContent className="flex items-start gap-3 p-4 text-sm text-success">
              <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0" />
              <div>
                <p className="font-medium">{successMessage}</p>
                {offerState === 'CONTACT_REQUESTED' && activeOffer.expiresAt ? (
                  <p className="mt-1 text-foreground">
                    Lịch thay thế vẫn đang được giữ cho Quý khách trong thời gian hiệu lực.
                  </p>
                ) : null}
              </div>
            </CardContent>
          </Card>
        ) : null}

        <div className="grid gap-4 lg:grid-cols-2">
          <AppointmentCard
            title="Lịch cũ"
            appointment={activeOffer.originalAppointment}
            badge={<Badge variant="outline">Cần đổi lịch</Badge>}
          />
          <AppointmentCard
            title="Lịch thay thế đang được giữ"
            appointment={activeOffer.acceptedAppointment ?? activeOffer.proposedAppointment}
            badge={<Badge className="border-primary/25 bg-primary/10 text-primary">Đang hỗ trợ</Badge>}
          />
        </div>

        <Card className="border-border/70 shadow-sm">
          <CardContent className="space-y-3 p-5">
            <div className="flex flex-wrap items-center gap-2">
              {holdTags.map((tag) => (
                <Badge key={tag} variant="outline" className="rounded-full">
                  {tag}
                </Badge>
              ))}
            </div>
            <div className="flex items-start gap-3 rounded-lg border border-warning/25 bg-warning/10 px-4 py-3 text-sm text-warning">
              <Clock3 className="mt-0.5 h-4 w-4 shrink-0" />
              <div>
                <p className="font-medium">
                  Lịch này được giữ đến {formatHoldDeadline(activeOffer.expiresAt)}.
                </p>
                <p className="mt-1 text-foreground">
                  Sau thời gian này, lịch giữ chỗ sẽ được mở lại cho bệnh nhân khác.
                </p>
              </div>
            </div>
          </CardContent>
        </Card>

        {offerState === 'HELD' ? (
          <div className="flex flex-col gap-2 rounded-lg border bg-card p-4 shadow-sm sm:flex-row sm:items-center sm:justify-end">
            <Button onClick={() => setDialogAction('accept')} disabled={isSubmitting}>
              <CheckCircle2 className="mr-2 h-4 w-4" />
              Chấp nhận lịch mới
            </Button>
            <Button variant="outline" onClick={() => setDialogAction('contact')} disabled={isSubmitting}>
              <PhoneCall className="mr-2 h-4 w-4" />
              Yêu cầu lễ tân liên hệ
            </Button>
            <Button
              variant="outline"
              className="border-destructive/25 text-destructive hover:bg-destructive/10 hover:text-destructive"
              onClick={() => setDialogAction('cancel')}
              disabled={isSubmitting}
            >
              <XCircle className="mr-2 h-4 w-4" />
              Hủy khám
            </Button>
          </div>
        ) : (
          <div className="flex justify-end">
            <Button asChild variant="outline">
              <Link to="/">Về trang chủ</Link>
            </Button>
          </div>
        )}
      </div>

      <Dialog open={Boolean(dialogAction)} onOpenChange={(open) => !open && setDialogAction(null)}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>{dialogCopy?.title}</DialogTitle>
            <DialogDescription>{dialogCopy?.description}</DialogDescription>
          </DialogHeader>
          <div className="rounded-lg border bg-muted/30 p-3 text-sm">
            <div className="flex items-start gap-2">
              {dialogAction === 'cancel' ? (
                <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-destructive" />
              ) : (
                <Stethoscope className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
              )}
              <p>
                Quý khách có thể xác nhận lịch mới hoặc yêu cầu lễ tân liên hệ hỗ trợ. Đây là
                hỗ trợ chủ động từ phòng khám cho lịch hẹn cần thay đổi.
              </p>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogAction(null)} disabled={isSubmitting}>
              Quay lại
            </Button>
            <Button
              variant={dialogAction === 'cancel' ? 'destructive' : 'default'}
              onClick={() => void submitAction()}
              disabled={isSubmitting}
            >
              {dialogCopy?.confirmLabel}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </section>
  );
}
