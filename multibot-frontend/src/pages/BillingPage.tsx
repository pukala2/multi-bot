import { useState } from 'react';
import { Plus } from 'lucide-react';
import { Topbar } from '@/components/layout/Topbar';
import { Card, CardBody, CardHeader, CardTitle, CardSubtitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { AddBotDialog } from '@/components/bots/AddBotDialog';
import { useBillingUsage, useBillingPlan } from '@/hooks/useBilling';
import { useBotLimit } from '@/hooks/useBots';
import { useAuth } from '@/hooks/useAuth';
import { cn } from '@/lib/cn';

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('pl-PL', { day: 'numeric', month: 'long', year: 'numeric' });
}

export function BillingPage() {
  const auth = useAuth();
  const usage = useBillingUsage();
  const plan = useBillingPlan();
  const isOwner = auth.data?.role === 'OWNER';
  const botLimit = useBotLimit(isOwner);
  const [addBotOpen, setAddBotOpen] = useState(false);

  // Super admin nie ma tenanta = nie ma planu. Cross-tenant overview pojawi sie w Fazie D.
  if (auth.data?.role === 'SUPER_ADMIN') {
    return (
      <>
        <Topbar title="Plan i limity" />
        <Card>
          <CardBody className="text-text-muted text-[13px] py-8 text-center">
            Jako super admin nie masz wlasnego planu — to widok per-klient.<br/>
            Wkrótce: lista wszystkich klientów + ich plany w panelu super admina.
          </CardBody>
        </Card>
      </>
    );
  }

  if (usage.isPending || plan.isPending) {
    return (
      <>
        <Topbar title="Plan i limity" />
        <Card>
          <CardBody>
            <div className="h-32 bg-bg-elevated/40 rounded animate-pulse" />
          </CardBody>
        </Card>
      </>
    );
  }

  if (!usage.data || !plan.data) {
    return (
      <>
        <Topbar title="Plan i limity" />
        <Card>
          <CardBody className="text-text-muted text-[13px]">
            Nie udało się pobrać danych o planie.
          </CardBody>
        </Card>
      </>
    );
  }

  const u = usage.data;
  const p = plan.data;
  const unlimited = u.unlimited;
  const used = u.userMessages;
  const limit = u.effectiveLimit;
  const percentUsed = u.percentUsed;

  // Kolor pasku w zależności od zużycia
  const barColorClass =
    percentUsed >= 100 ? 'bg-error'
    : percentUsed >= 80 ? 'bg-warning'
    : 'bg-accent';

  return (
    <>
      <Topbar title="Plan i limity" />

      {/* Plan info */}
      <Card className="mb-4">
        <CardHeader>
          <div className="flex items-center gap-3">
            <CardTitle>Twój plan: {p.name}</CardTitle>
            <Badge variant={p.code === 'FREE' ? 'neutral' : 'info'}>{p.code}</Badge>
          </div>
          <CardSubtitle>
            {p.pricePlnMonthly === 0
              ? 'Darmowy'
              : p.pricePlnMonthly === null
              ? 'Cena indywidualna'
              : `${p.pricePlnMonthly} zł / miesiąc`}
          </CardSubtitle>
        </CardHeader>
        <CardBody>
          <div className="grid grid-cols-3 gap-3 text-[13px]">
            <Feature label="Wiadomości / miesiąc" value={unlimited ? 'Bez limitu' : limit.toLocaleString('pl-PL')} />
            <Feature label="Własna domena" value={p.customDomainAllowed ? 'Tak' : 'Nie'} ok={p.customDomainAllowed} />
            <Feature label="Dostęp do API" value={p.apiAccessAllowed ? 'Tak' : 'Nie'} ok={p.apiAccessAllowed} />
          </div>
        </CardBody>
      </Card>

      {/* Boty (self-serve dodawanie) */}
      {isOwner && botLimit.data && (
        <Card className="mb-4">
          <CardHeader>
            <div className="flex items-center justify-between w-full gap-3">
              <div>
                <CardTitle>Boty (strony)</CardTitle>
                <CardSubtitle>Każda strona to osobny bot. Limit wiadomości jest wspólny dla wszystkich.</CardSubtitle>
              </div>
              <Button
                size="sm"
                disabled={!botLimit.data.allowed}
                onClick={() => setAddBotOpen(true)}
                title={botLimit.data.allowed ? 'Dodaj bota' : 'Limit planu osiągnięty'}
              >
                <Plus className="size-3.5" /> Dodaj bota
              </Button>
            </div>
          </CardHeader>
          <CardBody>
            <div className="text-[14px]">
              <span className="text-[22px] font-semibold">{botLimit.data.currentBots}</span>
              <span className="text-text-dim text-[13px] ml-1">
                / {botLimit.data.unlimited ? '∞' : botLimit.data.maxBots} botów
              </span>
            </div>
            {!botLimit.data.allowed && !botLimit.data.unlimited && (
              <div className="mt-3 text-[12.5px] text-warning bg-warning/10 border border-warning/20 rounded-md px-3 py-2">
                Osiągnięto limit stron w planie {p.code}. Zmień plan, aby dodać więcej botów.
              </div>
            )}
          </CardBody>
        </Card>
      )}

      {/* Usage progress */}
      <Card className="mb-4">
        <CardHeader>
          <div>
            <CardTitle>Wykorzystanie w tym miesiącu</CardTitle>
            <CardSubtitle>
              Reset: {formatDate(u.resetsAt)} (za {u.remainingDays} {u.remainingDays === 1 ? 'dzień' : 'dni'})
            </CardSubtitle>
          </div>
        </CardHeader>
        <CardBody className="space-y-4">
          {unlimited ? (
            <div className="text-[15px] text-text">
              <span className="text-success font-semibold">Bez limitu</span>
              <span className="text-text-dim ml-2">— pisz ile chcesz</span>
            </div>
          ) : (
            <>
              <div className="flex justify-between items-baseline">
                <div>
                  <span className="text-[26px] font-semibold">{used.toLocaleString('pl-PL')}</span>
                  <span className="text-text-dim text-[13px] ml-1">/ {limit.toLocaleString('pl-PL')} wiadomości</span>
                </div>
                <div className={cn(
                  'text-[13.5px] font-medium',
                  percentUsed >= 100 ? 'text-error'
                  : percentUsed >= 80 ? 'text-warning'
                  : 'text-text-muted'
                )}>
                  {percentUsed.toFixed(0)}%
                </div>
              </div>
              <div className="h-2 bg-bg-elevated rounded-full overflow-hidden">
                <div
                  className={cn('h-full rounded-full transition-all', barColorClass)}
                  style={{ width: `${Math.min(100, percentUsed)}%` }}
                />
              </div>
              {percentUsed >= 80 && percentUsed < 100 && (
                <div className="text-[12.5px] text-warning bg-warning/10 border border-warning/20 rounded-md px-3 py-2">
                  Zbliżasz się do limitu. Przy przekroczeniu asystent przestanie odpowiadać do końca miesiąca.
                </div>
              )}
              {percentUsed >= 100 && (
                <div className="text-[12.5px] text-error bg-error/10 border border-error/20 rounded-md px-3 py-2">
                  Limit miesięczny osiągnięty. Asystent na stronie nie odpowiada. Skontaktuj się z administratorem
                  żeby zwiększyć limit lub poczekaj do {formatDate(u.resetsAt)}.
                </div>
              )}
            </>
          )}
        </CardBody>
      </Card>

      {/* Stats */}
      <Card>
        <CardHeader>
          <div>
            <CardTitle>Statystyki okresu</CardTitle>
            <CardSubtitle>Co dzieje się w tym miesiącu</CardSubtitle>
          </div>
        </CardHeader>
        <CardBody>
          <div className="grid grid-cols-3 gap-3">
            <Stat label="Pytania klientów" value={u.userMessages.toLocaleString('pl-PL')} />
            <Stat label="Odpowiedzi bota" value={u.aiMessages.toLocaleString('pl-PL')} />
            <Stat
              label="Średnio wiad. / pytanie"
              value={u.userMessages > 0 ? ((u.aiMessages + u.userMessages) / u.userMessages).toFixed(1) : '0.0'}
            />
          </div>
        </CardBody>
      </Card>

      {isOwner && <AddBotDialog open={addBotOpen} onOpenChange={setAddBotOpen} />}
    </>
  );
}

function Feature({ label, value, ok }: { label: string; value: string; ok?: boolean }) {
  return (
    <div className="bg-bg-elevated rounded-md px-3 py-2.5">
      <div className="text-[11px] text-text-muted uppercase tracking-wider">{label}</div>
      <div className={cn(
        'text-[14.5px] font-medium mt-0.5',
        ok === false ? 'text-text-dim' : 'text-text'
      )}>
        {value}
      </div>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="bg-bg-elevated rounded-md p-4">
      <div className="text-[11px] text-text-muted uppercase tracking-wider">{label}</div>
      <div className="text-[22px] font-semibold mt-1.5">{value}</div>
    </div>
  );
}
