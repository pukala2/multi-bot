import { useQuery } from '@tanstack/react-query';
import { api } from '@/api/client';
import { unwrap } from '@/api/errors';

export function useAdminStats() {
  return useQuery({
    queryKey: ['admin-stats'],
    queryFn: async () => {
      const result = await api.GET('/api/admin/stats', {});
      return unwrap(result);
    },
    refetchInterval: 30_000,
    staleTime: 10_000,
  });
}
