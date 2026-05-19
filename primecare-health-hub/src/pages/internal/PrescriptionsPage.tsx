import { useSearchParams, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ArrowLeft } from 'lucide-react';
import { PageHeader } from '@/components/PageHeader';
import { Button } from '@/components/ui/button';
import { EncounterPrescriptionsPanel } from '@/components/doctor/EncounterPrescriptionsPanel';
import { useDoctorEncounter } from '@/hooks/use-doctor-data';

export default function PrescriptionsPage() {
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const encounterId = searchParams.get('encounterId') || '';
  const { data: encounter } = useDoctorEncounter(encounterId);

  if (!encounterId) {
    return (
      <div>
        <PageHeader
          title={t('modules.prescriptions.title')}
          description={t('modules.prescriptions.desc')}
        />
        <p className="text-muted-foreground">
          Mở đơn thuốc từ hồ sơ khám để xem, kê và cập nhật đơn thuốc theo từng lượt khám.
        </p>
      </div>
    );
  }

  const canEdit = encounter?.status !== 'COMPLETED' && encounter?.status !== 'CANCELLED';
  const canCreatePrescription = Boolean(encounter?.canCreatePrescription && canEdit);
  const createBlockedReason =
    encounter?.canCreatePrescription === false && (encounter.issuedPrescriptionCount ?? 0) > 0
      ? 'Lần khám đã có đơn thuốc đang hiệu lực.'
      : encounter?.canCreatePrescription === false
        ? 'Không thể tạo đơn thuốc mới cho lần khám này.'
        : undefined;

  return (
    <div className="space-y-6">
      <PageHeader
        title={t('modules.prescriptions.title')}
        description={`${t('modules.prescriptions.desc')} · Hồ sơ #${encounterId}`}
        actions={
          <Button
            type="button"
            variant="outline"
            onClick={() => navigate(`/app/doctor/encounters/${encounterId}`)}
          >
            <ArrowLeft className="mr-2 h-4 w-4" />
            Quay lại hồ sơ khám
          </Button>
        }
      />
      <EncounterPrescriptionsPanel
        encounterId={encounterId}
        canCreatePrescription={canCreatePrescription}
        canEdit={Boolean(canEdit)}
        createBlockedReason={createBlockedReason}
      />
    </div>
  );
}
