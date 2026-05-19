import { useEffect, useRef, useState } from 'react';
import { BrowserQRCodeReader, type IScannerControls } from '@zxing/browser';
import { cn } from '@/lib/utils';

type QrCheckInScannerProps = {
  active: boolean;
  onDetected: (value: string) => void;
  onError?: (message: string) => void;
  className?: string;
};

function stopVideoStream(video: HTMLVideoElement | null) {
  const stream = video?.srcObject;
  if (typeof MediaStream !== 'undefined' && stream instanceof MediaStream) {
    stream.getTracks().forEach((track) => track.stop());
  }

  if (video) {
    video.pause();
    video.srcObject = null;
  }
}

function getCameraErrorMessage(error: unknown) {
  const name = (error as { name?: string })?.name;

  if (name === 'NotAllowedError' || name === 'PermissionDeniedError') {
    return 'Trình duyệt chưa được cấp quyền dùng webcam. Vui lòng cho phép camera rồi thử lại.';
  }

  if (name === 'NotFoundError' || name === 'DevicesNotFoundError') {
    return 'Không tìm thấy webcam trên thiết bị này. Bạn vẫn có thể nhập mã thủ công.';
  }

  return 'Không thể bật webcam. Vui lòng thử lại hoặc nhập mã thủ công.';
}

export function QrCheckInScanner({
  active,
  onDetected,
  onError,
  className,
}: QrCheckInScannerProps) {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const controlsRef = useRef<IScannerControls | null>(null);
  const detectedRef = useRef(false);
  const onDetectedRef = useRef(onDetected);
  const onErrorRef = useRef(onError);
  const [isStarting, setIsStarting] = useState(false);

  useEffect(() => {
    onDetectedRef.current = onDetected;
  }, [onDetected]);

  useEffect(() => {
    onErrorRef.current = onError;
  }, [onError]);

  useEffect(() => {
    const video = videoRef.current;

    const stopScanner = () => {
      controlsRef.current?.stop();
      controlsRef.current = null;
      stopVideoStream(video);
    };

    if (!active) {
      detectedRef.current = false;
      setIsStarting(false);
      stopScanner();
      return undefined;
    }

    if (!video) return undefined;

    if (typeof navigator === 'undefined' || !navigator.mediaDevices?.getUserMedia) {
      onErrorRef.current?.('Trình duyệt không hỗ trợ mở webcam. Vui lòng nhập mã thủ công.');
      return undefined;
    }

    let cancelled = false;
    detectedRef.current = false;
    setIsStarting(true);

    const reader = new BrowserQRCodeReader();

    reader
      .decodeFromVideoDevice(undefined, video, (result, _error, controls) => {
        if (result && !detectedRef.current) {
          detectedRef.current = true;
          controls.stop();
          controlsRef.current = null;
          stopVideoStream(video);
          onDetectedRef.current(result.getText());
        }
      })
      .then((controls) => {
        if (cancelled || detectedRef.current) {
          controls.stop();
          stopVideoStream(video);
          return;
        }

        controlsRef.current = controls;
        setIsStarting(false);
      })
      .catch((error) => {
        if (cancelled) return;
        setIsStarting(false);
        onErrorRef.current?.(getCameraErrorMessage(error));
      });

    return () => {
      cancelled = true;
      stopScanner();
    };
  }, [active]);

  return (
    <div className={cn('relative overflow-hidden rounded-md border border-border bg-muted', className)}>
      <video
        ref={videoRef}
        muted
        playsInline
        className="aspect-video w-full bg-muted object-cover"
      />

      <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
        <div className="relative h-40 w-40 rounded-md border border-primary/70">
          <span className="absolute -left-px -top-px h-8 w-8 rounded-tl-md border-l-2 border-t-2 border-primary" />
          <span className="absolute -right-px -top-px h-8 w-8 rounded-tr-md border-r-2 border-t-2 border-primary" />
          <span className="absolute -bottom-px -left-px h-8 w-8 rounded-bl-md border-b-2 border-l-2 border-primary" />
          <span className="absolute -bottom-px -right-px h-8 w-8 rounded-br-md border-b-2 border-r-2 border-primary" />
        </div>
      </div>

      {isStarting ? (
        <div className="absolute inset-x-0 bottom-0 bg-background/90 px-3 py-2 text-xs text-muted-foreground">
          Đang bật webcam...
        </div>
      ) : null}
    </div>
  );
}
