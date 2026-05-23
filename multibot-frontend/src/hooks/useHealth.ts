import { useQuery } from '@tanstack/react-query';

export interface HealthStatus {
  status: 'UP' | 'DOWN' | 'OUT_OF_SERVICE' | 'UNKNOWN';
  groups?: string[];
}

export function useHealth() {
  return useQuery({
    queryKey: ['health'],
    queryFn: async (): Promise<HealthStatus> => {
      const response = await fetch('/actuator/health');
      if (!response.ok) {
        throw new Error(`Health check failed: HTTP ${response.status}`);
      }
      return response.json();
    },
    refetchInterval: 30_000,
    staleTime: 10_000,
  });
}
