import { useEffect, useMemo, useState } from 'react';
import { Download, FileSearch, Loader2, FileText } from 'lucide-react';
import { toast } from 'sonner';
import { useTranslation } from 'react-i18next';
import { Link, useSearchParams } from 'react-router-dom';

import { StatusBadge } from '@/components/StatusBadge';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { InputOTP, InputOTPGroup, InputOTPSlot } from '@/components/ui/input-otp';
import { LookupDetailItem } from '@/components/public/LookupDetailItem';
import { buildApiUrl } from '@/lib/api-url';
import { getApiErrorMessage } from '@/lib/error-utils';
import { Label } from '@/components/ui/label';
import { useRequestResultLookupOtp, useVerifyResultLookupOtp } from '@/hooks/use-public-data';
import type { ResultLookupVerifyResult } from '@/types/api';

export default function ResultLookupPage() {
  const { i18n, t } = useTranslation();
  const [searchParams] = useSearchParams();
  const isEn = i18n.language?.startsWith('en');

  const [resultCode, setResultCode] = useState('');
  const [otp, setOtp] = useState('');
  const [otpDialogOpen, setOtpDialogOpen] = useState(false);
  const [pendingCode, setPendingCode] = useState('');
  const [deliveryHint, setDeliveryHint] = useState<string | null>(null);
  const [resultLookup, setResultLookup] = useState<ResultLookupVerifyResult | null>(null);

  const requestResultOtp = useRequestResultLookupOtp();
  const verifyResultOtp = useVerifyResultLookupOtp();

  const resultPdfUrl = useMemo(() => {
    const lookupCode = resultLookup?.result?.encounterCode || resultLookup?.result?.appointmentCode;
    if (!resultLookup?.accessToken || !lookupCode) return null;
    return buildApiUrl(`/api/public/lookup/results/${encodeURIComponent(lookupCode)}/pdf?token=${encodeURIComponent(resultLookup.accessToken)}`);
  }, [resultLookup]);

  useEffect(() => {
    const focusTarget = searchParams.get('focus');
    if (!focusTarget) return;
    const element = document.getElementById(focusTarget) as HTMLInputElement | null;
    if (!element) return;
    element.scrollIntoView({ behavior: 'smooth', block: 'center' });
    window.setTimeout(() => element.focus(), 180);
  }, [searchParams]);

  const handleRequestOtp = async () => {
    const code = resultCode.trim().toUpperCase();
    if (!code) {
      toast.error(isEn ? 'Please enter a valid lookup code.' : 'Vui lòng nhập mã tra cứu hợp lệ.');
      return;
    }

    try {
      const response = await requestResultOtp.mutateAsync(code);
      setPendingCode(code);
      setOtp('');
      setDeliveryHint(response.maskedDestination ?? null);
      setOtpDialogOpen(true);
      setResultLookup(null);
      toast.success(isEn ? 'OTP sent successfully.' : 'Đã gửi OTP thành công.');
    } catch (error: unknown) {
      toast.error(getApiErrorMessage(error, isEn ? 'Unable to send OTP right now.' : 'Không thể gửi OTP lúc này.'));
    }
  };

  const handleVerifyOtp = async () => {
    if (otp.trim().length !== 6 || !pendingCode) return;
    try {
      const response = await verifyResultOtp.mutateAsync({ code: pendingCode, otp: otp.trim() });
      setResultLookup(response);
      setOtpDialogOpen(false);
      setOtp('');
      toast.success(isEn ? 'OTP verified successfully.' : 'Xác thực OTP thành công.');
    } catch (error: unknown) {
      toast.error(getApiErrorMessage(error, isEn ? 'OTP is invalid or has expired.' : 'OTP không hợp lệ hoặc đã hết hạn.'));
    }
  };

  return (
    <div className="public-page">
      <div className="container-page py-12">
        <div className="mx-auto max-w-2xl">
          <div className="mb-8 text-center">
            <div className="mx-auto mb-3 flex h-14 w-14 items-center justify-center rounded-2xl bg-primary-soft text-primary">
              <FileSearch className="h-7 w-7" />
            </div>
            <h1 className="public-page-title">{isEn ? 'Lookup medical result' : 'Tra cứu kết quả khám'}</h1>
            <p className="public-page-subtitle">
              {isEn
                ? 'Enter an appointment or encounter code to receive an OTP and view available results.'
                : 'Nhập mã lịch hẹn hoặc mã lần khám để nhận OTP và xem kết quả đã có.'}
            </p>
          </div>

          <div className="public-page-card p-6 md:p-8">
            <div className="mb-6 grid grid-cols-2 rounded-lg bg-muted p-1 text-sm font-medium">
              <Link to="/appointments/lookup" className="rounded-md px-3 py-2 text-center text-muted-foreground transition-colors hover:text-primary">
                {isEn ? 'Appointment' : 'Lịch hẹn'}
              </Link>
              <span className="rounded-md bg-card px-3 py-2 text-center text-foreground shadow-sm">
                {isEn ? 'Result' : 'Kết quả'}
              </span>
            </div>

            <div className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="resultLookupCode" className="text-sm font-medium">
                  {isEn ? 'Lookup code' : 'Mã tra cứu kết quả'}
                </Label>
                <Input
                  id="resultLookupCode"
                  value={resultCode}
                  onChange={(e) => setResultCode(e.target.value.toUpperCase())}
                  placeholder={isEn ? 'Appointment / encounter code' : 'Mã lịch / mã lần khám'}
                  className="h-11 rounded-lg font-mono"
                />
                <p className="text-xs text-muted-foreground">
                  {isEn ? 'Use the appointment code or encounter code.' : 'Dùng mã lịch hẹn hoặc mã lần khám.'}
                </p>
              </div>

              <Button
                className="h-11 w-full rounded-lg"
                onClick={() => void handleRequestOtp()}
                disabled={!resultCode.trim() || requestResultOtp.isPending}
              >
                {requestResultOtp.isPending ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <FileSearch className="mr-2 h-4 w-4" />}
                {isEn ? 'Send OTP' : 'Gửi OTP'}
              </Button>
            </div>
          </div>

          <p className="mt-6 flex items-center justify-center gap-2 text-center text-xs text-muted-foreground">
            <FileText className="h-4 w-4 text-success" />
            {isEn ? 'OTP is required before showing result information.' : 'Cần xác thực OTP trước khi hiển thị thông tin kết quả.'}
          </p>
        </div>

        {resultLookup && (
          <div className="mx-auto mt-8 max-w-5xl animate-in fade-in slide-in-from-bottom-2 public-page-card p-5 duration-300 md:p-6">
            <div className="mb-5 flex flex-col gap-3 border-b border-border pb-5 sm:flex-row sm:items-start sm:justify-between">
              <div className="flex min-w-0 items-center gap-3">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-primary-soft text-primary">
                  <FileText className="h-5 w-5" />
                </div>
                <div className="min-w-0">
                  <h2 className="text-lg font-semibold text-foreground">
                    {isEn ? 'Result summary' : 'Tóm tắt kết quả'}
                  </h2>
                  <p className="text-sm leading-6 text-muted-foreground">
                    {isEn ? 'Shown only after OTP verification.' : 'Chỉ hiển thị sau khi xác thực OTP.'}
                  </p>
                </div>
              </div>
              <div className="flex min-w-0 flex-wrap items-center gap-2">
                {resultLookup.result.encounterStatus && (
                  <StatusBadge
                    status={resultLookup.result.encounterStatus}
                    label={t(`status.${resultLookup.result.encounterStatus}`, resultLookup.result.encounterStatus)}
                    className="max-w-full whitespace-normal break-words"
                  />
                )}
                {typeof resultLookup.result.doctorConcluded === 'boolean' && (
                  <Badge
                    variant={resultLookup.result.doctorConcluded ? 'success' : 'secondary'}
                    className="max-w-full whitespace-normal break-words"
                  >
                    {resultLookup.result.doctorConcluded ? (isEn ? 'Doctor concluded' : 'Đã kết luận') : (isEn ? 'Not concluded' : 'Chưa kết luận')}
                  </Badge>
                )}
              </div>
            </div>

            <div className="grid min-w-0 gap-x-5 gap-y-3 sm:grid-cols-2">
              <LookupDetailItem label={isEn ? 'Appointment code' : 'Mã lịch'} value={resultLookup.result.appointmentCode} valueClassName="font-semibold text-primary" />
              <LookupDetailItem label={isEn ? 'Encounter code' : 'Mã lần khám'} value={resultLookup.result.encounterCode} />
              <LookupDetailItem label={isEn ? 'Patient' : 'Bệnh nhân'} value={resultLookup.result.patientFullName} />
              <LookupDetailItem label={isEn ? 'Doctor' : 'Bác sĩ'} value={resultLookup.result.doctorName} />
              <LookupDetailItem label={isEn ? 'Branch' : 'Chi nhánh'} value={resultLookup.result.branchName} />
              <LookupDetailItem label={isEn ? 'Visit date' : 'Ngày khám'} value={resultLookup.result.visitDate} />
              <LookupDetailItem
                label={isEn ? 'Service results' : 'Tổng kết quả dịch vụ'}
                value={typeof resultLookup.result.serviceResultCount === 'number' ? resultLookup.result.serviceResultCount : undefined}
              />
              <LookupDetailItem
                label={isEn ? 'Completed results' : 'Kết quả đã hoàn tất'}
                value={typeof resultLookup.result.completedResultCount === 'number' ? resultLookup.result.completedResultCount : undefined}
                valueClassName="text-primary"
              />
              <LookupDetailItem
                label={isEn ? 'Final diagnosis' : 'Chẩn đoán cuối cùng'}
                value={resultLookup.result.finalDiagnosis}
                className="sm:col-span-2"
              />
              <LookupDetailItem
                label={isEn ? 'Conclusion' : 'Kết luận'}
                value={resultLookup.result.conclusion}
                className="sm:col-span-2"
              />
            </div>

            {resultPdfUrl && resultLookup.result.pdfReady !== false && (
              <div className="mt-5 flex border-t border-border pt-5 sm:justify-end">
                <Button asChild className="h-10 w-full rounded-lg px-4 sm:w-auto">
                  <a href={resultPdfUrl} target="_blank" rel="noreferrer">
                    <Download className="mr-2 h-4 w-4" />
                    {isEn ? 'Download PDF' : 'Tải PDF'}
                  </a>
                </Button>
              </div>
            )}
          </div>
        )}

        <Dialog open={otpDialogOpen} onOpenChange={(open) => { setOtpDialogOpen(open); if (!open) setOtp(''); }}>
          <DialogContent className="flex max-h-[88vh] w-[calc(100vw-1rem)] max-w-[420px] flex-col gap-0 overflow-hidden rounded-xl border border-border bg-background p-0 shadow-card sm:w-full">
            <div className="shrink-0 border-b border-border bg-primary/5 px-4 py-3.5 pr-12 sm:px-5">
              <div className="flex items-start gap-3">
                <div className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
                  <FileSearch className="h-4 w-4" />
                </div>
                <div className="min-w-0">
                  <DialogTitle className="text-base font-semibold leading-6">{isEn ? 'Verify OTP' : 'Xác thực OTP'}</DialogTitle>
                  <DialogDescription className="mt-1 text-sm leading-5">
                    {isEn ? 'Enter the 6-digit OTP to unlock the result summary.' : 'Nhập OTP 6 số để mở tóm tắt kết quả.'}
                  </DialogDescription>
                </div>
              </div>
            </div>

            <div className="min-h-0 flex-1 space-y-3 overflow-y-auto bg-background p-4 sm:p-5">
              {deliveryHint && (
                <div className="rounded-lg border border-border bg-muted/35 px-3 py-2.5">
                  <p className="text-xs leading-5 text-muted-foreground">{isEn ? 'OTP destination' : 'Kênh nhận OTP'}</p>
                  <p className="break-all text-sm font-semibold leading-5 text-foreground">{deliveryHint}</p>
                </div>
              )}

              <div className="space-y-2.5">
                <Label className="text-sm font-semibold">{isEn ? 'OTP code' : 'Mã OTP'}</Label>
                <InputOTP maxLength={6} value={otp} onChange={setOtp} containerClassName="w-full justify-between gap-1.5">
                  <InputOTPGroup className="w-full justify-between gap-1.5">
                    {[...Array(6)].map((_, i) => (
                      <InputOTPSlot key={i} index={i} className="h-10 w-9 rounded-lg border border-input bg-background text-base font-bold shadow-sm sm:w-10" />
                    ))}
                  </InputOTPGroup>
                </InputOTP>
              </div>
            </div>

            <DialogFooter className="shrink-0 flex-col gap-2 border-t border-border bg-background p-4 sm:flex-row sm:justify-between">
              <Button variant="ghost" onClick={() => setOtpDialogOpen(false)} className="h-10 w-full rounded-lg px-4 sm:w-auto">{isEn ? 'Close' : 'Đóng'}</Button>
              <Button 
                onClick={() => void handleVerifyOtp()} 
                disabled={otp.length !== 6 || verifyResultOtp.isPending}
                className="h-10 w-full rounded-lg px-5 sm:w-auto"
              >
                {verifyResultOtp.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                {isEn ? 'Verify OTP' : 'Xác thực OTP'}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>
    </div>
  );
}
