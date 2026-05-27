import { useQuery } from '@tanstack/react-query';

export interface BillingUsage {
  plan: string;
  planName: string;
  monthlyMessagesLimit: number;
  effectiveLimit: number;
  userMessages: number;
  aiMessages: number;
  periodMonth: string;
  resetsAt: string;
  remainingDays: number;
  percentUsed: number;
  unlimited: boolean;
}

export interface BillingPlanInfo {
  code: string;
  name: string;
  monthlyMessagesLimit: number;
  effectiveLimit: number;
  customDomainAllowed: boolean;
  apiAccessAllowed: boolean;
  pricePlnMonthly: number | null;
}

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
