import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { readCookie } from '@/api/client';

export type UserRole = 'SUPER_ADMIN' | 'OWNER' | 'ADMIN';
export type UserStatus = 'ACTIVE' | 'DISABLED' | 'MUST_CHANGE_PASSWORD';

export interface CurrentUser {
  id: string;
  email: string;
  role: UserRole;
  status: UserStatus;
  tenantId: string | null;
  lastLoginAt: string | null;
}

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
