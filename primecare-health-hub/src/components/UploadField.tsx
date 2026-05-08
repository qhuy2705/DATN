import { useEffect, useId, useMemo, useRef, useState } from 'react';
import { ExternalLink, FileText, Upload, X } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import apiClient from '@/lib/api-client';
import { openProtectedFile } from '@/lib/download-file';
import { normalizeFileUrl, resolveImagePreviewUrl } from '@/lib/api-url';
import { toast } from 'sonner';

interface UploadFieldProps {
  value?: string;
  onChange: (url: string) => void;
  accept?: string;
  label?: string;
  ownerType?:
    | 'DOCTOR'
    | 'USER'
    | 'PATIENT'
    | 'SERVICE_RESULT'
    | 'APPOINTMENT'
    | 'PRESCRIPTION'
    | 'INVOICE';
  ownerId?: string;
  helperText?: string;
  disabled?: boolean;
}

const AVATAR_OWNER_TYPES = new Set(['DOCTOR', 'USER', 'PATIENT']);

export function UploadField({
  value,
  onChange,
  accept = 'image/*',
  label,
  ownerType,
  ownerId,
  helperText,
  disabled = false,
}: UploadFieldProps) {
  const { t } = useTranslation();
  const inputId = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  const [preview, setPreview] = useState(value || '');
  const [uploading, setUploading] = useState(false);

  useEffect(() => {
    setPreview(value || '');
  }, [value]);

  const canUpload = !disabled && Boolean(ownerType && ownerId);
  const uploadEndpoint = useMemo(() => {
    if (ownerType && AVATAR_OWNER_TYPES.has(ownerType)) {
      return '/files/avatars';
    }
    return '/files/attachments';
  }, [ownerType]);

  const handleFile = async (file: File) => {
    if (!canUpload) {
      toast.error(helperText || 'Bạn cần lưu hồ sơ trước khi upload tệp.');
      return;
    }

    setUploading(true);
    try {
      const formData = new FormData();
      formData.append('file', file);
      const { data } = await apiClient.post(uploadEndpoint, formData, {
        params: { ownerType, ownerId },
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      const url = data?.data?.url || data?.url;
      if (!url) throw new Error('Không nhận được URL tệp');
      setPreview(url);
      onChange(url);
      toast.success(
        uploadEndpoint === '/files/avatars' ? 'Upload ảnh thành công' : 'Upload tệp thành công',
      );
    } catch (error) {
      const maybeAxios = error as { response?: { data?: { message?: string } } };
      toast.error(maybeAxios.response?.data?.message || 'Upload thất bại');
    } finally {
      setUploading(false);
    }
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) void handleFile(file);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    if (disabled) return;
    const file = e.dataTransfer.files?.[0];
    if (file) void handleFile(file);
  };

  const renderPreview = () => {
    if (!preview) return null;

    const imagePreviewUrl = resolveImagePreviewUrl(preview, accept);
    if (imagePreviewUrl) {
      return <img src={imagePreviewUrl} alt="Preview" className="h-24 w-24 rounded-lg object-cover" />;
    }

    return (
      <div className="flex flex-col items-center gap-2 rounded-lg border bg-muted/30 px-4 py-3 text-center">
        <FileText className="h-8 w-8 text-muted-foreground" />
        <button
          type="button"
          onClick={(event) => {
            event.stopPropagation();
            void openProtectedFile(preview, 'tep-dinh-kem');
          }}
          className="inline-flex max-w-[220px] items-center gap-1 truncate text-sm text-primary underline-offset-4 hover:underline"
        >
          <span className="truncate">{normalizeFileUrl(preview).split('/').pop() || preview}</span>
          <ExternalLink className="h-3.5 w-3.5 shrink-0" />
        </button>
      </div>
    );
  };

  return (
    <div>
      {label && <label htmlFor={inputId} className="mb-ui-xs block text-sm font-medium text-foreground">{label}</label>}
      <div
        onDrop={handleDrop}
        onDragOver={(e) => e.preventDefault()}
        onClick={() => !disabled && inputRef.current?.click()}
        onKeyDown={(event) => {
          if (disabled) return;
          if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            inputRef.current?.click();
          }
        }}
        role="button"
        tabIndex={disabled ? -1 : 0}
        className={`rounded-lg border border-dashed border-border bg-muted/10 p-ui-lg text-center transition-[border-color,background-color,box-shadow] focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25 focus-visible:ring-offset-0 ${
          disabled ? 'cursor-not-allowed opacity-60' : 'cursor-pointer hover:border-primary/50 hover:bg-primary/5'
        }`}
      >
        <input
          id={inputId}
          ref={inputRef}
          type="file"
          accept={accept}
          onChange={handleChange}
          className="hidden"
          disabled={disabled}
        />
        {preview ? (
          <div className="relative inline-block">
            {renderPreview()}
            {!disabled && (
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  setPreview('');
                  onChange('');
                }}
                className="absolute -right-2 -top-2 flex h-6 w-6 items-center justify-center rounded-full bg-destructive text-destructive-foreground shadow-sm transition-colors hover:bg-destructive/90 focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-destructive/25 focus-visible:ring-offset-0"
                aria-label="Xóa tệp đã chọn"
              >
                <X className="h-3 w-3" />
              </button>
            )}
          </div>
        ) : (
          <div className="flex flex-col items-center gap-2 text-muted-foreground">
            {uploading ? (
              <div className="h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
            ) : (
              <>
                <Upload className="h-8 w-8" />
                <p className="text-sm">{t('common.upload')}</p>
                {(helperText || !canUpload) && (
                  <p className="max-w-xs text-xs text-muted-foreground">
                    {helperText || 'Lưu hồ sơ trước để có thể upload tệp.'}
                  </p>
                )}
              </>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
