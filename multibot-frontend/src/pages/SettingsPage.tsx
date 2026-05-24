import { useEffect, useState } from 'react';
import { Save, RotateCcw, Sparkles, Send } from 'lucide-react';
import { toast } from 'sonner';
import { Topbar } from '@/components/layout/Topbar';
import { Button } from '@/components/ui/button';
import { Card, CardBody, CardHeader, CardTitle, CardSubtitle } from '@/components/ui/card';
import { Textarea } from '@/components/ui/textarea';
import { Slider } from '@/components/ui/slider';
import { useRagSettings, useUpdateRagSettings } from '@/hooks/useRagSettings';
import { useStartSession } from '@/hooks/useStartSession';
import { useStreamMessage } from '@/hooks/useStreamMessage';
import { showApiError } from '@/api/errors';
import { ChatMessage, type ChatRole } from '@/components/domain/ChatMessage';

const DEFAULT_PROMPT_PLACEHOLDER = `Jesteś pomocnym asystentem firmy. Odpowiadasz na pytania użytkowników WYŁĄCZNIE
na podstawie poniższych fragmentów dokumentów firmowych (KONTEKST).

Zasady:
- Jeśli odpowiedź NIE znajduje się w kontekście, odpowiedz: "Nie znam odpowiedzi na to pytanie."
- Nie wymyślaj informacji. Nie zgaduj.
- Cytuj numery źródeł w nawiasach kwadratowych [1], [2] gdzie to istotne.
- Odpowiadaj zwięźle i konkretnie.
- Używaj języka polskiego.`;

interface Draft {
  minScore: number;
  topK: number;
  temperature: number;
  systemPrompt: string;
}

interface PreviewTurn {
  role: ChatRole;
  content: string;
}

export function SettingsPage() {
  const settings = useRagSettings();
  const updateMutation = useUpdateRagSettings();
  const [draft, setDraft] = useState<Draft | null>(null);

  const startSession = useStartSession();
  const stream = useStreamMessage();
  const [previewSessionId, setPreviewSessionId] = useState<string | null>(null);
  const [previewInput, setPreviewInput] = useState('');
  const [previewTurns, setPreviewTurns] = useState<PreviewTurn[]>([]);

  useEffect(() => {
    if (settings.data && !draft) {
      setDraft({
        minScore: settings.data.minScore,
        topK: settings.data.topK,
        temperature: settings.data.temperature,
        systemPrompt: settings.data.systemPrompt ?? '',
      });
    }
  }, [settings.data, draft]);

  useEffect(() => {
    if (!stream.isStreaming && stream.text) {
      setPreviewTurns((t) => {
        const last = t[t.length - 1];
        if (last?.role === 'assistant' && last.content === stream.text) return t;
        if (last?.role !== 'user') return t;
        return [...t, { role: 'assistant', content: stream.text }];
      });
    }
  }, [stream.isStreaming, stream.text]);

  const handleSave = () => {
    if (!draft) return;
    updateMutation.mutate(
      {
        minScore: draft.minScore,
        topK: draft.topK,
        temperature: draft.temperature,
        systemPrompt: draft.systemPrompt.trim() || undefined,
      },
      {
        onSuccess: () => toast.success('Ustawienia zapisane'),
        onError: (err) => showApiError(err, 'Nie udało się zapisać'),
      }
    );
  };

  const handleReset = () => {
    if (!draft) return;
    setDraft({ ...draft, systemPrompt: '' });
  };

  const sendPreview = () => {
    const text = previewInput.trim();
    if (!text) return;

    const askWithSession = (sid: string) => {
      setPreviewTurns((t) => [...t, { role: 'user', content: text }]);
      setPreviewInput('');
      stream.reset();
      void stream.start({ sessionId: sid, content: text });
    };

    if (previewSessionId) {
      askWithSession(previewSessionId);
      return;
    }
    startSession.mutate(undefined, {
      onSuccess: (session) => {
        const sid = session.id ?? '';
        setPreviewSessionId(sid);
        askWithSession(sid);
      },
      onError: (err) => showApiError(err, 'Nie udało się rozpocząć podglądu'),
    });
  };

  if (settings.isPending || !draft) {
    return (
      <>
        <Topbar title="Ustawienia bota" />
        <Card><CardBody><div className="h-48 bg-bg-elevated/40 rounded animate-pulse" /></CardBody></Card>
      </>
    );
  }

  const minScoreLabel =
    draft.minScore <= 0.2 ? 'bardzo luźne' :
    draft.minScore <= 0.4 ? 'luźne' :
    draft.minScore <= 0.6 ? 'umiarkowane' :
    draft.minScore <= 0.8 ? 'ścisłe' : 'bardzo ścisłe';

  return (
    <>
      <Topbar
        title="Ustawienia bota"
        actions={
          <Button onClick={handleSave} disabled={updateMutation.isPending}>
            <Save className="size-3.5" />
            {updateMutation.isPending ? 'Zapisuję…' : 'Zapisz'}
          </Button>
        }
      />

      <div className="grid grid-cols-[1fr_400px] gap-4">
        <div className="space-y-4">
          <Card>
            <CardHeader>
              <div>
                <CardTitle>Próg dopasowania (similarity)</CardTitle>
                <CardSubtitle>
                  Im wyższy, tym bot rygorystyczniej dobiera fragmenty dokumentów.
                </CardSubtitle>
              </div>
            </CardHeader>
            <CardBody className="space-y-3">
              <div className="flex justify-between text-[12px]">
                <span className="text-text-muted">luźne (bot odpowiada chętniej)</span>
                <span className="text-text-muted">ścisłe (bot często mówi "nie wiem")</span>
              </div>
              <Slider
                value={draft.minScore}
                onChange={(v) => setDraft({ ...draft, minScore: Math.round(v * 100) / 100 })}
                min={0}
                max={1}
                step={0.05}
              />
              <div className="flex justify-between items-baseline">
                <span className="text-text-dim text-[11.5px]">0.00 — 1.00</span>
                <span className="text-text font-mono text-[15px]">
                  {draft.minScore.toFixed(2)} <span className="text-text-muted text-[12px]">({minScoreLabel})</span>
                </span>
              </div>
            </CardBody>
          </Card>

          <Card>
            <CardHeader>
              <div>
                <CardTitle>Liczba źródeł (top-K)</CardTitle>
                <CardSubtitle>
                  Ile fragmentów dokumentów bot bierze pod uwagę przed odpowiedzią.
                </CardSubtitle>
              </div>
            </CardHeader>
            <CardBody className="space-y-3">
              <Slider
                value={draft.topK}
                onChange={(v) => setDraft({ ...draft, topK: Math.round(v) })}
                min={1}
                max={20}
                step={1}
              />
              <div className="flex justify-between items-baseline">
                <span className="text-text-dim text-[11.5px]">1 — 20</span>
                <span className="text-text font-mono text-[15px]">{draft.topK} fragmentów</span>
              </div>
            </CardBody>
          </Card>

          <Card>
            <CardHeader>
              <div>
                <CardTitle>Temperatura (kreatywność)</CardTitle>
                <CardSubtitle>
                  0.0 — deterministyczne, jak najbardziej zgodne ze źródłem.
                  1.0 — bardziej swobodne sformułowania.
                </CardSubtitle>
              </div>
            </CardHeader>
            <CardBody className="space-y-3">
              <Slider
                value={draft.temperature}
                onChange={(v) => setDraft({ ...draft, temperature: Math.round(v * 10) / 10 })}
                min={0}
                max={1}
                step={0.1}
              />
              <div className="flex justify-between items-baseline">
                <span className="text-text-dim text-[11.5px]">0.0 — 1.0</span>
                <span className="text-text font-mono text-[15px]">{draft.temperature.toFixed(1)}</span>
              </div>
            </CardBody>
          </Card>

          <Card>
            <CardHeader>
              <div>
                <CardTitle>Prompt systemowy</CardTitle>
                <CardSubtitle>
                  Instrukcja dla bota. Zostaw puste żeby używać domyślnego.
                </CardSubtitle>
              </div>
              {draft.systemPrompt && (
                <Button variant="ghost" size="sm" onClick={handleReset}>
                  <RotateCcw className="size-3" />
                  Przywróć domyślny
                </Button>
              )}
            </CardHeader>
            <CardBody>
              <Textarea
                value={draft.systemPrompt}
                onChange={(e) =>
                  setDraft({ ...draft, systemPrompt: (e.target as HTMLTextAreaElement).value })
                }
                placeholder={DEFAULT_PROMPT_PLACEHOLDER}
                rows={12}
                className="font-mono text-[12.5px]"
              />
              <div className="text-[11.5px] text-text-dim mt-2">
                {draft.systemPrompt.length} / 4000 znaków
              </div>
            </CardBody>
          </Card>
        </div>

        <Card className="flex flex-col h-fit sticky top-6">
          <CardHeader>
            <div>
              <CardTitle>Podgląd na żywo</CardTitle>
              <CardSubtitle>
                Testuj zmiany. <span className="text-warning">Zapisz najpierw</span> żeby aktywować.
              </CardSubtitle>
            </div>
          </CardHeader>
          <CardBody className="space-y-3">
            <div className="bg-bg-elevated/30 rounded-lg p-3 min-h-[200px] max-h-[400px] overflow-y-auto space-y-3">
              {previewTurns.length === 0 && !stream.isStreaming && (
                <div className="text-center py-10 text-text-dim text-[12.5px]">
                  <Sparkles className="size-5 mx-auto mb-2 opacity-50" />
                  Wpisz pytanie aby przetestować
                </div>
              )}
              {previewTurns.map((t, i) => (
                <ChatMessage key={i} role={t.role} content={t.content} />
              ))}
              {stream.isStreaming && (
                <ChatMessage role="assistant" content={stream.text} streaming />
              )}
            </div>

            <div className="flex gap-2">
              <input
                value={previewInput}
                onChange={(e) => setPreviewInput((e.target as HTMLInputElement).value)}
                onKeyDown={(e) => { if (e.key === 'Enter') sendPreview(); }}
                placeholder="Zadaj pytanie testowe…"
                disabled={stream.isStreaming || startSession.isPending}
                className="flex-1 h-9 rounded-md border border-border bg-bg-panel px-3 text-[13px] focus:outline-none focus:border-accent"
              />
              <Button
                onClick={sendPreview}
                disabled={!previewInput.trim() || stream.isStreaming || startSession.isPending}
                size="md"
              >
                <Send className="size-3.5" />
              </Button>
            </div>
          </CardBody>
        </Card>
      </div>
    </>
  );
}
