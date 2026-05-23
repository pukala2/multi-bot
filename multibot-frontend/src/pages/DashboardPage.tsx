import { Link } from 'react-router-dom';
import { FileText, MessageSquare, Inbox, Plus, Upload } from 'lucide-react';
import { Topbar } from '@/components/layout/Topbar';
import { Button } from '@/components/ui/button';
import { Card, CardBody, CardHeader, CardTitle, CardSubtitle } from '@/components/ui/card';
import { DocumentStatusBar } from '@/components/domain/DocumentStatusBar';
import { RecentActivity } from '@/components/domain/RecentActivity';
import { useAdminStats } from '@/hooks/useAdminStats';
import type { ReactNode } from 'react';

interface StatTileProps {
  label: string;
  value: ReactNode;
  meta?: ReactNode;
  loading?: boolean;
}

function StatTile({ label, value, meta, loading }: StatTileProps) {
  return (
    <Card className="hover:border-border-strong transition-colors">
      <CardBody className="space-y-2">
        <div className="text-xs text-text-muted">{label}</div>
        {loading ? (
          <div className="h-[26px] w-16 bg-bg-elevated rounded animate-pulse" />
        ) : (
          <div className="text-[26px] font-semibold tracking-tight leading-none">{value}</div>
        )}
        {meta && !loading && <div className="text-[11.5px] text-text-dim">{meta}</div>}
      </CardBody>
    </Card>
  );
}

interface QuickActionProps {
  to: string;
  icon: typeof FileText;
  title: string;
  description: string;
}

function QuickAction({ to, icon: Icon, title, description }: QuickActionProps) {
  return (
    <Link
      to={to}
      className="group flex items-start gap-3 p-4 rounded-lg border border-border bg-bg-panel hover:border-accent/40 hover:bg-bg-elevated transition-colors"
    >
      <div className="size-9 rounded-md bg-bg-elevated flex items-center justify-center text-accent group-hover:bg-accent/10 transition-colors">
        <Icon className="size-4" />
      </div>
      <div className="flex-1 min-w-0">
        <div className="text-[13.5px] font-medium text-text">{title}</div>
        <div className="text-[12px] text-text-muted mt-0.5">{description}</div>
      </div>
    </Link>
  );
}

function formatCost(usd: number): string {
  if (usd < 0.01) return `~$${usd.toFixed(4)}`;
  return `~$${usd.toFixed(2)}`;
}

export function DashboardPage() {
  const stats = useAdminStats();
  const data = stats.data;

  const docByStatus = data?.documents.byStatus ?? {};
  const docTotal = data?.documents.total ?? 0;
  const isEmpty = !stats.isPending && docTotal === 0 && (data?.sessions.total ?? 0) === 0;

  return (
    <>
      <Topbar
        title="Panel"
        actions={
          <>
            <Link to="/documents">
              <Button variant="secondary">
                <Upload className="size-3.5" /> Wgraj
              </Button>
            </Link>
            <Link to="/chat">
              <Button>
                <Plus className="size-3.5" /> Nowa sesja
              </Button>
            </Link>
          </>
        }
      />

      {isEmpty && (
        <Card className="mb-6 border-accent/30 bg-accent/[0.03]">
          <CardBody className="text-center py-10">
            <div className="size-12 rounded-full bg-accent/15 text-accent flex items-center justify-center mx-auto mb-3">
              <Upload className="size-5" />
            </div>
            <h2 className="text-[16px] font-semibold mb-1">Witamy w Multibot</h2>
            <p className="text-text-muted text-[13px] max-w-md mx-auto mb-4">
              Aby zacząć, wgraj pierwszy dokument do bazy wiedzy. Bot zacznie z niego korzystać po
              kilku sekundach.
            </p>
            <Link to="/documents">
              <Button>
                <Upload className="size-3.5" /> Wgraj pierwszy dokument
              </Button>
            </Link>
          </CardBody>
        </Card>
      )}

      <div className="grid grid-cols-4 gap-4 mb-4">
        <StatTile
          label="Dokumenty"
          value={docTotal}
          meta={
            Object.keys(docByStatus).length > 0 ? (
              <DocumentStatusBar byStatus={docByStatus} />
            ) : (
              'wgraj pierwszy plik'
            )
          }
          loading={stats.isPending}
        />
        <StatTile
          label="Sesje dziś"
          value={data?.sessions.today ?? 0}
          meta={`${data?.sessions.thisWeek ?? 0} w tym tygodniu · ${data?.sessions.total ?? 0} łącznie`}
          loading={stats.isPending}
        />
        <StatTile
          label="Zużyte tokeny dziś"
          value={(data?.tokens.today ?? 0).toLocaleString('pl-PL')}
          meta={`${formatCost(data?.tokens.estimatedCostUsd ?? 0)} · łącznie ${(data?.tokens.total ?? 0).toLocaleString('pl-PL')}`}
          loading={stats.isPending}
        />
        <StatTile
          label="Wiadomości dziś"
          value={data?.messages.today ?? 0}
          meta={`${(data?.messages.total ?? 0).toLocaleString('pl-PL')} łącznie`}
          loading={stats.isPending}
        />
      </div>

      <div className="grid grid-cols-3 gap-4">
        <div className="col-span-2 space-y-4">
          <Card>
            <CardHeader>
              <div>
                <CardTitle>Szybkie akcje</CardTitle>
                <CardSubtitle>najczęściej używane skróty</CardSubtitle>
              </div>
            </CardHeader>
            <CardBody className="grid grid-cols-3 gap-3">
              <QuickAction
                to="/documents"
                icon={FileText}
                title="Dokumenty"
                description="Wgraj plik, sprawdź status indeksowania"
              />
              <QuickAction
                to="/chat"
                icon={MessageSquare}
                title="Testowy czat"
                description="Zadaj pytanie i zobacz jak bot odpowiada"
              />
              <QuickAction
                to="/sessions"
                icon={Inbox}
                title="Sesje"
                description="Przejrzyj rozmowy użytkowników"
              />
            </CardBody>
          </Card>
        </div>

        <div>
          <RecentActivity />
        </div>
      </div>
    </>
  );
}
