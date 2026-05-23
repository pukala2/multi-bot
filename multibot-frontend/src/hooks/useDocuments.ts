import { useQuery } from '@tanstack/react-query';
import { api } from '@/api/client';
import { unwrap } from '@/api/errors';

interface UseDocumentsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function useDocuments({ page = 0, size = 20, sort = 'uploadedAt,desc' }: UseDocumentsParams = {}) {
  return useQuery({
    queryKey: ['documents', { page, size, sort }],
    queryFn: async () => {
      const result = await api.GET('/api/documents', {
        params: { query: { page, size, sort } },
      });
      return unwrap(result);
    },
  });
}
