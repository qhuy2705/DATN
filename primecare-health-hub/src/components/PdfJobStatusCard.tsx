import { useTranslation } from 'react-i18next';
import { FileText, Download, Loader2, AlertCircle, Clock } from 'lucide-react';
import { Button } from '@/components/ui/button';
import type { PdfJob } from '@/types/api';

interface PdfJobStatusCardProps {
  job: PdfJob | null | undefined;
  onDownload?: (url: string) => void;
  loading?: boolean;
}

const statusIcons = {
  PENDING: Clock,
  PROCESSING: Loader2,
  COMPLETED: FileText,
  FAILED: AlertCircle,
};

export function PdfJobStatusCard({ job, onDownload, loading }: PdfJobStatusCardProps) {
  const { t } = useTranslation();

  if (loading || !job) {
    return (
      <div className="border rounded-lg p-6 flex items-center justify-center">
        <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
      </div>
    );
  }

  const Icon = statusIcons[job.status];
  const isSpinning = job.status === 'PROCESSING';
  const statusKey = job.status.toLowerCase() as 'pending' | 'processing' | 'completed' | 'failed';

  return (
    <div className={`border rounded-lg p-6 space-y-3 ${
      job.status === 'COMPLETED' ? 'border-success/30 bg-success/5' :
      job.status === 'FAILED' ? 'border-destructive/30 bg-destructive/5' : ''
    }`}>
      <div className="flex items-center gap-3">
        <Icon className={`h-5 w-5 ${isSpinning ? 'animate-spin' : ''} ${
          job.status === 'COMPLETED' ? 'text-success' :
          job.status === 'FAILED' ? 'text-destructive' : 'text-muted-foreground'
        }`} />
        <div>
          <p className="font-medium text-foreground">{t(`modules.pdfJob.${statusKey}`)}</p>
          <p className="text-xs text-muted-foreground">{job.createdAt}</p>
        </div>
      </div>
      {job.errorMessage && (
        <div className="rounded-xl border border-destructive/20 bg-destructive/5 px-3 py-2 text-sm text-destructive">
          {job.errorMessage}
        </div>
      )}
      {job.status === 'COMPLETED' && job.downloadUrl && (
        <Button size="sm" onClick={() => onDownload?.(job.downloadUrl!)} className="w-full" disabled={loading}>
          {loading ? <Loader2 className="h-4 w-4 mr-2 animate-spin" /> : <Download className="h-4 w-4 mr-2" />} {t('common.download')}
        </Button>
      )}
    </div>
  );
}
