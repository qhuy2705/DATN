import { PageHeader } from '@/components/PageHeader';
import { EmptyState } from '@/components/EmptyState';

interface PlaceholderPageProps {
  title: string;
  description?: string;
}

export default function PlaceholderPage({ title, description }: PlaceholderPageProps) {
  return (
    <div>
      <PageHeader title={title} description={description} />
      <EmptyState
        title="Đang phát triển"
        description="Module này đang được phát triển. Vui lòng quay lại sau."
      />
    </div>
  );
}
