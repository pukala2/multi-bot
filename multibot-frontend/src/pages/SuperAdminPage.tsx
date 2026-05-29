import { useState, type FormEvent } from 'react';
import { Plus, Copy, Check, Building2, Bot } from 'lucide-react';
import { toast } from 'sonner';
import { Topbar } from '@/components/layout/Topbar';
import { Card, CardBody } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Dialog, DialogHeader, DialogBody, DialogFooter } from '@/components/ui/dialog';
import {
  useOrganizations,
  useCreateOrganization,
  useAddBot,
  type OrganizationRow,
  type ProvisionResult,
  type AddBotResult,
} from '@/hooks/useSuperAdmin';
import { cn } from '@/lib/cn';
import { botColor, botColorTint } from '@/lib/botColor';

const PLANS = ['FREE', 'BASIC', 'PRO', 'ENTERPRISE'];

export function SuperAdminPage() {
  const orgs = useOrganizations();
  const createOrg = useCreateOrganization();
  const addBot = useAddBot();

  const [createOpen, setCreateOpen] = useState(false);
  const [provisionResult, setProvisionResult] = useState<ProvisionResult | null>(null);
  const [addBotFor, setAddBotFor] = useState<OrganizationRow | null>(null);
  const [addBotResult, setAddBotResult] = useState<AddBotResult | null>(null);

  // create-org form
  const [name, setName] = useState('');
  const [ownerEmail, setOwnerEmail] = useState('');
  const [plan, setPlan] = useState('FREE');
  const [companyName, setCompanyName] = useState('');
  const [nip, setNip] = useState('');
  const [firstBotName, setFirstBotName] = useState('');
  const [createError, setCreateError] = useState<string | null>(null);

  // add-bot form
  const [botName, setBotName] = useState('');
  const [addBotError, setAddBotError] = useState<string | null>(null);

  function resetCreateForm() {
    setName(''); setOwnerEmail(''); setPlan('FREE'); setCompanyName(''); setNip('');
    setFirstBotName(''); setCreateError(null);
  }

  async function handleCreate(e: FormEvent) {
    e.preventDefault();
    setCreateError(null);
    try {
      const r = await createOrg.mutateAsync({
        name, ownerEmail, plan,
        companyName: companyName || undefined,
        nip: nip || undefined,
        firstBotName: firstBotName || undefined,
      });
      setCreateOpen(false);
      resetCreateForm();
      setProvisionResult(r);
    } catch (err) {
      setCreateError(err instanceof Error ? err.message : 'Nie udało się utworzyć klienta');
    }
  }

  async function handleAddBot(e: FormEvent) {
    e.preventDefault();
    if (!addBotFor) return;
    setAddBotError(null);
    try {
      const r = await addBot.mutateAsync({ organizationId: addBotFor.id, name: botName });
      setAddBotFor(null);
      setBotName('');
      setAddBotResult(r);
    } catch (err) {
      setAddBotError(err instanceof Error ? err.message : 'Nie udało się dodać bota');
    }
  }

  return (
    <>
      <Topbar
        title="Super Admin — Klienci"
        actions={
          <Button onClick={() => { resetCreateForm(); setCreateOpen(true); }}>
            <Plus className="size-3.5" /> Nowa organizacja
          </Button>
        }
      />

      {orgs.isPending ? (
        <Card><CardBody><div className="h-40 bg-bg-elevated/40 rounded animate-pulse" /></CardBody></Card>
      ) : !orgs.data || orgs.data.length === 0 ? (
        <Card>
          <CardBody className="py-10 text-center text-text-muted text-[13px]">
            Brak klientów. Utwórz pierwszego przyciskiem „Nowa organizacja".
          </CardBody>
        </Card>
      ) : (
        <div className="space-y-3">
          {orgs.data.map((org) => (
            <OrganizationCard key={org.id} org={org} onAddBot={() => { setBotName(''); setAddBotError(null); setAddBotFor(org); }} />
          ))}
        </div>
      )}

      {/* Nowa organizacja */}
      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogHeader
          title="Nowa organizacja"
          description="Tworzy klienta + konto OWNER + pierwszego bota + widget. Hasło wygeneruje się automatycznie."
          onClose={() => setCreateOpen(false)}
        />
        <form onSubmit={handleCreate}>
          <DialogBody className="space-y-3">
            <Field label="Nazwa klienta *">
              <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="Ogrodowy Expert" required autoFocus />
            </Field>
            <Field label="Email właściciela (login) *">
              <Input type="email" value={ownerEmail} onChange={(e) => setOwnerEmail(e.target.value)} placeholder="kontakt@firma.pl" required />
            </Field>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Plan">
                <select
                  value={plan}
                  onChange={(e) => setPlan(e.target.value)}
                  className="h-8 w-full rounded-md border border-border bg-bg-panel px-3 text-[13px] text-text focus-visible:outline-none focus-visible:border-accent"
                >
                  {PLANS.map((p) => <option key={p} value={p}>{p}</option>)}
                </select>
              </Field>
              <Field label="Nazwa pierwszego bota (opcj.)">
                <Input value={firstBotName} onChange={(e) => setFirstBotName(e.target.value)} placeholder="domyślnie = nazwa klienta" />
              </Field>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Nazwa firmy (opcj.)">
                <Input value={companyName} onChange={(e) => setCompanyName(e.target.value)} placeholder="Firma Sp. z o.o." />
              </Field>
              <Field label="NIP (opcj.)">
                <Input value={nip} onChange={(e) => setNip(e.target.value)} placeholder="1234567890" />
              </Field>
            </div>
            {createError && (
              <div className="text-[12px] text-error bg-error/10 border border-error/20 rounded-md px-3 py-2">{createError}</div>
            )}
          </DialogBody>
          <DialogFooter>
            <Button type="button" variant="secondary" onClick={() => setCreateOpen(false)}>Anuluj</Button>
            <Button type="submit" disabled={createOrg.isPending || !name || !ownerEmail}>
              {createOrg.isPending ? 'Tworzę…' : 'Utwórz klienta'}
            </Button>
          </DialogFooter>
        </form>
      </Dialog>

      {/* Dodaj bota */}
      <Dialog open={addBotFor !== null} onOpenChange={(o) => !o && setAddBotFor(null)}>
        <DialogHeader
          title={`Nowy bot — ${addBotFor?.name ?? ''}`}
          description="Tworzy kolejnego bota (tenant + wersja v1 + widget) w tej organizacji. Limit wiadomości pozostaje wspólny."
          onClose={() => setAddBotFor(null)}
        />
        <form onSubmit={handleAddBot}>
          <DialogBody className="space-y-3">
            <Field label="Nazwa bota *">
              <Input value={botName} onChange={(e) => setBotName(e.target.value)} placeholder="Sklep meblowy" required autoFocus />
            </Field>
            {addBotError && (
              <div className="text-[12px] text-error bg-error/10 border border-error/20 rounded-md px-3 py-2">{addBotError}</div>
            )}
          </DialogBody>
          <DialogFooter>
            <Button type="button" variant="secondary" onClick={() => setAddBotFor(null)}>Anuluj</Button>
            <Button type="submit" disabled={addBot.isPending || !botName}>
              {addBot.isPending ? 'Tworzę…' : 'Dodaj bota'}
            </Button>
          </DialogFooter>
        </form>
      </Dialog>

      {/* Wynik provisioningu organizacji (pokazany RAZ) */}
      <Dialog open={provisionResult !== null} onOpenChange={(o) => !o && setProvisionResult(null)}>
        {provisionResult && (
          <>
            <DialogHeader
              title="Klient utworzony ✓"
              description="Skopiuj dane i przekaż klientowi. Hasło pokazujemy TYLKO TERAZ."
              onClose={() => setProvisionResult(null)}
            />
            <DialogBody className="space-y-2.5">
              <CopyRow label="Klient" value={provisionResult.organizationName} />
              <CopyRow label="Login" value={provisionResult.ownerEmail} />
              <CopyRow label="Hasło" value={provisionResult.generatedPassword} highlight />
              <CopyRow label="Pierwszy bot" value={provisionResult.firstBotName} />
              <CopyRow label="Klucz widgeta" value={provisionResult.widgetKey} />
              <div className="text-[12px] text-warning bg-warning/10 border border-warning/20 rounded-md px-3 py-2 mt-2">
                Zapisz hasło teraz — po zamknięciu nie odzyskasz go (tylko reset).
              </div>
            </DialogBody>
            <DialogFooter>
              <Button onClick={() => setProvisionResult(null)}>Gotowe</Button>
            </DialogFooter>
          </>
        )}
      </Dialog>

      {/* Wynik dodania bota (klucz widgeta pokazany RAZ) */}
      <Dialog open={addBotResult !== null} onOpenChange={(o) => !o && setAddBotResult(null)}>
        {addBotResult && (
          <>
            <DialogHeader
              title="Bot dodany ✓"
              description="Przekaż klientowi klucz widgeta do osadzenia na stronie."
              onClose={() => setAddBotResult(null)}
            />
            <DialogBody className="space-y-2.5">
              <CopyRow label="Bot" value={addBotResult.botName} />
              <CopyRow label="Klucz widgeta" value={addBotResult.widgetKey} highlight />
            </DialogBody>
            <DialogFooter>
              <Button onClick={() => setAddBotResult(null)}>Gotowe</Button>
            </DialogFooter>
          </>
        )}
      </Dialog>
    </>
  );
}

function OrganizationCard({ org, onAddBot }: { org: OrganizationRow; onAddBot: () => void }) {
  const unlimited = org.effectiveLimit === -1;
  const pct = unlimited || org.effectiveLimit === 0
    ? 0 : Math.round((org.userMessages / org.effectiveLimit) * 100);

  return (
    <Card>
      <CardBody>
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-center gap-2.5 min-w-0">
            <div className="size-9 rounded-lg bg-bg-elevated flex items-center justify-center text-text-muted shrink-0">
              <Building2 className="size-4" />
            </div>
            <div className="min-w-0">
              <div className="text-[14px] font-semibold truncate">{org.name}</div>
              <div className="text-[11px] text-text-dim mono">{org.slug}{org.nip ? ` · NIP ${org.nip}` : ''}</div>
            </div>
          </div>
          <div className="flex items-center gap-2 shrink-0">
            <Badge variant={org.plan === 'FREE' ? 'neutral' : 'info'}>{org.plan}</Badge>
            {org.status === 'ACTIVE'
              ? <span className="text-success text-[12px]">● aktywny</span>
              : <span className="text-text-dim text-[12px]">● {org.status.toLowerCase()}</span>}
          </div>
        </div>

        <div className="mt-3 text-[12px]">
          <span className="text-text-dim">Wiadomości (limit wspólny): </span>
          <span className={cn('font-medium', pct >= 100 && 'text-error', pct >= 80 && pct < 100 && 'text-warning')}>
            {org.userMessages.toLocaleString('pl-PL')}
          </span>
          <span className="text-text-dim"> / {unlimited ? '∞' : org.effectiveLimit.toLocaleString('pl-PL')}</span>
        </div>

        <div className="mt-3 border-t border-border pt-3">
          <div className="flex items-center justify-between mb-2">
            <div className="text-[11px] uppercase tracking-wider text-text-dim font-semibold">
              Boty ({org.bots.length})
            </div>
            <Button variant="secondary" size="sm" onClick={onAddBot}>
              <Plus className="size-3" /> Dodaj bota
            </Button>
          </div>
          <div className="grid grid-cols-2 gap-2">
            {org.bots.map((b) => (
              <div key={b.id} className="flex items-center gap-2.5 bg-bg-elevated rounded-md px-3 py-2">
                <span
                  className="size-6 rounded flex items-center justify-center shrink-0"
                  style={{ color: botColor(b.id), backgroundColor: botColorTint(b.id) }}
                >
                  <Bot className="size-3.5" />
                </span>
                <div className="min-w-0">
                  <div className="text-[13px] font-medium truncate">{b.name}</div>
                  <div className="text-[11px] text-text-dim">{b.userMessages.toLocaleString('pl-PL')} wiad.</div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </CardBody>
    </Card>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1.5">
      <label className="text-[12px] text-text-muted">{label}</label>
      {children}
    </div>
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
