import { useMemo, useState } from 'react';
import { ChevronDown, Pencil, Pill, Plus, XCircle } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { StatusBadge } from '@/components/StatusBadge';
import {
  useCancelPrescription,
  useDoctorEncounterPrescriptions,
} from '@/hooks/use-doctor-data';
import type { Prescription } from '@/types/api';
import { PrescriptionEditorDialog } from './PrescriptionEditorDialog';
import {
  getRefundReason,
  getRefundedAt,
  isRefundedOrCancelledItem,
  MEDICATION_REFUNDED_LABEL,
} from '@/lib/refund-status';

interface EncounterPrescriptionsPanelProps {
  encounterId: string;
  canCreatePrescription: boolean;
  canEdit: boolean;
  createBlockedReason?: string;
}

function formatIssuedDate(value?: string) {
  if (!value) return 'Chưa phát hành ngày';
  return new Date(value).toLocaleString('vi-VN');
}

function canEditPrescription(prescription: Prescription) {
  return prescription.status === 'ISSUED' || prescription.status === 'DRAFT';
}

function canCancelPrescription(prescription: Prescription) {
  return prescription.status === 'DRAFT' || prescription.status === 'ISSUED';
}

export function EncounterPrescriptionsPanel({
  encounterId,
  canCreatePrescription,
  canEdit,
  createBlockedReason,
}: EncounterPrescriptionsPanelProps) {
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editItem, setEditItem] = useState<Prescription | null>(null);
  const [expandedIds, setExpandedIds] = useState<Set<string>>(() => new Set());

  const { data: prescriptionsPage, isLoading } = useDoctorEncounterPrescriptions(encounterId);
  const cancelPrescription = useCancelPrescription(encounterId);

  const prescriptions = useMemo(
    () => prescriptionsPage?.items ?? [],
    [prescriptionsPage?.items],
  );

  const openCreate = () => {
    setEditItem(null);
    setDialogOpen(true);
  };

  const openEdit = (prescription: Prescription) => {
    setEditItem(prescription);
    setDialogOpen(true);
  };

  const handleCancel = (prescription: Prescription) => {
    const confirmed = window.confirm(`Hủy đơn thuốc ${prescription.code || prescription.id}?`);
    if (!confirmed) return;
    cancelPrescription.mutate(prescription.id);
  };

  const toggleExpanded = (prescriptionId: string) => {
    setExpandedIds((prev) => {
      const next = new Set(prev);
      if (next.has(prescriptionId)) {
        next.delete(prescriptionId);
      } else {
        next.add(prescriptionId);
      }
      return next;
    });
  };

  const createDisabled = !canEdit || !canCreatePrescription;
  const disabledReason = !canEdit
    ? 'Không thể tạo đơn thuốc mới cho lần khám này.'
    : createBlockedReason || 'Không thể tạo đơn thuốc mới cho lần khám này.';

  return (
    <div className="rounded-xl border bg-card p-4">
      <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="text-base font-semibold">Đơn thuốc</h3>
          <p className="mt-1 text-sm text-muted-foreground">
            {prescriptions.length > 0
              ? `${prescriptions.length} đơn thuốc trong hồ sơ khám`
              : 'Chưa có đơn thuốc'}
          </p>
        </div>
        <Button type="button" size="sm" onClick={openCreate} disabled={createDisabled}>
          <Plus className="h-4 w-4" />
          Tạo
        </Button>
      </div>

      {createDisabled && (
        <p className="mb-4 rounded-md border bg-muted/30 px-3 py-2 text-sm text-muted-foreground">
          {disabledReason}
        </p>
      )}

      {isLoading ? (
        <p className="text-sm text-muted-foreground">Đang tải đơn thuốc...</p>
      ) : prescriptions.length === 0 ? (
        <div className="rounded-lg border border-dashed bg-muted/10 p-6 text-center text-sm text-muted-foreground">
          <Pill className="mx-auto mb-2 h-5 w-5" />
          Chưa có đơn thuốc
        </div>
      ) : (
        <div className="space-y-2">
          {prescriptions.map((prescription) => {
            const previewItems = prescription.items.slice(0, 2);
            const hiddenItemCount = Math.max(prescription.items.length - previewItems.length, 0);
            const expanded = expandedIds.has(prescription.id);

            return (
              <div key={prescription.id} className="rounded-lg border bg-background/60 p-3">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="font-semibold">{prescription.code || `Đơn ${prescription.id}`}</p>
                      <StatusBadge status={prescription.status} />
                    </div>
                    <p className="mt-1 text-xs text-muted-foreground">
                      {prescription.items.length} thuốc · {formatIssuedDate(prescription.issuedDate)}
                    </p>
                  </div>
                  {canEdit && (
                    <div className="flex flex-wrap items-center gap-2">
                      {canEditPrescription(prescription) && (
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          onClick={() => openEdit(prescription)}
                        >
                          <Pencil className="h-4 w-4" />
                          Sửa
                        </Button>
                      )}
                      {canCancelPrescription(prescription) && (
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          onClick={() => handleCancel(prescription)}
                          disabled={cancelPrescription.isPending}
                          className="text-destructive hover:text-destructive"
                        >
                          <XCircle className="h-4 w-4" />
                          Hủy
                        </Button>
                      )}
                    </div>
                  )}
                </div>

                <div className="mt-3 space-y-1.5">
                  {previewItems.length > 0 ? (
                    previewItems.map((item, index) => (
                      <div
                        key={item.id || `${prescription.id}-preview-${index}`}
                        className="flex items-center justify-between gap-2 rounded-md bg-muted/20 px-3 py-2 text-sm"
                      >
                        <div className="min-w-0">
                          <span className="block truncate font-medium text-foreground">
                            {item.medicationName}
                          </span>
                          {isRefundedOrCancelledItem(item) ? (
                            <span className="mt-1 inline-flex rounded-full border border-muted-foreground/20 bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                              {MEDICATION_REFUNDED_LABEL}
                            </span>
                          ) : null}
                        </div>
                        <span className="shrink-0 text-xs text-muted-foreground">
                          {item.quantity ?? 0} {item.unit || 'đơn vị'}
                        </span>
                      </div>
                    ))
                  ) : (
                    <p className="rounded-md bg-muted/20 px-3 py-2 text-sm text-muted-foreground">
                      Chưa có thuốc trong đơn.
                    </p>
                  )}
                  {hiddenItemCount > 0 ? (
                    <p className="px-1 text-xs text-muted-foreground">
                      +{hiddenItemCount} thuốc khác
                    </p>
                  ) : null}
                </div>

                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  onClick={() => toggleExpanded(prescription.id)}
                  className="mt-2 px-2 text-primary hover:text-primary"
                >
                  {expanded ? 'Thu gọn' : 'Xem chi tiết'}
                  <ChevronDown className={`h-4 w-4 transition-transform ${expanded ? 'rotate-180' : ''}`} />
                </Button>

                {expanded ? (
                  <div className="mt-3 space-y-3 border-t pt-3">
                    {prescription.generalNote ? (
                      <p className="rounded-md bg-muted/30 px-3 py-2 text-sm text-foreground">
                        {prescription.generalNote}
                      </p>
                    ) : null}

                    {prescription.items.map((item, index) => {
                      const refunded = isRefundedOrCancelledItem(item);
                      const refundReason = getRefundReason(item);
                      const refundedAt = getRefundedAt(item);
                      return (
                        <div
                          key={item.id || `${prescription.id}-${index}`}
                          className="rounded-md border bg-muted/10 px-3 py-3"
                        >
                          <div className="flex flex-wrap items-start justify-between gap-2">
                            <div>
                              <div className="flex flex-wrap items-center gap-2">
                                <p className="font-medium text-foreground">{item.medicationName}</p>
                                {refunded ? (
                                  <span className="rounded-full border border-muted-foreground/20 bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                                    {MEDICATION_REFUNDED_LABEL}
                                  </span>
                                ) : null}
                              </div>
                              <p className="text-xs text-muted-foreground">
                                {[item.strength, item.dosageForm].filter(Boolean).join(' · ') || 'Thông tin thuốc'}
                              </p>
                              {refunded && (refundReason || refundedAt) ? (
                                <p className="mt-1 text-xs text-muted-foreground">
                                  {[refundReason, refundedAt ? `Lúc ${new Date(refundedAt).toLocaleString('vi-VN')}` : '']
                                    .filter(Boolean)
                                    .join(' · ')}
                                </p>
                              ) : null}
                            </div>
                            <span className="rounded-full bg-background px-2.5 py-1 text-xs text-muted-foreground">
                              {item.quantity ?? 0} {item.unit || 'đơn vị'}
                            </span>
                          </div>
                          <div className="mt-2 grid gap-2 text-sm text-muted-foreground md:grid-cols-2">
                            <span>Liều: {item.dose || '—'}</span>
                            <span>Tần suất: {item.frequency || '—'}</span>
                            <span>Số ngày: {item.durationDays ?? '—'}</span>
                            <span>Đường dùng: {item.route || '—'}</span>
                          </div>
                          {item.instruction ? (
                            <p className="mt-2 text-sm text-foreground">{item.instruction}</p>
                          ) : null}
                        </div>
                      );
                    })}
                  </div>
                ) : null}
              </div>
            );
          })}
        </div>
      )}

      <PrescriptionEditorDialog
        encounterId={encounterId}
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        editItem={editItem}
        onSaved={() => setEditItem(null)}
      />
    </div>
  );
}
