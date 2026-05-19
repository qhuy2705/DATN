import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { Copy, CreditCard, FileText, QrCode, Receipt, ShieldCheck, Wallet, ExternalLink } from 'lucide-react';
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
  DialogDescription,
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
  useChangeInvoicePaymentMethod,
  useConfirmBankTransfer,
  useCreateInvoice,
  useCreateInvoicePdfJob,
  useMarkPaid,
  useReconcileBankTransfer,
  useRefundableInvoiceItems,
  useRefundInvoiceItems,
} from '@/hooks/use-cashier-data';
import { useDebouncedValue } from '@/hooks/use-debounced-value';
import type { CashierServiceOrder, Invoice, InvoiceItemResponse, PaymentMethod, PaymentStatus, RefundableInvoiceItem } from '@/types/api';
import { Textarea } from '@/components/ui/textarea';
import { getPaymentStatusLabel } from '@/lib/filter-options';
import { toLocalDateInputValue } from '@/lib/date';
import { Checkbox } from '@/components/ui/checkbox';
import { getRefundReason, getRefundedAt, isRefundedOrCancelledItem } from '@/lib/refund-status';
import { getApiErrorMessage } from '@/lib/error-utils';

type TabMode = 'service-orders' | 'invoices';

const REVIEWABLE_STATUSES = new Set<PaymentStatus>(['PENDING_CONFIRMATION', 'PAYMENT_REVIEW']);
const PAYMENT_METHOD_OPTIONS: Array<{ value: PaymentMethod; label: string }> = [
  { value: 'CASH', label: 'Tiền mặt' },
  { value: 'BANK_TRANSFER', label: 'Chuyển khoản / VietQR' },
  { value: 'VNPAY', label: 'VNPAY' },
];
const FINAL_PAYMENT_STATUSES = new Set<string>(['PAID', 'REFUNDED', 'PAYMENT_REVIEW', 'VOID', 'CANCELLED']);
const ITEM_REFUND_PAYMENT_STATUSES = new Set<string>(['PAID', 'PARTIALLY_REFUNDED']);

function canChangeInvoicePaymentMethod(invoice?: Invoice | null) {
  if (!invoice || FINAL_PAYMENT_STATUSES.has(invoice.paymentStatus)) return false;
  if (invoice.paymentStatus === 'UNPAID') return true;
  return (
    invoice.paymentStatus === 'PENDING_CONFIRMATION' &&
    invoice.paymentMethod === 'BANK_TRANSFER' &&
    !invoice.paymentDetectedAt &&
    !invoice.paymentReviewReason
  );
}

function canRefundInvoiceItems(invoice?: Invoice | null) {
  return Boolean(invoice && ITEM_REFUND_PAYMENT_STATUSES.has(invoice.paymentStatus));
}

function getInvoiceItemAmount(item: InvoiceItemResponse) {
  return item.refundableAmount ?? item.remainingAmount ?? item.totalAmount ?? item.subtotalAmount ?? 0;
}

function getInvoiceItemGroup(item: InvoiceItemResponse) {
  const raw = [item.itemType, item.referenceType].filter(Boolean).join(' ').toUpperCase();
  return raw.includes('MED') || raw.includes('DRUG') || raw.includes('PRESCRIPTION') ? 'Thuốc' : 'Dịch vụ';
}

function getItemStatusLabel(item: InvoiceItemResponse) {
  return item.refundStatus || item.itemStatus || item.status || (item.refunded ? 'REFUNDED' : 'ACTIVE');
}

function formatDateTime(value?: string) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('vi-VN');
}

function getInvoiceKind(invoice?: Invoice | null) {
  if (!invoice) return 'Hóa đơn';
  const raw = [
    invoice.invoiceType,
    invoice.type,
    invoice.referenceType,
    ...(invoice.items ?? []).map((item) => item.referenceType),
  ]
    .filter(Boolean)
    .join(' ')
    .toUpperCase();

  if (raw.includes('PRESCRIPTION') || raw.includes('MEDICATION') || raw.includes('DRUG')) {
    return 'Hóa đơn thuốc';
  }
  if (raw.includes('SERVICE')) return 'Hóa đơn dịch vụ';
  return 'Hóa đơn';
}

export default function InvoicesPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  
  const queryClient = useQueryClient();
  const [mode, setMode] = useState<TabMode>('service-orders');
  const [serviceOrderPage, setServiceOrderPage] = useState(1);
  const [invoicePage, setInvoicePage] = useState(1);
  const [serviceOrderSearch, setServiceOrderSearch] = useState('');
  const [invoiceSearch, setInvoiceSearch] = useState('');
  const [serviceOrderPaymentFilter, setServiceOrderPaymentFilter] = useState('__all__');
  const [serviceOrderInvoicedFilter, setServiceOrderInvoicedFilter] = useState('__all__');
  const [invoicePaymentFilter, setInvoicePaymentFilter] = useState('__all__');
  const [createInvoiceOpen, setCreateInvoiceOpen] = useState(false);
  const [itemRefundInvoice, setItemRefundInvoice] = useState<Invoice | null>(null);
  const [selectedRefundItemIds, setSelectedRefundItemIds] = useState<number[]>([]);
  const [itemRefundReason, setItemRefundReason] = useState('');
  const [itemRefundError, setItemRefundError] = useState('');
  const [selectedServiceOrder, setSelectedServiceOrder] =
    useState<CashierServiceOrder | null>(null);
  const [paymentMethod, setPaymentMethod] = useState('CASH');
  const [viewInvoiceId, setViewInvoiceId] = useState<string | null>(null);
  const [markPaidInvoice, setMarkPaidInvoice] = useState<Invoice | null>(null);
  const [changePaymentInvoice, setChangePaymentInvoice] = useState<Invoice | null>(null);
  const [newPaymentMethod, setNewPaymentMethod] = useState<PaymentMethod | ''>('');
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
  const {
    data: refundableItems = [],
    isLoading: refundableItemsLoading,
    isError: refundableItemsError,
    refetch: refetchRefundableItems,
  } = useRefundableInvoiceItems(itemRefundInvoice?.id ?? null);

  const createInvoice = useCreateInvoice();
  const changePaymentMethod = useChangeInvoicePaymentMethod();
  const markPaid = useMarkPaid();
  const refundInvoiceItems = useRefundInvoiceItems();
  const createPdfJob = useCreateInvoicePdfJob();
  const reconcileBankTransfer = useReconcileBankTransfer();
  const confirmBankTransfer = useConfirmBankTransfer();

  const serviceOrders = useMemo(
    () => serviceOrdersData?.items ?? [],
    [serviceOrdersData?.items],
  );
  const invoices = useMemo(() => invoicesData?.items ?? [], [invoicesData?.items]);
  const viewInvoice = invoiceDetail ?? invoices.find((invoice) => invoice.id === viewInvoiceId) ?? null;
  const selectedRefundTotal = useMemo(
    () =>
      refundableItems
        .filter((item) => selectedRefundItemIds.includes(item.id))
        .reduce((total, item) => total + getInvoiceItemAmount(item), 0),
    [refundableItems, selectedRefundItemIds],
  );
  const refundableServiceItems = useMemo(
    () => refundableItems.filter((item) => getInvoiceItemGroup(item) === 'Dịch vụ'),
    [refundableItems],
  );
  const refundableMedicationItems = useMemo(
    () => refundableItems.filter((item) => getInvoiceItemGroup(item) === 'Thuốc'),
    [refundableItems],
  );

  const loadedPageSummary = useMemo(
    () => ({
      serviceOrdersWaiting: serviceOrders.filter((order) => !order.invoiced).length,
      serviceOrdersUnpaidAmount: serviceOrders
        .filter((order) => order.paymentStatus !== 'PAID')
        .reduce((total, order) => total + (order.estimatedTotalAmount ?? 0), 0),
      unpaidInvoices: invoices.filter((invoice) =>
        ['UNPAID', 'PENDING_CONFIRMATION', 'PAYMENT_REVIEW'].includes(invoice.paymentStatus),
      ).length,
    }),
    [invoices, serviceOrders],
  );
  const serviceOrdersWaiting =
    cashierSummary?.serviceOrdersWaiting ?? loadedPageSummary.serviceOrdersWaiting;
  const serviceOrdersUnpaidAmount =
    cashierSummary?.serviceOrdersUnpaidAmount ?? loadedPageSummary.serviceOrdersUnpaidAmount;
  const unpaidInvoices = cashierSummary?.unpaidInvoices ?? loadedPageSummary.unpaidInvoices;
  const netPaidRevenue = cashierSummary?.netPaidRevenueInRange ?? cashierSummary?.paidRevenue ?? 0;
  const grossPaidRevenue = cashierSummary?.grossPaidRevenueInRange ?? netPaidRevenue;
  const refundedRevenue =
    cashierSummary?.refundedAmountForPaidInvoicesInRange ??
    cashierSummary?.refundedAmountInRange ??
    Math.max(0, grossPaidRevenue - netPaidRevenue);
  const refundsProcessedAmount = cashierSummary?.refundsProcessedInRange;
  const revenueDescription = `Tổng thu: ${formatMoney(grossPaidRevenue)} ₫ · Đã hoàn: ${formatMoney(refundedRevenue)} ₫${
    typeof refundsProcessedAmount === 'number' && refundsProcessedAmount > 0
      ? ` · Hoàn phát sinh hôm nay: ${formatMoney(refundsProcessedAmount)} ₫`
      : ''
  }`;

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const status = params.get('vnpayStatus');
    if (!status) return;

    const code = params.get('vnpayCode');
    const txnRef = params.get('vnpayTxnRef');
    const invoiceIdParam = params.get('invoiceId');
    const sync = params.get('sync');

    setMode('invoices');
    if (invoiceIdParam) setViewInvoiceId(invoiceIdParam);

    if (status === 'success') {
      if (sync === 'pending') {
        toast.success('VNPAY báo thanh toán thành công. Hệ thống đang chờ IPN cập nhật hóa đơn.');
      } else {
        toast.success('Thanh toán VNPAY thành công.');
      }
    } else {
      if (code === '24') {
        toast.error('Bạn đã hủy hoặc chưa hoàn tất thanh toán VNPAY.');
      } else if (code === 'invalid_signature') {
        toast.error('Không thể xác thực phản hồi VNPAY.');
      } else {
        toast.error(`Thanh toán VNPAY chưa thành công${code ? ` (${code})` : ''}`);
      }
    }

    // Refetch/invalidate cashier queries so UI refreshes from backend
    try {
      queryClient.invalidateQueries({ queryKey: ['cashier', 'summary'] });
      queryClient.invalidateQueries({ queryKey: ['cashier', 'service-orders'] });
      queryClient.invalidateQueries({ queryKey: ['cashier', 'invoices'] });
      queryClient.invalidateQueries({ queryKey: ['cashier', 'invoice'] });
    } catch (e) {
      // fallback to calling available refetch function
      void refetchInvoices();
    }

    // remove vnpay params from url to avoid repeated toasts on reload
    params.delete('vnpayStatus');
    params.delete('vnpayCode');
    params.delete('vnpayTxnRef');
    params.delete('invoiceId');
    params.delete('sync');
    const nextUrl = `${window.location.pathname}${params.toString() ? `?${params.toString()}` : ''}`;
    window.history.replaceState({}, '', nextUrl);
  }, []);

  useEffect(() => {
    const invoiceId = searchParams.get('invoiceId');
    if (!invoiceId) return;
    setMode('invoices');
    setViewInvoiceId(invoiceId);
  }, [searchParams]);

  const openCreateInvoice = (row: CashierServiceOrder) => {
    setSelectedServiceOrder(row);
    setPaymentMethod('CASH');
    setCreateInvoiceOpen(true);
  };

  const openChangePaymentMethod = (invoice: Invoice) => {
    setChangePaymentInvoice(invoice);
    setNewPaymentMethod('');
    setViewInvoiceId(null);
  };

  const closeChangePaymentMethod = () => {
    if (changePaymentMethod.isPending) return;
    setChangePaymentInvoice(null);
    setNewPaymentMethod('');
  };

  const openItemRefundDialog = (invoice: Invoice) => {
    setItemRefundInvoice(invoice);
    setSelectedRefundItemIds([]);
    setItemRefundReason('');
    setItemRefundError('');
    setViewInvoiceId(null);
  };

  const closeItemRefundDialog = () => {
    if (refundInvoiceItems.isPending) return;
    setItemRefundInvoice(null);
    setSelectedRefundItemIds([]);
    setItemRefundReason('');
    setItemRefundError('');
  };

  const toggleRefundItem = (item: RefundableInvoiceItem, checked: boolean) => {
    if (!item.refundable) return;
    setSelectedRefundItemIds((current) => {
      if (checked) return current.includes(item.id) ? current : [...current, item.id];
      return current.filter((id) => id !== item.id);
    });
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

    if (invoice.paymentMethod === 'VNPAY') {
      const url = invoice.vnpPaymentUrl || invoice.paymentUrl;
      if (url) {
        const popup = window.open(url, '_blank', 'noopener,noreferrer');
        if (popup) {
          toast.success('Đã tạo hóa đơn VNPAY. Vui lòng hoàn tất thanh toán trên cổng VNPAY.');
        } else {
          toast.warning('Trình duyệt đã chặn popup. Vui lòng bấm “Mở cổng VNPAY” trong chi tiết hóa đơn.');
        }
        setViewInvoiceId(invoice.id);
      } else {
        toast.error('Không nhận được liên kết thanh toán VNPAY từ hệ thống.');
        setViewInvoiceId(invoice.id);
      }
      return;
    }

    if (invoice.paymentMethod === 'BANK_TRANSFER') {
      setViewInvoiceId(invoice.id);
      toast.success('Đã tạo hóa đơn chuyển khoản và sinh mã tham chiếu đối soát');
    }
  };

  const submitChangePaymentMethod = async () => {
    if (!changePaymentInvoice || !newPaymentMethod || newPaymentMethod === changePaymentInvoice.paymentMethod) return;
    const selectedPaymentMethod = newPaymentMethod;

    const invoice = await changePaymentMethod.mutateAsync({
      invoiceId: changePaymentInvoice.id,
      paymentMethod: selectedPaymentMethod,
      ...(selectedPaymentMethod === 'VNPAY'
        ? { returnUrl: window.location.origin + '/app/cashier/invoices' }
        : {}),
    });

    setChangePaymentInvoice(null);
    setNewPaymentMethod('');
    setMode('invoices');
    setViewInvoiceId(invoice.id);

    if (selectedPaymentMethod === 'VNPAY') {
      const url = invoice.vnpPaymentUrl || invoice.paymentUrl;
      if (url) {
        const popup = window.open(url, '_blank', 'noopener,noreferrer');
        if (popup) {
          toast.success('Đã đổi sang VNPAY. Vui lòng hoàn tất thanh toán trên cổng VNPAY.');
        } else {
          toast.warning('Trình duyệt đã chặn popup. Vui lòng bấm “Mở cổng VNPAY” trong chi tiết hóa đơn.');
        }
      } else {
        toast.error('Không nhận được liên kết thanh toán VNPAY từ hệ thống.');
      }
    }
  };

  const submitItemRefund = async () => {
    if (!itemRefundInvoice || refundInvoiceItems.isPending) return;
    const reason = itemRefundReason.trim();

    if (selectedRefundItemIds.length === 0) {
      setItemRefundError('Vui lòng chọn ít nhất một mục có thể hoàn.');
      return;
    }

    if (!reason) {
      setItemRefundError('Vui lòng nhập lý do hủy/hoàn tiền.');
      return;
    }

    try {
      const invoice = await refundInvoiceItems.mutateAsync({
        invoiceId: itemRefundInvoice.id,
        reason,
        items: selectedRefundItemIds.map((invoiceItemId) => ({ invoiceItemId })),
      });
      const nextInvoiceId = invoice.id || itemRefundInvoice.id;
      setItemRefundInvoice(null);
      setSelectedRefundItemIds([]);
      setItemRefundReason('');
      setItemRefundError('');
      setMode('invoices');
      setViewInvoiceId(nextInvoiceId);
    } catch (error) {
      setItemRefundError(getApiErrorMessage(error, 'Hoàn tiền theo mục thất bại'));
      void refetchRefundableItems();
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

  const submitMarkPaid = async () => {
    if (!markPaidInvoice || markPaid.isPending) return;
    try {
      await markPaid.mutateAsync(markPaidInvoice.id);
      setMarkPaidInvoice(null);
    } catch {
      // useMarkPaid already shows the backend message; keep the dialog open for retry.
    }
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
      key: 'invoiceType',
      header: 'Loại',
      cell: (row) => <span className="text-sm">{getInvoiceKind(row)}</span>,
    },
    {
      key: 'totalAmount',
      header: 'Tổng hóa đơn',
      cell: (row) => (
        <div>
          <p>{formatMoney(row.totalAmount ?? 0)}</p>
          {typeof row.refundedAmount === 'number' && row.refundedAmount > 0 ? (
            <p className="text-xs text-muted-foreground">
              Đã hoàn {formatMoney(row.refundedAmount)} · Còn lại sau hoàn {formatMoney(row.remainingAmount ?? 0)}
            </p>
          ) : null}
        </div>
      ),
    },
    {
      key: 'paymentMethod',
      header: 'Phương thức',
      cell: (row) => (
        <div>
          <p>{paymentMethodLabel(row.paymentMethod)}</p>
          {row.paymentMethod === 'VNPAY' && row.paymentStatus === 'UNPAID' && (
            <p className="text-xs text-muted-foreground">Chờ thanh toán qua VNPAY</p>
          )}
        </div>
      ),
    },
    {
      key: 'paymentStatus',
      header: 'Trạng thái',
      cell: (row) => <StatusBadge status={row.paymentStatus} />,
    },
  ];

  const changePaymentEligible = canChangeInvoicePaymentMethod(changePaymentInvoice);
  const samePaymentMethodSelected =
    Boolean(newPaymentMethod && newPaymentMethod === changePaymentInvoice?.paymentMethod);
  const canSubmitPaymentMethodChange =
    Boolean(changePaymentInvoice && newPaymentMethod) &&
    changePaymentEligible &&
    !samePaymentMethodSelected &&
    !changePaymentMethod.isPending;

  return (
    <div className="space-y-4">
      <PageHeader
        title="Thu ngân"
        description="Theo dõi phiếu chờ thu, tạo hóa đơn, đối soát chuyển khoản và xuất PDF một cách an toàn hơn."
      />

      <div className="space-y-2">
        <p className="text-xs font-medium text-muted-foreground">
          Tổng quan hôm nay · Các thẻ này không áp dụng bộ lọc tìm kiếm hoặc trạng thái bên dưới.
        </p>
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
          <StatCard title="Phiếu chờ tạo hóa đơn hôm nay" value={serviceOrdersWaiting} icon={Receipt} />
          <StatCard
            title="Giá trị chờ thanh toán hôm nay"
            value={formatMoney(serviceOrdersUnpaidAmount)}
            icon={Wallet}
          />
          <StatCard title="Hóa đơn chờ thanh toán hôm nay" value={unpaidInvoices} icon={FileText} />
          <StatCard
            title="Doanh thu ròng đã thu hôm nay"
            value={formatMoney(netPaidRevenue)}
            icon={ShieldCheck}
            description={revenueDescription}
          />
        </div>
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
                  <SelectItem value="PARTIALLY_REFUNDED">{getPaymentStatusLabel('PARTIALLY_REFUNDED')}</SelectItem>
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
                        {canChangeInvoicePaymentMethod(item) && (
                          <Button size="sm" variant="outline" onClick={() => openChangePaymentMethod(item)}>
                            <CreditCard className="mr-1 h-4 w-4" />
                            Đổi phương thức thanh toán
                          </Button>
                        )}
                        {item.paymentStatus === 'UNPAID' && item.paymentMethod === 'CASH' && (
                          <Button
                            size="sm"
                            variant="outline"
                            disabled={markPaid.isPending}
                            onClick={() => setMarkPaidInvoice(item)}
                          >
                            <Wallet className="mr-1 h-4 w-4" />
                            Đánh dấu đã thu
                          </Button>
                        )}
                        {item.paymentMethod === 'VNPAY' && item.paymentStatus !== 'PAID' && (item.vnpPaymentUrl || item.paymentUrl) && (
                          <Button size="sm" variant="outline" onClick={() => {
                            const url = item.vnpPaymentUrl || item.paymentUrl;
                            window.open(url, '_blank', 'noopener,noreferrer');
                          }}>
                            <ExternalLink className="mr-1 h-4 w-4" />
                            Mở cổng VNPAY
                          </Button>
                        )}
                        {item.paymentMethod === 'BANK_TRANSFER' && REVIEWABLE_STATUSES.has(item.paymentStatus) && (
                          <Button size="sm" variant="outline" onClick={() => openReconcileDialog(item)}>
                            <QrCode className="mr-1 h-4 w-4" />
                            Đối soát CK
                          </Button>
                        )}
                    {canRefundInvoiceItems(item) && (
                      <Button
                        size="sm"
                        variant="outline"
                        className="border-destructive/20 text-destructive hover:bg-destructive/10 hover:text-destructive"
                        onClick={() => openItemRefundDialog(item)}
                      >
                        Hủy/hoàn mục chưa thực hiện
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
                  <SelectItem value="BANK_TRANSFER">Chuyển khoản / VietQR</SelectItem>
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

      <Dialog
        open={!!changePaymentInvoice}
        onOpenChange={(open) => {
          if (!open) closeChangePaymentMethod();
        }}
      >
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>Đổi phương thức thanh toán</DialogTitle>
            <DialogDescription>
              Hóa đơn chưa được xác nhận thanh toán. Bạn có thể đổi phương thức trước khi thu tiền.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <div className="rounded-xl border border-border/70 bg-muted/20 px-3 py-3 text-sm">
              <InfoRow label="Mã hóa đơn" value={changePaymentInvoice?.code || changePaymentInvoice?.id} />
              <InfoRow label="Bệnh nhân" value={changePaymentInvoice?.patientName || '-'} />
              <div className="mt-3 grid gap-3 sm:grid-cols-2">
                <div>
                  <p className="text-muted-foreground">Phương thức hiện tại</p>
                  <p className="font-medium text-foreground">
                    {paymentMethodLabel(changePaymentInvoice?.paymentMethod)}
                  </p>
                </div>
                <div>
                  <p className="text-muted-foreground">Trạng thái</p>
                  {changePaymentInvoice ? (
                    <div className="mt-1">
                      <StatusBadge status={changePaymentInvoice.paymentStatus} />
                    </div>
                  ) : null}
                </div>
                <div className="sm:col-span-2">
                  <p className="text-muted-foreground">Tổng hóa đơn</p>
                  <p className="font-semibold text-foreground">
                    {formatMoney(changePaymentInvoice?.totalAmount ?? 0)} ₫
                  </p>
                </div>
              </div>
            </div>

            <div className="rounded-xl border border-warning/25 bg-warning/5 px-3 py-2 text-xs leading-5 text-warning">
              Thông tin thanh toán cũ như mã chuyển khoản hoặc link VNPAY có thể được hủy và tạo lại theo phương thức mới.
            </div>

            {!changePaymentEligible ? (
              <div className="rounded-xl border border-destructive/20 bg-destructive/5 px-3 py-2 text-sm text-destructive">
                Hóa đơn đã thanh toán không thể đổi phương thức.
              </div>
            ) : null}

            <div>
              <label className="mb-1.5 block text-sm font-medium">Phương thức mới</label>
              <Select
                value={newPaymentMethod}
                onValueChange={(value) => setNewPaymentMethod(value as PaymentMethod)}
                disabled={!changePaymentEligible || changePaymentMethod.isPending}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Chọn phương thức mới" />
                </SelectTrigger>
                <SelectContent>
                  {PAYMENT_METHOD_OPTIONS.map((option) => (
                    <SelectItem
                      key={option.value}
                      value={option.value}
                      disabled={option.value === changePaymentInvoice?.paymentMethod}
                    >
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {samePaymentMethodSelected ? (
                <p className="mt-1 text-xs text-warning">Phương thức này đang được áp dụng.</p>
              ) : null}
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={closeChangePaymentMethod} disabled={changePaymentMethod.isPending}>
              Hủy
            </Button>
            <Button
              onClick={() => void submitChangePaymentMethod()}
              disabled={!canSubmitPaymentMethodChange}
            >
              {changePaymentMethod.isPending ? 'Đang đổi...' : 'Xác nhận đổi'}
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
              <div>
                <p className="text-muted-foreground">Loại hóa đơn</p>
                <p className="font-medium">{getInvoiceKind(viewInvoice)}</p>
              </div>
            </div>

            <div className="grid gap-4 lg:grid-cols-[1fr_300px]">
              <div className="space-y-4">
                <div className="border rounded-md">
                  <DataTable 
                     columns={[
                       {
                         key: 'name',
                         header: 'Mục',
                         cell: (r) => (
                           <InvoiceItemNameCell item={r} />
                         ),
                       },
                       { key: 'quantity', header: 'SL' },
                       { key: 'unitPrice', header: 'Đơn giá', cell: (r) => formatMoney(r.unitPrice || 0) },
                       {
                         key: 'subTotalAmount',
                         header: 'Thành tiền',
                         cell: (r) => (
                           <div>
                             <p>{formatMoney(r.subtotalAmount || 0)}</p>
                             {typeof r.refundedAmount === 'number' && r.refundedAmount > 0 ? (
                               <p className="text-xs text-muted-foreground">
                                 Đã hoàn {formatMoney(r.refundedAmount)} · Còn lại sau hoàn {formatMoney(r.remainingAmount ?? 0)}
                               </p>
                             ) : null}
                           </div>
                         ),
                       }
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
                      <div className="flex justify-between text-sm text-success">
                        <span>Giảm giá:</span>
                        <span>-{formatMoney(viewInvoice.discountAmount)} ₫</span>
                      </div>
                    )}
                    <div className="flex justify-between text-sm">
                      <span className="text-muted-foreground">Thuế VAT:</span>
                      <span className="font-medium">{formatMoney(viewInvoice?.taxAmount || 0)} ₫</span>
                    </div>
                    <div className="flex justify-between text-lg font-bold border-t pt-2 mt-2">
                      <span>Tổng hóa đơn:</span>
                      <span>{formatMoney(viewInvoice?.totalAmount || 0)} ₫</span>
                    </div>
                    {typeof viewInvoice?.refundedAmount === 'number' && viewInvoice.refundedAmount > 0 ? (
                      <>
                        <div className="flex justify-between text-sm text-muted-foreground">
                          <span>Đã hoàn:</span>
                          <span>{formatMoney(viewInvoice.refundedAmount)} ₫</span>
                        </div>
                        <div className="flex justify-between text-sm font-semibold">
                          <span>Còn lại sau hoàn:</span>
                          <span>{formatMoney(viewInvoice.remainingAmount ?? 0)} ₫</span>
                        </div>
                      </>
                    ) : null}
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

                {viewInvoice?.paymentMethod === 'VNPAY' && (
                  <>
                    <InfoRow label="Mã giao dịch VNPAY" value={viewInvoice.vnpTxnRef} copyable />
                    {viewInvoice.paymentStatus !== 'PAID' && (viewInvoice.vnpPaymentUrl || viewInvoice.paymentUrl) && (
                      <div className="mt-2">
                        <Button size="sm" variant="outline" onClick={() => {
                          const url = viewInvoice.vnpPaymentUrl || viewInvoice.paymentUrl;
                          window.open(url, '_blank', 'noopener,noreferrer');
                        }}>
                          Mở lại cổng VNPAY
                        </Button>
                      </div>
                    )}
                    <p className="text-xs text-muted-foreground mt-2">
                      {viewInvoice.paymentStatus === 'PAID'
                        ? 'Thanh toán VNPAY đã được xác nhận.'
                        : 'Trạng thái thanh toán sẽ được cập nhật tự động sau khi VNPAY trả kết quả hợp lệ.'}
                    </p>
                  </>
                )}

                {viewInvoice?.paymentMethod === 'BANK_TRANSFER' && (
                  <>
                    {/* QR codes intentionally stay on a white scan surface for camera contrast. */}
                    {viewInvoice.qrCodeBase64 && (
                      <div className="qr-code-scan-surface rounded-xl border border-border bg-white p-3">
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
                      <div className="rounded-lg border border-warning/20 bg-warning/10 px-3 py-2 text-warning">
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
            {canChangeInvoicePaymentMethod(viewInvoice) && (
              <Button
                variant="outline"
                onClick={() => {
                  if (viewInvoice) openChangePaymentMethod(viewInvoice);
                }}
              >
                <CreditCard className="mr-2 h-4 w-4" />
                Đổi phương thức thanh toán
              </Button>
            )}
            {canRefundInvoiceItems(viewInvoice) && (
              <Button
                variant="outline"
                className="border-destructive/20 text-destructive hover:bg-destructive/10 hover:text-destructive"
                onClick={() => {
                  if (viewInvoice) openItemRefundDialog(viewInvoice);
                }}
              >
                Hủy/hoàn mục chưa thực hiện
              </Button>
            )}
            {viewInvoice?.paymentStatus === 'UNPAID' && viewInvoice.paymentMethod === 'CASH' && (
              <Button
                disabled={markPaid.isPending}
                onClick={() => setMarkPaidInvoice(viewInvoice)}
              >
                Xác nhận đã thu tiền
              </Button>
            )}
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <MarkPaidConfirmationDialog
        invoice={markPaidInvoice}
        isPending={markPaid.isPending}
        onOpenChange={(open) => {
          if (!open && !markPaid.isPending) setMarkPaidInvoice(null);
        }}
        onConfirm={() => void submitMarkPaid()}
      />

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

      <Dialog
        open={!!itemRefundInvoice}
        onOpenChange={(open) => {
          if (!open) closeItemRefundDialog();
        }}
      >
        <DialogContent className="max-h-[90vh] max-w-3xl overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Hủy/hoàn mục chưa thực hiện</DialogTitle>
            <DialogDescription>
              Chỉ các dịch vụ chưa thực hiện hoặc thuốc chưa phát mới có thể hoàn tiền. Lịch sử chỉ định vẫn được giữ lại để truy vết.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <div className="grid gap-3 rounded-lg border border-border/70 bg-muted/20 px-3 py-3 text-sm sm:grid-cols-2">
              <InfoRow label="Mã hóa đơn" value={itemRefundInvoice?.code || itemRefundInvoice?.id} />
              <InfoRow label="Bệnh nhân" value={itemRefundInvoice?.patientName || '-'} />
              <div>
                <p className="text-muted-foreground">Trạng thái</p>
                {itemRefundInvoice ? <StatusBadge status={itemRefundInvoice.paymentStatus} /> : null}
              </div>
              <div>
                <p className="text-muted-foreground">Tổng hóa đơn</p>
                <p className="font-semibold text-foreground">{formatMoney(itemRefundInvoice?.totalAmount ?? 0)} ₫</p>
                {typeof itemRefundInvoice?.refundedAmount === 'number' && itemRefundInvoice.refundedAmount > 0 ? (
                  <p className="mt-1 text-xs text-muted-foreground">
                    Đã hoàn {formatMoney(itemRefundInvoice.refundedAmount)} ₫ · Còn lại sau hoàn {formatMoney(itemRefundInvoice.remainingAmount ?? 0)} ₫
                  </p>
                ) : null}
              </div>
            </div>

            {refundableItemsLoading ? (
              <p className="rounded-md border bg-muted/20 px-3 py-3 text-sm text-muted-foreground">
                Đang tải danh sách mục có thể hoàn...
              </p>
            ) : refundableItemsError ? (
              <div className="rounded-md border border-destructive/20 bg-destructive/5 px-3 py-3 text-sm text-destructive">
                Không tải được danh sách mục có thể hoàn.
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  className="ml-2"
                  onClick={() => void refetchRefundableItems()}
                >
                  Thử lại
                </Button>
              </div>
            ) : refundableItems.length === 0 ? (
              <p className="rounded-md border bg-muted/20 px-3 py-3 text-sm text-muted-foreground">
                Không có mục nào đủ điều kiện hủy/hoàn tiền.
              </p>
            ) : (
              <div className="space-y-3">
                <RefundableItemsGroup
                  title="Dịch vụ"
                  items={refundableServiceItems}
                  selectedIds={selectedRefundItemIds}
                  onToggle={toggleRefundItem}
                />
                <RefundableItemsGroup
                  title="Thuốc"
                  items={refundableMedicationItems}
                  selectedIds={selectedRefundItemIds}
                  onToggle={toggleRefundItem}
                />
              </div>
            )}

            <div>
              <label className="mb-1.5 block text-sm font-medium">Lý do hủy/hoàn tiền</label>
              <Textarea
                value={itemRefundReason}
                onChange={(event) => {
                  setItemRefundReason(event.target.value);
                  if (itemRefundError) setItemRefundError('');
                }}
                rows={3}
                placeholder="Nhập lý do hủy/hoàn tiền..."
                disabled={refundInvoiceItems.isPending}
              />
            </div>

            <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border bg-muted/20 px-3 py-2 text-sm">
              <span className="text-muted-foreground">
                Đã chọn {selectedRefundItemIds.length} mục
              </span>
              <span className="font-semibold text-foreground">
                Tổng tiền hoàn: {formatMoney(selectedRefundTotal)} ₫
              </span>
            </div>

            {itemRefundError ? (
              <p className="rounded-md border border-destructive/20 bg-destructive/5 px-3 py-2 text-sm text-destructive">
                {itemRefundError}
              </p>
            ) : null}
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={closeItemRefundDialog} disabled={refundInvoiceItems.isPending}>
              Hủy
            </Button>
            <Button
              variant="destructive"
              disabled={
                selectedRefundItemIds.length === 0 ||
                !itemRefundReason.trim() ||
                refundableItemsLoading ||
                refundInvoiceItems.isPending
              }
              onClick={() => void submitItemRefund()}
            >
              {refundInvoiceItems.isPending ? 'Đang hoàn tiền...' : 'Xác nhận hoàn tiền'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function InvoiceItemNameCell({ item }: { item: InvoiceItemResponse }) {
  const refunded = isRefundedOrCancelledItem(item);
  const reason = getRefundReason(item);
  const refundedAt = getRefundedAt(item);
  const refundedAmount = typeof item.refundedAmount === 'number' ? item.refundedAmount : 0;
  const hasRefundAmount = refundedAmount > 0;
  const remainingAmount = typeof item.remainingAmount === 'number' ? item.remainingAmount : undefined;

  return (
    <div className="min-w-0">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-medium">{item.nameSnapshot || item.referenceType || `Mục #${item.id}`}</span>
        {refunded ? (
          <span className="rounded-full border border-muted-foreground/20 bg-muted px-2 py-0.5 text-xs text-muted-foreground">
            Đã hủy / đã hoàn
          </span>
        ) : null}
      </div>
      <p className="mt-0.5 text-xs text-muted-foreground">
        {getItemStatusLabel(item)}
        {hasRefundAmount
          ? ` · Đã hoàn ${formatMoney(refundedAmount)} ₫`
          : ''}
        {hasRefundAmount && typeof remainingAmount === 'number'
          ? ` · Còn lại sau hoàn ${formatMoney(remainingAmount)} ₫`
          : ''}
      </p>
      {refunded && (reason || refundedAt) ? (
        <p className="mt-0.5 text-xs text-muted-foreground">
          {[reason, refundedAt ? `Lúc ${formatDateTime(refundedAt)}` : ''].filter(Boolean).join(' · ')}
        </p>
      ) : null}
    </div>
  );
}

function RefundableItemsGroup({
  title,
  items,
  selectedIds,
  onToggle,
}: {
  title: 'Dịch vụ' | 'Thuốc';
  items: RefundableInvoiceItem[];
  selectedIds: number[];
  onToggle: (item: RefundableInvoiceItem, checked: boolean) => void;
}) {
  if (items.length === 0) return null;

  return (
    <section className="rounded-lg border border-border/70">
      <div className="flex items-center justify-between border-b px-3 py-2">
        <h4 className="text-sm font-semibold">{title}</h4>
        <span className="text-xs text-muted-foreground">{items.length} mục</span>
      </div>
      <div className="divide-y">
        {items.map((item) => {
          const disabled = !item.refundable;
          const checked = selectedIds.includes(item.id);
          const notRefundableReason = item.notRefundableReason || 'Không thể hoàn';
          return (
            <label
              key={item.id}
              className={`flex gap-3 px-3 py-3 text-sm ${disabled ? 'bg-muted/20 text-muted-foreground' : 'cursor-pointer hover:bg-muted/20'}`}
            >
              <Checkbox
                checked={checked}
                disabled={disabled}
                onCheckedChange={(value) => onToggle(item, value === true)}
                className="mt-1"
              />
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-medium text-foreground">
                    {item.nameSnapshot || item.referenceType || `Mục #${item.id}`}
                  </span>
                  <span className={`rounded-full border px-2 py-0.5 text-xs ${item.refundable ? 'border-success/25 bg-success/10 text-success' : 'border-muted-foreground/20 bg-muted text-muted-foreground'}`}>
                    {item.refundable ? 'Có thể hoàn' : 'Không thể hoàn'}
                  </span>
                  {isRefundedOrCancelledItem(item) ? (
                    <span className="rounded-full border border-muted-foreground/20 bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                      Đã hủy / đã hoàn tiền
                    </span>
                  ) : null}
                </div>
                <p className="mt-1 text-xs text-muted-foreground">
                  SL {item.quantity ?? 1} · {formatMoney(getInvoiceItemAmount(item))} ₫ · {getItemStatusLabel(item)}
                </p>
                {disabled ? (
                  <p className="mt-1 text-xs text-muted-foreground">
                    Không thể hoàn: {notRefundableReason}
                  </p>
                ) : null}
                {typeof item.refundedAmount === 'number' && item.refundedAmount > 0 ? (
                  <p className="mt-1 text-xs text-muted-foreground">
                    Đã hoàn {formatMoney(item.refundedAmount)} ₫
                  </p>
                ) : null}
              </div>
            </label>
          );
        })}
      </div>
    </section>
  );
}

function MarkPaidConfirmationDialog({
  invoice,
  isPending,
  onOpenChange,
  onConfirm,
}: {
  invoice: Invoice | null;
  isPending: boolean;
  onOpenChange: (open: boolean) => void;
  onConfirm: () => void;
}) {
  return (
    <Dialog open={Boolean(invoice)} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>Xác nhận đã thu tiền</DialogTitle>
          <DialogDescription>
            Vui lòng xác nhận bạn đã thu đủ tiền cho hóa đơn này. Thao tác này sẽ cập nhật trạng thái thanh toán và được ghi nhận trong hệ thống.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-3 rounded-lg border border-border/70 bg-muted/20 px-3 py-3 text-sm">
          <InfoRow label="Mã hóa đơn" value={invoice?.code || invoice?.id} />
          <InfoRow label="Bệnh nhân" value={invoice?.patientName || '-'} />
          <div className="grid gap-3 sm:grid-cols-2">
            <div>
              <p className="text-muted-foreground">Số tiền cần thu</p>
              <p className="font-semibold text-foreground">
                {formatMoney(invoice?.totalAmount ?? 0)} ₫
              </p>
            </div>
            <div>
              <p className="text-muted-foreground">Phương thức</p>
              <p className="font-semibold text-foreground">
                {paymentMethodLabel(invoice?.paymentMethod)}
              </p>
            </div>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={isPending}>
            Hủy
          </Button>
          <Button onClick={onConfirm} disabled={!invoice || isPending}>
            {isPending ? 'Đang xác nhận...' : 'Xác nhận đã thu'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
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
  if (value === 'BANK_TRANSFER') return 'Chuyển khoản / VietQR';
  return value || '-';
}
