import { useQuery } from '@tanstack/react-query';
import { api } from '@/api/client';
import { unwrap } from '@/api/errors';

export function useDocumentFilenames() {
  return useQuery({
    queryKey: ['documents', 'filenames-map'],
    queryFn: async () => {
      const result = await api.GET('/api/documents', {
        params: { query: { page: 0, size: 100, sort: 'uploadedAt,desc' } },
      });
      const data = unwrap(result);
      const map = new Map<string, string>();
      for (const doc of data.content ?? []) {
        if (doc.filename && doc.id) {
          map.set(doc.filename, doc.id);
        }
      }
      return map;
    },
    staleTime: 5_000,
  });
}
