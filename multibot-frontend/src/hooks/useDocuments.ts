import { useQuery } from '@tanstack/react-query';
import { api } from '@/api/client';
import { unwrap } from '@/api/errors';

interface UseDocumentsParams {
  page?: number;
  size?: number;
  sort?: string;
}

const PROCESSING_STATUSES = ['PENDING', 'PROCESSING'];

export function useDocuments({ page = 0, size = 20, sort = 'uploadedAt,desc' }: UseDocumentsParams = {}) {
  return useQuery({
    queryKey: ['documents', { page, size, sort }],
    queryFn: async () => {
      const result = await api.GET('/api/documents', {
        params: { query: { page, size, sort } },
      });
      return unwrap(result);
    },
    refetchInterval: (query) => {
      const data = query.state.data;
      const stillProcessing = data?.content?.some((doc) =>
        PROCESSING_STATUSES.includes(doc.status as string)
      );
      return stillProcessing ? 2_000 : false;
    },
  });
}
