import { useMemo, useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import PatientStatusHistoryDialog from '@/components/patient/PatientStatusHistoryDialog';
import {
  useOpenPatientResultPdf,
  usePatientResultStatusHistory,
  usePatientResults,
} from '@/hooks/use-patient-portal';

export default function PatientResultsPage() {
  const { data, isLoading } = usePatientResults();
  const openPdfMutation = useOpenPatientResultPdf();
  const [selectedResultId, setSelectedResultId] = useState<string | null>(null);

  const historyQuery = usePatientResultStatusHistory(selectedResultId ?? undefined, Boolean(selectedResultId));

  const selectedResult = useMemo(
    () => data?.items.find((item) => item.resultId === selectedResultId),
    [data?.items, selectedResultId],
  );

  return (
    <>
      <Card>
        <CardHeader>
          <CardTitle>Kết quả cận lâm sàng</CardTitle>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <p className="text-sm text-muted-foreground">Đang tải kết quả...</p>
          ) : !data?.items?.length ? (
            <p className="text-sm text-muted-foreground">Chưa có kết quả nào khả dụng.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b text-left text-muted-foreground">
                    <th className="py-2 pr-3">Dịch vụ</th>
                    <th className="py-2 pr-3">Mã lần khám</th>
                    <th className="py-2 pr-3">Trạng thái</th>
                    <th className="py-2 pr-3">PDF</th>
                    <th className="py-2 pr-3">Xác minh lúc</th>
                    <th className="py-2 pr-3">Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {data.items.map((row) => (
                    <tr key={row.resultId} className="border-b last:border-0 align-top">
                      <td className="py-3 pr-3">{row.serviceName || '-'}</td>
                      <td className="py-3 pr-3">{row.encounterCode || '-'}</td>
                      <td className="py-3 pr-3"><Badge variant="secondary">{row.status || '-'}</Badge></td>
                      <td className="py-3 pr-3">
                        <Badge variant={row.pdfReady ? 'default' : 'outline'}>{row.reportPdfStatus || '-'}</Badge>
                      </td>
                      <td className="py-3 pr-3">{row.verifiedAt || '-'}</td>
                      <td className="py-3 pr-3">
                        <div className="flex flex-col items-start gap-2">
                          <Button type="button" variant="outline" size="sm" onClick={() => setSelectedResultId(row.resultId)}>
                            Xem lịch sử trạng thái
                          </Button>
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            disabled={!row.pdfReady || openPdfMutation.isPending}
                            onClick={() => openPdfMutation.mutate(row.resultId)}
                          >
                            Mở PDF kết quả
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
        open={Boolean(selectedResultId)}
        onOpenChange={(open) => {
          if (!open) setSelectedResultId(null);
        }}
        title={selectedResult ? `Lịch sử trạng thái - ${selectedResult.serviceName || selectedResult.resultId}` : 'Lịch sử trạng thái'}
        items={historyQuery.data}
        isLoading={historyQuery.isLoading}
      />
    </>
  );
}
