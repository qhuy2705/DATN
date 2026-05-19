import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/lib/api-client';
import { downloadProtectedFile } from '@/lib/download-file';
import { getApiErrorMessage } from '@/lib/error-utils';
import type {
  CashierServiceOrder,
  CashierSummary,
  Invoice,
  PageResponse,
  PaymentMethod,
  PdfJob,
  RefundableInvoiceItem,
} from '@/types/api';
import {
  normalizeCashierServiceOrder,
  normalizeCashierSummary,
  normalizeInvoice,
  normalizeRefundableInvoiceItem,
  normalizePdfJob,
  unwrapApiData,
  unwrapPage,
} from '@/lib/api-adapters';
import { toast } from 'sonner';

type CashierQueryOptions = {
  enabled?: boolean;
  staleTime?: number;
  refetchOnWindowFocus?: boolean;
};

export const cashierQueryKeys = {
  serviceOrders: (params?: Record<string, string>) =>
    ['cashier', 'service-orders', params] as const,
  invoices: (params?: Record<string, string>) => ['cashier', 'invoices', params] as const,
  summary: (params?: Record<string, string>) => ['cashier', 'summary', params] as const,
  invoiceDetail: (invoiceId: string) => ['cashier', 'invoice', invoiceId] as const,
  refundableInvoiceItems: (invoiceId: string | null) =>
    ['cashier', 'invoice', invoiceId, 'refundable-items'] as const,
  invoicePdfJob: (jobId: string | null) => ['cashier', 'invoice-pdf-job', jobId] as const,
};

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function normalizeInvoicePayload(payload: unknown) {
  const data = unwrapApiData(payload);
  const invoicePayload = isRecord(data) && isRecord(data.invoice) ? data.invoice : data;
  return normalizeInvoice(invoicePayload);
}

function collectRefundableItems(payload: unknown): RefundableInvoiceItem[] {
  const data = unwrapApiData(payload);
  if (Array.isArray(data)) return data.map(normalizeRefundableInvoiceItem);
  if (!isRecord(data)) return [];

  const knownGroups: Array<[unknown, string]> = [
    [data.items, 'ITEM'],
    [data.serviceItems, 'SERVICE'],
    [data.services, 'SERVICE'],
    [data.medicationItems, 'MEDICATION'],
    [data.medications, 'MEDICATION'],
    [data.drugs, 'MEDICATION'],
  ];

  return knownGroups.flatMap(([value, itemType]) =>
    Array.isArray(value)
      ? value.map((item) =>
          normalizeRefundableInvoiceItem(
            isRecord(item) && typeof item.itemType === 'undefined'
              ? { ...item, itemType }
              : item,
          ),
        )
      : [],
  );
}

export function useCashierSummary(params?: Record<string, string>, options?: CashierQueryOptions) {
  return useQuery<CashierSummary>({
    queryKey: cashierQueryKeys.summary(params),
    queryFn: async () => {
      const { data } = await apiClient.get('/cashier/summary', { params });
      return normalizeCashierSummary(unwrapApiData(data));
    },
    staleTime: 30_000,
    refetchOnWindowFocus: false,
    placeholderData: (previousData) => previousData,
    ...options,
  });
}

export function useCashierServiceOrders(params?: Record<string, string>, options?: CashierQueryOptions) {
  return useQuery({
    queryKey: cashierQueryKeys.serviceOrders(params),
    queryFn: async () => {
      const { data } = await apiClient.get('/cashier/service-orders', { params });
      const page = unwrapPage<unknown>(data);
      return {
        ...page,
        items: page.items.map(normalizeCashierServiceOrder) as CashierServiceOrder[],
      };
    },
    staleTime: 30_000,
    refetchOnWindowFocus: false,
    placeholderData: (previousData) => previousData,
    ...options,
  });
}

export function useCashierInvoices(params?: Record<string, string>, options?: CashierQueryOptions) {
  return useQuery({
    queryKey: cashierQueryKeys.invoices(params),
    queryFn: async () => {
      const { data } = await apiClient.get('/cashier/invoices', { params });
      const page = unwrapPage<unknown>(data);
      return {
        ...page,
        items: page.items.map(normalizeInvoice) as Invoice[],
      };
    },
    staleTime: 30_000,
    refetchOnWindowFocus: false,
    placeholderData: (previousData) => previousData,
    ...options,
  });
}

export function useCashierInvoiceDetail(invoiceId: string | null) {
  return useQuery({
    queryKey: cashierQueryKeys.invoiceDetail(invoiceId || ''),
    queryFn: async () => {
      const { data } = await apiClient.get(`/cashier/invoices/${invoiceId}`);
      return normalizeInvoice(unwrapApiData(data)) as Invoice;
    },
    enabled: !!invoiceId,
  });
}

export function useRefundableInvoiceItems(invoiceId: string | null) {
  return useQuery({
    queryKey: cashierQueryKeys.refundableInvoiceItems(invoiceId),
    queryFn: async () => {
      const { data } = await apiClient.get(`/cashier/invoices/${invoiceId}/refundable-items`);
      return collectRefundableItems(data);
    },
    enabled: Boolean(invoiceId),
    staleTime: 15_000,
  });
}

export function useCreateInvoice() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      serviceOrderId,
      body,
    }: {
      serviceOrderId: string;
      body: { paymentMethod: string; returnUrl?: string };
    }) => {
      const { data } = await apiClient.post(
        `/cashier/service-orders/${serviceOrderId}/invoice`,
        body,
      );
      return normalizeInvoice(unwrapApiData(data));
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['cashier', 'summary'] });
      qc.invalidateQueries({ queryKey: ['cashier', 'service-orders'] });
      qc.invalidateQueries({ queryKey: ['cashier', 'invoices'] });
      toast.success('Đã tạo hóa đơn');
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Tạo hóa đơn thất bại')),
  });
}

export function useChangeInvoicePaymentMethod() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      invoiceId,
      paymentMethod,
      returnUrl,
    }: {
      invoiceId: string;
      paymentMethod: PaymentMethod;
      returnUrl?: string;
    }) => {
      const { data } = await apiClient.post(
        `/cashier/invoices/${invoiceId}/change-payment-method`,
        {
          paymentMethod,
          ...(returnUrl ? { returnUrl } : {}),
        },
      );
      return normalizeInvoicePayload(data);
    },
    onSuccess: (invoice) => {
      qc.setQueryData(cashierQueryKeys.invoiceDetail(invoice.id), invoice);
      qc.getQueriesData<PageResponse<Invoice>>({ queryKey: ['cashier', 'invoices'] }).forEach(([key, page]) => {
        if (!page?.items) return;
        qc.setQueryData(key, {
          ...page,
          items: page.items.map((item) => (item.id === invoice.id ? invoice : item)),
        });
      });
      qc.invalidateQueries({ queryKey: ['cashier', 'summary'] });
      qc.invalidateQueries({ queryKey: ['cashier', 'service-orders'] });
      qc.invalidateQueries({ queryKey: ['cashier', 'invoices'] });
      qc.invalidateQueries({ queryKey: ['cashier', 'invoice'] });
      toast.success('Đã đổi phương thức thanh toán');
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Đổi phương thức thanh toán thất bại')),
  });
}

export function useRefundInvoiceItems() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      invoiceId,
      reason,
      items,
    }: {
      invoiceId: string;
      reason: string;
      items: Array<{ invoiceItemId: number }>;
    }) => {
      const { data } = await apiClient.post(`/cashier/invoices/${invoiceId}/refund-items`, {
        reason,
        items,
      });
      return normalizeInvoicePayload(data);
    },
    onSuccess: (invoice, variables) => {
      if (invoice.id) {
        qc.setQueryData(cashierQueryKeys.invoiceDetail(invoice.id), invoice);
        qc.getQueriesData<PageResponse<Invoice>>({ queryKey: ['cashier', 'invoices'] }).forEach(([key, page]) => {
          if (!page?.items) return;
          qc.setQueryData(key, {
            ...page,
            items: page.items.map((item) => (item.id === invoice.id ? invoice : item)),
          });
        });
      }
      qc.invalidateQueries({ queryKey: ['cashier', 'summary'] });
      qc.invalidateQueries({ queryKey: ['cashier', 'service-orders'] });
      qc.invalidateQueries({ queryKey: ['cashier', 'invoices'] });
      qc.invalidateQueries({ queryKey: ['cashier', 'invoice'] });
      qc.invalidateQueries({ queryKey: cashierQueryKeys.refundableInvoiceItems(variables.invoiceId) });
      qc.invalidateQueries({ queryKey: ['admin', 'dashboard'] });
      qc.invalidateQueries({ queryKey: ['service-desk'] });
      qc.invalidateQueries({ queryKey: ['doctor', 'encounter'] });
      qc.invalidateQueries({ queryKey: ['pharmacist-prescriptions'] });
      qc.invalidateQueries({ queryKey: ['pharmacy-inventory'] });
      qc.invalidateQueries({ queryKey: ['pharmacy-batches'] });
      qc.invalidateQueries({ queryKey: ['pharmacy-expiring-batches'] });
      toast.success('Đã hoàn tiền các mục đã chọn');
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Hoàn tiền theo mục thất bại')),
  });
}

export function useMarkPaid() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (invoiceId: string) => {
      const { data } = await apiClient.post(`/cashier/invoices/${invoiceId}/mark-paid`);
      return normalizeInvoice(unwrapApiData(data));
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['cashier', 'summary'] });
      qc.invalidateQueries({ queryKey: ['cashier', 'service-orders'] });
      qc.invalidateQueries({ queryKey: ['cashier', 'invoices'] });
      qc.invalidateQueries({ queryKey: ['cashier', 'invoice'] });
      toast.success('Đã cập nhật trạng thái thanh toán.');
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Xác nhận thanh toán thất bại')),
  });
}

export function useReconcileBankTransfer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      invoiceId,
      body,
    }: {
      invoiceId: string;
      body: {
        transactionRef: string;
        amount: number;
        provider?: string;
        transferContent?: string;
        bankAccountNo?: string;
        bankCode?: string;
        transactionTime?: string;
        rawPayload?: string;
      };
    }) => {
      const { data } = await apiClient.post(
        `/cashier/invoices/${invoiceId}/bank-transfer/reconcile`,
        body,
      );
      return normalizeInvoice(unwrapApiData(data));
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['cashier', 'summary'] });
      qc.invalidateQueries({ queryKey: ['cashier', 'service-orders'] });
      qc.invalidateQueries({ queryKey: ['cashier', 'invoices'] });
      qc.invalidateQueries({ queryKey: ['cashier', 'invoice'] });
      toast.success('Đã tiếp nhận giao dịch chuyển khoản');
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Đối soát chuyển khoản thất bại')),
  });
}

export function useConfirmBankTransfer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      invoiceId,
      body,
    }: {
      invoiceId: string;
      body?: { transactionRef?: string; note?: string };
    }) => {
      const { data } = await apiClient.post(
        `/cashier/invoices/${invoiceId}/bank-transfer/confirm`,
        body ?? {},
      );
      return normalizeInvoice(unwrapApiData(data));
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['cashier', 'summary'] });
      qc.invalidateQueries({ queryKey: ['cashier', 'service-orders'] });
      qc.invalidateQueries({ queryKey: ['cashier', 'invoices'] });
      qc.invalidateQueries({ queryKey: ['cashier', 'invoice'] });
      toast.success('Đã xác nhận chuyển khoản thủ công');
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Xác nhận chuyển khoản thất bại')),
  });
}

export function useCreateInvoicePdfJob() {
  return useMutation({
    mutationFn: async (invoiceId: string) => {
      const { data } = await apiClient.post(`/cashier/invoices/${invoiceId}/pdf-jobs`);
      return normalizePdfJob(unwrapApiData(data));
    },
    onSuccess: () => toast.success('Đang tạo PDF...'),
    onError: (error) => toast.error(getApiErrorMessage(error, 'Tạo PDF thất bại')),
  });
}

export function useInvoicePdfJob(jobId: string | null) {
  return useQuery({
    queryKey: cashierQueryKeys.invoicePdfJob(jobId),
    queryFn: async () => {
      const { data } = await apiClient.get(`/cashier/invoice-pdf-jobs/${jobId}`);
      return normalizePdfJob(unwrapApiData(data)) as PdfJob;
    },
    enabled: !!jobId,
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status === 'PENDING' || status === 'PROCESSING' ? 2000 : false;
    },
  });
}

export function useDownloadInvoicePdf() {
  return useMutation({
    mutationFn: async ({ url, fallbackFilename }: { url: string; fallbackFilename?: string }) => {
      await downloadProtectedFile(url, fallbackFilename);
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Tải PDF thất bại')),
  });
}

export function useRefundInvoice() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      invoiceId,
      refundAmount,
      reason,
    }: {
      invoiceId: string;
      refundAmount: number;
      reason: string;
    }) => {
      const { data } = await apiClient.post(`/cashier/invoices/${invoiceId}/refund`, null, {
        params: { refundAmount, reason },
      });
      return normalizeInvoice(unwrapApiData(data));
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['cashier', 'summary'] });
      qc.invalidateQueries({ queryKey: ['cashier', 'service-orders'] });
      qc.invalidateQueries({ queryKey: ['cashier', 'invoices'] });
      qc.invalidateQueries({ queryKey: ['cashier', 'invoice'] });
      qc.invalidateQueries({ queryKey: ['admin', 'dashboard'] });
      toast.success('Đã hoàn tiền hóa đơn');
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Hoàn tiền thất bại')),
  });
}
