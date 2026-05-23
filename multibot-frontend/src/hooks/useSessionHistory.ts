import { useQuery } from '@tanstack/react-query';
import { api } from '@/api/client';
import { unwrap } from '@/api/errors';

export function useSessionHistory(sessionId: string, page = 0, size = 100) {
  return useQuery({
    queryKey: ['chat-session-history', sessionId, { page, size }],
    queryFn: async () => {
      const result = await api.GET('/api/chat/sessions/{id}/messages', {
        params: { path: { id: sessionId }, query: { page, size, sort: 'createdAt,asc' } },
      });
      return unwrap(result);
    },
    enabled: !!sessionId,
  });
}
