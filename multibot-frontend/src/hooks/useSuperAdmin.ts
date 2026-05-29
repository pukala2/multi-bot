import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { readCookie } from '@/api/client';

export interface BotRow {
  id: string;
  name: string;
  slug: string;
  userMessages: number;
}

export interface OrganizationRow {
  id: string;
  name: string;
  slug: string;
  plan: string;
  status: string;
  companyName: string | null;
  nip: string | null;
  userMessages: number;     // pooled — suma wszystkich botow org
  effectiveLimit: number;   // -1 = unlimited
  bots: BotRow[];
  createdAt: string;
}

export interface CreateOrganizationInput {
  name: string;
  ownerEmail: string;
  plan: string;
  companyName?: string;
  nip?: string;
  firstBotName?: string;
}

export interface ProvisionResult {
  organizationId: string;
  organizationName: string;
  ownerEmail: string;
  generatedPassword: string;
  firstBotId: string;
  firstBotName: string;
  widgetKey: string;
}

export interface AddBotResult {
  botId: string;
  botName: string;
  widgetKey: string;
}

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
    mutationFn: async ({ organizationId, name }: { organizationId: string; name: string }): Promise<AddBotResult> => {
      const r = await fetch(`/api/super-admin/organizations/${organizationId}/bots`, {
        method: 'POST',
        headers: mutationHeaders(),
        credentials: 'same-origin',
        body: JSON.stringify({ name }),
      });
      if (!r.ok) throw new Error(await parseError(r));
      return (await r.json()) as AddBotResult;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ORGS_KEY });
    },
  });
}
