export interface ChatSession {
  id: string;
}

export interface SseHandlers {
  onChunk: (text: string) => void;
  onDone: () => void;
  onError: (msg: string) => void;
}

function widgetHeaders(publicKey: string | null, extra?: Record<string, string>): Record<string, string> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json', ...extra };
  if (publicKey) headers['X-Multibot-Widget-Key'] = publicKey;
  return headers;
}

export async function startSession(
  apiUrl: string,
  publicKey: string | null,
  turnstileToken?: string
): Promise<ChatSession> {
  const headers = widgetHeaders(publicKey);
  if (turnstileToken) headers['X-Turnstile-Token'] = turnstileToken;
  const response = await fetch(`${apiUrl}/api/chat/sessions`, {
    method: 'POST',
    headers,
    body: '{}',
  });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return response.json();
}

export async function streamMessage(
  apiUrl: string,
  publicKey: string | null,
  sessionId: string,
  content: string,
  handlers: SseHandlers,
  signal: AbortSignal
): Promise<void> {
  const response = await fetch(`${apiUrl}/api/chat/sessions/${sessionId}/messages`, {
    method: 'POST',
    headers: widgetHeaders(publicKey, { Accept: 'text/event-stream' }),
    body: JSON.stringify({ content }),
    signal,
  });

  if (response.status === 402) {
    // Tenant przekroczyl miesieczny limit wiadomosci
    try {
      const body = await response.json() as { resetsAt?: string };
      const resetDate = body.resetsAt
        ? new Date(body.resetsAt).toLocaleDateString('pl-PL', { day: 'numeric', month: 'long' })
        : '1. dnia następnego miesiąca';
      handlers.onError(`Asystent będzie dostępny ponownie ${resetDate}. W międzyczasie skontaktuj się z nami innym kanałem.`);
    } catch {
      handlers.onError('Asystent czasowo niedostępny.');
    }
    return;
  }

  if (!response.ok || !response.body) {
    handlers.onError(`HTTP ${response.status}`);
    return;
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });

      const events = buffer.split('\n\n');
      buffer = events.pop() ?? '';

      for (const block of events) {
        const lines = block.split('\n');
        let eventName = 'message';
        let data = '';
        for (const line of lines) {
          if (line.startsWith('event:')) eventName = line.slice(6).trim();
          else if (line.startsWith('data:')) data += line.slice(5).trim();
        }
        if (!data) continue;

        try {
          if (eventName === 'chunk') {
            const parsed = JSON.parse(data) as { text: string };
            handlers.onChunk(parsed.text);
          } else if (eventName === 'done') {
            handlers.onDone();
            return;
          } else if (eventName === 'error') {
            const parsed = JSON.parse(data) as { message: string };
            handlers.onError(parsed.message);
            return;
          }
        } catch {
          // ignore parse errors
        }
      }
    }
  } catch (err) {
    if ((err as Error).name !== 'AbortError') {
      handlers.onError((err as Error).message);
    }
  }
}
