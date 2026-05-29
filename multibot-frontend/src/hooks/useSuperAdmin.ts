import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { readCookie } from '@/api/client';
import type { components } from '@/api/schema';

// Typy z kontraktu (openapi.yaml → schema.ts).
export type BotRow = components['schemas']['OrganizationBotRow'];
export type OrganizationRow = components['schemas']['OrganizationRow'];      // userMessages pooled; effectiveLimit -1 = unlimited
export type CreateOrganizationInput = components['schemas']['CreateOrganizationRequest'];
export type ProvisionResult = components['schemas']['ProvisionResponse'];
export type AddBotResult = components['schemas']['AddBotResponse'];

const ORGS_KEY = ['superadmin', 'organizations'] as const;

function mutationHeaders(): Record<string, string> {
  const csrf = readCookie('XSRF-TOKEN');
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (csrf) headers['X-XSRF-TOKEN'] = csrf;
  return headers;
}

async function parseError(r: Response): Promise<string> {
  let msg = `HTTP ${r.status}`;
  try {
    const err = await r.json();
    msg = err.detail ?? err.message ?? msg;
  } catch { /* ignore */ }
  return msg;
}

export function useOrganizations() {
  return useQuery({
    queryKey: ORGS_KEY,
    queryFn: async (): Promise<OrganizationRow[]> => {
      const r = await fetch('/api/super-admin/organizations', { credentials: 'same-origin' });
      if (!r.ok) throw new Error(`organizations failed: ${r.status}`);
      return (await r.json()) as OrganizationRow[];
    },
    staleTime: 30_000,
  });
}

export function useCreateOrganization() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (input: CreateOrganizationInput): Promise<ProvisionResult> => {
      const r = await fetch('/api/super-admin/organizations', {
        method: 'POST',
        headers: mutationHeaders(),
        credentials: 'same-origin',
        body: JSON.stringify(input),
      });
      if (!r.ok) throw new Error(await parseError(r));
      return (await r.json()) as ProvisionResult;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ORGS_KEY });
    },
  });
}

export function useAddBot() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ organizationId, name, url }: { organizationId: string; name: string; url?: string }): Promise<AddBotResult> => {
      const r = await fetch(`/api/super-admin/organizations/${organizationId}/bots`, {
        method: 'POST',
        headers: mutationHeaders(),
        credentials: 'same-origin',
        body: JSON.stringify({ name, url: url || undefined }),
      });
      if (!r.ok) throw new Error(await parseError(r));
      return (await r.json()) as AddBotResult;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ORGS_KEY });
    },
  });
}
