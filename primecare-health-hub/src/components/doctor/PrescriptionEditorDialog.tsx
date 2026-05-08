import { useEffect, useMemo, useState } from 'react';
import { AxiosError } from 'axios';
import { Plus, Trash2, TriangleAlert } from 'lucide-react';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import {
  useCreatePrescription,
  useDoctorMedications,
  useUpdatePrescription,
} from '@/hooks/use-doctor-data';
import { getApiErrorMessage } from '@/lib/error-utils';
import type { Medication, Prescription } from '@/types/api';

type EditablePrescriptionItem = {
  medicationId: string;
  medicationName?: string;
  quantity: string;
  dose: string;
  frequency: string;
  durationDays: string;
  route: string;
  instruction: string;
};

interface PrescriptionEditorDialogProps {
  encounterId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  editItem?: Prescription | null;
  onSaved?: () => void;
}

const createEmptyItem = (): EditablePrescriptionItem => ({
  medicationId: '',
  medicationName: '',
  quantity: '1',
  dose: '',
  frequency: '',
  durationDays: '1',
  route: 'Uống',
  instruction: '',
});

function mapPrescriptionItems(prescription?: Prescription | null): EditablePrescriptionItem[] {
  if (!prescription?.items?.length) return [createEmptyItem()];

  return prescription.items.map((item) => ({
    medicationId: item.medicationId,
    medicationName: item.medicationName,
    quantity: String(item.quantity ?? 1),
    dose: item.dose || '',
    frequency: item.frequency || '',
    durationDays: String(item.durationDays ?? 1),
    route: item.route || 'Uống',
    instruction: item.instruction || '',
  }));
}

function resolveMedicationLabel(medication: Medication) {
  return [
    medication.name,
    medication.strength,
    medication.dosageForm,
  ].filter(Boolean).join(' · ');
}

function getPrescriptionSafetyWarning(error: unknown) {
  const axiosError = error as AxiosError<{ code?: string; errorCode?: string; message?: string }>;
  const payload = axiosError.response?.data;
  const rawCode = payload?.code || payload?.errorCode || payload?.message || '';
  const code = String(rawCode);

  if (code === 'PRESCRIPTION_ALLERGY_WARNING' || code.includes('dị ứng')) {
    return payload?.message || 'Bệnh nhân có tiền sử dị ứng với thuốc này.';
  }

  if (code === 'PRESCRIPTION_DRUG_INTERACTION' || code.includes('Tương tác thuốc')) {
    return payload?.message || 'Phát hiện tương tác thuốc cần bác sĩ kiểm tra lại.';
  }

  return null;
}

export function PrescriptionEditorDialog({
  encounterId,
  open,
  onOpenChange,
  editItem,
  onSaved,
}: PrescriptionEditorDialogProps) {
  const [generalNote, setGeneralNote] = useState('');
  const [items, setItems] = useState<EditablePrescriptionItem[]>([createEmptyItem()]);
  const [validationErrors, setValidationErrors] = useState<string[]>([]);
  const [safetyWarning, setSafetyWarning] = useState<string | null>(null);

  const { data: medicationsPage } = useDoctorMedications({ page: '0', size: '100' });
  const createPrescription = useCreatePrescription(encounterId);
  const updatePrescription = useUpdatePrescription(encounterId);

  const medications = useMemo(
    () => (medicationsPage?.items ?? []).filter((item) => item.status !== 'INACTIVE'),
    [medicationsPage?.items],
  );

  useEffect(() => {
    if (!open) return;
    setGeneralNote(editItem?.generalNote || '');
    setItems(mapPrescriptionItems(editItem));
    setValidationErrors([]);
    setSafetyWarning(null);
  }, [editItem, open]);

  const addItem = () => {
    setItems((prev) => [...prev, createEmptyItem()]);
  };

  const removeItem = (index: number) => {
    setItems((prev) => prev.filter((_, idx) => idx !== index));
  };

  const updateItemField = (
    index: number,
    key: keyof EditablePrescriptionItem,
    value: string,
  ) => {
    setItems((prev) => {
      const next = [...prev];
      next[index] = { ...next[index], [key]: value };

      if (key === 'medicationId') {
        const medication = medications.find((item) => item.id === value);
        next[index].medicationName = medication?.name || '';
      }

      return next;
    });
  };

  const validate = () => {
    const errors: string[] = [];

    if (items.length === 0) {
      errors.push('Cần có ít nhất 1 thuốc trong đơn.');
    }

    items.forEach((item, index) => {
      const row = index + 1;
      if (!item.medicationId) errors.push(`Dòng ${row}: chưa chọn thuốc.`);
      if (Number(item.quantity) <= 0) errors.push(`Dòng ${row}: số lượng phải lớn hơn 0.`);
      if (Number(item.durationDays) <= 0) errors.push(`Dòng ${row}: số ngày phải lớn hơn 0.`);
    });

    setValidationErrors(errors);
    return errors.length === 0;
  };

  const submit = async () => {
    setSafetyWarning(null);
    if (!validate()) return;

    const body = {
      generalNote: generalNote || undefined,
      items: items.map((item) => ({
        medicationId: Number(item.medicationId),
        quantity: Number(item.quantity),
        dose: item.dose || undefined,
        frequency: item.frequency || undefined,
        durationDays: Number(item.durationDays),
        route: item.route || undefined,
        instruction: item.instruction || undefined,
      })),
    };

    try {
      if (editItem) {
        await updatePrescription.mutateAsync({
          prescriptionId: editItem.id,
          body,
        });
      } else {
        await createPrescription.mutateAsync(body);
      }

      onOpenChange(false);
      onSaved?.();
    } catch (error) {
      const warning = getPrescriptionSafetyWarning(error);
      if (warning) {
        setSafetyWarning(warning);
        return;
      }

      setValidationErrors([getApiErrorMessage(error, 'Không thể lưu đơn thuốc')]);
    }
  };

  const isSaving = createPrescription.isPending || updatePrescription.isPending;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-4xl">
        <DialogHeader>
          <DialogTitle>{editItem ? 'Cập nhật đơn thuốc' : 'Kê đơn thuốc'}</DialogTitle>
        </DialogHeader>

        <div className="space-y-4">
          {safetyWarning && (
            <div className="flex items-start gap-3 rounded-md border border-destructive/20 bg-destructive/10 p-4 text-destructive">
              <TriangleAlert className="mt-0.5 h-5 w-5 shrink-0" />
              <div>
                <p className="font-semibold">Cảnh báo an toàn thuốc</p>
                <p className="mt-1 text-sm">{safetyWarning}</p>
              </div>
            </div>
          )}

          {validationErrors.length > 0 && (
            <div className="rounded-md border border-warning/20 bg-warning/10 p-3 text-sm text-warning">
              <p className="font-medium">Cần kiểm tra lại đơn thuốc</p>
              <ul className="mt-2 list-disc space-y-1 pl-5">
                {validationErrors.map((error) => (
                  <li key={error}>{error}</li>
                ))}
              </ul>
            </div>
          )}

          <div>
            <label className="mb-1.5 block text-sm font-medium">Ghi chú chung</label>
            <Textarea
              value={generalNote}
              onChange={(event) => setGeneralNote(event.target.value)}
              rows={3}
              placeholder="Dặn dò chung cho đơn thuốc"
            />
          </div>

          <div className="space-y-3">
            {items.map((item, index) => (
              <div key={`${index}-${item.medicationId}`} className="rounded-lg border p-4">
                <div className="mb-3 flex items-center justify-between gap-3">
                  <p className="font-medium">Thuốc #{index + 1}</p>
                  {items.length > 1 && (
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      onClick={() => removeItem(index)}
                      className="text-muted-foreground hover:text-destructive"
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  )}
                </div>

                <div className="grid grid-cols-1 gap-3 md:grid-cols-[minmax(0,1fr)_120px_120px]">
                  <div>
                    <label className="mb-1.5 block text-xs font-medium text-muted-foreground">
                      Thuốc
                    </label>
                    <Select
                      value={item.medicationId}
                      onValueChange={(value) => updateItemField(index, 'medicationId', value)}
                    >
                      <SelectTrigger className="h-9">
                        <SelectValue placeholder="Chọn thuốc" />
                      </SelectTrigger>
                      <SelectContent>
                        {medications.map((medication) => (
                          <SelectItem key={medication.id} value={medication.id}>
                            {resolveMedicationLabel(medication)}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                  <div>
                    <label className="mb-1.5 block text-xs font-medium text-muted-foreground">
                      Số lượng
                    </label>
                    <Input
                      className="h-9"
                      min={1}
                      type="number"
                      value={item.quantity}
                      onChange={(event) => updateItemField(index, 'quantity', event.target.value)}
                    />
                  </div>
                  <div>
                    <label className="mb-1.5 block text-xs font-medium text-muted-foreground">
                      Số ngày
                    </label>
                    <Input
                      className="h-9"
                      min={1}
                      type="number"
                      value={item.durationDays}
                      onChange={(event) => updateItemField(index, 'durationDays', event.target.value)}
                    />
                  </div>
                </div>

                <div className="mt-3 grid grid-cols-1 gap-3 md:grid-cols-3">
                  <div>
                    <label className="mb-1.5 block text-xs font-medium text-muted-foreground">
                      Liều
                    </label>
                    <Input
                      className="h-9"
                      value={item.dose}
                      onChange={(event) => updateItemField(index, 'dose', event.target.value)}
                      placeholder="VD: 1 viên"
                    />
                  </div>
                  <div>
                    <label className="mb-1.5 block text-xs font-medium text-muted-foreground">
                      Tần suất
                    </label>
                    <Input
                      className="h-9"
                      value={item.frequency}
                      onChange={(event) => updateItemField(index, 'frequency', event.target.value)}
                      placeholder="VD: 2 lần/ngày"
                    />
                  </div>
                  <div>
                    <label className="mb-1.5 block text-xs font-medium text-muted-foreground">
                      Đường dùng
                    </label>
                    <Input
                      className="h-9"
                      value={item.route}
                      onChange={(event) => updateItemField(index, 'route', event.target.value)}
                    />
                  </div>
                </div>

                <div className="mt-3">
                  <label className="mb-1.5 block text-xs font-medium text-muted-foreground">
                    Hướng dẫn
                  </label>
                  <Input
                    className="h-9"
                    value={item.instruction}
                    onChange={(event) => updateItemField(index, 'instruction', event.target.value)}
                    placeholder="VD: uống sau ăn"
                  />
                </div>
              </div>
            ))}
          </div>

          <Button type="button" variant="outline" onClick={addItem} className="w-full">
            <Plus className="mr-2 h-4 w-4" />
            Thêm thuốc
          </Button>
        </div>

        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
            Hủy
          </Button>
          <Button type="button" onClick={() => void submit()} disabled={isSaving}>
            Lưu đơn thuốc
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
