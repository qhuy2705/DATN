import { useState } from 'react';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import {
  useDoctorPatientAllergies,
  useAddDoctorPatientAllergy,
  useDeleteDoctorPatientAllergy,
} from '@/hooks/use-doctor-data';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Loader2, Trash2 } from 'lucide-react';
import { PatientAllergyResponse } from '@/types/api';

export function PatientAllergyModal({
  patientId,
  open,
  onOpenChange,
}: {
  patientId: string | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { data: allergies, isLoading } = useDoctorPatientAllergies(patientId ?? undefined);
  const addAllergy = useAddDoctorPatientAllergy(patientId || '');
  const deleteAllergy = useDeleteDoctorPatientAllergy(patientId || '');

  const [form, setForm] = useState({
    allergenName: '',
    allergyType: 'DRUG',
    severity: 'MILD',
    reaction: '',
  });

  const handleAdd = async () => {
    if (!form.allergenName.trim() || !patientId) return;
    await addAllergy.mutateAsync(form);
    setForm({
      allergenName: '',
      allergyType: 'DRUG',
      severity: 'MILD',
      reaction: '',
    });
  };

  const allergyTypeMap: Record<string, string> = {
    DRUG: 'Thuốc',
    FOOD: 'Thực phẩm',
    ENVIRONMENTAL: 'Môi trường',
    OTHER: 'Khác',
  };

  const severityMap: Record<string, string> = {
    MILD: 'Nhẹ',
    MODERATE: 'Vừa',
    SEVERE: 'Nặng',
    LIFE_THREATENING: 'Đe dọa tính mạng',
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-3xl">
        <DialogHeader>
          <DialogTitle>Quản lý Dị ứng Bệnh nhân</DialogTitle>
        </DialogHeader>
        <div className="space-y-6">
          <div className="grid grid-cols-1 md:grid-cols-5 gap-3 items-end">
            <div className="md:col-span-2">
              <label className="text-xs font-medium mb-1 block">Tác nhân gây dị ứng (*)</label>
              <Input
                placeholder="VD: Penicillin"
                value={form.allergenName}
                onChange={(e) => setForm({ ...form, allergenName: e.target.value })}
              />
            </div>
            <div>
              <label className="text-xs font-medium mb-1 block">Loại</label>
              <Select value={form.allergyType} onValueChange={(v) => setForm({ ...form, allergyType: v })}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="DRUG">Thuốc</SelectItem>
                  <SelectItem value="FOOD">Thực phẩm</SelectItem>
                  <SelectItem value="ENVIRONMENTAL">Môi trường</SelectItem>
                  <SelectItem value="OTHER">Khác</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div>
              <label className="text-xs font-medium mb-1 block">Mức độ</label>
              <Select value={form.severity} onValueChange={(v) => setForm({ ...form, severity: v })}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="MILD">Nhẹ</SelectItem>
                  <SelectItem value="MODERATE">Vừa</SelectItem>
                  <SelectItem value="SEVERE">Nặng</SelectItem>
                  <SelectItem value="LIFE_THREATENING">Đe dọa tính mạng</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <Button onClick={handleAdd} disabled={addAllergy.isPending || !form.allergenName.trim()}>
              Thêm
            </Button>
          </div>
          <div className="col-span-full">
            <label className="text-xs font-medium mb-1 block">Biểu hiện / Phản ứng</label>
            <Input
              placeholder="VD: Nổi mề đay, khó thở..."
              value={form.reaction}
              onChange={(e) => setForm({ ...form, reaction: e.target.value })}
            />
          </div>

          <div className="border rounded-md mt-6">
            <div className="bg-muted px-4 py-2 text-sm font-medium border-b flex items-center justify-between">
              <span>Danh sách dị ứng đã ghi nhận</span>
              <span className="text-xs text-muted-foreground">{allergies?.length || 0} mục</span>
            </div>
            {isLoading ? (
              <div className="p-8 flex justify-center text-muted-foreground">
                <Loader2 className="h-6 w-6 animate-spin" />
              </div>
            ) : !allergies || allergies.length === 0 ? (
              <div className="p-8 text-center text-sm text-muted-foreground">
                Chưa ghi nhận tiền sử dị ứng nào.
              </div>
            ) : (
              <ul className="divide-y max-h-[300px] overflow-y-auto">
                {allergies.map((a: PatientAllergyResponse) => (
                  <li key={a.id} className="p-4 flex items-start gap-4">
                    <div className="flex-1 space-y-1 text-sm">
                      <div className="flex items-center gap-2">
                        <span className="font-semibold text-foreground">{a.allergenName}</span>
                        <span className="text-xs px-2 rounded bg-muted">
                          {allergyTypeMap[a.allergyType]}
                        </span>
                        <span className={`text-xs px-2 rounded ${
                          a.severity === 'SEVERE' || a.severity === 'LIFE_THREATENING'
                            ? 'bg-destructive/10 text-destructive'
                            : 'bg-warning/10 text-warning'
                        }`}>
                          {severityMap[a.severity]}
                        </span>
                      </div>
                      {a.reaction && <p className="text-muted-foreground text-xs mt-1">Biểu hiện: {a.reaction}</p>}
                      {a.notedByName && (
                        <p className="text-xs text-muted-foreground mt-1">
                          Ghi nhận bởi: {a.notedByName} {a.createdAt ? `(${new Date(a.createdAt).toLocaleDateString('vi-VN')})` : ''}
                        </p>
                      )}
                    </div>
                    <Button
                      variant="ghost"
                      size="icon"
                      className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                      onClick={() => deleteAllergy.mutate(a.id)}
                      disabled={deleteAllergy.isPending}
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
