import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { readCookie } from '@/api/client';

const KEY = ['widget', 'origins'] as const;

/** Dozwolone domeny (allowed_origins) aktywnego bota. Endpoint ręczny (poza openapi). */
export function useWidgetOrigins() {
  return useQuery({
    queryKey: KEY,
    queryFn: async (): Promise<string[]> => {
      const r = await fetch('/api/admin/widget/origins', { credentials: 'same-origin' });
      if (!r.ok) throw new Error(`origins failed: ${r.status}`);
      const data = await r.json();
      return (data.origins ?? []) as string[];
    },
    staleTime: 30_000,
  });
}

export function useUpdateWidgetOrigins() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (origins: string[]): Promise<string[]> => {
      const csrf = readCookie('XSRF-TOKEN');
      const headers: Record<string, string> = { 'Content-Type': 'application/json' };
      if (csrf) headers['X-XSRF-TOKEN'] = csrf;
      const r = await fetch('/api/admin/widget/origins', {
        method: 'PUT',
        headers,
        credentials: 'same-origin',
        body: JSON.stringify({ origins }),
      });
      if (!r.ok) {
        let msg = `HTTP ${r.status}`;
        try {
          const e = await r.json();
          msg = e.detail ?? e.message ?? msg;
        } catch { /* ignore */ }
        throw new Error(msg);
      }
      const data = await r.json();
      return (data.origins ?? []) as string[];
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEY });
    },
  });
}
