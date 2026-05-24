import { useEffect, useRef, useState } from 'react';
import { toast } from 'sonner';
import { Plus } from 'lucide-react';
import { Topbar } from '@/components/layout/Topbar';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { ChatMessage, type ChatRole } from '@/components/domain/ChatMessage';
import { ChatInput } from '@/components/domain/ChatInput';
import { SourcesPanel } from '@/components/domain/SourcesPanel';
import { useStartSession } from '@/hooks/useStartSession';
import { useStreamMessage, type ChatSource } from '@/hooks/useStreamMessage';
import { useVersions } from '@/hooks/useVersions';
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
  const [selectedVersionId, setSelectedVersionId] = useState<string>('');
  const startSession = useStartSession();
  const stream = useStreamMessage();
  const versions = useVersions();
  const scrollRef = useRef<HTMLDivElement>(null);
  const finalizedRef = useRef(false);

  useEffect(() => {
    if (versions.data && versions.data.length > 0 && !selectedVersionId) {
      const active = versions.data.find((v) => v.status === 'ACTIVE');
      setSelectedVersionId(active?.id ?? versions.data[0].id);
    }
  }, [versions.data, selectedVersionId]);

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

  const activeVersion = versions.data?.find((v) => v.status === 'ACTIVE');
  const selectedVersion = versions.data?.find((v) => v.id === selectedVersionId);
  const isTestingNonActive = selectedVersion && selectedVersion.status !== 'ACTIVE';

  const handleSend = (content: string) => {
    if (!sessionId) {
      toast.error('Sesja nie jest gotowa');
      return;
    }
    finalizedRef.current = false;
    setTurns((t) => [...t, { role: 'user', content }]);
    void stream.start({
      sessionId,
      content,
      versionId: isTestingNonActive ? selectedVersionId : undefined,
    });
  };

  const handleNewSession = () => {
    stream.reset();
    setTurns([]);
    setSources([]);
    finalizedRef.current = false;
    setSessionId(null);
    startSession.reset();
  };

  const handleVersionChange = (newVersionId: string) => {
    setSelectedVersionId(newVersionId);
    handleNewSession();
  };

  return (
    <>
      <Topbar
        title="Testowy czat"
        actions={
          <div className="flex gap-2 items-center">
            <select
              value={selectedVersionId}
              onChange={(e) => handleVersionChange(e.target.value)}
              disabled={stream.isStreaming || !versions.data}
              className="h-8 rounded-md border border-border bg-bg-panel px-3 text-[13px] text-text focus:outline-none focus:border-accent disabled:opacity-50"
            >
              {(versions.data ?? []).map((v) => (
                <option key={v.id} value={v.id}>
                  {v.name} {v.status === 'ACTIVE' ? '(aktywna)' : v.status === 'DRAFT' ? '(robocza)' : '(archiwum)'}
                </option>
              ))}
            </select>
            <Button variant="secondary" onClick={handleNewSession} disabled={stream.isStreaming}>
              <Plus className="size-3.5" /> Nowa sesja
            </Button>
          </div>
        }
      />

      {isTestingNonActive && (
        <Card className="mb-4 border-warning/40 bg-warning/[0.04]">
          <div className="px-5 py-3 flex items-center gap-2 text-[12.5px]">
            <Badge variant="warning" withDot>tryb testowy</Badge>
            <span className="text-text-muted">
              Testujesz <span className="text-text font-medium">{selectedVersion?.name}</span>{' '}
              ({selectedVersion?.status === 'DRAFT' ? 'robocza' : 'archiwum'}).
              {activeVersion && (
                <> Aktywna na produkcji: <span className="text-text">{activeVersion.name}</span>.</>
              )}
            </span>
          </div>
        </Card>
      )}

      <div className="grid grid-cols-[1fr_360px] gap-4 h-[calc(100vh-200px)]">
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
