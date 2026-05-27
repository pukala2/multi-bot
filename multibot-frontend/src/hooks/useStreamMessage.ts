import { useRef, useState, useCallback } from 'react';
import { fetchEventSource, EventStreamContentType } from '@microsoft/fetch-event-source';
import { readCookie } from '@/api/client';

export interface ChatSource {
  chunkId?: string;
  documentId?: string;
  documentFilename?: string;
  snippet?: string;
  score?: number;
}

export interface StreamingState {
  text: string;
  sources: ChatSource[];
  tokensUsed: number | null;
  isStreaming: boolean;
  error: string | null;
}

interface StartStreamOptions {
  sessionId: string;
  content: string;
  versionId?: string;
  onDone?: () => void;
}

const INITIAL_STATE: StreamingState = {
  text: '',
  sources: [],
  tokensUsed: null,
  isStreaming: false,
  error: null,
};

export function useStreamMessage() {
  const [state, setState] = useState<StreamingState>(INITIAL_STATE);
  const abortRef = useRef<AbortController | null>(null);

  const start = useCallback(async ({ sessionId, content, versionId, onDone }: StartStreamOptions) => {
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;

    setState({ text: '', sources: [], tokensUsed: null, isStreaming: true, error: null });

    const url = versionId
      ? `/api/chat/sessions/${sessionId}/messages?versionId=${encodeURIComponent(versionId)}`
      : `/api/chat/sessions/${sessionId}/messages`;

    const csrfToken = readCookie('XSRF-TOKEN');
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    };
    if (csrfToken) headers['X-XSRF-TOKEN'] = csrfToken;

    try {
      await fetchEventSource(url, {
        method: 'POST',
        headers,
        body: JSON.stringify({ content }),
        signal: controller.signal,
        credentials: 'same-origin',
        openWhenHidden: true,
        async onopen(response) {
          if (response.ok && response.headers.get('content-type')?.startsWith(EventStreamContentType)) {
            return;
          }
          throw new Error(`Nieoczekiwana odpowiedź serwera: HTTP ${response.status}`);
        },
        onmessage(event) {
          try {
            if (event.event === 'chunk') {
              const data = JSON.parse(event.data) as { text: string };
              setState((s) => ({ ...s, text: s.text + data.text }));
              return;
            }
            if (event.event === 'sources') {
              const data = JSON.parse(event.data) as { sources: ChatSource[] };
              setState((s) => ({ ...s, sources: data.sources ?? [] }));
              return;
            }
            if (event.event === 'done') {
              const data = JSON.parse(event.data) as { tokensUsed: number };
              setState((s) => ({ ...s, tokensUsed: data.tokensUsed, isStreaming: false }));
              onDone?.();
              controller.abort();
              return;
            }
            if (event.event === 'error') {
              const data = JSON.parse(event.data) as { message: string };
              setState((s) => ({ ...s, error: data.message, isStreaming: false }));
              controller.abort();
            }
          } catch {
            // ignore parse errors on partial events
          }
        },
        onerror(err) {
          setState((s) => ({
            ...s,
            isStreaming: false,
            error: err instanceof Error ? err.message : 'Błąd połączenia',
          }));
          throw err;
        },
      });
    } catch (err) {
      if (controller.signal.aborted) return;
      setState((s) => ({
        ...s,
        isStreaming: false,
        error: err instanceof Error ? err.message : 'Nieznany błąd',
      }));
    }
  }, []);

  const reset = useCallback(() => {
    abortRef.current?.abort();
    setState(INITIAL_STATE);
  }, []);

  return { ...state, start, reset };
}
