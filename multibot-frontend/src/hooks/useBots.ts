import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { readCookie } from '@/api/client';

export interface BotLimit {
  allowed: boolean;
  currentBots: number;
  maxBots: number;      // -1 = bez limitu
  unlimited: boolean;
}

export interface AddedBot {
  botId: string;
  botName: string;
  widgetKey: string;
}

const LIMIT_KEY = ['bots', 'limit'] as const;
const ME_KEY = ['auth', 'me'] as const;

/** Limit botów dla zalogowanego ownera. Wyłączony dla super admina (brak organizacji → 403). */
export function useBotLimit(enabled = true) {
  return useQuery({
    queryKey: LIMIT_KEY,
    enabled,
    queryFn: async (): Promise<BotLimit> => {
      const r = await fetch('/api/bots/limit', { credentials: 'same-origin' });
      if (!r.ok) throw new Error(`bot limit failed: ${r.status}`);
      return (await r.json()) as BotLimit;
    },
    staleTime: 30_000,
  });
}

export function useAddOwnerBot() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ name, url }: { name: string; url?: string }): Promise<AddedBot> => {
      const csrf = readCookie('XSRF-TOKEN');
      const headers: Record<string, string> = { 'Content-Type': 'application/json' };
      if (csrf) headers['X-XSRF-TOKEN'] = csrf;
      const r = await fetch('/api/bots', {
        method: 'POST',
        headers,
        credentials: 'same-origin',
        body: JSON.stringify({ name, url: url || undefined }),
      });
      if (!r.ok) {
        let msg = `HTTP ${r.status}`;
        try {
          const e = await r.json();
          msg = e.detail ?? e.message ?? msg;
        } catch { /* ignore */ }
        throw new Error(msg);
      }
      return (await r.json()) as AddedBot;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ME_KEY });      // /me bots -> przełącznik się odświeża
      qc.invalidateQueries({ queryKey: LIMIT_KEY });
    },
  });
}
