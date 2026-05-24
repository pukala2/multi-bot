import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';
import { unwrap } from '@/api/errors';

export function useVersions() {
  return useQuery({
    queryKey: ['versions'],
    queryFn: async () => {
      const result = await api.GET('/api/admin/versions', {});
      return unwrap(result);
    },
  });
}

export function useActiveVersion() {
  return useQuery({
    queryKey: ['versions', 'active'],
    queryFn: async () => {
      const result = await api.GET('/api/admin/versions/active', {});
      return unwrap(result);
    },
    retry: false,
  });
}

export function useCreateVersion() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (vars: { name: string; copyFromVersionId?: string }) => {
      const result = await api.POST('/api/admin/versions', {
        body: { name: vars.name, copyFromVersionId: vars.copyFromVersionId },
      });
      return unwrap(result);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['versions'] });
    },
  });
}

export function useActivateVersion() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (id: string) => {
      const result = await api.POST('/api/admin/versions/{id}/activate', {
        params: { path: { id } },
      });
      return unwrap(result);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['versions'] });
      qc.invalidateQueries({ queryKey: ['documents'] });
    },
  });
}

export function useDeleteVersion() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (id: string) => {
      const result = await api.DELETE('/api/admin/versions/{id}', {
        params: { path: { id } },
      });
      if (result.error) throw result.error;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['versions'] });
    },
  });
}

