import { useEffect, useMemo, useState } from 'react';
import { Activity, CalendarSearch, Download, Loader2, Search } from 'lucide-react';
import { toast } from 'sonner';
import { useTranslation } from 'react-i18next';
import { Link, useSearchParams } from 'react-router-dom';

import { StatusBadge } from '@/components/StatusBadge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { LookupDetailItem } from '@/components/public/LookupDetailItem';
import { OtpVerificationDialog } from '@/components/public/OtpVerificationDialog';
import { buildApiUrl } from '@/lib/api-url';
import { getApiErrorMessage } from '@/lib/error-utils';
import { Label } from '@/components/ui/label';
import { useCancelAppointmentLookup, useRequestAppointmentLookupOtp, useVerifyAppointmentLookupOtp } from '@/hooks/use-public-data';
import type { AppointmentLookupVerifyResult, PublicLookupOtpResult } from '@/types/api';

const DEFAULT_OTP_EXPIRES_IN_SECONDS = 300;
const DEFAULT_RESEND_AVAILABLE_IN_SECONDS = 30;

type OtpNoticeKind = 'sent' | 'resent';

function normalizeLookupOtpChannel(response?: Pick<PublicLookupOtpResult, 'channel' | 'deliveryChannel'>) {
  return (response?.channel || response?.deliveryChannel || '').trim().toUpperCase();
}

function isMaskedEmail(destination?: string | null) {
  return Boolean(destination?.includes('@'));
}

function readSeconds(value: unknown) {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return Math.max(0, Math.ceil(value));
  }

  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) return Math.max(0, Math.ceil(parsed));
  }

  return undefined;
}

function secondsOrDefault(value: unknown, fallback: number) {
  return readSeconds(value) ?? fallback;
}

function getOtpDeliveryMessage(channel: string | null, destination: string | null, kind: OtpNoticeKind, isEn: boolean) {
  const isEmailDelivery = channel === 'EMAIL' || isMaskedEmail(destination) || (!channel && !destination);
  const emailTargetVi = destination ? ` tới ${destination}` : ' tới email đã đăng ký';
  const emailTargetEn = destination ? ` to ${destination}` : ' to the registered email address';

  if (isEmailDelivery) {
    if (kind === 'resent') {
      return isEn
        ? `A new OTP has been sent by email${emailTargetEn}.`
        : `Mã OTP mới đã được gửi qua email${emailTargetVi}.`;
    }

    return isEn
      ? `The OTP has been sent by email${emailTargetEn}.`
      : `OTP đã được gửi qua email${emailTargetVi}.`;
  }

  if (kind === 'resent') {
    return isEn
      ? 'A new OTP has been sent to the registered verification channel.'
      : 'Mã OTP mới đã được gửi tới kênh xác thực đã đăng ký.';
  }

  return isEn
    ? 'The OTP has been sent to the registered verification channel.'
    : 'OTP đã được gửi tới kênh xác thực đã đăng ký.';
}

function getNestedRecord(value: unknown) {
  return value && typeof value === 'object' ? value as Record<string, unknown> : undefined;
}

function getResendCooldownSeconds(error: unknown) {
  const responseData = (error as { response?: { data?: unknown } }).response?.data;
  const responseRecord = getNestedRecord(responseData);
  const detailsRecord = getNestedRecord(responseRecord?.details);
  const dataRecord = getNestedRecord(responseRecord?.data);

  const candidates = [
    responseRecord?.resendAvailableInSeconds,
    responseRecord?.retryAfterSeconds,
    responseRecord?.cooldownSeconds,
    dataRecord?.resendAvailableInSeconds,
    dataRecord?.retryAfterSeconds,
    dataRecord?.cooldownSeconds,
    detailsRecord?.resendAvailableInSeconds,
    detailsRecord?.retryAfterSeconds,
    detailsRecord?.cooldownSeconds,
  ];

  for (const candidate of candidates) {
    const seconds = readSeconds(candidate);
    if (typeof seconds === 'number') return seconds;
  }

  return undefined;
}

function getErrorStatus(error: unknown) {
  return (error as { response?: { status?: number } }).response?.status;
}

function getOtpVerifyFeedback(error: unknown, isEn: boolean) {
  const rawMessage = getApiErrorMessage(error, '');
  const responseData = (error as { response?: { data?: unknown } }).response?.data;
  const status = getErrorStatus(error);
  const responseRecord = getNestedRecord(responseData);
  const detailsRecord = getNestedRecord(responseRecord?.details);
  const code = [responseRecord?.code, responseRecord?.errorCode, detailsRecord?.code]
    .filter((value): value is string => typeof value === 'string')
    .join(' ');
  const normalizedCode = code.toUpperCase();
  const normalized = `${code} ${rawMessage}`.toLowerCase();
  const isExpired = normalized.includes('expired') || normalized.includes('expire') || normalized.includes('hết hạn') || normalized.includes('het han') || normalized.includes('hết hiệu lực') || normalized.includes('het hieu luc');
  const isLocked = normalizedCode.includes('PUBLIC_LOOKUP_OTP_LOCKED') || normalized.includes('locked') || normalized.includes('bị khóa') || normalized.includes('bi khoa');
  const isInvalid = normalized.includes('invalid') || normalized.includes('incorrect') || normalized.includes('wrong') || normalized.includes('không đúng') || normalized.includes('khong dung') || normalized.includes('không hợp lệ') || normalized.includes('khong hop le');

  if (isExpired) {
    return {
      expired: true,
      message: isEn
        ? 'The OTP has expired. Please request a new code.'
        : 'Mã OTP đã hết hạn. Vui lòng gửi lại mã mới.',
    };
  }

  if (isLocked) {
    return {
      expired: true,
      message: isEn
        ? 'The OTP has been locked after too many incorrect attempts. Please request a new code.'
        : 'Mã OTP đã bị khóa do nhập sai quá nhiều lần. Vui lòng gửi lại mã mới.',
    };
  }

  if (isInvalid || status === 401 || !rawMessage) {
    return {
      expired: false,
      message: isEn
        ? 'The OTP is incorrect. Please check it again.'
        : 'Mã OTP không đúng. Vui lòng kiểm tra lại.',
    };
  }

  return { expired: false, message: rawMessage };
}

export default function AppointmentLookupPage() {
  const { i18n, t } = useTranslation();
  const [searchParams] = useSearchParams();
  const isEn = i18n.language?.startsWith('en');

  const [appointmentCode, setAppointmentCode] = useState('');
  const [otp, setOtp] = useState('');
  const [otpDialogOpen, setOtpDialogOpen] = useState(false);
  const [pendingCode, setPendingCode] = useState('');
  const [deliveryHint, setDeliveryHint] = useState<string | null>(null);
  const [otpChannel, setOtpChannel] = useState<string | null>(null);
  const [otpNoticeKind, setOtpNoticeKind] = useState<OtpNoticeKind>('sent');
  const [otpSecondsRemaining, setOtpSecondsRemaining] = useState(DEFAULT_OTP_EXPIRES_IN_SECONDS);
  const [resendSecondsRemaining, setResendSecondsRemaining] = useState(0);
  const [resendErrorActive, setResendErrorActive] = useState(false);
  const [otpError, setOtpError] = useState<string | null>(null);
  const [appointmentLookup, setAppointmentLookup] = useState<AppointmentLookupVerifyResult | null>(null);
  const [cancelReason, setCancelReason] = useState('');

  const requestAppointmentOtp = useRequestAppointmentLookupOtp();
  const cancelAppointmentLookup = useCancelAppointmentLookup();
  const verifyAppointmentOtp = useVerifyAppointmentLookupOtp();

  const activeRequestPending = requestAppointmentOtp.isPending;
  const resendDisabled = resendSecondsRemaining > 0 || requestAppointmentOtp.isPending || !pendingCode;
  const deliveryMessage = getOtpDeliveryMessage(otpChannel, deliveryHint, otpNoticeKind, isEn);
  const resendCountdownMessage = resendErrorActive
    ? isEn
      ? `Please wait ${resendSecondsRemaining} seconds before requesting another OTP.`
      : `Vui lòng chờ ${resendSecondsRemaining} giây trước khi gửi lại mã OTP.`
    : isEn
      ? `You can request another code in ${resendSecondsRemaining} seconds.`
      : `Có thể gửi lại sau ${resendSecondsRemaining} giây`;
  const cancellableStatuses = new Set(['REQUESTED', 'CONFIRMED']);
  const canCancelAppointment = Boolean(appointmentLookup?.accessToken && appointmentLookup?.appointment?.status && cancellableStatuses.has(appointmentLookup.appointment.status));

  const appointmentPdfUrl = useMemo(() => {
    if (!appointmentLookup?.accessToken || !appointmentLookup.appointment?.code) return null;
    return buildApiUrl(`/api/public/lookup/appointments/${encodeURIComponent(appointmentLookup.appointment.code)}/pdf?token=${encodeURIComponent(appointmentLookup.accessToken)}`);
  }, [appointmentLookup]);

  useEffect(() => {
    const focusTarget = searchParams.get('focus');
    if (!focusTarget) return;
    const element = document.getElementById(focusTarget) as HTMLInputElement | null;
    if (!element) return;
    element.scrollIntoView({ behavior: 'smooth', block: 'center' });
    window.setTimeout(() => element.focus(), 180);
  }, [searchParams]);

  useEffect(() => {
    if (!otpDialogOpen || (otpSecondsRemaining <= 0 && resendSecondsRemaining <= 0)) return;

    const timer = window.setInterval(() => {
      setOtpSecondsRemaining((seconds) => Math.max(0, seconds - 1));
      setResendSecondsRemaining((seconds) => Math.max(0, seconds - 1));
    }, 1000);

    return () => window.clearInterval(timer);
  }, [otpDialogOpen, otpSecondsRemaining, resendSecondsRemaining]);

  const applyOtpRequestResult = (response: PublicLookupOtpResult, code: string, noticeKind: OtpNoticeKind) => {
    setPendingCode(code);
    setOtp('');
    setOtpError(null);
    setDeliveryHint(response.maskedDestination ?? null);
    setOtpChannel(normalizeLookupOtpChannel(response) || null);
    setOtpNoticeKind(noticeKind);
    setOtpSecondsRemaining(secondsOrDefault(response.expiresInSeconds, DEFAULT_OTP_EXPIRES_IN_SECONDS));
    setResendSecondsRemaining(secondsOrDefault(response.resendAvailableInSeconds, DEFAULT_RESEND_AVAILABLE_IN_SECONDS));
    setResendErrorActive(false);
    setOtpDialogOpen(true);
    setAppointmentLookup(null);
  };

  const handleOtpRequestError = (error: unknown, fallback: string) => {
    const cooldownSeconds = getResendCooldownSeconds(error);
    const isCooldown = typeof cooldownSeconds === 'number' || getErrorStatus(error) === 429;

    if (isCooldown) {
      const nextCooldown = cooldownSeconds ?? Math.max(resendSecondsRemaining, DEFAULT_RESEND_AVAILABLE_IN_SECONDS);
      setResendSecondsRemaining(nextCooldown);
      setResendErrorActive(true);
      toast.error(isEn ? `Please wait ${nextCooldown} seconds before requesting another OTP.` : `Vui lòng chờ ${nextCooldown} giây trước khi gửi lại mã OTP.`);
      return;
    }

    toast.error(getApiErrorMessage(error, fallback));
  };

  const handleRequestOtp = async () => {
    const code = appointmentCode.trim().toUpperCase();
    if (!code) {
      toast.error(isEn ? 'Please enter a valid appointment code.' : 'Vui lòng nhập mã lịch hẹn hợp lệ.');
      return;
    }

    try {
      const response = await requestAppointmentOtp.mutateAsync(code);
      applyOtpRequestResult(response, code, 'sent');
      toast.success(getOtpDeliveryMessage(normalizeLookupOtpChannel(response) || null, response.maskedDestination ?? null, 'sent', isEn));
    } catch (error: unknown) {
      handleOtpRequestError(error, isEn ? 'Unable to send OTP right now.' : 'Không thể gửi OTP lúc này.');
    }
  };

  const handleResendOtp = async () => {
    const code = pendingCode || appointmentCode.trim().toUpperCase();
    if (!code) return;

    try {
      const response = await requestAppointmentOtp.mutateAsync(code);
      applyOtpRequestResult(response, code, 'resent');
      toast.success(getOtpDeliveryMessage(normalizeLookupOtpChannel(response) || null, response.maskedDestination ?? null, 'resent', isEn));
    } catch (error: unknown) {
      handleOtpRequestError(error, isEn ? 'Unable to send another OTP right now.' : 'Chưa thể gửi lại mã OTP lúc này.');
    }
  };

  const handleOtpChange = (value: string) => {
    setOtp(value);
    if (otpError) setOtpError(null);
  };

  const handleVerifyOtp = async () => {
    if (otp.trim().length !== 6 || !pendingCode) return;
    try {
      const response = await verifyAppointmentOtp.mutateAsync({ code: pendingCode, otp: otp.trim() });
      setAppointmentLookup(response);
      setOtpDialogOpen(false);
      setOtp('');
      setOtpError(null);
      toast.success(isEn ? 'OTP verified successfully.' : 'Xác thực OTP thành công.');
    } catch (error: unknown) {
      const feedback = getOtpVerifyFeedback(error, isEn);
      if (feedback.expired) setOtpSecondsRemaining(0);
      setOtpError(feedback.message);
      toast.error(feedback.message);
    }
  };

  const handleCancelAppointment = async () => {
    if (!appointmentLookup?.accessToken || !appointmentLookup?.appointment?.code) return;

    try {
      const response = await cancelAppointmentLookup.mutateAsync({
        code: appointmentLookup.appointment.code,
        token: appointmentLookup.accessToken,
        reason: cancelReason.trim() || undefined,
      });

      setAppointmentLookup((prev) => prev ? ({ ...prev, appointment: { ...prev.appointment, status: response.status || 'CANCELLED' } }) : prev);
      toast.success(response.message || (isEn ? 'Appointment cancelled successfully.' : 'Đã hủy lịch hẹn thành công.'));
    } catch (error: unknown) {
      toast.error(getApiErrorMessage(error, isEn ? 'Unable to cancel this appointment right now.' : 'Chưa thể hủy lịch hẹn lúc này.'));
    }
  };

  return (
    <div className="public-page">
      <div className="container-page py-12">
        <div className="mx-auto max-w-2xl">
          <div className="mb-8 text-center">
            <div className="mx-auto mb-3 flex h-14 w-14 items-center justify-center rounded-2xl bg-primary-soft text-primary">
              <CalendarSearch className="h-7 w-7" />
            </div>
            <h1 className="public-page-title">{isEn ? 'Lookup appointment' : 'Tra cứu phiếu hẹn'}</h1>
            <p className="public-page-subtitle">
              {isEn
                ? 'Enter your appointment code to receive an OTP and reopen the appointment slip.'
                : 'Nhập mã lịch hẹn để nhận OTP và mở lại phiếu hẹn.'}
            </p>
          </div>

          <div className="public-page-card p-6 md:p-8">
            <div className="mb-6 grid grid-cols-2 rounded-lg bg-muted p-1 text-sm font-medium">
              <span className="rounded-md bg-card px-3 py-2 text-center text-foreground shadow-sm">
                {isEn ? 'Appointment' : 'Lịch hẹn'}
              </span>
              <Link to="/results/lookup" className="rounded-md px-3 py-2 text-center text-muted-foreground transition-colors hover:text-primary">
                {isEn ? 'Result' : 'Kết quả'}
              </Link>
            </div>

            <div className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="appointmentLookupCode" className="text-sm font-medium">
                  {isEn ? 'Appointment code' : 'Mã lịch hẹn'}
                </Label>
                <Input
                  id="appointmentLookupCode"
                  value={appointmentCode}
                  onChange={(e) => setAppointmentCode(e.target.value.toUpperCase())}
                  placeholder="VD: PC123ABC"
                  className="h-11 rounded-lg font-mono"
                />
                <p className="text-xs text-muted-foreground">
                  {isEn ? 'Use the code on your booking confirmation.' : 'Dùng mã trên xác nhận đặt lịch của bạn.'}
                </p>
              </div>

              <Button
                className="h-11 w-full rounded-lg"
                onClick={() => void handleRequestOtp()}
                disabled={!appointmentCode.trim() || activeRequestPending}
              >
                {requestAppointmentOtp.isPending ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Search className="mr-2 h-4 w-4" />}
                {isEn ? 'Send OTP' : 'Gửi OTP'}
              </Button>
            </div>
          </div>

          <p className="mt-6 flex items-center justify-center gap-2 text-center text-xs text-muted-foreground">
            <Activity className="h-4 w-4 text-success" />
            {isEn ? 'OTP is time-limited and required before showing private information.' : 'Mã OTP có hiệu lực trong thời gian ngắn trước khi hiển thị thông tin riêng tư.'}
          </p>
        </div>

        {appointmentLookup && (
          <div className="mx-auto mt-8 max-w-5xl animate-in fade-in slide-in-from-bottom-2 public-page-card p-5 duration-300 md:p-6">
            <div className="mb-5 flex flex-col gap-3 border-b border-border pb-5 sm:flex-row sm:items-start sm:justify-between">
              <div className="flex min-w-0 items-center gap-3">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-primary-soft text-primary">
                  <Activity className="h-5 w-5" />
                </div>
                <div className="min-w-0">
                  <h2 className="text-lg font-semibold text-foreground">
                    {isEn ? 'Appointment details' : 'Chi tiết phiếu hẹn'}
                  </h2>
                  <p className="text-sm leading-6 text-muted-foreground">
                    {isEn ? 'Check date, doctor, branch, and available actions.' : 'Kiểm tra ngày khám, bác sĩ, cơ sở và thao tác khả dụng.'}
                  </p>
                </div>
              </div>
              {appointmentLookup.appointment.status ? (
                <StatusBadge
                  status={appointmentLookup.appointment.status}
                  label={t(`status.${appointmentLookup.appointment.status}`, appointmentLookup.appointment.status)}
                  className="max-w-full whitespace-normal break-words"
                />
              ) : (
                <span className="inline-flex max-w-full items-center rounded-full border border-border bg-muted px-2.5 py-1 text-xs font-medium text-muted-foreground">
                  {isEn ? 'Unknown' : 'Không rõ'}
                </span>
              )}
            </div>

            <div className="grid min-w-0 gap-x-5 gap-y-3 sm:grid-cols-2">
              <LookupDetailItem label={isEn ? 'Code' : 'Mã lịch'} value={appointmentLookup.appointment.code} valueClassName="font-semibold text-primary" />
              <LookupDetailItem label={isEn ? 'Patient' : 'Bệnh nhân'} value={appointmentLookup.appointment.patientFullName} />
              <LookupDetailItem label={isEn ? 'Doctor' : 'Bác sĩ'} value={appointmentLookup.appointment.doctorName} />
              <LookupDetailItem label={isEn ? 'Specialty' : 'Chuyên khoa'} value={appointmentLookup.appointment.specialtyName} />
              <LookupDetailItem label={isEn ? 'Branch' : 'Chi nhánh'} value={appointmentLookup.appointment.branchName} />
              <LookupDetailItem label={isEn ? 'Visit date' : 'Ngày khám'} value={appointmentLookup.appointment.visitDate} />
              <LookupDetailItem label={isEn ? 'Time' : 'Giờ khám'} value={appointmentLookup.appointment.slotRange} />
              <LookupDetailItem
                label={isEn ? 'Projected queue' : 'STT dự kiến'}
                value={typeof appointmentLookup.appointment.queueNo === 'number' ? appointmentLookup.appointment.queueNo : undefined}
                valueClassName="text-primary"
              />
            </div>

            <div className="mt-5 flex flex-col gap-3 border-t border-border pt-5 sm:flex-row sm:items-center sm:justify-between">
              <div className="text-sm leading-5 text-muted-foreground">
                {isEn ? 'Appointment slip' : 'Phiếu hẹn'}
              </div>
              {appointmentPdfUrl && appointmentLookup.appointment.pdfReady !== false && (
                <Button asChild className="h-10 w-full rounded-lg px-4 sm:w-auto">
                  <a href={appointmentPdfUrl} target="_blank" rel="noreferrer">
                    <Download className="mr-2 h-4 w-4" />
                    {isEn ? 'Download PDF' : 'Tải PDF'}
                  </a>
                </Button>
              )}
            </div>

            <div className="mt-5 border-t border-border pt-5">
              <div className="mb-3 min-w-0">
                <h3 className="text-sm font-semibold text-foreground">{isEn ? 'Cancel appointment' : 'Hủy lịch hẹn'}</h3>
                <p className="mt-1 break-words text-xs leading-5 text-muted-foreground [overflow-wrap:anywhere]">
                  {canCancelAppointment
                    ? isEn
                      ? 'Available while the appointment is requested or confirmed. Reason is optional.'
                      : 'Khả dụng khi lịch đang chờ xác nhận hoặc đã xác nhận. Lý do không bắt buộc.'
                    : isEn
                      ? 'Online cancellation is unavailable for this current status.'
                      : 'Không thể hủy trực tuyến ở trạng thái hiện tại.'}
                </p>
              </div>
              <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-start">
                <Textarea
                  value={cancelReason}
                  onChange={(event) => setCancelReason(event.target.value)}
                  rows={2}
                  placeholder={isEn ? 'Reason for cancellation (optional)' : 'Lý do hủy lịch (không bắt buộc)'}
                  disabled={!canCancelAppointment || cancelAppointmentLookup.isPending}
                  className="min-h-[72px] resize-none rounded-lg"
                />
                <Button
                  variant="destructive"
                  className="h-10 w-full rounded-lg px-4 sm:w-auto"
                  onClick={() => void handleCancelAppointment()}
                  disabled={!canCancelAppointment || cancelAppointmentLookup.isPending}
                >
                  {cancelAppointmentLookup.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                  {isEn ? 'Cancel appointment' : 'Hủy lịch hẹn'}
                </Button>
              </div>
            </div>
          </div>
        )}

        <OtpVerificationDialog
          open={otpDialogOpen}
          onOpenChange={(open) => {
            setOtpDialogOpen(open);
            if (!open) {
              setOtp('');
              setOtpError(null);
              setResendErrorActive(false);
            }
          }}
          isEn={isEn}
          otp={otp}
          onOtpChange={handleOtpChange}
          otpError={otpError}
          otpSecondsRemaining={otpSecondsRemaining}
          deliveryMessage={deliveryMessage}
          resendSecondsRemaining={resendSecondsRemaining}
          resendCountdownMessage={resendCountdownMessage}
          resendDisabled={resendDisabled}
          resendPending={requestAppointmentOtp.isPending}
          verifyPending={verifyAppointmentOtp.isPending}
          onResend={() => void handleResendOtp()}
          onVerify={() => void handleVerifyOtp()}
        />
      </div>
    </div>
  );
}
