import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/lib/api-client';
import {
  normalizeExpiringBatch,
  normalizeMedicationBatch,
  normalizePharmacyInventoryItem,
  normalizePrescription,
  unwrapApiData,
  unwrapPage,
} from '@/lib/api-adapters';
import type {
  ApiQueryParams,
  ApiResponse,
  CreateBatchRequest,
  ExpiringBatch,
  MedicationBatch,
  PageResponse,
  PharmacyInventoryItem,
  Prescription,
  PrescriptionStatus,
  UpdateBatchRequest,
} from '@/types/api';
import { toast } from 'sonner';
import { getApiErrorMessage } from '@/lib/error-utils';

const PHARMACY_INVENTORY_QUERY_KEY = ['pharmacy-inventory'] as const;
const PHARMACY_BATCHES_QUERY_KEY = ['pharmacy-batches'] as const;
const PHARMACY_EXPIRING_QUERY_KEY = ['pharmacy-expiring-batches'] as const;

function cleanCreateBatchPayload(payload: CreateBatchRequest): CreateBatchRequest {
  return {
    medicationId: payload.medicationId.trim(),
    batchNumber: payload.batchNumber.trim(),
    quantity: payload.quantity,
    expiryDate: payload.expiryDate,
    ...(payload.status?.trim() ? { status: payload.status.trim() } : {}),
  };
}

function cleanUpdateBatchPayload(payload: UpdateBatchRequest): UpdateBatchRequest {
  return {
    ...(payload.medicationId?.trim() ? { medicationId: payload.medicationId.trim() } : {}),
    ...(payload.batchNumber?.trim() ? { batchNumber: payload.batchNumber.trim() } : {}),
    ...(typeof payload.quantity === 'number' ? { quantity: payload.quantity } : {}),
    ...(payload.expiryDate?.trim() ? { expiryDate: payload.expiryDate.trim() } : {}),
    ...(payload.status?.trim() ? { status: payload.status.trim() } : {}),
  };
}

function invalidatePharmacyInventory(qc: ReturnType<typeof useQueryClient>) {
  qc.invalidateQueries({ queryKey: PHARMACY_INVENTORY_QUERY_KEY });
  qc.invalidateQueries({ queryKey: PHARMACY_BATCHES_QUERY_KEY });
  qc.invalidateQueries({ queryKey: PHARMACY_EXPIRING_QUERY_KEY });
}

export function usePharmacistPrescriptions(params?: { q?: string; status?: PrescriptionStatus; page?: string; size?: string }) {
  return useQuery({
    queryKey: ['pharmacist-prescriptions', params],
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<PageResponse<unknown>>>('/pharmacy/prescriptions', { params });
      const page = unwrapPage<unknown>(data);
      return {
        ...page,
        items: page.items.map((item) => normalizePrescription(item) as Prescription),
      };
    },
    staleTime: 30_000,
    refetchOnWindowFocus: false,
    placeholderData: (previousData) => previousData,
  });
}

export function usePharmacyInventory(params?: ApiQueryParams) {
  return useQuery({
    queryKey: [...PHARMACY_INVENTORY_QUERY_KEY, params],
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<PageResponse<unknown>>>('/pharmacy/inventory', { params });
      const page = unwrapPage<unknown>(data);
      return {
        ...page,
        items: page.items.map((item) => normalizePharmacyInventoryItem(item) as PharmacyInventoryItem),
      };
    },
    staleTime: 30_000,
    refetchOnWindowFocus: false,
    placeholderData: (previousData) => previousData,
  });
}

export function usePharmacyBatches(params?: ApiQueryParams) {
  return useQuery({
    queryKey: [...PHARMACY_BATCHES_QUERY_KEY, params],
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<PageResponse<unknown>>>('/pharmacy/inventory/batches', { params });
      const page = unwrapPage<unknown>(data);
      return {
        ...page,
        items: page.items.map((item) => normalizeMedicationBatch(item) as MedicationBatch),
      };
    },
    staleTime: 30_000,
    refetchOnWindowFocus: false,
    placeholderData: (previousData) => previousData,
  });
}

export function useExpiringBatches(params?: ApiQueryParams) {
  return useQuery({
    queryKey: [...PHARMACY_EXPIRING_QUERY_KEY, params],
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<PageResponse<unknown>>>('/pharmacy/inventory/expiring', { params });
      const page = unwrapPage<unknown>(data);
      return {
        ...page,
        items: page.items.map((item) => normalizeExpiringBatch(item) as ExpiringBatch),
      };
    },
    staleTime: 30_000,
    refetchOnWindowFocus: false,
    placeholderData: (previousData) => previousData,
  });
}

export function useCreateBatch() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (payload: CreateBatchRequest) => {
      const { data } = await apiClient.post<ApiResponse<MedicationBatch>>(
        '/pharmacy/inventory/batches',
        cleanCreateBatchPayload(payload),
      );
      return normalizeMedicationBatch(unwrapApiData(data)) as MedicationBatch;
    },
    onSuccess: () => {
      toast.success('Thêm lô thuốc thành công');
      invalidatePharmacyInventory(qc);
    },
    onError: (e) => {
      toast.error(getApiErrorMessage(e, 'Không thể thêm lô thuốc.'));
    },
  });
}

export function useUpdateBatch() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, payload }: { id: string; payload: UpdateBatchRequest }) => {
      const { data } = await apiClient.put<ApiResponse<MedicationBatch>>(
        `/pharmacy/inventory/batches/${id}`,
        cleanUpdateBatchPayload(payload),
      );
      return normalizeMedicationBatch(unwrapApiData(data)) as MedicationBatch;
    },
    onSuccess: () => {
      toast.success('Cập nhật lô thuốc thành công');
      invalidatePharmacyInventory(qc);
    },
    onError: (e) => {
      toast.error(getApiErrorMessage(e, 'Không thể cập nhật lô thuốc.'));
    },
  });
}

export function useDispensePrescription() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (id: string) => {
      const { data } = await apiClient.post<ApiResponse<Prescription>>(`/pharmacy/prescriptions/${id}/dispense`);
      return normalizePrescription(unwrapApiData(data)) as Prescription;
    },
    onSuccess: (updatedPrescription) => {
      toast.success('Phát thuốc thành công');
      qc.setQueriesData<PageResponse<Prescription>>({ queryKey: ['pharmacist-prescriptions'] }, (previous) => {
        if (!previous) return previous;

        return {
          ...previous,
          items: previous.items.map((item) =>
            item.id === updatedPrescription.id ? updatedPrescription : item,
          ),
        };
      });
      qc.invalidateQueries({ queryKey: ['pharmacist-prescriptions'] });
      invalidatePharmacyInventory(qc);
    },
    onError: (e) => {
      toast.error(getApiErrorMessage(e, 'Không thể phát thuốc. Vui lòng kiểm tra đơn đã thanh toán trước khi phát.'));
    },
  });
}
