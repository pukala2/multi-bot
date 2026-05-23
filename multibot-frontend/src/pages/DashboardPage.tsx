import { Topbar } from '@/components/layout/Topbar';
import { Button } from '@/components/ui/button';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/card';
import { Plus, Upload } from 'lucide-react';

interface StatTileProps {
  label: string;
  value: string | number;
  delta?: string;
  deltaPositive?: boolean;
}

function StatTile({ label, value, delta, deltaPositive = true }: StatTileProps) {
  return (
    <Card className="hover:border-border-strong transition-colors">
      <CardBody className="space-y-2">
        <div className="text-xs text-text-muted">{label}</div>
        <div className="text-[26px] font-semibold tracking-tight leading-none">{value}</div>
        {delta && (
          <div className={`text-[11px] ${deltaPositive ? 'text-success' : 'text-error'}`}>
            {delta}
          </div>
        )}
      </CardBody>
    </Card>
  );
}

export function DashboardPage() {
  return (
    <>
      <Topbar
        title="Dashboard"
        actions={
          <>
            <Button variant="secondary">
              <Upload className="size-3.5" /> Upload
            </Button>
            <Button>
              <Plus className="size-3.5" /> New session
            </Button>
          </>
        }
      />

      <div className="grid grid-cols-4 gap-4 mb-8">
        <StatTile label="Documents" value={3} delta="↑ 2 this week" />
        <StatTile label="Sessions today" value={12} delta="↑ 40%" />
        <StatTile label="Tokens used" value="8,472" delta="~$0.04" />
        <StatTile label="Avg latency" value="1.4s" delta="↑ 0.2s" deltaPositive={false} />
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Welcome to Multibot</CardTitle>
        </CardHeader>
        <CardBody>
          <p className="text-text-muted text-[13.5px] leading-relaxed">
            Admin panel jest gotowy. W kolejnych fazach dodamy: pełną listę dokumentów z uploadem (F3),
            playground czatu z SSE streamingiem (F4), historię sesji (F5) oraz dashboard z realnymi
            statystykami z backendu (F6).
          </p>
          <p className="text-text-dim text-[12.5px] mt-3 mono">
            Backend: <span className="text-info">http://localhost:8080</span> (proxy via Vite /api/* &
            /actuator/*)
          </p>
        </CardBody>
      </Card>
    </>
  );
}
