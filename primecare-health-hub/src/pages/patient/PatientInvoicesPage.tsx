import { useMemo, useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import PatientStatusHistoryDialog from '@/components/patient/PatientStatusHistoryDialog';
import {
  useDownloadPatientInvoicePdf,
  usePatientInvoiceStatusHistory,
  usePatientInvoices,
} from '@/hooks/use-patient-portal';

function formatCurrency(value?: number) {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(value || 0);
}

export default function PatientInvoicesPage() {
  const { data, isLoading } = usePatientInvoices();
  const downloadInvoiceMutation = useDownloadPatientInvoicePdf();
  const [selectedInvoiceId, setSelectedInvoiceId] = useState<string | null>(null);

  const historyQuery = usePatientInvoiceStatusHistory(selectedInvoiceId ?? undefined, Boolean(selectedInvoiceId));

  const selectedInvoice = useMemo(
    () => data?.items.find((item) => item.id === selectedInvoiceId),
    [data?.items, selectedInvoiceId],
  );

  return (
    <>
      <Card>
        <CardHeader>
          <CardTitle>Hóa đơn của tôi</CardTitle>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <p className="text-sm text-muted-foreground">Đang tải hóa đơn...</p>
          ) : !data?.items?.length ? (
            <p className="text-sm text-muted-foreground">Bạn chưa có hóa đơn nào.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b text-left text-muted-foreground">
                    <th className="py-2 pr-3">Mã hóa đơn</th>
                    <th className="py-2 pr-3">Mã chỉ định</th>
                    <th className="py-2 pr-3">Tổng tiền</th>
                    <th className="py-2 pr-3">Thanh toán</th>
                    <th className="py-2 pr-3">Ngày tạo</th>
                    <th className="py-2 pr-3">Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {data.items.map((row) => (
                    <tr key={row.id} className="border-b last:border-0 align-top">
                      <td className="py-3 pr-3">{row.code}</td>
                      <td className="py-3 pr-3">{row.serviceOrderCode || '-'}</td>
                      <td className="py-3 pr-3">{formatCurrency(row.totalAmount)}</td>
                      <td className="py-3 pr-3"><Badge variant={row.paymentStatus === 'PAID' ? 'default' : 'secondary'}>{row.paymentStatus || '-'}</Badge></td>
                      <td className="py-3 pr-3">{row.createdAt || '-'}</td>
                      <td className="py-3 pr-3">
                        <div className="flex flex-col items-start gap-2">
                          <Button type="button" variant="outline" size="sm" onClick={() => setSelectedInvoiceId(row.id)}>
                            Xem lịch sử trạng thái
                          </Button>
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            disabled={downloadInvoiceMutation.isPending || row.canDownloadPdf === false}
                            onClick={() => downloadInvoiceMutation.mutate(row.id)}
                          >
                            Tải PDF hóa đơn
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>

      <PatientStatusHistoryDialog
        open={Boolean(selectedInvoiceId)}
        onOpenChange={(open) => {
          if (!open) setSelectedInvoiceId(null);
        }}
        title={selectedInvoice ? `Lịch sử trạng thái - ${selectedInvoice.code}` : 'Lịch sử trạng thái'}
        items={historyQuery.data}
        isLoading={historyQuery.isLoading}
      />
    </>
  );
}
