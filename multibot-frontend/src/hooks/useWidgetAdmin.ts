import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';
import { unwrap } from '@/api/errors';
import type { components } from '@/api/schema';

type WidgetSettingsRequest = components['schemas']['WidgetSettingsRequest'];

export function useWidgetAdmin() {
  return useQuery({
    queryKey: ['widget-admin'],
    queryFn: async () => {
      const result = await api.GET('/api/admin/widget', {});
      return unwrap(result);
    },
  });
}

export function useUpdateWidgetSettings() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (settings: WidgetSettingsRequest) => {
      const result = await api.PUT('/api/admin/widget', { body: settings });
      return unwrap(result);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['widget-admin'] });
    },
  });
}
