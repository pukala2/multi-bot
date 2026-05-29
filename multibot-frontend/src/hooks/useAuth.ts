import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { readCookie } from '@/api/client';
import type { components } from '@/api/schema';

// Typy wyprowadzone z kontraktu (openapi.yaml → schema.ts). Single source of truth.
export type BotSummary = components['schemas']['BotSummary'];
export type CurrentUser = components['schemas']['MeResponse'];
export type UserRole = CurrentUser['role'];
export type UserStatus = CurrentUser['status'];

const ME_KEY = ['auth', 'me'] as const;

async function fetchMe(): Promise<CurrentUser | null> {
  const r = await fetch('/api/auth/me', { credentials: 'same-origin' });
  if (r.status === 401) return null;
  if (!r.ok) throw new Error(`me failed: ${r.status}`);
  return (await r.json()) as CurrentUser;
}

export function useAuth() {
  return useQuery({
    queryKey: ME_KEY,
    queryFn: fetchMe,
    staleTime: 60_000,
    retry: false,
  });
}

interface LoginInput {
  email: string;
  password: string;
}

export function useLogin() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ email, password }: LoginInput): Promise<CurrentUser> => {
      const csrf = readCookie('XSRF-TOKEN');
      const headers: Record<string, string> = { 'Content-Type': 'application/json' };
      if (csrf) headers['X-XSRF-TOKEN'] = csrf;
      const r = await fetch('/api/auth/login', {
        method: 'POST',
        headers,
        credentials: 'same-origin',
        body: JSON.stringify({ email, password }),
      });
      if (r.status === 401) {
        throw new Error('Niepoprawny email lub hasło');
      }
      if (!r.ok) {
        throw new Error(`Login nieudany: HTTP ${r.status}`);
      }
      return (await r.json()) as CurrentUser;
    },
    onSuccess: (user) => {
      qc.setQueryData(ME_KEY, user);
    },
  });
}

export function useSwitchActiveBot() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (tenantId: string): Promise<BotSummary> => {
      const csrf = readCookie('XSRF-TOKEN');
      const headers: Record<string, string> = { 'Content-Type': 'application/json' };
      if (csrf) headers['X-XSRF-TOKEN'] = csrf;
      const r = await fetch('/api/auth/active-bot', {
        method: 'POST',
        headers,
        credentials: 'same-origin',
        body: JSON.stringify({ tenantId }),
      });
      if (!r.ok) throw new Error(`Nie udało się przełączyć bota: HTTP ${r.status}`);
      return (await r.json()) as BotSummary;
    },
    onSuccess: (bot) => {
      qc.setQueryData(ME_KEY, (old: CurrentUser | null | undefined) =>
        old ? { ...old, activeBot: bot } : old);
      // Aktywny bot zmienia tenant -> USUŃ cały cache danych poprzedniego bota (oprócz /me).
      // removeQueries (nie invalidate), żeby strony nie inicjalizowały formularzy ze stale danych
      // poprzedniego bota podczas refetchu (np. ustawienia widgetu zapisywane do złego bota).
      qc.removeQueries({ predicate: (q) => q.queryKey[0] !== 'auth' });
    },
  });
}

export function useLogout() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      const csrf = readCookie('XSRF-TOKEN');
      const headers: Record<string, string> = {};
      if (csrf) headers['X-XSRF-TOKEN'] = csrf;
      const r = await fetch('/api/auth/logout', {
        method: 'POST',
        headers,
        credentials: 'same-origin',
      });
      if (!r.ok && r.status !== 204) {
        throw new Error(`Logout nieudany: HTTP ${r.status}`);
      }
    },
    onSuccess: () => {
      qc.setQueryData(ME_KEY, null);
      qc.clear();
      window.location.href = '/login';
    },
  });
}
