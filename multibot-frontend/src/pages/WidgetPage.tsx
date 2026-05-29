import { useEffect, useState } from 'react';
import { Copy, Check, Save, Plus, X } from 'lucide-react';
import { toast } from 'sonner';
import { Topbar } from '@/components/layout/Topbar';
import { Button } from '@/components/ui/button';
import { Card, CardBody, CardHeader, CardTitle, CardSubtitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { useWidgetAdmin, useUpdateWidgetSettings } from '@/hooks/useWidgetAdmin';
import { useWidgetOrigins, useUpdateWidgetOrigins } from '@/hooks/useWidgetOrigins';
import { showApiError } from '@/api/errors';
import type { components } from '@/api/schema';

type Position = 'bottom-right' | 'bottom-left';
type Settings = components['schemas']['WidgetSettingsRequest'];

// Bazuje na aktualnym origin panelu: dev -> localhost:5173 (ale widget i tak z :8080),
// prod -> https://twoja-domena. Caddy serwuje widget i API z tego samego origin co panel,
// wiec window.location.origin jest poprawnym hostem dla embed + api-url.
// W dev nadpisujemy na :8080 (bo tam stoi backend serwujacy widget).
const WIDGET_HOST = import.meta.env.DEV ? 'http://localhost:8080' : window.location.origin;

function buildSnippet(publicKey: string): string {
  return `<script
  src="${WIDGET_HOST}/widget/embed.js"
  data-multibot-widget
  data-key="${publicKey}"
  data-api-url="${WIDGET_HOST}"
></script>`;
}

export function WidgetPage() {
  const widget = useWidgetAdmin();
  const updateMutation = useUpdateWidgetSettings();
  const origins = useWidgetOrigins();
  const updateOrigins = useUpdateWidgetOrigins();
  const [draft, setDraft] = useState<Settings | null>(null);
  const [originsList, setOriginsList] = useState<string[] | null>(null);
  const [newOrigin, setNewOrigin] = useState('');
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (origins.data && originsList === null) {
      setOriginsList(origins.data);
    }
  }, [origins.data, originsList]);

  // Auto-zapis przy dodaniu/usunięciu domeny — bez osobnego przycisku.
  const persistOrigins = (list: string[]) => {
    setOriginsList(list);
    updateOrigins.mutate(list, {
      onSuccess: (saved) => setOriginsList(saved),
      onError: (err) => {
        showApiError(err, 'Nie udało się zapisać domen');
        setOriginsList(origins.data ?? []);   // cofnij na ostatni zapisany stan
      },
    });
  };

  const addOrigin = () => {
    const v = newOrigin.trim();
    if (!v) return;
    const current = originsList ?? [];
    if (current.includes(v)) { setNewOrigin(''); return; }
    persistOrigins([...current, v]);
    setNewOrigin('');
  };

  const removeOrigin = (idx: number) => {
    const current = originsList ?? [];
    persistOrigins(current.filter((_, i) => i !== idx));
  };

  useEffect(() => {
    if (widget.data && !draft) {
      const s = widget.data.settings;
      setDraft({
        accentColor: s.accentColor,
        position: s.position,
        title: s.title,
        greeting: s.greeting,
        placeholder: s.placeholder,
        isActive: s.isActive,
      });
    }
  }, [widget.data, draft]);

  const handleSave = () => {
    if (!draft) return;
    updateMutation.mutate(draft, {
      onSuccess: () => toast.success('Ustawienia widgetu zapisane'),
      onError: (err) => showApiError(err, 'Nie udało się zapisać'),
    });
  };

  const handleCopy = async () => {
    if (!widget.data?.publicKey) return;
    await navigator.clipboard.writeText(buildSnippet(widget.data.publicKey));
    setCopied(true);
    toast.success('Skopiowano do schowka');
    setTimeout(() => setCopied(false), 2000);
  };

  const updateField = <K extends keyof Settings>(field: K, value: Settings[K]) => {
    setDraft((d) => (d ? { ...d, [field]: value } : null));
  };

  if (widget.isPending) {
    return (
      <>
        <Topbar title="Widget" />
        <Card><CardBody><div className="h-48 bg-bg-elevated/40 rounded animate-pulse" /></CardBody></Card>
      </>
    );
  }

  if (!widget.data || !draft) return null;

  const snippet = buildSnippet(widget.data.publicKey);

  return (
    <>
      <Topbar
        title="Widget"
        actions={
          <Button onClick={handleSave} disabled={updateMutation.isPending}>
            <Save className="size-3.5" />
            {updateMutation.isPending ? 'Zapisuję…' : 'Zapisz'}
          </Button>
        }
      />

      <div className="grid grid-cols-[1fr_360px] gap-4">
        <div className="space-y-4">
          <Card>
            <CardHeader>
              <div>
                <CardTitle>Kod do wklejenia na stronie</CardTitle>
                <CardSubtitle>
                  Skopiuj poniższy fragment i wklej go przed znacznikiem <code className="text-accent mono">{'</body>'}</code> na swojej stronie.
                </CardSubtitle>
              </div>
              <Button variant="secondary" onClick={handleCopy}>
                {copied ? <Check className="size-3.5" /> : <Copy className="size-3.5" />}
                {copied ? 'Skopiowano' : 'Kopiuj'}
              </Button>
            </CardHeader>
            <CardBody>
              <pre className="bg-bg-elevated border border-border rounded-md p-4 text-[12.5px] mono text-text-muted overflow-x-auto leading-relaxed">
                {snippet}
              </pre>
              <div className="mt-3 text-[12px] text-text-dim">
                Klucz publiczny: <span className="mono text-text-muted">{widget.data.publicKey}</span>
              </div>
            </CardBody>
          </Card>

          <Card>
            <CardHeader>
              <div>
                <CardTitle>Dozwolone domeny</CardTitle>
                <CardSubtitle>
                  Widget zadziała tylko na dodanych domenach. Brak = działa wszędzie (tylko do testów).
                </CardSubtitle>
              </div>
            </CardHeader>
            <CardBody className="space-y-2">
              {(originsList ?? []).length === 0 && !origins.isPending && (
                <p className="text-[12px] text-text-dim">
                  Brak domen — widget działa na każdej stronie. Dodaj domenę, żeby go zabezpieczyć.
                </p>
              )}
              {(originsList ?? []).map((o, i) => (
                <div key={`${o}-${i}`} className="flex items-center justify-between gap-2 bg-bg-elevated rounded-md px-3 py-2">
                  <span className="text-[13px] mono truncate">{o}</span>
                  <button
                    onClick={() => removeOrigin(i)}
                    disabled={updateOrigins.isPending}
                    className="text-text-muted hover:text-error size-7 flex items-center justify-center rounded hover:bg-bg-panel shrink-0 disabled:opacity-50"
                    title="Usuń domenę"
                  >
                    <X className="size-3.5" />
                  </button>
                </div>
              ))}
              <div className="flex gap-2 pt-1">
                <Input
                  value={newOrigin}
                  onChange={(e) => setNewOrigin((e.target as HTMLInputElement).value)}
                  onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); addOrigin(); } }}
                  placeholder="https://twojadomena.pl"
                  className="flex-1 mono text-[12.5px]"
                  disabled={origins.isPending}
                />
                <Button variant="secondary" onClick={addOrigin} disabled={!newOrigin.trim() || updateOrigins.isPending}>
                  <Plus className="size-3.5" /> Dodaj
                </Button>
              </div>
            </CardBody>
          </Card>

          <Card>
            <CardHeader>
              <div>
                <CardTitle>Wygląd i zachowanie</CardTitle>
                <CardSubtitle>Zmiany odświeżają się na żywo u klientów po zapisaniu</CardSubtitle>
              </div>
            </CardHeader>
            <CardBody className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="text-[12.5px] text-text-muted block mb-1.5">Kolor akcentu</label>
                  <div className="flex gap-2 items-center">
                    <input
                      type="color"
                      value={draft.accentColor}
                      onChange={(e) => updateField('accentColor', e.target.value)}
                      className="size-9 rounded-md border border-border bg-bg-panel cursor-pointer"
                    />
                    <Input
                      value={draft.accentColor}
                      onChange={(e) => updateField('accentColor', (e.target as HTMLInputElement).value)}
                      className="flex-1 mono"
                    />
                  </div>
                </div>

                <div>
                  <label className="text-[12.5px] text-text-muted block mb-1.5">Pozycja na stronie</label>
                  <select
                    value={draft.position}
                    onChange={(e) => updateField('position', e.target.value as Position)}
                    className="h-8 w-full rounded-md border border-border bg-bg-panel px-3 text-[13px] text-text focus:outline-none focus:border-accent"
                  >
                    <option value="bottom-right">Prawy dolny róg</option>
                    <option value="bottom-left">Lewy dolny róg</option>
                  </select>
                </div>
              </div>

              <div>
                <label className="text-[12.5px] text-text-muted block mb-1.5">Tytuł okna czatu</label>
                <Input
                  value={draft.title}
                  onChange={(e) => updateField('title', (e.target as HTMLInputElement).value)}
                  maxLength={60}
                />
              </div>

              <div>
                <label className="text-[12.5px] text-text-muted block mb-1.5">Pierwsza wiadomość bota</label>
                <Textarea
                  value={draft.greeting}
                  onChange={(e) => updateField('greeting', (e.target as HTMLTextAreaElement).value)}
                  rows={2}
                />
              </div>

              <div>
                <label className="text-[12.5px] text-text-muted block mb-1.5">Placeholder w polu wpisywania</label>
                <Input
                  value={draft.placeholder}
                  onChange={(e) => updateField('placeholder', (e.target as HTMLInputElement).value)}
                  maxLength={80}
                />
              </div>
            </CardBody>
          </Card>
        </div>

        <Card>
          <CardHeader>
            <div>
              <CardTitle>Podgląd</CardTitle>
              <CardSubtitle>tak będzie wyglądać u klienta</CardSubtitle>
            </div>
          </CardHeader>
          <CardBody>
            <div
              className="rounded-lg overflow-hidden shadow-2xl border border-border"
              style={{ background: '#ffffff' }}
            >
              <div
                className="px-4 py-3 flex items-center justify-between text-white text-[13px] font-semibold"
                style={{ background: draft.accentColor }}
              >
                {draft.title}
                <span>×</span>
              </div>
              <div className="bg-[#f6f6f8] p-3 space-y-2 min-h-[180px]">
                <div className="bg-white border border-[#e5e5ea] rounded-2xl rounded-bl-md px-3 py-2 text-[13px] text-[#1a1a1f] max-w-[85%]">
                  {draft.greeting}
                </div>
                <div
                  className="rounded-2xl rounded-br-md px-3 py-2 text-[13px] text-white max-w-[85%] ml-auto"
                  style={{ background: draft.accentColor }}
                >
                  Ile kosztuje wasza sofa?
                </div>
              </div>
              <div className="p-2 bg-white border-t border-[#e5e5ea] flex gap-2">
                <div className="flex-1 border border-[#e5e5ea] rounded-md px-3 py-2 text-[12.5px] text-[#6e6e76]">
                  {draft.placeholder}
                </div>
                <div
                  className="rounded-md size-9 flex items-center justify-center text-white text-[13px]"
                  style={{ background: draft.accentColor }}
                >
                  →
                </div>
              </div>
            </div>
            <div className="text-[11px] text-text-dim mt-3 text-center">
              pozycja: {draft.position === 'bottom-right' ? 'prawy dół' : 'lewy dół'}
            </div>
          </CardBody>
        </Card>
      </div>
    </>
  );
}
