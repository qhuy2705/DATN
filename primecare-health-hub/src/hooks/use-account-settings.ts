import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/lib/api-client';
import { normalizeDoctor, unwrapApiData } from '@/lib/api-adapters';
import type { Doctor } from '@/types/api';
import { toast } from 'sonner';
import { useAuthStore } from '@/stores/auth-store';

export function useDoctorMyProfile(enabled = true) {
  return useQuery({
    queryKey: ['doctor', 'profile'],
    enabled,
    queryFn: async () => {
      const { data } = await apiClient.get('/doctor/profile');
      return normalizeDoctor(unwrapApiData(data)) as Doctor;
    },
  });
}

export function useUpdateDoctorMyProfile() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: Record<string, unknown>) => {
      const { data } = await apiClient.put('/doctor/profile', body);
      return normalizeDoctor(unwrapApiData(data)) as Doctor;
    },
    onSuccess: (doctor) => {
      qc.invalidateQueries({ queryKey: ['doctor', 'profile'] });
      qc.invalidateQueries({ queryKey: ['public', 'doctors'] });
      qc.invalidateQueries({ queryKey: ['public', 'doctor'] });
      qc.invalidateQueries({ queryKey: ['admin', 'doctors'] });
      const currentUser = useAuthStore.getState().user;
      if (currentUser) {
        useAuthStore.getState().setUser({
          ...currentUser,
          fullName: doctor.fullName,
          avatarUrl: doctor.avatarUrl,
        });
      }
      toast.success('Cập nhật hồ sơ bác sĩ thành công');
    },
    onError: (error: unknown) => {
      const maybeAxios = error as { response?: { data?: { message?: string } } };
      toast.error(maybeAxios.response?.data?.message || 'Cập nhật hồ sơ thất bại');
    },
  });
}
