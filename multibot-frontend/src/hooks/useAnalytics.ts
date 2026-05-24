import { useQuery } from '@tanstack/react-query';
import { api } from '@/api/client';
import { unwrap } from '@/api/errors';

export type AnalyticsPeriod = 'D1' | 'D7' | 'D30' | 'D90';

export function useAnalytics(period: AnalyticsPeriod) {
  return useQuery({
    queryKey: ['analytics', period],
    queryFn: async () => {
      const result = await api.GET('/api/admin/analytics', {
        params: { query: { period } },
      });
      return unwrap(result);
    },
    refetchInterval: 60_000,
  });
}
