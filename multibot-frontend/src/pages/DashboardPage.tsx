import { Link } from 'react-router-dom';
import { FileText, MessageSquare, Inbox, Plus, Upload } from 'lucide-react';
import { Topbar } from '@/components/layout/Topbar';
import { Button } from '@/components/ui/button';
import { Card, CardBody, CardHeader, CardTitle, CardSubtitle } from '@/components/ui/card';
import { useDocuments } from '@/hooks/useDocuments';
import type { ReactNode } from 'react';

interface StatTileProps {
  label: string;
  value: ReactNode;
  delta?: string;
  deltaPositive?: boolean;
  loading?: boolean;
}

function StatTile({ label, value, delta, deltaPositive = true, loading }: StatTileProps) {
  return (
    <Card className="hover:border-border-strong transition-colors">
      <CardBody className="space-y-2">
        <div className="text-xs text-text-muted">{label}</div>
        {loading ? (
          <div className="h-[26px] w-16 bg-bg-elevated rounded animate-pulse" />
        ) : (
          <div className="text-[26px] font-semibold tracking-tight leading-none">{value}</div>
        )}
        {delta && !loading && (
          <div className={`text-[11px] ${deltaPositive ? 'text-success' : 'text-error'}`}>{delta}</div>
        )}
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

export function DashboardPage() {
  const documents = useDocuments({ size: 1 });
  const docCount = documents.data?.pagination?.totalElements;

  return (
    <>
      <Topbar
        title="Panel"
        actions={
          <>
            <Button variant="secondary">
              <Upload className="size-3.5" /> Wgraj
            </Button>
            <Button>
              <Plus className="size-3.5" /> Nowa sesja
            </Button>
          </>
        }
      />

      <div className="grid grid-cols-4 gap-4 mb-6">
        <StatTile
          label="Dokumenty"
          value={docCount ?? '—'}
          delta={docCount !== undefined ? `${docCount} w bazie` : undefined}
          loading={documents.isPending}
        />
        <StatTile label="Sesje dziś" value={12} delta="↑ 40%" />
        <StatTile label="Zużyte tokeny" value="8 472" delta="~$0.04" />
        <StatTile label="Średni czas odpowiedzi" value="1.4s" delta="↑ 0.2s" deltaPositive={false} />
      </div>

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
    </>
  );
}
