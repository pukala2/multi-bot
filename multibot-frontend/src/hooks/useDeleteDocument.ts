import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';
import { ApiError, type ProblemDetail } from '@/api/errors';

export function useDeleteDocument() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (id: string) => {
      const { error, response } = await api.DELETE('/api/documents/{id}', {
        params: { path: { id } },
      });
      if (error || !response.ok) {
        throw new ApiError((error as ProblemDetail) ?? { status: response.status, title: `HTTP ${response.status}` });
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['documents'] });
    },
  });
}
