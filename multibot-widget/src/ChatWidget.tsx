import { useState, useRef, useEffect } from 'preact/hooks';
import type { WidgetConfig } from './config';
import { startSession, streamMessage } from './api';

interface Turn {
  role: 'user' | 'bot';
  content: string;
}

interface Props {
  config: WidgetConfig;
}

export function ChatWidget({ config }: Props) {
  const [open, setOpen] = useState(false);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [turns, setTurns] = useState<Turn[]>([{ role: 'bot', content: config.greeting }]);
  const [streamingText, setStreamingText] = useState('');
  const [isStreaming, setIsStreaming] = useState(false);
  const [input, setInput] = useState('');
  const messagesRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    if (open && !sessionId) {
      startSession(config.apiUrl)
        .then((s) => setSessionId(s.id))
        .catch(() =>
          setTurns((t) => [...t, { role: 'bot', content: 'Nie udało się połączyć z botem. Spróbuj odświeżyć stronę.' }])
        );
    }
  }, [open, sessionId, config.apiUrl]);

  useEffect(() => {
    messagesRef.current?.scrollTo({ top: messagesRef.current.scrollHeight, behavior: 'smooth' });
  }, [turns.length, streamingText, isStreaming]);

  useEffect(() => {
    if (open) inputRef.current?.focus();
  }, [open]);

  const send = () => {
    const text = input.trim();
    if (!text || !sessionId || isStreaming) return;

    setTurns((t) => [...t, { role: 'user', content: text }]);
    setInput('');
    setStreamingText('');
    setIsStreaming(true);

    const controller = new AbortController();
    abortRef.current = controller;
    let accumulated = '';

    void streamMessage(
      config.apiUrl,
      sessionId,
      text,
      {
        onChunk: (chunk) => {
          accumulated += chunk;
          setStreamingText(accumulated);
        },
        onDone: () => {
          setTurns((t) => [...t, { role: 'bot', content: accumulated || '(pusta odpowiedź)' }]);
          setStreamingText('');
          setIsStreaming(false);
        },
        onError: (msg) => {
          setTurns((t) => [...t, { role: 'bot', content: `⚠️ ${msg}` }]);
          setStreamingText('');
          setIsStreaming(false);
        },
      },
      controller.signal
    );
  };

  const onKeyDown = (e: KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      send();
    }
  };

  const rootStyle = { '--mb-accent': config.accentColor } as Record<string, string>;

  return (
    <div className={`multibot-widget-root pos-${config.position}`} style={rootStyle}>
      {!open && (
        <button
          className="multibot-bubble"
          onClick={() => setOpen(true)}
          aria-label="Otwórz czat"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
          </svg>
        </button>
      )}

      {open && (
        <div className="multibot-window">
          <div className="multibot-header">
            <div className="multibot-header-title">{config.title}</div>
            <button
              className="multibot-header-close"
              onClick={() => setOpen(false)}
              aria-label="Zamknij"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round">
                <line x1="18" y1="6" x2="6" y2="18" />
                <line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </div>

          <div className="multibot-messages" ref={messagesRef}>
            {turns.map((turn, i) => (
              <div key={i} className={`multibot-msg ${turn.role}`}>{turn.content}</div>
            ))}
            {isStreaming && (
              <div className="multibot-msg bot">
                {streamingText || (
                  <div className="multibot-typing">
                    <span className="multibot-typing-dot" />
                    <span className="multibot-typing-dot" />
                    <span className="multibot-typing-dot" />
                  </div>
                )}
              </div>
            )}
          </div>

          <div className="multibot-input-row">
            <textarea
              ref={inputRef}
              className="multibot-input"
              value={input}
              onInput={(e) => setInput((e.target as HTMLTextAreaElement).value)}
              onKeyDown={onKeyDown}
              placeholder={config.placeholder}
              disabled={!sessionId || isStreaming}
              rows={1}
            />
            <button
              className="multibot-send"
              onClick={send}
              disabled={!sessionId || isStreaming || !input.trim()}
              aria-label="Wyślij"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <line x1="22" y1="2" x2="11" y2="13" />
                <polygon points="22 2 15 22 11 13 2 9 22 2" />
              </svg>
            </button>
          </div>

          <div className="multibot-footer">
            powered by <a href="https://multibot.app" target="_blank" rel="noopener">Multibot</a>
          </div>
        </div>
      )}
    </div>
  );
}
