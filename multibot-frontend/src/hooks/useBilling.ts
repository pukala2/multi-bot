import { useQuery } from '@tanstack/react-query';
import type { components } from '@/api/schema';

// Typy z kontraktu (openapi.yaml → schema.ts).
export type BillingUsage = components['schemas']['BillingUsageResponse'];
export type BillingPlanInfo = components['schemas']['BillingPlanResponse'];

async function fetchUsage(): Promise<BillingUsage> {
  const r = await fetch('/api/admin/billing/usage', { credentials: 'same-origin' });
  if (!r.ok) throw new Error(`usage failed: ${r.status}`);
  return (await r.json()) as BillingUsage;
}

async function fetchPlan(): Promise<BillingPlanInfo> {
  const r = await fetch('/api/admin/billing/plan', { credentials: 'same-origin' });
  if (!r.ok) throw new Error(`plan failed: ${r.status}`);
  return (await r.json()) as BillingPlanInfo;
}

export function useBillingUsage() {
  return useQuery({
    queryKey: ['billing', 'usage'],
    queryFn: fetchUsage,
    refetchInterval: 30_000,
  });
}

export function useBillingPlan() {
  return useQuery({
    queryKey: ['billing', 'plan'],
    queryFn: fetchPlan,
    staleTime: 5 * 60_000,
  });
}
