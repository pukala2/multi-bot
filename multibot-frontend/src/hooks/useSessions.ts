import { useQuery } from '@tanstack/react-query';
import { api } from '@/api/client';
import { unwrap } from '@/api/errors';

interface UseSessionsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function useSessions({ page = 0, size = 20, sort = 'lastActivityAt,desc' }: UseSessionsParams = {}) {
  return useQuery({
    queryKey: ['chat-sessions', { page, size, sort }],
    queryFn: async () => {
      const result = await api.GET('/api/chat/sessions', {
        params: { query: { page, size, sort } },
      });
      return unwrap(result);
    },
  });
}
