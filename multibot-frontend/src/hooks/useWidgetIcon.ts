import { useMutation, useQueryClient } from '@tanstack/react-query';
import { readCookie } from '@/api/client';
import type { components } from '@/api/schema';

type WidgetSettingsResponse = components['schemas']['WidgetSettingsResponse'];

const ADMIN_KEY = ['widget-admin'] as const;

/** Upload ikonki (multipart przez fetch z CSRF) — backend waliduje magic-bytes/rozmiar. */
export function useUploadWidgetIcon() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (file: File): Promise<WidgetSettingsResponse> => {
      const csrf = readCookie('XSRF-TOKEN');
      const headers: Record<string, string> = {};
      if (csrf) headers['X-XSRF-TOKEN'] = csrf;
      const form = new FormData();
      form.append('file', file);
      const r = await fetch('/api/admin/widget/icon', {
        method: 'POST',
        headers,
        credentials: 'same-origin',
        body: form,
      });
      if (!r.ok) {
        let msg = `HTTP ${r.status}`;
        try { const e = await r.json(); msg = e.detail ?? e.message ?? msg; } catch { /* ignore */ }
        throw new Error(msg);
      }
      return (await r.json()) as WidgetSettingsResponse;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ADMIN_KEY }),
  });
}

export function useDeleteWidgetIcon() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (): Promise<WidgetSettingsResponse> => {
      const csrf = readCookie('XSRF-TOKEN');
      const headers: Record<string, string> = {};
      if (csrf) headers['X-XSRF-TOKEN'] = csrf;
      const r = await fetch('/api/admin/widget/icon', {
        method: 'DELETE',
        headers,
        credentials: 'same-origin',
      });
      if (!r.ok) throw new Error(`HTTP ${r.status}`);
      return (await r.json()) as WidgetSettingsResponse;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ADMIN_KEY }),
  });
}
