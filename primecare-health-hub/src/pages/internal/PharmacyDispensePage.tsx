import { useEffect, useMemo, useState } from 'react';
import { PageHeader } from '@/components/PageHeader';
import { DataTable, type Column } from '@/components/DataTable';
import { StatusBadge } from '@/components/StatusBadge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog';
import { usePharmacistPrescriptions, useDispensePrescription } from '@/hooks/use-pharmacist-data';
import { useDebouncedValue } from '@/hooks/use-debounced-value';
import type { Prescription, PrescriptionStatus } from '@/types/api';
import { Pill } from 'lucide-react';

type PharmacyStatusFilter = '__all__' | 'all' | PrescriptionStatus;

const PRESCRIPTION_STATUS_LABELS: Record<PrescriptionStatus, string> = {
  DRAFT: 'Nháp',
  ISSUED: 'Đã kê / Chờ thanh toán',
  PAID: 'Đã thanh toán / Chờ phát',
  DISPENSED: 'Đã phát',
  CANCELLED: 'Đã hủy',
};

const isDispensableStatus = (status: PrescriptionStatus) => status === 'PAID';
const isConcreteStatusFilter = (status: PharmacyStatusFilter): status is PrescriptionStatus =>
  status !== '__all__' && status !== 'all';

export default function PharmacyDispensePage() {
  const [page, setPage] = useState(1);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<PharmacyStatusFilter>('__all__');
  const [viewItem, setViewItem] = useState<Prescription | null>(null);
  const selectedStatus = isConcreteStatusFilter(statusFilter) ? statusFilter : undefined;
  const debouncedSearch = useDebouncedValue(search.trim(), 400);

  useEffect(() => {
    setPage(1);
  }, [debouncedSearch, selectedStatus]);

  const prescriptionParams = useMemo(
    () => ({
      page: String(page - 1),
      size: '20',
      ...(debouncedSearch ? { q: debouncedSearch } : {}),
      ...(selectedStatus ? { status: selectedStatus } : {}),
    }),
    [debouncedSearch, page, selectedStatus],
  );

  const { data, isLoading } = usePharmacistPrescriptions(prescriptionParams);

  const dispense = useDispensePrescription();

  const columns: Column<Prescription>[] = [
    {
      key: 'code',
      header: 'Mã đơn',
      cell: (r) => <span className="font-mono text-sm">{r.code || r.id}</span>,
    },
    {
      key: 'encounterCode',
      header: 'Mã lần khám',
      cell: (r) => <span>{r.encounterCode || '-'}</span>,
    },
    {
      key: 'status',
      header: 'Trạng thái',
      cell: (r) => <StatusBadge status={r.status} label={PRESCRIPTION_STATUS_LABELS[r.status]} />,
    },
    {
      key: 'issuedDate',
      header: 'Ngày kê',
      cell: (r) => <span className="text-sm">{r.issuedDate ? new Date(r.issuedDate).toLocaleString('vi-VN') : '-'}</span>,
    },
  ];

  return (
    <div className="space-y-4">
      <PageHeader
        title="Kho dược & Phát thuốc"
        description="Xem các đơn thuốc đã được xuất và tiến hành phát thuốc cho bệnh nhân."
      />
      <div className="grid grid-cols-1 md:grid-cols-2 gap-3 mb-4">
        <Input
          placeholder="Tìm theo mã đơn..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <Select value={statusFilter} onValueChange={(value) => setStatusFilter(value as PharmacyStatusFilter)}>
          <SelectTrigger>
            <SelectValue placeholder="Trạng thái" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="__all__">Tất cả</SelectItem>
            <SelectItem value="DRAFT">{PRESCRIPTION_STATUS_LABELS.DRAFT}</SelectItem>
            <SelectItem value="ISSUED">{PRESCRIPTION_STATUS_LABELS.ISSUED}</SelectItem>
            <SelectItem value="PAID">{PRESCRIPTION_STATUS_LABELS.PAID}</SelectItem>
            <SelectItem value="DISPENSED">{PRESCRIPTION_STATUS_LABELS.DISPENSED}</SelectItem>
            <SelectItem value="CANCELLED">{PRESCRIPTION_STATUS_LABELS.CANCELLED}</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <div className="border rounded-lg bg-card">
        <DataTable
          columns={columns}
          data={data?.items || []}
          page={page}
          totalPages={Math.max(data?.meta?.totalPages || 1, 1)}
          onPageChange={setPage}
          emptyMessage={isLoading ? 'Đang tải...' : 'Không có đơn thuốc nào'}
          keyExtractor={(r) => r.id}
          actions={(r) => {
            const p = r as Prescription;
            return (
              <div className="flex gap-2">
                <Button size="sm" variant="outline" onClick={() => setViewItem(p)}>
                  Chi tiết
                </Button>
                {isDispensableStatus(p.status) && (
                  <Button size="sm" onClick={() => {
                    dispense.mutate(p.id);
                  }} disabled={dispense.isPending}>
                    <Pill className="mr-2 h-4 w-4" />
                    Phát
                  </Button>
                )}
                {p.status === 'ISSUED' ? (
                  <span className="inline-flex items-center rounded-md bg-muted px-2.5 py-1 text-xs text-muted-foreground">
                    Chờ thanh toán
                  </span>
                ) : null}
              </div>
            );
          }}
        />
      </div>

      <Dialog open={!!viewItem} onOpenChange={(o) => !o && setViewItem(null)}>
        <DialogContent className="max-w-2xl max-h-[80vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Chi tiết Đơn thuốc {viewItem?.code}</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div className="bg-muted p-4 rounded-md text-sm space-y-1">
              <p><strong>Ngày kê:</strong> {viewItem?.issuedDate ? new Date(viewItem.issuedDate).toLocaleString('vi-VN') : '-'}</p>
              <p><strong>Trạng thái:</strong> {viewItem && <StatusBadge status={viewItem.status} label={PRESCRIPTION_STATUS_LABELS[viewItem.status]} />}</p>
              {viewItem?.generalNote && <p><strong>Ghi chú:</strong> {viewItem.generalNote}</p>}
            </div>

            <div>
              <h4 className="font-semibold text-sm mb-2">Danh sách thuốc</h4>
              <ul className="space-y-3">
                {viewItem?.items?.map((it, idx) => (
                  <li key={idx} className="border p-3 rounded-md text-sm flex justify-between items-start">
                    <div>
                      <p className="font-medium">{it.medicationName}</p>
                      <p className="text-muted-foreground mt-1">
                        {it.dose} · {it.frequency} · {it.route} ({it.durationDays} ngày)
                      </p>
                      {it.instruction && <p className="text-muted-foreground text-xs mt-1">HD: {it.instruction}</p>}
                    </div>
                    <div className="font-semibold">
                      SL: {it.quantity}
                    </div>
                  </li>
                ))}
              </ul>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setViewItem(null)}>Đóng</Button>
            {viewItem && isDispensableStatus(viewItem.status) && (
              <Button onClick={() => {
                dispense.mutate(viewItem.id);
                setViewItem(null);
              }} disabled={dispense.isPending}>
                <Pill className="mr-2 h-4 w-4" />
                Xác nhận phát thuốc
              </Button>
            )}
            {viewItem?.status === 'ISSUED' ? (
              <span className="self-center text-sm text-muted-foreground">
                Đơn đã kê và đang chờ thanh toán, chưa thể phát thuốc.
              </span>
            ) : null}
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
