export interface ChatSession {
  id: string;
}

export interface SseHandlers {
  onChunk: (text: string) => void;
  onDone: () => void;
  onError: (msg: string) => void;
}

export async function startSession(apiUrl: string): Promise<ChatSession> {
  const response = await fetch(`${apiUrl}/api/chat/sessions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: '{}',
  });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return response.json();
}

export async function streamMessage(
  apiUrl: string,
  sessionId: string,
  content: string,
  handlers: SseHandlers,
  signal: AbortSignal
): Promise<void> {
  const response = await fetch(`${apiUrl}/api/chat/sessions/${sessionId}/messages`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    },
    body: JSON.stringify({ content }),
    signal,
  });

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
