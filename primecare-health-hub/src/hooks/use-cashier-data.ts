import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/lib/api-client';
import { downloadProtectedFile } from '@/lib/download-file';
import { getApiErrorMessage } from '@/lib/error-utils';
import type { CashierServiceOrder, CashierSummary, Invoice, PdfJob } from '@/types/api';
import {
  normalizeCashierServiceOrder,
  normalizeCashierSummary,
  normalizeInvoice,
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
  invoicePdfJob: (jobId: string | null) => ['cashier', 'invoice-pdf-job', jobId] as const,
};

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
      toast.success('Đã xác nhận thanh toán');
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
      refundAmount?: number;
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
      toast.success('Đã hoàn tiền hóa đơn');
    },
    onError: (error) => toast.error(getApiErrorMessage(error, 'Hoàn tiền thất bại')),
  });
}
