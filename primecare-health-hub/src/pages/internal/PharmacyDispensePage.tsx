import { type ReactNode, useEffect, useMemo, useState } from 'react';
import { PageHeader } from '@/components/PageHeader';
import { DataTable, type Column } from '@/components/DataTable';
import { StatusBadge } from '@/components/StatusBadge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog';
import { usePharmacistPrescriptions, useDispensePrescription } from '@/hooks/use-pharmacist-data';
import { useDebouncedValue } from '@/hooks/use-debounced-value';
import type { Prescription, PrescriptionStatus } from '@/types/api';
import { CreditCard, Pill } from 'lucide-react';
import { Link } from 'react-router-dom';
import {
  getRefundReason,
  getRefundedAt,
  isRefundedOrCancelledItem,
  MEDICATION_REFUNDED_LABEL,
} from '@/lib/refund-status';

type PharmacyStatusFilter = '__all__' | 'all' | PrescriptionStatus;

const PRESCRIPTION_STATUS_LABELS: Record<PrescriptionStatus, string> = {
  DRAFT: 'Nháp',
  ISSUED: 'Đã kê / Chờ thanh toán',
  PAID: 'Đã thanh toán / Chờ phát',
  DISPENSED: 'Đã phát thuốc',
  CANCELLED: 'Đã hủy',
};

function getActivePrescriptionItems(prescription?: Prescription | null) {
  return prescription?.items?.filter((item) => !isRefundedOrCancelledItem(item)) ?? [];
}

const canDispensePrescription = (prescription: Prescription) =>
  prescription.status === 'PAID' &&
  (typeof prescription.canDispense === 'boolean' ? prescription.canDispense : true) &&
  getActivePrescriptionItems(prescription).length > 0;
const isConcreteStatusFilter = (status: PharmacyStatusFilter): status is PrescriptionStatus =>
  status !== '__all__' && status !== 'all';

function getPrescriptionPaymentTarget(prescription: Prescription) {
  const href =
    prescription.paymentUrl ||
    prescription.paymentRoute ||
    (prescription.invoiceId
      ? `/app/cashier/invoices?invoiceId=${encodeURIComponent(prescription.invoiceId)}`
      : '');
  if (!href) return null;
  return {
    href,
    external: /^https?:\/\//i.test(href),
  };
}

function PrescriptionPaymentLink({ prescription }: { prescription: Prescription }) {
  const target = getPrescriptionPaymentTarget(prescription);
  if (!target) return null;

  const content = (
    <>
      <CreditCard className="mr-2 h-4 w-4" />
      Thanh toán
    </>
  );

  return (
    <Button asChild size="sm" variant="outline">
      {target.external ? (
        <a href={target.href} target="_blank" rel="noreferrer">
          {content}
        </a>
      ) : (
        <Link to={target.href}>{content}</Link>
      )}
    </Button>
  );
}

function PrescriptionInfoRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid gap-1 sm:grid-cols-[9rem_1fr]">
      <span className="text-muted-foreground">{label}</span>
      <span className="font-medium">{children}</span>
    </div>
  );
}

interface DispenseConfirmationDialogProps {
  prescription: Prescription | null;
  open: boolean;
  loading: boolean;
  onOpenChange: (open: boolean) => void;
  onConfirm: () => void;
}

function DispenseConfirmationDialog({
  prescription,
  open,
  loading,
  onOpenChange,
  onConfirm,
}: DispenseConfirmationDialogProps) {
  const itemCount = prescription?.items?.length ?? 0;
  const activeItemCount = getActivePrescriptionItems(prescription).length;
  const canConfirm = Boolean(prescription && canDispensePrescription(prescription));

  return (
    <Dialog
      open={open}
      onOpenChange={(nextOpen) => {
        if (loading && !nextOpen) return;
        onOpenChange(nextOpen);
      }}
    >
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>Xác nhận phát thuốc</DialogTitle>
          <DialogDescription>
            Vui lòng xác nhận bạn đã kiểm tra đơn thuốc, số lượng và tồn kho trước khi phát thuốc cho bệnh nhân.
          </DialogDescription>
        </DialogHeader>

        {prescription ? (
          <div className="space-y-4 text-sm">
            <div className="rounded-md border border-border bg-muted/40 p-3 space-y-2">
              <PrescriptionInfoRow label="Bệnh nhân">
                {prescription.patientName || 'Chưa có thông tin'}
              </PrescriptionInfoRow>
              <PrescriptionInfoRow label="Mã đơn thuốc">
                <span className="font-mono">{prescription.code || prescription.id}</span>
              </PrescriptionInfoRow>
              <PrescriptionInfoRow label="Bác sĩ">
                {prescription.doctorName || 'Chưa có thông tin'}
              </PrescriptionInfoRow>
              <PrescriptionInfoRow label="Trạng thái">
                <StatusBadge
                  status={prescription.status}
                  label={PRESCRIPTION_STATUS_LABELS[prescription.status]}
                />
              </PrescriptionInfoRow>
              <PrescriptionInfoRow label="Tổng số thuốc">
                {activeItemCount} thuốc còn hiệu lực / {itemCount} thuốc
              </PrescriptionInfoRow>
            </div>

            <div>
              <div className="mb-2 flex items-center justify-between gap-2">
                <h4 className="font-semibold">Danh sách thuốc</h4>
                <span className="text-xs text-muted-foreground">{activeItemCount} thuốc còn hiệu lực</span>
              </div>
              {itemCount > 0 ? (
                <ul className="max-h-56 overflow-y-auto rounded-md border border-border divide-y divide-border">
                  {prescription.items.map((item, index) => {
                    const refunded = isRefundedOrCancelledItem(item);
                    return (
                      <li key={item.id || `${prescription.id}-confirm-${index}`} className="flex items-start justify-between gap-3 p-3">
                        <div className="min-w-0">
                          <div className="flex flex-wrap items-center gap-2">
                            <p className="font-medium">{item.medicationName || 'Thuốc chưa có tên'}</p>
                            {refunded ? (
                              <span className="rounded-full border border-muted-foreground/20 bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                                {MEDICATION_REFUNDED_LABEL}
                              </span>
                            ) : null}
                          </div>
                          <p className="mt-1 text-xs text-muted-foreground">
                            {[item.dose, item.frequency, item.route, item.durationDays ? `${item.durationDays} ngày` : item.duration]
                              .filter(Boolean)
                              .join(' · ') || 'Chưa có hướng dẫn dùng'}
                          </p>
                        </div>
                        <span className="shrink-0 font-semibold">SL: {item.quantity ?? '-'}</span>
                      </li>
                    );
                  })}
                </ul>
              ) : (
                <p className="rounded-md border border-border bg-muted/30 p-3 text-muted-foreground">
                  Chưa có danh sách thuốc.
                </p>
              )}
            </div>

            {!canConfirm ? (
              <p className="rounded-md border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">
                {activeItemCount === 0
                  ? 'Không còn thuốc nào đủ điều kiện phát.'
                  : 'Không thể phát thuốc cho đơn này.'}
              </p>
            ) : null}
          </div>
        ) : null}

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={loading}>
            Hủy
          </Button>
          <Button onClick={onConfirm} disabled={loading || !canConfirm}>
            <Pill className="mr-2 h-4 w-4" />
            {loading ? 'Đang phát thuốc...' : 'Xác nhận phát thuốc'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export default function PharmacyDispensePage() {
  const [page, setPage] = useState(1);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<PharmacyStatusFilter>('__all__');
  const [viewItem, setViewItem] = useState<Prescription | null>(null);
  const [confirmItem, setConfirmItem] = useState<Prescription | null>(null);
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

  const { data, isLoading, refetch } = usePharmacistPrescriptions(prescriptionParams);

  const dispense = useDispensePrescription();

  const openDispenseConfirm = (prescription: Prescription) => {
    if (!canDispensePrescription(prescription) || dispense.isPending) return;
    setConfirmItem(prescription);
  };

  const handleConfirmDispense = () => {
    if (!confirmItem || dispense.isPending || !canDispensePrescription(confirmItem)) return;

    const prescriptionId = confirmItem.id;

    dispense.mutate(prescriptionId, {
      onSuccess: (updatedPrescription) => {
        setConfirmItem(null);
        setViewItem((current) => (current?.id === updatedPrescription.id ? updatedPrescription : current));
      },
      onError: () => {
        void refetch().then((result) => {
          const latestPrescription = result.data?.items.find((item) => item.id === prescriptionId);
          if (!latestPrescription) return;

          setConfirmItem((current) => (current?.id === prescriptionId ? latestPrescription : current));
          setViewItem((current) => (current?.id === prescriptionId ? latestPrescription : current));
        });
      },
    });
  };

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
            const activeItemCount = getActivePrescriptionItems(p).length;
            return (
              <div className="flex gap-2">
                <Button size="sm" variant="outline" onClick={() => setViewItem(p)}>
                  Chi tiết
                </Button>
                {canDispensePrescription(p) ? (
                  <Button size="sm" onClick={() => openDispenseConfirm(p)} disabled={dispense.isPending}>
                    <Pill className="mr-2 h-4 w-4" />
                    Xác nhận phát thuốc
                  </Button>
                ) : p.status === 'PAID' ? (
                  <span className="inline-flex items-center rounded-md bg-muted px-2.5 py-1 text-xs text-muted-foreground">
                    {activeItemCount === 0 ? 'Đã hủy / đã hoàn tiền' : 'Không thể phát thuốc'}
                  </span>
                ) : (
                  null
                )}
                {p.status === 'ISSUED' ? (
                  <>
                    <span className="inline-flex items-center rounded-md bg-muted px-2.5 py-1 text-xs text-muted-foreground">
                      Chờ thanh toán
                    </span>
                    <PrescriptionPaymentLink prescription={p} />
                  </>
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
              {viewItem?.invoiceCode || viewItem?.invoiceId ? (
                <p><strong>Hóa đơn:</strong> {viewItem.invoiceCode || viewItem.invoiceId}</p>
              ) : null}
              {viewItem?.generalNote && <p><strong>Ghi chú:</strong> {viewItem.generalNote}</p>}
            </div>

            <div>
              <h4 className="font-semibold text-sm mb-2">Danh sách thuốc</h4>
              <ul className="space-y-3">
                {viewItem?.items?.map((it, idx) => {
                  const refunded = isRefundedOrCancelledItem(it);
                  const refundReason = getRefundReason(it);
                  const refundedAt = getRefundedAt(it);
                  return (
                    <li key={idx} className="border p-3 rounded-md text-sm flex justify-between items-start gap-3">
                      <div>
                        <div className="flex flex-wrap items-center gap-2">
                          <p className="font-medium">{it.medicationName}</p>
                          {refunded ? (
                            <span className="rounded-full border border-muted-foreground/20 bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                              {MEDICATION_REFUNDED_LABEL}
                            </span>
                          ) : null}
                        </div>
                        <p className="text-muted-foreground mt-1">
                          {it.dose} · {it.frequency} · {it.route} ({it.durationDays} ngày)
                        </p>
                        {it.instruction && <p className="text-muted-foreground text-xs mt-1">HD: {it.instruction}</p>}
                        {refunded && (refundReason || refundedAt) ? (
                          <p className="text-muted-foreground text-xs mt-1">
                            {[refundReason, refundedAt ? `Lúc ${new Date(refundedAt).toLocaleString('vi-VN')}` : '']
                              .filter(Boolean)
                              .join(' · ')}
                          </p>
                        ) : null}
                      </div>
                      <div className="font-semibold shrink-0">
                        SL: {it.quantity}
                      </div>
                    </li>
                  );
                })}
              </ul>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setViewItem(null)}>Đóng</Button>
            {viewItem && canDispensePrescription(viewItem) && (
              <Button onClick={() => openDispenseConfirm(viewItem)} disabled={dispense.isPending}>
                <Pill className="mr-2 h-4 w-4" />
                Xác nhận phát thuốc
              </Button>
            )}
            {viewItem && !canDispensePrescription(viewItem) && viewItem.status !== 'ISSUED' ? (
              <span className="self-center text-sm text-muted-foreground">
                {viewItem.status === 'DISPENSED'
                  ? 'Đã phát thuốc'
                  : getActivePrescriptionItems(viewItem).length === 0
                    ? 'Đã hủy / đã hoàn tiền'
                    : 'Không thể phát thuốc'}
              </span>
            ) : null}
            {viewItem?.status === 'ISSUED' ? (
              <div className="flex items-center gap-2">
                <span className="self-center text-sm text-muted-foreground">
                  Đơn đã kê và đang chờ thanh toán, chưa thể phát thuốc.
                </span>
                <PrescriptionPaymentLink prescription={viewItem} />
              </div>
            ) : null}
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <DispenseConfirmationDialog
        open={!!confirmItem}
        prescription={confirmItem}
        loading={dispense.isPending}
        onOpenChange={(open) => {
          if (!open) setConfirmItem(null);
        }}
        onConfirm={handleConfirmDispense}
      />
    </div>
  );
}
