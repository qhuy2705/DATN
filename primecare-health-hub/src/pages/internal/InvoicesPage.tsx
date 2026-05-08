import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Copy, FileText, QrCode, Receipt, ShieldCheck, Wallet } from 'lucide-react';
import { toast } from 'sonner';
import { PageHeader } from '@/components/PageHeader';
import { DataTable, type Column } from '@/components/DataTable';
import { StatCard } from '@/components/StatCard';
import { StatusBadge } from '@/components/StatusBadge';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
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
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import {
  useCashierInvoiceDetail,
  useCashierInvoices,
  useCashierServiceOrders,
  useCashierSummary,
  useConfirmBankTransfer,
  useCreateInvoice,
  useCreateInvoicePdfJob,
  useMarkPaid,
  useReconcileBankTransfer,
  useRefundInvoice,
} from '@/hooks/use-cashier-data';
import { useDebouncedValue } from '@/hooks/use-debounced-value';
import type { CashierServiceOrder, Invoice, PaymentStatus } from '@/types/api';
import { Textarea } from '@/components/ui/textarea';
import { getPaymentStatusLabel } from '@/lib/filter-options';
import { toLocalDateInputValue } from '@/lib/date';

type TabMode = 'service-orders' | 'invoices';

const REVIEWABLE_STATUSES = new Set<PaymentStatus>(['PENDING_CONFIRMATION', 'PAYMENT_REVIEW']);

export default function InvoicesPage() {
  const navigate = useNavigate();

  const [mode, setMode] = useState<TabMode>('service-orders');
  const [serviceOrderPage, setServiceOrderPage] = useState(1);
  const [invoicePage, setInvoicePage] = useState(1);
  const [serviceOrderSearch, setServiceOrderSearch] = useState('');
  const [invoiceSearch, setInvoiceSearch] = useState('');
  const [serviceOrderPaymentFilter, setServiceOrderPaymentFilter] = useState('__all__');
  const [serviceOrderInvoicedFilter, setServiceOrderInvoicedFilter] = useState('__all__');
  const [invoicePaymentFilter, setInvoicePaymentFilter] = useState('__all__');
  const [createInvoiceOpen, setCreateInvoiceOpen] = useState(false);
  const [refundInvoiceId, setRefundInvoiceId] = useState<string | null>(null);
  const [refundReason, setRefundReason] = useState('');
  const [selectedServiceOrder, setSelectedServiceOrder] =
    useState<CashierServiceOrder | null>(null);
  const [paymentMethod, setPaymentMethod] = useState('CASH');
  const [viewInvoiceId, setViewInvoiceId] = useState<string | null>(null);
  const [reconcileOpen, setReconcileOpen] = useState(false);
  const [reconcileInvoiceId, setReconcileInvoiceId] = useState<string | null>(null);
  const [transactionRef, setTransactionRef] = useState('');
  const [transactionAmount, setTransactionAmount] = useState('');
  const [transactionContent, setTransactionContent] = useState('');
  const [transactionTime, setTransactionTime] = useState('');
  const [confirmNote, setConfirmNote] = useState('');
  const debouncedServiceOrderSearch = useDebouncedValue(serviceOrderSearch.trim(), 400);
  const debouncedInvoiceSearch = useDebouncedValue(invoiceSearch.trim(), 400);
  const summaryParams = useMemo(() => ({ date: toLocalDateInputValue() }), []);

  useEffect(() => {
    setServiceOrderPage(1);
  }, [debouncedServiceOrderSearch, serviceOrderInvoicedFilter, serviceOrderPaymentFilter]);

  useEffect(() => {
    setInvoicePage(1);
  }, [debouncedInvoiceSearch, invoicePaymentFilter]);

  const serviceOrderParams = useMemo(
    () => ({
      page: String(serviceOrderPage - 1),
      size: '20',
      ...(debouncedServiceOrderSearch ? { q: debouncedServiceOrderSearch } : {}),
      ...(serviceOrderPaymentFilter !== '__all__'
        ? { paymentStatus: serviceOrderPaymentFilter }
        : {}),
      ...(serviceOrderInvoicedFilter !== '__all__'
        ? { invoiced: serviceOrderInvoicedFilter }
        : {}),
    }),
    [debouncedServiceOrderSearch, serviceOrderInvoicedFilter, serviceOrderPage, serviceOrderPaymentFilter],
  );

  const invoiceParams = useMemo(
    () => ({
      page: String(invoicePage - 1),
      size: '20',
      ...(debouncedInvoiceSearch ? { q: debouncedInvoiceSearch } : {}),
      ...(invoicePaymentFilter !== '__all__' ? { paymentStatus: invoicePaymentFilter } : {}),
    }),
    [debouncedInvoiceSearch, invoicePage, invoicePaymentFilter],
  );

  const {
    data: serviceOrdersData,
    isLoading: serviceOrdersLoading,
    isError: serviceOrdersError,
    refetch: refetchServiceOrders,
  } = useCashierServiceOrders(serviceOrderParams, {
    enabled: mode === 'service-orders',
    staleTime: 30_000,
  });

  const {
    data: invoicesData,
    isLoading: invoicesLoading,
    isError: invoicesError,
    refetch: refetchInvoices,
  } = useCashierInvoices(invoiceParams, {
    enabled: mode === 'invoices',
    staleTime: 30_000,
  });

  const { data: cashierSummary } = useCashierSummary(summaryParams);
  const { data: invoiceDetail } = useCashierInvoiceDetail(viewInvoiceId);

  const createInvoice = useCreateInvoice();
  const markPaid = useMarkPaid();
  const refundInvoice = useRefundInvoice();
  const createPdfJob = useCreateInvoicePdfJob();
  const reconcileBankTransfer = useReconcileBankTransfer();
  const confirmBankTransfer = useConfirmBankTransfer();

  const serviceOrders = useMemo(
    () => serviceOrdersData?.items ?? [],
    [serviceOrdersData?.items],
  );
  const invoices = useMemo(() => invoicesData?.items ?? [], [invoicesData?.items]);
  const viewInvoice = invoiceDetail ?? invoices.find((invoice) => invoice.id === viewInvoiceId) ?? null;

  const loadedPageSummary = useMemo(
    () => ({
      serviceOrdersWaiting: serviceOrders.filter((order) => !order.invoiced).length,
      serviceOrdersUnpaidAmount: serviceOrders
        .filter((order) => order.paymentStatus !== 'PAID')
        .reduce((total, order) => total + (order.estimatedTotalAmount ?? 0), 0),
      unpaidInvoices: invoices.filter((invoice) =>
        ['UNPAID', 'PENDING_CONFIRMATION', 'PAYMENT_REVIEW'].includes(invoice.paymentStatus),
      ).length,
      paidRevenue: invoices
        .filter((invoice) => invoice.paymentStatus === 'PAID')
        .reduce((total, invoice) => total + (invoice.totalAmount ?? 0), 0),
    }),
    [invoices, serviceOrders],
  );
  const serviceOrdersWaiting =
    cashierSummary?.serviceOrdersWaiting ?? loadedPageSummary.serviceOrdersWaiting;
  const serviceOrdersUnpaidAmount =
    cashierSummary?.serviceOrdersUnpaidAmount ?? loadedPageSummary.serviceOrdersUnpaidAmount;
  const unpaidInvoices = cashierSummary?.unpaidInvoices ?? loadedPageSummary.unpaidInvoices;
  const paidRevenue = cashierSummary?.paidRevenue ?? loadedPageSummary.paidRevenue;

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const status = params.get('vnpayStatus');
    if (!status) return;

    setMode('invoices');

    if (status === 'success') {
      toast.success('Thanh toán VNPAY thành công');
    } else {
      toast.error(
        `Thanh toán VNPAY chưa thành công${
          params.get('vnpayCode') ? ` (${params.get('vnpayCode')})` : ''
        }`,
      );
    }

    params.delete('vnpayStatus');
    params.delete('vnpayCode');
    params.delete('vnpayTxnRef');
    params.delete('invoiceId');

    const nextUrl = `${window.location.pathname}${
      params.toString() ? `?${params.toString()}` : ''
    }`;
    window.history.replaceState({}, '', nextUrl);
  }, []);

  const openCreateInvoice = (row: CashierServiceOrder) => {
    setSelectedServiceOrder(row);
    setPaymentMethod('CASH');
    setCreateInvoiceOpen(true);
  };

  const submitCreateInvoice = async () => {
    if (!selectedServiceOrder) return;

    const invoice = await createInvoice.mutateAsync({
      serviceOrderId: selectedServiceOrder.id,
      body: {
        paymentMethod,
        ...(paymentMethod === 'VNPAY'
          ? { returnUrl: window.location.origin + '/app/cashier/invoices' }
          : {}),
      },
    });

    setCreateInvoiceOpen(false);
    setMode('invoices');

    if (invoice.vnpPaymentUrl) {
      window.open(invoice.vnpPaymentUrl, '_blank');
      return;
    }

    if (invoice.paymentMethod === 'BANK_TRANSFER') {
      setViewInvoiceId(invoice.id);
      toast.success('Đã tạo hóa đơn chuyển khoản và sinh mã tham chiếu đối soát');
    }
  };

  const handleCreatePdf = async (invoiceId: string) => {
    const job = await createPdfJob.mutateAsync(invoiceId);
    navigate(`/app/cashier/invoice-pdf-jobs/${job.id}`);
  };

  const openReconcileDialog = (invoice: Invoice) => {
    setReconcileInvoiceId(invoice.id);
    setTransactionRef('');
    setTransactionAmount(String(invoice.totalAmount ?? 0));
    setTransactionContent(invoice.transferContent ?? '');
    setTransactionTime('');
    setReconcileOpen(true);
  };

  const submitReconcile = async () => {
    if (!reconcileInvoiceId) return;
    await reconcileBankTransfer.mutateAsync({
      invoiceId: reconcileInvoiceId,
      body: {
        transactionRef,
        amount: Number(transactionAmount || 0),
        provider: 'MANUAL_REVIEW',
        transferContent: transactionContent,
        transactionTime: transactionTime ? new Date(transactionTime).toISOString() : undefined,
      },
    });
    setReconcileOpen(false);
    setViewInvoiceId(reconcileInvoiceId);
  };

  const submitConfirmBankTransfer = async () => {
    if (!viewInvoice) return;
    await confirmBankTransfer.mutateAsync({
      invoiceId: viewInvoice.id,
      body: { note: confirmNote || 'Cashier xác nhận từ sao kê ngân hàng' },
    });
    setConfirmNote('');
  };

  const serviceOrderColumns: Column<CashierServiceOrder>[] = [
    {
      key: 'code',
      header: 'Mã phiếu chỉ định',
      cell: (row) => (
        <div>
          <p className="font-medium">{row.code}</p>
          <p className="text-xs text-muted-foreground">{row.patientName || '-'}</p>
        </div>
      ),
    },
    {
      key: 'doctorName',
      header: 'Bác sĩ',
      cell: (row) => (
        <div>
          <p>{row.doctorName || '-'}</p>
          <p className="text-xs text-muted-foreground">{row.branchName || '-'}</p>
        </div>
      ),
    },
    {
      key: 'estimatedTotalAmount',
      header: 'Tổng tiền',
      cell: (row) => <span>{formatMoney(row.estimatedTotalAmount ?? 0)}</span>,
    },
    {
      key: 'paymentStatus',
      header: 'Thanh toán',
      cell: (row) => <StatusBadge status={row.paymentStatus || 'UNPAID'} />,
    },
    {
      key: 'invoiced',
      header: 'Hóa đơn',
      cell: (row) => (
        <span className="text-sm">
          {row.invoiced ? row.invoiceCode || 'Đã tạo hóa đơn' : 'Chưa tạo'}
        </span>
      ),
    },
  ];

  const invoiceColumns: Column<Invoice>[] = [
    {
      key: 'code',
      header: 'Mã hóa đơn',
      cell: (row) => (
        <div>
          <p className="font-medium">{row.code || row.id}</p>
          <p className="text-xs text-muted-foreground">{row.patientName || '-'}</p>
        </div>
      ),
    },
    {
      key: 'serviceOrderCode',
      header: 'Phiếu chỉ định',
      cell: (row) => (
        <div>
          <p>{row.serviceOrderCode || '-'}</p>
          <p className="text-xs text-muted-foreground">{row.doctorName || '-'}</p>
        </div>
      ),
    },
    {
      key: 'totalAmount',
      header: 'Tổng tiền',
      cell: (row) => <span>{formatMoney(row.totalAmount ?? 0)}</span>,
    },
    {
      key: 'paymentMethod',
      header: 'Phương thức',
      cell: (row) => <span>{paymentMethodLabel(row.paymentMethod)}</span>,
    },
    {
      key: 'paymentStatus',
      header: 'Trạng thái',
      cell: (row) => <StatusBadge status={row.paymentStatus} />,
    },
  ];

  return (
    <div className="space-y-4">
      <PageHeader
        title="Thu ngân"
        description="Theo dõi phiếu chờ thu, tạo hóa đơn, đối soát chuyển khoản và xuất PDF một cách an toàn hơn."
      />

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
        <StatCard title="Phiếu chờ tạo hóa đơn hôm nay" value={serviceOrdersWaiting} icon={Receipt} />
        <StatCard
          title="Giá trị chưa thanh toán hôm nay"
          value={formatMoney(serviceOrdersUnpaidAmount)}
          icon={Wallet}
        />
        <StatCard title="Hóa đơn chưa thu hôm nay" value={unpaidInvoices} icon={FileText} />
        <StatCard title="Doanh thu đã ghi nhận hôm nay" value={formatMoney(paidRevenue)} icon={ShieldCheck} />
      </div>

      <Card className="border-border/70 shadow-sm">
        <CardHeader className="space-y-3">
          <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
            <div>
              <CardTitle className="text-lg">Trung tâm thao tác thu ngân</CardTitle>
              <CardDescription>
                Luồng chuyển khoản giờ tách rõ giữa tạo QR, chờ đối soát, cần kiểm tra và xác nhận thanh toán.
              </CardDescription>
            </div>
            <Tabs value={mode} onValueChange={(value) => setMode(value as TabMode)}>
              <TabsList className="grid w-full grid-cols-2 sm:w-auto">
                <TabsTrigger value="service-orders">Phiếu chờ thu</TabsTrigger>
                <TabsTrigger value="invoices">Hóa đơn</TabsTrigger>
              </TabsList>
            </Tabs>
          </div>
        </CardHeader>
      </Card>

      {mode === 'service-orders' && (
        <Card className="border-border/70 shadow-sm">
          <CardHeader>
            <CardTitle className="text-lg">Danh sách phiếu chờ thu</CardTitle>
            <CardDescription>
              Tạo hóa đơn ngay cho các phiếu chỉ định chưa được xuất hóa đơn.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
              <Input
                placeholder="Tìm theo mã phiếu / tên bệnh nhân"
                value={serviceOrderSearch}
                onChange={(e) => setServiceOrderSearch(e.target.value)}
              />
              <Select
                value={serviceOrderPaymentFilter}
                onValueChange={setServiceOrderPaymentFilter}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Lọc thanh toán" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="__all__">Tất cả trạng thái</SelectItem>
                  <SelectItem value="UNPAID">{getPaymentStatusLabel('UNPAID')}</SelectItem>
                  <SelectItem value="PAID">{getPaymentStatusLabel('PAID')}</SelectItem>
                </SelectContent>
              </Select>
              <Select
                value={serviceOrderInvoicedFilter}
                onValueChange={setServiceOrderInvoicedFilter}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Lọc hóa đơn" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="__all__">Tất cả</SelectItem>
                  <SelectItem value="true">Đã tạo hóa đơn</SelectItem>
                  <SelectItem value="false">Chưa tạo hóa đơn</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <DataTable
              columns={serviceOrderColumns}
              data={serviceOrders}
              page={serviceOrderPage}
              totalPages={Math.max(serviceOrdersData?.meta.totalPages ?? 1, 1)}
              onPageChange={setServiceOrderPage}
              isLoading={serviceOrdersLoading}
              isError={serviceOrdersError}
              onRetry={() => void refetchServiceOrders()}
              emptyMessage="Không có phiếu chờ thu phù hợp"
              keyExtractor={(row) => row.id}
              actions={(row) => {
                const item = row as CashierServiceOrder;
                if (item.invoiced) return null;
                return <Button size="sm" onClick={() => openCreateInvoice(item)}>Tạo hóa đơn</Button>;
              }}
            />
          </CardContent>
        </Card>
      )}

      {mode === 'invoices' && (
        <Card className="border-border/70 shadow-sm">
          <CardHeader>
            <CardTitle className="text-lg">Danh sách hóa đơn</CardTitle>
            <CardDescription>
              Hóa đơn chuyển khoản sẽ đi qua trạng thái chờ đối soát hoặc cần kiểm tra trước khi ghi nhận doanh thu.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
              <Input
                placeholder="Tìm theo mã hóa đơn / mã phiếu / tên bệnh nhân"
                value={invoiceSearch}
                onChange={(e) => setInvoiceSearch(e.target.value)}
              />
              <Select value={invoicePaymentFilter} onValueChange={setInvoicePaymentFilter}>
                <SelectTrigger>
                  <SelectValue placeholder="Lọc trạng thái" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="__all__">Tất cả trạng thái</SelectItem>
                  <SelectItem value="UNPAID">{getPaymentStatusLabel('UNPAID')}</SelectItem>
                  <SelectItem value="PENDING_CONFIRMATION">{getPaymentStatusLabel('PENDING_CONFIRMATION')}</SelectItem>
                  <SelectItem value="PAYMENT_REVIEW">{getPaymentStatusLabel('PAYMENT_REVIEW')}</SelectItem>
                  <SelectItem value="PAID">{getPaymentStatusLabel('PAID')}</SelectItem>
                  <SelectItem value="REFUNDED">{getPaymentStatusLabel('REFUNDED')}</SelectItem>
                  <SelectItem value="VOID">{getPaymentStatusLabel('VOID')}</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <DataTable
              columns={invoiceColumns}
              data={invoices}
              page={invoicePage}
              totalPages={Math.max(invoicesData?.meta.totalPages ?? 1, 1)}
              onPageChange={setInvoicePage}
              isLoading={invoicesLoading}
              isError={invoicesError}
              onRetry={() => void refetchInvoices()}
              emptyMessage="Không có hóa đơn phù hợp"
              keyExtractor={(row) => row.id}
              actions={(row) => {
                const item = row as Invoice;
                return (
                  <div className="flex flex-wrap gap-2">
                    <Button size="sm" variant="outline" onClick={() => setViewInvoiceId(item.id)}>
                      Chi tiết
                    </Button>
                    {item.paymentStatus === 'UNPAID' && item.paymentMethod !== 'BANK_TRANSFER' && (
                      <Button
                        size="sm"
                        variant="outline"
                        disabled={markPaid.isPending}
                        onClick={() => markPaid.mutate(item.id)}
                      >
                        <Wallet className="mr-1 h-4 w-4" />
                        Đánh dấu đã thu
                      </Button>
                    )}
                    {item.paymentMethod === 'BANK_TRANSFER' && REVIEWABLE_STATUSES.has(item.paymentStatus) && (
                      <Button size="sm" variant="outline" onClick={() => openReconcileDialog(item)}>
                        <QrCode className="mr-1 h-4 w-4" />
                        Đối soát CK
                      </Button>
                    )}
                    {item.paymentStatus === 'PAID' && (
                      <Button
                        size="sm"
                        variant="outline"
                        className="text-red-600 border-red-200 hover:bg-red-50 hover:text-red-700"
                        onClick={() => {
                          setRefundInvoiceId(item.id);
                          setRefundReason('');
                        }}
                      >
                        Hoàn tiền
                      </Button>
                    )}
                    <Button
                      size="sm"
                      variant="outline"
                      disabled={createPdfJob.isPending}
                      onClick={() => handleCreatePdf(item.id)}
                    >
                      PDF
                    </Button>
                  </div>
                );
              }}
            />
          </CardContent>
        </Card>
      )}

      <Dialog open={createInvoiceOpen} onOpenChange={setCreateInvoiceOpen}>
        <DialogContent className="max-w-xl">
          <DialogHeader>
            <DialogTitle>Tạo hóa đơn</DialogTitle>
          </DialogHeader>

          <div className="space-y-4">
            <div className="rounded-2xl border border-border/70 bg-muted/25 p-4 text-sm">
              <p className="font-medium text-foreground">{selectedServiceOrder?.code}</p>
              <p className="mt-1 text-muted-foreground">{selectedServiceOrder?.patientName || '-'}</p>
              <p className="mt-1 text-muted-foreground">{selectedServiceOrder?.doctorName || '-'} · {selectedServiceOrder?.branchName || '-'}</p>
              <p className="mt-3 text-base font-semibold text-foreground">
                Tạm tính: {formatMoney(selectedServiceOrder?.estimatedTotalAmount ?? 0)}
              </p>
            </div>

            {selectedServiceOrder?.items && selectedServiceOrder.items.length > 0 && (
              <div className="border rounded-md max-h-[250px] overflow-y-auto">
                <DataTable
                  columns={[
                    { key: 'name', header: 'Dịch vụ', cell: (r) => r.serviceNameVn || r.serviceNameEn || r.serviceCode },
                    { key: 'quantity', header: 'SL', cell: (r) => r.quantity },
                    { key: 'price', header: 'Đơn giá', cell: (r) => formatMoney(r.price || 0) },
                    { key: 'lineTotalAmount', header: 'Thành tiền', cell: (r) => formatMoney(r.lineTotalAmount || 0) }
                  ]}
                  data={selectedServiceOrder.items}
                  keyExtractor={(r) => r.id}
                />
              </div>
            )}

            <div>
              <label className="mb-1.5 block text-sm font-medium">Phương thức thanh toán</label>
              <Select value={paymentMethod} onValueChange={setPaymentMethod}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="CASH">Tiền mặt</SelectItem>
                  <SelectItem value="VNPAY">VNPAY</SelectItem>
                  <SelectItem value="BANK_TRANSFER">Chuyển khoản</SelectItem>
                </SelectContent>
              </Select>
            </div>

            {paymentMethod === 'VNPAY' && (
              <div className="rounded-2xl border border-primary/20 bg-primary/5 p-3 text-sm text-muted-foreground">
                Sau khi tạo hóa đơn, hệ thống sẽ mở cổng thanh toán VNPAY ở tab mới.
              </div>
            )}
            {paymentMethod === 'BANK_TRANSFER' && (
              <div className="rounded-2xl border border-primary/20 bg-primary/5 p-3 text-sm text-muted-foreground">
                Hệ thống sẽ tạo mã tham chiếu ngắn để dùng cho VietQR/chuyển khoản và chuyển hóa đơn sang trạng thái chờ đối soát.
              </div>
            )}
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setCreateInvoiceOpen(false)}>
              Hủy
            </Button>
            <Button onClick={submitCreateInvoice} disabled={createInvoice.isPending}>
              {createInvoice.isPending ? 'Đang tạo...' : 'Tạo hóa đơn'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={!!viewInvoiceId} onOpenChange={(open) => !open && setViewInvoiceId(null)}>
        <DialogContent className="max-w-4xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Chi tiết hóa đơn {viewInvoice?.code}</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div className="grid grid-cols-2 gap-4 text-sm mb-4">
              <div>
                <p className="text-muted-foreground">Bệnh nhân</p>
                <p className="font-medium">{viewInvoice?.patientName}</p>
              </div>
              <div>
                <p className="text-muted-foreground">Bác sĩ / Nơi khám</p>
                <p className="font-medium">{viewInvoice?.doctorName} · {viewInvoice?.branchName}</p>
              </div>
            </div>

            <div className="grid gap-4 lg:grid-cols-[1fr_300px]">
              <div className="space-y-4">
                <div className="border rounded-md">
                  <DataTable 
                     columns={[
                       { key: 'name', header: 'Mục', cell: (r) => r.nameSnapshot || r.referenceType },
                       { key: 'quantity', header: 'SL' },
                       { key: 'unitPrice', header: 'Đơn giá', cell: (r) => formatMoney(r.unitPrice || 0) },
                       { key: 'subTotalAmount', header: 'Thành tiền', cell: (r) => formatMoney(r.subtotalAmount || 0) }
                     ]}
                     data={viewInvoice?.items || []}
                     keyExtractor={(r) => r.id.toString()}
                     emptyMessage="Không có chi tiết hóa đơn"
                  />
                </div>

                <div className="flex justify-end mt-4 border-t pt-4">
                  <div className="w-full max-w-sm space-y-3">
                    <div className="flex justify-between text-sm">
                      <span className="text-muted-foreground">Tạm tính:</span>
                      <span className="font-medium">{formatMoney(viewInvoice?.subtotalAmount || 0)} ₫</span>
                    </div>
                    {!!viewInvoice?.discountAmount && (
                      <div className="flex justify-between text-sm text-green-600">
                        <span>Giảm giá:</span>
                        <span>-{formatMoney(viewInvoice.discountAmount)} ₫</span>
                      </div>
                    )}
                    <div className="flex justify-between text-sm">
                      <span className="text-muted-foreground">Thuế VAT:</span>
                      <span className="font-medium">{formatMoney(viewInvoice?.taxAmount || 0)} ₫</span>
                    </div>
                    <div className="flex justify-between text-lg font-bold border-t pt-2 mt-2">
                      <span>Tổng thanh toán:</span>
                      <span>{formatMoney(viewInvoice?.totalAmount || 0)} ₫</span>
                    </div>
                  </div>
                </div>
              </div>

              <div className="space-y-3 rounded-xl border border-border/70 bg-muted/20 p-4 text-sm">
                <div className="flex items-center justify-between">
                  <span className="text-muted-foreground">Trạng thái</span>
                  <StatusBadge status={viewInvoice?.paymentStatus || 'UNPAID'} />
                </div>
                <div>
                  <p className="text-muted-foreground">Phương thức</p>
                  <p className="font-medium">{paymentMethodLabel(viewInvoice?.paymentMethod)}</p>
                </div>

                {viewInvoice?.paymentMethod === 'BANK_TRANSFER' && (
                  <>
                    {viewInvoice.qrCodeBase64 && (
                      <div className="rounded-xl border bg-white p-3">
                        <img
                          src={`data:image/png;base64,${viewInvoice.qrCodeBase64}`}
                          alt="VietQR"
                          className="mx-auto h-52 w-52 object-contain"
                        />
                      </div>
                    )}
                    <InfoRow label="Mã tham chiếu" value={viewInvoice.paymentReference} copyable />
                    <InfoRow label="Nội dung chuyển khoản" value={viewInvoice.transferContent} copyable />
                    <InfoRow label="Ngân hàng" value={viewInvoice.bankCode} />
                    <InfoRow label="Số tài khoản" value={viewInvoice.bankAccountNo} copyable />
                    <InfoRow label="Chủ tài khoản" value={viewInvoice.bankAccountName} />
                    {viewInvoice.paymentReviewReason && (
                      <div className="rounded-lg border border-orange-200 bg-orange-50 px-3 py-2 text-orange-700">
                        {viewInvoice.paymentReviewReason}
                      </div>
                    )}
                    {REVIEWABLE_STATUSES.has(viewInvoice.paymentStatus) && (
                      <>
                        <Textarea
                          placeholder="Ghi chú xác nhận thủ công (tùy chọn)"
                          value={confirmNote}
                          onChange={(e) => setConfirmNote(e.target.value)}
                          rows={3}
                        />
                        <div className="flex gap-2">
                          <Button variant="outline" className="flex-1" onClick={() => openReconcileDialog(viewInvoice)}>
                            Nhập giao dịch
                          </Button>
                          <Button
                            className="flex-1"
                            disabled={confirmBankTransfer.isPending}
                            onClick={submitConfirmBankTransfer}
                          >
                            Xác nhận thủ công
                          </Button>
                        </div>
                      </>
                    )}
                  </>
                )}
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setViewInvoiceId(null)}>Đóng</Button>
            {viewInvoice?.paymentStatus === 'UNPAID' && viewInvoice.paymentMethod !== 'BANK_TRANSFER' && (
              <Button onClick={() => {
                markPaid.mutate(viewInvoice.id);
                setViewInvoiceId(null);
              }}>
                Xác nhận đã thu tiền
              </Button>
            )}
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={reconcileOpen} onOpenChange={setReconcileOpen}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>Đối soát giao dịch chuyển khoản</DialogTitle>
          </DialogHeader>
          <div className="space-y-3">
            <Input
              placeholder="Mã giao dịch ngân hàng"
              value={transactionRef}
              onChange={(e) => setTransactionRef(e.target.value)}
            />
            <Input
              placeholder="Số tiền"
              value={transactionAmount}
              onChange={(e) => setTransactionAmount(e.target.value)}
            />
            <Input
              placeholder="Nội dung chuyển khoản"
              value={transactionContent}
              onChange={(e) => setTransactionContent(e.target.value)}
            />
            <Input
              type="datetime-local"
              value={transactionTime}
              onChange={(e) => setTransactionTime(e.target.value)}
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setReconcileOpen(false)}>
              Hủy
            </Button>
            <Button
              disabled={!transactionRef.trim() || !transactionAmount.trim() || reconcileBankTransfer.isPending}
              onClick={submitReconcile}
            >
              {reconcileBankTransfer.isPending ? 'Đang xử lý...' : 'Ghi nhận giao dịch'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={!!refundInvoiceId} onOpenChange={(open) => !open && setRefundInvoiceId(null)}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Hoàn tiền hóa đơn</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <p className="text-sm text-muted-foreground">
              Vui lòng nhập lý do hoàn tiền. Thao tác này sẽ đánh dấu hóa đơn là đã hoàn tiền (REFUNDED) và cập nhật lại doanh thu hệ thống.
            </p>
            <Textarea
              placeholder="Nhập lý do hoàn tiền..."
              value={refundReason}
              onChange={(e) => setRefundReason(e.target.value)}
              rows={3}
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setRefundInvoiceId(null)}>
              Hủy
            </Button>
            <Button
              variant="destructive"
              disabled={!refundReason.trim() || refundInvoice.isPending}
              onClick={() => {
                if (refundInvoiceId) {
                  refundInvoice.mutate({ invoiceId: refundInvoiceId, reason: refundReason });
                  setRefundInvoiceId(null);
                }
              }}
            >
              Xác nhận hoàn tiền
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function InfoRow({ label, value, copyable = false }: { label: string; value?: string; copyable?: boolean }) {
  if (!value) return null;
  return (
    <div>
      <p className="text-muted-foreground">{label}</p>
      <div className="flex items-center gap-2">
        <p className="font-medium break-all">{value}</p>
        {copyable && (
          <Button
            type="button"
            variant="ghost"
            size="icon"
            className="h-7 w-7"
            onClick={() => {
              navigator.clipboard.writeText(value);
              toast.success(`Đã sao chép ${label.toLowerCase()}`);
            }}
          >
            <Copy className="h-4 w-4" />
          </Button>
        )}
      </div>
    </div>
  );
}

function formatMoney(value: number) {
  return value.toLocaleString('vi-VN');
}

function paymentMethodLabel(value?: string) {
  if (value === 'CASH') return 'Tiền mặt';
  if (value === 'VNPAY') return 'VNPAY';
  if (value === 'BANK_TRANSFER') return 'Chuyển khoản';
  return value || '-';
}
