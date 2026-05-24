import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';
import { unwrap } from '@/api/errors';
import type { components } from '@/api/schema';

type RagSettingsRequest = components['schemas']['RagSettingsRequest'];

export function useRagSettings() {
  return useQuery({
    queryKey: ['rag-settings'],
    queryFn: async () => {
      const result = await api.GET('/api/admin/rag-settings', {});
      return unwrap(result);
    },
  });
}

export function useUpdateRagSettings() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (settings: RagSettingsRequest) => {
      const result = await api.PUT('/api/admin/rag-settings', { body: settings });
      return unwrap(result);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['rag-settings'] });
    },
  });
}
