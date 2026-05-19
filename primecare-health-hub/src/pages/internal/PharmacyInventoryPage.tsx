import { useEffect, useMemo, useState } from 'react';
import {
  AlertTriangle,
  Boxes,
  CalendarClock,
  PackageCheck,
  Plus,
  RefreshCw,
  SquarePen,
} from 'lucide-react';
import { toast } from 'sonner';
import { DataTable, type Column } from '@/components/DataTable';
import { ErrorState } from '@/components/ErrorState';
import { PageHeader } from '@/components/PageHeader';
import { StatCard } from '@/components/StatCard';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Checkbox } from '@/components/ui/checkbox';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import {
  useCreateBatch,
  useExpiringBatches,
  usePharmacyBatches,
  usePharmacyInventory,
  useUpdateBatch,
} from '@/hooks/use-pharmacist-data';
import { useDebouncedValue } from '@/hooks/use-debounced-value';
import type {
  ApiQueryParams,
  ExpiringBatch,
  MedicationBatch,
  PharmacyInventoryItem,
} from '@/types/api';

type InventoryTab = 'inventory' | 'batches' | 'expiring';

interface BatchFormState {
  medicationId: string;
  batchNumber: string;
  quantity: string;
  expiryDate: string;
  status: string;
}

type BatchDialogState =
  | { mode: 'create'; medicationId?: string }
  | { mode: 'edit'; batch: MedicationBatch }
  | null;

const PAGE_SIZE = '20';

const EMPTY_BATCH_FORM: BatchFormState = {
  medicationId: '',
  batchNumber: '',
  quantity: '',
  expiryDate: '',
  status: '',
};

const BATCH_STATUS_LABELS: Record<string, string> = {
  ACTIVE: 'Đang lưu hành',
  AVAILABLE: 'Đang lưu hành',
  LOW_STOCK: 'Tồn thấp',
  OUT_OF_STOCK: 'Hết tồn',
  EXPIRING: 'Sắp hết hạn',
  EXPIRED: 'Đã hết hạn',
  QUARANTINED: 'Tạm giữ',
  RECALL: 'Thu hồi',
  INACTIVE: 'Ngưng hoạt động',
};

function formatDate(value?: string) {
  if (!value) return '—';
  const timestamp = Date.parse(value);
  if (Number.isNaN(timestamp)) return value;
  return new Date(timestamp).toLocaleDateString('vi-VN');
}

function toDateInputValue(value?: string) {
  if (!value) return '';
  if (/^\d{4}-\d{2}-\d{2}/.test(value)) return value.slice(0, 10);
  const timestamp = Date.parse(value);
  if (Number.isNaN(timestamp)) return '';
  return new Date(timestamp).toISOString().slice(0, 10);
}

function isExpiredDate(value?: string) {
  if (!value) return false;
  const timestamp = Date.parse(value);
  if (Number.isNaN(timestamp)) return false;
  const expiryDate = new Date(timestamp);
  expiryDate.setHours(0, 0, 0, 0);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return expiryDate.getTime() < today.getTime();
}

function isBatchExpired(batch: Pick<MedicationBatch, 'expiryDate' | 'status'> & { expired?: boolean }) {
  return batch.expired === true || batch.status === 'EXPIRED' || isExpiredDate(batch.expiryDate);
}

function getBatchStatusLabel(batch: Pick<MedicationBatch, 'status' | 'expiryDate'> & { expired?: boolean }) {
  if (isBatchExpired(batch)) return 'Đã hết hạn';
  if (!batch.status) return 'Đang lưu hành';
  return BATCH_STATUS_LABELS[batch.status] ?? batch.status;
}

function getBatchStatusVariant(batch: Pick<MedicationBatch, 'status' | 'expiryDate'> & { expired?: boolean }) {
  if (isBatchExpired(batch)) return 'destructive';
  if (batch.status === 'LOW_STOCK' || batch.status === 'EXPIRING') return 'warning';
  if (batch.status === 'ACTIVE' || batch.status === 'AVAILABLE') return 'success';
  return 'secondary';
}

function getInventoryStatus(item: PharmacyInventoryItem) {
  if (item.totalQuantity <= 0) {
    return { label: 'Hết tồn', variant: 'destructive' as const };
  }
  if (item.lowStock) {
    return { label: 'Tồn thấp', variant: 'warning' as const };
  }
  if (item.status && item.status !== 'ACTIVE' && item.status !== 'AVAILABLE') {
    return {
      label: BATCH_STATUS_LABELS[item.status] ?? item.status,
      variant: 'secondary' as const,
    };
  }
  return { label: 'Đủ tồn', variant: 'success' as const };
}

function buildQueryParams(page: number, search: string, extra?: ApiQueryParams): ApiQueryParams {
  return {
    page: String(page - 1),
    size: PAGE_SIZE,
    ...(search ? { q: search } : {}),
    ...extra,
  };
}

export default function PharmacyInventoryPage() {
  const [activeTab, setActiveTab] = useState<InventoryTab>('inventory');
  const [search, setSearch] = useState('');
  const [lowStockOnly, setLowStockOnly] = useState(false);
  const [expiringOnly, setExpiringOnly] = useState(false);
  const [inventoryPage, setInventoryPage] = useState(1);
  const [batchPage, setBatchPage] = useState(1);
  const [expiringPage, setExpiringPage] = useState(1);
  const [dialogState, setDialogState] = useState<BatchDialogState>(null);
  const [form, setForm] = useState<BatchFormState>(EMPTY_BATCH_FORM);
  const debouncedSearch = useDebouncedValue(search.trim(), 400);

  useEffect(() => {
    setInventoryPage(1);
    setBatchPage(1);
    setExpiringPage(1);
  }, [debouncedSearch, lowStockOnly]);

  useEffect(() => {
    if (lowStockOnly) setActiveTab('inventory');
  }, [lowStockOnly]);

  useEffect(() => {
    if (expiringOnly) setActiveTab('expiring');
  }, [expiringOnly]);

  const inventoryParams = useMemo(
    () => buildQueryParams(inventoryPage, debouncedSearch, lowStockOnly ? { lowStock: true } : undefined),
    [debouncedSearch, inventoryPage, lowStockOnly],
  );
  const batchParams = useMemo(
    () => buildQueryParams(batchPage, debouncedSearch),
    [batchPage, debouncedSearch],
  );
  const expiringParams = useMemo(
    () => buildQueryParams(expiringPage, debouncedSearch),
    [debouncedSearch, expiringPage],
  );
  const inventorySummaryParams = useMemo(() => ({ page: '0', size: '1' }), []);
  const lowStockSummaryParams = useMemo(() => ({ page: '0', size: '1', lowStock: true }), []);
  const expiringSummaryParams = useMemo(() => ({ page: '0', size: '100' }), []);

  const inventoryQuery = usePharmacyInventory(inventoryParams);
  const batchesQuery = usePharmacyBatches(batchParams);
  const expiringQuery = useExpiringBatches(expiringParams);
  const inventorySummaryQuery = usePharmacyInventory(inventorySummaryParams);
  const lowStockSummaryQuery = usePharmacyInventory(lowStockSummaryParams);
  const expiringSummaryQuery = useExpiringBatches(expiringSummaryParams);
  const createBatch = useCreateBatch();
  const updateBatch = useUpdateBatch();

  const inventoryRows = useMemo(() => inventoryQuery.data?.items ?? [], [inventoryQuery.data?.items]);
  const batchRows = useMemo(() => batchesQuery.data?.items ?? [], [batchesQuery.data?.items]);
  const expiringRows = useMemo(() => expiringQuery.data?.items ?? [], [expiringQuery.data?.items]);

  const expiredBatchCount = useMemo(
    () => (expiringSummaryQuery.data?.items ?? expiringRows).filter(isBatchExpired).length,
    [expiringRows, expiringSummaryQuery.data?.items],
  );

  const openCreateDialog = (medicationId?: string) => {
    setDialogState({ mode: 'create', medicationId });
    setForm({
      ...EMPTY_BATCH_FORM,
      medicationId: medicationId ?? '',
    });
  };

  const openEditDialog = (batch: MedicationBatch) => {
    setDialogState({ mode: 'edit', batch });
    setForm({
      medicationId: batch.medicationId,
      batchNumber: batch.batchNumber ?? batch.batchCode,
      quantity: String(batch.quantity),
      expiryDate: toDateInputValue(batch.expiryDate),
      status: batch.status ?? '',
    });
  };

  const closeDialog = () => {
    setDialogState(null);
    setForm(EMPTY_BATCH_FORM);
  };

  const submitBatch = async () => {
    const quantity = Number(form.quantity);
    const isCreate = dialogState?.mode === 'create';

    if (!dialogState) return;
    if (isCreate && !form.medicationId.trim()) {
      toast.error('Vui lòng nhập mã thuốc.');
      return;
    }
    if (!form.batchNumber.trim()) {
      toast.error('Vui lòng nhập mã lô.');
      return;
    }
    if (!Number.isFinite(quantity) || quantity < 0) {
      toast.error('Vui lòng nhập số lượng hợp lệ.');
      return;
    }
    if (!form.expiryDate.trim()) {
      toast.error('Vui lòng chọn hạn dùng.');
      return;
    }

    if (isCreate) {
      await createBatch.mutateAsync({
        medicationId: form.medicationId.trim(),
        batchNumber: form.batchNumber.trim(),
        quantity,
        expiryDate: form.expiryDate,
        status: form.status.trim() || undefined,
      });
    } else {
      await updateBatch.mutateAsync({
        id: dialogState.batch.id,
        payload: {
          medicationId: form.medicationId.trim() || undefined,
          batchNumber: form.batchNumber.trim(),
          quantity,
          expiryDate: form.expiryDate,
          status: form.status.trim() || undefined,
        },
      });
    }

    closeDialog();
  };

  const refreshAll = () => {
    void inventoryQuery.refetch();
    void batchesQuery.refetch();
    void expiringQuery.refetch();
    void inventorySummaryQuery.refetch();
    void lowStockSummaryQuery.refetch();
    void expiringSummaryQuery.refetch();
  };

  const inventoryColumns: Column<PharmacyInventoryItem>[] = [
    {
      key: 'medicationName',
      header: 'Thuốc',
      cell: (row) => (
        <div>
          <p className="font-medium text-foreground">{row.medicationName}</p>
          <p className="text-xs text-muted-foreground">{row.medicationCode || row.medicationId || '—'}</p>
        </div>
      ),
    },
    {
      key: 'totalQuantity',
      header: 'Tổng tồn',
      className: 'text-right',
      cell: (row) => (
        <span className="font-mono text-sm font-semibold tabular-nums">{row.totalQuantity.toLocaleString('vi-VN')}</span>
      ),
    },
    {
      key: 'unit',
      header: 'Đơn vị',
      cell: (row) => <span>{row.unit || '—'}</span>,
    },
    {
      key: 'status',
      header: 'Trạng thái',
      cell: (row) => {
        const status = getInventoryStatus(row);
        return <Badge variant={status.variant}>{status.label}</Badge>;
      },
    },
  ];

  const batchColumns: Column<MedicationBatch>[] = [
    {
      key: 'batchNumber',
      header: 'Mã lô',
      cell: (row) => <span className="font-mono text-sm">{row.batchNumber || row.batchCode || row.batchId}</span>,
    },
    {
      key: 'medicationName',
      header: 'Thuốc',
      cell: (row) => (
        <div>
          <p className="font-medium text-foreground">{row.medicationName}</p>
          <p className="text-xs text-muted-foreground">{row.medicationCode || row.medicationId || '—'}</p>
        </div>
      ),
    },
    {
      key: 'quantity',
      header: 'Số lượng',
      className: 'text-right',
      cell: (row) => (
        <span className="font-mono text-sm font-semibold tabular-nums">{row.quantity.toLocaleString('vi-VN')}</span>
      ),
    },
    {
      key: 'expiryDate',
      header: 'Hạn dùng',
      cell: (row) => <span>{formatDate(row.expiryDate)}</span>,
    },
    {
      key: 'status',
      header: 'Trạng thái',
      cell: (row) => (
        <Badge variant={getBatchStatusVariant(row)}>
          {getBatchStatusLabel(row)}
        </Badge>
      ),
    },
  ];

  const expiringColumns: Column<ExpiringBatch>[] = [
    ...batchColumns,
    {
      key: 'daysUntilExpiry',
      header: 'Còn lại',
      cell: (row) => (
        <span className="text-sm text-muted-foreground">
          {isBatchExpired(row)
            ? 'Đã hết hạn'
            : typeof row.daysUntilExpiry === 'number'
              ? `${row.daysUntilExpiry} ngày`
              : 'Sắp hết hạn'}
        </span>
      ),
    },
  ];

  const hasAnyError = inventoryQuery.isError || batchesQuery.isError || expiringQuery.isError;
  const mutationPending = createBatch.isPending || updateBatch.isPending;

  return (
    <div className="space-y-4">
      <PageHeader
        title="Tồn kho thuốc"
        description="Theo dõi tồn kho tổng, lô thuốc và các lô sắp hết hạn trong kho dược."
        actions={
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" onClick={refreshAll}>
              <RefreshCw className="mr-2 h-4 w-4" />
              Làm mới
            </Button>
            <Button onClick={() => openCreateDialog()}>
              <Plus className="mr-2 h-4 w-4" />
              Thêm lô
            </Button>
          </div>
        }
      />

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
        <StatCard
          title="Tổng loại thuốc"
          value={inventorySummaryQuery.data?.meta.totalItems ?? inventoryQuery.data?.meta.totalItems ?? 0}
          icon={Boxes}
        />
        <StatCard
          title="Thuốc tồn thấp"
          value={lowStockSummaryQuery.data?.meta.totalItems ?? inventoryRows.filter((item) => item.lowStock).length}
          icon={AlertTriangle}
        />
        <StatCard
          title="Lô sắp hết hạn"
          value={expiringSummaryQuery.data?.meta.totalItems ?? expiringQuery.data?.meta.totalItems ?? 0}
          icon={CalendarClock}
        />
        <StatCard
          title="Lô đã hết hạn"
          value={expiredBatchCount}
          icon={PackageCheck}
        />
      </div>

      <Card>
        <CardContent className="grid grid-cols-1 gap-4 pt-6 lg:grid-cols-[minmax(0,1fr)_auto] lg:items-center">
          <Input
            placeholder="Tìm theo tên thuốc / mã thuốc / mã lô"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
          <div className="flex flex-wrap gap-4">
            <label className="flex items-center gap-2 text-sm text-muted-foreground">
              <Checkbox
                checked={lowStockOnly}
                onCheckedChange={(checked) => setLowStockOnly(checked === true)}
              />
              Chỉ tồn thấp
            </label>
            <label className="flex items-center gap-2 text-sm text-muted-foreground">
              <Checkbox
                checked={expiringOnly}
                onCheckedChange={(checked) => setExpiringOnly(checked === true)}
              />
              Chỉ sắp hết hạn
            </label>
          </div>
        </CardContent>
      </Card>

      {hasAnyError ? (
        <ErrorState
          title="Không tải được tồn kho thuốc"
          description="Vui lòng thử lại hoặc kiểm tra quyền truy cập kho dược."
          onRetry={refreshAll}
        />
      ) : (
        <Tabs value={activeTab} onValueChange={(value) => setActiveTab(value as InventoryTab)}>
          <TabsList className="grid w-full grid-cols-3 md:w-auto">
            <TabsTrigger value="inventory">Tồn kho tổng</TabsTrigger>
            <TabsTrigger value="batches">Lô thuốc</TabsTrigger>
            <TabsTrigger value="expiring">Sắp hết hạn</TabsTrigger>
          </TabsList>

          <TabsContent value="inventory" className="mt-4">
            <DataTable
              columns={inventoryColumns}
              data={inventoryRows}
              page={inventoryPage}
              totalPages={Math.max(inventoryQuery.data?.meta.totalPages ?? 1, 1)}
              onPageChange={setInventoryPage}
              isLoading={inventoryQuery.isLoading}
              emptyMessage="Không có thuốc trong tồn kho"
              keyExtractor={(row) => row.medicationId}
              actions={(row) => (
                <Button size="sm" variant="outline" onClick={() => openCreateDialog(row.medicationId)}>
                  <Plus className="mr-2 h-4 w-4" />
                  Thêm lô
                </Button>
              )}
            />
          </TabsContent>

          <TabsContent value="batches" className="mt-4">
            <DataTable
              columns={batchColumns}
              data={batchRows}
              page={batchPage}
              totalPages={Math.max(batchesQuery.data?.meta.totalPages ?? 1, 1)}
              onPageChange={setBatchPage}
              isLoading={batchesQuery.isLoading}
              emptyMessage="Không có lô thuốc"
              keyExtractor={(row) => row.id || row.batchNumber || row.batchCode}
              actions={(row) => (
                <Button size="icon" variant="outline" onClick={() => openEditDialog(row)} aria-label="Sửa lô thuốc">
                  <SquarePen className="h-4 w-4" />
                </Button>
              )}
            />
          </TabsContent>

          <TabsContent value="expiring" className="mt-4">
            <DataTable
              columns={expiringColumns}
              data={expiringRows}
              page={expiringPage}
              totalPages={Math.max(expiringQuery.data?.meta.totalPages ?? 1, 1)}
              onPageChange={setExpiringPage}
              isLoading={expiringQuery.isLoading}
              emptyMessage="Không có lô sắp hết hạn"
              keyExtractor={(row) => row.id || row.batchNumber || row.batchCode}
              actions={(row) => (
                <Button size="icon" variant="outline" onClick={() => openEditDialog(row)} aria-label="Sửa lô thuốc">
                  <SquarePen className="h-4 w-4" />
                </Button>
              )}
            />
          </TabsContent>
        </Tabs>
      )}

      <Dialog open={!!dialogState} onOpenChange={(open) => !open && closeDialog()}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>{dialogState?.mode === 'edit' ? 'Sửa lô thuốc' : 'Thêm lô thuốc'}</DialogTitle>
            <DialogDescription>
              Cập nhật thông tin lô để tồn kho và cảnh báo hạn dùng phản ánh đúng dữ liệu backend.
            </DialogDescription>
          </DialogHeader>

          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="batch-medication-id">Mã thuốc *</Label>
              <Input
                id="batch-medication-id"
                value={form.medicationId}
                onChange={(event) => setForm((current) => ({ ...current, medicationId: event.target.value }))}
                placeholder="ID thuốc từ danh mục thuốc"
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="batch-code">Mã lô *</Label>
              <Input
                id="batch-code"
                value={form.batchNumber}
                onChange={(event) => setForm((current) => ({ ...current, batchNumber: event.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="batch-quantity">Số lượng *</Label>
              <Input
                id="batch-quantity"
                type="number"
                min={0}
                value={form.quantity}
                onChange={(event) => setForm((current) => ({ ...current, quantity: event.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="batch-expiry-date">Hạn dùng *</Label>
              <Input
                id="batch-expiry-date"
                type="date"
                value={form.expiryDate}
                onChange={(event) => setForm((current) => ({ ...current, expiryDate: event.target.value }))}
              />
            </div>
            <div className="space-y-2 md:col-span-2">
              <Label htmlFor="batch-status">Trạng thái</Label>
              <Input
                id="batch-status"
                value={form.status}
                onChange={(event) => setForm((current) => ({ ...current, status: event.target.value }))}
                placeholder="ACTIVE, QUARANTINED, EXPIRED..."
              />
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={closeDialog}>
              Hủy
            </Button>
            <Button onClick={submitBatch} disabled={mutationPending}>
              Lưu
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
