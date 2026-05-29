import { describe, it, expect, beforeEach, vi } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { useSwitchActiveBot, type CurrentUser } from './useAuth';

const ME_KEY = ['auth', 'me'] as const;

const USER: CurrentUser = {
  id: 'u1',
  email: 'owner@demo.local',
  role: 'OWNER',
  status: 'ACTIVE',
  organizationId: 'org1',
  lastLoginAt: null,
  activeBot: { id: 'bot-A', name: 'Bot A', slug: 'bot-a' },
  bots: [
    { id: 'bot-A', name: 'Bot A', slug: 'bot-a' },
    { id: 'bot-B', name: 'Bot B', slug: 'bot-b' },
  ],
};

const NEW_BOT = { id: 'bot-B', name: 'Bot B', slug: 'bot-b' };

function makeWrapper(qc: QueryClient) {
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

describe('useSwitchActiveBot', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=csrf-tok';
    vi.restoreAllMocks();
  });

  it('po sukcesie ustawia activeBot w cache /me i czyści dane innych botów', async () => {
    const fetchMock = vi.fn(async () => ({
      ok: true,
      status: 200,
      json: async () => NEW_BOT,
    }));
    vi.stubGlobal('fetch', fetchMock);

    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    // Cache poprzedniego bota: /me + dane tenantowe które muszą zniknąć.
    qc.setQueryData(ME_KEY, USER);
    qc.setQueryData(['documents'], [{ id: 'doc-stary' }]);
    qc.setQueryData(['widget', 'origins'], ['https://stary.example']);

    const { result } = renderHook(() => useSwitchActiveBot(), { wrapper: makeWrapper(qc) });

    await result.current.mutateAsync('bot-B');

    await waitFor(() => {
      expect(qc.getQueryData<CurrentUser>(ME_KEY)?.activeBot).toEqual(NEW_BOT);
    });

    // /me zachowane (queryKey[0] === 'auth'), reszta usunięta (removeQueries, nie invalidate).
    expect(qc.getQueryData(ME_KEY)).toBeDefined();
    expect(qc.getQueryData(['documents'])).toBeUndefined();
    expect(qc.getQueryData(['widget', 'origins'])).toBeUndefined();
  });

  it('wysyła nagłówek X-XSRF-TOKEN przy mutacji', async () => {
    const fetchMock = vi.fn(async () => ({
      ok: true,
      status: 200,
      json: async () => NEW_BOT,
    }));
    vi.stubGlobal('fetch', fetchMock);

    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    qc.setQueryData(ME_KEY, USER);
    const { result } = renderHook(() => useSwitchActiveBot(), { wrapper: makeWrapper(qc) });

    await result.current.mutateAsync('bot-B');

    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe('/api/auth/active-bot');
    expect(init.method).toBe('POST');
    expect((init.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('csrf-tok');
    expect(JSON.parse(init.body as string)).toEqual({ tenantId: 'bot-B' });
  });

  it('błąd HTTP odrzuca mutację i NIE zmienia activeBot', async () => {
    const fetchMock = vi.fn(async () => ({ ok: false, status: 403, json: async () => ({}) }));
    vi.stubGlobal('fetch', fetchMock);

    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    qc.setQueryData(ME_KEY, USER);
    const { result } = renderHook(() => useSwitchActiveBot(), { wrapper: makeWrapper(qc) });

    await expect(result.current.mutateAsync('bot-B')).rejects.toThrow();
    expect(qc.getQueryData<CurrentUser>(ME_KEY)?.activeBot).toEqual(USER.activeBot);
  });
});
