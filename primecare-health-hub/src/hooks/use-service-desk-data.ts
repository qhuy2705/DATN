import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import apiClient from '@/lib/api-client';
import {
  normalizeAuditLog,
  normalizeServiceDeskQueueItem,
  unwrapApiData,
  unwrapPage,
} from '@/lib/api-adapters';
import { getApiErrorMessage } from '@/lib/error-utils';
import type {
  AuditLog,
  ServiceDeskQueueItem,
  ServiceDeskSummary,
  ServiceResultTemplateCode,
} from '@/types/api';

export const serviceDeskQueryKeys = {
  queue: (params?: Record<string, string>) => ['service-desk', 'queue', params] as const,
  summary: (departmentCode?: string) => ['service-desk', 'summary', departmentCode] as const,
  history: (itemId?: string) => ['service-desk', 'history', itemId] as const,
};

export type SubmitServiceResultBody = {
  resultTextVn?: string;
  resultTextEn?: string;
  resultDataJson?: string;
  fieldValuesJson?: string;
  attachmentUrl?: string;
  attachmentMimeType?: string;
  attachmentUrlsJson?: string;
  conclusionText?: string;
  impressionText?: string;
  templateCode?: ServiceResultTemplateCode;
  templateSchemaJson?: string;
  reportTitle?: string;
};

export function useServiceDeskQueue(params?: Record<string, string>) {
  return useQuery({
    queryKey: serviceDeskQueryKeys.queue(params),
    queryFn: async () => {
      const { data } = await apiClient.get('/service-desk/results', { params });
      const page = unwrapPage<unknown>(data);
      return {
        ...page,
        items: page.items.map(normalizeServiceDeskQueueItem) as ServiceDeskQueueItem[],
      };
    },
    staleTime: 15_000,
    refetchOnWindowFocus: false,
    placeholderData: (previousData) => previousData,
  });
}

export function useServiceDeskSummary(departmentCode?: string) {
  return useQuery({
    queryKey: serviceDeskQueryKeys.summary(departmentCode),
    queryFn: async () => {
      const { data } = await apiClient.get('/service-desk/results/summary', {
        params: departmentCode ? { departmentCode } : undefined,
      });
      return unwrapApiData<ServiceDeskSummary>(data);
    },
    staleTime: 30_000,
    refetchOnWindowFocus: false,
  });
}

export function useServiceResultHistory(itemId?: string) {
  return useQuery({
    queryKey: serviceDeskQueryKeys.history(itemId),
    queryFn: async () => {
      const { data } = await apiClient.get(`/service-desk/results/${itemId}/history`);
      const raw = unwrapApiData<unknown[]>(data);
      return Array.isArray(raw) ? raw.map(normalizeAuditLog) : [];
    },
    enabled: !!itemId,
  });
}

export function useSubmitServiceResult() {
  const qc = useQueryClient();

  return useMutation({
    mutationFn: async ({ itemId, body }: { itemId: string; body: SubmitServiceResultBody }) => {
      const { data } = await apiClient.post(`/service-desk/results/${itemId}`, body);
      return unwrapApiData(data);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['service-desk'] });
      qc.invalidateQueries({ queryKey: ['doctor', 'encounter'] });
      toast.success('Đã cập nhật kết quả cận lâm sàng');
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, 'Cập nhật kết quả cận lâm sàng thất bại'));
    },
  });
}

// Giữ lại hook này để tương thích ngược với dữ liệu cũ nếu cần,
// nhưng UX chính không còn dùng quy trình xác nhận 2 bước nữa.
export function useVerifyServiceResult() {
  const qc = useQueryClient();

  return useMutation({
    mutationFn: async (itemId: string) => {
      const { data } = await apiClient.post(`/service-desk/results/${itemId}/verify`);
      return unwrapApiData(data);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['service-desk'] });
      qc.invalidateQueries({ queryKey: ['doctor', 'encounter'] });
      toast.success('Đã xác nhận kết quả cận lâm sàng');
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, 'Xác nhận kết quả cận lâm sàng thất bại'));
    },
  });
}
