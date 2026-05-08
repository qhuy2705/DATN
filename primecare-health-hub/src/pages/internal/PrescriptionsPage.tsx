import { useSearchParams, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ArrowLeft } from 'lucide-react';
import { PageHeader } from '@/components/PageHeader';
import { Button } from '@/components/ui/button';
import { EncounterPrescriptionsPanel } from '@/components/doctor/EncounterPrescriptionsPanel';

export default function PrescriptionsPage() {
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const encounterId = searchParams.get('encounterId') || '';

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
        canCreatePrescription
        canEdit
      />
    </div>
  );
}
