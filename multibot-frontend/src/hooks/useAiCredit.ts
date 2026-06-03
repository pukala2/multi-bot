import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { readCookie } from '@/api/client';

export interface AiCreditTopup {
  id: string;
  amountUsd: number;
  note: string | null;
  createdAt: string;
}

export interface AiCreditSummary {
  toppedUp: number;
  spent: number;
  remaining: number;
  topups: AiCreditTopup[];
}

const KEY = ['superadmin', 'ai-credit'] as const;

export function useAiCredit() {
  return useQuery({
    queryKey: KEY,
    queryFn: async (): Promise<AiCreditSummary> => {
      const r = await fetch('/api/super-admin/ai-credit', { credentials: 'same-origin' });
      if (!r.ok) throw new Error(`ai-credit failed: ${r.status}`);
      return (await r.json()) as AiCreditSummary;
    },
    staleTime: 30_000,
  });
}

export function useAddTopup() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ amount, note }: { amount: number; note?: string }): Promise<AiCreditSummary> => {
      const csrf = readCookie('XSRF-TOKEN');
      const headers: Record<string, string> = { 'Content-Type': 'application/json' };
      if (csrf) headers['X-XSRF-TOKEN'] = csrf;
      const r = await fetch('/api/super-admin/ai-credit/topups', {
        method: 'POST',
        headers,
        credentials: 'same-origin',
        body: JSON.stringify({ amount, note: note || undefined }),
      });
      if (!r.ok) {
        let msg = `HTTP ${r.status}`;
        try { const e = await r.json(); msg = e.detail ?? e.message ?? msg; } catch { /* ignore */ }
        throw new Error(msg);
      }
      return (await r.json()) as AiCreditSummary;
    },
    onSuccess: (data) => {
      qc.setQueryData(KEY, data);
    },
  });
}
