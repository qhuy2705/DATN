import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ClipboardList, Plus, Trash2, TriangleAlert } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Accordion } from '@/components/ui/accordion';
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import {
  ClinicalNoteSection,
  ConclusionSection,
  DiagnosisSection,
  EncounterHeader,
  EncounterStatusBanner,
  EncounterTimelineSection,
  PatientSummaryCard,
  PrescriptionSection,
  QuickEncounterSection,
  ResultsSection,
  ServiceOrdersSection,
  VitalsSection,
  type EncounterFormState,
  type ServiceOrderDraftItem,
} from '@/components/doctor/encounter/EncounterDetailSections';
import { PrescriptionEditorDialog } from '@/components/doctor/PrescriptionEditorDialog';
import {
  useCompleteEncounter,
  useCreateDoctorServiceOrder,
  useDoctorEncounter,
  useDoctorEncounterRealtime,
  useDoctorServiceOrders,
  useReopenEncounter,
  useSaveEncounterDiagnoses,
  useUpdateEncounter,
  useValidateVitals,
} from '@/hooks/use-doctor-data';
import { useMedicalServices } from '@/hooks/use-public-data';
import type { Encounter, EncounterDiagnosisResponse } from '@/types/api';

type PendingVitalsAction = 'save' | 'complete' | 'leave' | null;

function normalizeDiagnosisType(
  value: EncounterDiagnosisResponse['diagnosisType'] | 'COMORBIDITY',
): EncounterDiagnosisResponse['diagnosisType'] {
  if (value === 'COMORBIDITY') return 'SECONDARY';
  if (value === 'PRELIMINARY' || value === 'FINAL' || value === 'SECONDARY') return value;
  return 'PRELIMINARY';
}

function createEmptyEncounterForm(): EncounterFormState {
  return {
    intakeReasonForVisit: '',
    visitType: '',
    triagePriority: '',
    triageNote: '',
    insuranceNote: '',
    emergencyContactName: '',
    emergencyContactPhone: '',
    chiefComplaint: '',
    clinicalNote: '',
    diagnoses: [],
    conclusion: '',
    heightCm: '',
    weightKg: '',
    temperatureC: '',
    pulse: '',
    systolicBp: '',
    diastolicBp: '',
    respiratoryRate: '',
    spo2: '',
    allergySnapshot: '',
    chronicDiseaseSnapshot: '',
    pastMedicalHistory: '',
    physicalExamination: '',
    treatmentPlan: '',
    followUpDate: '',
    followUpNote: '',
  };
}

function buildEncounterForm(encounter: Encounter): EncounterFormState {
  return {
    ...createEmptyEncounterForm(),
    intakeReasonForVisit: encounter.intakeReasonForVisit || '',
    visitType: encounter.visitType || '',
    triagePriority: encounter.triagePriority || '',
    triageNote: encounter.triageNote || '',
    insuranceNote: encounter.insuranceNote || '',
    emergencyContactName: encounter.emergencyContactName || '',
    emergencyContactPhone: encounter.emergencyContactPhone || '',
    chiefComplaint: encounter.chiefComplaint || '',
    clinicalNote: encounter.clinicalNote || '',
    diagnoses: (encounter.diagnoses || []).map((diagnosis) => ({
      ...diagnosis,
      diagnosisType: normalizeDiagnosisType(
        diagnosis.diagnosisType as EncounterDiagnosisResponse['diagnosisType'] | 'COMORBIDITY',
      ),
    })),
    conclusion: encounter.conclusion || '',
    heightCm: encounter.heightCm?.toString() || '',
    weightKg: encounter.weightKg?.toString() || '',
    temperatureC: encounter.temperatureC?.toString() || '',
    pulse: encounter.pulse?.toString() || '',
    systolicBp: encounter.systolicBp?.toString() || '',
    diastolicBp: encounter.diastolicBp?.toString() || '',
    respiratoryRate: encounter.respiratoryRate?.toString() || '',
    spo2: encounter.spo2?.toString() || '',
    allergySnapshot: encounter.allergySnapshot || '',
    chronicDiseaseSnapshot: encounter.chronicDiseaseSnapshot || '',
    pastMedicalHistory: encounter.pastMedicalHistory || '',
    physicalExamination: encounter.physicalExamination || '',
    treatmentPlan: encounter.treatmentPlan || '',
    followUpDate: encounter.followUpDate || '',
    followUpNote: encounter.followUpNote || '',
  };
}

function getCompletionBlockingReasons(encounter: Encounter, form: EncounterFormState) {
  const reasons: string[] = [];
  const hasFinalDiagnosis =
    form.diagnoses.some((diagnosis) => diagnosis.diagnosisType === 'FINAL') ||
    Boolean(encounter.finalDiagnosis?.trim());

  if (!hasFinalDiagnosis) reasons.push('Chưa có chẩn đoán chính ICD-10');
  if (!form.conclusion.trim() && !encounter.conclusion?.trim()) reasons.push('Chưa có kết luận');
  if (encounter.hasPendingPayment) reasons.push('Còn chỉ định dịch vụ chờ thanh toán');
  if (encounter.hasWaitingResults) reasons.push('Còn dịch vụ chờ kết quả');
  if (encounter.status === 'COMPLETED') reasons.push('Hồ sơ đã hoàn tất');
  if (encounter.status === 'CANCELLED') reasons.push('Hồ sơ đã hủy');

  return reasons;
}

function getPrescriptionBlockReason(encounter?: Encounter) {
  if (!encounter) return '';
  if (encounter.status === 'COMPLETED') return 'Hồ sơ đã hoàn tất, không thể kê thêm đơn.';
  if (encounter.status === 'CANCELLED') return 'Hồ sơ đã hủy, không thể kê đơn.';
  if (encounter.hasPendingPayment) return 'Còn chỉ định dịch vụ chờ thanh toán.';
  if (encounter.hasWaitingResults) return 'Còn dịch vụ chờ kết quả.';
  if (encounter.canCreatePrescription === false) return 'Hồ sơ chưa đủ điều kiện kê đơn thuốc.';
  return '';
}

export default function EncounterDetailPage() {
  const { id = '' } = useParams();
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();

  const { data: encounter, isLoading } = useDoctorEncounter(id);
  const { data: serviceOrders = [] } = useDoctorServiceOrders(id);
  useDoctorEncounterRealtime(id);
  const { data: medicalServices = [] } = useMedicalServices();

  const saveEncounter = useUpdateEncounter(id);
  const saveDiagnoses = useSaveEncounterDiagnoses(id);
  const completeEncounter = useCompleteEncounter(id);
  const createServiceOrder = useCreateDoctorServiceOrder(id);
  const reopenEncounter = useReopenEncounter(id);
  const validateVitals = useValidateVitals(id);

  const [orderDialogOpen, setOrderDialogOpen] = useState(false);
  const [prescriptionDialogOpen, setPrescriptionDialogOpen] = useState(false);
  const [reopenDialogOpen, setReopenDialogOpen] = useState(false);
  const [reopenReason, setReopenReason] = useState('');
  const [vitalsWarningOpen, setVitalsWarningOpen] = useState(false);
  const [vitalsWarnings, setVitalsWarnings] = useState<{ field: string; value: string; warningMessage: string }[]>([]);
  const [pendingVitalsAction, setPendingVitalsAction] = useState<PendingVitalsAction>(null);
  const [savedFormSnapshot, setSavedFormSnapshot] = useState('');
  const [loadedEncounterId, setLoadedEncounterId] = useState('');
  const [orderNote, setOrderNote] = useState('');
  const [orderItems, setOrderItems] = useState<ServiceOrderDraftItem[]>([
    { medicalServiceId: '', quantity: '1', note: '' },
  ]);
  const [form, setForm] = useState<EncounterFormState>(() => createEmptyEncounterForm());

  useEffect(() => {
    if (!encounter) return;
    const nextForm = buildEncounterForm(encounter);
    const nextSnapshot = JSON.stringify(nextForm);
    const currentDirty = Boolean(savedFormSnapshot) && JSON.stringify(form) !== savedFormSnapshot;

    if (loadedEncounterId === encounter.id && currentDirty) return;
    if (loadedEncounterId === encounter.id && savedFormSnapshot === nextSnapshot) return;

    setForm(nextForm);
    setSavedFormSnapshot(nextSnapshot);
    setLoadedEncounterId(encounter.id);
  }, [encounter, form, loadedEncounterId, savedFormSnapshot]);

  const orderedServiceIds = useMemo(
    () =>
      new Set(
        serviceOrders.flatMap((order) => order.items.map((item) => item.medicalServiceId)),
      ),
    [serviceOrders],
  );

  const selectableServices = useMemo(
    () => medicalServices.filter((service) => !orderedServiceIds.has(service.id)),
    [medicalServices, orderedServiceIds],
  );

  const canEdit = encounter?.status !== 'COMPLETED' && encounter?.status !== 'CANCELLED';
  const canOrderServices =
    !!encounter &&
    canEdit &&
    !encounter.hasPendingPayment &&
    !encounter.hasWaitingResults;
  const canCreatePrescription = !!encounter?.canCreatePrescription && canEdit;
  const prescriptionBlockReason = getPrescriptionBlockReason(encounter);
  const formDirty = savedFormSnapshot ? JSON.stringify(form) !== savedFormSnapshot : false;
  const completionBlockingReasons = encounter
    ? getCompletionBlockingReasons(encounter, form)
    : [];
  const backendCompletionBlocked =
    !!encounter &&
    encounter.canComplete === false &&
    completionBlockingReasons.length === 0 &&
    !formDirty;
  const completionDisplayReasons = backendCompletionBlocked
    ? ['Hệ thống chưa cho phép hoàn tất hồ sơ trong trạng thái hiện tại']
    : completionBlockingReasons;
  const canComplete = !!encounter && completionDisplayReasons.length === 0;
  const isSavingWorkspace =
    saveEncounter.isPending ||
    saveDiagnoses.isPending ||
    validateVitals.isPending;

  const toBody = () => ({
    intakeReasonForVisit: form.intakeReasonForVisit || undefined,
    visitType: form.visitType || undefined,
    triagePriority: form.triagePriority || undefined,
    triageNote: form.triageNote || undefined,
    insuranceNote: form.insuranceNote || undefined,
    emergencyContactName: form.emergencyContactName || undefined,
    emergencyContactPhone: form.emergencyContactPhone || undefined,
    chiefComplaint: form.chiefComplaint || undefined,
    clinicalNote: form.clinicalNote || undefined,
    conclusion: form.conclusion || undefined,
    heightCm: form.heightCm ? Number(form.heightCm) : undefined,
    weightKg: form.weightKg ? Number(form.weightKg) : undefined,
    temperatureC: form.temperatureC ? Number(form.temperatureC) : undefined,
    pulse: form.pulse ? Number(form.pulse) : undefined,
    systolicBp: form.systolicBp ? Number(form.systolicBp) : undefined,
    diastolicBp: form.diastolicBp ? Number(form.diastolicBp) : undefined,
    respiratoryRate: form.respiratoryRate ? Number(form.respiratoryRate) : undefined,
    spo2: form.spo2 ? Number(form.spo2) : undefined,
    allergySnapshot: form.allergySnapshot || undefined,
    chronicDiseaseSnapshot: form.chronicDiseaseSnapshot || undefined,
    pastMedicalHistory: form.pastMedicalHistory || undefined,
    physicalExamination: form.physicalExamination || undefined,
    treatmentPlan: form.treatmentPlan || undefined,
    followUpDate: form.followUpDate || undefined,
    followUpNote: form.followUpNote || undefined,
  });

  const resetOrderForm = () => {
    setOrderNote('');
    setOrderItems([{ medicalServiceId: '', quantity: '1', note: '' }]);
  };

  const addOrderItem = () => {
    setOrderItems((prev) => [...prev, { medicalServiceId: '', quantity: '1', note: '' }]);
  };

  const removeOrderItem = (index: number) => {
    setOrderItems((prev) => prev.filter((_, idx) => idx !== index));
  };

  const updateOrderItem = (index: number, key: keyof ServiceOrderDraftItem, value: string) => {
    setOrderItems((prev) => {
      const next = [...prev];
      next[index] = { ...next[index], [key]: value };
      return next;
    });
  };

  const submitServiceOrder = async () => {
    await createServiceOrder.mutateAsync({
      note: orderNote || undefined,
      items: orderItems
        .filter((item) => item.medicalServiceId)
        .map((item) => ({
          medicalServiceId: Number(item.medicalServiceId),
          quantity: Number(item.quantity || '1'),
          note: item.note || undefined,
        })),
    });
    setOrderDialogOpen(false);
    resetOrderForm();
  };

  const runAfterSuccessfulSave = async (action: PendingVitalsAction) => {
    if (action === 'leave') {
      navigate('/app/doctor/appointments');
      return;
    }

    if (action === 'complete') {
      await completeEncounter.mutateAsync(toBody());
    }
  };

  const saveWorkspace = async (
    action: PendingVitalsAction = 'save',
    skipVitalsValidation = false,
  ) => {
    const body = toBody();

    if (!skipVitalsValidation) {
      try {
        const res = await validateVitals.mutateAsync(body);
        if (Array.isArray(res) && res.length > 0) {
          setVitalsWarnings(res);
          setPendingVitalsAction(action);
          setVitalsWarningOpen(true);
          return false;
        }
      } catch {
        // If validation service is unavailable, keep the doctor workflow moving and let save API validate.
      }
    }

    await saveEncounter.mutateAsync(body);
    await saveDiagnoses.mutateAsync({
      current: form.diagnoses,
      previous: encounter?.diagnoses ?? [],
    });
    setSavedFormSnapshot(JSON.stringify(form));
    await runAfterSuccessfulSave(action);
    return true;
  };

  const handleSaveClick = async () => {
    try {
      await saveWorkspace('save');
    } catch {
      // Mutation hooks surface concrete API errors.
    }
  };

  const handleCompleteClick = async () => {
    if (!canComplete) return;

    try {
      await saveWorkspace('complete');
    } catch {
      // Mutation hooks surface concrete API errors.
    }
  };

  const handleLeaveWorkspace = async () => {
    if (!formDirty) {
      navigate('/app/doctor/appointments');
      return;
    }

    const shouldSave = window.confirm(
      'Bạn có thay đổi chưa lưu. Lưu trước khi rời hồ sơ? Chọn OK để lưu và quay lại danh sách, Cancel để ở lại hồ sơ.',
    );

    if (!shouldSave) return;

    try {
      await saveWorkspace('leave');
    } catch {
      // Mutation hooks surface concrete API errors.
    }
  };

  const confirmVitalsAndContinue = async () => {
    const action = pendingVitalsAction ?? 'save';
    setVitalsWarningOpen(false);
    setPendingVitalsAction(null);

    try {
      await saveWorkspace(action, true);
    } catch {
      // Mutation hooks surface concrete API errors.
    }
  };

  if (isLoading) {
    return <div className="py-12 text-center text-muted-foreground">Đang tải...</div>;
  }

  if (!encounter) {
    return <div className="py-12 text-center text-muted-foreground">{t('common.noData')}</div>;
  }

  const patientMeta = [
    encounter.patientPhone,
    encounter.patientEmail,
    encounter.patientDob ? new Date(encounter.patientDob).toLocaleDateString('vi-VN') : undefined,
  ].filter(Boolean).join(' · ');

  const encounterTimeLabel = encounter.startedAt
    ? new Date(encounter.startedAt).toLocaleString('vi-VN')
    : encounter.createdAt || '';

  return (
    <div className="mx-auto max-w-none space-y-4">
      <EncounterHeader
        encounter={encounter}
        encounterTitle={t('modules.encounter.title')}
        patientMeta={patientMeta}
        encounterTimeLabel={encounterTimeLabel}
        actionProps={{
          encounter,
          canEdit,
          canOrderServices,
          canCreatePrescription,
          canComplete,
          isSavingWorkspace,
          completePending: completeEncounter.isPending,
          formDirty,
          onSave: () => void handleSaveClick(),
          onOrderServices: () => setOrderDialogOpen(true),
          onCreatePrescription: () => setPrescriptionDialogOpen(true),
          onLeave: () => void handleLeaveWorkspace(),
          onReopen: () => setReopenDialogOpen(true),
          onComplete: () => void handleCompleteClick(),
        }}
      />

      <EncounterStatusBanner
        encounter={encounter}
        canComplete={canComplete}
        completionReasons={completionDisplayReasons}
      />

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,1fr)_380px]">
        <div className="min-w-0 space-y-4">
          <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
            <ClipboardList className="h-4 w-4 text-primary" />
            Workspace khám
          </div>

          <Accordion
            type="multiple"
            defaultValue={['quick', 'exam', 'diagnosis', 'plan']}
            className="space-y-3"
          >
            <QuickEncounterSection
              form={form}
              setForm={setForm}
              encounter={encounter}
              canEdit={canEdit}
            />
            <VitalsSection form={form} setForm={setForm} canEdit={canEdit} />
            <ClinicalNoteSection form={form} setForm={setForm} canEdit={canEdit} />
            <DiagnosisSection form={form} setForm={setForm} canEdit={canEdit} />
            <ConclusionSection form={form} setForm={setForm} canEdit={canEdit} />
          </Accordion>
        </div>

        <aside className="space-y-4 xl:sticky xl:top-24 xl:self-start">
          <PatientSummaryCard encounter={encounter} />
          <ServiceOrdersSection
            orders={serviceOrders}
            canCreate={canOrderServices}
            onCreate={() => setOrderDialogOpen(true)}
            language={i18n.language}
          />
          <ResultsSection orders={serviceOrders} language={i18n.language} />
          <PrescriptionSection
            encounterId={encounter.id}
            canCreatePrescription={canCreatePrescription}
            canEdit={canEdit}
          />
          <EncounterTimelineSection encounterId={encounter.id} />
          {prescriptionBlockReason ? (
            <p className="rounded-lg border bg-muted/25 px-3 py-2 text-sm text-muted-foreground">
              Kê đơn: {prescriptionBlockReason}
            </p>
          ) : null}
        </aside>
      </div>

      <PrescriptionEditorDialog
        encounterId={encounter.id}
        open={prescriptionDialogOpen}
        onOpenChange={setPrescriptionDialogOpen}
      />

      <Dialog open={orderDialogOpen} onOpenChange={setOrderDialogOpen}>
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>Tạo chỉ định dịch vụ</DialogTitle>
          </DialogHeader>

          <div className="space-y-4">
            {orderItems.map((item, index) => (
              <div key={`${index}-${item.medicalServiceId}`} className="rounded-lg border p-4">
                <div className="mb-3 flex items-center justify-between">
                  <p className="font-medium">Dịch vụ #{index + 1}</p>
                  {orderItems.length > 1 && (
                    <Button variant="ghost" size="icon" onClick={() => removeOrderItem(index)}>
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  )}
                </div>

                <div className="grid grid-cols-1 gap-4 md:grid-cols-[minmax(0,1fr)_120px]">
                  <div>
                    <label className="mb-1.5 block text-sm font-medium">Dịch vụ</label>
                    <Select
                      value={item.medicalServiceId}
                      onValueChange={(value) => updateOrderItem(index, 'medicalServiceId', value)}
                    >
                      <SelectTrigger>
                        <SelectValue placeholder="Chọn dịch vụ" />
                      </SelectTrigger>
                      <SelectContent>
                        {selectableServices.map((service) => (
                          <SelectItem key={service.id} value={service.id}>
                            {service.name}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                  <div>
                    <label className="mb-1.5 block text-sm font-medium">Số lượng</label>
                    <Input
                      type="number"
                      min={1}
                      value={item.quantity}
                      onChange={(event) => updateOrderItem(index, 'quantity', event.target.value)}
                    />
                  </div>
                </div>

                <div className="mt-4">
                  <label className="mb-1.5 block text-sm font-medium">Ghi chú</label>
                  <Input
                    value={item.note}
                    onChange={(event) => updateOrderItem(index, 'note', event.target.value)}
                  />
                </div>
              </div>
            ))}

            <Button variant="outline" onClick={addOrderItem} disabled={selectableServices.length === 0}>
              <Plus className="mr-2 h-4 w-4" />
              Thêm dịch vụ
            </Button>

            <div>
              <label className="mb-1.5 block text-sm font-medium">Ghi chú phiếu chỉ định</label>
              <Textarea value={orderNote} onChange={(event) => setOrderNote(event.target.value)} rows={3} />
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => { setOrderDialogOpen(false); resetOrderForm(); }}>
              Hủy
            </Button>
            <Button
              onClick={submitServiceOrder}
              disabled={createServiceOrder.isPending || orderItems.every((item) => !item.medicalServiceId)}
            >
              Tạo chỉ định
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={vitalsWarningOpen} onOpenChange={setVitalsWarningOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-destructive">
              <TriangleAlert className="h-5 w-5" />
              Cảnh báo Sinh hiệu bất thường
            </DialogTitle>
          </DialogHeader>
          <div className="space-y-4 text-sm">
            <p>Hệ thống phát hiện một số chỉ số sinh hiệu vượt ngưỡng lâm sàng an toàn. Bác sĩ có chắc chắn muốn lưu các chỉ số này?</p>
            <ul className="list-disc space-y-1 pl-5 text-muted-foreground">
              {vitalsWarnings.map((warning, index) => (
                <li key={`${warning.field}-${index}`}>
                  <strong>{warning.field}:</strong> {warning.value} - {warning.warningMessage}
                </li>
              ))}
            </ul>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setVitalsWarningOpen(false)}>Quay lại sửa</Button>
            <Button variant="destructive" onClick={() => void confirmVitalsAndContinue()} disabled={isSavingWorkspace}>
              Vẫn tiếp tục lưu
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={reopenDialogOpen} onOpenChange={setReopenDialogOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Mở lại hồ sơ khám</DialogTitle>
          </DialogHeader>
          <div className="space-y-4 text-sm">
            <p className="text-muted-foreground">
              Hồ sơ này đã được đánh dấu là HOÀN TẤT. Nếu bạn muốn mở lại để bổ sung kết quả hoặc điều chỉnh chẩn đoán, vui lòng nhập lý do bên dưới.
            </p>
            <Textarea
              placeholder="Nhập lý do mở lại..."
              value={reopenReason}
              onChange={(event) => setReopenReason(event.target.value)}
              rows={3}
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setReopenDialogOpen(false)}>Hủy</Button>
            <Button
              onClick={() => {
                reopenEncounter.mutate(reopenReason);
                setReopenDialogOpen(false);
              }}
              disabled={!reopenReason.trim() || reopenEncounter.isPending}
            >
              Xác nhận mở lại
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
