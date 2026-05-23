import { useEffect, useRef, useState } from 'react';
import { toast } from 'sonner';
import { Plus } from 'lucide-react';
import { Topbar } from '@/components/layout/Topbar';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { ChatMessage, type ChatRole } from '@/components/domain/ChatMessage';
import { ChatInput } from '@/components/domain/ChatInput';
import { SourcesPanel } from '@/components/domain/SourcesPanel';
import { useStartSession } from '@/hooks/useStartSession';
import { useStreamMessage, type ChatSource } from '@/hooks/useStreamMessage';
import { showApiError } from '@/api/errors';

interface ChatTurn {
  role: ChatRole;
  content: string;
  tokensUsed?: number | null;
}

export function ChatPage() {
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [turns, setTurns] = useState<ChatTurn[]>([]);
  const [sources, setSources] = useState<ChatSource[]>([]);
  const startSession = useStartSession();
  const stream = useStreamMessage();
  const scrollRef = useRef<HTMLDivElement>(null);
  const finalizedRef = useRef(false);

  useEffect(() => {
    if (!sessionId && !startSession.isPending && !startSession.isSuccess && !startSession.isError) {
      startSession.mutate(undefined, {
        onSuccess: (session) => setSessionId(session.id ?? null),
        onError: (err) => showApiError(err, 'Nie udało się utworzyć sesji'),
      });
    }
  }, [sessionId, startSession]);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: 'smooth' });
  }, [turns.length, stream.text]);

  useEffect(() => {
    if (stream.error) {
      toast.error('Błąd odpowiedzi bota', { description: stream.error });
    }
  }, [stream.error]);

  useEffect(() => {
    if (stream.sources.length > 0) {
      setSources(stream.sources);
    }
  }, [stream.sources]);

  useEffect(() => {
    if (!stream.isStreaming && stream.text && !finalizedRef.current) {
      finalizedRef.current = true;
      setTurns((t) => [
        ...t,
        { role: 'assistant', content: stream.text, tokensUsed: stream.tokensUsed },
      ]);
    }
  }, [stream.isStreaming, stream.text, stream.tokensUsed]);

  const handleSend = (content: string) => {
    if (!sessionId) {
      toast.error('Sesja nie jest gotowa');
      return;
    }
    finalizedRef.current = false;
    setTurns((t) => [...t, { role: 'user', content }]);
    void stream.start({ sessionId, content });
  };

  const handleNewSession = () => {
    stream.reset();
    setTurns([]);
    setSources([]);
    finalizedRef.current = false;
    setSessionId(null);
    startSession.reset();
  };

  return (
    <>
      <Topbar
        title="Testowy czat"
        actions={
          <Button variant="secondary" onClick={handleNewSession} disabled={stream.isStreaming}>
            <Plus className="size-3.5" /> Nowa sesja
          </Button>
        }
      />

      <div className="grid grid-cols-[1fr_360px] gap-4 h-[calc(100vh-160px)]">
        <Card className="flex flex-col overflow-hidden">
          <div ref={scrollRef} className="flex-1 overflow-y-auto p-5 space-y-5">
            {turns.length === 0 && !stream.isStreaming && (
              <div className="text-center py-16">
                <div className="text-text-muted text-[14px]">Brak wiadomości w tej sesji.</div>
                <div className="text-text-dim text-[12.5px] mt-1">
                  Zadaj pytanie aby przetestować bota.
                </div>
              </div>
            )}

            {turns.map((turn, idx) => (
              <ChatMessage
                key={idx}
                role={turn.role}
                content={turn.content}
                tokensUsed={turn.tokensUsed}
              />
            ))}

            {stream.isStreaming && (
              <ChatMessage role="assistant" content={stream.text} streaming />
            )}
          </div>

          <ChatInput
            onSend={handleSend}
            disabled={!sessionId || stream.isStreaming}
            placeholder={
              !sessionId
                ? 'Tworzę sesję…'
                : 'Zadaj pytanie… (Enter wysyła, Shift+Enter nowa linia)'
            }
          />
        </Card>

        <SourcesPanel sources={sources} loading={stream.isStreaming && sources.length === 0} />
      </div>
    </>
  );
}
