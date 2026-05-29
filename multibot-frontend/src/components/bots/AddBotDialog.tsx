import { useState, type FormEvent } from 'react';
import { Copy, Check } from 'lucide-react';
import { toast } from 'sonner';
import { Dialog, DialogHeader, DialogBody, DialogFooter } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useAddOwnerBot, type AddedBot } from '@/hooks/useBots';
import { cn } from '@/lib/cn';

export function AddBotDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const addBot = useAddOwnerBot();
  const [name, setName] = useState('');
  const [url, setUrl] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<AddedBot | null>(null);

  function close(o: boolean) {
    if (!o) {
      setName('');
      setUrl('');
      setError(null);
      setResult(null);
    }
    onOpenChange(o);
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      const r = await addBot.mutateAsync({ name, url: url.trim() || undefined });
      setResult(r);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Nie udało się dodać bota');
    }
  }

  return (
    <Dialog open={open} onOpenChange={close}>
      {result ? (
        <>
          <DialogHeader
            title="Bot dodany ✓"
            description="Skopiuj klucz widgeta i osadź bota na swojej stronie."
            onClose={() => close(false)}
          />
          <DialogBody className="space-y-2.5">
            <CopyRow label="Bot" value={result.botName} />
            <CopyRow label="Klucz widgeta" value={result.widgetKey} highlight />
          </DialogBody>
          <DialogFooter>
            <Button onClick={() => close(false)}>Gotowe</Button>
          </DialogFooter>
        </>
      ) : (
        <form onSubmit={submit}>
          <DialogHeader
            title="Nowy bot"
            description="Dodaj kolejną stronę / bota. Limit wiadomości pozostaje wspólny dla całego konta."
            onClose={() => close(false)}
          />
          <DialogBody className="space-y-3">
            <div className="space-y-1.5">
              <label className="text-[12px] text-text-muted">Nazwa bota *</label>
              <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="Sklep meblowy" required autoFocus />
            </div>
            <div className="space-y-1.5">
              <label className="text-[12px] text-text-muted">Adres strony (opcj.)</label>
              <Input value={url} onChange={(e) => setUrl(e.target.value)} placeholder="https://twojadomena.pl" />
              <p className="text-[11px] text-text-dim">Zablokuje widget do tej domeny. Możesz zmienić później w zakładce Widget.</p>
            </div>
            {error && (
              <div className="text-[12px] text-error bg-error/10 border border-error/20 rounded-md px-3 py-2">{error}</div>
            )}
          </DialogBody>
          <DialogFooter>
            <Button type="button" variant="secondary" onClick={() => close(false)}>Anuluj</Button>
            <Button type="submit" disabled={addBot.isPending || !name}>
              {addBot.isPending ? 'Tworzę…' : 'Dodaj bota'}
            </Button>
          </DialogFooter>
        </form>
      )}
    </Dialog>
  );
}

function CopyRow({ label, value, highlight }: { label: string; value: string; highlight?: boolean }) {
  const [copied, setCopied] = useState(false);
  function copy() {
    navigator.clipboard.writeText(value).then(() => {
      setCopied(true);
      toast.success(`Skopiowano: ${label}`);
      setTimeout(() => setCopied(false), 1500);
    });
  }
  return (
    <div className="flex items-center justify-between gap-2 bg-bg-elevated rounded-md px-3 py-2">
      <div className="min-w-0">
        <div className="text-[11px] text-text-dim uppercase tracking-wider">{label}</div>
        <div className={cn('text-[13px] mono truncate', highlight ? 'text-accent font-semibold' : 'text-text')}>{value}</div>
      </div>
      <button onClick={copy} className="text-text-muted hover:text-text size-7 flex items-center justify-center rounded hover:bg-bg-panel shrink-0" title="Kopiuj">
        {copied ? <Check className="size-3.5 text-success" /> : <Copy className="size-3.5" />}
      </button>
    </div>
  );
}
