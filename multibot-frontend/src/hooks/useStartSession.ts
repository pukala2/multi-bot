import { useMutation } from '@tanstack/react-query';
import { api } from '@/api/client';
import { unwrap } from '@/api/errors';

export function useStartSession() {
  return useMutation({
    mutationFn: async () => {
      const result = await api.POST('/api/chat/sessions', {
        body: {},
      });
      return unwrap(result);
    },
  });
}
