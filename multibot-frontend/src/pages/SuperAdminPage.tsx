import { useState, type FormEvent } from 'react';
import { Plus, Copy, Check, Building2 } from 'lucide-react';
import { toast } from 'sonner';
import { Topbar } from '@/components/layout/Topbar';
import { Card, CardBody } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '@/components/ui/table';
import { Dialog, DialogHeader, DialogBody, DialogFooter } from '@/components/ui/dialog';
import { useTenants, useCreateTenant, type ProvisionResult } from '@/hooks/useSuperAdmin';
import { cn } from '@/lib/cn';

const PLANS = ['FREE', 'BASIC', 'PRO', 'ENTERPRISE'];

export function SuperAdminPage() {
  const tenants = useTenants();
  const createTenant = useCreateTenant();

  const [formOpen, setFormOpen] = useState(false);
  const [result, setResult] = useState<ProvisionResult | null>(null);

  // form fields
  const [name, setName] = useState('');
  const [ownerEmail, setOwnerEmail] = useState('');
  const [plan, setPlan] = useState('FREE');
  const [companyName, setCompanyName] = useState('');
  const [nip, setNip] = useState('');
  const [error, setError] = useState<string | null>(null);

  function resetForm() {
    setName(''); setOwnerEmail(''); setPlan('FREE'); setCompanyName(''); setNip(''); setError(null);
  }

  async function handleCreate(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      const r = await createTenant.mutateAsync({
        name, ownerEmail, plan,
        companyName: companyName || undefined,
        nip: nip || undefined,
      });
      setFormOpen(false);
      resetForm();
      setResult(r);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Nie udało się utworzyć klienta');
    }
  }

  return (
    <>
      <Topbar
        title="Super Admin — Klienci"
        actions={
          <Button onClick={() => { resetForm(); setFormOpen(true); }}>
            <Plus className="size-3.5" /> Nowy klient
          </Button>
        }
      />

      <Card>
        {tenants.isPending ? (
          <CardBody><div className="h-40 bg-bg-elevated/40 rounded animate-pulse" /></CardBody>
        ) : !tenants.data || tenants.data.length === 0 ? (
          <CardBody className="py-10 text-center text-text-muted text-[13px]">
            Brak klientów. Utwórz pierwszego przyciskiem „Nowy klient".
          </CardBody>
        ) : (
          <Table>
            <TableHeader>
              <TableRow className="hover:bg-transparent">
                <TableHead>Klient</TableHead>
                <TableHead>Plan</TableHead>
                <TableHead>Wiadomości</TableHead>
                <TableHead>NIP</TableHead>
                <TableHead>Status</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {tenants.data.map((t) => {
                const unlimited = t.effectiveLimit === -1;
                const pct = unlimited || t.effectiveLimit === 0
                  ? 0 : Math.round((t.userMessages / t.effectiveLimit) * 100);
                return (
                  <TableRow key={t.id}>
                    <TableCell>
                      <div className="flex items-center gap-2.5">
                        <div className="size-7 rounded-md bg-bg-elevated flex items-center justify-center text-text-muted">
                          <Building2 className="size-3.5" />
                        </div>
                        <div>
                          <div className="text-[13px] font-medium">{t.name}</div>
                          <div className="text-[11px] text-text-dim mono">{t.slug}</div>
                        </div>
                      </div>
                    </TableCell>
                    <TableCell>
                      <Badge variant={t.plan === 'FREE' ? 'neutral' : 'info'}>{t.plan}</Badge>
                    </TableCell>
                    <TableCell>
                      <span className={cn('font-medium', pct >= 100 && 'text-error', pct >= 80 && pct < 100 && 'text-warning')}>
                        {t.userMessages.toLocaleString('pl-PL')}
                      </span>
                      <span className="text-text-dim"> / {unlimited ? '∞' : t.effectiveLimit.toLocaleString('pl-PL')}</span>
                    </TableCell>
                    <TableCell className="text-text-muted mono text-[12px]">{t.nip ?? '—'}</TableCell>
                    <TableCell>
                      {t.status === 'ACTIVE'
                        ? <span className="text-success text-[12px]">● aktywny</span>
                        : <span className="text-text-dim text-[12px]">● {t.status.toLowerCase()}</span>}
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        )}
      </Card>

      {/* Formularz nowego klienta */}
      <Dialog open={formOpen} onOpenChange={setFormOpen}>
        <DialogHeader
          title="Nowy klient"
          description="Tworzy tenant + konto OWNER + bota + widget. Hasło wygeneruje się automatycznie."
          onClose={() => setFormOpen(false)}
        />
        <form onSubmit={handleCreate}>
          <DialogBody className="space-y-3">
            <Field label="Nazwa klienta *">
              <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="Ogrodowy Expert" required autoFocus />
            </Field>
            <Field label="Email właściciela (login) *">
              <Input type="email" value={ownerEmail} onChange={(e) => setOwnerEmail(e.target.value)} placeholder="kontakt@firma.pl" required />
            </Field>
            <Field label="Plan">
              <select
                value={plan}
                onChange={(e) => setPlan(e.target.value)}
                className="h-8 w-full rounded-md border border-border bg-bg-panel px-3 text-[13px] text-text focus-visible:outline-none focus-visible:border-accent"
              >
                {PLANS.map((p) => <option key={p} value={p}>{p}</option>)}
              </select>
            </Field>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Nazwa firmy (opcj.)">
                <Input value={companyName} onChange={(e) => setCompanyName(e.target.value)} placeholder="Firma Sp. z o.o." />
              </Field>
              <Field label="NIP (opcj.)">
                <Input value={nip} onChange={(e) => setNip(e.target.value)} placeholder="1234567890" />
              </Field>
            </div>
            {error && (
              <div className="text-[12px] text-error bg-error/10 border border-error/20 rounded-md px-3 py-2">{error}</div>
            )}
          </DialogBody>
          <DialogFooter>
            <Button type="button" variant="secondary" onClick={() => setFormOpen(false)}>Anuluj</Button>
            <Button type="submit" disabled={createTenant.isPending || !name || !ownerEmail}>
              {createTenant.isPending ? 'Tworzę…' : 'Utwórz klienta'}
            </Button>
          </DialogFooter>
        </form>
      </Dialog>

      {/* Wynik — dane do przekazania (pokazane RAZ) */}
      <Dialog open={result !== null} onOpenChange={(o) => !o && setResult(null)}>
        {result && (
          <>
            <DialogHeader
              title="Klient utworzony ✓"
              description="Skopiuj dane i przekaż klientowi. Hasło pokazujemy TYLKO TERAZ."
              onClose={() => setResult(null)}
            />
            <DialogBody className="space-y-2.5">
              <CopyRow label="Klient" value={result.tenantName} />
              <CopyRow label="Login" value={result.ownerEmail} />
              <CopyRow label="Hasło" value={result.generatedPassword} highlight />
              <CopyRow label="Klucz widgeta" value={result.widgetKey} />
              <div className="text-[12px] text-warning bg-warning/10 border border-warning/20 rounded-md px-3 py-2 mt-2">
                Zapisz hasło teraz — po zamknięciu nie odzyskasz go (tylko reset).
              </div>
            </DialogBody>
            <DialogFooter>
              <Button onClick={() => setResult(null)}>Gotowe</Button>
            </DialogFooter>
          </>
        )}
      </Dialog>
    </>
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
