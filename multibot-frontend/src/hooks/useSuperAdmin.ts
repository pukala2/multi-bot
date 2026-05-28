import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { readCookie } from '@/api/client';

export interface TenantRow {
  id: string;
  name: string;
  slug: string;
  plan: string;
  status: string;
  companyName: string | null;
  nip: string | null;
  userMessages: number;
  effectiveLimit: number;   // -1 = unlimited
  createdAt: string;
}

export interface CreateTenantInput {
  name: string;
  ownerEmail: string;
  plan: string;
  companyName?: string;
  nip?: string;
}

export interface ProvisionResult {
  tenantId: string;
  tenantName: string;
  ownerEmail: string;
  generatedPassword: string;
  widgetKey: string;
}

const TENANTS_KEY = ['superadmin', 'tenants'] as const;

export function useTenants() {
  return useQuery({
    queryKey: TENANTS_KEY,
    queryFn: async (): Promise<TenantRow[]> => {
      const r = await fetch('/api/super-admin/tenants', { credentials: 'same-origin' });
      if (!r.ok) throw new Error(`tenants failed: ${r.status}`);
      return (await r.json()) as TenantRow[];
    },
    staleTime: 30_000,
  });
}

export function useCreateTenant() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (input: CreateTenantInput): Promise<ProvisionResult> => {
      const csrf = readCookie('XSRF-TOKEN');
      const headers: Record<string, string> = { 'Content-Type': 'application/json' };
      if (csrf) headers['X-XSRF-TOKEN'] = csrf;
      const r = await fetch('/api/super-admin/tenants', {
        method: 'POST',
        headers,
        credentials: 'same-origin',
        body: JSON.stringify(input),
      });
      if (!r.ok) {
        let msg = `HTTP ${r.status}`;
        try {
          const err = await r.json();
          msg = err.detail ?? err.message ?? msg;
        } catch { /* ignore */ }
        throw new Error(msg);
      }
      return (await r.json()) as ProvisionResult;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: TENANTS_KEY });
    },
  });
}
