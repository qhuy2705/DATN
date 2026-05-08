import { useId, useRef, type KeyboardEvent } from 'react';
import { REGEXP_ONLY_DIGITS } from 'input-otp';
import { Clock3, Loader2, Mail, RefreshCw } from 'lucide-react';

import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from '@/components/ui/dialog';
import { InputOTP, InputOTPGroup, InputOTPSlot } from '@/components/ui/input-otp';
import { Label } from '@/components/ui/label';

type OtpVerificationDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  isEn: boolean;
  otp: string;
  onOtpChange: (value: string) => void;
  otpError: string | null;
  otpSecondsRemaining: number;
  deliveryMessage: string;
  resendSecondsRemaining: number;
  resendCountdownMessage: string;
  resendDisabled: boolean;
  resendPending: boolean;
  verifyPending: boolean;
  onResend: () => void;
  onVerify: () => void;
};

function formatCountdown(seconds: number) {
  const normalizedSeconds = Math.max(0, Math.floor(seconds));
  const minutes = Math.floor(normalizedSeconds / 60).toString().padStart(2, '0');
  const remainder = (normalizedSeconds % 60).toString().padStart(2, '0');
  return `${minutes}:${remainder}`;
}

export function OtpVerificationDialog({
  open,
  onOpenChange,
  isEn,
  otp,
  onOtpChange,
  otpError,
  otpSecondsRemaining,
  deliveryMessage,
  resendSecondsRemaining,
  resendCountdownMessage,
  resendDisabled,
  resendPending,
  verifyPending,
  onResend,
  onVerify,
}: OtpVerificationDialogProps) {
  const otpInputRef = useRef<HTMLInputElement>(null);
  const otpErrorId = useId();
  const otpHelpId = useId();
  const isOtpComplete = otp.trim().length === 6;
  const canSubmit = isOtpComplete && !verifyPending;
  const isExpired = otpSecondsRemaining === 0;

  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key !== 'Enter' || !canSubmit) return;
    event.preventDefault();
    onVerify();
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        className="flex max-h-[88vh] w-[calc(100vw-1rem)] max-w-[440px] flex-col gap-0 overflow-hidden rounded-lg border border-border bg-background p-0 shadow-card sm:w-full"
        onOpenAutoFocus={(event) => {
          event.preventDefault();
          window.setTimeout(() => otpInputRef.current?.focus(), 0);
        }}
      >
        <div className="shrink-0 border-b border-border/60 bg-primary/5 px-4 py-3.5 pr-14 sm:px-5">
          <div className="flex items-start gap-3">
            <div className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
              <Mail className="h-4 w-4" />
            </div>
            <div className="min-w-0">
              <DialogTitle className="text-base font-semibold leading-6">
                {isEn ? 'Verify OTP' : 'Xác thực OTP'}
              </DialogTitle>
              <DialogDescription className="mt-1 text-sm leading-5">
                {isEn
                  ? 'Enter the 6-digit OTP sent to your email.'
                  : 'Nhập mã OTP 6 số đã được gửi tới email của bạn.'}
              </DialogDescription>
            </div>
          </div>
        </div>

        <div className="min-h-0 flex-1 space-y-3 overflow-y-auto overscroll-contain bg-background px-4 py-3.5 sm:px-5">
          <div className="rounded-lg border border-primary/10 bg-primary/5 px-3 py-2.5">
            <div className="flex items-start gap-2.5">
              <Mail className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
              <p className="text-sm font-medium leading-5 text-foreground">{deliveryMessage}</p>
            </div>
          </div>

          <div className="rounded-lg border border-border/70 bg-muted/35 px-3 py-2.5">
            <div className="flex items-center justify-between gap-3">
              <div className="flex min-w-0 items-center gap-2 text-sm font-medium text-foreground">
                <Clock3 className="h-4 w-4 shrink-0 text-primary" />
                <span>
                  {isEn ? 'OTP is valid for 5 minutes' : 'Mã OTP có hiệu lực trong 5 phút'}
                </span>
              </div>
              <span className="shrink-0 text-base font-semibold tabular-nums text-primary">
                {formatCountdown(otpSecondsRemaining)}
              </span>
            </div>
            {isExpired && (
              <p className="mt-2 text-sm font-medium text-destructive">
                {isEn
                  ? 'The OTP has expired. Please request a new code.'
                  : 'Mã OTP đã hết hạn. Vui lòng gửi lại mã mới.'}
              </p>
            )}
          </div>

          <div className="space-y-2.5">
            <Label htmlFor="appointment-lookup-otp" className="text-sm font-semibold">
              {isEn ? 'OTP code' : 'Mã OTP'}
            </Label>
            <InputOTP
              ref={otpInputRef}
              id="appointment-lookup-otp"
              maxLength={6}
              value={otp}
              onChange={onOtpChange}
              onKeyDown={handleKeyDown}
              pattern={REGEXP_ONLY_DIGITS}
              inputMode="numeric"
              autoComplete="one-time-code"
              aria-invalid={Boolean(otpError)}
              aria-describedby={otpError ? otpErrorId : otpHelpId}
              pasteTransformer={(pastedText) => pastedText.replace(/\D/g, '').slice(0, 6)}
              containerClassName="w-full justify-between gap-1.5"
            >
              <InputOTPGroup className="w-full justify-between gap-1.5">
                {[...Array(6)].map((_, index) => (
                  <InputOTPSlot
                    key={index}
                    index={index}
                    className="h-10 w-10 rounded-lg border border-input bg-background text-base font-semibold shadow-sm"
                  />
                ))}
              </InputOTPGroup>
            </InputOTP>
            <p id={otpHelpId} className="sr-only">
              {isEn ? 'Enter 6 digits.' : 'Nhập đủ 6 chữ số.'}
            </p>
            {otpError && (
              <p id={otpErrorId} role="alert" className="text-sm font-medium leading-5 text-destructive">
                {otpError}
              </p>
            )}
          </div>
        </div>

        <div className="shrink-0 space-y-3 border-t border-border/60 bg-background px-4 py-3.5 sm:px-5">
          <div className="grid grid-cols-2 gap-2">
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
              className="h-10 rounded-lg"
            >
              {isEn ? 'Cancel' : 'Hủy'}
            </Button>
            <Button
              type="button"
              onClick={onVerify}
              disabled={!canSubmit}
              className="h-10 rounded-lg shadow-soft"
            >
              {verifyPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              {isEn ? 'Confirm' : 'Xác nhận'}
            </Button>
          </div>

          <div className="flex items-center justify-between gap-3 rounded-lg bg-muted/35 px-3 py-2.5">
            <div className="min-w-0">
              <p className="text-sm font-semibold leading-5 text-foreground">
                {isEn ? 'Need a new OTP?' : 'Cần mã OTP mới?'}
              </p>
              <p className="text-xs leading-5 text-muted-foreground">
                {resendSecondsRemaining > 0
                  ? resendCountdownMessage
                  : isEn
                    ? 'You can request a new code now.'
                    : 'Có thể gửi lại mã ngay bây giờ.'}
              </p>
            </div>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={onResend}
              disabled={resendDisabled}
              className="h-9 shrink-0 rounded-md px-3"
            >
              {resendPending ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <RefreshCw className="mr-2 h-4 w-4" />}
              {isEn ? 'Resend' : 'Gửi lại mã'}
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
