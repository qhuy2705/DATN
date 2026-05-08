import { useParams } from 'react-router-dom';
import { PageHeader } from '@/components/PageHeader';
import { PdfJobStatusCard } from '@/components/PdfJobStatusCard';
import { useDownloadInvoicePdf, useInvoicePdfJob } from '@/hooks/use-cashier-data';

export default function InvoicePdfJobPage() {
  const { jobId } = useParams();
  const { data: job, isLoading } = useInvoicePdfJob(jobId ?? null);
  const downloadPdf = useDownloadInvoicePdf();

  return (
    <div>
      <PageHeader
        title="Tạo PDF hóa đơn"
        description="Theo dõi tiến trình sinh PDF và tải hóa đơn qua phiên đăng nhập an toàn."
      />
      <div className="max-w-md">
        {job ? (
          <PdfJobStatusCard
            job={job}
            loading={downloadPdf.isPending}
            onDownload={(url) =>
              downloadPdf.mutate({
                url,
                fallbackFilename: `hoa-don-${job.invoiceId || job.id}.pdf`,
              })
            }
          />
        ) : (
          <div className="text-sm text-muted-foreground">
            {isLoading ? 'Đang tải...' : 'Không tìm thấy job'}
          </div>
        )}
      </div>
    </div>
  );
}