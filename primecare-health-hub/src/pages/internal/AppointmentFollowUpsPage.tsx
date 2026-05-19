import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  ChevronLeft,
  ChevronRight,
  ExternalLink,
  Search,
} from 'lucide-react';
import { EmptyState } from '@/components/EmptyState';
import { ErrorState } from '@/components/ErrorState';
import { LoadingSkeleton } from '@/components/LoadingSkeleton';
import { PageHeader } from '@/components/PageHeader';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { useFollowUpQueue } from '@/hooks/use-reception-data';
import { useDebouncedValue } from '@/hooks/use-debounced-value';
import {
  badgeClass,
  formatHoldDeadline,
  getFollowUpTypeClass,
  getFollowUpTypeDisplay,
  getHoldStatusClass,
  getHoldStatusDisplay,
} from '@/lib/doctor-cancellation';
import type { FollowUpQueueItem } from '@/types/api';

type FollowUpCategory = 'ALL' | 'NO_SHOW' | 'DOCTOR_CANCELLATION' | 'CONTACT_REQUEST';

const PAGE_SIZE = 20;

const FOLLOW_UP_FILTERS: Array<{ value: FollowUpCategory; label: string }> = [
  { value: 'ALL', label: 'Tất cả' },
  { value: 'NO_SHOW', label: 'Không đến' },
  { value: 'DOCTOR_CANCELLATION', label: 'Bác sĩ hủy' },
  { value: 'CONTACT_REQUEST', label: 'Cần liên hệ' },
];

function compactDateTime(date?: string, start?: string, end?: string) {
  const time = [start, end].filter(Boolean).join(' - ');
  return [date, time].filter(Boolean).join(' · ') || 'Chưa có thông tin';
}

function formatDateTime(value?: string | null) {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('vi-VN');
}

function getHttpStatus(error: unknown) {
  if (typeof error !== 'object' || error === null) return undefined;
  const directStatus = (error as { status?: unknown }).status;
  if (typeof directStatus === 'number') return directStatus;
  const response = (error as { response?: { status?: unknown } }).response;
  if (typeof response?.status === 'number') return response.status;
  const statusCode = (error as { statusCode?: unknown }).statusCode;
  if (typeof statusCode === 'number') return statusCode;
  const responseData = (error as { response?: { data?: { status?: unknown; statusCode?: unknown } } })
    .response?.data;
  if (typeof responseData?.status === 'number') return responseData.status;
  if (typeof responseData?.statusCode === 'number') return responseData.statusCode;
  return undefined;
}

function getEmptyCopy(category: FollowUpCategory) {
  switch (category) {
    case 'NO_SHOW':
      return {
        title: 'Hiện không có ca không đến cần liên hệ lại.',
        description: 'Các lịch không đến còn cần nhân viên xử lý sẽ xuất hiện tại đây.',
      };
    case 'DOCTOR_CANCELLATION':
      return {
        title: 'Hiện không có lịch bác sĩ hủy đang chờ xử lý.',
        description: 'Những ca cần theo dõi phản hồi hoặc hỗ trợ sau khi bác sĩ hủy sẽ xuất hiện tại đây.',
      };
    case 'CONTACT_REQUEST':
      return {
        title: 'Hiện không có yêu cầu liên hệ nào đang chờ xử lý.',
        description: 'Các yêu cầu cần nhân viên gọi lại hoặc hỗ trợ tiếp sẽ xuất hiện tại đây.',
      };
    case 'ALL':
    default:
      return {
        title: 'Hiện không có lịch nào cần xử lý sau lịch.',
        description: 'Các ca no-show, bác sĩ hủy hoặc bệnh nhân yêu cầu liên hệ sẽ xuất hiện tại đây.',
      };
  }
}

function PaginationControls({
  page,
  totalPages,
  onPageChange,
}: {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}) {
  if (totalPages <= 1) return null;

  return (
    <div className="flex items-center justify-between rounded-lg border border-border bg-card px-4 py-3 shadow-sm">
      <p className="text-sm text-muted-foreground">
        Trang {page} / {totalPages}
      </p>
      <div className="flex gap-2">
        <Button
          variant="outline"
          size="icon"
          onClick={() => onPageChange(page - 1)}
          disabled={page <= 1}
          aria-label="Trang trước"
        >
          <ChevronLeft className="h-4 w-4" />
        </Button>
        <Button
          variant="outline"
          size="icon"
          onClick={() => onPageChange(page + 1)}
          disabled={page >= totalPages}
          aria-label="Trang sau"
        >
          <ChevronRight className="h-4 w-4" />
        </Button>
      </div>
    </div>
  );
}

function FollowUpQueueRow({
  item,
}: {
  item: FollowUpQueueItem;
}) {
  const isDoctorCancellation = String(item.followUpType).startsWith('DOCTOR_CANCELLATION');
  const appointmentPath = item.appointmentId
    ? `/app/appointments/${item.appointmentId}/process`
    : undefined;

  return (
    <div className="rounded-lg border border-border/70 bg-background p-4 text-sm">
      <div className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_auto] lg:items-start">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <span className={badgeClass(getFollowUpTypeClass(item.followUpType))}>
              {getFollowUpTypeDisplay(item.followUpType)}
            </span>
            {isDoctorCancellation ? (
              <span className={badgeClass(getHoldStatusClass(item.holdStatus))}>
                {getHoldStatusDisplay(item.holdStatus)}
              </span>
            ) : null}
            {item.expiresAt ? (
              <Badge variant="outline" className="rounded-full">
                Hạn xử lý {formatHoldDeadline(item.expiresAt)}
              </Badge>
            ) : null}
          </div>

          <div className="mt-3 grid gap-2 md:grid-cols-[minmax(0,1fr)_minmax(220px,0.8fr)]">
            <div className="min-w-0">
              <p className="font-semibold text-foreground">
                {item.patientFullName || 'Bệnh nhân'}
              </p>
              <p className="mt-1 break-words text-xs text-muted-foreground">
                {[item.patientPhone, item.patientEmail].filter(Boolean).join(' · ') ||
                  'Chưa có liên hệ'}
              </p>
            </div>
            <div className="min-w-0 text-xs text-muted-foreground md:text-right">
              <p className="font-medium text-foreground">
                {item.appointmentCode || item.appointmentId || 'Chưa có mã lịch'}
              </p>
              <p className="mt-1 break-words">
                {[item.specialtyName, item.doctorName].filter(Boolean).join(' · ') ||
                  'Chưa có thông tin chuyên khoa'}
              </p>
            </div>
          </div>

          <p className="mt-2 text-xs text-muted-foreground">
            Tạo lúc {formatDateTime(item.createdAt)}
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2 lg:justify-end">
          {appointmentPath ? (
            <Button asChild size="sm">
              <Link to={appointmentPath}>
                <ExternalLink className="mr-1.5 h-4 w-4" />
                Mở lịch hẹn
              </Link>
            </Button>
          ) : (
            <Button size="sm" variant="outline" disabled>
              <ExternalLink className="mr-1.5 h-4 w-4" />
              Mở lịch hẹn
            </Button>
          )}
        </div>
      </div>

      {isDoctorCancellation ? (
        <div className="mt-3 grid gap-2 rounded-lg bg-muted/30 p-3 text-xs text-muted-foreground md:grid-cols-2">
          <div>
            <p className="font-medium text-foreground">Lịch cũ</p>
            <p className="mt-1 break-words">
              {compactDateTime(item.originalVisitDate, item.originalSlotStart, item.originalSlotEnd)}
            </p>
          </div>
          <div>
            <p className="font-medium text-foreground">Slot từng được giữ</p>
            <p className="mt-1 break-words">
              {compactDateTime(item.heldVisitDate, item.heldSlotStart, item.heldSlotEnd)}
              {item.heldDoctorName ? ` · ${item.heldDoctorName}` : ''}
            </p>
          </div>
        </div>
      ) : null}
    </div>
  );
}

export default function AppointmentFollowUpsPage() {
  const [activeCategory, setActiveCategory] = useState<FollowUpCategory>('ALL');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(1);
  const debouncedSearch = useDebouncedValue(search.trim(), 350);

  useEffect(() => {
    setPage(1);
  }, [activeCategory, debouncedSearch]);

  const followUpParams = useMemo(
    () => ({
      page: String(page - 1),
      size: String(PAGE_SIZE),
      category: activeCategory,
      ...(debouncedSearch ? { q: debouncedSearch } : {}),
    }),
    [activeCategory, debouncedSearch, page],
  );

  const followUpQuery = useFollowUpQueue(followUpParams);
  const items = followUpQuery.data?.items ?? [];
  const meta = followUpQuery.data?.meta;
  const totalPages = Math.max(meta?.totalPages ?? 1, 1);
  const emptyCopy = getEmptyCopy(activeCategory);
  const errorStatus = getHttpStatus(followUpQuery.error);
  const errorTitle =
    errorStatus === 403
      ? 'Bạn không có quyền xem danh sách này'
      : 'Không tải được danh sách cần xử lý';
  const errorDescription =
    errorStatus === 403
      ? 'Chức năng này dành cho nhân viên tiếp nhận.'
      : 'Vui lòng thử lại hoặc kiểm tra kết nối.';

  return (
    <div className="space-y-4">
      <PageHeader
        title="Cần xử lý sau lịch"
        description="Theo dõi các lịch cần nhân viên liên hệ hoặc xử lý tiếp, như no-show, bác sĩ hủy hoặc yêu cầu liên hệ."
      />

      <Card className="border-border/70 p-3 shadow-sm">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex flex-wrap gap-2">
            {FOLLOW_UP_FILTERS.map((item) => (
              <Button
                key={item.value}
                size="sm"
                variant={activeCategory === item.value ? 'default' : 'outline'}
                onClick={() => setActiveCategory(item.value)}
              >
                {item.label}
              </Button>
            ))}
          </div>

          <div className="relative w-full lg:max-w-sm">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Tìm bệnh nhân, SĐT hoặc mã lịch..."
              className="pl-9"
            />
          </div>
        </div>
      </Card>

      {followUpQuery.isError ? (
        <ErrorState
          title={errorTitle}
          description={errorDescription}
          onRetry={() => void followUpQuery.refetch()}
          className="min-h-40 py-8"
        />
      ) : followUpQuery.isLoading ? (
        <LoadingSkeleton variant="list" count={4} />
      ) : items.length ? (
        <>
          <Card className="border-border/70 shadow-sm">
            <CardHeader className="pb-3">
              <CardTitle className="text-base">Danh sách cần xử lý</CardTitle>
              <p className="text-sm text-muted-foreground">
                Hiển thị {items.length} mục
                {typeof meta?.totalItems === 'number' ? ` trong ${meta.totalItems} mục` : ''}.
              </p>
            </CardHeader>
            <CardContent className="space-y-3">
              {items.map((item, index) => (
                <FollowUpQueueRow
                  key={item.id || `${item.appointmentId}-${index}`}
                  item={item}
                />
              ))}
            </CardContent>
          </Card>

          <PaginationControls page={page} totalPages={totalPages} onPageChange={setPage} />
        </>
      ) : (
        <EmptyState
          title={emptyCopy.title}
          description={
            search.trim()
              ? 'Thử đổi từ khóa tìm kiếm hoặc chọn bộ lọc khác.'
              : emptyCopy.description
          }
          className="min-h-48"
        />
      )}

    </div>
  );
}
